package com.example.applestockpulse

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppBackupTest {

    private lateinit var db: HistoryDatabase
    private lateinit var dao: HistoryDao

    private val event = StockEventEntity(
        id = 7,
        timestamp = 1_000L,
        storeNumber = "R601",
        storeName = "Pacific Centre",
        sku = "MK244VC/A",
        kind = StockEventKind.SOLD_OUT,
        durationMs = 90_000L,
        afterGap = true
    )
    private val check = CheckLogEntity(id = 3, timestamp = 1_000L, postalCode = "V6C1T2", skuCount = 4, success = true, background = true)
    private val state = LastStateEntity(storeNumber = "R601", sku = "MK244VC/A", inStock = false, since = 1_000L, lastSeen = 2_000L)

    private val contents = AppBackup.Contents(
        exportedAt = 5_000L,
        settings = AppBackup.Settings("V6C1T2", 50, "R601", "iPhone Duo", "applestock-abc"),
        rules = listOf(WatchRule(id = "rule-1", name = "Duo", selectors = listOf(DeviceSelector("iPhone Duo", setOf("256GB"))), createdAt = 1L)),
        events = listOf(event),
        checks = listOf(check),
        states = listOf(state)
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HistoryDatabase::class.java).allowMainThreadQueries().build()
        dao = db.historyDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `a backup reads back the same`() {
        val decoded = AppBackup.decode(AppBackup.encode(contents))

        assertEquals(contents.settings, decoded.settings)
        assertEquals(contents.rules, decoded.rules)
        assertEquals(contents.checks.map { it.copy(id = 0) }, decoded.checks)
        assertEquals(contents.states, decoded.states)
        assertEquals(contents.events.map { it.copy(id = 0) }, decoded.events)
    }

    @Test
    fun `an event without a duration keeps it empty`() {
        val decoded = AppBackup.decode(AppBackup.encode(contents.copy(events = listOf(event.copy(durationMs = null)))))
        assertNull(decoded.events.single().durationMs)
    }

    @Test
    fun `other files are refused`() {
        assertThrows(AppBackup.InvalidBackupException::class.java) { AppBackup.decode("not json") }
        assertThrows(AppBackup.InvalidBackupException::class.java) { AppBackup.decode("""{"format":"something-else"}""") }
        assertThrows(AppBackup.InvalidBackupException::class.java) {
            AppBackup.decode("""{"format":"applestockpulse-backup","version":${AppBackup.FORMAT_VERSION + 1}}""")
        }
    }

    @Test
    fun `restoring twice adds the history once`() = runTest {
        assertEquals(1, dao.mergeBackup(contents.events, contents.checks, contents.states))
        assertEquals(0, dao.mergeBackup(contents.events, contents.checks, contents.states))

        assertEquals(1, dao.allEvents().size)
        assertEquals(1, dao.allChecks().size)
        assertEquals(1, dao.allStates().size)
    }

    @Test
    fun `restore keeps the newer state of a store and phone`() = runTest {
        val newer = state.copy(inStock = true, lastSeen = 9_000L)
        dao.upsertStates(listOf(newer))

        dao.mergeBackup(emptyList(), emptyList(), listOf(state))

        assertEquals(newer, dao.allStates().single())
    }
}
