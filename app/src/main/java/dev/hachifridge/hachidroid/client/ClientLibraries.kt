package dev.hachifridge.hachidroid.client

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dev.hachifridge.hachidroid.core.NativeLibAlignment
import dev.hachifridge.hachidroid.core.senbei.SenbeiNative
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * One client's native-library folder in the app's private storage.
 *
 * Each client keeps the libraries a patch writes here rather than touching the game's own data
 * directory. [gameLibraries] only exist inside the game and are restored by unpacking the
 * protected originals or supplied by the user; [modLibrary] is the client's own mod runtime.
 *
 * Handling is shared: inspect, import and remove are the same workflow whichever client owns the
 * folder, so a change made for one is a change for all.
 */
class LibrarySet(
    /** Legacy on-disk names, kept so libraries unpacked by earlier builds stay found. */
    val directoryName: String,
    /** Restored libraries a patch of this client always writes. */
    val gameLibraries: List<String>,
    /**
     * Restored libraries only a packer-protected build needs. One client can ship both shapes —
     * Uma's Global build is packed and Japan's is not — so these count only when
     * [ShellDetection] says the picked build carries the shell.
     */
    val protectedGameLibraries: List<String>,
    val modLibrary: String,
    /**
     * Game-side files a packed build needs that are not libraries at all.
     *
     * Uma's Global build obfuscates its il2cpp `global-metadata.dat` as well as its image, so the
     * restored copy has to be written into the APK beside the restored `.so`. They are stored and
     * listed with the libraries — the same unpack, import and remove workflow applies — but the
     * patch writes them somewhere else, which is the only thing [LibrarySet] does not decide.
     */
    val protectedGameAssets: List<String> = emptyList(),
    /** Directory where the shared mod runtime binary is kept. Defaults to [directoryName]. */
    val modDirectoryName: String = directoryName
) {

    /** Every name the folder may hold; used for import matching, not for readiness. */
    val knownLibraries: List<String>
        get() = (gameLibraries + protectedGameLibraries + protectedGameAssets + modLibrary).distinct()

    /** Names that exist only in a packer-protected build; nothing else ever provides them. */
    val protectedNames: List<String>
        get() = (protectedGameLibraries + protectedGameAssets).distinct()

    /**
     * Whether [name] may be stored for a build of this shape.
     *
     * The folder is shared by every build of a client — Uma's Japan and Global releases are one
     * client — so a restored image or deobfuscated metadata imported while a plain build is the
     * target would later be handed to the other release's patch. Those names are therefore only
     * accepted for a protected build; the mod runtime and a plain build's own libraries are not.
     */
    fun accepts(name: String, shellProtected: Boolean): Boolean =
        name in knownLibraries && (shellProtected || name !in protectedNames)

    /** The game-side libraries a patch of this build restores. */
    fun gameLibrariesFor(shellProtected: Boolean): List<String> =
        if (shellProtected) gameLibraries + protectedGameLibraries else gameLibraries

    /** Every game-side file a patch of this build restores, non-library assets included. */
    fun gameFilesFor(shellProtected: Boolean): List<String> =
        if (shellProtected) gameLibrariesFor(true) + protectedGameAssets else gameLibraries

    /** Everything a patch of this build needs, mod included. */
    fun requiredLibraries(shellProtected: Boolean): List<String> =
        gameFilesFor(shellProtected) + modLibrary

    /**
     * Program headers sit at the front of an ELF, so a prefix is enough to read the load
     * alignment. Reading whole files would mean a few hundred megabytes of `libil2cpp.so`.
     */
    private val headerProbeBytes = 32 * 1024

    data class LibStatus(
        val name: String,
        val present: Boolean,
        val sizeBytes: Long,
        /** null when the file is missing or not an ELF whose headers can be read. */
        val is16KbCompatible: Boolean?
    )

    data class Status(
        val libs: List<LibStatus>,
        /** The game-side names this status was built for, so readiness needs no second lookup. */
        val gameLibraries: List<String>,
        /** Whether the build this status describes is packer-protected. */
        val shellProtected: Boolean = false,
        /** The build the stored restored files came from; null when nothing recorded it. */
        val artifactsStamp: String? = null,
        /** The build being patched; null when its manifest could not be read. */
        val targetStamp: String? = null
    ) {
        val total: Int get() = libs.size
        val presentCount: Int get() = libs.count { it.present }
        val missingCount: Int get() = total - presentCount

        fun isPresent(name: String): Boolean = libs.any { it.name == name && it.present }

        /** The game-side libraries are in place, whatever the mod source says. */
        val gameLibrariesReady: Boolean get() = gameLibraries.all { isPresent(it) }
        val isReady: Boolean get() = total > 0 && presentCount == total
        val hasMisaligned: Boolean get() = libs.any { it.present && it.is16KbCompatible == false }

        val formattedArtifactsStamp: String get() = formatDisplayStamp(artifactsStamp)
        val formattedTargetStamp: String get() = formatDisplayStamp(targetStamp)

        /**
         * Restored files that belong to another game version.
         *
         * Only meaningful for a packed build: a plain one ships its own libraries, so leftovers
         * from the other release are never written and must not raise a warning.
         */
        val artifactsStale: Boolean
            get() = shellProtected && artifactsStamp != null &&
                targetStamp != null && !stampsMatch(artifactsStamp, targetStamp)
    }

    /** The checklist before anything has been inspected. */
    fun emptyStatus(shellProtected: Boolean): Status = Status(
        libs = requiredLibraries(shellProtected).map { LibStatus(it, false, 0L, null) },
        gameLibraries = gameFilesFor(shellProtected),
        shellProtected = shellProtected
    )

    fun directory(context: Context): File {
        val dir = context.filesDir.resolve(directoryName)
        // Migration for Global profile: if libs_glb does not exist or has no restored artifacts,
        // copy any existing restored artifacts from the legacy "libs" folder.
        if (directoryName == "libs_glb" && (!dir.exists() || dir.list().isNullOrEmpty())) {
            val legacy = context.filesDir.resolve("libs")
            if (legacy.isDirectory) {
                dir.mkdirs()
                listOf("libil2cpp.so", "global-metadata.dat", "build.stamp").forEach { name ->
                    val file = legacy.resolve(name)
                    if (file.isFile) file.copyTo(dir.resolve(name), overwrite = true)
                }
            }
        }
        return dir
    }

    fun modDirectory(context: Context): File = context.filesDir.resolve(modDirectoryName)

    /**
     * The game version the restored files were unpacked or imported from.
     *
     * A restored image and its metadata only match the build they came from, so the version is
     * recorded beside them and compared with the build being patched. Null means nothing is known,
     * which reads as "cannot tell", not "fresh".
     */
    fun readStamp(context: Context): String? = runCatching {
        directory(context).resolve(STAMP_FILE)
            .takeIf { it.isFile }
            ?.readText()
            ?.trim()
            ?.removeSuffix("/null")
            ?.removeSuffix("/")
            ?.ifEmpty { null }
    }.getOrNull()

    /** Touches the filesystem; call from a background dispatcher. A null [stamp] forgets it. */
    fun writeStamp(context: Context, stamp: String?) {
        val file = directory(context).resolve(STAMP_FILE)
        if (stamp == null) {
            file.delete()
            return
        }
        val clean = stamp.trim().removeSuffix("/null").removeSuffix("/")
        file.parentFile?.mkdirs()
        runCatching { file.writeText(clean) }
    }

    /** Whether the folder's restored files were produced by a different build than [targetStamp]. */
    fun isStale(context: Context, targetStamp: String?): Boolean {
        val stored = readStamp(context) ?: return false
        return targetStamp != null && !stampsMatch(stored, targetStamp)
    }

    /**
     * Deletes one library from private storage, so it can be unpacked or imported again after a
     * game update replaces it. Touches the filesystem; call from a background dispatcher.
     */
    fun remove(context: Context, name: String): Boolean = runCatching {
        val targetDir = if (name == modLibrary) modDirectory(context) else directory(context)
        val file = targetDir.resolve(name)
        !file.exists() || file.delete()
    }.getOrDefault(false)

    /** Touches the filesystem; call from a background dispatcher. */
    fun inspect(
        context: Context,
        shellProtected: Boolean,
        targetStamp: String? = null
    ): Status {
        val dir = directory(context)
        val modDir = modDirectory(context)
        return Status(
            libs = requiredLibraries(shellProtected).map { name ->
                val file = if (name == modLibrary) {
                    val primary = modDir.resolve(name)
                    if (primary.isFile && primary.length() > 0L) primary else dir.resolve(name)
                } else {
                    dir.resolve(name)
                }
                if (file.isFile && file.length() > 0L) {
                    LibStatus(name, present = true, sizeBytes = file.length(), is16KbCompatible = alignmentOf(name, file))
                } else {
                    LibStatus(name, present = false, sizeBytes = 0L, is16KbCompatible = null)
                }
            },
            gameLibraries = gameFilesFor(shellProtected),
            shellProtected = shellProtected,
            artifactsStamp = readStamp(context),
            targetStamp = targetStamp
        )
    }

    /**
     * Only a shared object has `PT_LOAD` segments to check; a game asset such as the il2cpp
     * metadata is not an ELF, so there is no alignment to report for it.
     */
    private fun alignmentOf(name: String, file: File): Boolean? =
        if (!isLibraryName(name)) null else NativeLibAlignment.inspect(file)?.is16KbCompatible

    /**
     * What one import pass produced. [skipped] keeps the picked names that carried nothing we
     * recognise, so the UI can say what it ignored instead of failing without a reason.
     * [stillPacked] are the ones the packer still holds: they cannot be restored by copying, and
     * saying so is the whole point of the check.
     */
    data class ImportReport(
        val imported: List<String>,
        val skipped: List<String>,
        val stillPacked: List<String> = emptyList()
    ) {
        val isEmpty: Boolean get() = imported.isEmpty()
    }

    /**
     * Copies libraries the user picked into private storage. A pick may be a `.so` file chosen
     * on its own or an archive (a config split APK, or a zip of the extracted libs); entries
     * are matched by file name, so the entry's directory inside the archive does not matter.
     *
     * A name only a packed build has is ignored when [shellProtected] is false, so picking the
     * other release's archive cannot seed this build with its artifacts.
     *
     * Touches the filesystem and reads the picked streams: call from a background dispatcher.
     */
    fun import(context: Context, uris: List<Uri>, shellProtected: Boolean): ImportReport {
        val dir = directory(context)
        if (!dir.exists() && !dir.mkdirs()) {
            return ImportReport(emptyList(), uris.map { displayNameOf(context, it) })
        }

        val imported = LinkedHashSet<String>()
        val skipped = mutableListOf<String>()
        val stillPacked = mutableListOf<String>()

        for (uri in uris) {
            val name = displayNameOf(context, uri)
            if (accepts(name, shellProtected)) {
                val dest = if (name == modLibrary) modDirectory(context) else dir
                dest.mkdirs()
                val result = runCatching {
                    context.contentResolver.openInputStream(uri)
                        ?.use { writeLibrary(dest.resolve(name), it, probePacked = isRestorable(name)) }
                }.getOrNull() ?: WriteResult.FAILED
                when (result) {
                    WriteResult.WRITTEN -> imported += name
                    WriteResult.STILL_PACKED -> stillPacked += name
                    WriteResult.FAILED -> skipped += name
                }
                continue
            }

            // Anything else is treated as an archive; a wrong guess simply yields no libraries.
            val found = mutableListOf<String>()
            val packed = mutableListOf<String>()
            runCatching {
                extractLibraries(
                    context = context,
                    uri = uri,
                    dir = dir,
                    accept = { libName -> accepts(libName, shellProtected) && libName !in imported },
                    onStillPacked = { libName -> if (libName !in packed) packed += libName }
                )
            }.onSuccess { found += it }
            imported += found
            stillPacked += packed
            if (found.isEmpty() && packed.isEmpty()) skipped += name
        }

        return ImportReport(imported.toList(), skipped, stillPacked.distinct())
    }

    /**
     * Walks [uri] as a zip and copies every entry named like a required library. `libil2cpp.so`
     * is hundreds of megabytes, so entries are streamed to the destination instead of buffered.
     */
    private fun extractLibraries(
        context: Context,
        uri: Uri,
        dir: File,
        accept: (String) -> Boolean,
        onStillPacked: (String) -> Unit
    ): List<String> {
        val found = mutableListOf<String>()
        context.contentResolver.openInputStream(uri)?.use { raw ->
            ZipInputStream(BufferedInputStream(raw)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val baseName = entry.name.substringAfterLast('/')
                    if (!entry.isDirectory && accept(baseName) && baseName !in found) {
                        val dest = if (baseName == modLibrary) modDirectory(context) else dir
                        dest.mkdirs()
                        when (writeLibrary(
                            dest.resolve(baseName),
                            zip,
                            probePacked = isRestorable(baseName)
                        )) {
                            WriteResult.WRITTEN -> found += baseName
                            WriteResult.STILL_PACKED -> onStillPacked(baseName)
                            WriteResult.FAILED -> {}
                        }
                    }
                    zip.closeEntry()
                }
            }
        }
        return found
    }

    /** What one entry's copy produced; a still-encrypted library is not a failed copy. */
    private enum class WriteResult { WRITTEN, STILL_PACKED, FAILED }

    /**
     * A library the Patch Studio writes itself, as opposed to the mod runtime or a plugin.
     *
     * Only these are ever restored, so only these can arrive still carrying the packer's payload.
     */
    private fun isRestorable(name: String): Boolean =
        name.endsWith(".so") && name in knownLibraries && name != modLibrary

    /**
     * Streams [input] into [dest] through a temporary file, so an interrupted import never
     * leaves a half-written library that would pass the presence check. A library's ELF magic is
     * checked first because a renamed file is otherwise indistinguishable from a real one; a game
     * asset has no such header, so its first bytes are simply copied.
     *
     * A restorable `.so` is then probed for the packer: the header survives encryption, so the
     * magic alone accepts the game's encrypted image and would install a build that dies in
     * `il2cpp_init`. Everything written here has to be loadable as-is.
     * [input] is left open for the caller.
     */
    private fun writeLibrary(dest: File, input: InputStream, probePacked: Boolean): WriteResult {
        val temp = File(dest.parentFile, "${dest.name}.part")
        val copied = runCatching {
            FileOutputStream(temp).use { out ->
                val magic = ByteArray(4)
                var read = 0
                while (read < magic.size) {
                    val count = input.read(magic, read, magic.size - read)
                    if (count <= 0) return@use false
                    read += count
                }
                if (isLibraryName(dest.name) && !isElf(magic)) return@use false
                out.write(magic)
                input.copyTo(out, COPY_BUFFER_BYTES)
                out.fd.sync()
                true
            }
        }.getOrDefault(false)

        if (copied == false) {
            temp.delete()
            return WriteResult.FAILED
        }
        if (probePacked && runCatching { SenbeiNative.isProtectedLibrary(temp.path) }.getOrDefault(false)) {
            temp.delete()
            return WriteResult.STILL_PACKED
        }
        dest.delete()
        if (temp.renameTo(dest)) return WriteResult.WRITTEN
        temp.delete()
        return WriteResult.FAILED
    }

    /** A restored library is a `.so`; anything else in the folder is a game asset. */
    private fun isLibraryName(name: String): Boolean = name.endsWith(".so")

    private fun isElf(magic: ByteArray): Boolean =
        magic.size == 4 &&
            magic[0] == 0x7F.toByte() &&
            magic[1] == 'E'.code.toByte() &&
            magic[2] == 'L'.code.toByte() &&
            magic[3] == 'F'.code.toByte()

    private fun displayNameOf(context: Context, uri: Uri): String {
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) return cursor.getString(0) ?: ""
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/').orEmpty()
    }

    private companion object {
        const val COPY_BUFFER_BYTES = 512 * 1024

        /** Records which game version the restored files came from; see [readStamp]. */
        const val STAMP_FILE = "build.stamp"
    }
}

