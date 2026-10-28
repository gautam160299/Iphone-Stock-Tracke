package com.example.applestockpulse

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

/**
 * What one check saw for one store/SKU pair.
 */
data class StockObservation(
    val storeNumber: String,
    val storeName: String,
    val sku: String,
    val inStock: Boolean
)

data class DetectedChanges(
    val events: List<StockEventEntity>,
    val states: List<LastStateEntity>
)

/**
 * Turns check results into in-stock / sold-out events. Pure logic, no database.
 */
object ChangeDetector {

    // Longer than two missed checks: the change may have happened any time in between
    const val GAP_THRESHOLD_MS = 5 * 60 * 1000L

    fun detect(
        previous: Map<Pair<String, String>, LastStateEntity>,
        observations: List<StockObservation>,
        now: Long
    ): DetectedChanges {
        val events = mutableListOf<StockEventEntity>()
        val states = mutableListOf<LastStateEntity>()

        for (obs in observations) {
            val old = previous[obs.storeNumber to obs.sku]
            when {
                old == null -> {
                    states.add(LastStateEntity(obs.storeNumber, obs.sku, obs.inStock, since = now, lastSeen = now))
                    if (obs.inStock) {
                        events.add(event(obs, now, StockEventKind.FIRST_SEEN, afterGap = true))
                    }
                }
                old.inStock != obs.inStock -> {
                    val afterGap = now - old.lastSeen > GAP_THRESHOLD_MS
                    val kind = if (obs.inStock) StockEventKind.RESTOCK else StockEventKind.SOLD_OUT
                    val duration = if (!obs.inStock) now - old.since else null
                    events.add(event(obs, now, kind, afterGap, duration))
                    states.add(LastStateEntity(obs.storeNumber, obs.sku, obs.inStock, since = now, lastSeen = now))
                }
                else -> states.add(old.copy(lastSeen = now))
            }
        }
        return DetectedChanges(events, states)
    }

    private fun event(
        obs: StockObservation,
        now: Long,
        kind: String,
        afterGap: Boolean,
        durationMs: Long? = null
    ) = StockEventEntity(
        timestamp = now,
        storeNumber = obs.storeNumber,
        storeName = obs.storeName,
        sku = obs.sku,
        kind = kind,
        durationMs = durationMs,
        afterGap = afterGap
    )
}

data class CheckGap(val start: Long, val end: Long)

object CheckGaps {

    const val MIN_GAP_MS = 10 * 60 * 1000L

    /**
     * Periods of at least [minGapMs] without a successful background check, between [from] and [now].
     * Only counts time after the first check, because nothing was missed before the app existed.
     */
    fun find(checks: List<CheckLogEntity>, from: Long, now: Long, minGapMs: Long = MIN_GAP_MS): List<CheckGap> {
        val times = checks.filter { it.background && it.success && it.timestamp >= from }.map { it.timestamp }.sorted()
        if (times.isEmpty()) return emptyList()
        val gaps = mutableListOf<CheckGap>()
        for (i in 1 until times.size) {
            if (times[i] - times[i - 1] >= minGapMs) gaps.add(CheckGap(times[i - 1], times[i]))
        }
        if (now - times.last() >= minGapMs) gaps.add(CheckGap(times.last(), now))
        return gaps
    }
}

data class StoreRestocks(val storeNumber: String, val storeName: String, val restocks: Int)

data class PatternSummary(
    val rangeStart: Long,
    // Days since the first check in the range
    val dataDays: Double,
    // Share of hours since the first check that had at least one successful background check
    val coverage: Double,
    val restockCount: Int,
    val restocksLast7Days: Int,
    val restocksByHour: IntArray,
    // [weekday 0=Mon..6=Sun][hour]
    val restocksByWeekdayHour: Array<IntArray>,
    val medianSellOutMinutes: Int?,
    val sellOutSamples: Int,
    val bestStores: List<StoreRestocks>,
    val peakHour: Int?
) {
    val hasEnoughData: Boolean get() = restockCount >= MIN_RESTOCKS

    companion object {
        const val MIN_RESTOCKS = 3
    }
}

object PatternsCalculator {

    private const val HOUR_MS = 60 * 60 * 1000L
    private const val DAY_MS = 24 * HOUR_MS

