package io.gusher.core

import app.cash.turbine.test
import io.gusher.core.buffer.BufferPool
import io.gusher.core.frame.AudioCodec
import io.gusher.core.frame.AudioFrame
import io.gusher.core.pipeline.MediaPipeline
import io.gusher.core.time.Micros
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MediaPipelineTest {
    private val pool = BufferPool()

    private fun makeFrame(pts: Long) = AudioFrame(
        pts = Micros(pts),
        payload = pool.acquire(128),
        codec = AudioCodec.AAC_LC,
        sampleRate = 48_000,
        channels = 2
    )

    @Test
    fun publish_delivers_to_subscribers() = runTest {
        val pipeline = MediaPipeline()
        pipeline.frames.test {
            val f = makeFrame(100)
            assertTrue(pipeline.publish(f))
            val received = awaitItem()
            assertEquals(Micros(100), received.pts)
            received.release()
        }
    }

    @Test
    fun counters_track_published_frames() = runTest {
        val pipeline = MediaPipeline()
        pipeline.frames.test {
            repeat(3) { pipeline.publish(makeFrame(it.toLong())) }
            repeat(3) { awaitItem().release() }
        }
        assertEquals(3L, pipeline.publishedFrames)
    }
}