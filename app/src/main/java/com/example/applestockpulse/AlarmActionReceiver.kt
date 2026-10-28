package com.example.applestockpulse

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Handles the "Mute rule" button on stock alarms. A receiver works even when the
 * service is not running, unlike a service intent started from the background.
 */
class AlarmActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_MUTE_RULES) return
        val ruleIds = intent.getStringArrayExtra(EXTRA_RULE_IDS)?.toList().orEmpty()
        if (ruleIds.isNotEmpty()) {
            WatchRuleStore.get(context).setAlarm(ruleIds, on = false)
        }
        AlertNotifier.cancel(context, intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0))
    }

    companion object {
        const val ACTION_MUTE_RULES = "com.example.applestockpulse.ACTION_MUTE_RULES"
        private const val EXTRA_RULE_IDS = "rule_ids"
        private const val EXTRA_NOTIFICATION_ID = "notification_id"

        fun mutePendingIntent(context: Context, notificationId: Int, ruleIds: List<String>): PendingIntent {
            val intent = Intent(context, AlarmActionReceiver::class.java).apply {
                action = ACTION_MUTE_RULES
                putExtra(EXTRA_RULE_IDS, ruleIds.toTypedArray())
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            }
            return PendingIntent.getBroadcast(
                context,
                notificationId + 2,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
