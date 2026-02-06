package com.zack.focus

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

class OverlayGate(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null

    fun isShowing(): Boolean = overlayView != null

    fun show(packageName: String, onUnlock: () -> Unit) {
        if (overlayView != null) return

        mainHandler.post {
            if (overlayView != null) return@post

            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

            val view = ComposeView(context).apply {
                setContent {
                    MaterialTheme {
                        OverlayGateScreen(
                            packageName = packageName,
                            onUnlock = onUnlock
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
    onUnlock: () -> Unit
) {
    var holding by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(holding) {
        if (!holding) {
            progress = 0f
            return@LaunchedEffect
        }

        val totalMs = 3000
        val stepMs = 50
        var elapsed = 0

        while (holding && elapsed < totalMs) {
            delay(stepMs.toLong())
            elapsed += stepMs
            progress = elapsed / totalMs.toFloat()
        }

        if (holding && progress >= 1f) onUnlock()
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("Focus", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(8.dp))
            Text("Blocked content detected", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(packageName, style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(24.dp))
            LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))

            Button(
                onClick = { holding = true },
                enabled = !holding,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Hold to unlock")
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "This pause is the point — it breaks autopilot.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
