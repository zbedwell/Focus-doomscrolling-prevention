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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.zack.focus.ui.theme.FocusTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val focusStore = FocusStore(this)
        focusStore.initializeDefaultsIfNeeded()

        setContent {
            FocusTheme {
                AppRoot(focusStore)
            }
        }
    }
}

private sealed class Screen {
    object Onboarding : Screen()
    object Home : Screen()
    object EndFocus : Screen()
}

@Composable
private fun AppRoot(focusStore: FocusStore) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var usageOk by remember { mutableStateOf(hasUsageAccess(context)) }
    var overlayOk by remember { mutableStateOf(hasOverlayPermission(context)) }

    val refreshPermissions = {
        usageOk = hasUsageAccess(context)
        overlayOk = hasOverlayPermission(context)
    }

    // If Focus Mode was active when the app was last closed/killed, restart the service.
    LaunchedEffect(Unit) {
        if (focusStore.isFocusModeActive()) {
            WatcherService.start(context)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshPermissions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionsGranted = usageOk && overlayOk
    var screen by remember { mutableStateOf<Screen>(if (permissionsGranted) Screen.Home else Screen.Onboarding) }

    // Push to Home once both permissions are granted from Onboarding
    if (permissionsGranted && screen == Screen.Onboarding) {
        screen = Screen.Home
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        when (screen) {
            is Screen.Onboarding -> OnboardingScreen(
                usageOk = usageOk,
                overlayOk = overlayOk,
                onEnableUsage = { context.startActivity(usageAccessSettingsIntent()) },
                onEnableOverlay = { context.startActivity(overlaySettingsIntent(context)) },
                onContinue = { screen = Screen.Home }
            )
            is Screen.Home -> HomeScreen(
                focusStore = focusStore,
                onEndFocus = { screen = Screen.EndFocus }
            )
            is Screen.EndFocus -> EndFocusScreen(
                onCancelled = { screen = Screen.Home },
                onCompleted = {
                    focusStore.setFocusModeActive(false)
                    WatcherService.stop(context)
                    screen = Screen.Home
                }
            )
        }
    }
}

// ─── Onboarding ──────────────────────────────────────────────────────────────

@Composable
private fun OnboardingScreen(
    usageOk: Boolean,
    overlayOk: Boolean,
    onEnableUsage: () -> Unit,
    onEnableOverlay: () -> Unit,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Welcome to Focus", style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text(
            "Focus helps protect your attention by blocking short-form content " +
                    "and distracting apps while Focus Mode is active.",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(32.dp))

        PermissionStep(
            number = "1",
            title = "Enable Usage Access",
            description = "Lets Focus detect which app is in the foreground. " +
                    "Used only to identify distracting apps.",
            granted = usageOk,
            onEnable = onEnableUsage
        )
        Spacer(Modifier.height(16.dp))
        PermissionStep(
            number = "2",
            title = "Allow Overlay Permission",
            description = "Lets Focus appear over other apps to show the blocking screen.",
            granted = overlayOk,
            onEnable = onEnableOverlay
        )

        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onContinue,
            enabled = usageOk && overlayOk,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continue to Focus")
        }
        if (!usageOk || !overlayOk) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Both permissions are required for blocking to work.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun PermissionStep(
    number: String,
    title: String,
    description: String,
    granted: Boolean,
    onEnable: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (granted) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$number.", style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.padding(4.dp))
                Text(title, style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(8.dp))
            Text(description, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            if (granted) {
                Text("Granted", color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge)
            } else {
                Button(onClick = onEnable) { Text("Enable") }
            }
        }
    }
}

// ─── Home ─────────────────────────────────────────────────────────────────────

