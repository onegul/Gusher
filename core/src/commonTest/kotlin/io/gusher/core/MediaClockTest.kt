package io.gusher.core

import app.cash.turbine.test
import io.gusher.core.time.MediaClock
import io.gusher.core.time.Micros
import io.gusher.core.time.MonotonicClock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MediaClockTest {
    private class FakeClock : MonotonicClock {
        var now: Long = 0L
        override fun nowMicros(): Micros = Micros(now)
    }

    @Test
    fun stampVideo_is_monotonic_even_with_regressing_input() {
        val clock = MediaClock(FakeClock())

        val p1 = clock.stampVideo(Micros(1000))
        val p2 = clock.stampVideo(Micros(500)) // regression
        val p3 = clock.stampVideo(Micros(500)) // duplicate

        assertTrue(p2 > p1)
        assertTrue(p3 > p2)
    }

    @Test
    fun elapsed_starts_at_zero_then_progresses() {
        val fake = FakeClock().apply { now = 1_000_000 }
        val clock = MediaClock(fake)

        assertEquals(Micros.ZERO, clock.elapsed())
        fake.now = 1_500_000
        assertEquals(Micros(500_000), clock.elapsed())
    }

    @Test
    fun drift_reports_video_minus_audio() {
        val clock = MediaClock(FakeClock())
        clock.stampVideo(Micros(10_000))
        clock.stampAudio(Micros(8_000))
        assertEquals(Micros(2_000), clock.drift())
    }

    @Test
    fun reset_clears_all_state() {
        val fake = FakeClock().apply { now = 500_000 }
        val clock = MediaClock(fake)
        clock.elapsed()
        clock.stampVideo(Micros(1000))

        clock.reset()
        assertEquals(Micros.ZERO, clock.drift())
        fake.now = 600_000
        assertEquals(Micros.ZERO, clock.elapsed())
    }

    @Test
    fun tick_emits_current_state() = runTest {
        val clock = MediaClock(FakeClock())
        clock.stampVideo(Micros(5000))
        clock.stampAudio(Micros(3000))

        clock.ticks.test {
            clock.tick()
            val event = awaitItem()
            assertEquals(Micros(5000), event.lastVideoPts)
            assertEquals(Micros(3000), event.lastAudioPts)
            assertEquals(Micros(2000), event.drift)
        }
    }
}