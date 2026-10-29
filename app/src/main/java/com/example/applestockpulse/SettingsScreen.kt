package com.example.applestockpulse

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    modifier: Modifier,
    preferences: TrackerPreferences,
    historyDao: HistoryDao,
    isRunning: Boolean,
    hasNotificationPermission: Boolean,
    isIgnoringBatteryOptimizations: Boolean,
    eventCount: Int,
    unknownSkus: List<String>,
    actions: AppActions,
    onLocationSaved: (SearchLocation) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exportCsv = rememberCsvExporter(historyDao)
    val backup = rememberBackupActions()

    var postal by remember { mutableStateOf(SearchLocation.formatPostalCode(preferences.getPostalCode())) }
    var radius by remember { mutableStateOf(preferences.getRadiusKm()) }
    var storeFilter by remember { mutableStateOf(preferences.getStoreFilter()) }
    var confirmClear by remember { mutableStateOf(false) }
    var checkInterval by remember { mutableStateOf(preferences.getCheckIntervalSeconds()) }
    var canUseFullScreenAlarm by remember { mutableStateOf(AlertNotifier.canUseFullScreenAlarm(context)) }
    LifecycleResumeEffect(Unit) {
        canUseFullScreenAlarm = AlertNotifier.canUseFullScreenAlarm(context)
        onPauseOrDispose { }
    }
    val postalValid = TrackerPreferences.isValidPostalCode(postal)
    val changed = TrackerPreferences.cleanPostalCode(postal) != preferences.getCleanPostalCode() ||
        radius != preferences.getRadiusKm() ||
        TrackerPreferences.parseStoreFilter(storeFilter) != preferences.getParsedStoreFilter()

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SettingsCard("Default location") {
            Text("Used by every device group that has no location of its own, and by Browse.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = postal,
                onValueChange = { postal = it.uppercase() },
                label = { Text("Postal code") },
                isError = !postalValid,
                supportingText = if (!postalValid) {
                    { Text("Use a Canadian postal code, e.g. V6C 1T2") }
                } else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text("Search radius", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            RadiusChips(selected = radius, onSelect = { radius = it })
            OutlinedTextField(
                value = storeFilter,
                onValueChange = { storeFilter = it.uppercase() },
                label = { Text("Only these stores (optional)") },
                placeholder = { Text("e.g. R601, R488") },
                supportingText = { Text("Leave blank for all stores in the radius") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                enabled = postalValid && changed,
                onClick = {
                    preferences.setPostalCode(postal)
                    preferences.setRadiusKm(radius)
                    preferences.setStoreFilter(TrackerPreferences.parseStoreFilter(storeFilter).joinToString(", "))
                    onLocationSaved(preferences.getDefaultLocation())
                    Toast.makeText(context, "Location saved", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save location") }
        }

        SettingsCard("Alarms") {
            StatusRow(
                title = "Notifications",
                status = if (hasNotificationPermission) "On" else "Off — alarms cannot sound",
                ok = hasNotificationPermission,
                action = if (hasNotificationPermission) "Settings" else "Allow",
                onAction = { if (hasNotificationPermission) openNotificationSettings(context) else actions.requestNotifications() }
            )
            StatusRow(
                title = "Full-screen alarm",
                status = if (canUseFullScreenAlarm) "On — the alarm wakes the screen, also when locked"
                else "Off — the alarm shows only as a notification",
                ok = canUseFullScreenAlarm,
                action = if (canUseFullScreenAlarm) null else "Allow",
                onAction = { openFullScreenAlarmSettings(context) }
            )
            StatusRow(
                title = "Test alarm",
                status = "Rings at full alarm volume until you stop it (${AlarmPlayer.MAX_RING_MS / 60_000} min at most)",
                ok = true,
                action = "Test",
                onAction = {
                    if (hasNotificationPermission) AlertNotifier.postTestAlarm(context) else actions.requestNotifications()
                }
            )
            Text("A store/device pair alarms at most once every 10 minutes, so stock that flickers does not repeat the alarm.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SettingsCard("Other devices (ntfy)") {
            NtfySettings(preferences)
        }

        SettingsCard("Background") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Monitoring", fontWeight = FontWeight.SemiBold)
                    Text(if (isRunning) "On · checks about every $checkInterval s" else "Off", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = isRunning, onCheckedChange = actions.startOrStop)
            }
            Text("Check every", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TrackerPreferences.CHECK_INTERVAL_OPTIONS_SEC.forEach { seconds ->
                    FilterChip(
                        selected = seconds == checkInterval,
                        onClick = {
                            checkInterval = seconds
                            preferences.setCheckIntervalSeconds(seconds)
                        },
                        label = { Text("$seconds s") }
                    )
                }
            }
            Text(
                when (checkInterval) {
                    30 -> "Fastest. Apple can block your network for a while; use it only during a sale you expect."
                    45 -> "Recommended. Fast enough for most restocks, with less risk of an Apple block."
                    else -> "Safest. Use it when nothing is expected, or after Apple blocked your network."
                } + " The change starts after the next check.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            StatusRow(
                title = "Battery",
                status = if (isIgnoringBatteryOptimizations) "Unrestricted — checks continue with the screen off" else "Optimized — Android may stop checks",
                ok = isIgnoringBatteryOptimizations,
                action = if (isIgnoringBatteryOptimizations) null else "Setup",
                onAction = { requestBatteryExemption(context) }
            )
            Text("While monitoring is on, the phone stays awake between checks. This uses more battery; stop monitoring when you do not need it.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SettingsCard("History") {
            Text("Keeps ${TrackerPreferences.HISTORY_RETENTION_DAYS} days · $eventCount change(s) saved", fontSize = 13.sp)
            Text(
                "Back up saves history, alerts and settings to a file. Keep the file in Drive or Downloads. " +
                    "After a reinstall or on a new phone, tap Restore and choose the file.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = backup.export, modifier = Modifier.weight(1f)) { Text("Back up") }
                OutlinedButton(onClick = backup.restore, modifier = Modifier.weight(1f)) { Text("Restore") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = exportCsv, modifier = Modifier.weight(1f)) { Text("Export CSV") }
                OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.weight(1f)) { Text("Clear history") }
            }
        }

        SettingsCard("About") {
            Text("AppleStockPulse ${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.SemiBold)
            Text(
                if (unknownSkus.isEmpty()) "Part numbers: no problems found in the last check."
                else "Apple does not recognize: ${unknownSkus.joinToString()}. Correct them in ProductCatalog.kt.",
                fontSize = 12.sp,
                color = if (unknownSkus.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else StockColors.Error
            )
            Text("Stock data comes from Apple's public in-store pickup page. Apple can change or block it at any time.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.height(LocalDockPadding.current))
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear history?") },
            text = { Text("This deletes all recent changes and patterns data. It cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch {
                        withContext(Dispatchers.IO) { historyDao.clearAll() }
                        Toast.makeText(context, "History cleared", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Clear", color = StockColors.Error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
}

private class BackupActions(val export: () -> Unit, val restore: () -> Unit)

@Composable
private fun rememberBackupActions(): BackupActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val message = withContext(Dispatchers.IO) {
                try {
                    "Backup saved: ${AppBackup.export(context, uri)} change(s)"
                } catch (e: Exception) {
                    Log.e("SettingsScreen", "Backup failed", e)
                    "Backup failed: ${e.localizedMessage}"
                }
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val message = withContext(Dispatchers.IO) {
                try {
                    val result = AppBackup.restore(context, uri)
                    "Restored ${result.eventsAdded} new change(s) of ${result.eventsInFile} and ${result.rules} alert(s)"
                } catch (e: AppBackup.InvalidBackupException) {
                    e.message ?: "Not a backup file"
                } catch (e: Exception) {
                    Log.e("SettingsScreen", "Restore failed", e)
                    "Restore failed: ${e.localizedMessage}"
                }
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            requestPoll(context)
        }
    }

    return BackupActions(
        export = { exportLauncher.launch(AppBackup.suggestedFileName()) },
        // Some file pickers label JSON as plain text or octet-stream
        restore = { restoreLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
    )
}

/** ntfy topic for alarms on a laptop, tablet or second phone. */
@Composable
private fun NtfySettings(preferences: TrackerPreferences) {
    val context = LocalContext.current
    var savedTopic by remember { mutableStateOf(preferences.getNtfyTopic()) }
    var topic by remember { mutableStateOf(savedTopic) }
    val target = NtfyNotifier.parseTarget(topic)
    val topicValid = topic.isBlank() || target != null

    Text(
        "Each alarm is also sent to this ntfy topic. Install the ntfy app on another phone, tablet or laptop and subscribe to the same topic.",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
        "Anyone who knows the topic can read it. Use a long, random name.",
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    OutlinedTextField(
        value = topic,
        onValueChange = { topic = it.trim() },
        label = { Text("ntfy topic") },
        placeholder = { Text("e.g. applestock-x7k2p9q4") },
        isError = !topicValid,
        supportingText = {
            Text(
                if (topicValid) "Leave blank to turn off. A full https:// topic URL also works."
                else "Use letters, digits, - and _ only, or an https:// topic URL"
            )
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = { topic = randomNtfyTopic() }, modifier = Modifier.weight(1f)) {
            Text("Generate")
        }
        OutlinedButton(
            enabled = target != null,
            onClick = {
                NtfyNotifier.publish(
                    target,
                    title = "AppleStockPulse test",
                    message = "Alerts on this device work.",
                    clickUrl = null
                ) { _, message ->
                    ContextCompat.getMainExecutor(context).execute {
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                }
            },
            modifier = Modifier.weight(1f)
        ) {
            Text("Send test")
        }
    }
    Button(
        enabled = topicValid && topic != savedTopic,
        onClick = {
            preferences.setNtfyTopic(topic)
            savedTopic = topic
            Toast.makeText(context, if (topic.isBlank()) "ntfy alerts off" else "ntfy topic saved", Toast.LENGTH_SHORT).show()
        },
        modifier = Modifier.fillMaxWidth()
    ) { Text("Save topic") }
}

private fun randomNtfyTopic(): String {
    val chars = "abcdefghijkmnpqrstuvwxyz23456789"
    return "applestock-" + (1..16).map { chars.random() }.joinToString("")
}

private fun openFullScreenAlarmSettings(context: Context) {
    try {
        context.startActivity(AlertNotifier.fullScreenAlarmSettingsIntent(context))
    } catch (e: Exception) {
        Toast.makeText(context, "Open Settings > Apps > AppleStockPulse and allow full-screen notifications.", Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel(title)
            content()
        }
    }
}

@Composable
private fun StatusRow(title: String, status: String, ok: Boolean, action: String?, onAction: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(status, fontSize = 12.sp, color = if (ok) MaterialTheme.colorScheme.onSurfaceVariant else StockColors.Error)
        }
        if (action != null) {
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(onClick = onAction, shape = RoundedCornerShape(10.dp)) { Text(action, fontSize = 12.sp) }
        }
    }
}
