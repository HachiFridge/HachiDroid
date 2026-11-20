package dev.hachifridge.hachidroid.client

import android.content.Context
import android.net.Uri
import android.os.StatFs
import dev.hachifridge.hachidroid.core.senbei.SenbeiNative
import java.io.BufferedInputStream
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Produces the native libraries a shell-protected patch needs, on the device itself.
 *
 * Both protected clients ship a packer that keeps their libraries encrypted, so nothing in the
 * APK can be loaded as-is. The engine unpacks each protected image into a loadable one, and the
 * wording everywhere in the app follows that.
 *
 * The input is whatever the user can actually get hold of: the game's `config.arm64_v8a` split, or
 * any archive that happens to contain the libraries. Each entry named in [unpackable] is streamed
 * out of the archive, probed, and — only if it really carries a packer payload — handed to Senbei.
 * An entry that is already an unpacked library (a community build, a previous unpack) is accepted
 * as-is rather than rejected: the probe is the only thing that distinguishes them, and users
 * routinely have one or the other.
 *
 * Everything here is disk-bound and memory-heavy: Priconne's protected copy alone is ~300 MB and
 * its unpacked image ~210 MB. Calls belong on an IO dispatcher, and [unpack] refuses to start
 * without the headroom for every copy involved.
 */
internal object ShellLibraryUnpacker {

    private const val WORK_DIRECTORY = "senbei_work"

    /**
     * Protected copy + unpacked image + the engine's stage-2 modules, plus slack for the filesystem
     * bookkeeping that happens while both copies exist.
     */
    private const val REQUIRED_BYTES = 700L * 1024 * 1024

    private const val COPY_BUFFER_BYTES = 256 * 1024

    sealed interface Outcome {
        /** [libraries] are the names that landed in the destination folder. */
        data class Unpacked(val libraries: List<String>) : Outcome

        /** The archive held no library we could use, or every entry failed. */
        data object NothingUnpacked : Outcome

        data object NotEnoughSpace : Outcome

        /** No Senbei build for this device, so the user has to supply libraries directly. */
        data object Unavailable : Outcome

        data class Failed(val reason: String) : Outcome
    }

    /**
     * One pass over an archive. [sawLibrary] separates "this archive has no such entries" from
     * "it had them and every one failed", which the UI words differently.
     */
    private class Scan {
        val unpacked = mutableListOf<String>()
        var sawLibrary = false
    }

    /** A split set is a handful of APKs; anything beyond this is not an XAPK we should chew on. */
    private const val MAX_NESTED_SPLITS = 8

    /**
     * Streams [uri] through [unpackable] and writes every library it can into [destination].
     * Touches the filesystem and reads the picked stream: call from an IO dispatcher.
     */
    fun unpack(
        context: Context,
        uri: Uri,
        unpackable: List<String>,
        destination: File,
        /** Entries that are il2cpp metadata rather than a library, restored by value. */
        metadata: Set<String> = emptySet()
    ): Outcome {
        if (!SenbeiNative.isAvailable) return Outcome.Unavailable
        if (unpackable.isEmpty()) return Outcome.NothingUnpacked

        val workDir = File(context.cacheDir, WORK_DIRECTORY)
        return try {
            workDir.deleteRecursively()
            if (!workDir.mkdirs()) {
                return Outcome.Failed("the unpack workspace could not be created")
            }
            if (!hasHeadroom(workDir)) return Outcome.NotEnoughSpace
            if (!destination.exists() && !destination.mkdirs()) {
                return Outcome.Failed("the library directory could not be created")
            }

            val scan = Scan()
            context.contentResolver.openInputStream(uri)?.use { raw ->
                ZipInputStream(BufferedInputStream(raw)).use { zip ->
                    populate(zip, unpackable, metadata, workDir, destination, scan)
                }
            } ?: return Outcome.Failed("the picked file could not be read")

            // An XAPK wraps the split APKs instead of carrying their contents, so its libraries
            // are one archive deeper. Only looked at when the top level had nothing to offer.
            if (scan.unpacked.isEmpty() && !scan.sawLibrary) {
                unpackNested(context, uri, unpackable, metadata, workDir, destination, scan)
            }

            when {
                scan.unpacked.isNotEmpty() -> Outcome.Unpacked(scan.unpacked.toList())
                scan.sawLibrary -> Outcome.Failed("no library could be unpacked")
                else -> Outcome.NothingUnpacked
            }
        } catch (error: Exception) {
            Outcome.Failed(error.message ?: error.javaClass.simpleName)
        } finally {
            workDir.deleteRecursively()
        }
    }

