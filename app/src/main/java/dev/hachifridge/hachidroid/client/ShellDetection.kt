package dev.hachifridge.hachidroid.client

import android.content.Context
import android.net.Uri
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Tells a packer-protected game build from a plain one, and which version it is.
 *
 * The two Umamusume releases HachiDroid supports are built very differently, and which patch a
 * split set needs follows from the build, not from the package the user happened to pick:
 *
 * - Japan's build ships an ordinary `libil2cpp.so`, the 6 KB `libmain.so` loader, and no packer.
 *   Hachimi replaces `libmain.so` and nothing else is touched.
 * - Global's build ships an encrypted `libil2cpp.so` behind `lib__<hash>__.so` (its ELF header is
 *   intact, the body is not), plus `libsigner.so`; the shell owns `Application`. That build needs
 *   the shell removed and a Senbei-restored library supplied before Hachimi can load.
 * - Priconne is packaged the same way as Global, which is why it shares the shell flow.
 *
 * The packer stub is the reliable marker: it is present exactly when the build is protected, and
 * its name is the one the shell loader itself uses. The manifest package is the cheap answer for a
 * selection that carries no `lib/` entry (the base split alone, or an XAPK).
 *
 * The same pass also reads `versionName`/`versionCode`, because a restored library and metadata are
 * only valid for the build they were unpacked from; the studio compares that stamp with the one the
 * folder recorded.
 */
object ShellDetection {

    /** A build whose store copy carries the packer, even before its splits are inspected. */
    private val PACKED_PACKAGES = setOf(
        "com.cygames.umamusume",
        "jp.co.cygames.princessconnectredive"
    )

    /**
     * Builds whose manifest settles the question without scanning the rest of the archive.
     *
     * Every package a client claims is either packed or known to ship its libraries plain. An
     * unknown package is not assumed plain: a future or region variant may carry the loader in an
     * entry after the manifest, so its archive keeps being scanned.
     */
    private val KNOWN_PLAIN_PACKAGES: Set<String> by lazy {
        Client.entries.flatMap { it.packageNames }.toSet() - PACKED_PACKAGES
    }

    private const val MANIFEST_ENTRY = "AndroidManifest.xml"

    /** Only the binary manifest is read, and it is small; this keeps a corrupt entry bounded. */
    private const val MANIFEST_LIMIT_BYTES = 4 * 1024 * 1024

    private const val ATTR_VERSION_CODE = 0x0101021b
    private const val ATTR_VERSION_NAME = 0x0101021c

    /**
     * How much of an inner APK is read when a picked archive turns out to be an XAPK.
     *
     * An APK's `AndroidManifest.xml` is its first entry, so a bounded prefix is enough to read the
     * package and version without writing the split set to disk.
     */
    private const val NESTED_PREFIX_BYTES = 3 * 1024 * 1024

    /**
     * What one archive, or one picked set, says about the build it belongs to.
     *
     * [versionStamp] is `versionName/versionCode` — enough to tell one store update from another
     * without keeping the archive around.
     */
    data class BuildInfo(
        val packageName: String? = null,
        val versionStamp: String? = null,
        val packerStub: Boolean = false
    ) {
        /** Whether these picks are a packer-protected build. */
        val protected: Boolean get() = packerStub || isPackedPackage(packageName)
    }

    fun isPackerStub(fileName: String): Boolean =
        fileName.startsWith("lib__") && fileName.endsWith("__.so") && fileName.length > 8

    fun isPackedPackage(packageName: String?): Boolean =
        packageName != null && packageName in PACKED_PACKAGES

    /**
     * Whether the installed copy decides the build, which it only does while nothing is picked.
     *
     * Once files are picked, they are the build being patched: a user who has Global installed and
     * picks Japan's splits is patching Japan, and answering from the installed package would demand
     * Global's restored libraries and fail a build that needs none of them.
     */
    fun installedTargetIsPacked(anyFilePicked: Boolean, installedPackage: String?): Boolean =
        !anyFilePicked && isPackedPackage(installedPackage)

    /** True when an extracted split still carries the packer's loader. */
    fun hasPackerStub(directory: File): Boolean =
        directory.walkTopDown().any { it.isFile && isPackerStub(it.name) }

    /**
     * Whether the picked files belong to a packer-protected build.
     *
     * Touches the picked streams: call from an IO dispatcher.
     */
    fun isProtected(context: Context, uris: List<Uri>): Boolean = inspect(context, uris).protected

    /**
     * One pass over the picks: the package, the version stamp and the packer stub, all from the
     * same read. Touches the picked streams: call from an IO dispatcher.
     */
    fun inspect(context: Context, uris: List<Uri>): BuildInfo =
        firstConclusive(uris.indices) { index ->
            runCatching {
                context.contentResolver.openInputStream(uris[index])?.use { raw ->
                    ZipInputStream(raw.buffered()).use { probeStream(it, allowNested = true) }
                }
            }.getOrNull()
        }

    /** Inspects one archive stream; exposed so the nested (XAPK) path can be tested on the JVM. */
    internal fun inspectStream(input: java.io.InputStream): BuildInfo =
        ZipInputStream(input.buffered()).use { probeStream(it, allowNested = true) }