/**
 * Tracen's native libraries: the Hachimi runtime, which is downloaded rather than unpacked, and a
 * restored `libil2cpp.so` for a packer-protected build only.
 *
 * Japan ships the library plain, so nothing is restored there; Global ships it encrypted, so the
 * same client asks for it exactly when [ShellDetection] finds the shell. Global also obfuscates the
 * il2cpp metadata, so a deobfuscated `global-metadata.dat` is restored beside the library and
 * written into the base split — a restored image with the shipped metadata resolves null method
 * infos and dies in `il2cpp_init`.
 */
/**
 * Tracen Global native libraries: packed build needing restored libil2cpp and metadata.
 */
val TracenGlbLibraries = LibrarySet(
    directoryName = "libs_glb",
    gameLibraries = emptyList(),
    protectedGameLibraries = listOf("libil2cpp.so"),
    modLibrary = "libmain-arm64-v8a.so",
    protectedGameAssets = listOf("global-metadata.dat"),
    modDirectoryName = "libs"
)

/**
 * Tracen Japan native libraries: plain build without packer.
 */
val TracenJpLibraries = LibrarySet(
    directoryName = "libs_jp",
    gameLibraries = emptyList(),
    protectedGameLibraries = emptyList(),
    modLibrary = "libmain-arm64-v8a.so",
    modDirectoryName = "libs"
)

