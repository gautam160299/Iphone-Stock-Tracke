package com.example.applestockpulse

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Loud stock alarm: loops the alarm sound at full alarm volume and vibrates until the user
 * stops it or MAX_RING_MS passes. Uses the alarm stream, so silent and vibrate mode do not mute it.
 * Safe to call from any thread; all player work runs on the main thread.
 */
object AlarmPlayer {

    private const val TAG = "AlarmPlayer"

    // Apple stock is usually gone after this, so ringing longer only annoys
    const val MAX_RING_MS = 3 * 60 * 1000L

    // Repeats from index 0 until cancelled
    private val VIBRATION_PATTERN = longArrayOf(0, 800, 400, 800, 400, 1200, 600)

    private val ALARM_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private val handler = Handler(Looper.getMainLooper())
    private val autoStop = Runnable { stopOnMain() }

    // Main thread only
    private var appContext: Context? = null
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var savedAlarmVolume: Int? = null

    private val _isRinging = MutableStateFlow(false)
    val isRinging: StateFlow<Boolean> = _isRinging.asStateFlow()

    /** Starts the alarm, or restarts the auto-stop timer when it already rings. */
    fun start(context: Context) {
        val app = context.applicationContext
        _isRinging.value = true
        handler.post { startOnMain(app) }
    }

    fun stop() {
        _isRinging.value = false
        handler.post { stopOnMain() }
    }

    private fun startOnMain(app: Context) {
        appContext = app
        handler.removeCallbacks(autoStop)
        handler.postDelayed(autoStop, MAX_RING_MS)
        _isRinging.value = true
        if (player != null) return

        raiseAlarmVolume(app)
        player = createLoopingPlayer(app)
        if (player == null) Log.e(TAG, "No alarm sound could be played; vibration only")
        vibrator = startVibration(app)
        Log.i(TAG, "Alarm ringing")
    }

    private fun stopOnMain() {
        handler.removeCallbacks(autoStop)
        _isRinging.value = false
        player?.let {
            try {
                it.stop()
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Player was not playing", e)
            }
            it.release()
        }
        player = null
        vibrator?.cancel()
        vibrator = null
        restoreAlarmVolume()
    }

    private fun raiseAlarmVolume(app: Context) {
        val audio = app.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            val current = audio.getStreamVolume(AudioManager.STREAM_ALARM)
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            if (current < max) {
                audio.setStreamVolume(AudioManager.STREAM_ALARM, max, 0)
                savedAlarmVolume = current
            }
        } catch (e: SecurityException) {
            // Some Do Not Disturb setups refuse volume changes; play at the current volume
            Log.w(TAG, "Could not raise alarm volume", e)
        }
    }

    private fun restoreAlarmVolume() {
        val volume = savedAlarmVolume ?: return
        savedAlarmVolume = null
        val audio = appContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            audio.setStreamVolume(AudioManager.STREAM_ALARM, volume, 0)
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not restore alarm volume", e)
        }
    }

    /** Tries the alarm, ringtone and notification sounds in turn; some phones have no alarm sound set. */
    private fun createLoopingPlayer(app: Context): MediaPlayer? {
        val candidates = listOfNotNull<Uri>(
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            Settings.System.DEFAULT_ALARM_ALERT_URI,
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        ).distinct()

        for (uri in candidates) {
            val candidate = MediaPlayer()
            try {
                candidate.setAudioAttributes(ALARM_ATTRIBUTES)
                candidate.setDataSource(app, uri)
                candidate.isLooping = true
                candidate.setWakeMode(app, PowerManager.PARTIAL_WAKE_LOCK)
                candidate.prepare()
                candidate.start()
                return candidate
            } catch (e: Exception) {
                Log.w(TAG, "Cannot play $uri", e)
                candidate.release()
            }
        }
        return null
    }

    private fun startVibration(app: Context): Vibrator? {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (vibrator == null || !vibrator.hasVibrator()) return null

        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> vibrator.vibrate(
                VibrationEffect.createWaveform(VIBRATION_PATTERN, 0),
                VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM)
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> @Suppress("DEPRECATION") vibrator.vibrate(
                VibrationEffect.createWaveform(VIBRATION_PATTERN, 0),
                ALARM_ATTRIBUTES
            )
            else -> @Suppress("DEPRECATION") vibrator.vibrate(VIBRATION_PATTERN, 0)
        }
        return vibrator
    }
}
