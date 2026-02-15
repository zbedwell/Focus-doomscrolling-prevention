package com.zack.focus

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

class OverlayGate(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null

    fun isShowing(): Boolean = overlayView != null

    fun show(
        packageName: String,
        sessionActive: Boolean,
        sessionRemainingMs: Long,
        onContinueRequested: (String) -> Unit,
        onCloseAppRequested: (String) -> Unit,
        onStartSessionRequested: (String) -> Unit
    ) {
        if (overlayView != null) return

        mainHandler.post {
            if (overlayView != null) return@post

            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

            val view = ComposeView(context).apply {
                setContent {
                    MaterialTheme {
                        OverlayGateScreen(
                            packageName = packageName,
                            sessionActive = sessionActive,
                            sessionRemainingMs = sessionRemainingMs,
                            onContinueRequested = onContinueRequested,
                            onCloseAppRequested = onCloseAppRequested,
                            onStartSessionRequested = onStartSessionRequested
                        )
                    }
                }
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP
            }

            overlayView = view
            windowManager?.addView(view, params)
        }
    }

    fun hide() {
        mainHandler.post {
            val view = overlayView ?: return@post
            val wm = windowManager ?: return@post

            wm.removeView(view)
            overlayView = null
            windowManager = null
        }
    }
}

@Composable
private fun OverlayGateScreen(
    packageName: String,
    sessionActive: Boolean,
    sessionRemainingMs: Long,
    onContinueRequested: (String) -> Unit,
    onCloseAppRequested: (String) -> Unit,
    onStartSessionRequested: (String) -> Unit
) {
    var countdownRunning by remember { mutableStateOf(false) }
    var secondsRemaining by remember { mutableIntStateOf(CONTINUE_DELAY_SECONDS) }

    LaunchedEffect(countdownRunning, sessionActive) {
        if (sessionActive || !countdownRunning) {
            if (!sessionActive) secondsRemaining = CONTINUE_DELAY_SECONDS
            return@LaunchedEffect
        }

        while (countdownRunning && secondsRemaining > 0) {
            delay(1000L)
            secondsRemaining -= 1
        }

        if (countdownRunning && secondsRemaining == 0) {
            onContinueRequested(packageName)
        }
    }

    val progress = ((CONTINUE_DELAY_SECONDS - secondsRemaining).coerceAtLeast(0)).toFloat() /
        CONTINUE_DELAY_SECONDS.toFloat()

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.overlay_title),
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.overlay_subtitle),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
            Text(packageName, style = MaterialTheme.typography.bodySmall)

            Spacer(Modifier.height(24.dp))
            LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))

            if (sessionActive) {
                Text(
                    text = stringResource(
                        R.string.overlay_focus_active_remaining,
                        formatDurationMmSs(sessionRemainingMs)
                    ),
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { onCloseAppRequested(packageName) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.overlay_close_app))
                }
            } else {
                val continueLabel = if (countdownRunning) {
                    stringResource(R.string.overlay_continue_countdown, secondsRemaining)
                } else {
                    stringResource(R.string.overlay_continue_delay, CONTINUE_DELAY_SECONDS)
                }

                Button(
                    onClick = { countdownRunning = true },
                    enabled = !countdownRunning,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(continueLabel)
                }

                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onCloseAppRequested(packageName) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.overlay_close_app))
                }

                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { onStartSessionRequested(packageName) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.overlay_start_focus))
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.overlay_footer),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private const val CONTINUE_DELAY_SECONDS = 15