    /**
     * The same answer for archives the caller already copied to local storage.
     *
     * Entry names come from the zip's central directory, so a config split with no manifest is
     * settled without decompressing anything.
     */
    fun inspectFiles(files: List<File>): BuildInfo =
        firstConclusive(files.indices) { index ->
            runCatching {
                java.util.zip.ZipFile(files[index]).use { zip ->
                    val names = zip.entries().asSequence().map { it.name }.toList()
                    val stub = names.any { isPackerStub(it.substringAfterLast('/')) }
                    val manifest = names.firstOrNull { it == MANIFEST_ENTRY }?.let { entryName ->
                        zip.getInputStream(zip.getEntry(entryName)).use { raw ->
                            readLimited(raw, MANIFEST_LIMIT_BYTES)?.let { bytes -> loadManifest(bytes) }
                        }
                    }
                    BuildInfo(manifest?.packageName, manifest?.let { versionStampOf(it) }, stub)
                }
            }.getOrNull()
        }

    /**
     * Walks the picks in order until one of them settles the build. The first package and stamp
     * seen are kept even when a later pick is the one carrying the stub, so a base split alone can
     * still name its version.
     */
    private inline fun firstConclusive(range: IntRange, probe: (Int) -> BuildInfo?): BuildInfo {
        var packageName: String? = null
        var stamp: String? = null
        for (index in range) {
            val info = probe(index) ?: continue
            if (packageName == null) packageName = info.packageName
            if (stamp == null) stamp = info.versionStamp
            if (info.packerStub) return BuildInfo(packageName, stamp, packerStub = true)
        }
        return BuildInfo(packageName, stamp)
    }

    /**
     * Reads one archive from the front: the manifest names the package and version, and the first
     * `lib__<hash>__.so` proves the packer.
     *
     * A known plain package ends the scan; anything else keeps looking for the stub, since the
     * loader is not guaranteed to sit before the manifest in every build.
     */
    private fun probeStream(zip: ZipInputStream, allowNested: Boolean): BuildInfo {
        var packageName: String? = null
        var stamp: String? = null
        while (true) {
            val entry = zip.nextEntry ?: break
            if (entry.isDirectory) {
                zip.closeEntry()
                continue
            }
            val name = entry.name.substringAfterLast('/')
            if (isPackerStub(name)) return BuildInfo(packageName, stamp, packerStub = true)
            if (entry.name == MANIFEST_ENTRY && packageName == null) {
                val bytes = zip.readLimited(MANIFEST_LIMIT_BYTES)
                val manifest = bytes?.let { loadManifest(it) }
                if (manifest != null) {
                    packageName = manifest.packageName
                    stamp = versionStampOf(manifest)
                    if (isPackedPackage(packageName) || packageName in KNOWN_PLAIN_PACKAGES) break
                }
            } else if (allowNested && name.endsWith(".apk", ignoreCase = true)) {
                // An XAPK carries the split APKs themselves, so the build is one archive deeper.
                // The inner manifest is what names it, and a packed package settles the question.
                val nested = probeNested(zip)
                if (nested != null) {
                    if (packageName == null) packageName = nested.packageName
                    if (stamp == null) stamp = nested.versionStamp
                    if (nested.packerStub) return BuildInfo(packageName, stamp, packerStub = true)
                    if (isPackedPackage(packageName) || packageName in KNOWN_PLAIN_PACKAGES) break
                }
            }
            zip.closeEntry()
        }
        return BuildInfo(packageName, stamp)
    }

    /**
     * Reads the first [NESTED_PREFIX_BYTES] of the entry the stream is positioned on as its own
     * archive. A truncated zip simply yields no manifest, which is why nothing here throws.
     */
    private fun probeNested(zip: ZipInputStream): BuildInfo? {
        val buffer = ByteArray(NESTED_PREFIX_BYTES)
        var read = 0
        while (read < buffer.size) {
            val count = zip.read(buffer, read, buffer.size - read)
            if (count <= 0) break
            read += count
        }
        if (read == 0) return null
        return runCatching {
            ZipInputStream(ByteArrayInputStream(buffer, 0, read)).use { inner ->
                probeStream(inner, allowNested = false)
            }
        }.getOrNull()?.takeIf { it.packageName != null || it.packerStub }
    }

    private fun loadManifest(bytes: ByteArray): AndroidManifestBlock? =
        runCatching { AndroidManifestBlock.load(ByteArrayInputStream(bytes)) }.getOrNull()

    /** `versionName/versionCode`, or null when the manifest carries neither. */
    private fun versionStampOf(manifest: AndroidManifestBlock): String? {
        val root = manifest.manifestElement ?: return null
        val code = runCatching {
            root.searchAttributeByResourceId(ATTR_VERSION_CODE)?.valueAsString
        }.getOrNull()
        val name = runCatching {
            root.searchAttributeByResourceId(ATTR_VERSION_NAME)?.valueAsString
        }.getOrNull()
        if (code.isNullOrEmpty() && name.isNullOrEmpty()) return null
        return "${name.orEmpty()}/$code"
    }

    private fun ZipInputStream.readLimited(limit: Int): ByteArray? = readLimited(this, limit)

    private fun readLimited(input: InputStream, limit: Int): ByteArray? {
        val buffer = ByteArray(limit)
        var read = 0
        while (read < limit) {
            val count = input.read(buffer, read, limit - read)
            if (count <= 0) break
            read += count
        }
        return if (read == 0) null else buffer.copyOf(read)
    }
}
