package com.example.applestockpulse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppleInventoryParserTest {

    private val skus = listOf("MK244VC/A", "MK254VC/A", "MK264VC/A")

    private val response = """
        {"body": {"stores": [
          {"storeNumber": "R601", "storeName": "Pacific Centre", "city": "Vancouver",
           "storeDistanceWithUnit": "0.4 km", "phoneNumber": "778-373-1800",
           "address": {"address2": "701 West Georgia Street", "postalCode": "V7Y 1G5"},
           "partsAvailability": {
             "MK244VC/A": {"pickupDisplay": "available", "pickupSearchQuote": "Available Today"},
             "MK254VC/A": {"pickupDisplay": "unavailable", "pickupSearchQuote": "Currently unavailable"}
           }},
          {"storeNumber": "R488", "storeName": "Metrotown", "city": "Burnaby",
           "partsAvailability": {
             "MK254VC/A": {"pickupDisplay": "ineligible"}
           }},
          {"storeNumber": "R601", "storeName": "Duplicate", "city": "Vancouver", "partsAvailability": {}},
          {"storeNumber": "", "storeName": "No number", "partsAvailability": {}}
        ]}}
    """.trimIndent()

    @Test
    fun `parses stores, availability and address`() {
        val result = AppleInventoryParser.parse(response, skus, emptySet(), "iPhone Duo")

        assertEquals(listOf("R601", "R488"), result.stores.map { it.storeNumber })
        val pacific = result.stores.first()
        assertEquals(1, pacific.totalAvailableInStore)
        assertEquals("0.4 km away", pacific.distanceText)
        assertEquals("701 West Georgia Street, Vancouver, V7Y 1G5", pacific.addressText)
        assertTrue(pacific.variants.first { it.sku == "MK244VC/A" }.isAvailable)
        assertFalse(pacific.variants.first { it.sku == "MK254VC/A" }.isAvailable)
        assertNull(result.apiErrorMessage)
    }

    @Test
    fun `reports part numbers no store knows`() {
        val result = AppleInventoryParser.parse(response, skus, emptySet(), "iPhone Duo")

        assertEquals(listOf("MK264VC/A"), result.unknownSkus)
        val unknown = result.stores.first().variants.first { it.sku == "MK264VC/A" }
        assertFalse(unknown.isKnown)
        assertFalse(unknown.isAvailable)
    }

    @Test
    fun `keeps Apple error message`() {
        val json = """{"body": {"errorMessage": "Invalid postal code", "stores": []}}"""
        val result = AppleInventoryParser.parse(json, skus, emptySet(), "iPhone Duo")

        assertEquals("Invalid postal code", result.apiErrorMessage)
        assertTrue(result.stores.isEmpty())
        // Without stores, nothing can be said about the SKUs
        assertTrue(result.unknownSkus.isEmpty())
    }

    @Test
    fun `applies store filter after counting returned stores`() {
        val result = AppleInventoryParser.parse(response, skus, setOf("R488"), "iPhone Duo")

        assertEquals(listOf("R488"), result.stores.map { it.storeNumber })
        assertEquals(4, result.storesBeforeFilter)
    }

    @Test
    fun `missing body gives empty result`() {
        val result = AppleInventoryParser.parse("{}", skus, emptySet(), "iPhone Duo")

        assertTrue(result.stores.isEmpty())
        assertTrue(result.unknownSkus.isEmpty())
    }
}
