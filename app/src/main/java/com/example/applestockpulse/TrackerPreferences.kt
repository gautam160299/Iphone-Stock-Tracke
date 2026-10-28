package com.example.applestockpulse

import android.content.Context
import android.content.SharedPreferences

class TrackerPreferences(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    init {
        // Drop keys from earlier versions: free-text SKUs and the inverted "armed" set
        if (prefs.contains(LEGACY_KEY_SKUS) || prefs.contains(LEGACY_KEY_NOTIFIED_SKUS)) {
            prefs.edit().remove(LEGACY_KEY_SKUS).remove(LEGACY_KEY_NOTIFIED_SKUS).apply()
        }
    }

    companion object {
        private const val PREFS_NAME = "apple_stock_pulse_prefs"
        const val KEY_SELECTED_MODEL = "key_selected_model"
        const val KEY_SELECTED_STORAGE = "key_selected_storage"
        const val KEY_SELECTED_COLOR = "key_selected_color"
        const val KEY_POSTAL_CODE = "key_postal_code"
        const val KEY_STORE_FILTER = "key_store_filter"
        const val KEY_MUTED_SKUS = "key_muted_skus"
        const val KEY_TRACKING_ENABLED = "key_tracking_enabled"
        private const val LEGACY_KEY_SKUS = "key_skus"
        private const val LEGACY_KEY_NOTIFIED_SKUS = "key_notified_skus"

        const val DEFAULT_MODEL = "iPhone Duo"
        const val DEFAULT_STORAGE = "All"
        const val DEFAULT_COLOR = "All"
        const val DEFAULT_POSTAL_CODE = "V6C1T2"
        const val DEFAULT_STORE_FILTER = ""

        private val POSTAL_CODE_REGEX = Regex("^[ABCEGHJ-NPRSTVXY][0-9][ABCEGHJ-NPRSTV-Z][0-9][ABCEGHJ-NPRSTV-Z][0-9]$")

        /**
         * Canadian postal code check, e.g. "V6C 1T2" and "v6c1t2" are valid, "12345" is not.
         */
        fun isValidPostalCode(postalCode: String): Boolean {
            return POSTAL_CODE_REGEX.matches(cleanPostalCode(postalCode))
        }

        fun cleanPostalCode(postalCode: String): String {
            return postalCode.replace("\\s+".toRegex(), "").uppercase()
        }

        /**
         * Store numbers such as "R601, r488" -> {"R601", "R488"}.
         */
        fun parseStoreFilter(raw: String): Set<String> {
            return raw.split(",")
                .map { it.trim().uppercase() }
                .filter { it.isNotEmpty() }
                .toSet()
        }
    }

    fun getSelectedModel(): String {
        return prefs.getString(KEY_SELECTED_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
    }

    fun setSelectedModel(model: String) {
        prefs.edit().putString(KEY_SELECTED_MODEL, model).apply()
    }

    fun getSelectedStorage(): String {
        return prefs.getString(KEY_SELECTED_STORAGE, DEFAULT_STORAGE) ?: DEFAULT_STORAGE
    }

    fun setSelectedStorage(storage: String) {
        prefs.edit().putString(KEY_SELECTED_STORAGE, storage).apply()
    }

    fun getSelectedColor(): String {
        return prefs.getString(KEY_SELECTED_COLOR, DEFAULT_COLOR) ?: DEFAULT_COLOR
    }

    fun setSelectedColor(color: String) {
        prefs.edit().putString(KEY_SELECTED_COLOR, color).apply()
    }

    fun getPostalCode(): String {
        return prefs.getString(KEY_POSTAL_CODE, DEFAULT_POSTAL_CODE)?.takeIf { it.isNotBlank() }
            ?: DEFAULT_POSTAL_CODE
    }

    fun setPostalCode(postalCode: String) {
        prefs.edit().putString(KEY_POSTAL_CODE, cleanPostalCode(postalCode)).apply()
    }

    fun getStoreFilter(): String {
        return prefs.getString(KEY_STORE_FILTER, DEFAULT_STORE_FILTER) ?: DEFAULT_STORE_FILTER
    }

    fun setStoreFilter(storeFilter: String) {
        prefs.edit().putString(KEY_STORE_FILTER, storeFilter.trim().uppercase()).apply()
    }

    fun saveSelection(model: String, storage: String, color: String, postalCode: String) {
        prefs.edit()
            .putString(KEY_SELECTED_MODEL, model)
            .putString(KEY_SELECTED_STORAGE, storage)
            .putString(KEY_SELECTED_COLOR, color)
            .putString(KEY_POSTAL_CODE, postalCode.trim().uppercase())
            .apply()
    }

    /**
     * Alarms are on for every tracked SKU by default. This set holds the SKUs the user turned off.
     */
    fun getMutedSkus(): Set<String> {
        return prefs.getStringSet(KEY_MUTED_SKUS, emptySet())?.toSet() ?: emptySet()
    }

    /**
     * Turns the alarm for one SKU on or off. Returns true when the alarm is now on.
     */
    fun toggleSkuAlarm(sku: String): Boolean {
        val muted = getMutedSkus().toMutableSet()
        val clean = sku.trim().uppercase()
        val nowArmed = if (muted.contains(clean)) {
            muted.remove(clean)
            true
        } else {
            muted.add(clean)
            false
        }
        prefs.edit().putStringSet(KEY_MUTED_SKUS, muted).apply()
        return nowArmed
    }

    fun isSkuArmed(sku: String): Boolean {
        return !getMutedSkus().contains(sku.trim().uppercase())
    }

    /**
     * True while the user wants background monitoring. Lets a system restart of the service
     * resume monitoring, and keeps a restart from starting monitoring the user never asked for.
     */
    fun isTrackingEnabled(): Boolean {
        return prefs.getBoolean(KEY_TRACKING_ENABLED, false)
    }

    fun setTrackingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TRACKING_ENABLED, enabled).apply()
    }

    /**
     * Resolves the list of SKUs that the background service should query.
     */
    fun getActiveQuerySkus(): List<String> {
        val model = getSelectedModel()
        val storage = getSelectedStorage()
        val color = getSelectedColor()
        return ProductCatalog.getSkusForSelection(model, storage, color)
    }

    /**
     * Formats Canadian postal code without whitespace, e.g. "V6C 1T2" -> "V6C1T2".
     */
    fun getCleanPostalCode(): String {
        return cleanPostalCode(getPostalCode())
    }

    fun getParsedStoreFilter(): Set<String> {
        return parseStoreFilter(getStoreFilter())
    }
}