/**
 * Tracen Traditional Chinese native libraries: plain build without packer.
 */
val TracenTwLibraries = LibrarySet(
    directoryName = "libs_tw",
    gameLibraries = emptyList(),
    protectedGameLibraries = emptyList(),
    modLibrary = "libmain-arm64-v8a.so",
    modDirectoryName = "libs"
)

/**
 * Tracen Korean native libraries: plain build without packer.
 */
val TracenKrLibraries = LibrarySet(
    directoryName = "libs_kr",
    gameLibraries = emptyList(),
    protectedGameLibraries = emptyList(),
    modLibrary = "libmain-arm64-v8a.so",
    modDirectoryName = "libs"
)

/** Default / legacy Tracen native libraries set for backward compatibility. */
val TracenLibraries = TracenGlbLibraries

/**
 * Astraea's native libraries: the two protected game images (restored by Senbei or imported)
 * and the DaringTact mod. The folder name is the one earlier builds wrote to.
 */
val AstraeaLibraries = LibrarySet(
    directoryName = "priconne_libs",
    gameLibraries = listOf("libil2cpp.so", "libnative.so"),
    protectedGameLibraries = emptyList(),
    modLibrary = "libdaringtact.so"
)

/**
 * Formats an internal version stamp (`versionName/versionCode` or `versionName`) into a clean
 * user-facing string, stripping any trailing `/null` or empty build codes.
 */
