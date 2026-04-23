package io.gusher.core.time

import kotlinx.atomicfu.AtomicLong
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Master clock for A/V synchronization.
 *
 * Responsibilities:
 *  - Assign monotonically non-decreasing PTS values to incoming frames
 *  - Detect and correct drift between the capture clock and wall clock
 *  - Expose sync events for downstream consumers (e.g., ABR, telemetry)
 *
 * Thread-safety: all methods are safe for concurrent use.
 */
public class MediaClock(private val source: MonotonicClock = MonotonicClock.System) {
    private val startMicros = atomic(-1L)
    private val lastVideoPts = atomic(Long.MIN_VALUE)
    private val lastAudioPts = atomic(Long.MIN_VALUE)

    private val _ticks = MutableSharedFlow<ClockTick>(replay = 0, extraBufferCapacity = 64)
    public val ticks: SharedFlow<ClockTick> = _ticks.asSharedFlow()

    /**
     * Returns elapsed time since the first call. The first invocation establishes t=0.
     */
    public fun elapsed(): Micros {
        val now = source.nowMicros().value
        val start = startMicros.value
        return if (start == -1L) {
            startMicros.compareAndSet(-1L, now)
            Micros.ZERO
        } else
            Micros(now - start)
    }

    /**
     * Stamps a video frame. Enforces monotonic PTS; duplicates are bumped by 1 microsecond.
     */
    public fun stampVideo(capturedAt: Micros? = null): Micros {
        val base = capturedAt?.value ?: elapsed().value
        return Micros(advanceMonotonic(lastVideoPts, base))
    }

    /**
     * Stamps an audio frame. Enforces monotonic PTS; duplicates are bumped by 1 microsecond.
     */
    public fun stampAudio(capturedAt: Micros? = null): Micros {
        val base = capturedAt?.value ?: elapsed().value
        return Micros(advanceMonotonic(lastAudioPts, base))
    }

    /**
     * Reports the current A/V drift (video PTS - audio PTS).
     * A positive value means video is ahead of audio.
     */
    public fun drift(): Micros {
        val v = lastVideoPts.value
        val a = lastAudioPts.value
        if (v == Long.MIN_VALUE || a == Long.MIN_VALUE) return Micros.ZERO
        return Micros(v - a)
    }

    /**
     * Emits a tick event for observers. Call from a pipeline heartbeat.
     */
    public suspend fun tick() {
        _ticks.emit(
            ClockTick(
                elapsed = elapsed(),
                drift = drift(),
                lastVideoPts = lastVideoPts.value.takeIf { it != Long.MIN_VALUE }?.let(::Micros),
                lastAudioPts = lastAudioPts.value.takeIf { it != Long.MIN_VALUE }?.let(::Micros)
            )
        )
    }

    public fun reset() {
        startMicros.value = -1L
        lastVideoPts.value = Long.MIN_VALUE
        lastAudioPts.value = Long.MIN_VALUE
    }

    private fun advanceMonotonic(ref: AtomicLong, proposed: Long): Long {
        while (true) {
            val prev = ref.value
            val next = if (prev == Long.MIN_VALUE) proposed else maxOf(prev + 1, proposed)
            if (ref.compareAndSet(prev, next)) return next
        }
    }
}