package com.example.applestockpulse

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Backup file with the stock history, the alerts and the settings, so they survive an
 * uninstall or a move to a new phone. Restoring adds to what the phone already has.
 */
object AppBackup {

    private const val FORMAT = "applestockpulse-backup"
    const val FORMAT_VERSION = 1

    data class Settings(
        val postalCode: String,
        val radiusKm: Int,
        val storeFilter: String,
        val browseModel: String,
        val ntfyTopic: String,
        val checkIntervalSec: Int = TrackerPreferences.DEFAULT_CHECK_INTERVAL_SEC
    )

    data class Contents(
        val exportedAt: Long,
        val settings: Settings,
        val rules: List<WatchRule>,
        val events: List<StockEventEntity>,
        val checks: List<CheckLogEntity>,
        val states: List<LastStateEntity>
    )

    data class RestoreResult(val eventsAdded: Int, val eventsInFile: Int, val rules: Int)

    class InvalidBackupException(message: String) : Exception(message)

    fun suggestedFileName(now: Long = System.currentTimeMillis()): String =
        "applestockpulse-backup-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))}.json"

    /** Call off the main thread. Returns the number of events written. */
    suspend fun export(context: Context, uri: Uri): Int {
        val dao = HistoryDatabase.get(context).historyDao()
        val prefs = TrackerPreferences(context)
        val contents = Contents(
            exportedAt = System.currentTimeMillis(),
            settings = Settings(
                postalCode = prefs.getPostalCode(),
                radiusKm = prefs.getRadiusKm(),
                storeFilter = prefs.getStoreFilter(),
                browseModel = prefs.getBrowseModel(),
                ntfyTopic = prefs.getNtfyTopic(),
                checkIntervalSec = prefs.getCheckIntervalSeconds()
            ),
            rules = WatchRuleStore.get(context).rules.value,
            events = dao.allEvents(),
            checks = dao.allChecks(),
            states = dao.allStates()
        )
        val stream = context.contentResolver.openOutputStream(uri)
            ?: throw IllegalStateException("Cannot write the backup file")
        stream.use { it.write(encode(contents).toByteArray()) }
        return contents.events.size
    }

    /** Call off the main thread. Throws [InvalidBackupException] for a file that is not a backup. */
    suspend fun restore(context: Context, uri: Uri): RestoreResult {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: throw InvalidBackupException("Cannot read the file")
        val contents = decode(text)

        val prefs = TrackerPreferences(context)
        with(contents.settings) {
            if (TrackerPreferences.isValidPostalCode(postalCode)) prefs.setPostalCode(postalCode)
            prefs.setRadiusKm(radiusKm)
            prefs.setStoreFilter(storeFilter)
            if (browseModel.isNotBlank()) prefs.setBrowseModel(browseModel)
            if (ntfyTopic.isNotBlank()) prefs.setNtfyTopic(ntfyTopic)
            prefs.setCheckIntervalSeconds(checkIntervalSec)
        }

        val ruleStore = WatchRuleStore.get(context)
        contents.rules.forEach { ruleStore.upsert(it) }

        val dao = HistoryDatabase.get(context).historyDao()
        val added = dao.mergeBackup(contents.events, contents.checks, contents.states)
        // Restored changes are old news; do not mark them unread
        prefs.setLastSeenEventId(dao.maxEventId())

        return RestoreResult(eventsAdded = added, eventsInFile = contents.events.size, rules = contents.rules.size)
    }

    fun encode(contents: Contents): String {
        val root = JSONObject()
            .put("format", FORMAT)
            .put("version", FORMAT_VERSION)
            .put("exportedAt", contents.exportedAt)
            .put(
                "settings",
                JSONObject()
                    .put("postalCode", contents.settings.postalCode)
                    .put("radiusKm", contents.settings.radiusKm)
                    .put("storeFilter", contents.settings.storeFilter)
                    .put("browseModel", contents.settings.browseModel)
                    .put("ntfyTopic", contents.settings.ntfyTopic)
                    .put("checkIntervalSec", contents.settings.checkIntervalSec)
            )
            .put("rules", JSONArray(WatchRuleJson.encode(contents.rules)))
            .put("events", JSONArray().apply {
                contents.events.forEach { e ->
                    put(JSONObject().apply {
                        put("timestamp", e.timestamp)
                        put("storeNumber", e.storeNumber)
                        put("storeName", e.storeName)
                        put("sku", e.sku)
                        put("kind", e.kind)
                        e.durationMs?.let { put("durationMs", it) }
                        put("afterGap", e.afterGap)
                    })
                }
            })
            .put("checks", JSONArray().apply {
                contents.checks.forEach { c ->
                    put(JSONObject().apply {
                        put("timestamp", c.timestamp)
                        put("postalCode", c.postalCode)
                        put("skuCount", c.skuCount)
                        put("success", c.success)
                        put("background", c.background)
                    })
                }
            })
            .put("states", JSONArray().apply {
                contents.states.forEach { s ->
                    put(JSONObject().apply {
                        put("storeNumber", s.storeNumber)
                        put("sku", s.sku)
                        put("inStock", s.inStock)
                        put("since", s.since)
                        put("lastSeen", s.lastSeen)
                    })
                }
            })
        return root.toString()
    }

    fun decode(json: String): Contents {
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw InvalidBackupException("This file is not an AppleStockPulse backup")
        }
        if (root.optString("format") != FORMAT) throw InvalidBackupException("This file is not an AppleStockPulse backup")
        val version = root.optInt("version", 0)
        if (version > FORMAT_VERSION) throw InvalidBackupException("This backup is from a newer app version. Update the app first.")

        val settings = root.optJSONObject("settings") ?: JSONObject()
        return Contents(
            exportedAt = root.optLong("exportedAt"),
            settings = Settings(
                postalCode = settings.optString("postalCode"),
                radiusKm = settings.optInt("radiusKm", SearchLocation.DEFAULT_RADIUS_KM),
                storeFilter = settings.optString("storeFilter"),
                browseModel = settings.optString("browseModel"),
                ntfyTopic = settings.optString("ntfyTopic"),
                checkIntervalSec = settings.optInt("checkIntervalSec", TrackerPreferences.DEFAULT_CHECK_INTERVAL_SEC)
            ),
            rules = WatchRuleJson.decode(root.optJSONArray("rules")?.toString() ?: "[]"),
            events = root.optJSONArray("events").objects().mapNotNull { o ->
                val sku = o.optString("sku").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                StockEventEntity(
                    timestamp = o.optLong("timestamp"),
                    storeNumber = o.optString("storeNumber"),
                    storeName = o.optString("storeName"),
                    sku = sku,
                    kind = o.optString("kind"),
                    durationMs = if (o.has("durationMs")) o.optLong("durationMs") else null,
                    afterGap = o.optBoolean("afterGap")
                )
            },
            checks = root.optJSONArray("checks").objects().map { o ->
                CheckLogEntity(
                    timestamp = o.optLong("timestamp"),
                    postalCode = o.optString("postalCode"),
                    skuCount = o.optInt("skuCount"),
                    success = o.optBoolean("success"),
                    background = o.optBoolean("background")
                )
            },
            states = root.optJSONArray("states").objects().mapNotNull { o ->
                val sku = o.optString("sku").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                LastStateEntity(
                    storeNumber = o.optString("storeNumber"),
                    sku = sku,
                    inStock = o.optBoolean("inStock"),
                    since = o.optLong("since"),
                    lastSeen = o.optLong("lastSeen")
                )
            }
        )
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
}
