package com.example.applestockpulse

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Fixed status colours, the same on every screen
object StockColors {
    val InStock = Color(0xFF10B981)
    val InStockSoft = Color(0x1F10B981)
    val Watched = Color(0xFFD97706)
    val WatchedSoft = Color(0x33F59E0B)
    val Error = Color(0xFFEF4444)
    val ErrorSoft = Color(0x22EF4444)
    val Idle = Color(0xFF94A3B8)
}

object TimeText {
    private const val DAY_MS = 24 * 60 * 60 * 1000L

    fun time(ms: Long): String = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(ms))

    fun timeWithSeconds(ms: Long): String = SimpleDateFormat("h:mm:ss a", Locale.getDefault()).format(Date(ms))

    fun dayTime(ms: Long): String = SimpleDateFormat("EEE h:mm a", Locale.getDefault()).format(Date(ms))

    fun dayHeader(ms: Long, now: Long = System.currentTimeMillis()): String {
        val day = startOfDay(ms)
        val today = startOfDay(now)
        return when (day) {
            today -> "Today"
            today - DAY_MS -> "Yesterday"
            else -> SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(ms))
        }
    }

    fun duration(ms: Long): String {
        val minutes = (ms / 60_000).coerceAtLeast(1)
        return if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"
    }

    fun hourLabel(hour: Int): String = when {
        hour == 0 -> "12 AM"
        hour < 12 -> "$hour AM"
        hour == 12 -> "12 PM"
        else -> "${hour - 12} PM"
    }

    fun startOfDay(ms: Long): Long = Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

@Composable
fun PulseIndicator(isActive: Boolean, hasError: Boolean = false, size: Int = 12) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(animation = tween(900), repeatMode = RepeatMode.Reverse),
        label = "pulse_scale"
    )
    val color = when {
        hasError -> StockColors.Error
        isActive -> StockColors.InStock
        else -> StockColors.Idle
    }
    Box(
        modifier = Modifier
            .size(size.dp)
            .scale(if (isActive) scale else 1f)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
fun ErrorNoticeCard(message: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("error_notice_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = StockColors.ErrorSoft)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.Warning, contentDescription = "Notice", tint = StockColors.Error, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(text = message, color = StockColors.Error, fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
fun InfoBanner(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    text: String,
    actionLabel: String,
    tint: Color,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = 0.13f))
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = tint)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = tint)
            ) {
                Text(actionLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun NotificationPermissionBanner(onRequestPermission: () -> Unit) {
    InfoBanner(
        icon = Icons.Default.NotificationsActive,
        title = "Notifications are off",
        text = "Alarms cannot sound until you allow notifications.",
        actionLabel = "Allow",
        tint = StockColors.Watched,
        onAction = onRequestPermission
    )
}

@Composable
fun BatteryOptimizationBanner(onRequestExemption: () -> Unit) {
    InfoBanner(
        icon = Icons.Default.BatteryAlert,
        title = "Background checks may stop",
        text = "Allow unrestricted battery use so checks continue while the screen is off.",
        actionLabel = "Setup",
        tint = Color(0xFF0284C7),
        onAction = onRequestExemption
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    text: String,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            text,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (action != null) {
            Spacer(modifier = Modifier.size(4.dp))
            action()
        }
    }
}

@Composable
fun SmallOutlinedAction(text: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, shape = RoundedCornerShape(10.dp)) {
        Text(text, fontSize = 12.sp)
    }
}

/**
 * Returns a function that asks where to save, then writes the 30-day history as CSV.
 */
@Composable
fun rememberCsvExporter(historyDao: HistoryDao): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val since = System.currentTimeMillis() - TrackerPreferences.HISTORY_RETENTION_DAYS * 24 * 60 * 60 * 1000L
                    val csv = HistoryCsv.build(historyDao.eventsSince(since))
                    context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray()) } != null
                } catch (e: Exception) {
                    false
                }
            }
            Toast.makeText(context, if (ok) "History exported" else "Export failed", Toast.LENGTH_SHORT).show()
        }
    }
    return { launcher.launch("applestockpulse-history.csv") }
}

fun checkBatteryOptimization(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
}

@SuppressLint("BatteryLife")
fun requestBatteryExemption(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (_: Exception) {
            Toast.makeText(context, "Please allow unrestricted battery usage in App Settings.", Toast.LENGTH_SHORT).show()
        }
    }
}

fun startTrackerService(context: Context) {
    val intent = Intent(context, StockTrackerService::class.java).apply {
        action = StockTrackerService.ACTION_START
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
    Toast.makeText(context, "Monitoring started", Toast.LENGTH_SHORT).show()
}

fun stopTrackerService(context: Context) {
    val intent = Intent(context, StockTrackerService::class.java).apply {
        action = StockTrackerService.ACTION_STOP
    }
    context.startService(intent)
    Toast.makeText(context, "Monitoring stopped", Toast.LENGTH_SHORT).show()
}

fun requestPoll(context: Context) {
    val intent = Intent(context, StockTrackerService::class.java).apply {
        action = StockTrackerService.ACTION_FORCE_POLL
    }
    context.startService(intent)
}

fun requestBrowsePoll(context: Context, model: String) {
    val intent = Intent(context, StockTrackerService::class.java).apply {
        action = StockTrackerService.ACTION_BROWSE_POLL
        putExtra(StockTrackerService.EXTRA_MODEL, model)
    }
    context.startService(intent)
}

fun openNotificationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    }
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(context, "Open Settings > Apps > AppleStockPulse > Notifications.", Toast.LENGTH_LONG).show()
    }
}

fun openProductPage(context: Context, sku: String) {
    try {
        context.startActivity(AlertNotifier.productPageIntent(sku))
    } catch (e: Exception) {
        Toast.makeText(context, "Unable to open browser: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
