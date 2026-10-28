package com.example.applestockpulse

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.R

/**
 * Notification channels and stock alarms. Used by the service and by the UI (test alarm),
 * so the test alarm never has to start the foreground service.
 */
object AlertNotifier {

    private const val TAG = "AlertNotifier"

    const val STATUS_CHANNEL_ID = "channel_ongoing_status"
    const val ALERT_CHANNEL_ID = "channel_stock_alerts"
    private const val TEST_NOTIFICATION_ID = 9999

    private val VIBRATION_PATTERN = longArrayOf(0, 600, 300, 600, 300, 800)

    fun canPostAlarms(context: Context): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = manager.getNotificationChannel(ALERT_CHANNEL_ID)
            if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) return false
        }
        return true
    }

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val statusChannel = NotificationChannel(
            STATUS_CHANNEL_ID,
            "Ongoing Polling Status",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows active background inventory polling status."
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(statusChannel)

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_ALARM)
            .build()

        val alertChannel = NotificationChannel(
            ALERT_CHANNEL_ID,
            "Apple Stock Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "High-priority alarms when tracked Apple inventory is available for pickup."
            setSound(alarmSound(), audioAttributes)
            enableVibration(true)
            vibrationPattern = VIBRATION_PATTERN
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableLights(true)
        }
        notificationManager.createNotificationChannel(alertChannel)
    }

    /**
     * One alarm per store, listing every variant that just came into stock there and the
     * rules that watch them. Tapping it opens the Apple product page of the first variant.
     */
    fun postStoreAlarm(
        context: Context,
        store: StoreInventoryCard,
        newVariants: List<VariantAvailability>,
        rules: List<WatchRule>
    ) {
        if (newVariants.isEmpty()) return
        ensureChannels(context)

        val notificationId = notificationIdFor(store.storeNumber)
        val names = newVariants.map { ProductCatalog.getProductName(it.sku) }
        val title = if (names.size == 1) "🚨 In Stock: ${names.first()}" else "🚨 ${names.size} models in stock"
        val lines = newVariants.joinToString("\n") { "• ${ProductCatalog.getProductName(it.sku)} — ${it.pickupQuote}" }
        val ruleLine = if (rules.isEmpty()) "" else "\nWatched by: ${rules.joinToString { it.displayName() }}"
        val distance = store.distanceText.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""

        val builder = baseAlarmBuilder(context, notificationId, newVariants.first().sku)
            .setContentTitle(title)
            .setContentText("Apple ${store.storeName} (${store.city})$distance")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Apple ${store.storeName} (${store.city})$distance\n$lines$ruleLine")
            )

        rules.firstOrNull()?.let { rule ->
            val detailsIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_OPEN_RULE_ID, rule.id)
            }
            builder.addAction(
                android.R.drawable.ic_menu_info_details,
                "Details",
                PendingIntent.getActivity(
                    context,
                    notificationId + 1,
                    detailsIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            builder.addAction(
                android.R.drawable.ic_lock_silent_mode,
                if (rules.size == 1) "Mute rule" else "Mute rules",
                AlarmActionReceiver.mutePendingIntent(context, notificationId, rules.map { it.id })
            )
        }

        notify(context, notificationId, builder.build())
        Log.i(TAG, "Alarm for store ${store.storeNumber}: ${newVariants.joinToString { it.sku }}")
    }

    fun cancel(context: Context, notificationId: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)
    }

    fun postTestAlarm(context: Context) {
        ensureChannels(context)
        val sample = ProductCatalog.CATALOG.first()
        val notification = baseAlarmBuilder(context, TEST_NOTIFICATION_ID, sample.sku)
            .setContentTitle("🚨 [TEST] In Stock: ${sample.name}")
            .setContentText("Pacific Centre (Vancouver) has stock! Available Today")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("TEST ALARM NOTIFICATION:\n${sample.name}\nPacific Centre (Vancouver)\nIf you heard the alarm sound, alerts work.\nTap to test the product page link.")
            )
            .build()
        notify(context, TEST_NOTIFICATION_ID, notification)
    }

    fun productPageIntent(sku: String): Intent {
        return Intent(Intent.ACTION_VIEW, Uri.parse(ProductCatalog.productPageUrl(sku))).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    private fun baseAlarmBuilder(context: Context, requestCode: Int, sku: String): NotificationCompat.Builder {
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            productPageIntent(sku),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_apple_stock)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSound(alarmSound())
            .setVibrate(VIBRATION_PATTERN)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_send, "Open on Apple.ca", pendingIntent)
    }

    private fun notify(context: Context, id: Int, notification: Notification) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(id, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission missing; alarm not shown", e)
        }
    }

    private fun notificationIdFor(storeNumber: String): Int = "store:$storeNumber".hashCode()

    private fun alarmSound(): Uri =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
}
