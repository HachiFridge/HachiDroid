package dev.hachifridge.hachidroid.core

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import org.junit.Assert.assertEquals
import org.junit.Test

class UpeMigrationPrefsTest {

    private val unknownKey = intPreferencesKey("install_method")

    @Test
    fun parseSourcePrefs_readsWhitelistedKeys() {
        val source = preferencesOf(
            UpeMigration.SRC_CHECK_FOR_UPDATES to false,
            UpeMigration.SRC_HACHIMI_REPO to "someone/Hachimi-Edge",
            UpeMigration.SRC_MERGE_APKS to true,
            UpeMigration.SRC_EXPORT_INTERNAL_DATA_PROVIDER to false,
            UpeMigration.SRC_USE_INTERNAL_FILES_DIR to true,
            UpeMigration.SRC_HACHIMI_LIBS_VERSION to "v1.2.3",
            UpeMigration.SRC_USE_UNIVERSAL_SIGNING_KEY to true,
            unknownKey to 4
        )

        val parsed = UpeMigration.parseSourcePrefs(source)

        assertEquals(false, parsed.checkForUpdates)
        assertEquals("someone/Hachimi-Edge", parsed.hachimiRepo)
        assertEquals(true, parsed.mergeApks)
        assertEquals(false, parsed.exportInternalDataProvider)
        assertEquals(true, parsed.useInternalFilesDir)
        assertEquals("v1.2.3", parsed.appLibsVersion)
        assertEquals(true, parsed.useUniversalSigningKey)
    }

    @Test
    fun parseSourcePrefs_ignoresBlankRepoAndEmptyLibVersion() {
        val source = preferencesOf(
            UpeMigration.SRC_HACHIMI_REPO to "   ",
            UpeMigration.SRC_HACHIMI_LIBS_VERSION to ""
        )

        val parsed = UpeMigration.parseSourcePrefs(source)

        assertEquals(null, parsed.hachimiRepo)
        assertEquals(null, parsed.appLibsVersion)
    }

    @Test
    fun parseSourcePrefs_absentKeysStayNull() {
        val parsed = UpeMigration.parseSourcePrefs(preferencesOf())

        assertEquals(null, parsed.checkForUpdates)
        assertEquals(null, parsed.hachimiRepo)
        assertEquals(null, parsed.mergeApks)
        assertEquals(null, parsed.exportInternalDataProvider)
        assertEquals(null, parsed.useInternalFilesDir)
        assertEquals(null, parsed.appLibsVersion)
        assertEquals(null, parsed.useUniversalSigningKey)
    }

    @Test
    fun foldSourcePrefs_firstSourceWinsPerKey() {
        val first = UpeMigration.SourcePrefs(
            hachimiRepo = "fork/Hachimi-Edge",
            mergeApks = false
        )
        val second = UpeMigration.SourcePrefs(
            checkForUpdates = false,
            hachimiRepo = "upstream/Hachimi-Edge",
            mergeApks = true,
            appLibsVersion = "v9"
        )

        val folded = UpeMigration.foldSourcePrefs(listOf(first, second))

        assertEquals("fork/Hachimi-Edge", folded.hachimiRepo) // first wins
        assertEquals(false, folded.mergeApks) // first wins
        assertEquals(false, folded.checkForUpdates) // falls through
        assertEquals("v9", folded.appLibsVersion) // falls through
    }
}
