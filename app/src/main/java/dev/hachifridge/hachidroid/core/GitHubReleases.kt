package dev.hachifridge.hachidroid.core

import java.net.URL
import dev.hachifridge.hachidroid.utils.fetchJson as utilsFetchJson

/** A downloadable APK attached to a GitHub release. */
data class ApkAsset(
    val name: String,
    val downloadUrl: String,
    /** Expected file size in bytes; <= 0 when GitHub does not report it. */
    val size: Long,
    /** GitHub's per-asset digest ("sha256:<hex>") when provided. */
    val digest: String?
)

/** Download URLs the in-app updater needs from a release. */
data class ReleaseAssets(
    val apk: ApkAsset?,
    /** SHA256SUMS.txt published by the release workflow, when present. */
    val checksumUrl: String?
)

class GitHubReleases(
    private val repoPath: String,
    private val baseUrl: String = "https://api.github.com/repos"
) {
    private val releasesApi get() = URL("$baseUrl/$repoPath/releases")
    private val latestApi   get() = URL("$baseUrl/$repoPath/releases/latest")

    private fun fetchJson(url: URL): HashMap<*, *> =
        utilsFetchJson(url, "application/vnd.github+json")

    fun fetchReleases() = fetchJson(releasesApi)
    fun fetchLatest() = fetchJson(latestApi)

    /**
     * Picks the release APK (preferring "release" in the name) and SHA256SUMS.txt
     * from a release JSON object. Nested objects come back as Map, not HashMap.
     */
    fun findReleaseAssets(release: HashMap<*, *>): ReleaseAssets {
        val assets = release["assets"] as? List<*> ?: return ReleaseAssets(null, null)
        var apk: ApkAsset? = null
        var checksumUrl: String? = null
        for (raw in assets) {
            val asset = raw as? Map<*, *> ?: continue
            val name = asset["name"] as? String ?: continue
            when {
                name.endsWith(".apk", ignoreCase = true) -> {
                    val url = asset["browser_download_url"] as? String ?: continue
                    val preferred = name.contains("release", ignoreCase = true)
                    if (apk == null || (preferred && !apk.name.contains("release", ignoreCase = true))) {
                        apk = ApkAsset(
                            name = name,
                            downloadUrl = url,
                            size = (asset["size"] as? Number)?.toLong() ?: 0L,
                            digest = asset["digest"] as? String
                        )
                    }
                }
                name.equals("SHA256SUMS.txt", ignoreCase = true) -> {
                    checksumUrl = asset["browser_download_url"] as? String ?: checksumUrl
                }
            }
        }
        return ReleaseAssets(apk, checksumUrl)
    }
}