fun formatDisplayStamp(stamp: String?): String {
    if (stamp.isNullOrBlank()) return ""
    val parts = stamp.split('/', limit = 2)
    val name = parts[0].trim()
    val code = parts.getOrNull(1)?.trim()?.takeIf { it != "null" && it.isNotEmpty() }
    return if (code != null) "$name ($code)" else name
}

/**
 * Compares two version stamps to determine if they describe the same game build.
 * Normalizes legacy `/null` suffixes and matches on version name and code.
 */
fun stampsMatch(stamp1: String?, stamp2: String?): Boolean {
    if (stamp1 == null || stamp2 == null) return false
    val clean1 = stamp1.trim().removeSuffix("/null").removeSuffix("/")
    val clean2 = stamp2.trim().removeSuffix("/null").removeSuffix("/")
    if (clean1 == clean2) return true

    val parts1 = clean1.split('/', limit = 2)
    val name1 = parts1[0].trim()
    val code1 = parts1.getOrNull(1)?.trim()?.takeIf { it != "null" && it.isNotEmpty() }

    val parts2 = clean2.split('/', limit = 2)
    val name2 = parts2[0].trim()
    val code2 = parts2.getOrNull(1)?.trim()?.takeIf { it != "null" && it.isNotEmpty() }

    if (name1.isNotEmpty() && name1 == name2) {
        if (code1 != null && code2 != null) {
            return code1 == code2
        }
        return true
    }

    return false
}
