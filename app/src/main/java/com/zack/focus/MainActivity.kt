package com.zack.focus

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val requestPostNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPostNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            MaterialTheme {
                FocusHomeScreen(
                    onStartProtection = { WatcherService.startProtection(this) },
                    onSelectApps = {
                        startActivity(Intent(this, AppSelectionActivity::class.java))
                    },
                    onStartSession = {
                        WatcherService.startSession25(this)
                        startActivity(Intent(this, FocusTimerActivity::class.java))
                    }
                )
            }
        }
    }
}

@Composable
private fun FocusHomeScreen(
    onStartProtection: () -> Unit,
    onSelectApps: () -> Unit,
    onStartSession: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusStore = remember(context) { FocusStore(context) }
    val blockedAppRepository = remember(context) { BlockedAppRepository(context) }

    var usageEnabled by remember { mutableStateOf(hasUsageAccess(context)) }
    var overlayEnabled by remember { mutableStateOf(hasOverlayPermission(context)) }
    var blockedCount by remember { mutableIntStateOf(0) }
    var sessionRemainingMs by remember { mutableLongStateOf(0L) }

    val refreshState = {
        usageEnabled = hasUsageAccess(context)
        overlayEnabled = hasOverlayPermission(context)
        blockedCount = focusStore.getBlockedPackages().size
        sessionRemainingMs = focusStore.remainingSessionMs(System.currentTimeMillis())
    }

    LaunchedEffect(Unit) {
        val installedPackages = withContext(Dispatchers.Default) {
            blockedAppRepository.getLaunchableApps().map { it.packageName }.toSet()
        }
        focusStore.initializeDefaultsIfNeeded(installedPackages)
        refreshState()
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            sessionRemainingMs = focusStore.remainingSessionMs(System.currentTimeMillis())
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.main_subtitle),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.main_blocked_count, blockedCount),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(4.dp))
            val sessionText = if (sessionRemainingMs > 0L) {
                stringResource(R.string.main_session_active, formatDurationMmSs(sessionRemainingMs))
            } else {
                stringResource(R.string.main_session_inactive)
            }
            Text(text = sessionText, style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(20.dp))

            PermissionCard(
                title = stringResource(R.string.permission_usage_title),
                status = if (usageEnabled) {
                    stringResource(R.string.permission_enabled)
                } else {
                    stringResource(R.string.permission_usage_disabled)
                },
                onEnable = { context.startActivity(usageAccessSettingsIntent()) },
                onRecheck = { usageEnabled = hasUsageAccess(context) }
            )

            Spacer(Modifier.height(12.dp))

            PermissionCard(
                title = stringResource(R.string.permission_overlay_title),
                status = if (overlayEnabled) {
                    stringResource(R.string.permission_enabled)
                } else {
                    stringResource(R.string.permission_overlay_disabled)
                },
                onEnable = { context.startActivity(overlaySettingsIntent(context)) },
                onRecheck = { overlayEnabled = hasOverlayPermission(context) }
            )

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = onSelectApps,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.main_select_apps))
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    onStartSession()
                    refreshState()
                },
                enabled = usageEnabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.start_25_min_focus))
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    onStartProtection()
                    refreshState()
                },
                enabled = usageEnabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.main_start_protection))
            }
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    status: String,
    onEnable: () -> Unit,
    onRecheck: () -> Unit
) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(status)
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = onEnable) {
                    Text(stringResource(R.string.permission_enable_button))
                }
                OutlinedButton(onClick = onRecheck) {
                    Text(stringResource(R.string.permission_recheck_button))
                }
            }
        }
    }
}
