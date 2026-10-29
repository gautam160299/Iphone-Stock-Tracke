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
    val isChecking: Boolean = false,
    val lastCheckedTimestamp: Long? = null,
    val lastStatusMessage: String = "Tracker is idle",
    // Latest result per postal code
    val snapshots: Map<String, LocationSnapshot> = emptyMap(),
    val watchedSkuCount: Int = 0,
    val requestsPerCheck: Int = 0,
    val error: String? = null
)

data class BrowseState(
    val model: String = "",
    val postalCode: String = "",
    val isLoading: Boolean = false,
    val snapshot: LocationSnapshot? = null
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
    private var lastPruneAtMs = 0L
    private var previouslyAvailablePairs: Set<String> = emptySet()
    private val lastAlarmAtMs = mutableMapOf<String, Long>()

    // Main thread only: manual checks running or queued
    private var pendingWatchPolls = 0
    private var pendingBrowsePolls = 0

    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var preferences: TrackerPreferences
    private lateinit var ruleStore: WatchRuleStore
    private lateinit var historyDao: HistoryDao
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
        const val ACTION_BROWSE_POLL = "com.example.applestockpulse.ACTION_BROWSE_POLL"
        const val EXTRA_MODEL = "model"

        // Part numbers per request to Apple; more SKUs are split into several requests
        const val MAX_SKUS_PER_REQUEST = 10

        // Minimum gap between two requests to Apple, so repeated taps cannot flood it
        private const val MIN_REQUEST_GAP_MS = 5_000L

        // A store/SKU pair that flickers in and out of stock alarms at most once per this period
        private const val ALARM_COOLDOWN_MS = 10 * 60 * 1000L

        // Renewed every cycle; only expires if the loop hangs
        private const val WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1000L

        private const val PRUNE_INTERVAL_MS = 6 * 60 * 60 * 1000L
        private const val DAY_MS = 24 * 60 * 60 * 1000L

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _uiState = MutableStateFlow(TrackerUiState())
        val uiState: StateFlow<TrackerUiState> = _uiState.asStateFlow()

        private val _browseState = MutableStateFlow(BrowseState())
        val browseState: StateFlow<BrowseState> = _browseState.asStateFlow()

        fun requestCount(skuCount: Int): Int = (skuCount + MAX_SKUS_PER_REQUEST - 1) / MAX_SKUS_PER_REQUEST
    }

    override fun onCreate() {
        super.onCreate()
        preferences = TrackerPreferences(this)
        ruleStore = WatchRuleStore.get(this)
        historyDao = HistoryDatabase.get(this).historyDao()
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
                requestManualWatchPoll()
                if (_isRunning.value) START_STICKY else START_NOT_STICKY
            }
            ACTION_BROWSE_POLL -> {
                requestBrowsePoll(intent.getStringExtra(EXTRA_MODEL) ?: preferences.getBrowseModel())
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
        val initialNotification = buildOngoingStatusNotification("Scanning nearby Apple Store inventory...")

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
                    pollMutex.withLock { executeWatchCycle(background = true) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Unhandled exception in polling cycle", e)
                    _uiState.update {
                        it.copy(isChecking = false, lastStatusMessage = "Poll error: ${e.message}", error = e.localizedMessage)
                    }
                }

                // 27-33 s: Apple stock often sells out within a minute
                val jitterDelay = 27_000L + Random.nextLong(0, 6_001L)
                Log.d(TAG, "Next poll in ${jitterDelay / 1000}s")
                delay(jitterDelay)
            }
        }
    }

    /**
     * Check all rules now ("Check Now", a new or edited rule). Without monitoring this only
     * refreshes the screen: no alarms, no wake lock, and the service stops itself afterwards.
     */
    private fun requestManualWatchPoll() {
        // One running and one queued is enough; more taps add nothing
        if (pendingWatchPolls >= 2) return
        pendingWatchPolls++
        launchManual(onDone = { pendingWatchPolls-- }) { executeWatchCycle(background = false) }
    }

    private fun requestBrowsePoll(model: String) {
        if (pendingBrowsePolls >= 2) return
        pendingBrowsePolls++
        launchManual(onDone = { pendingBrowsePolls-- }) { executeBrowse(model) }
    }

    private fun launchManual(onDone: () -> Unit, block: suspend () -> Unit) {
        serviceScope.launch {
            try {
                pollMutex.withLock { block() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Manual poll failed", e)
                _uiState.update { it.copy(isChecking = false, lastStatusMessage = "Poll error: ${e.message}", error = e.localizedMessage) }
            } finally {
                withContext(NonCancellable + Dispatchers.Main) {
                    onDone()
                    stopSelfIfIdle()
                }
            }
        }
    }

    private fun stopSelfIfIdle() {
        if (!_isRunning.value && pendingWatchPolls == 0 && pendingBrowsePolls == 0) {
            stopSelf()
        }
    }

    // Must hold pollMutex
    private suspend fun executeWatchCycle(background: Boolean) {
        val rules = ruleStore.rules.value
        val defaultLocation = preferences.getDefaultLocation()
        val storeFilter = preferences.getParsedStoreFilter()
        val currentTimeFormatted = SimpleDateFormat("h:mm:ss a", Locale.getDefault()).format(Date())

        if (rules.isEmpty()) {
            _uiState.update {
                it.copy(
                    snapshots = emptyMap(),
                    watchedSkuCount = 0,
                    requestsPerCheck = 0,
                    lastStatusMessage = "No devices to watch",
                    error = null
                )
            }
            updateOngoingStatusNotification("No devices to watch. Add one in the app.")
            return
        }

        // One group of SKUs per postal code; each rule applies its own radius afterwards.
        // Every colour and storage of a watched model is checked, so restock history covers
        // the whole model (alarms still follow the rules only).
        val skusByPostal = rules
            .groupBy { it.effectiveLocation(defaultLocation).postalCode }
            .mapValues { (_, group) ->
                val models = group.flatMap { rule -> rule.selectors.map { it.model } }.distinct()
                (models.flatMap { ProductCatalog.getSkusForSelection(it, "All", "All") } + group.flatMap { it.skus() })
                    .distinct()
                    .sorted()
            }
            .filterValues { it.isNotEmpty() }

        val skuCount = skusByPostal.values.sumOf { it.size }
        val requests = skusByPostal.values.sumOf { requestCount(it.size) }
        _uiState.update { it.copy(isChecking = true, watchedSkuCount = skuCount, requestsPerCheck = requests) }

        val snapshots = mutableMapOf<String, LocationSnapshot>()
        for ((postal, skus) in skusByPostal) {
            if (!TrackerPreferences.isValidPostalCode(postal)) {
                snapshots[postal] = LocationSnapshot(postal, error = "\"$postal\" is not a valid Canadian postal code.")
                continue
            }
            snapshots[postal] = fetchLocation(postal, skus, storeFilter, background)
        }

        val errors = snapshots.values.mapNotNull { snap ->
            snap.error?.let { if (snapshots.size > 1) "${SearchLocation.formatPostalCode(snap.postalCode)}: $it" else it }
        }
        val unknown = snapshots.values.flatMap { it.unknownSkus }.distinct()
        val problems = errors + listOfNotNull(
            unknown.takeIf { it.isNotEmpty() }?.let {
                "Apple does not recognize these part numbers: ${it.joinToString()}. Check them in ProductCatalog.kt."
            }
        )

        val inStockRules = rules.count {
            RuleStatusCalculator.statusFor(it, defaultLocation, snapshots).inStock
        }
        val statusMsg = if (inStockRules > 0) {
            "Checked at $currentTimeFormatted — 🚨 $inStockRules of ${rules.size} watched device groups in stock!"
        } else {
            "Checked at $currentTimeFormatted — None in stock nearby"
        }

        _uiState.update {
            it.copy(
                isRunning = _isRunning.value,
                isChecking = false,
                lastCheckedTimestamp = System.currentTimeMillis(),
                lastStatusMessage = statusMsg,
                snapshots = snapshots,
                error = problems.joinToString("\n").ifEmpty { null }
            )
        }

        // Alarms only while the user has monitoring on
        if (_isRunning.value) {
            postAlarmsForNewStock(rules, defaultLocation, snapshots)
        }

        updateOngoingStatusNotification(
            if (inStockRules > 0) "🚨 $inStockRules device group(s) IN STOCK! ($currentTimeFormatted)"
            else "${rules.size} device group(s) • None available ($currentTimeFormatted)"
        )
    }

    // Must hold pollMutex
    private suspend fun executeBrowse(model: String) {
        val postal = preferences.getCleanPostalCode()
        val skus = ProductCatalog.getSkusForSelection(model, "All", "All")
        _browseState.update { it.copy(model = model, postalCode = postal, isLoading = true) }

        val snapshot = if (!TrackerPreferences.isValidPostalCode(postal)) {
            LocationSnapshot(postal, error = "\"$postal\" is not a valid Canadian postal code. Fix it in Settings.")
        } else {
            fetchLocation(postal, skus, preferences.getParsedStoreFilter(), background = false)
        }

        // A newer Browse request for another model may have started meanwhile
        _browseState.update {
            if (it.model == model) it.copy(isLoading = false, snapshot = snapshot) else it
        }
    }

    /**
     * Fetches all [skus] for one postal code in groups of [MAX_SKUS_PER_REQUEST],
     * merges the stores, and records the result in the history.
     */
    private suspend fun fetchLocation(
        postalCode: String,
        skus: List<String>,
        storeFilter: Set<String>,
        background: Boolean
    ): LocationSnapshot {
        val storesByNumber = linkedMapOf<String, StoreInventoryCard>()
        val unknown = mutableListOf<String>()
        val errors = mutableListOf<String>()
        var lastStatus: Int? = null
        var failed = false

        for (chunk in skus.chunked(MAX_SKUS_PER_REQUEST)) {
            val result = fetchChunk(postalCode, chunk)
            lastStatus = result.statusCode ?: lastStatus
            if (result.error != null) {
                failed = true
                errors.add(result.error)
                continue
            }
            val parsed = result.parsed ?: continue
            parsed.apiErrorMessage?.let { errors.add("Apple: $it") }
            unknown.addAll(parsed.unknownSkus)
            if (parsed.storesBeforeFilter == 0 && parsed.apiErrorMessage == null) {
                errors.add("Apple returned no stores near ${SearchLocation.formatPostalCode(postalCode)}.")
            }
            for (store in parsed.stores) {
                val existing = storesByNumber[store.storeNumber]
                storesByNumber[store.storeNumber] = if (existing == null) store else {
                    val variants = existing.variants + store.variants
                    existing.copy(variants = variants, totalAvailableInStore = variants.count { it.isAvailable })
                }
            }
        }

        val allStores = storesByNumber.values.sortedBy { it.distanceKm ?: Double.MAX_VALUE }
        recordHistory(postalCode, skus, allStores, success = !failed, background = background)

        val visible = if (storeFilter.isEmpty()) allStores else allStores.filter { it.storeNumber.uppercase() in storeFilter }
        if (allStores.isNotEmpty() && visible.isEmpty()) {
            errors.add("No nearby store matches your store filter (${storeFilter.joinToString()}).")
        }

        return LocationSnapshot(
            postalCode = postalCode,
            checkedAt = System.currentTimeMillis(),
            stores = visible,
            httpStatusCode = lastStatus,
            error = errors.distinct().joinToString("\n").ifEmpty { null },
            unknownSkus = unknown.distinct()
        )
    }

    private data class ChunkResult(
        val statusCode: Int?,
        val parsed: InventoryParseResult?,
        val error: String?
    )

    private suspend fun fetchChunk(postalCode: String, skus: List<String>): ChunkResult {
        val waitMs = lastRequestAtMs + MIN_REQUEST_GAP_MS - System.currentTimeMillis()
        if (waitMs > 0) delay(waitMs)
        lastRequestAtMs = System.currentTimeMillis()

        val queryBuilder = StringBuilder("pl=true&mts.0=regular")
        skus.forEachIndexed { index, sku ->
            queryBuilder.append("&parts.$index=").append(URLEncoder.encode(sku.trim(), "UTF-8"))
        }
        queryBuilder.append("&location=").append(URLEncoder.encode(postalCode, "UTF-8"))

        val request = Request.Builder()
            .url("https://www.apple.com/ca/shop/retail/pickup-message?$queryBuilder")
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 15; SM-F966U) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36")
            .addHeader("Accept", "application/json, text/javascript, */*; q=0.01")
            .addHeader("Accept-Language", "en-CA,en-US;q=0.9,en;q=0.8")
            .addHeader("Referer", "https://www.apple.com/ca/shop/buy-iphone")
            .addHeader("Sec-Fetch-Mode", "cors")
            .addHeader("Sec-Fetch-Site", "same-origin")
            .build()

        Log.d(TAG, "Polling ${skus.size} SKU(s) near $postalCode")
        val (statusCode, body) = try {
            okHttpClient.newCall(request).await()
        } catch (e: IOException) {
            Log.e(TAG, "Network error", e)
            return ChunkResult(null, null, "Network error: ${e.localizedMessage}")
        }

        if (statusCode !in 200..299) {
            val message = if (statusCode == 541) {
                "HTTP 541: Apple is blocking or rate-limiting these requests. Retrying in ~30s..."
            } else {
                "HTTP $statusCode from Apple server"
            }
            return ChunkResult(statusCode, null, message)
        }

        return try {
            ChunkResult(statusCode, AppleInventoryParser.parse(body, skus, emptySet(), ""), null)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Apple stores JSON", e)
            ChunkResult(statusCode, null, "Apple sent a response that is not inventory data (HTTP $statusCode). It may be a block page.")
        }
    }

    private suspend fun recordHistory(
        postalCode: String,
        skus: List<String>,
        stores: List<StoreInventoryCard>,
        success: Boolean,
        background: Boolean
    ) {
        try {
            val now = System.currentTimeMillis()
            val observations = stores.flatMap { store ->
                store.variants.filter { it.isKnown }.map {
                    StockObservation(store.storeNumber, store.storeName, it.sku, it.isAvailable)
                }
            }
            val previous = if (observations.isEmpty()) emptyMap() else
                historyDao.statesForSkus(skus).associateBy { it.storeNumber to it.sku }
            val changes = ChangeDetector.detect(previous, observations, now)
            historyDao.record(
                CheckLogEntity(timestamp = now, postalCode = postalCode, skuCount = skus.size, success = success, background = background),
                changes.events,
                changes.states
            )
            if (now - lastPruneAtMs > PRUNE_INTERVAL_MS) {
                historyDao.pruneBefore(now - TrackerPreferences.HISTORY_RETENTION_DAYS * DAY_MS)
                lastPruneAtMs = now
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // History is a bonus; never let it break the alarm path
            Log.e(TAG, "Failed to record history", e)
        }
    }

    // Must hold pollMutex
    private fun postAlarmsForNewStock(
        rules: List<WatchRule>,
        defaultLocation: SearchLocation,
        snapshots: Map<String, LocationSnapshot>
    ) {
        val now = System.currentTimeMillis()
        val availableNow = mutableSetOf<String>()
        val armedRules = rules.filter { it.alarmOn }.map { it to it.skus().toSet() }

        for ((postal, snapshot) in snapshots) {
            val rulesHere = armedRules.filter { (rule, _) -> rule.effectiveLocation(defaultLocation).postalCode == postal }
            if (rulesHere.isEmpty()) continue

            for (store in snapshot.stores) {
                val newVariants = mutableListOf<VariantAvailability>()
                val matchedRules = linkedSetOf<WatchRule>()
                for (variant in store.variants.filter { it.isAvailable }) {
                    val matching = rulesHere.filter { (rule, skus) ->
                        variant.sku in skus &&
                            LocationSnapshot.isWithinRadius(store, rule.effectiveLocation(defaultLocation).radiusKm)
                    }.map { it.first }
                    if (matching.isEmpty()) continue

                    val key = "${store.storeNumber}:${variant.sku}"
                    availableNow.add(key)
                    if (key !in previouslyAvailablePairs && now - (lastAlarmAtMs[key] ?: 0L) >= ALARM_COOLDOWN_MS) {
                        newVariants.add(variant)
                        matchedRules.addAll(matching)
                    }
                }
                if (newVariants.isEmpty() || !_isRunning.value) continue

                newVariants.forEach { lastAlarmAtMs["${store.storeNumber}:${it.sku}"] = now }
                AlertNotifier.postStoreAlarm(this, store, newVariants, matchedRules.toList())
            }
        }

        previouslyAvailablePairs = availableNow
    }

    private fun buildOngoingStatusNotification(statusText: String): Notification {
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

        val rules = ruleStore.rules.value
        val bodyText = "Watching ${rules.size} device group(s) near ${preferences.getDefaultLocation().label()}"

        return NotificationCompat.Builder(this, AlertNotifier.STATUS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_apple_stock)
            .setContentTitle("AppleStockPulse Monitoring Active")
            .setContentText(statusText)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$statusText\n$bodyText"))
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

    private fun updateOngoingStatusNotification(statusText: String) {
        if (_isRunning.value) {
            notificationManager.notify(NOTIFICATION_ID_STATUS, buildOngoingStatusNotification(statusText))
        }
    }

    private fun stopTracker() {
        _isRunning.value = false
        preferences.setTrackingEnabled(false)
        // Cancels the loop and any poll in flight, including its HTTP call, so no alarm can follow
        pollingJob?.cancel()
        serviceJob.cancelChildren()
        _uiState.update {
            it.copy(isRunning = false, isChecking = false, lastStatusMessage = "Tracker stopped by user")
        }
        _browseState.update { it.copy(isLoading = false) }
        AlarmPlayer.stop()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        _isRunning.value = false
        _uiState.update { it.copy(isRunning = false, isChecking = false) }
        _browseState.update { it.copy(isLoading = false) }
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
