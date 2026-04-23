package io.gusher.core.time

import kotlin.jvm.JvmInline

/**
 * A presentation or decode timestamp in microseconds.
 * Chosen as the canonical unit because it divides cleanly into both 90kHz (RTMP video clock) and 48kHz
 * (common audio rate).
 */
@JvmInline
public value class Micros(public val value: Long) : Comparable<Micros> {
    public operator fun plus(other: Micros): Micros = Micros(value + other.value)

    public operator fun minus(other: Micros): Micros = Micros(value - other.value)

    override fun compareTo(other: Micros): Int = value.compareTo(other.value)

    public companion object {
        public val ZERO: Micros = Micros(0L)

        public fun fromMillis(ms: Long): Micros = Micros(ms * 1_000L)

        public fun fromNanos(ns: Long): Micros = Micros(ns / 1_000L)
    }
}

public fun Long.micros(): Micros = Micros(this)