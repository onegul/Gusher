package io.gusher.core.pipeline

import io.gusher.core.frame.Frame
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * A backpressure-aware pipeline for [Frame] transport.
 *
 * Live streaming semantics: if consumers can't keep up, we DROP OLDEST rather than block the producer (encoder stall
 * would be catastrophic). Dropped frames are counted and surfaced view [droppedFrames].
 */
public class MediaPipeline(bufferCapacity: Int = DEFAULT_BUFFER) {
    private val _frames = MutableSharedFlow<Frame>(
        replay = 0,
        extraBufferCapacity = bufferCapacity,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    public val frames: Flow<Frame> = _frames.asSharedFlow()

    private val _published = atomic(0L)
    private val _dropped = atomic(0L)

    public val publishedFrames: Long get() = _published.value
    public val droppedFrames: Long get() = _dropped.value

    /**
     * Emits a frame. Returns true if delivered to the buffer, false if dropped.
     * The frame's payload ownership transfers to the pipeline on success.
     */
    public fun publish(frame: Frame): Boolean {
        val accepted = _frames.tryEmit(frame)
        if (accepted)
            _published.incrementAndGet()
        else {
            _dropped.incrementAndGet()
            frame.release()
        }
        return accepted
    }

    public companion object {
        public const val DEFAULT_BUFFER: Int = 128
    }
}