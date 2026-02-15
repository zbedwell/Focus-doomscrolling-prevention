package com.zack.focus

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
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
    private lateinit var blockedAppRepository: BlockedAppRepository

    private var lastInterruptPackage: String? = null
    private var lastInterruptAtMs: Long = 0L

    override fun onCreate() {
        super.onCreate()

        focusStore = FocusStore(this)
        blockedAppRepository = BlockedAppRepository(this)
        detector = ForegroundAppDetector(this)
        overlayGate = OverlayGate(this)

        initializeDefaultBlockedApps()
        createNotificationChannel()

        startForeground(NOTIF_ID, buildOngoingNotification(System.currentTimeMillis()))
        scope.launch { loopSafely() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SESSION_25 -> {
                focusStore.startSession25(System.currentTimeMillis())
            }

            ACTION_REFRESH_STATE,
            ACTION_START_PROTECTION,
            null -> Unit
        }

        updateOngoingNotification(System.currentTimeMillis())
        return START_STICKY
    }

    private suspend fun loopSafely() {
        while (scope.isActive) {
            try {
                val now = System.currentTimeMillis()
                updateOngoingNotification(now)

                val fg = detector.getForegroundPackage()
                if (fg != null && fg != packageName && GatePolicy.shouldGate(fg, focusStore, now)) {
                    val overlayAllowed = hasOverlayPermission(this)
                    if (overlayAllowed && !overlayGate.isShowing()) {
                        showGateOverlayForPackage(fg, now)
                    } else if (!overlayAllowed) {
                        maybeNotifyInterrupt(fg, focusStore.isSessionActive(now))
                    }
                }

                delay(POLL_INTERVAL_MS)
            } catch (t: Throwable) {
                Log.e(TAG, "Watcher loop error", t)
                delay(ERROR_RETRY_MS)
            }
        }
    }

    private fun showGateOverlayForPackage(packageName: String, nowMs: Long) {
        overlayGate.show(
            packageName = packageName,
            sessionActive = focusStore.isSessionActive(nowMs),
            sessionRemainingMs = focusStore.remainingSessionMs(nowMs),
            onContinueRequested = { pkg ->
                GatePolicy.recordUnlocked(pkg)
                overlayGate.hide()
            },
            onCloseAppRequested = {
                sendUserHome()
                overlayGate.hide()
            },
            onStartSessionRequested = {
                focusStore.startSession25(System.currentTimeMillis())
                updateOngoingNotification(System.currentTimeMillis())
                sendUserHome()
                overlayGate.hide()
            }
        )
    }

    private fun sendUserHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(homeIntent)
    }

    private fun initializeDefaultBlockedApps() {
        runCatching {
            val installedPackages = blockedAppRepository
                .getLaunchableApps()
                .map { it.packageName }
                .toSet()
            focusStore.initializeDefaultsIfNeeded(installedPackages)
        }.onFailure { throwable ->
            Log.e(TAG, "Failed to initialize default blocked packages", throwable)
        }
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
            NotificationChannel(
                CHANNEL_ID,
                "Focus Protection",
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    private fun updateOngoingNotification(nowMs: Long) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildOngoingNotification(nowMs))
    }

    private fun buildOngoingNotification(nowMs: Long): Notification {
        val sessionActive = focusStore.isSessionActive(nowMs)
        val title = if (sessionActive) {
            getString(R.string.notif_focus_active_title)
        } else {
            getString(R.string.notif_focus_protection_title)
        }
        val text = if (sessionActive) {
            getString(
                R.string.notif_focus_remaining,
                formatDurationMmSs(focusStore.remainingSessionMs(nowMs))
            )
        } else {
            getString(R.string.notif_focus_protection_text)
        }

        return buildNotification(
            title = title,
            text = text,
            ongoing = true
        )
    }

    private fun buildNotification(title: String, text: String, ongoing: Boolean): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(ongoing)
            .build()
    }

    private fun maybeNotifyInterrupt(packageName: String, sessionActive: Boolean) {
        val now = System.currentTimeMillis()
        if (packageName == lastInterruptPackage && (now - lastInterruptAtMs) < INTERRUPT_COOLDOWN_MS) {
            return
        }

        lastInterruptPackage = packageName
        lastInterruptAtMs = now

        val message = if (sessionActive) {
            getString(R.string.notif_interrupt_session_text, packageName)
        } else {
            getString(R.string.notif_interrupt_text, packageName)
        }

        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(
            INTERRUPT_NOTIF_ID,
            buildNotification(
                title = getString(R.string.notif_interrupt_title),
                text = message,
                ongoing = false
            )
        )
    }

    companion object {
        const val ACTION_START_PROTECTION = "com.zack.focus.action.START_PROTECTION"
        const val ACTION_START_SESSION_25 = "com.zack.focus.action.START_SESSION_25"
        const val ACTION_REFRESH_STATE = "com.zack.focus.action.REFRESH_STATE"

        private const val TAG = "FocusWatcher"
        private const val CHANNEL_ID = "focus_watcher"
        private const val NOTIF_ID = 1001
        private const val INTERRUPT_NOTIF_ID = 1002
        private const val INTERRUPT_COOLDOWN_MS = 60_000L
        private const val POLL_INTERVAL_MS = 1000L
        private const val ERROR_RETRY_MS = 1500L

        fun startProtection(context: Context) {
            startServiceWithAction(context, ACTION_START_PROTECTION)
        }

        fun startSession25(context: Context) {
            startServiceWithAction(context, ACTION_START_SESSION_25)
        }

        fun refreshState(context: Context) {
            startServiceWithAction(context, ACTION_REFRESH_STATE)
        }

        private fun startServiceWithAction(context: Context, action: String) {
            val intent = Intent(context, WatcherService::class.java).setAction(action)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
