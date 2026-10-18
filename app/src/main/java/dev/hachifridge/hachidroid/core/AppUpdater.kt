package dev.hachifridge.hachidroid.core

import android.content.Context
import android.util.Log
import dev.hachifridge.hachidroid.R
import dev.hachifridge.hachidroid.utils.bytesToHex
import dev.hachifridge.hachidroid.utils.downloadFileAndDigestSHA256
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Built-in self-updater: downloads the release APK and verifies it before install.
 * Checks in order: file size, GitHub's per-asset SHA-256 `digest`, then the release's
 * SHA256SUMS.txt (an unusable checksum file fails the update, never a silent skip);
 * older releases fall back to size only. Any failure deletes the partial file.
 */
object AppUpdater {
    private const val TAG = "AppUpdater"

    fun updateDir(context: Context): File = context.cacheDir.resolve("update")

    fun updateFile(context: Context, tagName: String): File =
        updateDir(context).resolve("HachiDroid-${tagName.removePrefix("v")}.apk")

    /**
     * Downloads the APK for [tagName] and verifies it. [onProgress] gets
     * (fraction 0..1, totalBytes), fraction only with a Content-Length.
     * Throws IOException with a user-presentable message on failure.
     */
    fun downloadAndVerify(
        context: Context,
        tagName: String,
        assets: ReleaseAssets,
        onProgress: (Float, Long) -> Unit
    ): File {
        val apk = assets.apk ?: throw IOException(context.getString(R.string.update_no_apk))

        val dir = updateDir(context)
        dir.deleteRecursively()
        dir.mkdirs()
        val dest = updateFile(context, tagName)

        val computedSha = try {
            downloadFileAndDigestSHA256(URL(apk.downloadUrl), dest) { fraction ->
                onProgress(fraction, apk.size)
            }
        } catch (e: Exception) {
            dest.delete()
            val reason = e.message ?: e.javaClass.simpleName
            throw IOException(context.getString(R.string.update_download_failed, reason))
        }

        try {
            if (apk.size > 0 && dest.length() != apk.size) {
                throw IOException(context.getString(R.string.update_verify_failed))
            }
            val expected = apk.digest?.let { normalizeDigest(it) }
                ?: fetchExpectedHash(context, assets.checksumUrl, apk.name)
            if (expected != null && !bytesToHex(computedSha).equals(expected, ignoreCase = true)) {
                throw IOException(context.getString(R.string.update_verify_failed))
            }
        } catch (e: IOException) {
            dest.delete()
            throw e
        }

        Log.i(TAG, "Update $tagName downloaded and verified (${dest.length()} bytes)")
        return dest
    }

    private fun fetchExpectedHash(context: Context, checksumUrl: String?, fileName: String): String? {
        if (checksumUrl == null) return null
        val text = try {
            val conn = URL(checksumUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.doInput = true
            conn.connect()
            if (conn.responseCode != HttpURLConnection.HTTP_OK) throw IOException()
            conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (_: Exception) {
            throw IOException(context.getString(R.string.update_verify_failed))
        }
        return parseSha256Sums(text)[fileName]
            ?: throw IOException(context.getString(R.string.update_verify_failed))
    }

    /** "sha256:ABCD..." -> "ABCD..." (GitHub's digest prefix). */
    internal fun normalizeDigest(digest: String): String =
        digest.trim().removePrefix("sha256:").trim()

    private val SHA256SUMS_LINE = Regex("""([0-9a-fA-F]{64})\s+\*?(.+)""")

    /** Parses `sha256sum` output ("<64 hex>  <name>", optional binary-mode "*") to name -> hash. */
    internal fun parseSha256Sums(text: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (line in text.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val match = SHA256SUMS_LINE.matchEntire(trimmed) ?: continue
            val (hash, name) = match.destructured
            result[name] = hash
        }
        return result
    }
}
