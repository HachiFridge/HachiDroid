package dev.hachifridge.hachidroid.core

import android.content.Context
import android.os.Build
import android.os.SystemClock
import dev.hachifridge.hachidroid.BuildConfig
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Raw run log on disk: one file per patching run, named after the moment it started and kept in
 * the app's own data folder (/sdcard/Android/data/<package>/files), so it can be pulled off the
 * device or shared from the log viewer without root or adb.
 *
 * Every write is best effort. A full disk or a dead SD card must never be the reason a patch fails,
 * so failures here are swallowed and the run carries on with the in-memory log only.
 */
class PatchLogFile private constructor(
    val file: File,
    private val writer: BufferedWriter
) {
    private val lock = Any()
    private val clock = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT)
    private var lastFlushAt = SystemClock.elapsedRealtime()

    @Volatile
    private var closed = false

    /**
     * Appends [line] with a per-line timestamp. Stack traces arrive as one multi-line string and
     * keep their own shape; only the first line carries the timestamp.
     */
    fun append(line: String) {
        if (closed) return
        synchronized(lock) {
            if (closed) return
            try {
                var first = true
                for (raw in line.lineSequence()) {
                    if (raw.isNotEmpty()) {
                        if (first) writer.write("[" + clock.format(Date()) + "] ")
                        writer.write(raw)
                    }
                    writer.write("\r\n")
                    first = false
                }
                // Flushing every line would mean thousands of writes while the APK is being
                // rebuilt (every entry is logged), so settle for half a second of exposure.
                val now = SystemClock.elapsedRealtime()
                if (now - lastFlushAt >= FLUSH_INTERVAL_MS) {
                    writer.flush()
                    lastFlushAt = now
                }
            } catch (_: Exception) {
                closeQuietly()
            }
        }
    }

    /** Writes the verdict line and releases the file; later [append] calls are ignored. */
    fun close(verdict: String) {
        append("== " + verdict)
        synchronized(lock) { closeQuietly() }
    }

    private fun closeQuietly() {
        if (closed) return
        closed = true
        try {
            writer.flush()
            writer.close()
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val FLUSH_INTERVAL_MS = 500L

        /**
         * Opens a log for a run that is starting now. Returns null when the folder cannot be
         * created or the file cannot be opened; the run then simply has no file to share.
         */
        fun create(context: Context): PatchLogFile? {
            val dir = context.getExternalFilesDir(null) ?: context.filesDir
            if (!dir.exists() && !dir.mkdirs()) return null

            val startedAt = Date()
            val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(startedAt)
            val target = dir.resolve("run-$stamp.log")

            return try {
                val log = PatchLogFile(
                    file = target,
                    writer = BufferedWriter(FileWriter(target, false), 16 * 1024)
                )
                log.append(
                    "HachiDroid " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ") · " +
                        (if (BuildConfig.DEBUG) "debug" else "release")
                )
                log.append(
                    "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ") · " +
                        Build.MANUFACTURER + " " + Build.MODEL
                )
                log.append("Started " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(startedAt))
                log
            } catch (_: Exception) {
                null
            }
        }
    }
}
