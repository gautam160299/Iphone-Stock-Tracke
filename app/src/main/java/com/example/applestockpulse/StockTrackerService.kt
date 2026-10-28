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

data class VariantAvailability(
    val sku: String,
    val model: String,
    val storage: String,
    val color: String,
    val isAvailable: Boolean,
    val pickupDisplay: String,
    val pickupQuote: String
)

data class StoreInventoryCard(
    val storeNumber: String,
    val storeName: String,
    val city: String,
    val distanceText: String,
    val addressText: String,
    val phoneText: String,
    val totalAvailableInStore: Int,
    val variants: List<VariantAvailability>
)

data class TrackerUiState(
    val isRunning: Boolean = false,
    val lastCheckedTimestamp: Long? = null,
    val lastStatusMessage: String = "Tracker is idle",
    val httpStatusCode: Int? = null,
    val selectedModel: String = "iPhone Duo",
    val storeCards: List<StoreInventoryCard> = emptyList(),
    val totalAvailableCount: Int = 0,
    val activeSkus: List<String> = emptyList(),
    val postalCode: String = "V6C1T2",
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
                serviceScope.launch {
                    executePollCycle()
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
            val statusChannel = NotificationChannel(
                STATUS_CHANNEL_ID,
                "Ongoing Polling Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active background inventory polling status."
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(statusChannel)

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

                val jitterDelay = 52_000L + Random.nextLong(0, 16_001L)
                Log.d(TAG, "Next poll in ${jitterDelay / 1000}s")
                delay(jitterDelay)
            }
        }
    }

    private fun executePollCycle() {
        val postalCode = preferences.getCleanPostalCode()
        val model = preferences.getSelectedModel()
        val skus = preferences.getActiveQuerySkus()
        val storeFilter = preferences.getParsedStoreFilter()

        val timeFormatter = SimpleDateFormat("h:mm:ss a", Locale.getDefault())
        val currentTimeFormatted = timeFormatter.format(Date())

        if (skus.isEmpty() || postalCode.isBlank()) {
            _uiState.value = _uiState.value.copy(
                lastStatusMessage = "Configuration incomplete (Postal code missing)",
                error = "Please provide a valid Canadian postal code."
            )
            return
        }

        // Batch all SKUs into a single GET request
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

            // Parse response into structured store cards with full variant matrix
            val parsedStores = parseAppleStoresJson(
                jsonString = responseBody,
                skus = skus,
                storeFilter = storeFilter,
                selectedModel = model
            )

            val totalAvailable = parsedStores.sumOf { it.totalAvailableInStore }
            val nowMs = System.currentTimeMillis()

            val statusMsg = if (totalAvailable > 0) {
                "Checked at $currentTimeFormatted — 🚨 $totalAvailable IN STOCK in nearby stores!"
            } else {
                "Checked at $currentTimeFormatted — None in stock nearby"
            }

            _uiState.value = TrackerUiState(
                isRunning = _isRunning.value,
                lastCheckedTimestamp = nowMs,
                lastStatusMessage = statusMsg,
                httpStatusCode = statusCode,
                selectedModel = model,
                storeCards = parsedStores,
                totalAvailableCount = totalAvailable,
                activeSkus = skus,
                postalCode = postalCode,
                error = null
            )

            val summaryNotificationText = if (totalAvailable > 0) {
                "🚨 $totalAvailable $model IN STOCK! ($currentTimeFormatted)"
            } else {
                "$model • None available ($currentTimeFormatted)"
            }
            updateOngoingStatusNotification(summaryNotificationText, totalAvailable, currentTimeFormatted)

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

    private fun parseAppleStoresJson(
        jsonString: String,
        skus: List<String>,
        storeFilter: Set<String>,
        selectedModel: String
    ): List<StoreInventoryCard> {
        val cards = mutableListOf<StoreInventoryCard>()

        try {
            val root = JSONObject(jsonString)
            val body = root.optJSONObject("body") ?: return emptyList()

            val storesArray: JSONArray = body.optJSONArray("stores")
                ?: body.optJSONObject("content")?.optJSONObject("pickupMessage")?.optJSONArray("stores")
                ?: body.optJSONObject("pickupMessage")?.optJSONArray("stores")
                ?: JSONArray()

            for (i in 0 until storesArray.length()) {
                val storeObj = storesArray.optJSONObject(i) ?: continue
                val storeNumber = storeObj.optString("storeNumber", "").trim()
                val storeName = storeObj.optString("storeName", "Apple Store")
                val city = storeObj.optString("city", "")

                if (storeFilter.isNotEmpty() && !storeFilter.contains(storeNumber.uppercase())) {
                    continue
                }

                // Format distance
                val distanceRaw = storeObj.optString("storeDistanceWithUnit", "")
                val distanceText = if (distanceRaw.isNotBlank()) "$distanceRaw away" else ""

                // Address & Phone
                val addressObj = storeObj.optJSONObject("address")
                val addrLine = addressObj?.optString("address2", "") ?: ""
                val postCode = addressObj?.optString("postalCode", "") ?: ""
                val addressText = listOf(addrLine, city, postCode).filter { it.isNotBlank() }.joinToString(", ")
                val phoneText = storeObj.optString("phoneNumber", "")

                val partsAvailability = storeObj.optJSONObject("partsAvailability") ?: JSONObject()

                val variantsList = mutableListOf<VariantAvailability>()
                var storeAvailableCount = 0

                for (sku in skus) {
                    val skuObj = partsAvailability.optJSONObject(sku)
                    val pickupDisplay = skuObj?.optString("pickupDisplay") ?: "unavailable"
                    val pickupQuote = skuObj?.optString("pickupSearchQuote") ?: "None available"
                    val isAvailable = pickupDisplay.equals("available", ignoreCase = true)

                    if (isAvailable) {
                        storeAvailableCount++
                    }

                    val product = ProductCatalog.getProduct(sku)
                    val modelName = product?.model ?: selectedModel
                    val storage = product?.storage ?: ""
                    val color = product?.color ?: ""

                    val pairKey = "$storeNumber:$sku"

                    if (isAvailable) {
                        // Check if this SKU is armed for alarms
                        if (preferences.isSkuArmed(sku)) {
                            if (!alertedStoreSkuPairs.contains(pairKey)) {
                                alertedStoreSkuPairs.add(pairKey)
                                triggerStockAlarmNotification(
                                    storeNumber = storeNumber,
                                    storeName = storeName,
                                    city = city,
                                    sku = sku,
                                    productTitle = product?.name ?: "$modelName $storage $color",
                                    pickupQuote = pickupQuote
                                )
                            }
                        }
                    } else {
                        alertedStoreSkuPairs.remove(pairKey)
                    }

                    variantsList.add(
                        VariantAvailability(
                            sku = sku,
                            model = modelName,
                            storage = storage,
                            color = color,
                            isAvailable = isAvailable,
                            pickupDisplay = pickupDisplay,
                            pickupQuote = pickupQuote
                        )
                    )
                }

                cards.add(
                    StoreInventoryCard(
                        storeNumber = storeNumber,
                        storeName = storeName,
                        city = city,
                        distanceText = distanceText,
                        addressText = addressText,
                        phoneText = phoneText,
                        totalAvailableInStore = storeAvailableCount,
                        variants = variantsList
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Apple stores JSON", e)
        }

        return cards
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
                    .bigText("$productTitle\nApple Store $storeName ($city)\nStatus: $pickupQuote\nTap to open Apple Store Bag immediately.")
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

        val testName = "iPhone Duo 256GB Star White"

        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_apple_stock)
            .setContentTitle("🚨 [TEST] In Stock: $testName")
            .setContentText("Pacific Centre (Vancouver) has stock! Available Today")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("TEST ALARM NOTIFICATION:\n$testName\nPacific Centre (Vancouver)\nAudio & vibration verification successful.\nTap to test checkout bag deep-link.")
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

        val model = preferences.getSelectedModel()
        val postal = preferences.getPostalCode()
        val timeLabel = lastChecked ?: "Just started"

        val bodyText = "$model • Postal: $postal • Stock: $availableCount • Last: $timeLabel"

        return NotificationCompat.Builder(this, STATUS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_apple_stock)
            .setContentTitle("AppleStockPulse Monitoring Active")
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
