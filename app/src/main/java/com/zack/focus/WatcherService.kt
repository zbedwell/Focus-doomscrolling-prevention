package com.zack.focus

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.*

class WatcherService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var detector: ForegroundAppDetector
    private lateinit var overlayGate: OverlayGate

    override fun onCreate() {
        super.onCreate()

        detector = ForegroundAppDetector(this)
        overlayGate = OverlayGate(this)

        startForeground(
            NOTIF_ID,
            buildNotification("Focus is active", "Blocking doomscrolling apps")
        )

        scope.launch { loopSafely() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Keep service running if system reclaims it
        return START_STICKY
    }

    private suspend fun loopSafely() {
        while (scope.isActive) {
            try {
                val fg = detector.getForegroundPackage()
                val overlayAllowed = hasOverlayPermission(this)

                Log.d("FocusWatcher", "fg=$fg overlay=$overlayAllowed showing=${overlayGate.isShowing()}")

                if (
                    fg != null &&
                    fg != packageName &&
                    overlayAllowed &&
                    GatePolicy.shouldGate(fg) &&
                    !overlayGate.isShowing()
                ) {
                    overlayGate.show(fg) {
                        GatePolicy.recordUnlocked(fg)
                        overlayGate.hide()
                    }
                }

                delay(1000)
            } catch (t: Throwable) {
                Log.e("FocusWatcher", "Watcher loop error", t)
                delay(1500)
            }
        }
    }

    override fun onDestroy() {
        overlayGate.hide()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(title: String, text: String): Notification {
        val channelId = CHANNEL_ID

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    "Focus Protection",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }

        return Notification.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "focus_watcher"
        private const val NOTIF_ID = 1001
    }
}
