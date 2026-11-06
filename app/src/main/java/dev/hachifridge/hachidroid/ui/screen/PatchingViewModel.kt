package dev.hachifridge.hachidroid.ui.screen

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.hachifridge.hachidroid.R
import dev.hachifridge.hachidroid.core.PatchLogFile
import dev.hachifridge.hachidroid.patcher.Patcher
import dev.hachifridge.hachidroid.ui.patcher.PatcherLauncher
import dev.hachifridge.hachidroid.utils.copyTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * One pipeline stage, merged across every file it ran on. [completed]/[total] drive the
 * "1/2" badge so a split-APK run stays a handful of rows instead of one per file.
 */
data class PatchStage(
    val label: String,
    val detail: String?,
    val seconds: Float,
    val completed: Int = 1,
    val total: Int = 1,
    /** Every run folded into this row, in order, so the row can be opened file by file. */
    val entries: List<StageEntry> = emptyList()
)

/** One run of a stage on one file: what it worked on and how long that took. */
data class StageEntry(val detail: String?, val seconds: Float)

class PatchingViewModel : ViewModel() {
    companion object {
        private const val MAX_LOG_LINES = 200
    }

    val log = mutableStateListOf<String>()

    /** Timeline rows, in the order stages first completed. */
    val stages = mutableStateListOf<PatchStage>()
    private val stagePositions = HashMap<String, Int>()

    /** The run being shown; held here because PatcherLauncher drops its reference when done. */
    private var activePatcher: Patcher? = null

    /**
     * Folds repeats of [label] into one row. A split-APK run reports every stage once per
     * APK, which used to print one row per file and bury the pipeline shape. The folding
     * rules themselves live in [StageTimeline]; this only keeps the row order and the index.
     */
    private fun recordStage(label: String, detail: String?, seconds: Float) {
        val at = stagePositions[label]
        val next = StageTimeline.nextStage(
            previous = at?.let { stages[it] },
            label = label,
            detail = detail,
            seconds = seconds,
            patchUnitCount = activePatcher?.patchUnitCount ?: 1
        )
        if (at == null) {
            stagePositions[label] = stages.size
            stages.add(next)
        } else {
            stages[at] = next
        }
    }

    /** Drops the expected-but-never-run rows of a successful run; see [StageTimeline.clampTotals]. */
    private fun clampTotalsToCompleted() {
        val clamped = StageTimeline.clampTotals(stages)
        for (i in stages.indices) {
            if (stages[i] != clamped[i]) stages[i] = clamped[i]
        }
    }
    var currentTask by mutableStateOf("")
    var progress by mutableFloatStateOf(-1f)
    var completed by mutableStateOf(false)
    var isSuccess by mutableStateOf(false)
    var isCancelled by mutableStateOf(false)

    /** Verdict headline for a failed run; supplied by the patcher (patching vs uninstall). */
    var failureTitle by mutableStateOf("")
    /** Success message captured at launch; the screen would otherwise read it after the patcher is gone. */
    var successMessage by mutableStateOf("")
    /** Last real message the patcher logged, so a failure says why instead of just that it did. */
    var failureReason by mutableStateOf<String?>(null)
    /** Task that was still running when the run failed, i.e. where it stopped. */
    var failedTask by mutableStateOf<String?>(null)
    private var lastReportedTask: String? = null
    
    var sfFile by mutableStateOf<File?>(null)
    var sfCallback: (Boolean) -> Unit = {}

    var legacyFile by mutableStateOf<File?>(null)
    var legacyCallback: (Boolean) -> Unit = {}

    /** The run's log file on disk, opened before the patcher starts and closed when it ends. */
    var logFile by mutableStateOf<File?>(null)
        private set

    // Written from the patcher thread, read from wherever the run finishes: both must see it.
    @Volatile
    private var runLog: PatchLogFile? = null

    private val logChannel = Channel<String>(Channel.UNLIMITED)

    /** Sends a line to the screen and to the log file, so both always say the same thing. */
    private fun emit(line: String) {
        logChannel.trySend(line)
        runLog?.append(line)
    }

    /** Empties everything the previous run left on screen; only a fresh run may call this. */
    private fun clearRunState(workingStr: String) {
        log.clear()
        stages.clear()
        stagePositions.clear()
        runLog = null
        logFile = null
        completed = false
        isSuccess = false
        isCancelled = false
        progress = -1f
        failureReason = null
        failedTask = null
        lastReportedTask = null
        currentTask = workingStr
    }

    init {
        viewModelScope.launch(Dispatchers.Main) {
            logChannel.receiveAsFlow().collect { line ->
                log.add(line)
                while (log.size > MAX_LOG_LINES) {
                    log.removeAt(0)
                }
            }
        }
    }

