package com.zack.focus

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class WatcherService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var detector: ForegroundAppDetector
    private lateinit var overlayGate: OverlayGate
    private lateinit var focusStore: FocusStore

    override fun onCreate() {
        super.onCreate()
        focusStore = FocusStore(this)
        focusStore.initializeDefaultsIfNeeded()
        detector = ForegroundAppDetector(this)
        overlayGate = OverlayGate(this)

        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        scope.launch { watchLoop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private suspend fun watchLoop() {
        while (scope.isActive) {
            try {
                if (!focusStore.isFocusModeActive()) {
                    stopSelf()
                    break
                }

                val fg = detector.getForegroundPackage()

                if (fg != null && fg != packageName) {
                    if (GatePolicy.shouldGate(fg, focusStore)) {
                        // Blocked app detected — show gate if not already showing
                        if (hasOverlayPermission(this) && !overlayGate.isShowing()) {
                            val blocked = fg
                            overlayGate.show(
                                packageName = blocked,
                                onGoBack = {
                                    sendUserHome()
                                    overlayGate.hide()
                                }
                            )
                        }
                    } else {
                        // A non-blocked app is positively in the foreground — safe to hide gate
                        if (overlayGate.isShowing()) overlayGate.hide()
                    }
                }
                // fg == null or fg == packageName: no change to overlay state

                delay(POLL_MS)
            } catch (t: Throwable) {
                Log.e(TAG, "Watch loop error", t)
                delay(1500L)
            }
        }
    }

    private fun sendUserHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    override fun onDestroy() {
        overlayGate.hide()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Focus Protection", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun buildNotification(): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Focus is active")
            .setContentText("Protecting your attention from distractions.")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setContentIntent(tapIntent)
            .build()
    }

    companion object {
        private const val TAG = "FocusWatcher"
        private const val CHANNEL_ID = "focus_watcher"
        private const val NOTIF_ID = 1001
        private const val POLL_MS = 800L

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, WatcherService::class.java)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WatcherService::class.java))
        }
    }
}
