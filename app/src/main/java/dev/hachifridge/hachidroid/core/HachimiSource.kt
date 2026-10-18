package dev.hachifridge.hachidroid.core

import android.content.Context
import androidx.datastore.preferences.core.edit
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import dev.hachifridge.hachidroid.utils.fetchJson

/**
 * Tracen's mod runtime, `libmain-arm64-v8a.so`, from the Hachimi releases.
 *
 * The patcher fetches this lazily as part of a run. This object is the same fetch on demand, so a
 * stale library can be replaced from the Mod Source sheet without starting a patch first.
 */
object HachimiSource {

    const val MOD_LIB_NAME = "libmain-arm64-v8a.so"

    /** Published next to the library; the download is only accepted when the two agree. */
    private const val CHECKSUM_ASSET = "sha256.json"

    private const val CONNECT_TIMEOUT_MS = 20_000
    private const val READ_TIMEOUT_MS = 120_000

    /** [version] is the release tag, so the sheet can tell what is cached and what arrived. */
    sealed interface Outcome {
        data class Installed(val version: String) : Outcome

        /** The cached library is already the newest release, so nothing was downloaded. */
        data class AlreadyLatest(val version: String) : Outcome

        /** No repository is configured, so there is nothing to download from. */
        data object RepoMissing : Outcome

        /** The release exists but carries no mod library. */
        data object AssetMissing : Outcome

        /** The release has no checksum file, so the download could not be verified. */
        data object ChecksumMissing : Outcome

        /** The bytes did not match the published checksum. */
        data object ChecksumMismatch : Outcome

        data class Failed(val reason: String?) : Outcome
    }

    /** Where the patcher expects the library; it replaces this file wholesale after a download. */
    fun target(context: Context): File =
        context.filesDir.resolve("libs").resolve(MOD_LIB_NAME)

    /**
     * Fetches the newest release asset. Network-bound, and [withContext] keeps that off the main
     * thread. Codeberg mirrors the same repository layout, and is tried when GitHub refuses.
     */
    suspend fun download(context: Context): Outcome = withContext(Dispatchers.IO) {
        val repo = (context.getPrefValue(PrefKey.HACHIMI_REPO) as String).trim().trim('/')
        if (repo.isEmpty()) return@withContext Outcome.RepoMissing

        val latest = try {
            GitHubReleases(repo).fetchLatest()
        } catch (ex: Exception) {
            try {
                GitHubReleases(repo, baseUrl = "https://codeberg.org/api/v1/repos").fetchLatest()
            } catch (fallback: Exception) {
                Log.w("HachimiSource", "Release lookup failed for $repo", fallback)
                return@withContext Outcome.Failed(fallback.message ?: ex.message)
            }
        }

        val version = latest["tag_name"] as? String ?: ""
        val target = target(context)
        val currentVersion = context.getPrefValue(PrefKey.HACHIMI_LIBS_VERSION) as String
        if (version.isNotEmpty() && version == currentVersion && target.isFile) {
            return@withContext Outcome.AlreadyLatest(version)
        }

        val assets = (latest["assets"] as? List<*>)?.filterIsInstance<Map<*, *>>().orEmpty()
        fun urlOf(name: String): String? = assets.firstNotNullOfOrNull { asset ->
            if (asset["name"] == name) asset["browser_download_url"] as? String else null
        }

        val libUrl = urlOf(MOD_LIB_NAME) ?: return@withContext Outcome.AssetMissing
        val checksumUrl = urlOf(CHECKSUM_ASSET) ?: return@withContext Outcome.ChecksumMissing

        val expected = try {
            (fetchJson(URL(checksumUrl))[MOD_LIB_NAME] as? String)?.lowercase()
                ?: return@withContext Outcome.ChecksumMismatch
        } catch (ex: Exception) {
            Log.w("HachimiSource", "Checksum fetch failed", ex)
            return@withContext Outcome.Failed(ex.message)
        }

        try {
            // Same-directory staging keeps the final step a rename, so a failure never leaves a
            // half-written library where the patcher would pick it up.
            target.parentFile?.mkdirs()
            val temp = File(target.parentFile, "${target.name}.part")
            val digest = downloadTo(libUrl, temp)
            if (digest != expected) {
                temp.delete()
                Log.w("HachimiSource", "Checksum mismatch: $digest != $expected")
                return@withContext Outcome.ChecksumMismatch
            }
            if (target.exists() && !target.delete()) {
                temp.delete()
                return@withContext Outcome.Failed("Could not replace the existing library")
            }
            if (!temp.renameTo(target)) {
                temp.delete()
                return@withContext Outcome.Failed("Could not move the library into place")
            }
            context.dataStore.edit { it[PrefKey.HACHIMI_LIBS_VERSION] = version }
            Outcome.Installed(version)
        } catch (ex: Exception) {
            Log.w("HachimiSource", "Mod library download failed", ex)
            Outcome.Failed(ex.message)
        }
    }

    /** Copies the body to [target] and returns the lowercase hex SHA-256 of what was written. */
    private fun downloadTo(url: String, target: File): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/octet-stream")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            val digest = MessageDigest.getInstance("SHA-256")
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                    }
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        } finally {
            connection.disconnect()
        }
    }
}
