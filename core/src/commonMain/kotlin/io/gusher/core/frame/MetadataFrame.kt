package io.gusher.core.frame

import io.gusher.core.buffer.BufferRef
import io.gusher.core.time.Micros

public data class MetadataFrame(
    override val pts: Micros,
    override val payload: BufferRef,
    val kind: MetadataKind
) : Frame {
    override val dts: Micros get() = pts
}

public enum class MetadataKind {
    SE1,
    ID3,
    CUSTOM
}
