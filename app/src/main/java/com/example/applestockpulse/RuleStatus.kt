package com.example.applestockpulse

/**
 * Result of the latest check for one postal code.
 */
data class LocationSnapshot(
    val postalCode: String,
    val checkedAt: Long? = null,
    // Every store Apple returned (store filter applied), with distance
    val stores: List<StoreInventoryCard> = emptyList(),
    val httpStatusCode: Int? = null,
    val error: String? = null,
    val unknownSkus: List<String> = emptyList()
) {
    val farthestKm: Double? get() = stores.mapNotNull { it.distanceKm }.maxOrNull()

    fun storesWithin(radiusKm: Int): List<StoreInventoryCard> =
        stores.filter { isWithinRadius(it, radiusKm) }

    companion object {
        // A store without a distance is kept: Apple chose to return it as nearby
        fun isWithinRadius(store: StoreInventoryCard, radiusKm: Int): Boolean =
            store.distanceKm == null || store.distanceKm <= radiusKm
    }
}

data class RuleStoreHit(
    val store: StoreInventoryCard,
    val available: List<VariantAvailability>
)

data class RuleStatus(
    val location: SearchLocation,
    val checkedAt: Long?,
    val storesInRange: Int,
    val hits: List<RuleStoreHit>,
    val error: String?
) {
    val inStock: Boolean get() = hits.isNotEmpty()
    val availableCount: Int get() = hits.sumOf { it.available.size }
}

object RuleStatusCalculator {

    fun statusFor(
        rule: WatchRule,
        defaultLocation: SearchLocation,
        snapshots: Map<String, LocationSnapshot>
    ): RuleStatus {
        val location = rule.effectiveLocation(defaultLocation)
        val snapshot = snapshots[location.postalCode]
            ?: return RuleStatus(location, null, 0, emptyList(), null)
        val skus = rule.skus().toSet()
        val inRange = snapshot.storesWithin(location.radiusKm)
        val hits = inRange.mapNotNull { store ->
            val available = store.variants.filter { it.isAvailable && it.sku in skus }
            if (available.isEmpty()) null else RuleStoreHit(store, available)
        }.sortedBy { it.store.distanceKm ?: Double.MAX_VALUE }
        val unknown = snapshot.unknownSkus.filter { it in skus }
        val error = listOfNotNull(
            snapshot.error,
            unknown.takeIf { it.isNotEmpty() }?.let { "Apple does not recognize: ${it.joinToString()}" }
        ).joinToString("\n").ifEmpty { null }
        return RuleStatus(location, snapshot.checkedAt, inRange.size, hits, error)
    }
}
