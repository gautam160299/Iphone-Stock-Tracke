package com.example.applestockpulse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotifyToggleTest {

    private val duo256NightSky = "MK254VC/A"

    @Test
    fun `tap on an unwatched phone makes a one-phone alert`() {
        val decision = NotifyToggle.decide(emptyList(), duo256NightSky)

        assertTrue(decision is NotifyDecision.Create)
        val rule = (decision as NotifyDecision.Create).rule
        assertEquals(listOf(duo256NightSky), rule.skus())
        assertTrue(rule.isSinglePhone)
        assertEquals("iPhone Duo 256GB Night Sky", rule.displayName())
    }

    @Test
    fun `tap on a phone with its own alert removes that alert`() {
        val single = WatchRule(selectors = listOf(DeviceSelector("iPhone Duo", setOf("256GB"), setOf("Night Sky"))))
        val group = WatchRule(selectors = listOf(DeviceSelector("iPhone Duo")))

        val decision = NotifyToggle.decide(listOf(single, group), duo256NightSky)

        assertEquals(NotifyDecision.Remove(listOf(single)), decision)
    }

    @Test
    fun `tap on a phone covered only by a group points to that group`() {
        val group = WatchRule(name = "Duo hunt", selectors = listOf(DeviceSelector("iPhone Duo", setOf("256GB", "512GB"))))

        assertEquals(NotifyDecision.Covered(group), NotifyToggle.decide(listOf(group), duo256NightSky))
    }

    @Test
    fun `unknown part number does nothing`() {
        assertNull(NotifyToggle.decide(emptyList(), "NOPE"))
    }

    @Test
    fun `cell state follows the alerts`() {
        val single = WatchRule(selectors = listOf(DeviceSelector("iPhone Duo", setOf("256GB"), setOf("Night Sky"))))
        val mutedSingle = single.copy(alarmOn = false)
        val group = WatchRule(selectors = listOf(DeviceSelector("iPhone Duo")))

        assertEquals(NotifyCellState.NOTIFY, NotifyCellState.of(emptyList(), duo256NightSky))
        assertEquals(NotifyCellState.ON, NotifyCellState.of(listOf(single, group), duo256NightSky))
        assertEquals(NotifyCellState.GROUP, NotifyCellState.of(listOf(group), duo256NightSky))
        assertEquals(NotifyCellState.MUTED, NotifyCellState.of(listOf(mutedSingle), duo256NightSky))
    }

    @Test
    fun `store line reads like the site`() {
        val now = System.currentTimeMillis()
        val hour = 3_600_000L
        val events = (0 until 4).map {
            StockEventEntity(timestamp = now - it * 24 * hour - hour, storeNumber = "R1", storeName = "Metrotown", sku = duo256NightSky, kind = StockEventKind.RESTOCK)
        }
        val summary = PatternsCalculator.compute(events, emptyList(), setOf(duo256NightSky), "R1", 30, now)

        val line = storeInsightLine(summary, now)

        assertTrue(line, line.startsWith("Last restock "))
        assertTrue(line, line.contains("usually around "))
        assertTrue(line, line.endsWith("4 times this week"))
        assertEquals("No restocks seen yet · history fills in while monitoring is on", storeInsightLine(null, now))
    }
}
