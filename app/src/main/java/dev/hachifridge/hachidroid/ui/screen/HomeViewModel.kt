package dev.hachifridge.hachidroid.ui.screen

import android.content.Context
import android.content.pm.PackageInfo
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.hachifridge.hachidroid.R
import androidx.documentfile.provider.DocumentFile
import dev.hachifridge.hachidroid.core.DaringTactSource
import dev.hachifridge.hachidroid.core.GameChecker
import dev.hachifridge.hachidroid.core.HachimiSource
import dev.hachifridge.hachidroid.core.PatchMode
import dev.hachifridge.hachidroid.core.PrefKey
import dev.hachifridge.hachidroid.core.HachimiPluginEntry
import dev.hachifridge.hachidroid.core.HachimiPluginManager
import dev.hachifridge.hachidroid.core.AstraeaLibraries
import dev.hachifridge.hachidroid.core.dataStore
import dev.hachifridge.hachidroid.core.senbei.AstraeaUnpacker
import dev.hachifridge.hachidroid.core.senbei.SenbeiNative
import dev.hachifridge.hachidroid.core.getPrefValue
import dev.hachifridge.hachidroid.utils.showToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel : ViewModel() {
    private val _packageInfo = MutableStateFlow<PackageInfo?>(null)
    val packageInfo: StateFlow<PackageInfo?> = _packageInfo.asStateFlow()

    private val _plugins = MutableStateFlow<List<HachimiPluginEntry>>(emptyList())
    val plugins: StateFlow<List<HachimiPluginEntry>> = _plugins.asStateFlow()

    /** Active game family. Compose state because the hero card and the tiles both read it. */
    var mode by mutableStateOf(PatchMode.TRACEN)
        private set

    private val _libraries = MutableStateFlow(AstraeaLibraries.Status.EMPTY)
    val libraries: StateFlow<AstraeaLibraries.Status> = _libraries.asStateFlow()

    /**
     * The splits the studio has selected for Astraea. Kept here so the Libraries tile can unpack
     * them without asking for the same game twice; the studio owns the picks themselves.
     */
    var astraeaSplits by mutableStateOf<List<Uri>>(emptyList())

    /** True while Senbei is unpacking a picked split; the studio shows progress for this. */
    var unpackingLibraries by mutableStateOf(false)
        private set

    /**
     * Why the last unpack attempt produced nothing. A toast is gone before the user can act on it,
     * so the studio keeps the reason on screen until the next attempt replaces it.
     */
    var unpackError by mutableStateOf<String?>(null)
        private set

    /** Whether this device/build ships the Senbei bridge, which decides if restoring is offered. */
    val onDeviceUnpackAvailable: Boolean get() = SenbeiNative.isAvailable

    // Preferences handled in ViewModel. Tracen and Astraea each name their own side of the same
    // idea, so a call site says which game it is talking about.
    var tracenModSource by mutableStateOf("github")
    var tracenModSoUri by mutableStateOf("")
    var tracenModSoName by mutableStateOf("")
    var hachimiRepo by mutableStateOf("")
    /** Release tag of the cached mod library, empty until one has been downloaded. */
    var tracenModVersion by mutableStateOf("")
    var tracenModCached by mutableStateOf(false)

    /** True while a Hachimi download is running, so the tile can show it and not double-fire. */
    var fetchingTracenMod by mutableStateOf(false)
        private set

    /** Astraea's mod library source: "github" or "local". Hachimi has its own pair above. */
    var astraeaModSource by mutableStateOf("github")
    var astraeaModSoName by mutableStateOf("")
    var daringTactRepo by mutableStateOf("")

    /** True while a DaringTact download is running, so the studio can show it and not double-fire. */
    var fetchingAstraeaMod by mutableStateOf(false)
        private set

    fun refreshStatus(context: Context) {
        viewModelScope.launch {
            val savedMode = PatchMode.fromPref(context.getPrefValue(PrefKey.PATCH_MODE) as? String)
            GameChecker.applyMode(savedMode, context.packageManager)
            mode = savedMode
            _packageInfo.value = GameChecker.getPackageInfo(context.packageManager)
            _libraries.value = withContext(Dispatchers.IO) { AstraeaLibraries.inspect(context) }
            // Plugin bookkeeping reads plugins.json off disk; keep that off the main thread.
            _plugins.value = withContext(Dispatchers.IO) { HachimiPluginManager.listPlugins(context) }
            
            tracenModSource = context.getPrefValue(PrefKey.TRACEN_MOD_SOURCE) as String
            tracenModSoUri = context.getPrefValue(PrefKey.TRACEN_MOD_SO_URI) as String
            tracenModSoName = context.getPrefValue(PrefKey.TRACEN_MOD_SO_NAME) as String

            hachimiRepo = context.getPrefValue(PrefKey.HACHIMI_REPO) as String
            tracenModVersion = context.getPrefValue(PrefKey.HACHIMI_LIBS_VERSION) as String
            tracenModCached = HachimiSource.target(context).isFile

            astraeaModSource = context.getPrefValue(PrefKey.ASTRAEA_MOD_SOURCE) as String
            astraeaModSoName = context.getPrefValue(PrefKey.ASTRAEA_MOD_SO_NAME) as String
            daringTactRepo = context.getPrefValue(PrefKey.DARINGTACT_REPO) as String
        }
    }

    /**
     * Switches game family, persists it and re-resolves the target for the new one.
     * The library checklist is re-read too: it belongs to the Priconne side of the app.
     */
    fun setMode(context: Context, next: PatchMode) {
        if (next == mode) return
        mode = next
        GameChecker.applyMode(next, context.packageManager)
        _packageInfo.value = GameChecker.getPackageInfo(context.packageManager)
        viewModelScope.launch {
            context.dataStore.edit { it[PrefKey.PATCH_MODE] = next.prefValue }
            _libraries.value = withContext(Dispatchers.IO) { AstraeaLibraries.inspect(context) }
        }
    }

    /**
     * Copies picked libraries into private storage and re-reads the checklist. Everything in
     * here is disk-bound: `libil2cpp.so` alone is a few hundred megabytes.
     */
    fun importLibraries(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val report = withContext(Dispatchers.IO) { AstraeaLibraries.import(context, uris) }
            _libraries.value = withContext(Dispatchers.IO) { AstraeaLibraries.inspect(context) }
            val message = if (report.isEmpty) {
                context.getString(R.string.libraries_import_failed)
            } else {
                context.getString(R.string.libraries_import_done, report.imported.size)
            }
            context.showToast(message, Toast.LENGTH_SHORT)
        }
    }

    /** Drops one library so a stale copy can be replaced instead of sitting in the way. */
    fun removeLibrary(context: Context, name: String) {
        viewModelScope.launch {
            val removed = withContext(Dispatchers.IO) { AstraeaLibraries.remove(context, name) }
            _libraries.value = withContext(Dispatchers.IO) { AstraeaLibraries.inspect(context) }
            if (removed) unpackError = null
            context.showToast(
                context.getString(
                    if (removed) R.string.libraries_removed else R.string.libraries_remove_failed,
                    name
                ),
                Toast.LENGTH_SHORT
            )
        }
    }

    /**
     * Unpacks the protected libraries from the splits the studio already holds, so the user picks
     * the game once instead of picking it here as well. Only the config split carries them, but
     * both splits are selected together, so each is tried until one yields the libraries.
     */
    fun unpackLibrariesFromSplits(context: Context, uris: List<Uri>) {
        if (unpackingLibraries || uris.isEmpty()) return
        unpackingLibraries = true
        unpackError = null
        viewModelScope.launch {
            // Only the config split carries the libraries, but both splits are selected together,
            // so each is tried. A split that simply has nothing in it is the least useful answer,
            // so a real failure from another one wins.
            var outcome: AstraeaUnpacker.Outcome? = null
            for (uri in uris) {
                val attempt = withContext(Dispatchers.IO) { AstraeaUnpacker.unpack(context, uri) }
                if (attempt is AstraeaUnpacker.Outcome.Unpacked) {
                    outcome = attempt
                    break
                }
                if (outcome !is AstraeaUnpacker.Outcome.Failed || attempt is AstraeaUnpacker.Outcome.Failed) {
                    outcome = attempt
                }
            }
            val result = outcome ?: AstraeaUnpacker.Outcome.NothingUnpacked

            _libraries.value = withContext(Dispatchers.IO) { AstraeaLibraries.inspect(context) }
            unpackingLibraries = false
            if (result is AstraeaUnpacker.Outcome.Unpacked) {
                context.showToast(unpackMessage(context, result), Toast.LENGTH_SHORT)
            } else {
                Log.w("HomeViewModel", "library unpack produced nothing: $result")
                unpackError = unpackMessage(context, result)
            }
        }
    }

    private fun unpackMessage(context: Context, outcome: AstraeaUnpacker.Outcome): String =
        when (outcome) {
            is AstraeaUnpacker.Outcome.Unpacked ->
                context.getString(R.string.libraries_unpack_done, outcome.libraries.size)
            AstraeaUnpacker.Outcome.NotEnoughSpace ->
                context.getString(R.string.libraries_unpack_no_space)
            AstraeaUnpacker.Outcome.Unavailable ->
                context.getString(R.string.libraries_unpack_unavailable)
            AstraeaUnpacker.Outcome.NothingUnpacked ->
                context.getString(R.string.libraries_unpack_no_libs)
            is AstraeaUnpacker.Outcome.Failed ->
                context.getString(R.string.libraries_unpack_failed)
        }

    fun updateAstraeaSplits(uris: List<Uri>) {
        astraeaSplits = uris
    }

    /**
     * Downloads the newest Hachimi runtime library, or reports that the cached one is already it.
     * Replaces what the old force-redownload setting did by hand.
     */
    fun fetchTracenMod(context: Context) {
        if (fetchingTracenMod) return
        fetchingTracenMod = true
        viewModelScope.launch {
            val outcome = HachimiSource.download(context)
            tracenModVersion = context.getPrefValue(PrefKey.HACHIMI_LIBS_VERSION) as String
            tracenModCached = HachimiSource.target(context).isFile
            fetchingTracenMod = false
            val message = when (outcome) {
                is HachimiSource.Outcome.Installed ->
                    context.getString(R.string.mod_library_done, outcome.version)
                is HachimiSource.Outcome.AlreadyLatest ->
                    context.getString(R.string.mod_library_done, outcome.version)
                HachimiSource.Outcome.RepoMissing ->
                    context.getString(R.string.hachimi_mod_repo_missing)
                HachimiSource.Outcome.AssetMissing ->
                    context.getString(R.string.hachimi_mod_asset_missing)
                HachimiSource.Outcome.ChecksumMissing ->
                    context.getString(R.string.hachimi_mod_checksum_missing)
                HachimiSource.Outcome.ChecksumMismatch ->
                    context.getString(R.string.hachimi_mod_checksum_mismatch)
                is HachimiSource.Outcome.Failed ->
                    context.getString(R.string.mod_library_failed, outcome.reason ?: "")
            }
            context.showToast(message, Toast.LENGTH_LONG)
        }
    }

    fun setAstraeaModSource(context: Context, source: String) {
        astraeaModSource = source
        viewModelScope.launch {
            context.dataStore.edit { it[PrefKey.ASTRAEA_MOD_SOURCE] = source }
        }
    }

    /**
     * Copies a picked `.so` into the library folder. The studio shows the mod as ready from
     * [libraries] rather than from this pref, because the file is what the patcher reads.
     */
    fun importAstraeaMod(context: Context, uri: Uri) {
        viewModelScope.launch {
            val name = DocumentFile.fromSingleUri(context, uri)?.name
                ?: uri.lastPathSegment
                ?: AstraeaLibraries.MOD_LIBRARY
            val outcome = DaringTactSource.importLocal(context, uri)
            if (outcome is DaringTactSource.Outcome.Installed) {
                astraeaModSoName = name
                context.dataStore.edit {
                    it[PrefKey.ASTRAEA_MOD_SO_URI] = uri.toString()
                    it[PrefKey.ASTRAEA_MOD_SO_NAME] = name
                }
            }
            _libraries.value = withContext(Dispatchers.IO) { AstraeaLibraries.inspect(context) }
            context.showToast(astraeaModMessage(context, outcome), Toast.LENGTH_LONG)
        }
    }

    fun fetchAstraeaMod(context: Context) {
        if (fetchingAstraeaMod) return
        fetchingAstraeaMod = true
        viewModelScope.launch {
            val outcome = DaringTactSource.download(context)
            _libraries.value = withContext(Dispatchers.IO) { AstraeaLibraries.inspect(context) }
            fetchingAstraeaMod = false
            context.showToast(astraeaModMessage(context, outcome), Toast.LENGTH_LONG)
        }
    }

    private fun astraeaModMessage(context: Context, outcome: DaringTactSource.Outcome): String =
        when (outcome) {
            is DaringTactSource.Outcome.Installed ->
                context.getString(R.string.astraea_mod_installed, outcome.detail)
            DaringTactSource.Outcome.RepoMissing ->
                context.getString(R.string.astraea_mod_repo_missing)
            DaringTactSource.Outcome.AssetMissing ->
                context.getString(R.string.astraea_mod_asset_missing)
            DaringTactSource.Outcome.NotAnElf ->
                context.getString(R.string.astraea_mod_not_elf)
            is DaringTactSource.Outcome.Failed ->
                context.getString(R.string.astraea_mod_failed, outcome.reason ?: "")
        }

    fun setTracenModSource(context: Context, source: String) {
        tracenModSource = source
        viewModelScope.launch {
            context.dataStore.edit { it[PrefKey.TRACEN_MOD_SOURCE] = source }
        }
    }

    fun importTracenMod(context: Context, uri: Uri, name: String) {
        tracenModSoUri = uri.toString()
        tracenModSoName = name
        viewModelScope.launch {
            context.dataStore.edit {
                it[PrefKey.TRACEN_MOD_SO_URI] = uri.toString()
                it[PrefKey.TRACEN_MOD_SO_NAME] = name
            }
        }
    }

    fun addPlugin(context: Context, uri: Uri) {
        viewModelScope.launch {
            val added = withContext(Dispatchers.IO) { HachimiPluginManager.addPlugin(context, uri) }
            if (added == null) {
                context.showToast(context.getString(R.string.failed_to_add_plugin), Toast.LENGTH_SHORT)
            } else {
                context.showToast(context.getString(R.string.plugin_added), Toast.LENGTH_SHORT)
            }
            _plugins.value = withContext(Dispatchers.IO) { HachimiPluginManager.listPlugins(context) }
        }
    }

    fun setPluginEnabled(context: Context, fileName: String, enabled: Boolean) {
        viewModelScope.launch {
            _plugins.value = withContext(Dispatchers.IO) {
                HachimiPluginManager.setEnabled(context, fileName, enabled)
                HachimiPluginManager.listPlugins(context)
            }
        }
    }

    fun removePlugin(context: Context, fileName: String) {
        viewModelScope.launch {
            _plugins.value = withContext(Dispatchers.IO) {
                HachimiPluginManager.removePlugin(context, fileName)
                HachimiPluginManager.listPlugins(context)
            }
            context.showToast(context.getString(R.string.plugin_removed), Toast.LENGTH_SHORT)
        }
    }
}
