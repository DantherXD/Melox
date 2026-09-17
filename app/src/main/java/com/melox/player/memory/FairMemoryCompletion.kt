package com.melox.player.memory

import java.util.concurrent.atomic.AtomicBoolean

/** Arbitrates the independent watchdog and worker, including delayed completions. */
internal class FairMemoryCompletion(private val deadlineMillis: Long) {
    private val completed = AtomicBoolean()

    fun complete(success: Boolean, nowMillis: Long): Int? {
        if (!completed.compareAndSet(false, true)) return null
        return if (success && nowMillis < deadlineMillis) 0 else 1
    }
}

internal const val FAIR_MEMORY_TRIM = "itgsa.intent.action.TRIM"
internal const val FAIR_MEMORY_KILL = "itgsa.intent.action.KILL"

internal fun isValidFairMemoryNotification(
    intentAction: String?,
    commonAction: String?,
    notifyType: Int,
    hasNotifyId: Boolean,
): Boolean = hasNotifyId && (notifyType == 1000 || notifyType == 2000) && when (intentAction) {
    FAIR_MEMORY_TRIM -> commonAction == "trim"
    FAIR_MEMORY_KILL -> commonAction == "kill"
    else -> false
}
