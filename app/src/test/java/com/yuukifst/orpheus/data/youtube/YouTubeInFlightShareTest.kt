package com.yuukifst.orpheus.data.youtube

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class YouTubeInFlightShareTest {

    @Test
    fun `concurrent share with same key runs compute once`() = runTest {
        val share = YouTubeInFlightShare<String, Int>()
        val computeCount = AtomicInteger(0)
        val extractScope = CoroutineScope(coroutineContext + SupervisorJob())
        val first = async {
            share.share("k", extractScope) {
                delay(50)
                computeCount.incrementAndGet()
                7
            }
        }
        val second = async {
            share.share("k", extractScope) {
                delay(50)
                computeCount.incrementAndGet()
                7
            }
        }
        assertEquals(7, first.await())
        assertEquals(7, second.await())
        assertEquals(1, computeCount.get())
    }

    @Test
    fun `after success a later share runs compute again`() = runTest {
        val share = YouTubeInFlightShare<String, Int>()
        val computeCount = AtomicInteger(0)
        val extractScope = CoroutineScope(coroutineContext + SupervisorJob())
        share.share("k", extractScope) {
            computeCount.incrementAndGet()
            1
        }
        share.share("k", extractScope) {
            computeCount.incrementAndGet()
            2
        }
        assertEquals(2, computeCount.get())
    }

    @Test
    fun `failed compute is seen by waiters then retry runs again`() = runTest {
        val share = YouTubeInFlightShare<String, Int>()
        val computeCount = AtomicInteger(0)
        val extractScope = CoroutineScope(coroutineContext + SupervisorJob())
        val first = async {
            runCatching {
                share.share("k", extractScope) {
                    computeCount.incrementAndGet()
                    delay(20)
                    error("boom")
                }
            }
        }
        val second = async {
            runCatching {
                share.share("k", extractScope) {
                    computeCount.incrementAndGet()
                    delay(20)
                    error("boom")
                }
            }
        }
        assertTrue(first.await().isFailure)
        assertTrue(second.await().isFailure)
        assertEquals(1, computeCount.get())
        val retry = share.share("k", extractScope) {
            computeCount.incrementAndGet()
            9
        }
        assertEquals(9, retry)
        assertEquals(2, computeCount.get())
    }
}
