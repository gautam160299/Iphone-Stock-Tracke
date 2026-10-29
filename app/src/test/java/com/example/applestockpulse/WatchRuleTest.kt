package com.example.applestockpulse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WatchRuleTest {

    @Test
    fun `any colour selector expands to every colour of the chosen storages`() {
        val selector = DeviceSelector("iPhone Duo", storages = setOf("256GB", "512GB"))
        assertEquals(setOf("MK244VC/A", "MK254VC/A", "MK264VC/A", "MK274VC/A"), selector.skus().toSet())
    }

    @Test
    fun `exact selector gives one variant`() {
        assertEquals(listOf("MK254VC/A"), DeviceSelector("iPhone Duo", setOf("256GB"), setOf("Night Sky")).skus())
    }

    @Test
    fun `rule joins several models without duplicates`() {
        val rule = WatchRule(
            selectors = listOf(
                DeviceSelector("iPhone Duo", setOf("256GB"), setOf("Night Sky")),
                DeviceSelector("iPhone Duo", setOf("256GB")),
                DeviceSelector("iPhone 18 Pro Max", setOf("1TB"), setOf("Glacier"))
            )
        )
        assertEquals(setOf("MK244VC/A", "MK254VC/A", "MJXH4VC/A"), rule.skus().toSet())
        assertEquals(3, rule.skus().size)
        assertEquals("iPhone Duo + iPhone 18 Pro Max", rule.displayName())
    }

    @Test
    fun `rules survive a JSON round trip`() {
        val rules = listOf(
            WatchRule(
                name = "Duo hunt",
                selectors = listOf(DeviceSelector("iPhone Duo", setOf("256GB", "512GB"))),
                alarmOn = false,
                location = SearchLocation("M5V2T6", 20)
            ),
            WatchRule(selectors = listOf(DeviceSelector("iPhone Air")))
        )
        assertEquals(rules, WatchRuleJson.decode(WatchRuleJson.encode(rules)))
    }

    @Test
    fun `broken JSON gives no rules`() {
        assertTrue(WatchRuleJson.decode("not json").isEmpty())
    }

    @Test
    fun `rule uses its own location before the default`() {
        val default = SearchLocation("V6C1T2", 50)
        val own = SearchLocation("M5V2T6", 10)
        assertEquals(default, WatchRule(selectors = listOf(DeviceSelector("iPhone Air"))).effectiveLocation(default))
        assertEquals(own, WatchRule(selectors = listOf(DeviceSelector("iPhone Air")), location = own).effectiveLocation(default))
    }

    @Test
    fun `distance text is parsed in kilometres`() {
        assertEquals(10.7, AppleInventoryParser.parseDistanceKm("10.7 km")!!, 0.001)
        assertEquals(4.828, AppleInventoryParser.parseDistanceKm("3 mi")!!, 0.001)
        assertEquals(null, AppleInventoryParser.parseDistanceKm(""))
    }
}
