package com.zack.focus

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.zack.focus.ui.theme.FocusTheme
import kotlinx.coroutines.delay

class GateActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: ""

        setContent {
            FocusTheme {
                BypassFlow(
                    packageName = pkg,
                    onGoBack = { goHome() },
                    onUnlocked = {
                        TemporaryUnlockManager.grant(pkg)
                        finish()
                    }
                )
            }
        }
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    companion object {
        const val EXTRA_PACKAGE = "pkg"
    }
}

private sealed class BypassStep {
    object Gate : BypassStep()
    object Timer : BypassStep()
    object Reflection : BypassStep()
    object Phrase : BypassStep()
}

private val REFLECTION_OPTIONS = listOf(
    "I am bored",
    "I am stressed",
    "I am avoiding work",
    "I want entertainment",
    "I opened it automatically",
    "Other"
)

private const val CONFIRMATION_PHRASE = "I choose this intentionally"
private const val PAUSE_SECONDS = 30

@Composable
private fun BypassFlow(
    packageName: String,
    onGoBack: () -> Unit,
    onUnlocked: () -> Unit
) {
    var step by remember { mutableStateOf<BypassStep>(BypassStep.Gate) }

    // Back on Gate → go home; Back on friction steps → previous step (re-encounter the friction).
    BackHandler {
        step = when (step) {
            is BypassStep.Gate -> { onGoBack(); BypassStep.Gate }
            is BypassStep.Timer -> BypassStep.Gate
            is BypassStep.Reflection -> BypassStep.Timer
            is BypassStep.Phrase -> BypassStep.Reflection
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        when (step) {
            is BypassStep.Gate -> GateStep(
                packageName = packageName,
                onGoBack = onGoBack,
                onContinue = { step = BypassStep.Timer }
            )
            is BypassStep.Timer -> TimerStep(
                onDone = { step = BypassStep.Reflection }
            )
            is BypassStep.Reflection -> ReflectionStep(
                onDone = { step = BypassStep.Phrase }
            )
            is BypassStep.Phrase -> PhraseStep(
                onUnlocked = onUnlocked
            )
        }
    }
}

@Composable
private fun GateStep(
    packageName: String,
    onGoBack: () -> Unit,
    onContinue: () -> Unit
) {
    val appLabel = FocusStore.BLOCKED_APP_LABELS[packageName] ?: packageName

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Take a breath.", style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("You opened $appLabel, an app you chose to block.",
            style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Text("Is this really what you want right now?",
            style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        Text(MotivationMessages.getRandom(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(48.dp))

        Button(onClick = onGoBack, modifier = Modifier.fillMaxWidth()) {
            Text("Go Back")
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        ) {
            Text("I Still Want to Continue")
        }
    }
}

@Composable
private fun TimerStep(onDone: () -> Unit) {
    var secondsLeft by remember { mutableIntStateOf(PAUSE_SECONDS) }
    var started by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        started = true
        while (secondsLeft > 0) {
            delay(1000L)
            secondsLeft--
        }
    }

    val progress = (PAUSE_SECONDS - secondsLeft).toFloat() / PAUSE_SECONDS.toFloat()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Wait 30 seconds before continuing.",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text("Most urges fade when you pause.",
            style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(40.dp))

        CircularProgressIndicator(progress = { progress }, modifier = Modifier.padding(16.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (secondsLeft > 0) "$secondsLeft seconds" else "Done",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(40.dp))

        Button(
            onClick = onDone,
            enabled = secondsLeft == 0,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (secondsLeft > 0) "Please wait..." else "Continue")
        }
    }
}

@Composable
private fun ReflectionStep(onDone: () -> Unit) {
    var selected by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Why do you want to open this right now?",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(24.dp))

        Column(modifier = Modifier.selectableGroup()) {
            REFLECTION_OPTIONS.forEach { option ->
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
        Button(
            onClick = onDone,
            enabled = selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Next")
        }
    }
}

@Composable
private fun PhraseStep(onUnlocked: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    val matches = typed.trim().equals(CONFIRMATION_PHRASE, ignoreCase = true)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("One last step.", style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text("Type the phrase below to confirm this is intentional:",
            style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        Text(
            text = "\"$CONFIRMATION_PHRASE\"",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary
        )
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
            onClick = onUnlocked,
            enabled = matches,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Unlock for 5 Minutes")
        }
        Spacer(Modifier.height(12.dp))
        if (!matches && typed.isNotEmpty()) {
            Text(
                "Keep typing the exact phrase above.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
