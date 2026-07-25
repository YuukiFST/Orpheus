package com.yuukifst.orpheus.data.service

import java.util.concurrent.atomic.AtomicLong

/**
 * Invalidates in-flight mini-player dismiss clears when a new play starts.
 *
 * Dismiss fires [MusicService.ACTION_CLEAR_PLAYBACK] asynchronously; without a
 * generation check that clear can wipe media that started after the swipe.
 */
object PlaybackClearGeneration {
    private val generation = AtomicLong(0L)

    fun current(): Long = generation.get()

    fun bump(): Long = generation.incrementAndGet()

    fun matches(token: Long): Boolean = generation.get() == token
}
