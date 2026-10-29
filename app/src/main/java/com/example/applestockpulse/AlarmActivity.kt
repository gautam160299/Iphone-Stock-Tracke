package com.example.applestockpulse

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.MyApplicationTheme

/**
 * Flashing red alarm screen. The alarm notification opens it full screen, over the lock screen,
 * like an incoming call. It closes itself when the alarm stops elsewhere.
 */
class AlarmActivity : ComponentActivity() {

    companion object {
        private const val TAG = "AlarmActivity"

        const val ACTION_SHOW = "com.example.applestockpulse.ACTION_SHOW_ALARM"
        const val ACTION_OPEN = "com.example.applestockpulse.ACTION_OPEN_ALARM"

        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_SKU = "extra_sku"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_DETAILS = "extra_details"
    }

    private var alarm by mutableStateOf<Intent?>(null)

    // Set while the unlock prompt is up, so the alarm stopping does not close this screen early
    private var opening = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        enableEdgeToEdge()

        alarm = intent
        setContent {
            MyApplicationTheme {
                val current = alarm
                AlarmScreen(
                    title = current?.getStringExtra(EXTRA_TITLE) ?: "🚨 Apple stock alarm",
                    details = current?.getStringExtra(EXTRA_DETAILS).orEmpty(),
                    onOpen = { openProductPage() },
                    onStop = {
                        AlarmPlayer.stop()
                        finish()
                    },
                    onAlarmStopped = { if (!opening) finish() }
                )
            }
        }
        if (intent?.action == ACTION_OPEN) openProductPage()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // A second store came into stock, or the user tapped "Open" on the notification
        alarm = intent
        if (intent.action == ACTION_OPEN) openProductPage()
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /** Stops the alarm, asks to unlock if needed, then opens the Apple page. */
    private fun openProductPage() {
        opening = true
        AlarmPlayer.stop()
        val current = alarm
        val sku = current?.getStringExtra(EXTRA_SKU)
        current?.getIntExtra(EXTRA_NOTIFICATION_ID, 0)?.takeIf { it != 0 }?.let {
            AlertNotifier.cancelAlarmNotification(this, it)
        }
        if (sku == null) {
            finish()
            return
        }

        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && keyguard?.isKeyguardLocked == true) {
            keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = launchProductPage(sku)
                override fun onDismissCancelled() = finish()
                override fun onDismissError() = finish()
            })
        } else {
            launchProductPage(sku)
        }
    }

    private fun launchProductPage(sku: String) {
        try {
            startActivity(AlertNotifier.productPageIntent(sku))
        } catch (e: Exception) {
            Log.e(TAG, "Cannot open product page", e)
            Toast.makeText(this, "Unable to open browser: ${e.message}", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}

@Composable
private fun AlarmScreen(
    title: String,
    details: String,
    onOpen: () -> Unit,
    onStop: () -> Unit,
    onAlarmStopped: () -> Unit
) {
    val isRinging by AlarmPlayer.isRinging.collectAsStateWithLifecycle()
    var wasRinging by remember { mutableStateOf(false) }
    LaunchedEffect(isRinging) {
        if (isRinging) wasRinging = true else if (wasRinging) onAlarmStopped()
    }

    val flash = rememberInfiniteTransition(label = "alarmFlash")
    val background by flash.animateColor(
        initialValue = Color(0xFFB91C1C),
        targetValue = Color(0xFFEF4444),
        animationSpec = infiniteRepeatable(tween(450), RepeatMode.Reverse),
        label = "alarmBackground"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "IN STOCK NOW",
            color = Color.White,
            fontSize = 40.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = details,
            color = Color.White,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onOpen,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFFB91C1C))
        ) {
            Text("OPEN APPLE.CA", fontSize = 22.sp, fontWeight = FontWeight.Black)
        }
        Spacer(modifier = Modifier.height(14.dp))
        OutlinedButton(
            onClick = onStop,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
        ) {
            Text("STOP ALARM", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "The alarm stops by itself after ${AlarmPlayer.MAX_RING_MS / 60_000} minutes.",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}
