package io.gusher.core.buffer

import kotlinx.atomicfu.atomic

/**
 * A reference to a pooled byte buffer. Must be released exactly once via [close] or by using the [use] extension.
 * Double-release is a programming error and will throw in debug builds.
 *
 * The underlying storage is a shared [ByteArray]; consumers should treat [data] as valid only for the range
 * [offset, offset + length).
 */
public class BufferRef internal constructor(
    public val data: ByteArray,
    public val offset: Int,
    length: Int,
    private val onRelease: (BufferRef) -> Unit
) : AutoCloseable {
    private val _length = atomic(length)
    private val closed = atomic(false)

    public val length: Int get() = _length.value
    public val capacity: Int get() = data.size - offset

    public fun setLength(newLength: Int) {
        require(newLength in 0..capacity) { "length=$newLength out of [0, $capacity]" }
        _length.value = newLength
    }

    override fun close() {
        if (closed.compareAndSet(expect = false, update = true))
            onRelease(this)
    }

    public val isClosed: Boolean get() = closed.value
}

public inline fun <R> BufferRef.use(block: (BufferRef) -> R): R {
    try {
        return block(this)
    } finally {
        close()
    }
}