    /**
     * A "restock" is one store getting stock at one check, however many SKUs came in at once.
     * FIRST_SEEN events are left out: their real start time is unknown.
     */
    fun compute(
        events: List<StockEventEntity>,
        checks: List<CheckLogEntity>,
        skus: Set<String>?,
        storeNumber: String?,
        rangeDays: Int,
        now: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): PatternSummary {
        val rangeStart = now - rangeDays * DAY_MS
        val relevant = events.filter { e ->
            e.timestamp >= rangeStart &&
                (skus == null || e.sku in skus) &&
                (storeNumber == null || e.storeNumber == storeNumber)
        }

        val restocks = relevant
            .filter { it.kind == StockEventKind.RESTOCK }
            .groupBy { it.storeNumber to it.timestamp }
            .map { (key, group) -> Triple(key.first, group.first().storeName, key.second) }

        val calendar = Calendar.getInstance(timeZone)
        val byHour = IntArray(24)
        val byWeekdayHour = Array(7) { IntArray(24) }
        restocks.forEach { (_, _, time) ->
            calendar.timeInMillis = time
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            // Calendar: Sunday = 1 .. Saturday = 7 -> Monday = 0 .. Sunday = 6
            val weekday = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
            byHour[hour]++
            byWeekdayHour[weekday][hour]++
        }

        val sellOutMinutes = relevant
            .filter { it.kind == StockEventKind.SOLD_OUT && !it.afterGap && it.durationMs != null }
            .map { it.durationMs!! / 60_000.0 }
            .sorted()
        val median = if (sellOutMinutes.isEmpty()) null else {
            val mid = sellOutMinutes.size / 2
            val value = if (sellOutMinutes.size % 2 == 1) sellOutMinutes[mid] else (sellOutMinutes[mid - 1] + sellOutMinutes[mid]) / 2
            value.roundToInt().coerceAtLeast(1)
        }

        val bestStores = restocks
            .groupBy { it.first }
            .map { (store, list) -> StoreRestocks(store, list.first().second, list.size) }
            .sortedByDescending { it.restocks }

        val backgroundChecks = checks.filter { it.background && it.success && it.timestamp >= rangeStart }
        val firstCheck = backgroundChecks.minOfOrNull { it.timestamp }
        val (dataDays, coverage) = if (firstCheck == null) 0.0 to 0.0 else {
            val totalHours = ((now - firstCheck) / HOUR_MS).toInt() + 1
            val coveredHours = backgroundChecks.map { (it.timestamp - firstCheck) / HOUR_MS }.toSet().size
            (now - firstCheck).toDouble() / DAY_MS to (coveredHours.toDouble() / totalHours).coerceIn(0.0, 1.0)
        }

        val peak = byHour.indices.maxByOrNull { byHour[it] }?.takeIf { byHour[it] > 0 }

        return PatternSummary(
            rangeStart = rangeStart,
            dataDays = dataDays,
            coverage = coverage,
            restockCount = restocks.size,
            restocksLast7Days = restocks.count { it.third >= now - 7 * DAY_MS },
            restocksByHour = byHour,
            restocksByWeekdayHour = byWeekdayHour,
            medianSellOutMinutes = median,
            sellOutSamples = sellOutMinutes.size,
            bestStores = bestStores,
            peakHour = peak
        )
    }
}

object HistoryCsv {

    fun build(events: List<StockEventEntity>): String {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val builder = StringBuilder("time,store_number,store_name,sku,product,event,minutes_in_stock,time_uncertain\n")
        events.sortedBy { it.timestamp }.forEach { e ->
            builder.append(format.format(Date(e.timestamp))).append(',')
                .append(csv(e.storeNumber)).append(',')
                .append(csv(e.storeName)).append(',')
                .append(csv(e.sku)).append(',')
                .append(csv(ProductCatalog.getProductName(e.sku))).append(',')
                .append(e.kind).append(',')
                .append(e.durationMs?.let { (it / 60_000).toString() } ?: "").append(',')
                .append(e.afterGap).append('\n')
        }
        return builder.toString()
    }

    private fun csv(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' }) "\"" + value.replace("\"", "\"\"") + "\"" else value
}
