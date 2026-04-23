package io.gusher.core.time

public data class ClockTick(
    val elapsed: Micros,
    val drift: Micros,
    val lastVideoPts: Micros?,
    val lastAudioPts: Micros?
)