package com.yuukifst.orpheus.data.youtube

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class YouTubeInFlightShare<K, V> {
    private val mutex = Mutex()
    private val inFlight = mutableMapOf<K, Deferred<V>>()

    /**
     * [scope] must outlive callers (extractor-owned SupervisorJob + IO).
     * Do not pass a ViewModel/prefetch Job as [scope] — cancelling the waiter
     * must not cancel other waiters' work.
     */
    suspend fun share(
        key: K,
        scope: CoroutineScope,
        compute: suspend () -> V,
    ): V {
        val deferred = mutex.withLock {
            inFlight[key]?.takeIf { it.isActive } ?: scope.async {
                compute()
            }.also { inFlight[key] = it }
        }
        try {
            return deferred.await()
        } finally {
            mutex.withLock {
                if (inFlight[key] === deferred && deferred.isCompleted) {
                    inFlight.remove(key)
                }
            }
        }
    }
}
