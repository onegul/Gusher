package io.gusher.core

import io.gusher.core.buffer.BufferPool
import io.gusher.core.buffer.use
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class BufferPoolTest {
    @Test
    fun acquire_returns_buffer_of_sufficient_capacity() {
        val pool = BufferPool()
        pool.acquire(1500).use { ref ->
            assertTrue(ref.capacity >= 1500)
            assertEquals(1500, ref.length)
        }
    }

    @Test
    fun released_buffer_is_reused() {
        val pool = BufferPool()
        val first = pool.acquire(4096)
        val underlying = first.data
        first.close()

        val second = pool.acquire(4096)
        assertEquals(underlying, second.data, "pool should reuse the same array")
        second.close()

        val stats = pool.stats
        assertTrue(stats.hits >= 1)
    }

    @Test
    fun different_size_classes_do_not_collide() {
        val pool = BufferPool()
        val small = pool.acquire(1024)
        val large = pool.acquire(65536)
        assertNotEquals(small.data.size, large.data.size)
        small.close()
        large.close()
    }

    @Test
    fun oversized_request_bypasses_pool() {
        val pool = BufferPool(maxSizeClass = 1024)
        pool.acquire(1_000_000).use { ref ->
            assertTrue(ref.capacity >= 1_000_000)
        }
        assertEquals(1L, pool.stats.oversizedAllocations)
    }

    @Test
    fun double_close_is_idempotent() {
        val pool = BufferPool()
        val ref = pool.acquire(128)
        ref.close()
        ref.close()         // must not throw or re-pool
        assertTrue(ref.isClosed)
    }

    @Test
    fun zero_capacity_request_is_safe() {
        val pool = BufferPool()
        pool.acquire(0).use { ref ->
            assertEquals(0, ref.length)
        }
    }

    @Test
    fun pool_respects_soft_cap() {
        val pool = BufferPool(maxPoolBytes = 8192)
        // Release many 4 KiB buffers; only a couple should pool
        repeat(10) { pool.acquire(4096).close() }
        assertTrue(pool.stats.pooledBytes <= 8192)
    }
}