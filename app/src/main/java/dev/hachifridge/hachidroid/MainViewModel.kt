package dev.hachifridge.hachidroid

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.topjohnwu.superuser.Shell
import dev.hachifridge.hachidroid.core.AppUpdater
import dev.hachifridge.hachidroid.core.GameChecker
import dev.hachifridge.hachidroid.core.PatchMode
import dev.hachifridge.hachidroid.core.PrefKey
import dev.hachifridge.hachidroid.core.UpeMigration
import dev.hachifridge.hachidroid.core.UpdateChecker
import dev.hachifridge.hachidroid.core.dataStore
import dev.hachifridge.hachidroid.core.getPrefValue
import dev.hachifridge.hachidroid.patcher.LegacyInstaller
import dev.hachifridge.hachidroid.ui.patcher.PatcherLauncher
import dev.hachifridge.hachidroid.utils.deleteRecursive
import dev.hachifridge.hachidroid.utils.repoDir
import dev.hachifridge.hachidroid.utils.workDir
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** UI phases of the built-in app updater dialog. */
enum class UpdatePhase { AVAILABLE, DOWNLOADING, FAILED }

class MainViewModel : ViewModel() {
    val rootInitialized = mutableStateOf(false)
    val isRooted = mutableStateOf(false)
    val openUpdateDialog = mutableStateOf(false)
    val updateTagName = mutableStateOf("")
    val updateChangelog = mutableStateOf<String?>(null)
    var pendingUpdateDeepLink = mutableStateOf(false)

    // UmaPatcher-Edge migration
    val openMigrationDialog = mutableStateOf(false)
    val migrationSources = mutableStateOf(listOf<UpeMigration.DetectedSource>())
    val migrationRunning = mutableStateOf(false)
    val migrationMessage = mutableStateOf<String?>(null)

    // Built-in app updater
    val updatePhase = mutableStateOf(UpdatePhase.AVAILABLE)
    val updateProgress = mutableFloatStateOf(-1f)
    val updateProgressLabel = mutableStateOf("")
    val updateErrorText = mutableStateOf("")
    /** Set when the download finished; consumed by MainContent to launch the installer. */
    val updateReadyFile = mutableStateOf<File?>(null)
    private var updateJob: Job? = null

    fun init(context: Context) {
        GameChecker.init(context.packageManager)

        // The persisted mode decides which packages everything below targets, so apply it before
        // any patcher or install flow reads GameChecker.
        viewModelScope.launch {
            val mode = PatchMode.fromPref(context.getPrefValue(PrefKey.PATCH_MODE) as? String)
            GameChecker.applyMode(mode, context.packageManager)
        }

        // Housekeeping walks the disk; init() runs from composition, so keep it off the main thread.
        viewModelScope.launch(Dispatchers.IO) {
            // Don't wipe the work dir while a patch is running (init re-runs on recreation).
            if (!PatcherLauncher.patching) {
                context.workDir.mkdir()
                deleteRecursive(context.workDir, deleteRoot = false)
            }
            deleteRecursive(context.repoDir, deleteRoot = true)
        }

        Shell.getShell { shell ->
            isRooted.value = shell.isRoot
            rootInitialized.value = true

            // Offer the UmaPatcher-Edge migration once (root state is known here).
            viewModelScope.launch {
                checkMigration(context)
            }
        }

        UpdateChecker.init(context)
        UpdateChecker.callback = { name, changelog ->
            updateTagName.value = name
            updateChangelog.value = changelog
            openUpdateDialog.value = true
        }
    }

    private suspend fun checkMigration(context: Context) {
        try {
            val state = context.getPrefValue(PrefKey.UPE_MIGRATION_STATE) as? String ?: ""
            if (state.isNotEmpty() || PatcherLauncher.patching) return
            val sources = UpeMigration.detectSources(context)
            if (sources.isEmpty()) return
            migrationSources.value = sources
            openMigrationDialog.value = true
        } catch (e: Exception) {
            Log.w("MainViewModel", "UPE migration detection failed", e)
        }
    }

    fun startMigration(context: Context) {
        if (migrationRunning.value || migrationSources.value.isEmpty()) return
        val sources = migrationSources.value
        migrationRunning.value = true
        viewModelScope.launch {
            try {
                val summary = UpeMigration.migrate(context, sources)
                context.dataStore.edit { it[PrefKey.UPE_MIGRATION_STATE] = "done" }
                migrationMessage.value = context.getString(
                    if (summary.isBlank) R.string.migration_nothing_to_migrate
                    else R.string.migration_done
                )
            } catch (e: Exception) {
                // Leave the state flag untouched so the migration can be retried.
                Log.e("MainViewModel", "UPE migration failed", e)
                migrationMessage.value =
                    context.getString(R.string.migration_failed, e.message ?: e.javaClass.simpleName)
            } finally {
                migrationRunning.value = false
                openMigrationDialog.value = false
            }
        }
    }

