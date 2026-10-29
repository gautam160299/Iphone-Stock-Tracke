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
    fun `radius defaults to 50 km and only accepts offered values`() {
        val prefs = TrackerPreferences(context)
        assertEquals(50, prefs.getRadiusKm())
        prefs.setRadiusKm(250)
        assertEquals(250, prefs.getRadiusKm())
        prefs.setRadiusKm(7)
        assertEquals(50, prefs.getRadiusKm())
    }

    @Test
    fun `old single selection becomes the first rule`() {
        context.getSharedPreferences("apple_stock_pulse_prefs", Context.MODE_PRIVATE).edit()
            .putString("key_selected_model", "iPhone Air")
            .putString("key_selected_storage", "256GB")
            .putString("key_selected_color", "All")
            .commit()

        val selector = TrackerPreferences(context).takeLegacySelection()

        assertEquals(DeviceSelector("iPhone Air", setOf("256GB"), emptySet()), selector)
    }

    @Test
    fun `no old selection gives no rule`() {
        assertEquals(null, TrackerPreferences(context).takeLegacySelection())
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

    @Test
    fun `check interval defaults to 45 s and keeps only offered values`() {
        val prefs = TrackerPreferences(context)
        assertEquals(45, prefs.getCheckIntervalSeconds())

        prefs.setCheckIntervalSeconds(30)
        assertEquals(30, prefs.getCheckIntervalSeconds())

        prefs.setCheckIntervalSeconds(5)
        assertEquals(30, prefs.getCheckIntervalSeconds())
    }
}
