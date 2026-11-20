package dev.hachifridge.hachidroid.ui.screen

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest

import android.content.Context
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
import dev.hachifridge.hachidroid.core.PrefKey
import dev.hachifridge.hachidroid.client.PluginEntry
import dev.hachifridge.hachidroid.client.LibrarySet
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

    /** Active client. Compose state because the hero card and the tiles both read it. */
    var client by mutableStateOf(Client.TRACEN)
        private set

    private val _libraries = MutableStateFlow(client.libraries.emptyStatus(shellProtected = false))
    val libraries: StateFlow<LibrarySet.Status> = _libraries.asStateFlow()

    /**
     * Whether the build being patched is packer-protected.
     *
     * It is a property of the build, not of the client: Uma's Japan and Global releases are one
     * [Client] and only Global carries the shell. The picked files are that build, so they decide;
     * with nothing picked there is no build yet, and only a client whose every build is packed
     * counts as protected. The installed copy is reported separately as
     * [installedTargetProtected] so clearing the picker returns the studio to its plain shape
     * instead of describing the Global build that happens to be installed.
     */
    var patchProtected by mutableStateOf(false)
        private set

    /**
     * Whether the *installed* copy is packer-protected. Only the target picker uses this: a packed
     * install cannot be injected in place, so it steers the studio to APK files. It never decides
     * which libraries a patch of picked files needs.
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
            val savedClient = Client.fromPref(context.getPrefValue(PrefKey.CLIENT) as? String)
            // A target the user picked explicitly survives a restart. Honour it only while that
            // package is installed; otherwise the client's first installed release takes over.
            val savedTarget = context.getPrefValue(PrefKey.TARGET_PACKAGE) as String
            if (savedTarget.isNotEmpty() &&
                savedTarget in savedClient.packageNames &&
                runCatching { context.packageManager.getPackageInfo(savedTarget, 0) }.isSuccess
            ) {
                GameChecker.currentPackageName = savedTarget
            }
            GameChecker.applyClient(savedClient, context.packageManager)
            client = savedClient
            _packageInfo.value = GameChecker.getPackageInfo(context.packageManager)
            syncTargetState()
            refreshLibraries(context)
            // Plugin bookkeeping reads plugins.json off disk; keep that off the main thread.
            _plugins.value = withContext(Dispatchers.IO) { savedClient.plugins.listPlugins(context) }
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
                        client = nextClient
                        GameChecker.applyClient(nextClient, context.packageManager)
                        _packageInfo.value = GameChecker.getPackageInfo(context.packageManager)
                        syncTargetState()
                        refreshLibraries(context)
                        _plugins.value = withContext(Dispatchers.IO) {
                            nextClient.plugins.listPlugins(context)
                        }
                        readModState(context)
                    }
                }
        }
    }

    /**
     * Switches client, persists it and re-resolves the target for the new one. The library
     * checklist and the mod source are re-read too: both belong to the client.
     */
    fun setClient(context: Context, next: Client) {
        if (next == client) return
        client = next
        // Picks belong to the client they were made for; the new client's studio restores its own
        // and reports them back through [updatePatchSplits].
        patchSplits = emptyList()
        patchStamp = null
        splitsJob?.cancel()
        GameChecker.applyClient(next, context.packageManager)
        _packageInfo.value = GameChecker.getPackageInfo(context.packageManager)
        syncTargetState()
        viewModelScope.launch {
            context.dataStore.edit { it[PrefKey.CLIENT] = next.prefValue }
            refreshLibraries(context)
            _plugins.value = withContext(Dispatchers.IO) { next.plugins.listPlugins(context) }
            readModState(context)
        }
    }

    /**
     * Re-resolves the installed copy and, while nothing is picked, the build the studio describes.
     * Picked splits answer for themselves through [updatePatchSplits], so a Global copy still
     * installed under the same client cannot turn the checklist back into Global's restored
     * libraries while Japan's files are selected.
     */
    private fun syncTargetState() {
        installedTargetProtected = client.alwaysShellProtected ||
            ShellDetection.isPackedPackage(GameChecker.currentPackageName)
        if (patchSplits.isEmpty()) patchProtected = client.alwaysShellProtected
    }

    /**
     * What the picked files say about the build: its shape, and its version when readable.
     *
     * The version is what makes a restored image and its metadata checkable: they only match the
     * build they were unpacked from, so it is recorded beside them and compared here.
     */
    private data class PickShape(val packed: Boolean, val stamp: String?)

    /**
     * Settles the build from the picks themselves.
     *
     * The probe streams the picked archives, so it is resolved on an IO dispatcher rather than
     * read from [patchProtected]: that state is written by an asynchronous probe, and an import or
     * unpack that started before it landed must not decide from the previous answer. With nothing
     * picked there is no build to describe, and only a client whose every build is packed counts.
     */
    private suspend fun shapeOfPicks(context: Context, uris: List<Uri>): PickShape {
        if (uris.isEmpty() && !client.alwaysShellProtected) {
            return PickShape(packed = false, stamp = null)
        }
        val info = withContext(Dispatchers.IO) { ShellDetection.inspect(context, uris) }
        return PickShape(client.alwaysShellProtected || info.protected, info.versionStamp)
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
        return "${info.versionName.orEmpty()}/$code"
    }

    private suspend fun refreshLibraries(context: Context) {
        val shell = patchProtected
        val picks = patchSplits
        val target = if (picks.isEmpty()) installedStamp(context) else patchStamp
        val status = withContext(Dispatchers.IO) {
            client.libraries.inspect(context, shell, target)
        }
        // A pick that changed while this ran owns the checklist; do not overwrite it — neither a
        // different build shape nor a different version.
        val stillCurrent = patchProtected == shell &&
            picks == patchSplits &&
            (picks.isEmpty() || target == patchStamp)
        if (stillCurrent) _libraries.value = status
    }

    private suspend fun readModState(context: Context) {
        val source = client.modSource
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
        val picks = patchSplits
        val store = client.libraries
        viewModelScope.launch {
            // Resolved from the picks themselves: the asynchronous probe may not have landed yet.
            val shape = shapeOfPicks(context, picks)
            val protectedTarget = if (picks.isEmpty()) {
                client.alwaysShellProtected || installedTargetProtected
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
            val store = client.libraries
            val removed = withContext(Dispatchers.IO) { store.remove(context, name) }
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

                val store = client.libraries
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
        val name = DocumentFile.fromSingleUri(context, uri)?.name
            ?: uri.lastPathSegment
            ?: source.libraryName
        viewModelScope.launch {
            context.dataStore.edit {
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
