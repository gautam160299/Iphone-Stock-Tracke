package com.example.applestockpulse

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * One device group inside a rule. An empty set means "Any".
 * e.g. iPhone Duo + {256GB, 512GB} + {} = every iPhone Duo 256GB or 512GB in any colour.
 */
data class DeviceSelector(
    val model: String,
    val storages: Set<String> = emptySet(),
    val colors: Set<String> = emptySet()
) {
    fun skus(): List<String> = ProductCatalog.CATALOG.filter { item ->
        item.model.equals(model, ignoreCase = true) &&
            (storages.isEmpty() || storages.any { it.equals(item.storage, ignoreCase = true) }) &&
            (colors.isEmpty() || colors.any { it.equals(item.color, ignoreCase = true) })
    }.map { it.sku }

    fun storageLabel(): String = if (storages.isEmpty()) "Any storage" else
        storages.sortedBy { ProductCatalog.storageInGigabytes(it) }.joinToString(", ")

    fun colorLabel(): String = if (colors.isEmpty()) "Any colour" else colors.sorted().joinToString(", ")

    fun label(): String = "$model · ${storageLabel()} · ${colorLabel()}"
}

/**
 * Postal code plus search radius. Rules use the default location unless they set their own.
 */
data class SearchLocation(
    val postalCode: String,
    val radiusKm: Int
) {
    fun label(): String = "${formatPostalCode(postalCode)} · $radiusKm km"

    companion object {
        val RADIUS_OPTIONS_KM = listOf(10, 20, 30, 50, 100, 250)
        const val DEFAULT_RADIUS_KM = 50

        fun formatPostalCode(code: String): String {
            val clean = TrackerPreferences.cleanPostalCode(code)
            return if (clean.length == 6) "${clean.substring(0, 3)} ${clean.substring(3)}" else clean
        }
    }
}

data class WatchRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val selectors: List<DeviceSelector>,
    val alarmOn: Boolean = true,
    // null = use the default location from Settings
    val location: SearchLocation? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun skus(): List<String> = selectors.flatMap { it.skus() }.distinct()

    fun displayName(): String = name.ifBlank {
        selectors.map { it.model }.distinct().joinToString(" + ")
    }

    fun effectiveLocation(default: SearchLocation): SearchLocation = location ?: default
}

object WatchRuleJson {

    fun encode(rules: List<WatchRule>): String {
        val array = JSONArray()
        rules.forEach { rule ->
            array.put(JSONObject().apply {
                put("id", rule.id)
                put("name", rule.name)
                put("alarmOn", rule.alarmOn)
                put("createdAt", rule.createdAt)
                rule.location?.let {
                    put("location", JSONObject().put("postalCode", it.postalCode).put("radiusKm", it.radiusKm))
                }
                put("selectors", JSONArray().apply {
                    rule.selectors.forEach { selector ->
                        put(JSONObject().apply {
                            put("model", selector.model)
                            put("storages", JSONArray(selector.storages.toList()))
                            put("colors", JSONArray(selector.colors.toList()))
                        })
                    }
                })
            })
        }
        return array.toString()
    }

    fun decode(json: String): List<WatchRule> {
        val array = try { JSONArray(json) } catch (_: Exception) { return emptyList() }
        return (0 until array.length()).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            val selectorsArray = obj.optJSONArray("selectors") ?: JSONArray()
            val selectors = (0 until selectorsArray.length()).mapNotNull { i ->
                val s = selectorsArray.optJSONObject(i) ?: return@mapNotNull null
                DeviceSelector(
                    model = s.optString("model"),
                    storages = s.optJSONArray("storages").toStringSet(),
                    colors = s.optJSONArray("colors").toStringSet()
                )
            }.filter { it.model.isNotBlank() }
            if (selectors.isEmpty()) return@mapNotNull null
            val locationObj = obj.optJSONObject("location")
            WatchRule(
                id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                name = obj.optString("name"),
                selectors = selectors,
                alarmOn = obj.optBoolean("alarmOn", true),
                location = locationObj?.let {
                    SearchLocation(it.optString("postalCode"), it.optInt("radiusKm", SearchLocation.DEFAULT_RADIUS_KM))
                },
                createdAt = obj.optLong("createdAt", System.currentTimeMillis())
            )
        }
    }

    private fun JSONArray?.toStringSet(): Set<String> {
        if (this == null) return emptySet()
        return (0 until length()).map { optString(it) }.filter { it.isNotBlank() }.toSet()
    }
}

/**
 * Saved watch rules, persisted as JSON. One instance per process so the UI and the service
 * see the same list.
 */
class WatchRuleStore private constructor(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val _rules = MutableStateFlow(loadOrMigrate(TrackerPreferences(context)))
    val rules: StateFlow<List<WatchRule>> = _rules.asStateFlow()

    fun get(id: String): WatchRule? = _rules.value.find { it.id == id }

    @Synchronized
    fun upsert(rule: WatchRule) {
        val current = _rules.value
        val updated = if (current.any { it.id == rule.id }) {
            current.map { if (it.id == rule.id) rule else it }
        } else {
            current + rule
        }
        save(updated)
    }

    @Synchronized
    fun delete(id: String) {
        save(_rules.value.filterNot { it.id == id })
    }

    @Synchronized
    fun setAlarm(ids: Collection<String>, on: Boolean) {
        save(_rules.value.map { if (it.id in ids) it.copy(alarmOn = on) else it })
    }

    private fun save(rules: List<WatchRule>) {
        prefs.edit().putString(KEY_RULES, WatchRuleJson.encode(rules)).apply()
        _rules.value = rules
    }

    private fun loadOrMigrate(trackerPreferences: TrackerPreferences): List<WatchRule> {
        prefs.getString(KEY_RULES, null)?.let { return WatchRuleJson.decode(it) }

        // First run of this version: turn the old single selection into the first rule
        val legacy = trackerPreferences.takeLegacySelection()
        val rules = if (legacy != null) listOf(WatchRule(selectors = listOf(legacy))) else emptyList()
        prefs.edit().putString(KEY_RULES, WatchRuleJson.encode(rules)).apply()
        return rules
    }

    companion object {
        private const val PREFS_NAME = "apple_stock_pulse_rules"
        private const val KEY_RULES = "key_watch_rules"

        @Volatile
        private var instance: WatchRuleStore? = null

        fun get(context: Context): WatchRuleStore =
            instance ?: synchronized(this) {
                instance ?: WatchRuleStore(context.applicationContext).also { instance = it }
            }
    }
}
