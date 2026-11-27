package dev.hachifridge.hachidroid.ui.screen

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.net.Uri
import android.os.Build
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
import dev.hachifridge.hachidroid.client.ModSource
import dev.hachifridge.hachidroid.client.ShellDetection
import dev.hachifridge.hachidroid.core.GameChecker
import dev.hachifridge.hachidroid.client.Client
import dev.hachifridge.hachidroid.client.ClientProfile
import dev.hachifridge.hachidroid.core.PrefKey
import dev.hachifridge.hachidroid.client.PluginEntry
import dev.hachifridge.hachidroid.client.LibrarySet
import dev.hachifridge.hachidroid.client.displayNameOf
import dev.hachifridge.hachidroid.core.dataStore
import dev.hachifridge.hachidroid.client.ShellLibraryUnpacker
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

    private val _plugins = MutableStateFlow<List<PluginEntry>>(emptyList())
    val plugins: StateFlow<List<PluginEntry>> = _plugins.asStateFlow()

    /** Active client profile / preset. */
    var profile by mutableStateOf(ClientProfile.TRACEN_GLB)
        private set

    /** Active client family, derived from [profile]. */
    val client: Client
        get() = profile.client

    /** The distinct server clients installed on the device for this family. */
    var installedProfiles by mutableStateOf<List<ClientProfile>>(emptyList())
        private set

    /** Presets activate only when more than one distinct server client in the same mode is installed. */
    val hasMultipleProfiles: Boolean
        get() = installedProfiles.size > 1

    private val _libraries = MutableStateFlow(profile.libraries.emptyStatus(shellProtected = false))
    val libraries: StateFlow<LibrarySet.Status> = _libraries.asStateFlow()

    /**
     * Whether the build being patched is packer-protected.
     */
    var patchProtected by mutableStateOf(false)
        private set

    /**
     * Whether the *installed* copy is packer-protected.
     */
    var installedTargetProtected by mutableStateOf(false)
        private set

    /**
     * The splits the studio has selected, kept here so the library section can unpack them without
     * asking for the same game twice; the studio owns the picks.
     */
    var patchSplits by mutableStateOf<List<Uri>>(emptyList())
        private set

    /** True while Senbei is unpacking a picked split; the studio shows progress for this. */
    var unpackingLibraries by mutableStateOf(false)
        private set

    /**
     * The game version of the picked build, when its manifest could be read. Compared with the
     * version the restored files were unpacked from, so a stale image is called out instead of
     * silently installed.
     */
    var patchStamp by mutableStateOf<String?>(null)
        private set

    /**
     * Why the last unpack attempt produced nothing. A toast is gone before the user can act on it,
     * so the studio keeps the reason on screen until the next attempt replaces it.
     */
    var unpackError by mutableStateOf<String?>(null)
        private set

    /** Whether this device/build ships the Senbei bridge, which decides if restoring is offered. */
    val onDeviceUnpackAvailable: Boolean get() = SenbeiNative.isAvailable

    // The mod runtime's source, for whichever client is active. The Mod Source sheet and the
    // patcher both read these, so there is one answer to "where does the mod come from".
    var modSourceKind by mutableStateOf("github")
        private set
    var modSoName by mutableStateOf("")
    var modRepo by mutableStateOf("")
    /** Release tag of the cached mod library, empty until one has been downloaded. */
    var modVersion by mutableStateOf("")
    var modCached by mutableStateOf(false)
        private set

    /** True while a mod download is running, so the sheet can show it and not double-fire. */
    var fetchingMod by mutableStateOf(false)
        private set

    fun refreshStatus(context: Context) {
        viewModelScope.launch {
            val pm = context.packageManager
            val savedClient = Client.fromPref(context.getPrefValue(PrefKey.CLIENT) as? String)
            installedProfiles = ClientProfile.getInstalledProfiles(pm, savedClient)

            val savedProfileId = context.getPrefValue(PrefKey.PROFILE) as? String
            val savedTarget = context.getPrefValue(PrefKey.TARGET_PACKAGE) as String

            val initialProfile = when {
                installedProfiles.size > 1 -> {
                    val matchingSaved = savedProfileId?.let { id -> installedProfiles.find { it.id == id } }
                    val matchingTarget = if (savedTarget.isNotEmpty()) installedProfiles.find { it.packageNames.contains(savedTarget) } else null
                    matchingSaved ?: matchingTarget ?: installedProfiles.first()
                }
                installedProfiles.size == 1 -> {
                    installedProfiles.first()
                }
                else -> {
                    if (savedProfileId != null) ClientProfile.fromId(savedProfileId)
                    else ClientProfile.defaultForClient(savedClient)
                }
            }

            if (savedTarget.isNotEmpty() &&
                savedTarget in initialProfile.packageNames &&
                runCatching { pm.getPackageInfo(savedTarget, 0) }.isSuccess
            ) {
                GameChecker.currentPackageName = savedTarget
            }
            GameChecker.applyProfile(initialProfile, pm)
            profile = initialProfile
            _packageInfo.value = GameChecker.getPackageInfo(pm)
            syncTargetState()
            refreshLibraries(context)
            // Plugin bookkeeping reads plugins.json off disk; keep that off the main thread.
            _plugins.value = withContext(Dispatchers.IO) { initialProfile.plugins.listPlugins(context) }
            readModState(context)
        }
        observeClientChanges(context)
    }

    private var clientJob: kotlinx.coroutines.Job? = null

    /** The probe that settles the picked build; only the newest pick's answer may land. */
    private var splitsJob: kotlinx.coroutines.Job? = null

    private fun observeClientChanges(context: Context) {
        clientJob?.cancel()
        clientJob = viewModelScope.launch {
            context.dataStore.data
                .map { Client.fromPref(it[PrefKey.CLIENT]) }
                .distinctUntilChanged()
                .collectLatest { nextClient ->
                    if (client != nextClient) {
                        setClient(context, nextClient)
                    }
                }
        }
    }

    /**
     * Switches the active profile and persists it.
     */
    fun setProfile(context: Context, next: ClientProfile) {
        if (next == profile) return
        profile = next
        patchSplits = emptyList()
        patchStamp = null
        splitsJob?.cancel()
        installedProfiles = ClientProfile.getInstalledProfiles(context.packageManager, next.client)
        GameChecker.applyProfile(next, context.packageManager)
        _packageInfo.value = GameChecker.getPackageInfo(context.packageManager)
        syncTargetState()
        viewModelScope.launch {
            context.dataStore.edit {
                it[PrefKey.PROFILE] = next.id
                it[PrefKey.CLIENT] = next.client.prefValue
                it[PrefKey.TARGET_PACKAGE] = next.defaultPackageName
            }
            refreshLibraries(context)
            _plugins.value = withContext(Dispatchers.IO) { next.plugins.listPlugins(context) }
            readModState(context)
        }
    }

    /**
     * Switches client family, persists it and re-resolves the profile for the new one.
     */
    fun setClient(context: Context, next: Client) {
        val pm = context.packageManager
        val installed = ClientProfile.getInstalledProfiles(pm, next)
        val targetProfile = if (installed.isNotEmpty()) {
            installed.first()
        } else {
            ClientProfile.defaultForClient(next)
        }
        setProfile(context, targetProfile)
    }

    private fun syncTargetState() {
        installedTargetProtected = profile.shellProtected ||
            ShellDetection.isPackedPackage(GameChecker.currentPackageName)
        if (patchSplits.isEmpty()) patchProtected = profile.shellProtected
    }

    /**
     * What the picked files say about the build: its shape, and its version when readable.
     *
     * The version is what makes a restored image and its metadata checkable: they only match the
     * build they were unpacked from, so it is recorded beside them and compared here.
     */
    private data class PickShape(val packed: Boolean, val stamp: String?, val packageName: String? = null)

    /**
     * Settles the build from the picks themselves.
     *
     * The probe streams the picked archives, so it is resolved on an IO dispatcher rather than
     * read from [patchProtected]: that state is written by an asynchronous probe, and an import or
     * unpack that started before it landed must not decide from the previous answer. With nothing
     * picked there is no build to describe, and only a client whose every build is packed counts.
     */
    private suspend fun shapeOfPicks(context: Context, uris: List<Uri>): PickShape {
        if (uris.isEmpty() && !profile.shellProtected) {
            return PickShape(packed = false, stamp = null)
        }
        val info = withContext(Dispatchers.IO) { ShellDetection.inspect(context, uris) }
        val detectedProfile = info.packageName?.let { ClientProfile.fromPackageName(it) }
        val baseProtected = detectedProfile?.shellProtected ?: profile.shellProtected
        return PickShape(baseProtected || info.protected, info.versionStamp, info.packageName)
    }

    /**
     * The installed copy's version, in the same `versionName/versionCode` form the picks report.
     * Used when there is nothing picked to describe the build.
     */
    @Suppress("DEPRECATION")
    private fun installedStamp(context: Context): String? {
        val info = GameChecker.getPackageInfo(context.packageManager) ?: return null
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            info.versionCode.toLong()
        }
        val name = info.versionName.orEmpty()
        return if (code > 0) "$name/$code" else name
    }

    private suspend fun refreshLibraries(context: Context) {
        val shell = patchProtected
        val picks = patchSplits
        val target = if (picks.isEmpty()) installedStamp(context) else patchStamp
        val status = withContext(Dispatchers.IO) {
            profile.libraries.inspect(context, shell, target)
        }
        // A pick that changed while this ran owns the checklist; do not overwrite it — neither a
        // different build shape nor a different version.
        val stillCurrent = patchProtected == shell &&
            picks == patchSplits &&
            (picks.isEmpty() || target == patchStamp)
        if (stillCurrent) _libraries.value = status
    }

    private suspend fun readModState(context: Context) {
        val source = profile.modSource
        modSourceKind = context.getPrefValue(source.sourceKey) as String
        modSoName = context.getPrefValue(source.nameKey) as String
        modRepo = context.getPrefValue(source.repoKey) as String
        modVersion = source.cachedVersion(context)
        modCached = source.isCached(context)
    }

    /**
     * Copies picked libraries into private storage and re-reads the checklist. Everything in
     * here is disk-bound: `libil2cpp.so` alone is a few hundred megabytes.
     */
    fun importLibraries(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        val store = profile.libraries

        // When a single mod runtime binary is selected, streamline through mod import so validation
        // and mod source preferences are seamlessly synced.
        val singleUri = uris.singleOrNull()
        if (singleUri != null && store.isModLibraryName(displayNameOf(context, singleUri))) {
            importMod(context, singleUri)
            return
        }

        val picks = patchSplits
        viewModelScope.launch {
            // Resolved from the picks themselves: the asynchronous probe may not have landed yet.
            val shape = shapeOfPicks(context, picks)
            val protectedTarget = if (picks.isEmpty()) {
                profile.shellProtected || installedTargetProtected
            } else {
                shape.packed
            }
            val stamp = when {
                !protectedTarget -> null
                picks.isEmpty() -> installedStamp(context)
                else -> shape.stamp
            }
            val report = withContext(Dispatchers.IO) {
                val result = store.import(context, uris, protectedTarget)
                // Imported restored files belong to this build; record which one.
                if (result.imported.isNotEmpty() && stamp != null) store.writeStamp(context, stamp)
                result
            }
            if (store.modLibrary in report.imported) {
                val modUri = uris.firstOrNull { uri ->
                    store.isModLibraryName(displayNameOf(context, uri))
                }
                val modName = modUri?.let { displayNameOf(context, it) } ?: store.modLibrary
                val targetFile = store.modDirectory(context).resolve(store.modLibrary)
                val version = (profile.modSource.validate(context, targetFile) as? ModSource.Validation.Ok)?.detail
                val vKey = profile.modSource.versionKey
                context.dataStore.edit {
                    it[profile.modSource.sourceKey] = "local"
                    it[profile.modSource.nameKey] = modName
                    if (modUri != null) it[profile.modSource.uriKey] = modUri.toString()
                    if (version != null && vKey != null) {
                        it[vKey] = version
                    }
                }
                readModState(context)
            }
            refreshLibraries(context)
            val message = when {
                report.isEmpty -> context.getString(R.string.libraries_import_failed)
                report.stillPacked.isNotEmpty() -> context.getString(
                    R.string.libraries_import_still_packed,
                    report.stillPacked.joinToString(", ")
                )
                report.skipped.isNotEmpty() -> context.getString(
                    R.string.libraries_import_partial,
                    report.imported.size,
                    report.skipped.joinToString(", ")
                )
                else -> context.getString(R.string.libraries_import_done, report.imported.size)
            }
            context.showToast(message, Toast.LENGTH_SHORT)
        }
    }

    /** Drops one library so a stale copy can be replaced instead of sitting in the way. */
    fun removeLibrary(context: Context, name: String) {
        viewModelScope.launch {
            val store = profile.libraries
            val isMod = store.isModLibraryName(name)
            val removed = withContext(Dispatchers.IO) { store.remove(context, name) }
            if (isMod && removed) {
                profile.modSource.versionKey?.let { key ->
                    context.dataStore.edit { it[key] = "" }
                }
                readModState(context)
            }
            refreshLibraries(context)
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
     * the game once instead of picking it here as well. Only the config split carries the native
     * libraries, but both splits are tried because the studio hands over the whole selection.
     */
    fun unpackLibrariesFromSplits(context: Context, uris: List<Uri>) {
        if (unpackingLibraries || uris.isEmpty()) return
        unpackingLibraries = true
        unpackError = null
        viewModelScope.launch {
            try {
                // The unpack writes protected artifacts, so the build is settled from the picks
                // here instead of trusted from the asynchronous probe.
                val shape = shapeOfPicks(context, uris)
                val packed = shape.packed
                patchProtected = packed
                patchStamp = shape.stamp

                val store = profile.libraries
                // A packed build keeps its libraries in the config split and its metadata in the
                // base one, so every pick is read: stopping at the first hit would leave the
                // other missing.
                val unpackable = store.gameFilesFor(packed)
                val metadata = if (packed) store.protectedGameAssets.toSet() else emptySet()
                val unpacked = mutableListOf<String>()
                var outcome: ShellLibraryUnpacker.Outcome? = null
                for (uri in uris) {
                    val attempt = withContext(Dispatchers.IO) {
                        ShellLibraryUnpacker.unpack(
                            context, uri, unpackable, store.directory(context), metadata
                        )
                    }
                    outcome = attempt
                    if (attempt is ShellLibraryUnpacker.Outcome.Unpacked) unpacked += attempt.libraries
                }
                val result = if (unpacked.isNotEmpty()) ShellLibraryUnpacker.Outcome.Unpacked(unpacked)
                    else outcome ?: ShellLibraryUnpacker.Outcome.NothingUnpacked

                if (result is ShellLibraryUnpacker.Outcome.Unpacked) {
                    // The files just written match the build they came from; record its version.
                    val stamp = shape.stamp ?: installedStamp(context)
                    if (stamp != null) {
                        withContext(Dispatchers.IO) { store.writeStamp(context, stamp) }
                    }
                }
                refreshLibraries(context)
                if (result is ShellLibraryUnpacker.Outcome.Unpacked) {
                    context.showToast(unpackMessage(context, result), Toast.LENGTH_SHORT)
                } else {
                    Log.w("HomeViewModel", "library unpack produced nothing: $result")
                    unpackError = unpackMessage(context, result)
                }
            } finally {
                unpackingLibraries = false
            }
        }
    }

    private fun unpackMessage(context: Context, outcome: ShellLibraryUnpacker.Outcome): String =
        when (outcome) {
            is ShellLibraryUnpacker.Outcome.Unpacked ->
                context.getString(R.string.libraries_unpack_done, outcome.libraries.size)
            ShellLibraryUnpacker.Outcome.NotEnoughSpace ->
                context.getString(R.string.libraries_unpack_no_space)
            ShellLibraryUnpacker.Outcome.Unavailable ->
                context.getString(R.string.libraries_unpack_unavailable)
            ShellLibraryUnpacker.Outcome.NothingUnpacked ->
                context.getString(R.string.libraries_unpack_no_libs)
            is ShellLibraryUnpacker.Outcome.Failed ->
                context.getString(R.string.libraries_unpack_failed)
        }

    /**
     * Records the studio's picks and re-reads the build they belong to. The probe reads the picked
     * archives, so it runs off the main thread; a pick that changes the answer also changes which
     * libraries the checklist asks for.
     */
    fun updatePatchSplits(context: Context, uris: List<Uri>) {
        patchSplits = uris
        // A newer pick supersedes the probe that is still running: without this, clearing the
        // picker and then letting an older Global probe finish would describe a build that is no
        // longer selected.
        splitsJob?.cancel()
        splitsJob = viewModelScope.launch {
            val shape = shapeOfPicks(context, uris)
            if (patchSplits != uris) return@launch
            val detected = shape.packageName?.let { ClientProfile.fromPackageName(it) }
            if (detected != null && detected != profile) {
                profile = detected
                installedProfiles = ClientProfile.getInstalledProfiles(context.packageManager, detected.client)
                GameChecker.applyProfile(detected, context.packageManager)
                _packageInfo.value = GameChecker.getPackageInfo(context.packageManager)
                syncTargetState()
                context.dataStore.edit {
                    it[PrefKey.PROFILE] = detected.id
                    it[PrefKey.CLIENT] = detected.client.prefValue
                    it[PrefKey.TARGET_PACKAGE] = detected.defaultPackageName
                }
                _plugins.value = withContext(Dispatchers.IO) { detected.plugins.listPlugins(context) }
                readModState(context)
            }
            patchStamp = shape.stamp
            patchProtected = shape.packed
            refreshLibraries(context)
        }
    }

    /**
     * Downloads the newest mod runtime for the active client, or reports that the cached one is
     * already it. This is the only place the mod library is fetched; the patch reuses the file.
     */
    fun fetchMod(context: Context) {
        if (fetchingMod) return
        fetchingMod = true
        val source = client.modSource
        viewModelScope.launch {
            val outcome = source.download(context)
            readModState(context)
            refreshLibraries(context)
            fetchingMod = false
            context.showToast(modMessage(context, outcome), Toast.LENGTH_LONG)
        }
    }

    /**
     * Copies a picked `.so` into the client's library folder. The pick is stored as well, so the
     * patcher can reproduce it if the file is ever removed.
     */
    fun importMod(context: Context, uri: Uri) {
        val source = client.modSource
        val name = displayNameOf(context, uri).ifEmpty { source.libraryName }
        viewModelScope.launch {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            context.dataStore.edit {
                it[source.sourceKey] = "local"
                it[source.uriKey] = uri.toString()
                it[source.nameKey] = name
            }
            val outcome = source.importLocal(context, uri)
            readModState(context)
            refreshLibraries(context)
            context.showToast(modMessage(context, outcome), Toast.LENGTH_LONG)
        }
    }

    fun setModSource(context: Context, source: String) {
        modSourceKind = source
        val key = client.modSource.sourceKey
        viewModelScope.launch {
            context.dataStore.edit { it[key] = source }
        }
    }

    private fun modMessage(context: Context, outcome: ModSource.Outcome): String =
        when (outcome) {
            is ModSource.Outcome.Installed ->
                context.getString(R.string.mod_library_done, outcome.detail)
            is ModSource.Outcome.AlreadyLatest ->
                context.getString(R.string.mod_library_done, outcome.version)
            ModSource.Outcome.RepoMissing ->
                context.getString(R.string.mod_repo_missing)
            ModSource.Outcome.AssetMissing ->
                context.getString(R.string.mod_asset_missing)
            ModSource.Outcome.ChecksumMissing ->
                context.getString(R.string.hachimi_mod_checksum_missing)
            ModSource.Outcome.ChecksumMismatch ->
                context.getString(R.string.hachimi_mod_checksum_mismatch)
            is ModSource.Outcome.Rejected ->
                outcome.message
            is ModSource.Outcome.Failed ->
                context.getString(R.string.mod_library_failed, outcome.reason ?: "")
        }

    fun addPlugin(context: Context, uri: Uri) {
        viewModelScope.launch {
            val store = client.plugins
            val added = withContext(Dispatchers.IO) { store.addPlugin(context, uri) }
            if (added == null) {
                context.showToast(context.getString(R.string.failed_to_add_plugin), Toast.LENGTH_SHORT)
            } else {
                context.showToast(context.getString(R.string.plugin_added), Toast.LENGTH_SHORT)
            }
            _plugins.value = withContext(Dispatchers.IO) { store.listPlugins(context) }
        }
    }

    fun setPluginEnabled(context: Context, fileName: String, enabled: Boolean) {
        viewModelScope.launch {
            val store = client.plugins
            _plugins.value = withContext(Dispatchers.IO) {
                store.setEnabled(context, fileName, enabled)
                store.listPlugins(context)
            }
        }
    }

    fun removePlugin(context: Context, fileName: String) {
        viewModelScope.launch {
            val store = client.plugins
            _plugins.value = withContext(Dispatchers.IO) {
                store.removePlugin(context, fileName)
                store.listPlugins(context)
            }
            context.showToast(context.getString(R.string.plugin_removed), Toast.LENGTH_SHORT)
        }
    }
}
