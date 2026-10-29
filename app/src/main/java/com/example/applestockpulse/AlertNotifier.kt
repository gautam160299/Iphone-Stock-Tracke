package com.example.applestockpulse

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.R

/**
 * Notification channels and stock alarms. Used by the service and by the UI (test alarm),
 * so the test alarm never has to start the foreground service.
 *
 * Each alarm rings through [AlarmPlayer], opens [AlarmActivity] full screen and is sent to ntfy.
 */
object AlertNotifier {

    private const val TAG = "AlertNotifier"

    const val STATUS_CHANNEL_ID = "channel_ongoing_status"

    // Android keeps a channel's sound and vibration from when it was first made, so a new
    // ID is the only way to change them on phones that already have the app
    const val ALERT_CHANNEL_ID = "channel_stock_alarms_v2"
    private const val LEGACY_ALERT_CHANNEL_ID = "channel_stock_alerts"

    private const val TEST_NOTIFICATION_ID = 9999

    fun canPostAlarms(context: Context): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = manager.getNotificationChannel(ALERT_CHANNEL_ID)
            if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) return false
        }
        return true
    }

    /** Android 14+ lets the user turn off full-screen alarms for apps that are not clocks or phones. */
    fun canUseFullScreenAlarm(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return notificationManager.canUseFullScreenIntent()
    }

    fun fullScreenAlarmSettingsIntent(context: Context): Intent {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        }
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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

        // Silent channel: AlarmPlayer plays the looping sound and vibration on the alarm stream
        val alertChannel = NotificationChannel(
            ALERT_CHANNEL_ID,
            "Apple Stock Alarms",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Full-screen alarms when tracked Apple inventory is available for pickup. " +
                "The app plays the alarm sound itself, at full alarm volume."
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableLights(true)
        }
        notificationManager.createNotificationChannel(alertChannel)
        notificationManager.deleteNotificationChannel(LEGACY_ALERT_CHANNEL_ID)
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

        val names = newVariants.map { ProductCatalog.getProductName(it.sku) }
        val title = if (names.size == 1) "🚨 In Stock: ${names.first()}" else "🚨 ${names.size} models in stock"
        val distance = store.distanceText.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""
        val storeLine = "Apple ${store.storeName} (${store.city})$distance"
        val lines = newVariants.joinToString("\n") { "• ${ProductCatalog.getProductName(it.sku)} — ${it.pickupQuote}" }
        val ruleLine = if (rules.isEmpty()) "" else "\nWatched by: ${rules.joinToString { it.displayName() }}"

        postAlarm(
            context,
            id = notificationIdFor(store.storeNumber),
            sku = newVariants.first().sku,
            title = title,
            summary = storeLine,
            details = "$storeLine\n$lines$ruleLine",
            rules = rules
        )
        Log.i(TAG, "Alarm for store ${store.storeNumber}: ${newVariants.joinToString { it.sku }}")
    }

    fun postTestAlarm(context: Context) {
        val sample = ProductCatalog.CATALOG.first()
        postAlarm(
            context,
            id = TEST_NOTIFICATION_ID,
            sku = sample.sku,
            title = "🚨 [TEST] In Stock: ${sample.name}",
            summary = "Pacific Centre (Vancouver) has stock! Available Today",
            details = "TEST ALARM\n${sample.name}\nPacific Centre (Vancouver)\n" +
                "If the alarm rang and this screen showed, alerts work."
        )
    }

    fun productPageIntent(sku: String): Intent {
        return Intent(Intent.ACTION_VIEW, Uri.parse(ProductCatalog.productPageUrl(sku))).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    fun cancel(context: Context, id: Int) {
        NotificationManagerCompat.from(context).cancel(id)
    }

    private fun postAlarm(
        context: Context,
        id: Int,
        sku: String,
        title: String,
        summary: String,
        details: String,
        rules: List<WatchRule> = emptyList()
    ) {
        ensureChannels(context)
        AlarmPlayer.start(context)

        val ruleId = rules.firstOrNull()?.id
        val fullScreen = alarmActivityIntent(context, AlarmActivity.ACTION_SHOW, id, sku, title, details, ruleId)
        val open = alarmActivityIntent(context, AlarmActivity.ACTION_OPEN, id, sku, title, details, ruleId)
        val stop = PendingIntent.getBroadcast(
            context,
            id,
            Intent(context, AlarmStopReceiver::class.java).setAction(AlarmStopReceiver.ACTION_STOP_ALARM),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_apple_stock)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$details\nTap to open the Apple product page."))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(open)
            // Wakes the screen and shows AlarmActivity over the lock screen
            .setFullScreenIntent(fullScreen, true)
            // Swiping the notification away also silences the alarm
            .setDeleteIntent(stop)
            .addAction(android.R.drawable.ic_menu_send, "Open on Apple.ca", open)
            .addAction(android.R.drawable.ic_lock_silent_mode, "Stop alarm", stop)

        // Android shows three buttons at most; "Details" is on the alarm screen instead
        if (rules.isNotEmpty()) {
            builder.addAction(
                android.R.drawable.ic_lock_silent_mode,
                if (rules.size == 1) "Mute rule" else "Mute rules",
                AlarmActionReceiver.mutePendingIntent(context, id, rules.map { it.id })
            )
        }

        notify(context, id, builder.build())
        NtfyNotifier.publish(TrackerPreferences(context).getNtfyTarget(), title, details, ProductCatalog.productPageUrl(sku))
    }

    private fun alarmActivityIntent(
        context: Context,
        action: String,
        id: Int,
        sku: String,
        title: String,
        details: String,
        ruleId: String?
    ): PendingIntent {
        val intent = Intent(context, AlarmActivity::class.java).apply {
            this.action = action
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(AlarmActivity.EXTRA_NOTIFICATION_ID, id)
            putExtra(AlarmActivity.EXTRA_SKU, sku)
            putExtra(AlarmActivity.EXTRA_TITLE, title)
            putExtra(AlarmActivity.EXTRA_DETAILS, details)
            putExtra(AlarmActivity.EXTRA_RULE_ID, ruleId)
        }
        return PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun notify(context: Context, id: Int, notification: Notification) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(id, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission missing; alarm shown without a notification", e)
        }
    }

    private fun notificationIdFor(storeNumber: String): Int = "store:$storeNumber".hashCode()
}
