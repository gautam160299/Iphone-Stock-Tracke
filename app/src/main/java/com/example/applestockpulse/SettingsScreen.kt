package com.example.applestockpulse

import android.widget.Toast
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

    var postal by remember { mutableStateOf(SearchLocation.formatPostalCode(preferences.getPostalCode())) }
    var radius by remember { mutableStateOf(preferences.getRadiusKm()) }
    var storeFilter by remember { mutableStateOf(preferences.getStoreFilter()) }
    var confirmClear by remember { mutableStateOf(false) }
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
                title = "Test alarm",
                status = "Plays the alarm sound and opens Apple.ca when tapped",
                ok = true,
                action = "Test",
                onAction = {
                    if (hasNotificationPermission) AlertNotifier.postTestAlarm(context) else actions.requestNotifications()
                }
            )
            Text("A store/device pair alarms at most once every 10 minutes, so stock that flickers does not repeat the alarm.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SettingsCard("Background") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Monitoring", fontWeight = FontWeight.SemiBold)
                    Text(if (isRunning) "On · checks about every 60 s" else "Off", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = isRunning, onCheckedChange = actions.startOrStop)
            }
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
