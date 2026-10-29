package com.example.applestockpulse

import org.json.JSONArray
import org.json.JSONObject

data class VariantAvailability(
    val sku: String,
    val model: String,
    val storage: String,
    val color: String,
    val isAvailable: Boolean,
    val pickupDisplay: String,
    val pickupQuote: String,
    // False when Apple sent no entry for this part number at this store
    val isKnown: Boolean = true
)

data class StoreInventoryCard(
    val storeNumber: String,
    val storeName: String,
    val city: String,
    val distanceText: String,
    // Parsed from distanceText; null when Apple sent no distance
    val distanceKm: Double? = null,
    val addressText: String,
    val phoneText: String,
    val totalAvailableInStore: Int,
    val variants: List<VariantAvailability>
)

data class InventoryParseResult(
    val stores: List<StoreInventoryCard>,
    // body.errorMessage from Apple, e.g. for an invalid part number or postal code
    val apiErrorMessage: String?,
    // Requested part numbers that no returned store knows; usually a wrong SKU
    val unknownSkus: List<String>,
    // Stores Apple returned before the store filter was applied
    val storesBeforeFilter: Int
)

/**
 * Parses https://www.apple.com/ca/shop/retail/pickup-message responses.
 * Pure function with no Android state, so it can be unit tested.
 */
object AppleInventoryParser {

    private val DISTANCE_REGEX = Regex("""(\d+(?:[.,]\d+)?)\s*(km|mi)?""", RegexOption.IGNORE_CASE)

    /**
     * "10.7 km" -> 10.7, "3 mi" -> 4.83, "" -> null.
     */
    fun parseDistanceKm(raw: String): Double? {
        val match = DISTANCE_REGEX.find(raw) ?: return null
        val value = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        return if (match.groupValues[2].equals("mi", ignoreCase = true)) value * 1.609344 else value
    }

    fun parse(
        jsonString: String,
        skus: List<String>,
        storeFilter: Set<String>,
        selectedModel: String
    ): InventoryParseResult {
        val root = JSONObject(jsonString)
        val body = root.optJSONObject("body")
            ?: return InventoryParseResult(emptyList(), null, emptyList(), 0)

        val apiErrorMessage = body.optString("errorMessage", "").takeIf { it.isNotBlank() }

        // Support both /shop/retail/pickup-message (body.stores) and legacy (body.content.pickupMessage.stores)
        val storesArray: JSONArray = body.optJSONArray("stores")
            ?: body.optJSONObject("content")?.optJSONObject("pickupMessage")?.optJSONArray("stores")
            ?: body.optJSONObject("pickupMessage")?.optJSONArray("stores")
            ?: JSONArray()

        val cards = mutableListOf<StoreInventoryCard>()
        val skusSeenAnywhere = mutableSetOf<String>()
        val seenStoreNumbers = mutableSetOf<String>()

        for (i in 0 until storesArray.length()) {
            val storeObj = storesArray.optJSONObject(i) ?: continue
            val partsAvailability = storeObj.optJSONObject("partsAvailability") ?: JSONObject()
            skus.filter { partsAvailability.has(it) }.forEach { skusSeenAnywhere.add(it) }

            val storeNumber = storeObj.optString("storeNumber", "").trim()
            // Store number is the list key in the UI; skip blanks and duplicates
            if (storeNumber.isEmpty() || !seenStoreNumbers.add(storeNumber.uppercase())) continue
            if (storeFilter.isNotEmpty() && !storeFilter.contains(storeNumber.uppercase())) continue

            val storeName = storeObj.optString("storeName", "Apple Store")
            val city = storeObj.optString("city", "")

            val distanceRaw = storeObj.optString("storeDistanceWithUnit", "")
            val distanceText = if (distanceRaw.isNotBlank()) "$distanceRaw away" else ""

            val addressObj = storeObj.optJSONObject("address")
            val addrLine = addressObj?.optString("address2", "") ?: ""
            val postCode = addressObj?.optString("postalCode", "") ?: ""
            val addressText = listOf(addrLine, city, postCode).filter { it.isNotBlank() }.joinToString(", ")
            val phoneText = storeObj.optString("phoneNumber", "")

            val variants = skus.map { sku ->
                val skuObj = partsAvailability.optJSONObject(sku)
                val pickupDisplay = skuObj?.optString("pickupDisplay", "unavailable") ?: "unavailable"
                val product = ProductCatalog.getProduct(sku)
                VariantAvailability(
                    sku = sku,
                    model = product?.model ?: selectedModel,
                    storage = product?.storage ?: "",
                    color = product?.color ?: "",
                    isAvailable = pickupDisplay.equals("available", ignoreCase = true),
                    pickupDisplay = pickupDisplay,
                    pickupQuote = skuObj?.optString("pickupSearchQuote", "")?.takeIf { it.isNotBlank() }
                        ?: if (skuObj == null) "Unknown to Apple" else "None available",
                    isKnown = skuObj != null
                )
            }

            cards.add(
                StoreInventoryCard(
                    storeNumber = storeNumber,
                    storeName = storeName,
                    city = city,
                    distanceText = distanceText,
                    distanceKm = parseDistanceKm(distanceRaw),
                    addressText = addressText,
                    phoneText = phoneText,
                    totalAvailableInStore = variants.count { it.isAvailable },
                    variants = variants
                )
            )
        }

        // Only judge SKUs when Apple returned stores; with no stores every SKU looks unknown
        val unknownSkus = if (storesArray.length() > 0) skus.filterNot { it in skusSeenAnywhere } else emptyList()

        return InventoryParseResult(
            stores = cards,
            apiErrorMessage = apiErrorMessage,
            unknownSkus = unknownSkus,
            storesBeforeFilter = storesArray.length()
        )
    }
}
