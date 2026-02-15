package com.zack.focus

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

// Legacy activity kept for fallback. OverlayGate is the active MVP interruption flow.
@Deprecated("Legacy gate activity. OverlayGate is used by the service.")
class GateActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: "unknown"

        setContent {
            MaterialTheme {
                GateScreen(
                    packageName = pkg,
                    onUnlock = {
                        GatePolicy.recordUnlocked(pkg)
                        finish()
                    },
                    onGoBack = {
                        // sends app to background; user can go home
                        (this as Activity).moveTaskToBack(true)
                        finish()
                    }
                )
            }
        }
    }

    companion object {
        const val EXTRA_PACKAGE = "pkg"
    }
}

@Composable
private fun GateScreen(
    packageName: String,
    onUnlock: () -> Unit,
    onGoBack: () -> Unit
) {
    var holding by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(holding) {
        if (!holding) {
            progress = 0f
            return@LaunchedEffect
        }

        val totalMs = 4000
        val stepMs = 40
        var elapsed = 0

        while (holding && elapsed < totalMs) {
            delay(stepMs.toLong())
            elapsed += stepMs
            progress = elapsed.toFloat() / totalMs.toFloat()
        }

        if (holding && progress >= 1f) onUnlock()
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Focus", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(8.dp))
            Text("Hold to unlock", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text("App: $packageName", style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(24.dp))
            LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { holding = true },
                    enabled = !holding
                ) { Text("Hold") }

                OutlinedButton(onClick = onGoBack) { Text("Go back") }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Tip: This pause is the point. Autopilot loses.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
