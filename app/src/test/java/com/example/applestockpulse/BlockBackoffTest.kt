package com.example.applestockpulse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockBackoffTest {

    private val minute = 60_000L
    private val hour = 60 * minute

    @Test
    fun `during a block, test requests come after 1, 2, 3, then every 5 minutes`() {
        val backoff = BlockBackoff()
        val waits = (1..6).map {
            backoff.onBlocked(0L)
            backoff.blockedUntilMs / minute
        }
        assertEquals(listOf(1L, 2L, 3L, 5L, 5L, 5L), waits)
    }

    @Test
    fun `a good answer ends the block and restarts the waits`() {
        val backoff = BlockBackoff()
        backoff.onBlocked(0L)
        backoff.onBlocked(0L)
        assertTrue(backoff.isBlocked(minute))

        backoff.onSuccess(3 * minute)
        assertFalse(backoff.isBlocked(3 * minute))

        backoff.onBlocked(10 * minute)
        assertEquals(11 * minute, backoff.blockedUntilMs)
    }

    @Test
    fun `the block ends by itself when the time passes`() {
        val backoff = BlockBackoff()
        backoff.onBlocked(0L)
        assertTrue(backoff.isBlocked(minute - 1))
        assertFalse(backoff.isBlocked(minute))
    }

    @Test
    fun `each new block slows normal checks by 15 s, at most 4 times`() {
        val backoff = BlockBackoff()
        backoff.onBlocked(0L)
        backoff.onBlocked(0L) // same block: no extra step
        assertEquals(15_000L, backoff.extraIntervalMs)

        repeat(5) { i ->
            backoff.onSuccess(minute * (i + 1))
            backoff.onBlocked(minute * (i + 1))
        }
        assertEquals(60_000L, backoff.extraIntervalMs)
    }

    @Test
    fun `two hours without a block remove one slowdown step`() {
        val backoff = BlockBackoff()
        backoff.onBlocked(10 * hour)
        backoff.onSuccess(10 * hour + minute)
        assertEquals(15_000L, backoff.extraIntervalMs)

        backoff.onSuccess(10 * hour + minute)
        assertEquals(15_000L, backoff.extraIntervalMs)
        backoff.onSuccess(12 * hour + minute)
        assertEquals(0L, backoff.extraIntervalMs)
    }

    @Test
    fun `only block answers count as blocks`() {
        assertTrue(BlockBackoff.isBlock(541))
        assertTrue(BlockBackoff.isBlock(429))
        assertTrue(BlockBackoff.isBlock(403))
        assertFalse(BlockBackoff.isBlock(500))
        assertFalse(BlockBackoff.isBlock(200))
        assertFalse(BlockBackoff.isBlock(null))
    }
}
