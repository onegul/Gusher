package io.gusher.core.frame

import io.gusher.core.buffer.BufferRef
import io.gusher.core.time.Micros

public data class VideoFrame(
    override val pts: Micros,
    override val dts: Micros,
    override val payload: BufferRef,
    val isKeyFrame: Boolean,
    val codec: VideoCodec,
    val width: Int,
    val height: Int
) : Frame

public enum class VideoCodec {
    H264,
    H265,
    AV1
}