    fun init(
        context: Context,
        patchSuccessMsg: String,
        patchFailedMsg: String,
        patchCancelledMsg: String,
        failureTitle: String
    ) {
        val patcher = PatcherLauncher.patcher ?: return

        // This ViewModel is activity-scoped, so every visit to the screen reuses the instance and
        // the previous run's state is still here. A different patcher means a new run: drop what
        // the last one left behind, or the screen keeps showing the other game's log and verdict
        // while the run that is waiting for its callbacks never starts.
        val newRun = patcher !== activePatcher
        if (!newRun && (completed || PatcherLauncher.patching)) return

        this.failureTitle = failureTitle
        successMessage = patchSuccessMsg
        failureReason = null
        failedTask = null
        lastReportedTask = null

        val workingStr = context.getString(R.string.working)
        if (newRun) {
            clearRunState(workingStr)
        } else if (currentTask.isEmpty()) {
            currentTask = workingStr
        }

        activePatcher = patcher

        patcher.setCallbacks(
            onLog = { line -> emit(line) },
            onStage = { label, detail, seconds -> recordStage(label, detail, seconds) },
            onProgress = { p ->
                viewModelScope.launch(Dispatchers.Main) {
                    progress = p
                }
            },
            onTask = { task ->
                lastReportedTask = task
                viewModelScope.launch(Dispatchers.Main) {
                    currentTask = task
                }
                emit("-- $task")
            },
            onSaveFile = { _, file, callback ->
                viewModelScope.launch(Dispatchers.Main) {
                    sfFile = file
                    sfCallback = callback
                }
            },
            onInstallLegacy = { file, callback ->
                viewModelScope.launch(Dispatchers.Main) {
                    legacyFile = file
                    legacyCallback = callback
                }
            }
        )

        viewModelScope.launch {
            // Opened before the patcher starts so no line can be lost, and off the main thread
            // because it writes to the SD card.
            val openLog = withContext(Dispatchers.IO) {
                PatchLogFile.create(context.applicationContext)
            }
            runLog = openLog
            logFile = openLog?.file

            PatcherLauncher.runPatcher(context) { success ->
                viewModelScope.launch(Dispatchers.Main) {
                    completed = true
                    isSuccess = success && !patcher.isCancelled
                    isCancelled = patcher.isCancelled
                    if (patcher.isCancelled) {
                        progress = 1f
                    } else {
                        if (success) clampTotalsToCompleted()
                        if (!success) {
                            failureReason = lastDiagnosticLine()
                            failedTask = lastReportedTask
                        }
                        progress = 1f
                    }
                    val verdict = when {
                        patcher.isCancelled -> patchCancelledMsg
                        success -> patchSuccessMsg
                        else -> patchFailedMsg
                    }
                    emit(verdict)
                    runLog?.close(verdict)
                    runLog = null
                    currentTask = context.getString(R.string.completed)
                }
            }
        }
    }

    /**
     * Last real message the patcher logged. Task headers ("-- "), stage timings ("· ")
     * and blank lines describe progress, not a cause, so they never become the reason.
     */
    private fun lastDiagnosticLine(): String? {
        val entry = log.lastOrNull { line ->
            line.isNotBlank() && !line.startsWith("· ") && !line.startsWith("-- ")
        } ?: return null
        // A stack trace arrives as one multi-line entry; its first line carries the cause.
        return entry.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(300)
    }    fun handleSaveFileResult(context: Context, uri: Uri?, completedStr: String) {
        // Snapshot the pending file before leaving the main thread. Reading the field inside the
        // IO coroutine meant a cleared field bailed out through `return@launch`, which also skipped
        // the callback the patcher is waiting on, so the run would hang instead of failing.
        val file = sfFile
        if (uri == null || file == null) {
            sfCallback(false)
            sfFile = null
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // A null stream means the document could not be opened; reporting success there
                // told the patcher the APK was saved when nothing had been written.
                val output = context.contentResolver.openOutputStream(uri)
                    ?: throw IOException("No output stream for $uri")
                output.use {
                    val length = file.length().toFloat()

                    viewModelScope.launch(Dispatchers.Main) {
                        currentTask = context.getString(R.string.copying_file_name).format(file.name)
                        progress = 0f
                    }

                    file.inputStream().use { input ->
                        input.copyTo(it) { current ->
                            if (length > 0f) {
                                viewModelScope.launch(Dispatchers.Main) {
                                    progress = current / length
                                }
                            }
                        }
                    }
                }

                viewModelScope.launch(Dispatchers.Main) {
                    progress = 1f
                    sfCallback(true)
                    currentTask = completedStr
                    sfFile = null
                }
            } catch (e: Exception) {
                Log.e("PatchingViewModel", "Failed to save file", e)
                viewModelScope.launch(Dispatchers.Main) {
                    sfCallback(false)
                    sfFile = null
                }
            }
        }
    }

    fun handleLegacyInstallResult(success: Boolean) {
        legacyCallback(success)
        legacyFile = null
    }
}