    fun dismissMigration(context: Context) {
        if (migrationRunning.value) return
        openMigrationDialog.value = false
        viewModelScope.launch {
            try {
                context.dataStore.edit { it[PrefKey.UPE_MIGRATION_STATE] = "dismissed" }
            } catch (e: Exception) {
                Log.w("MainViewModel", "Could not persist migration dismissal", e)
            }
        }
    }

    fun importMigrationKeystore(context: Context, uri: Uri) {
        if (migrationRunning.value) return
        migrationRunning.value = true
        viewModelScope.launch {
            try {
                val result = UpeMigration.importKeystoreFromUri(context, uri)
                if (result.isSuccess) {
                    context.dataStore.edit { it[PrefKey.UPE_MIGRATION_STATE] = "done" }
                    migrationMessage.value = context.getString(R.string.keystore_imported)
                    openMigrationDialog.value = false
                } else {
                    val err = result.exceptionOrNull()?.localizedMessage
                        ?: context.getString(R.string.failed_to_import_keystore)
                    migrationMessage.value = err
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Keystore import failed", e)
                migrationMessage.value = e.localizedMessage ?: context.getString(R.string.failed_to_import_keystore)
            } finally {
                migrationRunning.value = false
            }
        }
    }

    // ------------------------------------------------- built-in app updater

    fun startUpdateDownload(context: Context) {
        if (updatePhase.value == UpdatePhase.DOWNLOADING) return
        if (PatcherLauncher.patching) {
            updateErrorText.value = context.getString(R.string.update_busy)
            updatePhase.value = UpdatePhase.FAILED
            return
        }
        updateReadyFile.value = null
        updateErrorText.value = ""
        updateProgress.floatValue = -1f
        updateProgressLabel.value = context.getString(R.string.update_downloading_unknown)
        updatePhase.value = UpdatePhase.DOWNLOADING

        updateJob = viewModelScope.launch {
            var lastEmit = 0L
            var lastValue = -1f
            try {
                val assets = UpdateChecker.pendingAssets ?: UpdateChecker.fetchReleaseAssets()
                    ?: throw IOException(context.getString(R.string.update_no_apk))
                val file = withContext(Dispatchers.IO) {
                    AppUpdater.downloadAndVerify(context, updateTagName.value, assets) { fraction, totalBytes ->
                        // Throws when the job is cancelled so an in-flight read stops.
                        coroutineContext.ensureActive()
                        val now = System.currentTimeMillis()
                        if (fraction >= 1f || fraction - lastValue >= 0.01f || now - lastEmit >= 150L) {
                            lastValue = fraction
                            lastEmit = now
                            updateProgress.floatValue = fraction
                            updateProgressLabel.value =
                                if (totalBytes > 0) {
                                    context.getString(
                                        R.string.update_progress_mb,
                                        fraction * totalBytes / 1048576f,
                                        totalBytes / 1048576f
                                    )
                                } else {
                                    context.getString(R.string.update_downloading_unknown)
                                }
                        }
                    }
                }
                // Verified: MainContent installs it through the unknown-sources gate if needed.
                updateProgress.floatValue = 1f
                openUpdateDialog.value = false
                updatePhase.value = UpdatePhase.AVAILABLE
                updateReadyFile.value = file
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateErrorText.value = e.message ?: context.getString(R.string.update_failed_generic)
                updatePhase.value = UpdatePhase.FAILED
                openUpdateDialog.value = true
            }
        }
    }

    fun cancelUpdateDownload() {
        updateJob?.cancel()
        updateJob = null
        updateProgress.floatValue = -1f
        updatePhase.value = UpdatePhase.AVAILABLE
    }

    fun closeUpdateDialog() {
        if (updatePhase.value == UpdatePhase.DOWNLOADING) return
        openUpdateDialog.value = false
        updatePhase.value = UpdatePhase.AVAILABLE
        updateErrorText.value = ""
    }

    fun installUpdate(context: Context) {
        val file = updateReadyFile.value ?: AppUpdater.updateFile(context, updateTagName.value)
        if (!file.isFile) {
            updateReadyFile.value = null
            updateErrorText.value = context.getString(R.string.update_file_missing)
            updatePhase.value = UpdatePhase.FAILED
            openUpdateDialog.value = true
            return
        }
        try {
            context.startActivity(LegacyInstaller.buildInstallIntent(context, file))
            updateReadyFile.value = null
            openUpdateDialog.value = false
            updatePhase.value = UpdatePhase.AVAILABLE
            updateErrorText.value = ""
        } catch (e: Exception) {
            Log.e("MainViewModel", "Could not launch update installer", e)
            updateErrorText.value = e.message ?: context.getString(R.string.update_failed_generic)
            updatePhase.value = UpdatePhase.FAILED
            openUpdateDialog.value = true
        }
    }

    /** Drops a downloaded update without installing (permission dialog cancelled). */
    fun clearUpdateReady() {
        updateReadyFile.value = null
    }
}
