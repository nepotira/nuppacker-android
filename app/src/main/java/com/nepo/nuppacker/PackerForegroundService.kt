package com.nepo.nuppacker

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class PackerForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        const val CHANNEL_ID = "nuppacker_service_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.nepo.nuppacker.ACTION_START"
        const val ACTION_CANCEL = "com.nepo.nuppacker.ACTION_CANCEL"
        const val ACTION_UPDATE_PROGRESS = "com.nepo.nuppacker.ACTION_UPDATE_PROGRESS"

        const val EXTRA_CONFIG_JSON = "extra_config_json"
        const val EXTRA_TITLE_NAME = "extra_title_name"
        const val EXTRA_PROGRESS = "extra_progress"
        const val EXTRA_STATUS = "extra_status"

        @Volatile
        var isServiceRunning = false

        fun startService(context: Context, configJson: String, titleName: String) {
            val intent = Intent(context, PackerForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_CONFIG_JSON, configJson)
                putExtra(EXTRA_TITLE_NAME, titleName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopOrCancel(context: Context) {
            if (RustBridge.isNativeEngineAvailable()) {
                RustBridge.cancelBuild()
            }
            PackerBridge.cancelActiveBuild()
            val intent = Intent(context, PackerForegroundService::class.java).apply {
                action = ACTION_CANCEL
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        createNotificationChannel()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "NUPPacker:PackagingWakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire(6 * 60 * 60 * 1000L) // 6 hours max safety timeout
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                if (RustBridge.isNativeEngineAvailable()) {
                    RustBridge.cancelBuild()
                }
                PackerBridge.cancelActiveBuild()
                stopServiceGracefully()
            }
            ACTION_START -> {
                val title = intent.getStringExtra(EXTRA_TITLE_NAME) ?: "Switch Package"
                startForegroundNotification(title, 0, "Initializing...")
            }
            ACTION_UPDATE_PROGRESS -> {
                val title = intent.getStringExtra(EXTRA_TITLE_NAME) ?: "Switch Package"
                val progress = intent.getIntExtra(EXTRA_PROGRESS, 0)
                val status = intent.getStringExtra(EXTRA_STATUS) ?: "Packing files..."
                updateNotification(title, progress, status)
            }
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "NUP Packaging Operations",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time progress and status of background NUP builds"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundNotification(title: String, progress: Int, status: String) {
        val notification = buildNotification(title, progress, status)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    fun updateNotification(title: String, progress: Int, status: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(title, progress, status))
    }

    private fun buildNotification(title: String, progress: Int, status: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(this, PackerForegroundService::class.java).apply {
            action = ACTION_CANCEL
        }
        val cancelPendingIntent = PendingIntent.getService(
            this,
            1,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NUPPacker: $title")
            .setContentText("$progress% • $status")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppPendingIntent)
            .setProgress(100, progress, false)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)
            .build()
    }

    private fun stopServiceGracefully() {
        isServiceRunning = false
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        isServiceRunning = false
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
