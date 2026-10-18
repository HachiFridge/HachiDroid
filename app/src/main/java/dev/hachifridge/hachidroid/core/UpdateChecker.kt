package dev.hachifridge.hachidroid.core

import android.content.Context
import android.widget.Toast
import androidx.datastore.preferences.core.edit
import dev.hachifridge.hachidroid.BuildConfig
import dev.hachifridge.hachidroid.R
import dev.hachifridge.hachidroid.utils.showToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object UpdateChecker {
    private const val CHECK_TIMEOUT = 300000 // 5 minutes

    // HachiDroid's own releases, never UmaPatcher-Edge's: don't "update" into another app.
    private val releases = GitHubReleases(BuildConfig.REPO_PATH)
    private val currentVersion = parseVersion(BuildConfig.VERSION_NAME)
    private val scope = CoroutineScope(Dispatchers.IO)

    private var running = false
    var callback: (tagName: String, changelog: String?) -> Unit = { _, _ -> }

    /** Assets of the release that triggered the current update dialog. */
    @Volatile
    var pendingAssets: ReleaseAssets? = null

    /** Changelog/body of the release that triggered the current update dialog. */
    @Volatile
    var pendingChangelog: String? = null

    fun init(context: Context) {
        scope.launch {
            if (context.getPrefValue(PrefKey.CHECK_FOR_UPDATES) as Boolean) {
                val lastUpdateCheck = context.getPrefValue(PrefKey.LAST_UPDATE_CHECK) as Long
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastUpdateCheck < CHECK_TIMEOUT)
                    return@launch

                context.dataStore.edit { preferences ->
                    preferences[PrefKey.LAST_UPDATE_CHECK] = currentTime
                }

                try {
                    running = true
                    rawRun()
                }
                catch (_: Exception) {}
                running = false
            }
        }
    }

    fun run(context: Context? = null) {
        if (running) return
        running = true

        scope.launch {
            try {
                if (!rawRun()) {
                    context?.showToast(
                        context.getString(R.string.no_updates_available),
                        Toast.LENGTH_SHORT
                    )
                }
            }
            catch (_: Exception) {
                context?.showToast(
                    context.getString(R.string.failed_to_check_for_updates),
                    Toast.LENGTH_SHORT
                )
            }
            running = false
        }
    }

    private fun rawRun(): Boolean {
        val release = releases.fetchLatest()
        val tagName = release["tag_name"] as String
        val latestVersion = parseVersion(tagName.removePrefix("v"))
        val changelog = (release["body"] as? String)?.trim()?.takeIf { it.isNotEmpty() }

        return if (latestVersion != null && currentVersion != null && isNewer(latestVersion, currentVersion)) {
            pendingAssets = releases.findReleaseAssets(release)
            pendingChangelog = changelog
            callback(tagName, changelog)
            true
        } else false
    }

    /** Re-fetches the latest release's assets when retrying a download. */
    fun fetchReleaseAssets(): ReleaseAssets? = try {
        releases.findReleaseAssets(releases.fetchLatest())
    } catch (_: Exception) {
        null
    }

    /** True if [a] is strictly greater than [b] as a semver triple. */
    private fun isNewer(a: Triple<Int, Int, Int>, b: Triple<Int, Int, Int>): Boolean {
        if (a.first != b.first) return a.first > b.first
        if (a.second != b.second) return a.second > b.second
        return a.third > b.third
    }

    /** Parses [v]MAJOR.MINOR.PATCH into a comparable Triple; null for malformed strings (treated as not newer). */
    private fun parseVersion(s: String): Triple<Int, Int, Int>? {
        val parts = s.removePrefix("v").split(".")
        if (parts.size < 3) return null
        return try {
            Triple(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
        } catch (_: NumberFormatException) {
            null
        }
    }
}