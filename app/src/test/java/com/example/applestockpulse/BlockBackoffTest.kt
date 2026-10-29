package com.example.applestockpulse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockBackoffTest {

    private val minute = 60_000L

    @Test
    fun `each block waits longer, up to 15 minutes`() {
        val backoff = BlockBackoff()
        val waits = (1..6).map {
            backoff.onBlocked(0L)
            backoff.blockedUntilMs / minute
        }
        assertEquals(listOf(2L, 4L, 8L, 15L, 15L, 15L), waits)
    }

    @Test
    fun `a good answer ends the block and resets the wait`() {
        val backoff = BlockBackoff()
        backoff.onBlocked(0L)
        backoff.onBlocked(0L)
        assertTrue(backoff.isBlocked(3 * minute))

        backoff.onSuccess()
        assertFalse(backoff.isBlocked(3 * minute))

        backoff.onBlocked(10 * minute)
        assertEquals(12 * minute, backoff.blockedUntilMs)
    }

    @Test
    fun `the block ends by itself when the time passes`() {
        val backoff = BlockBackoff()
        backoff.onBlocked(0L)
        assertTrue(backoff.isBlocked(2 * minute - 1))
        assertFalse(backoff.isBlocked(2 * minute))
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
