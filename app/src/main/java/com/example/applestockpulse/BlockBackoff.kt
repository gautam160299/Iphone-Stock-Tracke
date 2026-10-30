package com.example.applestockpulse

/**
 * Handles answers that say Apple is blocking us (HTTP 541, 429, 403), to keep the time
 * without checks short:
 *
 * - During a block, one test request after 1, 2, 3, then every 5 minutes. A block that ends
 *   is noticed within 5 minutes, while the few test requests add almost no load.
 * - Each new block adds [slowdownStepMs] to the normal check interval (at most
 *   [maxSlowdownSteps] times), because Apple blocked us at that speed. After [recoverAfterMs]
 *   without a block, one step comes off again.
 *
 * Not thread-safe; use it under the poll mutex. [blockedUntilMs] and [extraIntervalMs] may be
 * read from anywhere.
 */
class BlockBackoff(
    private val waitsMs: List<Long> = listOf(1L, 2L, 3L, 5L).map { it * MINUTE_MS },
    private val slowdownStepMs: Long = 15_000L,
    private val maxSlowdownSteps: Int = 4,
    private val recoverAfterMs: Long = 2 * 60 * MINUTE_MS
) {
    companion object {
        private const val MINUTE_MS = 60_000L

        val BLOCK_STATUS_CODES = setOf(541, 429, 403)

        fun isBlock(statusCode: Int?): Boolean = statusCode != null && statusCode in BLOCK_STATUS_CODES
    }

    private var streak = 0
    private var slowdownSteps = 0
    private var lastSlowdownChangeMs = 0L

    /** No request to Apple until this time. 0 when not blocked. */
    @Volatile
    var blockedUntilMs = 0L
        private set

    /** Added to the chosen check interval because of earlier blocks. */
    @Volatile
    var extraIntervalMs = 0L
        private set

    fun isBlocked(nowMs: Long): Boolean = nowMs < blockedUntilMs

    fun onBlocked(nowMs: Long) {
        if (streak == 0 && slowdownSteps < maxSlowdownSteps) {
            slowdownSteps++
            lastSlowdownChangeMs = nowMs
        }
        streak++
        blockedUntilMs = nowMs + waitsMs[(streak - 1).coerceAtMost(waitsMs.lastIndex)]
        extraIntervalMs = slowdownSteps * slowdownStepMs
    }

    fun onSuccess(nowMs: Long) {
        streak = 0
        blockedUntilMs = 0L
        if (slowdownSteps > 0 && nowMs - lastSlowdownChangeMs >= recoverAfterMs) {
            slowdownSteps--
            lastSlowdownChangeMs = nowMs
        }
        extraIntervalMs = slowdownSteps * slowdownStepMs
    }
}
