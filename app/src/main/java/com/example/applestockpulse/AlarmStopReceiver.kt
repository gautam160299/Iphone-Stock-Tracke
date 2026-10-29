package com.example.applestockpulse

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** "Stop alarm" on the notification, and swiping the notification away. */
class AlarmStopReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_STOP_ALARM = "com.example.applestockpulse.ACTION_STOP_ALARM"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_STOP_ALARM) {
            AlarmPlayer.stop()
        }
    }
}
