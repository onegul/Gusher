package io.gusher.core.frame

import io.gusher.core.buffer.BufferRef
import io.gusher.core.time.Micros

public data class AudioFrame(
    override val pts: Micros,
    override val payload: BufferRef,
    val codec: AudioCodec,
    val sampleRate: Int,
    val channels: Int
) : Frame {
    override val dts: Micros
        get() = pts             // Audio has no B-frames
}

public enum class AudioCodec {
    AAC_LC,
    OPUS
}