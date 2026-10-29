package com.example.applestockpulse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleStatusTest {

    private fun store(number: String, km: Double?, vararg available: String, unavailable: List<String> = emptyList()) = StoreInventoryCard(
        storeNumber = number,
        storeName = "Store $number",
        city = "",
        distanceText = km?.let { "$it km" } ?: "",
        distanceKm = km,
        addressText = "",
        phoneText = "",
        totalAvailableInStore = available.size,
        variants = available.map { VariantAvailability(it, "", "", "", true, "available", "Today") } +
            unavailable.map { VariantAvailability(it, "", "", "", false, "unavailable", "None") }
    )

    private val default = SearchLocation("V6C1T2", 20)

    @Test
    fun `only stores inside the radius count`() {
        val rule = WatchRule(selectors = listOf(DeviceSelector("iPhone Duo", setOf("256GB"))))
        val snapshot = LocationSnapshot(
            "V6C1T2",
            checkedAt = 1,
            stores = listOf(store("R1", 5.0, "MK244VC/A"), store("R2", 30.0, "MK254VC/A"), store("R3", null, unavailable = listOf("MK244VC/A")))
        )

        val status = RuleStatusCalculator.statusFor(rule, default, mapOf("V6C1T2" to snapshot))

        assertTrue(status.inStock)
        assertEquals(listOf("R1"), status.hits.map { it.store.storeNumber })
        assertEquals(2, status.storesInRange) // R1 and R3 (no distance)
    }

    @Test
    fun `stock of other devices does not count`() {
        val rule = WatchRule(selectors = listOf(DeviceSelector("iPhone Air")))
        val snapshot = LocationSnapshot("V6C1T2", checkedAt = 1, stores = listOf(store("R1", 1.0, "MK244VC/A")))

        assertFalse(RuleStatusCalculator.statusFor(rule, default, mapOf("V6C1T2" to snapshot)).inStock)
    }

    @Test
    fun `rule with its own location reads that location`() {
        val rule = WatchRule(selectors = listOf(DeviceSelector("iPhone Duo")), location = SearchLocation("M5V2T6", 250))
        val status = RuleStatusCalculator.statusFor(rule, default, mapOf("V6C1T2" to LocationSnapshot("V6C1T2", checkedAt = 1)))

        assertNull(status.checkedAt)
        assertEquals(250, status.location.radiusKm)
    }

    @Test
    fun `unknown part numbers of the rule are reported`() {
        val rule = WatchRule(selectors = listOf(DeviceSelector("iPhone Duo", setOf("256GB"), setOf("Star White"))))
        val snapshot = LocationSnapshot("V6C1T2", checkedAt = 1, unknownSkus = listOf("MK244VC/A", "MJR64VC/A"))

        assertEquals("Apple does not recognize: MK244VC/A", RuleStatusCalculator.statusFor(rule, default, mapOf("V6C1T2" to snapshot)).error)
    }
}
