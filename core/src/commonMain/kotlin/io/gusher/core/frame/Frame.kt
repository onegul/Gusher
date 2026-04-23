package io.gusher.core.frame

import io.gusher.core.buffer.BufferRef
import io.gusher.core.time.Micros

/**
 * A unit of media flowing through the pipeline.
 *
 * Ownership contract: whoever receives a Frame is responsible for calling [release] exactly once. Failing to do so
 * leaks the underlying pooled buffer.
 */
public sealed interface Frame {
    public val pts: Micros
    public val dts: Micros
    public val payload: BufferRef

    public fun release() {
        payload.close()
    }
}