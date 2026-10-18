package dev.hachifridge.hachidroid.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdaterTest {

    // ------------------------------------------------------- parseSha256Sums

    @Test
    fun parseSha256Sums_parsesStandardOutput() {
        val text = buildString {
            appendLine("${"a".repeat(64)}  app-release.apk")
            appendLine("${"B".repeat(64)}  other-file.txt")
        }

        val parsed = AppUpdater.parseSha256Sums(text)

        assertEquals(2, parsed.size)
        assertEquals("a".repeat(64), parsed["app-release.apk"])
        assertEquals("B".repeat(64), parsed["other-file.txt"])
    }

    @Test
    fun parseSha256Sums_handlesBinaryModeMarkerAndSkipsGarbage() {
        val text = buildString {
            appendLine("${"0".repeat(64)} *binary-mode.apk")
            appendLine("not a checksum line")
            appendLine("")
            append("   ")
        }

        val parsed = AppUpdater.parseSha256Sums(text)

        assertEquals(1, parsed.size)
        assertEquals("0".repeat(64), parsed["binary-mode.apk"])
    }

    @Test
    fun parseSha256Sums_rejectsShortHashes() {
        val parsed = AppUpdater.parseSha256Sums("abcd1234  app-release.apk")
        assertTrue(parsed.isEmpty())
    }

    @Test
    fun parseSha256Sums_missingFileIsNull() {
        assertNull(AppUpdater.parseSha256Sums("${"a".repeat(64)}  something-else.apk")["app-release.apk"])
    }

    // -------------------------------------------------------- normalizeDigest

    @Test
    fun normalizeDigest_stripsGithubPrefix() {
        assertEquals("abc123", AppUpdater.normalizeDigest("sha256:abc123"))
        assertEquals("abc123", AppUpdater.normalizeDigest("  sha256:abc123  "))
        assertEquals("abc123", AppUpdater.normalizeDigest("abc123"))
    }

    // ------------------------------------------------------ findReleaseAssets

    private fun asset(
        name: String,
        url: String = "https://example.com/$name",
        size: Any? = 100,
        digest: Any? = null
    ): HashMap<String, Any?> = hashMapOf(
        "name" to name,
        "browser_download_url" to url,
        "size" to size,
        "digest" to digest
    )

    @Test
    fun findReleaseAssets_picksReleaseApkAndChecksumFile() {
        val release = hashMapOf<String, Any?>(
            "assets" to arrayListOf(
                asset("app-debug.apk"),
                asset(
                    "app-release.apk",
                    url = "https://example.com/app-release.apk",
                    size = 6_666_836,
                    digest = "sha256:deadbeef"
                ),
                asset("SHA256SUMS.txt")
            )
        )

        val assets = GitHubReleases("owner/repo").findReleaseAssets(release)

        val apk = assets.apk
        assertTrue(apk != null)
        assertEquals("app-release.apk", apk!!.name)
        assertEquals("https://example.com/app-release.apk", apk.downloadUrl)
        assertEquals(6_666_836L, apk.size)
        assertEquals("sha256:deadbeef", apk.digest)
        assertEquals("https://example.com/SHA256SUMS.txt", assets.checksumUrl)
    }

    @Test
    fun findReleaseAssets_singleApkIsPickedRegardlessOfName() {
        val release = hashMapOf<String, Any?>(
            "assets" to arrayListOf(asset("HachiDroid-1.2.3.apk", size = 42))
        )

        val assets = GitHubReleases("owner/repo").findReleaseAssets(release)

        assertEquals("HachiDroid-1.2.3.apk", assets.apk?.name)
        assertEquals(42L, assets.apk?.size)
        assertNull(assets.apk?.digest)
        assertNull(assets.checksumUrl)
    }

    @Test
    fun findReleaseAssets_handlesMissingOrMalformedAssets() {
        val github = GitHubReleases("owner/repo")

        assertEquals(
            ReleaseAssets(null, null),
            github.findReleaseAssets(hashMapOf<String, Any?>())
        )
        assertEquals(
            ReleaseAssets(null, null),
            github.findReleaseAssets(hashMapOf<String, Any?>("assets" to "not-a-list"))
        )
        assertEquals(
            ReleaseAssets(null, null),
            github.findReleaseAssets(
                hashMapOf<String, Any?>("assets" to arrayListOf("not-a-map"))
            )
        )
        // APK without a download URL is skipped.
        assertEquals(
            ReleaseAssets(null, null),
            github.findReleaseAssets(
                hashMapOf(
                    "assets" to arrayListOf(hashMapOf<String, Any?>("name" to "app.apk"))
                )
            )
        )
    }
}
