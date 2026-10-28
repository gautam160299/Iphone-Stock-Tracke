package com.example.applestockpulse

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrackerPreferencesTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("apple_stock_pulse_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `alarms are on by default`() {
        val prefs = TrackerPreferences(context)
        assertTrue(prefs.isSkuArmed("MK244VC/A"))
        assertTrue(prefs.isSkuArmed("MJR64VC/A"))
    }

    @Test
    fun `muting one SKU leaves every other SKU armed`() {
        val prefs = TrackerPreferences(context)

        assertFalse(prefs.toggleSkuAlarm("MK244VC/A"))

        assertFalse(prefs.isSkuArmed("MK244VC/A"))
        assertTrue(prefs.isSkuArmed("MK254VC/A"))
        // Another model is not affected
        assertTrue(prefs.isSkuArmed("MJR64VC/A"))

        assertTrue(prefs.toggleSkuAlarm("mk244vc/a"))
        assertTrue(prefs.isSkuArmed("MK244VC/A"))
    }

    @Test
    fun `old inverted armed set is removed`() {
        context.getSharedPreferences("apple_stock_pulse_prefs", Context.MODE_PRIVATE).edit()
            .putStringSet("key_notified_skus", setOf("MK244VC/A"))
            .commit()

        val prefs = TrackerPreferences(context)

        assertTrue(prefs.isSkuArmed("MJR64VC/A"))
    }

    @Test
    fun `tracking flag round trips`() {
        val prefs = TrackerPreferences(context)
        assertFalse(prefs.isTrackingEnabled())
        prefs.setTrackingEnabled(true)
        assertTrue(TrackerPreferences(context).isTrackingEnabled())
    }

    @Test
    fun `postal code check`() {
        assertTrue(TrackerPreferences.isValidPostalCode("V6C 1T2"))
        assertTrue(TrackerPreferences.isValidPostalCode("m5v2t6"))
        assertFalse(TrackerPreferences.isValidPostalCode("12345"))
        assertFalse(TrackerPreferences.isValidPostalCode(""))
        // D, F, I, O, Q, U are never used
        assertFalse(TrackerPreferences.isValidPostalCode("D6C1T2"))
    }

    @Test
    fun `store filter is cleaned`() {
        assertEquals(setOf("R601", "R488"), TrackerPreferences.parseStoreFilter(" r601, R488 ,,"))
    }
}
