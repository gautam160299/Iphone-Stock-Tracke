package com.example.applestockpulse

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

object StockEventKind {
    // Became available after an earlier check saw it unavailable
    const val RESTOCK = "restock"
    // Became unavailable after an earlier check saw it available
    const val SOLD_OUT = "sold_out"
    // Available on the first check that ever saw this store/SKU; the start time is unknown
    const val FIRST_SEEN = "first_seen"
}

@Entity(
    tableName = "stock_events",
    indices = [Index("timestamp"), Index("sku"), Index("storeNumber")]
)
data class StockEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val storeNumber: String,
    val storeName: String,
    val sku: String,
    val kind: String,
    // For SOLD_OUT: how long the item was in stock
    val durationMs: Long? = null,
    // True when checks stopped for a while before this change, so the exact time is unknown
    val afterGap: Boolean = false
)

@Entity(tableName = "check_log", indices = [Index("timestamp")])
data class CheckLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val postalCode: String,
    val skuCount: Int,
    val success: Boolean,
    // True for checks of the background monitor; false for Browse and manual checks
    val background: Boolean
)

@Entity(tableName = "last_state", primaryKeys = ["storeNumber", "sku"])
data class LastStateEntity(
    val storeNumber: String,
    val sku: String,
    val inStock: Boolean,
    // When the current state began (as far as the checks can tell)
    val since: Long,
    val lastSeen: Long
)

@Dao
interface HistoryDao {

    @Insert
    suspend fun insertEvents(events: List<StockEventEntity>)

    @Insert
    suspend fun insertCheck(check: CheckLogEntity)

    @Upsert
    suspend fun upsertStates(states: List<LastStateEntity>)

    @Query("SELECT * FROM last_state WHERE sku IN (:skus)")
    suspend fun statesForSkus(skus: List<String>): List<LastStateEntity>

    @Query("SELECT * FROM stock_events WHERE timestamp >= :since ORDER BY timestamp DESC, id DESC")
    fun observeEventsSince(since: Long): Flow<List<StockEventEntity>>

    @Query("SELECT * FROM stock_events WHERE timestamp >= :since ORDER BY timestamp DESC, id DESC")
    suspend fun eventsSince(since: Long): List<StockEventEntity>

    @Query("SELECT * FROM check_log WHERE timestamp >= :since ORDER BY timestamp ASC")
    fun observeChecksSince(since: Long): Flow<List<CheckLogEntity>>

    @Query("SELECT COALESCE(MAX(id), 0) FROM stock_events")
    suspend fun maxEventId(): Long

    @Query("DELETE FROM stock_events WHERE timestamp < :before")
    suspend fun deleteEventsBefore(before: Long)

    @Query("DELETE FROM check_log WHERE timestamp < :before")
    suspend fun deleteChecksBefore(before: Long)

    @Query("DELETE FROM last_state WHERE lastSeen < :before")
    suspend fun deleteStatesBefore(before: Long)

    @Query("DELETE FROM stock_events")
    suspend fun clearEvents()

    @Query("DELETE FROM check_log")
    suspend fun clearChecks()

    @Query("DELETE FROM last_state")
    suspend fun clearStates()

    @Transaction
    suspend fun pruneBefore(before: Long) {
        deleteEventsBefore(before)
        deleteChecksBefore(before)
        deleteStatesBefore(before)
    }

    @Transaction
    suspend fun clearAll() {
        clearEvents()
        clearChecks()
        clearStates()
    }

    @Transaction
    suspend fun record(check: CheckLogEntity, events: List<StockEventEntity>, states: List<LastStateEntity>) {
        insertCheck(check)
        if (events.isNotEmpty()) insertEvents(events)
        if (states.isNotEmpty()) upsertStates(states)
    }
}

@Database(
    entities = [StockEventEntity::class, CheckLogEntity::class, LastStateEntity::class],
    version = 1,
    exportSchema = false
)
abstract class HistoryDatabase : RoomDatabase() {

    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile
        private var instance: HistoryDatabase? = null

        fun get(context: Context): HistoryDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    HistoryDatabase::class.java,
                    "stock_history.db"
                ).build().also { instance = it }
            }
    }
}
