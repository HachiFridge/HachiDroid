package dev.hachifridge.hachidroid.core

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Astraea's mod library, `libdaringtact.so`, from either a GitHub release or a file the user has.
 *
 * Both paths land in the same place as the two unpacked libraries
 * ([AstraeaLibraries.directory]), because the patcher only looks at that folder. Keeping the
 * source choice separate from the library choice is deliberate: the mod is never produced by
 * unpacking, so the studio asks for it once, next to the files it patches.
 */
object DaringTactSource {

    /** What one fetch or import did, in enough detail for the studio to say it. */
    sealed interface Outcome {
        /** Installed; [detail] is the release tag for GitHub, the picked name for a local file. */
        data class Installed(val detail: String) : Outcome

        /** No repository is configured, so there is nothing to download from. */
        data object RepoMissing : Outcome

        /** The release exists but carries no DaringTact library. */
        data object AssetMissing : Outcome

        /** Downloaded or copied fine, but the bytes are not an ELF, so the game could not load it. */
        data object NotAnElf : Outcome

        data class Failed(val reason: String?) : Outcome
    }

    private const val CONNECT_TIMEOUT_MS = 20_000
    private const val READ_TIMEOUT_MS = 120_000

    fun target(context: Context): File =
        AstraeaLibraries.directory(context).resolve(AstraeaLibraries.MOD_LIBRARY)

    /**
     * The library folder only exists once something has been written to it, so every writer
     * creates it first: on a fresh install nothing has unpacked yet, and writing into a missing
     * directory was the failure this covers.
     */
    private fun prepare(context: Context): File {
        AstraeaLibraries.directory(context).mkdirs()
        return target(context)
    }

    /**
     * Fetches the newest DaringTact release asset. Network-bound: call from a background
     * dispatcher through [withContext] at the call site, which [download] already does.
     */
    suspend fun download(context: Context): Outcome = withContext(Dispatchers.IO) {
        val repo = (context.getPrefValue(PrefKey.DARINGTACT_REPO) as String).trim().trim('/')
        if (repo.isEmpty()) return@withContext Outcome.RepoMissing

        val release = try {
            GitHubReleases(repo).fetchLatest()
        } catch (ex: Exception) {
            // A typo in the repo and an offline phone both land here; the reason tells them apart.
            Log.w("DaringTactSource", "Release lookup failed for $repo", ex)
            return@withContext Outcome.Failed(ex.message)
        }

        val version = release["tag_name"] as? String ?: ""
        val assets = (release["assets"] as? List<*>)?.filterIsInstance<Map<*, *>>().orEmpty()
        val url = assets.firstNotNullOfOrNull { asset ->
            val name = asset["name"] as? String ?: return@firstNotNullOfOrNull null
            val isModLibrary = name == AstraeaLibraries.MOD_LIBRARY ||
                (name.endsWith(".so", ignoreCase = true) && name.contains("daringtact", ignoreCase = true))
            if (isModLibrary) asset["browser_download_url"] as? String else null
        } ?: return@withContext Outcome.AssetMissing

        try {
            val target = prepare(context)
            val temp = File(target.parentFile, "${target.name}.part")
            downloadTo(url, temp)
            install(temp, target, version)
        } catch (ex: Exception) {
            Log.w("DaringTactSource", "DaringTact download failed", ex)
            Outcome.Failed(ex.message)
        }
    }

    /** Copies a picked `.so` into the library folder, replacing whatever was there. */
    suspend fun importLocal(context: Context, uri: Uri): Outcome = withContext(Dispatchers.IO) {
        try {
            val target = prepare(context)
            val temp = File(target.parentFile, "${target.name}.part")
            val opened = context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
                true
            } ?: false
            if (!opened) return@withContext Outcome.Failed(null)
            install(temp, target, uri.lastPathSegment ?: AstraeaLibraries.MOD_LIBRARY)
        } catch (ex: Exception) {
            Log.w("DaringTactSource", "Local mod import failed", ex)
            Outcome.Failed(ex.message)
        }
    }

    /**
     * Moves [temp] into place only when it starts with the ELF magic. A stale `.part` from a failed
     * run is dropped either way, so the folder never holds a half-written library.
     */
    private fun install(temp: File, target: File, detail: String): Outcome {
        val isElf = runCatching {
            temp.inputStream().use { stream ->
                val magic = ByteArray(4)
                stream.read(magic) == magic.size &&
                    magic[0] == 0x7F.toByte() && magic[1] == 'E'.code.toByte() &&
                    magic[2] == 'L'.code.toByte() && magic[3] == 'F'.code.toByte()
            }
        }.getOrDefault(false)

        if (!isElf) {
            temp.delete()
            return Outcome.NotAnElf
        }
        if (target.exists() && !target.delete()) {
            temp.delete()
            return Outcome.Failed("Could not replace the existing library")
        }
        return if (temp.renameTo(target)) {
            Outcome.Installed(detail)
        } else {
            temp.delete()
            Outcome.Failed("Could not move the library into place")
        }
    }

    private fun downloadTo(url: String, target: File) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/octet-stream")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            connection.inputStream.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }
    }
}
