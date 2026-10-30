package com.example.applestockpulse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class HistoryLogicTest {

    private val minute = 60_000L
    private val utc: TimeZone = TimeZone.getTimeZone("UTC")

    private fun obs(store: String, sku: String, inStock: Boolean) = StockObservation(store, "Store $store", sku, inStock)

    @Test
    fun `first sighting in stock is recorded with unknown start`() {
        val result = ChangeDetector.detect(emptyMap(), listOf(obs("R1", "A", true), obs("R1", "B", false)), 1_000)

        assertEquals(1, result.events.size)
        assertEquals(StockEventKind.FIRST_SEEN, result.events[0].kind)
        assertTrue(result.events[0].afterGap)
        assertEquals(2, result.states.size)
    }

    @Test
    fun `restock and sell out are detected with duration`() {
        val start = 10 * minute
        val unavailable = mapOf(("R1" to "A") to LastStateEntity("R1", "A", false, since = 0, lastSeen = start))
        val restock = ChangeDetector.detect(unavailable, listOf(obs("R1", "A", true)), start + minute)
        assertEquals(StockEventKind.RESTOCK, restock.events.single().kind)
        assertFalse(restock.events.single().afterGap)

        val state = restock.states.associateBy { it.storeNumber to it.sku }
        val soldOut = ChangeDetector.detect(state, listOf(obs("R1", "A", false)), start + 17 * minute)
        val event = soldOut.events.single()
        assertEquals(StockEventKind.SOLD_OUT, event.kind)
        assertEquals(16 * minute, event.durationMs)
    }

    @Test
    fun `change after a long gap is marked as not exact`() {
        val old = mapOf(("R1" to "A") to LastStateEntity("R1", "A", false, since = 0, lastSeen = 0))
        val result = ChangeDetector.detect(old, listOf(obs("R1", "A", true)), 30 * minute)
        assertTrue(result.events.single().afterGap)
    }

    @Test
    fun `no change only refreshes last seen`() {
        val old = mapOf(("R1" to "A") to LastStateEntity("R1", "A", false, since = 0, lastSeen = 0))
        val result = ChangeDetector.detect(old, listOf(obs("R1", "A", false)), minute)
        assertTrue(result.events.isEmpty())
        assertEquals(minute, result.states.single().lastSeen)
        assertEquals(0L, result.states.single().since)
    }

    @Test
    fun `gaps are found between background checks and at the end`() {
        val checks = listOf(0L, minute, 2 * minute, 30 * minute).map {
            CheckLogEntity(timestamp = it, postalCode = "V6C1T2", skuCount = 1, success = true, background = true)
        } + CheckLogEntity(timestamp = 10 * minute, postalCode = "V6C1T2", skuCount = 1, success = true, background = false)

        val gaps = CheckGaps.find(checks, from = 0, now = 45 * minute)

        assertEquals(listOf(CheckGap(2 * minute, 30 * minute), CheckGap(30 * minute, 45 * minute)), gaps)
    }

    @Test
    fun `a gap counts the background checks that failed in it`() {
        val ok = listOf(0L, 30 * minute).map {
            CheckLogEntity(timestamp = it, postalCode = "V6C1T2", skuCount = 1, success = true, background = true)
        }
        val failed = listOf(1 * minute, 3 * minute, 8 * minute).map {
            CheckLogEntity(timestamp = it, postalCode = "V6C1T2", skuCount = 1, success = false, background = true)
        }

        val gaps = CheckGaps.find(ok + failed, from = 0, now = 31 * minute)

        assertEquals(listOf(CheckGap(0L, 30 * minute, failedChecks = 3)), gaps)
    }

    @Test
    fun `patterns count one restock per store and check, and skip first sightings`() {
        val nineAm = Calendar.getInstance(utc).apply {
            set(2026, Calendar.OCTOBER, 26, 9, 5, 0) // a Monday
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        fun event(time: Long, store: String, sku: String, kind: String, duration: Long? = null, afterGap: Boolean = false) =
            StockEventEntity(timestamp = time, storeNumber = store, storeName = "Store $store", sku = sku, kind = kind, durationMs = duration, afterGap = afterGap)

        val events = listOf(
            event(nineAm, "R1", "A", StockEventKind.RESTOCK),
            event(nineAm, "R1", "B", StockEventKind.RESTOCK),
            event(nineAm + 3_600_000, "R2", "A", StockEventKind.RESTOCK),
            event(nineAm + 86_400_000, "R1", "A", StockEventKind.RESTOCK),
            event(nineAm, "R3", "A", StockEventKind.FIRST_SEEN),
            event(nineAm + 10 * minute, "R1", "A", StockEventKind.SOLD_OUT, duration = 10 * minute),
            event(nineAm + 30 * minute, "R2", "A", StockEventKind.SOLD_OUT, duration = 20 * minute),
            event(nineAm + 40 * minute, "R1", "B", StockEventKind.SOLD_OUT, duration = 99 * minute, afterGap = true)
        )
        val checks = (0 until 48).map {
            CheckLogEntity(timestamp = nineAm + it * 3_600_000L, postalCode = "V6C1T2", skuCount = 2, success = true, background = true)
        }

        val summary = PatternsCalculator.compute(events, checks, skus = null, storeNumber = null, rangeDays = 30, now = nineAm + 2 * 86_400_000L, timeZone = utc)

        assertEquals(3, summary.restockCount)
        assertEquals(2, summary.restocksByHour[9])
        assertEquals(1, summary.restocksByHour[10])
        assertEquals(9, summary.peakHour)
        assertEquals(1, summary.restocksByWeekdayHour[0][9]) // Monday
        assertEquals(1, summary.restocksByWeekdayHour[1][9]) // Tuesday
        assertEquals(15, summary.medianSellOutMinutes) // 10 and 20; the after-gap sample is ignored
        assertEquals(2, summary.sellOutSamples)
        assertEquals("R1", summary.bestStores.first().storeNumber)
        assertEquals(2, summary.bestStores.first().restocks)
        assertTrue(summary.hasEnoughData)
        assertTrue(summary.coverage > 0.9)
    }

    @Test
    fun `patterns filter by sku and store`() {
        val events = listOf(
            StockEventEntity(timestamp = 1_000, storeNumber = "R1", storeName = "One", sku = "A", kind = StockEventKind.RESTOCK),
            StockEventEntity(timestamp = 2_000, storeNumber = "R2", storeName = "Two", sku = "B", kind = StockEventKind.RESTOCK)
        )
        val bySku = PatternsCalculator.compute(events, emptyList(), setOf("B"), null, 30, 10_000)
        val byStore = PatternsCalculator.compute(events, emptyList(), null, "R1", 30, 10_000)

        assertEquals(1, bySku.restockCount)
        assertEquals("R2", bySku.bestStores.single().storeNumber)
        assertEquals("R1", byStore.bestStores.single().storeNumber)
        assertFalse(bySku.hasEnoughData)
        assertNull(bySku.medianSellOutMinutes)
        assertEquals(0.0, bySku.coverage, 0.0)
    }

    @Test
    fun `csv escapes commas and quotes`() {
        val csv = HistoryCsv.build(
            listOf(StockEventEntity(timestamp = 0, storeNumber = "R1", storeName = "Store, \"Main\"", sku = "MK244VC/A", kind = StockEventKind.RESTOCK))
        )
        assertTrue(csv.contains("\"Store, \"\"Main\"\"\""))
        assertTrue(csv.startsWith("time,store_number"))
    }
}
