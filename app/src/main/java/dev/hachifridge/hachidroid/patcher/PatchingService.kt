package dev.hachifridge.hachidroid.patcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.hachifridge.hachidroid.MainActivity
import dev.hachifridge.hachidroid.R
import dev.hachifridge.hachidroid.ui.patcher.PatcherLauncher

class PatchingService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastUpdateTime = 0L
    private var updatePending = false

    private val updateRunnable = Runnable {
        updatePending = false
        publishNotification()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                PatcherLauncher.cancelPatcher()
                updateTask(getString(R.string.patching_cancelled_by_user))
                return START_NOT_STICKY
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        // Must transition to foreground immediately to satisfy OS timeouts.
        createNotificationChannel()
        val initialNotification = buildNotification(currentTask, currentProgress)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    initialNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, initialNotification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
        }

        if (!isRunning) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(updateRunnable)
        if (instance === this) {
            instance = null
        }
        isRunning = false
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_patching),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(task: String, progress: Float): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val cancelIntent = Intent(this, PatchingService::class.java).apply {
            action = ACTION_CANCEL
        }
        val cancelPendingIntent = PendingIntent.getService(
            this,
            1,
            cancelIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = currentTitle.ifBlank { getString(R.string.patching) }
        val contentText = task.ifBlank { getString(R.string.working) }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(contentText)
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .addAction(
                0,
                getString(R.string.cancel),
                cancelPendingIntent
            )

        if (progress < 0f) {
            builder.setProgress(100, 0, true)
        } else {
            val progressInt = (progress * 100).toInt().coerceIn(0, 100)
            builder.setProgress(100, progressInt, false)
        }

        return builder.build()
    }

    fun postNotificationUpdate(force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (force || now - lastUpdateTime >= UPDATE_INTERVAL_MS) {
            handler.removeCallbacks(updateRunnable)
            updatePending = false
            publishNotification()
        } else if (!updatePending) {
            updatePending = true
            handler.postDelayed(updateRunnable, UPDATE_INTERVAL_MS - (now - lastUpdateTime))
        }
    }

    private fun publishNotification() {
        lastUpdateTime = SystemClock.elapsedRealtime()
        try {
            val notification = buildNotification(currentTask, currentProgress)
            val manager = NotificationManagerCompat.from(this)
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Android 13+ without POST_NOTIFICATIONS runtime permission granted
        } catch (e: Exception) {
            Log.e(TAG, "Failed to publish notification update", e)
        }
    }

    companion object {
        private const val TAG = "PatchingService"
        private const val NOTIFICATION_ID = 4040
        private const val CHANNEL_ID = "patching_session"
        private const val ACTION_CANCEL = "dev.hachifridge.hachidroid.patcher.CANCEL"
        private const val ACTION_STOP = "dev.hachifridge.hachidroid.patcher.STOP"
        private const val UPDATE_INTERVAL_MS = 250L

        @Volatile
        private var instance: PatchingService? = null

        @Volatile
        private var currentTitle: String = ""

        @Volatile
        private var currentTask: String = ""

        @Volatile
        private var currentProgress: Float = -1f

        @Volatile
        private var isRunning: Boolean = false

        fun start(context: Context, title: String = "", initialTask: String = "") {
            currentTitle = title
            currentTask = initialTask
            currentProgress = -1f
            isRunning = true
            val intent = Intent(context, PatchingService::class.java)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start PatchingService", e)
            }
        }

        fun updateProgress(progress: Float) {
            currentProgress = progress
            instance?.postNotificationUpdate()
        }

        fun updateTask(task: String) {
            currentTask = task
            instance?.postNotificationUpdate(force = true)
        }

        fun stop(context: Context) {
            isRunning = false
            val inst = instance
            if (inst != null) {
                try {
                    inst.stopForeground(STOP_FOREGROUND_REMOVE)
                    inst.stopSelf()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to stop PatchingService cleanly", e)
                }
            } else {
                val intent = Intent(context, PatchingService::class.java).apply {
                    action = ACTION_STOP
                }
                try {
                    context.startService(intent)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to send stop intent to PatchingService", e)
                }
            }
        }
    }
}
