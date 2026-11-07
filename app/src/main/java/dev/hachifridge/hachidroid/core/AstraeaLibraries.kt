package dev.hachifridge.hachidroid.core

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Readiness of the three native libraries a patched Priconne install needs.
 *
 * They are kept in the app's private storage so the patcher never touches the game's own data
 * directory: `libil2cpp.so` and `libnative.so` are unpacked from the CrackProof-protected
 * originals, and `libdaringtact.so` is the mod itself.
 */
object AstraeaLibraries {

    /** Legacy on-disk name, kept so libraries unpacked by earlier builds stay found. */
    const val DIRECTORY_NAME = "priconne_libs"

    /** The mod itself. Unlike the other two it is never produced by unpacking. */
    const val MOD_LIBRARY = "libdaringtact.so"

    /**
     * The two libraries that only exist inside the game: they are either unpacked on the device
     * or supplied by the user. The mod is the third library, but it has its own source in the
     * studio, so library handling never asks for it.
     */
    val PROTECTED_LIBS = listOf("libil2cpp.so", "libnative.so")

    /** Everything a patch writes, mod included. */
    val requiredLibs = PROTECTED_LIBS + MOD_LIBRARY

    /**
     * Program headers sit at the front of an ELF, so a prefix is enough to read the load
     * alignment. Reading whole files would mean a few hundred megabytes of `libil2cpp.so`.
     */
    private const val HEADER_PROBE_BYTES = 32 * 1024

    data class LibStatus(
        val name: String,
        val present: Boolean,
        val sizeBytes: Long,
        /** null when the file is missing or not an ELF whose headers can be read. */
        val is16KbCompatible: Boolean?
    )

    data class Status(val libs: List<LibStatus>) {
        val total: Int get() = libs.size
        val presentCount: Int get() = libs.count { it.present }
        val missingCount: Int get() = total - presentCount

        fun isPresent(name: String): Boolean = libs.any { it.name == name && it.present }

        /** The two game-side libraries are in place, whatever the mod source says. */
        val protectedReady: Boolean get() = PROTECTED_LIBS.all { isPresent(it) }
        val isReady: Boolean get() = total > 0 && presentCount == total
        val hasMisaligned: Boolean get() = libs.any { it.present && it.is16KbCompatible == false }

        companion object {
            val EMPTY = Status(requiredLibs.map { LibStatus(it, false, 0L, null) })
        }
    }

    fun directory(context: Context): File = context.filesDir.resolve(DIRECTORY_NAME)

    /**
     * Deletes one library from private storage, so it can be unpacked or imported again after a
     * game update replaces it. Touches the filesystem; call from a background dispatcher.
     */
    fun remove(context: Context, name: String): Boolean = runCatching {
        val file = directory(context).resolve(name)
        !file.exists() || file.delete()
    }.getOrDefault(false)

    /** Touches the filesystem; call from a background dispatcher. */
    fun inspect(context: Context): Status {
        val dir = directory(context)
        return Status(
            requiredLibs.map { name ->
                val file = dir.resolve(name)
                if (file.isFile && file.length() > 0L) {
                    LibStatus(name, present = true, sizeBytes = file.length(), is16KbCompatible = alignmentOf(file))
                } else {
                    LibStatus(name, present = false, sizeBytes = 0L, is16KbCompatible = null)
                }
            }
        )
    }

    private fun alignmentOf(file: File): Boolean? =
        NativeLibAlignment.inspect(file)?.is16KbCompatible

    /**
     * What one import pass produced. [skipped] keeps the picked names that carried nothing we
     * recognise, so the UI can say what it ignored instead of failing without a reason.
     */
    data class ImportReport(val imported: List<String>, val skipped: List<String>) {
        val isEmpty: Boolean get() = imported.isEmpty()
    }

    /**
     * Copies libraries the user picked into private storage. A pick may be a `.so` file chosen
     * on its own or an archive (the config split APK, or a zip of the extracted libs); entries
     * are matched by file name, so the entry's directory inside the archive does not matter.
     *
     * Touches the filesystem and reads the picked streams: call from a background dispatcher.
     */
    fun import(context: Context, uris: List<Uri>): ImportReport {
        val dir = directory(context)
        if (!dir.exists() && !dir.mkdirs()) {
            return ImportReport(emptyList(), uris.map { displayNameOf(context, it) })
        }

        val imported = LinkedHashSet<String>()
        val skipped = mutableListOf<String>()

        for (uri in uris) {
            val name = displayNameOf(context, uri)
            if (name in requiredLibs) {
                val copied = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { writeLibrary(dir.resolve(name), it) }
                }.getOrNull() == true
                if (copied) imported += name else skipped += name
                continue
            }

            // Anything else is treated as an archive; a wrong guess simply yields no libraries.
            val found = runCatching {
                extractLibraries(context, uri, dir) { libName -> libName !in imported }
            }.getOrDefault(emptyList())
            imported += found
            if (found.isEmpty()) skipped += name
        }

        return ImportReport(imported.toList(), skipped)
    }

    /**
     * Walks [uri] as a zip and copies every entry named like a required library. `libil2cpp.so`
     * is hundreds of megabytes, so entries are streamed to the destination instead of buffered.
     */
    private fun extractLibraries(
        context: Context,
        uri: Uri,
        dir: File,
        accept: (String) -> Boolean
    ): List<String> {
        val found = mutableListOf<String>()
        context.contentResolver.openInputStream(uri)?.use { raw ->
            ZipInputStream(BufferedInputStream(raw)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val baseName = entry.name.substringAfterLast('/')
                    if (!entry.isDirectory && baseName in requiredLibs && accept(baseName) && baseName !in found) {
                        if (writeLibrary(dir.resolve(baseName), zip)) found += baseName
                    }
                    zip.closeEntry()
                }
            }
        }
        return found
    }

    /**
     * Streams [input] into [dest] through a temporary file, so an interrupted import never
     * leaves a half-written library that would pass the presence check. The ELF magic is
     * checked first because a renamed file is otherwise indistinguishable from a real one.
     * [input] is left open for the caller.
     */
    private fun writeLibrary(dest: File, input: InputStream): Boolean {
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
                if (!isElf(magic)) return@use false
                out.write(magic)
                input.copyTo(out, COPY_BUFFER_BYTES)
                out.fd.sync()
                true
            }
        }.getOrDefault(false)

        if (!copied) {
            temp.delete()
            return false
        }
        dest.delete()
        if (temp.renameTo(dest)) return true
        temp.delete()
        return false
    }

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

    private const val COPY_BUFFER_BYTES = 512 * 1024
}
