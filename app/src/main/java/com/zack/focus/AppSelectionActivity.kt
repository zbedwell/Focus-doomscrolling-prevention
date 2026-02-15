package com.zack.focus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppSelectionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AppSelectionScreen()
            }
        }
    }
}

@Composable
private fun AppSelectionScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val focusStore = remember(context) { FocusStore(context) }
    val blockedAppRepository = remember(context) { BlockedAppRepository(context) }

    var loading by remember { mutableStateOf(true) }
    var apps by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }
    var blockedPackages by remember { mutableStateOf(focusStore.getBlockedPackages()) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val loadedApps = withContext(Dispatchers.Default) {
            blockedAppRepository.getLaunchableApps()
        }
        focusStore.initializeDefaultsIfNeeded(loadedApps.map { it.packageName }.toSet())
        apps = loadedApps
        blockedPackages = focusStore.getBlockedPackages()
        loading = false
    }

    val filteredApps = remember(apps, query) {
        if (query.isBlank()) {
            apps
        } else {
            apps.filter { app ->
                app.label.contains(query, ignoreCase = true) ||
                    app.packageName.contains(query, ignoreCase = true)
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.app_selection_title),
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(
                    R.string.app_selection_selected_count,
                    blockedPackages.size
                ),
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.app_selection_search_label)) },
                singleLine = true
            )

            Spacer(Modifier.height(12.dp))

            if (loading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        val checked = blockedPackages.contains(app.packageName)
                        AppToggleRow(
                            app = app,
                            checked = checked,
                            onToggle = { shouldBlock ->
                                focusStore.setBlockedPackage(app.packageName, shouldBlock)
                                blockedPackages = if (shouldBlock) {
                                    blockedPackages + app.packageName
                                } else {
                                    blockedPackages - app.packageName
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppToggleRow(
    app: InstalledAppInfo,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) },
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(text = app.label, style = MaterialTheme.typography.bodyLarge)
                Text(text = app.packageName, style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = checked,
                onCheckedChange = onToggle
            )
        }
    }
}
