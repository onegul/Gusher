package io.gusher.core.time

import kotlin.time.TimeSource

/**
 * Abstraction over a monotonic time source so tests can inject a fake clock.
 */
public interface MonotonicClock {
    public fun nowMicros(): Micros

    public companion object {
        public val System: MonotonicClock = SystemMonotonicClock
    }
}

private object SystemMonotonicClock : MonotonicClock {
    private val origin = TimeSource.Monotonic.markNow()

    override fun nowMicros(): Micros = Micros(origin.elapsedNow().inWholeMicroseconds)
}