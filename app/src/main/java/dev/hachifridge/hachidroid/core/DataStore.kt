package dev.hachifridge.hachidroid.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "config")

object PrefKey {
    val CHECK_FOR_UPDATES = booleanPreferencesKey("check_for_updates")
    val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check")
    /** Version of the Hachimi library Tracen patches in. Legacy stored key, unchanged. */
    val HACHIMI_LIBS_VERSION = stringPreferencesKey("app_libs_version")
    val HACHIMI_REPO = stringPreferencesKey("hachimi_repo")
    /** Tracen's mod source. Stored under the legacy key so saved choices survive. */
    val TRACEN_MOD_SOURCE = stringPreferencesKey("hachimi_mod_source")
    // Tracen-prefixed like its sibling below; the stored key names are unchanged so existing
    // installs keep their choice.
    val TRACEN_MOD_SO_URI = stringPreferencesKey("custom_mod_so_uri")
    val TRACEN_MOD_SO_NAME = stringPreferencesKey("custom_mod_so_name")
    val MERGE_APKS = booleanPreferencesKey("merge_apks")
    val EXPORT_INTERNAL_DATA_PROVIDER = booleanPreferencesKey("export_internal_data_provider")
    val USE_INTERNAL_FILES_DIR = booleanPreferencesKey("use_internal_files_dir")
    /**
     * Legacy, single-slot studio state from before the game families were split. Only Tracen ever
     * wrote it, so it is adopted by that mode once and then cleared; see the load path in
     * `PatchingStudioCard`.
     */
    val FILE_URIS = stringPreferencesKey("app_patcher_file_uris")
    val INSTALL_METHOD = intPreferencesKey("install_method")

    /**
     * Astraea's mod library is not Hachimi, so it keeps its own source choice: the two games ship
     * different runtimes, and picking a DaringTact build for UmaJP (or the reverse) is never
     * useful. "github" or "local", mirroring the Hachimi pair above.
     */
    val ASTRAEA_MOD_SOURCE = stringPreferencesKey("astraea_mod_source")
    val ASTRAEA_MOD_SO_URI = stringPreferencesKey("astraea_mod_so_uri")
    val ASTRAEA_MOD_SO_NAME = stringPreferencesKey("astraea_mod_so_name")

    /** Repository the DaringTact mod library is downloaded from; editable in settings. */
    val DARINGTACT_REPO = stringPreferencesKey("daringtact_repo")

    /**
     * Studio state is per game family. One shared slot meant switching from Tracen to Astraea
     * handed the new mode the other mode's split APKs and install choice, which is wrong twice
     * over: those files are a different game, and Astraea cannot use most install methods.
     */
    fun fileUris(mode: PatchMode) = stringPreferencesKey("app_patcher_file_uris_${mode.prefValue}")

    fun installMethod(mode: PatchMode) = intPreferencesKey("install_method_${mode.prefValue}")
    val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")

    /** Which game family the dashboard and the patcher are aimed at; see [PatchMode]. */
    val PATCH_MODE = stringPreferencesKey("patch_mode")

    /** UmaPatcher-Edge migration: "" = not offered yet, "dismissed" = opted out, "done" = migrated. */
    val UPE_MIGRATION_STATE = stringPreferencesKey("upe_migration_state")
}

val defaultValues = mapOf(
    Pair(PrefKey.CHECK_FOR_UPDATES, true),
    Pair(PrefKey.LAST_UPDATE_CHECK, 0L),
    Pair(PrefKey.HACHIMI_LIBS_VERSION, ""),
    Pair(PrefKey.HACHIMI_REPO, "Tenshou170/Hachimi-Edge"),
    Pair(PrefKey.TRACEN_MOD_SOURCE, "github"),
    Pair(PrefKey.TRACEN_MOD_SO_URI, ""),
    Pair(PrefKey.TRACEN_MOD_SO_NAME, ""),
    Pair(PrefKey.MERGE_APKS, false),
    Pair(PrefKey.EXPORT_INTERNAL_DATA_PROVIDER, true),
    Pair(PrefKey.USE_INTERNAL_FILES_DIR, false),
    Pair(PrefKey.FILE_URIS, ""),
    Pair(PrefKey.INSTALL_METHOD, 1),
    Pair(PrefKey.ASTRAEA_MOD_SOURCE, "github"),
    Pair(PrefKey.ASTRAEA_MOD_SO_URI, ""),
    Pair(PrefKey.ASTRAEA_MOD_SO_NAME, ""),
    Pair(PrefKey.DARINGTACT_REPO, "HachiFridge/DaringTact"),
    // Off by default: the honey brand palette is HachiDroid's identity.
    Pair(PrefKey.DYNAMIC_COLOR, false),
    Pair(PrefKey.PATCH_MODE, PatchMode.TRACEN.prefValue),
    Pair(PrefKey.UPE_MIGRATION_STATE, "")
)

suspend fun Context.getPrefValue(key: Preferences.Key<*>): Any? {
    return withContext(Dispatchers.IO) {
        val value = dataStore.data
            .map {
                it[key]
            }
        return@withContext value.firstOrNull() ?: defaultValues[key]
    }
}