package com.melox.player.memory

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FairMemoryCompletionTest {
    @Test
    fun successfulWorkBeforeDeadline_repliesOnce() {
        val completion = FairMemoryCompletion(2_500L)
        assertEquals(0, completion.complete(true, 2_499L))
        assertNull(completion.complete(false, 2_500L))
    }

    @Test
    fun writeFailureAndLateSuccess_reportFailure() {
        assertEquals(1, FairMemoryCompletion(2_500L).complete(false, 100L))
        assertEquals(1, FairMemoryCompletion(2_500L).complete(true, 2_500L))
        assertEquals(1, FairMemoryCompletion(2_500L).complete(true, 9_000L))
    }

    @Test
    fun timedOutWriteCannotLaterReportSuccess() {
        val completion = FairMemoryCompletion(2_500L)
        assertEquals(1, completion.complete(false, 2_500L))
        assertNull(completion.complete(true, 3_000L))
    }

    @Test
    fun concurrentWatchdogAndWorker_onlyOneOwnsReply() {
        val completion = FairMemoryCompletion(2_500L)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val results = listOf(true, false).map { success ->
                executor.submit<Int?> {
                    start.await()
                    completion.complete(success, 2_500L)
                }
            }
            start.countDown()
            assertEquals(listOf(1), results.mapNotNull { it.get(2, TimeUnit.SECONDS) })
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun onlyMatchingActionsAndKnownTypesAreAccepted() {
        for (type in listOf(1000, 2000)) {
            assertTrue(isValidFairMemoryNotification(FAIR_MEMORY_TRIM, "trim", type, true))
            assertTrue(isValidFairMemoryNotification(FAIR_MEMORY_KILL, "kill", type, true))
        }
        assertFalse(isValidFairMemoryNotification(FAIR_MEMORY_KILL, "trim", 1000, true))
        assertFalse(isValidFairMemoryNotification(FAIR_MEMORY_TRIM, "kill", 1000, true))
        assertFalse(isValidFairMemoryNotification(FAIR_MEMORY_TRIM, null, 1000, true))
        assertFalse(isValidFairMemoryNotification("unknown", "trim", 1000, true))
        assertFalse(isValidFairMemoryNotification(FAIR_MEMORY_TRIM, "trim", -1, true))
        assertFalse(isValidFairMemoryNotification(FAIR_MEMORY_TRIM, "trim", 1000, false))
    }
}
