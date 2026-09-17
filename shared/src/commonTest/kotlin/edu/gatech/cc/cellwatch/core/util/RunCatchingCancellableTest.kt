package edu.gatech.cc.cellwatch.core.util

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RunCatchingCancellableTest {

    @Test
    fun returnsSuccess_whenBlockCompletes() {
        val result = runCatchingCancellable { "ok" }

        assertEquals("ok", result.getOrNull())
    }

    @Test
    fun capturesOrdinaryFailures_likeRunCatching() {
        val result = runCatchingCancellable { throw IllegalStateException("boom") }

        assertTrue(result.isFailure)
        assertEquals("boom", result.exceptionOrNull()?.message)
    }

    @Test
    fun rethrowsCancellation_ratherThanCapturingIt() {
        assertFailsWith<CancellationException> {
            runCatchingCancellable { throw CancellationException("cancelled") }
        }
    }
}