    /**
     * Copies every entry named in [unpackable] out of [zip]. A second copy of the same library in
     * one archive is ignored on purpose: only the first one is probed, which keeps the cost bound.
     */
    private fun populate(
        zip: ZipInputStream,
        unpackable: List<String>,
        metadata: Set<String>,
        workDir: File,
        destination: File,
        scan: Scan
    ) {
        while (true) {
            val entry = zip.nextEntry ?: break
            val name = entry.name.substringAfterLast('/')
            if (!entry.isDirectory && name in unpackable && name !in scan.unpacked) {
                scan.sawLibrary = true
                if (importOne(zip, name, workDir, destination, name in metadata)) {
                    scan.unpacked += name
                }
            }
            zip.closeEntry()
        }
    }

    /**
     * Reads the APKs inside an XAPK as if the user had picked them directly.
     *
     * Each inner split is streamed to the workspace, scanned, and deleted before the next one is
     * read, so two copies of a few hundred megabytes never sit side by side.
     */
    private fun unpackNested(
        context: Context,
        uri: Uri,
        unpackable: List<String>,
        metadata: Set<String>,
        workDir: File,
        destination: File,
        scan: Scan
    ) {
        var index = 0
        context.contentResolver.openInputStream(uri)?.use { raw ->
            ZipInputStream(BufferedInputStream(raw)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name.substringAfterLast('/')
                    if (!entry.isDirectory &&
                        name.endsWith(".apk", ignoreCase = true) &&
                        index < MAX_NESTED_SPLITS
                    ) {
                        val split = File(workDir, "split_${index++}.apk")
                        split.outputStream().use { out -> zip.copyTo(out, COPY_BUFFER_BYTES) }
                        if (split.length() > 0L) {
                            ZipInputStream(BufferedInputStream(split.inputStream())).use { inner ->
                                populate(inner, unpackable, metadata, workDir, destination, scan)
                            }
                        }
                        split.delete()
                    }
                    zip.closeEntry()
                }
            }
        }
    }

    /**
     * Streams one archive entry to the workspace and unpacks it if it is protected. The protected
     * copy is deleted before the next entry is read, so two large libraries never sit side by side.
     *
     * A metadata entry is deobfuscated instead of being probed for a native wrapper payload: it is not
     * an ELF at all, and handing the obfuscated copy on would install a build that dies in
     * `il2cpp_init`.
     */
    private fun importOne(
        zip: ZipInputStream,
        name: String,
        workDir: File,
        destDir: File,
        isMetadata: Boolean
    ): Boolean {
        val staged = File(workDir, name)
        staged.outputStream().use { out -> zip.copyTo(out, COPY_BUFFER_BYTES) }

        val candidate = if (isMetadata) {
            val restored = File(workDir, "$name.restored")
            val report = SenbeiNative.restoreMetadata(staged.path, restored.path)
            staged.delete()
            if (!report.ok) {
                restored.delete()
                return false
            }
            restored
        } else if (SenbeiNative.isProtectedLibrary(staged.path)) {
            val unpacked = File(workDir, "$name.unpacked")
            val report = SenbeiNative.unpackLibrary(
                staged.path,
                unpacked.path,
                File(workDir, "scratch").path
            )
            staged.delete()
            if (!report.ok) {
                unpacked.delete()
                return false
            }
            unpacked
        } else {
            staged
        }

        // renameTo is atomic within the same volume, and both directories live in app storage.
        val destination = File(destDir, name)
        destination.delete()
        if (candidate.renameTo(destination)) return true
        candidate.delete()
        return false
    }

    private fun hasHeadroom(dir: File): Boolean = runCatching {
        StatFs(dir.path).availableBytes >= REQUIRED_BYTES
    }.getOrDefault(false)
}
