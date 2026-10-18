package dev.hachifridge.hachidroid.core.senbei

import android.content.Context
import android.net.Uri
import android.os.StatFs
import dev.hachifridge.hachidroid.core.AstraeaLibraries
import java.io.BufferedInputStream
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Produces the libraries a patched Priconne install needs, on the device itself.
 *
 * "Unpacked", not "restored": the CrackProof shell never handed over a usable library, so nothing
 * is being put back. The engine unpacks the protected image into a loadable one, and the wording
 * everywhere in the app follows that.
 *
 * The input is whatever the user can actually get hold of: the game's `config.arm64_v8a` split, or
 * any archive that happens to contain the two protected libraries. Each one is streamed out of the
 * archive, probed, and — only if it really carries a CrackProof payload — handed to Senbei. An
 * entry that is already an unpacked library (a community build, a previous unpack) is accepted
 * as-is rather than being rejected: the probe is the only thing that distinguishes them, and users
 * routinely have one or the other.
 *
 * Everything here is disk-bound and memory-heavy: the protected copy alone is ~300 MB and the
 * unpacked image ~210 MB. Calls belong on an IO dispatcher, and [unpack] refuses to start without
 * the headroom for both.
 */
internal object AstraeaUnpacker {

    private const val WORK_DIRECTORY = "senbei_work"

    /** The two libraries CrackProof protects. The mod library is not part of unpacking. */
    private val UNPACKABLE = listOf("libil2cpp.so", "libnative.so")

    /**
     * Protected copy + unpacked image + the engine's stage-2 modules, plus slack for the filesystem
     * bookkeeping that happens while both copies exist.
     */
    private const val REQUIRED_BYTES = 700L * 1024 * 1024

    private const val COPY_BUFFER_BYTES = 256 * 1024

    sealed interface Outcome {
        /** [libraries] are the names that landed in [AstraeaLibraries.directory]. */
        data class Unpacked(val libraries: List<String>) : Outcome

        /** The archive held no library we could use, or every entry failed. */
        data object NothingUnpacked : Outcome

        data object NotEnoughSpace : Outcome

        /** No Senbei build for this device, so the user has to supply libraries directly. */
        data object Unavailable : Outcome

        data class Failed(val reason: String) : Outcome
    }

    fun unpack(context: Context, uri: Uri): Outcome {
        if (!SenbeiNative.isAvailable) return Outcome.Unavailable

        val workDir = File(context.cacheDir, WORK_DIRECTORY)
        val destDir = AstraeaLibraries.directory(context)
        return try {
            workDir.deleteRecursively()
            if (!workDir.mkdirs()) {
                return Outcome.Failed("the unpack workspace could not be created")
            }
            if (!hasHeadroom(workDir)) return Outcome.NotEnoughSpace
            if (!destDir.exists() && !destDir.mkdirs()) {
                return Outcome.Failed("the library directory could not be created")
            }

            val unpacked = mutableListOf<String>()
            var sawLibrary = false

            context.contentResolver.openInputStream(uri)?.use { raw ->
                ZipInputStream(BufferedInputStream(raw)).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val name = entry.name.substringAfterLast('/')
                        if (!entry.isDirectory && name in UNPACKABLE && name !in unpacked) {
                            // A second copy of the same library in one archive is ignored on
                            // purpose: only the first one is probed, which keeps the cost bound.
                            sawLibrary = true
                            if (importOne(zip, name, workDir, destDir)) unpacked += name
                        }
                        zip.closeEntry()
                    }
                }
            } ?: return Outcome.Failed("the picked file could not be read")

            if (unpacked.isEmpty()) {
                if (sawLibrary) Outcome.Failed("no library could be unpacked") else Outcome.NothingUnpacked
            } else {
                Outcome.Unpacked(unpacked)
            }
        } catch (error: Exception) {
            Outcome.Failed(error.message ?: error.javaClass.simpleName)
        } finally {
            workDir.deleteRecursively()
        }
    }

    /**
     * Streams one archive entry to the workspace and unpacks it if it is protected. The protected
     * copy is deleted before the next entry is read, so two large libraries never sit side by side.
     */
    private fun importOne(zip: ZipInputStream, name: String, workDir: File, destDir: File): Boolean {
        val staged = File(workDir, name)
        staged.outputStream().use { out -> zip.copyTo(out, COPY_BUFFER_BYTES) }

        val candidate = if (SenbeiNative.isProtectedLibrary(staged.path)) {
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
