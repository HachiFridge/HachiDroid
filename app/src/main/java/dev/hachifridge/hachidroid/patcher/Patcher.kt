package dev.hachifridge.hachidroid.patcher

import android.content.Context
import android.os.SystemClock
import android.util.Log
import dev.hachifridge.hachidroid.R
import dev.hachifridge.hachidroid.core.GameChecker
import dev.hachifridge.hachidroid.utils.copyTo
import com.topjohnwu.superuser.Shell
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

abstract class Patcher(
    var onLog: (String) -> Unit = {},
    var onProgress: (Float) -> Unit = {},
    var onTask: (String) -> Unit = {},
    var onSaveFile: (String, File, (Boolean) -> Unit) -> Unit = { _: String, _: File, _: (Boolean) -> Unit -> },
    var onInstallLegacy: (File, (Boolean) -> Unit) -> Unit = { _: File, _: (Boolean) -> Unit -> }
) {
    /**
     * Fired when a stage finishes: its id, the file it ran on (when it ran on one),
     * and its wall-clock cost. Drives the progress timeline.
     */
    var onStage: (label: String, detail: String?, seconds: Float) -> Unit = { _, _, _ -> }
    var isCancelled = false

    /**
     * How many APKs this run patches (1 for a standalone APK). The patching screen folds
     * repeated stages into one row and uses this as the "n/m" denominator.
     */
    var patchUnitCount: Int = 1

    /** Log message shown when the patcher finishes successfully. */
    open val successMessageRes: Int = R.string.patch_success_msg

    /** Log message shown when the patcher fails. */
    open val failureMessageRes: Int = R.string.patch_failed_msg

    /** Headline shown at the top of the patching screen when the run fails. */
    open val failureTitleRes: Int = R.string.patch_failed_title

    private var lastEmittedProgress = -1f
    private var lastProgressEmitAt = 0L

    var progress: Float = 0f
        set(value) {
            field = value
            // Each emit crosses to the main thread and invalidates Compose state, so a
            // 1 GB copy would otherwise push thousands of updates. Coalesce to ~1%
            // steps (and one every 100 ms), always passing indeterminate/complete.
            val now = SystemClock.elapsedRealtime()
            if (value < 0f || value >= 1f ||
                value - lastEmittedProgress >= 0.01f ||
                now - lastProgressEmitAt >= 100L
            ) {
                lastEmittedProgress = value
                lastProgressEmitAt = now
                onProgress(value)
            }
        }

    var task: String = ""
        set(value) {
            if (field == value) return
            field = value
            onTask(value)
        }

    fun setCallbacks(
        onLog: (String) -> Unit = {},
        onProgress: (Float) -> Unit = {},
        onTask: (String) -> Unit = {},
        onSaveFile: (String, File, (Boolean) -> Unit) -> Unit,
        onInstallLegacy: (File, (Boolean) -> Unit) -> Unit = { _, _ -> },
        onStage: (String, String?, Float) -> Unit = { _, _, _ -> }
    ) {
        this.onLog = onLog
        this.onProgress = onProgress
        this.onTask = onTask
        this.onSaveFile = onSaveFile
        this.onInstallLegacy = onInstallLegacy
        this.onStage = onStage
    }

    fun ensureActive() {
        if (isCancelled) throw kotlinx.coroutines.CancellationException("Patching cancelled by user")
    }

    abstract suspend fun run(context: Context): Boolean

    suspend fun safeRun(context: Context): Boolean {
        return try {
            run(context)
        } catch (ex: kotlinx.coroutines.CancellationException) {
            false
        } catch (ex: Exception) {
            logException(ex)
            false
        }
    }

    fun log(line: String) {
        onLog(line)
    }

    protected fun logAll(lines: Iterable<String>) {
        lines.forEach(onLog)
    }

    /** Per-stage wall-clock costs, replayed as one summary line when a run ends. */
    private val stageTimings = mutableListOf<String>()

    /**
     * Runs [block] and logs its wall-clock cost, so a slow patch run can be diagnosed
     * from the on-screen log alone. Prefixed with "·" to stay distinct from task headers.
     */
    protected suspend fun <T> timed(
        label: String,
        detail: String? = null,
        block: suspend () -> T
    ): T {
        val start = SystemClock.elapsedRealtime()
        try {
            return block()
        } finally {
            val seconds = (SystemClock.elapsedRealtime() - start) / 1000f
            val cost = String.format(Locale.ROOT, "%.2f", seconds)
            val subject = if (detail != null) "$label ($detail)" else label
            stageTimings += "$subject=$cost"
            log("· $subject: ${cost}s")
            onStage(label, detail, seconds)
        }
    }

    /** One-line replay of every stage cost, so the summary survives without scrolling. */
    protected fun timingSummary(): String = stageTimings.joinToString(", ")

    fun logException(ex: Exception) {
        val sw = StringWriter()
        PrintWriter(sw).use {
            ex.printStackTrace(it)
        }
        log(sw.toString())
        Log.e("HachiDroid", "logException", ex)
    }

    protected suspend fun saveFile(filename: String, file: File): Boolean {
        return suspendCoroutine { cont ->
            onSaveFile(filename, file) { cont.resume(it) }
        }
    }

    protected suspend fun installLegacy(file: File): Boolean {
        return suspendCoroutine { cont ->
            onInstallLegacy(file) { cont.resume(it) }
        }
    }

    protected fun copyFileProgress(srcFile: File, dstFile: File) {
        srcFile.inputStream().use { input ->
            dstFile.outputStream().use { output ->
                copyStreamProgress(input, output, srcFile.length())
            }
        }
    }

    protected fun copyStreamProgress(input: InputStream, output: OutputStream, length: Long) {
        val lengthFloat = length.toFloat()
        input.copyTo(output) { current ->
            ensureActive()
            progress = current / lengthFloat
        }
    }

    protected fun isDirectInstallAllowed(context: Context): Boolean {
        if (!GameChecker.isPackageInstalled(context.packageManager)) {
            log(context.getString(R.string.direct_install_unavailable))
            return false
        }
        if (Shell.isAppGrantedRoot() != true) {
            log(context.getString(R.string.root_required))
            return false
        }
        return true
    }
}