package com.example.applestockpulse

import com.example.R
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.random.Random

data class TrackerUiState(
    val isRunning: Boolean = false,
    val lastCheckedTimestamp: Long? = null,
    val lastStatusMessage: String = "Tracker is idle",
    val httpStatusCode: Int? = null,
    val selectedModel: String = "",
    val storeCards: List<StoreInventoryCard> = emptyList(),
    val totalAvailableCount: Int = 0,
    val activeSkus: List<String> = emptyList(),
    val postalCode: String = "",
    val error: String? = null
)

class StockTrackerService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(
        serviceJob + Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            Log.e(TAG, "Uncaught error in tracker coroutine", e)
        }
    )
    private var pollingJob: Job? = null

    // One request at a time; also guards the alarm bookkeeping below
    private val pollMutex = Mutex()
    private var lastRequestAtMs = 0L
    private var previouslyAvailablePairs: Set<String> = emptySet()
    private val lastAlarmAtMs = mutableMapOf<String, Long>()

    // Main thread only: manual polls running or queued
    private var pendingManualPolls = 0

    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var preferences: TrackerPreferences
    private lateinit var notificationManager: NotificationManager

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    companion object {
        private const val TAG = "StockTrackerService"

        const val NOTIFICATION_ID_STATUS = 1001

        const val ACTION_START = "com.example.applestockpulse.ACTION_START"
        const val ACTION_STOP = "com.example.applestockpulse.ACTION_STOP"
        const val ACTION_FORCE_POLL = "com.example.applestockpulse.ACTION_FORCE_POLL"

        // Minimum gap between two requests to Apple, so repeated "Check Now" taps cannot flood it
        private const val MIN_REQUEST_GAP_MS = 5_000L

        // A store/SKU pair that flickers in and out of stock alarms at most once per this period
        private const val ALARM_COOLDOWN_MS = 10 * 60 * 1000L

        // Renewed every cycle; only expires if the loop hangs
        private const val WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1000L

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _uiState = MutableStateFlow(TrackerUiState())
        val uiState: StateFlow<TrackerUiState> = _uiState.asStateFlow()
    }

    override fun onCreate() {
        super.onCreate()
        preferences = TrackerPreferences(this)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        AlertNotifier.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_STOP -> {
                Log.d(TAG, "ACTION_STOP received")
                stopTracker()
                START_NOT_STICKY
            }
            ACTION_FORCE_POLL -> {
                Log.d(TAG, "ACTION_FORCE_POLL received")
                requestManualPoll()
                if (_isRunning.value) START_STICKY else START_NOT_STICKY
            }
            ACTION_START -> {
                Log.d(TAG, "ACTION_START received")
                preferences.setTrackingEnabled(true)
                startTrackerForeground()
            }
            null -> {
                // System restart after the process was killed (START_STICKY)
                if (preferences.isTrackingEnabled()) {
                    Log.d(TAG, "Restarted by system; resuming monitoring")
                    startTrackerForeground()
                } else {
                    stopSelfIfIdle()
                    START_NOT_STICKY
                }
            }
            else -> {
                stopSelfIfIdle()
                START_NOT_STICKY
            }
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "AppleStockPulse::PollingWakeLock"
            )?.apply {
                setReferenceCounted(false)
            }
        }
        try {
            // Re-acquiring a non-reference-counted lock resets its timeout
            wakeLock?.acquire(WAKE_LOCK_TIMEOUT_MS)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake lock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "Wake lock released")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing wake lock", e)
        }
    }

    private fun startTrackerForeground(): Int {
        val initialNotification = buildOngoingStatusNotification(
            statusText = "Scanning nearby Apple Store inventory...",
            availableCount = 0,
            lastChecked = null
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID_STATUS,
                    initialNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID_STATUS, initialNotification)
            }
        } catch (e: Exception) {
            // E.g. ForegroundServiceStartNotAllowedException when Android 12+ restarts us in the background
            Log.e(TAG, "Failed to start foreground service", e)
            _isRunning.value = false
            preferences.setTrackingEnabled(false)
            _uiState.update {
                it.copy(
                    isRunning = false,
                    lastStatusMessage = "Monitoring stopped",
                    error = "Android did not allow monitoring to restart in the background. Open the app and tap Start."
                )
            }
            stopSelf()
            return START_NOT_STICKY
        }

        _isRunning.value = true
        _uiState.update { it.copy(isRunning = true, lastStatusMessage = "Monitoring started") }
        // Alarm for stock that is already there when monitoring starts
        previouslyAvailablePairs = emptySet()
        startPollingLoop()
        return START_STICKY
    }

    private fun startPollingLoop() {
        pollingJob?.cancel()
        pollingJob = serviceScope.launch {
            Log.d(TAG, "Background polling loop started")
            while (isActive) {
                // Keeps the CPU awake between polls; Doze honours it only with the battery exemption
                acquireWakeLock()
                try {
                    pollMutex.withLock { executePollCycle() }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Unhandled exception in polling cycle", e)
                    _uiState.update {
                        it.copy(lastStatusMessage = "Poll error: ${e.message}", error = e.localizedMessage)
                    }
                }

                val jitterDelay = 52_000L + Random.nextLong(0, 16_001L)
                Log.d(TAG, "Next poll in ${jitterDelay / 1000}s")
                delay(jitterDelay)
            }
        }
    }

    /**
     * Poll now (e.g. "Check Now", or a new model/location). Without monitoring this only refreshes
     * the screen: no alarms, no wake lock, and the service stops itself afterwards.
     */
    private fun requestManualPoll() {
        // One running and one queued is enough; more taps add nothing
        if (pendingManualPolls >= 2) return
        pendingManualPolls++
        serviceScope.launch {
            try {
                pollMutex.withLock { executePollCycle() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Manual poll failed", e)
                _uiState.update { it.copy(lastStatusMessage = "Poll error: ${e.message}", error = e.localizedMessage) }
            } finally {
                withContext(NonCancellable + Dispatchers.Main) {
                    pendingManualPolls--
                    stopSelfIfIdle()
                }
            }
        }
    }

    private fun stopSelfIfIdle() {
        if (!_isRunning.value && pendingManualPolls == 0) {
            stopSelf()
        }
    }

    // Must hold pollMutex
    private suspend fun executePollCycle() {
        val postalCode = preferences.getCleanPostalCode()
        val model = preferences.getSelectedModel()
        val storage = preferences.getSelectedStorage()
        val color = preferences.getSelectedColor()
        val skus = preferences.getActiveQuerySkus()
        val storeFilter = preferences.getParsedStoreFilter()

        val currentTimeFormatted = SimpleDateFormat("h:mm:ss a", Locale.getDefault()).format(Date())

        if (!TrackerPreferences.isValidPostalCode(postalCode)) {
            _uiState.update {
                it.copy(
                    selectedModel = model,
                    storeCards = emptyList(),
                    totalAvailableCount = 0,
                    lastStatusMessage = "Configuration incomplete (postal code)",
                    error = "\"$postalCode\" is not a valid Canadian postal code."
                )
            }
            return
        }

        if (skus.isEmpty()) {
            _uiState.update {
                it.copy(
                    selectedModel = model,
                    storeCards = emptyList(),
                    totalAvailableCount = 0,
                    lastStatusMessage = "Nothing to check",
                    error = "No $model exists as $storage + $color. Choose other filters."
                )
            }
            return
        }

        val waitMs = lastRequestAtMs + MIN_REQUEST_GAP_MS - System.currentTimeMillis()
        if (waitMs > 0) delay(waitMs)
        lastRequestAtMs = System.currentTimeMillis()

        // Batch all SKUs into a single GET request
        val queryBuilder = StringBuilder("pl=true&mts.0=regular")
        skus.forEachIndexed { index, sku ->
            queryBuilder.append("&parts.$index=").append(URLEncoder.encode(sku.trim(), "UTF-8"))
        }
        queryBuilder.append("&location=").append(URLEncoder.encode(postalCode, "UTF-8"))

        val targetUrl = "https://www.apple.com/ca/shop/retail/pickup-message?$queryBuilder"
        Log.d(TAG, "Polling ${skus.size} SKU(s)")

        val request = Request.Builder()
            .url(targetUrl)
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 15; SM-F966U) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36")
            .addHeader("Accept", "application/json, text/javascript, */*; q=0.01")
            .addHeader("Accept-Language", "en-CA,en-US;q=0.9,en;q=0.8")
            .addHeader("Referer", "https://www.apple.com/ca/shop/buy-iphone")
            .addHeader("Sec-Fetch-Mode", "cors")
            .addHeader("Sec-Fetch-Site", "same-origin")
            .build()

        val (statusCode, responseBody) = try {
            okHttpClient.newCall(request).await()
        } catch (e: IOException) {
            Log.e(TAG, "Network error during polling cycle", e)
            _uiState.update {
                it.copy(
                    lastCheckedTimestamp = System.currentTimeMillis(),
                    lastStatusMessage = "Network error: ${e.message}",
                    error = "Network error: ${e.localizedMessage}"
                )
            }
            updateOngoingStatusNotification("Connection failed at $currentTimeFormatted", 0, currentTimeFormatted)
            return
        }

        if (statusCode !in 200..299) {
            val errorMsg = if (statusCode == 541) {
                "HTTP 541: Apple is blocking or rate-limiting these requests. Retrying in ~60s..."
            } else {
                "HTTP $statusCode from Apple server"
            }
            Log.w(TAG, errorMsg)
            _uiState.update {
                it.copy(
                    lastCheckedTimestamp = System.currentTimeMillis(),
                    httpStatusCode = statusCode,
                    lastStatusMessage = errorMsg,
                    error = errorMsg
                )
            }
            updateOngoingStatusNotification("HTTP $statusCode at $currentTimeFormatted", 0, currentTimeFormatted)
            return
        }

        val parsed = try {
            AppleInventoryParser.parse(responseBody, skus, storeFilter, model)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Apple stores JSON", e)
            _uiState.update {
                it.copy(
                    lastCheckedTimestamp = System.currentTimeMillis(),
                    httpStatusCode = statusCode,
                    lastStatusMessage = "Unreadable response at $currentTimeFormatted",
                    error = "Apple sent a response that is not inventory data (HTTP $statusCode). It may be a block page."
                )
            }
            return
        }

        val problems = buildList {
            parsed.apiErrorMessage?.let { add("Apple: $it") }
            if (parsed.unknownSkus.isNotEmpty()) {
                add("Apple does not recognize these part numbers: ${parsed.unknownSkus.joinToString()}. Check them in ProductCatalog.kt.")
            }
            if (parsed.storesBeforeFilter == 0 && parsed.apiErrorMessage == null) {
                add("Apple returned no stores near $postalCode.")
            } else if (parsed.stores.isEmpty() && storeFilter.isNotEmpty()) {
                add("No nearby store matches your store filter (${storeFilter.joinToString()}).")
            }
        }

        val totalAvailable = parsed.stores.sumOf { it.totalAvailableInStore }
        val statusMsg = if (totalAvailable > 0) {
            "Checked at $currentTimeFormatted — 🚨 $totalAvailable IN STOCK in nearby stores!"
        } else {
            "Checked at $currentTimeFormatted — None in stock nearby"
        }

        _uiState.value = TrackerUiState(
            isRunning = _isRunning.value,
            lastCheckedTimestamp = System.currentTimeMillis(),
            lastStatusMessage = statusMsg,
            httpStatusCode = statusCode,
            selectedModel = model,
            storeCards = parsed.stores,
            totalAvailableCount = totalAvailable,
            activeSkus = skus,
            postalCode = postalCode,
            error = problems.joinToString("\n").ifEmpty { null }
        )

        // Alarms only while the user has monitoring on
        if (_isRunning.value) {
            postAlarmsForNewStock(parsed.stores)
        }

        val summaryNotificationText = if (totalAvailable > 0) {
            "🚨 $totalAvailable $model IN STOCK! ($currentTimeFormatted)"
        } else {
            "$model • None available ($currentTimeFormatted)"
        }
        updateOngoingStatusNotification(summaryNotificationText, totalAvailable, currentTimeFormatted)
    }

    // Must hold pollMutex
    private fun postAlarmsForNewStock(stores: List<StoreInventoryCard>) {
        val now = System.currentTimeMillis()
        val availableNow = mutableSetOf<String>()

        for (store in stores) {
            val newVariants = store.variants
                .filter { it.isAvailable && preferences.isSkuArmed(it.sku) }
                .filter { variant ->
                    val key = "${store.storeNumber}:${variant.sku}"
                    availableNow.add(key)
                    key !in previouslyAvailablePairs && now - (lastAlarmAtMs[key] ?: 0L) >= ALARM_COOLDOWN_MS
                }
            if (newVariants.isEmpty() || !_isRunning.value) continue

            newVariants.forEach { lastAlarmAtMs["${store.storeNumber}:${it.sku}"] = now }
            AlertNotifier.postStoreAlarm(this, store, newVariants)
        }

        previouslyAvailablePairs = availableNow
    }

    private fun buildOngoingStatusNotification(
        statusText: String,
        availableCount: Int,
        lastChecked: String?
    ): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, StockTrackerService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val model = preferences.getSelectedModel()
        val postal = preferences.getPostalCode()
        val timeLabel = lastChecked ?: "Just started"

        val bodyText = "$model • Postal: $postal • Stock: $availableCount • Last: $timeLabel"

        return NotificationCompat.Builder(this, AlertNotifier.STATUS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_apple_stock)
            .setContentTitle("AppleStockPulse Monitoring Active")
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$bodyText\n$statusText"))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Stop Tracker",
                stopPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateOngoingStatusNotification(
        statusText: String,
        availableCount: Int,
        lastChecked: String?
    ) {
        if (_isRunning.value) {
            val notification = buildOngoingStatusNotification(statusText, availableCount, lastChecked)
            notificationManager.notify(NOTIFICATION_ID_STATUS, notification)
        }
    }

    private fun stopTracker() {
        _isRunning.value = false
        preferences.setTrackingEnabled(false)
        // Cancels the loop and any poll in flight, including its HTTP call, so no alarm can follow
        pollingJob?.cancel()
        serviceJob.cancelChildren()
        _uiState.update {
            it.copy(isRunning = false, lastStatusMessage = "Tracker stopped by user")
        }
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        _isRunning.value = false
        _uiState.update { it.copy(isRunning = false) }
        serviceScope.cancel()
        releaseWakeLock()
        Log.d(TAG, "StockTrackerService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

/**
 * Runs the call on OkHttp's thread pool. Cancelling the coroutine cancels the HTTP call.
 */
private suspend fun Call.await(): Pair<Int, String> = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            try {
                val result = response.use { it.code to (it.body?.string() ?: "") }
                cont.resume(result)
            } catch (e: IOException) {
                cont.resumeWithException(e)
            }
        }
    })
}
