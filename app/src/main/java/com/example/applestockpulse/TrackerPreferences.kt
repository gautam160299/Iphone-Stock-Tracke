package com.example.applestockpulse

import android.content.Context
import android.content.SharedPreferences

class TrackerPreferences(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    companion object {
        private const val PREFS_NAME = "apple_stock_pulse_prefs"
        const val KEY_SKUS = "key_skus"
        const val KEY_POSTAL_CODE = "key_postal_code"
        const val KEY_STORE_FILTER = "key_store_filter"

        const val DEFAULT_SKUS = "MK244VC/A, MJR64VC/A"
        const val DEFAULT_POSTAL_CODE = "V3T2W1"
        const val DEFAULT_STORE_FILTER = ""
    }

    fun getSkus(): String {
        return prefs.getString(KEY_SKUS, DEFAULT_SKUS)?.takeIf { it.isNotBlank() } ?: DEFAULT_SKUS
    }

    fun setSkus(skus: String) {
        prefs.edit().putString(KEY_SKUS, skus.trim()).apply()
    }

    fun getPostalCode(): String {
        return prefs.getString(KEY_POSTAL_CODE, DEFAULT_POSTAL_CODE)?.takeIf { it.isNotBlank() }
            ?: DEFAULT_POSTAL_CODE
    }

    fun setPostalCode(postalCode: String) {
        prefs.edit().putString(KEY_POSTAL_CODE, postalCode.trim().uppercase()).apply()
    }

    fun getStoreFilter(): String {
        return prefs.getString(KEY_STORE_FILTER, DEFAULT_STORE_FILTER) ?: DEFAULT_STORE_FILTER
    }

    fun setStoreFilter(storeFilter: String) {
        prefs.edit().putString(KEY_STORE_FILTER, storeFilter.trim().uppercase()).apply()
    }

    fun saveConfiguration(skus: String, postalCode: String, storeFilter: String) {
        prefs.edit()
            .putString(KEY_SKUS, skus.trim())
            .putString(KEY_POSTAL_CODE, postalCode.trim().uppercase())
            .putString(KEY_STORE_FILTER, storeFilter.trim().uppercase())
            .apply()
    }

    /**
     * Returns individual SKU strings cleaned, trimmed, and validated.
     */
    fun getParsedSkus(): List<String> {
        val raw = getSkus()
        val parsed = raw.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return if (parsed.isEmpty()) listOf(DEFAULT_SKUS) else parsed
    }

    /**
     * Returns Canadian postal code without spaces, e.g. "V3T 2W1" -> "V3T2W1".
     */
    fun getCleanPostalCode(): String {
        return getPostalCode().replace("\\s+".toRegex(), "").uppercase()
    }

    /**
     * Returns target store IDs (e.g. Set of "R280", "R486") or empty set if no filter.
     */
    fun getParsedStoreFilter(): Set<String> {
        val raw = getStoreFilter()
        if (raw.isBlank()) return emptySet()
        return raw.split(",")
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() }
            .toSet()
    }
}
