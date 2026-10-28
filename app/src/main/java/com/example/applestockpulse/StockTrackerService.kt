package com.example.applestockpulse

import com.example.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.random.Random

data class StoreSkuAvailability(
    val storeNumber: String,
    val storeName: String,
    val city: String,
    val sku: String,
    val isAvailable: Boolean,
    val pickupDisplay: String,
    val pickupQuote: String,
    val productTitle: String = ""
)

data class TrackerUiState(
    val isRunning: Boolean = false,
    val lastCheckedTimestamp: Long? = null,
    val lastStatusMessage: String = "Tracker is idle",
    val httpStatusCode: Int? = null,
    val storesResults: List<StoreSkuAvailability> = emptyList(),
    val totalAvailableCount: Int = 0,
    val activeSkus: List<String> = emptyList(),
    val postalCode: String = "",
    val error: String? = null
)

class StockTrackerService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.IO)
    private var pollingJob: Job? = null

    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var preferences: TrackerPreferences
    private lateinit var notificationManager: NotificationManager

    private val alertedStoreSkuPairs = Collections.synchronizedSet(mutableSetOf<String>())

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    companion object {
        private const val TAG = "StockTrackerService"

        const val STATUS_CHANNEL_ID = "channel_ongoing_status"
        const val ALERT_CHANNEL_ID = "channel_stock_alerts"
        const val NOTIFICATION_ID_STATUS = 1001

        const val ACTION_START = "com.example.applestockpulse.ACTION_START"
        const val ACTION_STOP = "com.example.applestockpulse.ACTION_STOP"
        const val ACTION_FORCE_POLL = "com.example.applestockpulse.ACTION_FORCE_POLL"
        const val ACTION_TRIGGER_TEST = "com.example.applestockpulse.ACTION_TRIGGER_TEST"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _uiState = MutableStateFlow(TrackerUiState())
        val uiState: StateFlow<TrackerUiState> = _uiState.asStateFlow()
    }

    override fun onCreate() {
        super.onCreate()
        preferences = TrackerPreferences(this)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        createNotificationChannels()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_STOP -> {
                Log.d(TAG, "ACTION_STOP received")
                stopTracker()
                return START_NOT_STICKY
            }
            ACTION_TRIGGER_TEST -> {
                Log.d(TAG, "ACTION_TRIGGER_TEST received")
                triggerTestAlertNotification()
                if (!_isRunning.value) {
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
            ACTION_FORCE_POLL -> {
                Log.d(TAG, "ACTION_FORCE_POLL received")
                if (_isRunning.value) {
                    serviceScope.launch {
                        executePollCycle()
                    }
                }
            }
            ACTION_START -> {
                Log.d(TAG, "ACTION_START received")
                startTrackerForeground()
            }
        }

        return START_STICKY
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
            if (wakeLock?.isHeld == false) {
                // Acquire wakelock with 2-hour safety timeout
                wakeLock?.acquire(2 * 60 * 60 * 1000L)
                Log.d(TAG, "Partial wake lock acquired")
            }
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

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Channel 1: Ongoing Low-Priority Status Channel
            val statusChannel = NotificationChannel(
                STATUS_CHANNEL_ID,
                "Ongoing Polling Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active background inventory polling status."
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(statusChannel)

            // Channel 2: High-Priority Stock Alert Channel with Alarm Sound & Vibration
            val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

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
                setSound(alarmSound, audioAttributes)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 600, 300, 600, 300, 800)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableLights(true)
            }
            notificationManager.createNotificationChannel(alertChannel)
        }
    }

    private fun startTrackerForeground() {
        _isRunning.value = true
        val initialNotification = buildOngoingStatusNotification(
            statusText = "Initializing inventory polling...",
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
            Log.e(TAG, "Failed to start foreground service", e)
            startForeground(NOTIFICATION_ID_STATUS, initialNotification)
        }

        startPollingLoop()
    }

    private fun startPollingLoop() {
        pollingJob?.cancel()
        pollingJob = serviceScope.launch {
            Log.d(TAG, "Background polling loop started")
            while (isActive) {
                try {
                    executePollCycle()
                } catch (e: Exception) {
                    Log.e(TAG, "Unhandled exception in polling cycle", e)
                    _uiState.value = _uiState.value.copy(
                        lastStatusMessage = "Poll error: ${e.message}",
                        error = e.localizedMessage
                    )
                }

                // Randomized jitter delay between 52,000ms and 68,000ms
                val jitterDelay = 52_000L + Random.nextLong(0, 16_001L)
                Log.d(TAG, "Next poll scheduled in ${jitterDelay / 1000}s")
                delay(jitterDelay)
            }
        }
    }

    private fun executePollCycle() {
        val postalCode = preferences.getCleanPostalCode()
        val skus = preferences.getParsedSkus()
        val storeFilter = preferences.getParsedStoreFilter()

        val timeFormatter = SimpleDateFormat("h:mm:ss a", Locale.getDefault())
        val currentTimeFormatted = timeFormatter.format(Date())

        if (skus.isEmpty() || postalCode.isBlank()) {
            _uiState.value = _uiState.value.copy(
                lastStatusMessage = "Configuration incomplete (SKU or postal code missing)",
                error = "Please provide valid SKUs and postal code."
            )
            return
        }

        // Build query string
        val queryBuilder = StringBuilder("pl=true&mts.0=regular")
        skus.forEachIndexed { index, sku ->
            val encodedSku = URLEncoder.encode(sku.trim(), "UTF-8")
            queryBuilder.append("&parts.$index=").append(encodedSku)
        }
        val encodedPostal = URLEncoder.encode(postalCode, "UTF-8")
        queryBuilder.append("&location=").append(encodedPostal)

        val targetUrl = "https://www.apple.com/ca/shop/retail/pickup-message?$queryBuilder"
        Log.d(TAG, "Polling URL: $targetUrl")

        val request = Request.Builder()
            .url(targetUrl)
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 15; SM-F966U) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36")
            .addHeader("Accept", "application/json, text/javascript, */*; q=0.01")
            .addHeader("Accept-Language", "en-CA,en-US;q=0.9,en;q=0.8")
            .addHeader("Referer", "https://www.apple.com/ca/shop/buy-iphone")
            .addHeader("Sec-Fetch-Mode", "cors")
            .addHeader("Sec-Fetch-Site", "same-origin")
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            val statusCode = response.code
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMsg = if (statusCode == 541) {
                    "HTTP 541: Apple CDN rate-limiting/interception on endpoint. Retrying in ~60s..."
                } else {
                    "HTTP $statusCode from Apple server"
                }
                Log.w(TAG, errorMsg)
                _uiState.value = _uiState.value.copy(
                    lastCheckedTimestamp = System.currentTimeMillis(),
                    httpStatusCode = statusCode,
                    lastStatusMessage = errorMsg,
                    error = errorMsg
                )
                updateOngoingStatusNotification("HTTP $statusCode at $currentTimeFormatted", 0, currentTimeFormatted)
                return
            }

            // Parse response
            val parsedResults = parseAppleInventoryJson(
                jsonString = responseBody,
                skus = skus,
                storeFilter = storeFilter
            )

            // Check if Apple returned an API error message (e.g. invalid SKU)
            val apiErrorMessage = try {
                val rootObj = JSONObject(responseBody)
                rootObj.optJSONObject("body")?.optString("errorMessage")?.takeIf { it.isNotBlank() }
            } catch (_: Exception) {
                null
            }

            val availableCount = parsedResults.count { it.isAvailable }
            val nowMs = System.currentTimeMillis()

            val statusMessage = if (apiErrorMessage != null) {
                "Apple notice: $apiErrorMessage"
            } else {
                "Checked at $currentTimeFormatted — $availableCount in stock (${parsedResults.size} stores scanned)"
            }

            _uiState.value = TrackerUiState(
                isRunning = true,
                lastCheckedTimestamp = nowMs,
                lastStatusMessage = statusMessage,
                httpStatusCode = statusCode,
                storesResults = parsedResults,
                totalAvailableCount = availableCount,
                activeSkus = skus,
                postalCode = postalCode,
                error = apiErrorMessage
            )

            val summaryStatus = if (availableCount > 0) {
                "🚨 $availableCount IN STOCK! Checked $currentTimeFormatted"
            } else {
                "Active • Checked at $currentTimeFormatted"
            }
            updateOngoingStatusNotification(summaryStatus, availableCount, currentTimeFormatted)

        } catch (e: Exception) {
            Log.e(TAG, "Network error during polling cycle", e)
            _uiState.value = _uiState.value.copy(
                lastCheckedTimestamp = System.currentTimeMillis(),
                lastStatusMessage = "Network error: ${e.message}",
                error = e.localizedMessage
            )
            updateOngoingStatusNotification("Connection failed at $currentTimeFormatted", 0, currentTimeFormatted)
        }
    }

    private fun parseAppleInventoryJson(
        jsonString: String,
        skus: List<String>,
        storeFilter: Set<String>
    ): List<StoreSkuAvailability> {
        val results = mutableListOf<StoreSkuAvailability>()

        try {
            val root = JSONObject(jsonString)
            val body = root.optJSONObject("body") ?: return emptyList()

            // Support both /shop/retail/pickup-message (body.stores) and legacy (body.content.pickupMessage.stores)
            val storesArray: JSONArray = body.optJSONArray("stores")
                ?: body.optJSONObject("content")?.optJSONObject("pickupMessage")?.optJSONArray("stores")
                ?: body.optJSONObject("pickupMessage")?.optJSONArray("stores")
                ?: JSONArray()

            for (i in 0 until storesArray.length()) {
                val storeObj = storesArray.optJSONObject(i) ?: continue
                val storeNumber = storeObj.optString("storeNumber", "").trim()
                val storeName = storeObj.optString("storeName", "Apple Store")
                val city = storeObj.optString("city", "")
                val partsAvailability = storeObj.optJSONObject("partsAvailability") ?: JSONObject()

                // Filter target stores if user specified store numbers
                if (storeFilter.isNotEmpty() && !storeFilter.contains(storeNumber.uppercase())) {
                    continue
                }

                for (sku in skus) {
                    val skuObj = partsAvailability.optJSONObject(sku) ?: continue
                    val pickupDisplay = skuObj.optString("pickupDisplay", "unavailable")
                    val pickupSearchQuote = skuObj.optString("pickupSearchQuote", "")
                    val rawTitle = skuObj.optString("storePickupProductTitle", sku)
                    val friendlyTitle = ProductCatalog.getProductName(sku, rawTitle)
                    val isAvailable = pickupDisplay.equals("available", ignoreCase = true)

                    val pairKey = "$storeNumber:$sku"

                    if (isAvailable) {
                        if (!alertedStoreSkuPairs.contains(pairKey)) {
                            alertedStoreSkuPairs.add(pairKey)
                            triggerStockAlarmNotification(
                                storeNumber = storeNumber,
                                storeName = storeName,
                                city = city,
                                sku = sku,
                                productTitle = friendlyTitle,
                                pickupQuote = pickupSearchQuote
                            )
                        }
                    } else {
                        // Automatically re-arm for future restocks
                        alertedStoreSkuPairs.remove(pairKey)
                    }

                    results.add(
                        StoreSkuAvailability(
                            storeNumber = storeNumber,
                            storeName = storeName,
                            city = city,
                            sku = sku,
                            isAvailable = isAvailable,
                            pickupDisplay = pickupDisplay,
                            pickupQuote = pickupSearchQuote,
                            productTitle = friendlyTitle
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Apple inventory JSON", e)
        }

        return results
    }

    private fun triggerStockAlarmNotification(
        storeNumber: String,
        storeName: String,
        city: String,
        sku: String,
        productTitle: String,
        pickupQuote: String
    ) {
        val uniqueNotificationId = ("$storeNumber:$sku").hashCode()

        // Checkout Bag Deep Link
        val checkoutIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.apple.com/ca/shop/bag")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val checkoutPendingIntent = PendingIntent.getActivity(
            this,
            uniqueNotificationId,
            checkoutIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_apple_stock)
            .setContentTitle("🚨 In Stock: $productTitle")
            .setContentText("$storeName ($city): $pickupQuote")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$productTitle\nApple Store $storeName ($city)\nStatus: $pickupQuote\nSKU: $sku\nTap to open Apple Store Bag immediately.")
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSound(alarmSound)
            .setVibrate(longArrayOf(0, 600, 300, 600, 300, 800))
            .setAutoCancel(true)
            .setContentIntent(checkoutPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_send,
                "Open Apple Bag",
                checkoutPendingIntent
            )
            .build()

        notificationManager.notify(uniqueNotificationId, notification)
        Log.i(TAG, "Triggered high-priority alarm notification for $storeNumber:$sku ($storeName)")
    }

    fun triggerTestAlertNotification() {
        val uniqueId = 9999
        val checkoutIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.apple.com/ca/shop/bag")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val checkoutPendingIntent = PendingIntent.getActivity(
            this,
            uniqueId,
            checkoutIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val testSku = "MYDR3VC/A"
        val testName = ProductCatalog.getProductName(testSku, "iPhone 16 128GB White")

        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_apple_stock)
            .setContentTitle("🚨 [TEST] In Stock: $testName")
            .setContentText("Guildford Town Centre (Surrey) has stock! Available Today")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("TEST ALARM NOTIFICATION:\n$testName (SKU: $testSku)\nGuildford Town Centre (Surrey)\nAudio & vibration verification successful.\nTap to test checkout bag deep-link.")
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSound(alarmSound)
            .setVibrate(longArrayOf(0, 600, 300, 600, 300, 800))
            .setAutoCancel(true)
            .setContentIntent(checkoutPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_send,
                "Open Apple Bag",
                checkoutPendingIntent
            )
            .build()

        notificationManager.notify(uniqueId, notification)
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

        val activeSkus = preferences.getSkus()
        val postal = preferences.getPostalCode()
        val timeLabel = lastChecked ?: "Not checked yet"

        val bodyText = "SKUs: $activeSkus • Postal: $postal • Last: $timeLabel • Stock: $availableCount"

        return NotificationCompat.Builder(this, STATUS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_apple_stock)
            .setContentTitle("AppleStockPulse Polling Active")
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$bodyText\n$statusText"))
            .setOngoing(true)
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
        pollingJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isRunning = false,
            lastStatusMessage = "Tracker stopped by user"
        )
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        _isRunning.value = false
        pollingJob?.cancel()
        serviceScope.cancel()
        releaseWakeLock()
        Log.d(TAG, "StockTrackerService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
