package io.gusher.core.buffer

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * A size-class bucketed buffer pool to minimize allocation churn in hot paths (encoder output -> network).
 * Uses power-of-two size classes.
 *
 * Design:
 *  - Each size class has its own free-list guarded by a cheap lock
 *  - Over-large requests are allocated directly (not pooled)
 *  - Pool has a soft upper bound; excess buffers are discarded on release
 *
 * Thread-safety: safe for concurrent acquire/release from any thread.
 */
public class BufferPool(
    private val maxPoolBytes: Long = DEFAULT_MAX_POOL_BYTES,
    private val minSizeClass: Int = 1024,                       // 1 KiB
    private val maxSizeClass: Int = 4 * 1024 * 1024             // 4 MiB
) {
    init {
        require(minSizeClass > 0 && minSizeClass.isPowerOfTwo()) { "minSizeClass must be a positive power of two" }
        require(maxSizeClass >= minSizeClass && maxSizeClass.isPowerOfTwo()) { "maxSizeClass must be a power of two >= minSizeClass" }
    }

    private val buckets: Array<Bucket> = run {
        var size = minSizeClass
        val list = mutableListOf<Bucket>()
        while (size <= maxSizeClass) {
            list += Bucket(size)
            size = size shl 1
        }
        list.toTypedArray()
    }

    private val totalPooledBytes = atomic(0L)

    // Stats
    private val _hits = atomic(0L)
    private val _misses = atomic(0L)
    private val _oversized = atomic(0L)

    public val stats: PoolStats
        get() = PoolStats(
            hits = _hits.value,
            misses = _misses.value,
            oversizedAllocations = _oversized.value,
            pooledBytes = totalPooledBytes.value
        )

    /**
     * Acquires a buffer with capacity >= [minCapacity]. The returned [BufferRef] has
     * [BufferRef.length] == [minCapacity] initially; callers may adjust.
     */
    public fun acquire(minCapacity: Int): BufferRef {
        require(minCapacity >= 0) { "minCapacity must be non-negative" }
        if (minCapacity == 0) return BufferRef(EMPTY, 0, 0) { /* no-op */ }

        val bucketIdx = bucketIndexFor(minCapacity)
        if (bucketIdx == -1) {
            // Oversized: allocate directly, don't pool on release
            _oversized.incrementAndGet()
            val data = ByteArray(minCapacity)
            return BufferRef(data, 0, minCapacity) { /* GC reclaims */ }
        }

        val bucket = buckets[bucketIdx]
        val pooled = bucket.poll()
        return if (pooled != null) {
            _hits.incrementAndGet()
            totalPooledBytes.addAndGet(-pooled.size.toLong())
            BufferRef(pooled, 0, minCapacity, onRelease = ::onRelease)
        } else {
            _misses.incrementAndGet()
            BufferRef(ByteArray(bucket.sizeClass), 0, minCapacity, onRelease = ::onRelease)
        }
    }

    private fun onRelease(ref: BufferRef) {
        val capacity = ref.data.size
        val idx = bucketIndexFor(capacity)
        if (idx == -1) return // oversize; let GC reclaim

        val bucket = buckets[idx]
        if (totalPooledBytes.value + capacity > maxPoolBytes) return // soft cap reached

        if (bucket.offer(ref.data))
            totalPooledBytes.addAndGet(capacity.toLong())
    }

    public fun clear() {
        buckets.forEach { it.clear() }
        totalPooledBytes.value = 0L
    }

    private fun bucketIndexFor(capacity: Int): Int {
        if (capacity > maxSizeClass) return -1
        val clamped = maxOf(capacity, minSizeClass)
        val rounded = clamped.nextPowerOfTwo()
        val minLong = minSizeClass.countTrailingZeroBits()
        val log = rounded.countTrailingZeroBits()
        return log - minLong
    }

    private class Bucket(val sizeClass: Int) {
        private val lock = SynchronizedObject()
        private val stack = ArrayDeque<ByteArray>()
        private val maxDepth = 32 // per-bucket cap

        fun poll(): ByteArray? = synchronized(lock) {
            if (stack.isEmpty()) null else stack.removeLast()
        }

        fun offer(array: ByteArray): Boolean = synchronized(lock) {
            if (stack.size >= maxDepth)
                false
            else {
                stack.addLast(array)
                true
            }
        }

        fun clear(): Unit = synchronized(lock) { stack.clear() }
    }

    public data class PoolStats(
        val hits: Long,
        val misses: Long,
        val oversizedAllocations: Long,
        val pooledBytes: Long
    ) {
        val hitRate: Double
            get() = if (hits + misses == 0L) 0.0 else hits.toDouble() / (hits + misses)
    }

    public companion object {
        public const val DEFAULT_MAX_POOL_BYTES: Long = 32L * 1024 * 1024 // 32 MiB
        private val EMPTY = ByteArray(0)
    }
}

private fun Int.isPowerOfTwo(): Boolean = this > 0 && (this and (this - 1)) == 0

private fun Int.nextPowerOfTwo(): Int {
    if (this <= 1) return 1
    var v = this - 1
    v = v or (v ushr 1)
    v = v or (v ushr 2)
    v = v or (v ushr 4)
    v = v or (v ushr 8)
    v = v or (v ushr 16)
    return v + 1
}