package com.example.applestockpulse

import android.content.Context
import android.content.SharedPreferences

class TrackerPreferences(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    init {
        // Drop keys from earlier versions: free-text SKUs, the inverted "armed" set and per-SKU mutes
        if (prefs.contains(LEGACY_KEY_SKUS) || prefs.contains(LEGACY_KEY_NOTIFIED_SKUS) || prefs.contains(LEGACY_KEY_MUTED_SKUS)) {
            prefs.edit()
                .remove(LEGACY_KEY_SKUS)
                .remove(LEGACY_KEY_NOTIFIED_SKUS)
                .remove(LEGACY_KEY_MUTED_SKUS)
                .apply()
        }
    }

    companion object {
        private const val PREFS_NAME = "apple_stock_pulse_prefs"
        const val KEY_BROWSE_MODEL = "key_selected_model"
        const val KEY_POSTAL_CODE = "key_postal_code"
        const val KEY_RADIUS_KM = "key_radius_km"
        const val KEY_STORE_FILTER = "key_store_filter"
        const val KEY_TRACKING_ENABLED = "key_tracking_enabled"
        const val KEY_LAST_SEEN_EVENT_ID = "key_last_seen_event_id"
        const val KEY_NTFY_TOPIC = "key_ntfy_topic"
        private const val LEGACY_KEY_SELECTED_STORAGE = "key_selected_storage"
        private const val LEGACY_KEY_SELECTED_COLOR = "key_selected_color"
        private const val LEGACY_KEY_SKUS = "key_skus"
        private const val LEGACY_KEY_NOTIFIED_SKUS = "key_notified_skus"
        private const val LEGACY_KEY_MUTED_SKUS = "key_muted_skus"
        private const val LEGACY_ALL = "All"

        const val DEFAULT_MODEL = "iPhone Duo"
        const val DEFAULT_POSTAL_CODE = "V6C1T2"
        const val DEFAULT_STORE_FILTER = ""
        const val HISTORY_RETENTION_DAYS = 30

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

    /**
     * Model shown on the Browse screen.
     */
    fun getBrowseModel(): String {
        val saved = prefs.getString(KEY_BROWSE_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
        return ProductCatalog.getDeviceModel(saved).name
    }

    fun setBrowseModel(model: String) {
        prefs.edit().putString(KEY_BROWSE_MODEL, model).apply()
    }

    fun getPostalCode(): String {
        return prefs.getString(KEY_POSTAL_CODE, DEFAULT_POSTAL_CODE)?.takeIf { it.isNotBlank() }
            ?: DEFAULT_POSTAL_CODE
    }

    fun setPostalCode(postalCode: String) {
        prefs.edit().putString(KEY_POSTAL_CODE, cleanPostalCode(postalCode)).apply()
    }

    fun getRadiusKm(): Int {
        val saved = prefs.getInt(KEY_RADIUS_KM, SearchLocation.DEFAULT_RADIUS_KM)
        return if (saved in SearchLocation.RADIUS_OPTIONS_KM) saved else SearchLocation.DEFAULT_RADIUS_KM
    }

    fun setRadiusKm(radiusKm: Int) {
        prefs.edit().putInt(KEY_RADIUS_KM, radiusKm).apply()
    }

    fun getDefaultLocation(): SearchLocation = SearchLocation(getCleanPostalCode(), getRadiusKm())

    fun getStoreFilter(): String {
        return prefs.getString(KEY_STORE_FILTER, DEFAULT_STORE_FILTER) ?: DEFAULT_STORE_FILTER
    }

    fun setStoreFilter(storeFilter: String) {
        prefs.edit().putString(KEY_STORE_FILTER, storeFilter.trim().uppercase()).apply()
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
     * Newest stock event the user has seen on the Recent changes screen; newer ones are unread.
     */
    fun getLastSeenEventId(): Long = prefs.getLong(KEY_LAST_SEEN_EVENT_ID, 0L)

    fun setLastSeenEventId(id: Long) {
        prefs.edit().putLong(KEY_LAST_SEEN_EVENT_ID, id).apply()
    }

    /**
     * ntfy topic name or topic URL for alerts on other devices. Blank means off.
     */
    fun getNtfyTopic(): String {
        return prefs.getString(KEY_NTFY_TOPIC, "") ?: ""
    }

    fun setNtfyTopic(topic: String) {
        prefs.edit().putString(KEY_NTFY_TOPIC, topic.trim()).apply()
    }

    fun getNtfyTarget(): NtfyTarget? {
        return NtfyNotifier.parseTarget(getNtfyTopic())
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

    /**
     * The single model/storage/colour selection of the previous version, as a selector.
     * Returns null when there was none. Removes the old storage/colour keys.
     */
    fun takeLegacySelection(): DeviceSelector? {
        if (!prefs.contains(KEY_BROWSE_MODEL)) return null
        val model = getBrowseModel()
        val storage = prefs.getString(LEGACY_KEY_SELECTED_STORAGE, LEGACY_ALL) ?: LEGACY_ALL
        val color = prefs.getString(LEGACY_KEY_SELECTED_COLOR, LEGACY_ALL) ?: LEGACY_ALL
        prefs.edit().remove(LEGACY_KEY_SELECTED_STORAGE).remove(LEGACY_KEY_SELECTED_COLOR).apply()
        val selector = DeviceSelector(
            model = model,
            storages = if (storage == LEGACY_ALL) emptySet() else setOf(storage),
            colors = if (color == LEGACY_ALL) emptySet() else setOf(color)
        )
        return selector.takeIf { it.skus().isNotEmpty() } ?: DeviceSelector(model)
    }
}
