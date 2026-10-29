package com.example.applestockpulse

/**
 * Waits longer after each answer that says Apple is blocking us (HTTP 541, 429, 403), so the
 * app stops sending requests while the block is on. Sending more requests only makes the
 * block last longer. One good answer resets it. Not thread-safe; use it under the poll mutex.
 */
class BlockBackoff(
    private val firstWaitMs: Long = 2 * 60 * 1000L,
    private val maxWaitMs: Long = 15 * 60 * 1000L
) {
    companion object {
        val BLOCK_STATUS_CODES = setOf(541, 429, 403)

        fun isBlock(statusCode: Int?): Boolean = statusCode != null && statusCode in BLOCK_STATUS_CODES
    }

    private var streak = 0

    /** No request to Apple until this time. 0 when not blocked. */
    @Volatile
    var blockedUntilMs = 0L
        private set

    fun isBlocked(nowMs: Long): Boolean = nowMs < blockedUntilMs

    /** 2, 4, 8, 15, 15 ... minutes. */
    fun onBlocked(nowMs: Long) {
        streak++
        val wait = (firstWaitMs shl (streak - 1).coerceAtMost(10)).coerceAtMost(maxWaitMs)
        blockedUntilMs = nowMs + wait
    }

    fun onSuccess() {
        streak = 0
        blockedUntilMs = 0L
    }
}