@Composable
private fun HomeScreen(focusStore: FocusStore, onEndFocus: () -> Unit) {
    val context = LocalContext.current
    var focusActive by remember { mutableStateOf(focusStore.isFocusModeActive()) }
    val message = remember { MotivationMessages.getRandom() }
    val blockedPackages = remember { focusStore.getBlockedPackages() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(Modifier.height(32.dp))

        Text("Focus", style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary)
        Text("Protect your attention.", style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary)

        Spacer(Modifier.height(32.dp))

        // Status card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (focusActive) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    text = if (focusActive) "Focus Mode is active" else "Focus Mode is off",
                    style = MaterialTheme.typography.titleLarge
                )
                if (focusActive) {
                    Spacer(Modifier.height(4.dp))
                    Text("Blocked apps are being monitored.",
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        if (!focusActive) {
            Button(
                onClick = {
                    focusStore.setFocusModeActive(true)
                    WatcherService.start(context)
                    focusActive = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start Focus", style = MaterialTheme.typography.titleMedium)
            }
        } else {
            Button(
                onClick = onEndFocus,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Text("End Focus", style = MaterialTheme.typography.titleMedium)
            }
        }

        Spacer(Modifier.height(32.dp))

        // Motivation message
        Card(modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Column(Modifier.padding(16.dp)) {
                Text(message, style = MaterialTheme.typography.bodyLarge)
            }
        }

        Spacer(Modifier.height(24.dp))

        // Blocked apps list
        Text("Blocked apps", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        blockedPackages.forEach { pkg ->
            val label = FocusStore.BLOCKED_APP_LABELS[pkg] ?: pkg
            Text("• $label", style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 2.dp))
        }

        Spacer(Modifier.height(16.dp))

        // Permission warnings
        val usageOk = hasUsageAccess(context)
        val overlayOk = hasOverlayPermission(context)
        if (!usageOk) {
            Text("Usage Access is required for Focus Mode.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
        }
        if (!overlayOk) {
            Text("Overlay permission is required for blocking to work.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(32.dp))
    }
}

// ─── End Focus Flow ───────────────────────────────────────────────────────────

private sealed class EndStep {
    object Confirm : EndStep()
    object Timer : EndStep()
    object Reason : EndStep()
    object Phrase : EndStep()
}

private val END_REASON_OPTIONS = listOf(
    "I completed what I needed to do",
    "I have an emergency",
    "I want to check something quickly",
    "I changed my mind",
    "Other"
)

private const val END_PHRASE = "End my focus session"
private const val END_PAUSE_SECONDS = 30

@Composable
private fun EndFocusScreen(onCancelled: () -> Unit, onCompleted: () -> Unit) {
    var step by remember { mutableStateOf<EndStep>(EndStep.Confirm) }

    // System Back during the end-focus flow steps backward, not out of the app.
    BackHandler {
        step = when (step) {
            is EndStep.Confirm -> { onCancelled(); EndStep.Confirm }
            is EndStep.Timer -> EndStep.Confirm
            is EndStep.Reason -> EndStep.Timer
            is EndStep.Phrase -> EndStep.Reason
        }
    }

    when (step) {
        is EndStep.Confirm -> EndConfirmStep(
            onCancel = onCancelled,
            onProceed = { step = EndStep.Timer }
        )
        is EndStep.Timer -> EndTimerStep(
            onDone = { step = EndStep.Reason }
        )
        is EndStep.Reason -> EndReasonStep(
            onDone = { step = EndStep.Phrase }
        )
        is EndStep.Phrase -> EndPhraseStep(
            onCompleted = onCompleted,
            onCancel = onCancelled
        )
    }
}

@Composable
private fun EndConfirmStep(onCancel: () -> Unit, onProceed: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("You started Focus Mode to protect your attention.",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("Do you really want to turn it off?",
            style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(48.dp))
        Button(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("Keep Going")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onProceed, modifier = Modifier.fillMaxWidth()) {
            Text("I Want to End Focus")
        }
    }
}

@Composable
private fun EndTimerStep(onDone: () -> Unit) {
    var secondsLeft by remember { mutableIntStateOf(END_PAUSE_SECONDS) }
    LaunchedEffect(Unit) {
        while (secondsLeft > 0) { delay(1000L); secondsLeft-- }
    }
    val progress = (END_PAUSE_SECONDS - secondsLeft).toFloat() / END_PAUSE_SECONDS.toFloat()

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Take a moment.", style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text("Wait 30 seconds before ending your session.",
            style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(40.dp))
        androidx.compose.material3.CircularProgressIndicator(progress = { progress },
            modifier = Modifier.padding(16.dp))
        Spacer(Modifier.height(8.dp))
        Text(if (secondsLeft > 0) "$secondsLeft seconds" else "Done",
            style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(40.dp))
        Button(onClick = onDone, enabled = secondsLeft == 0, modifier = Modifier.fillMaxWidth()) {
            Text(if (secondsLeft > 0) "Please wait..." else "Continue")
        }
    }
}

@Composable
private fun EndReasonStep(onDone: () -> Unit) {
    var selected by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Why are you ending Focus Mode?",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(24.dp))
        Column(modifier = Modifier.selectableGroup()) {
            END_REASON_OPTIONS.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = selected == option,
                            onClick = { selected = option },
                            role = Role.RadioButton
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selected == option, onClick = null)
                    Spacer(Modifier.padding(horizontal = 8.dp))
                    Text(option, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        Spacer(Modifier.height(32.dp))
        Button(onClick = onDone, enabled = selected.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text("Next")
        }
    }
}

@Composable
private fun EndPhraseStep(onCompleted: () -> Unit, onCancel: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    val matches = typed.trim().equals(END_PHRASE, ignoreCase = true)

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Confirm ending your session.",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text("Type the phrase below:", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(12.dp))
        Text("\"$END_PHRASE\"",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = typed,
            onValueChange = { typed = it },
            label = { Text("Type the phrase") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = typed.isNotEmpty() && !matches
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onCompleted,
            enabled = matches,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text("End Focus Session")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("Actually, Keep Going")
        }
    }
}
