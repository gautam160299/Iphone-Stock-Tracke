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
        const val KEY_SELECTED_MODEL = "key_selected_model"
        const val KEY_SELECTED_STORAGE = "key_selected_storage"
        const val KEY_SELECTED_COLOR = "key_selected_color"
        const val KEY_POSTAL_CODE = "key_postal_code"
        const val KEY_STORE_FILTER = "key_store_filter"
        const val KEY_NOTIFIED_SKUS = "key_notified_skus"

        const val DEFAULT_MODEL = "iPhone Duo"
        const val DEFAULT_STORAGE = "All"
        const val DEFAULT_COLOR = "All"
        const val DEFAULT_POSTAL_CODE = "V6C1T2"
        const val DEFAULT_STORE_FILTER = ""
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
        prefs.edit().putString(KEY_POSTAL_CODE, postalCode.trim().uppercase()).apply()
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
     * Set of specific SKUs the user specifically requested instant alert notifications for.
     * If empty, all SKUs for the selected model/specs trigger alarms.
     */
    fun getArmedNotificationSkus(): Set<String> {
        return prefs.getStringSet(KEY_NOTIFIED_SKUS, emptySet()) ?: emptySet()
    }

    fun toggleArmedNotificationSku(sku: String) {
        val current = getArmedNotificationSkus().toMutableSet()
        val clean = sku.trim().uppercase()
        if (current.contains(clean)) {
            current.remove(clean)
        } else {
            current.add(clean)
        }
        prefs.edit().putStringSet(KEY_NOTIFIED_SKUS, current).apply()
    }

    fun isSkuArmed(sku: String): Boolean {
        val armed = getArmedNotificationSkus()
        return armed.isEmpty() || armed.contains(sku.trim().uppercase())
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
        return getPostalCode().replace("\\s+".toRegex(), "").uppercase()
    }

    fun getParsedStoreFilter(): Set<String> {
        val raw = getStoreFilter()
        if (raw.isBlank()) return emptySet()
        return raw.split(",")
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() }
            .toSet()
    }
}
