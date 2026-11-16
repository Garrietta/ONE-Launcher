package dev.one.launcher

import dev.one.launcher.data.PROGRESS_EMIT_INTERVAL_MS
import dev.one.launcher.data.shouldEmitProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Download progress throttle (v1.8.7): a 6.5 GB download at 8 KB
 * reads was ~850,000 Progress emissions, flooding the main thread
 * with recompositions. Now emissions are throttled to at most one
 * per 100 ms or per whole-percent step, and the buffer is 64 KB.
 * The emission decision is pure ([shouldEmitProgress]), so it is
 * unit-testable without a download (DownloadRepository itself needs
 * an Android Context / OkHttp and is not JVM-testable).
 */
class DownloadRepositoryTest {

    @Test
    fun `first emission happens immediately`() {
        assertTrue(shouldEmitProgress(0L, 1000L, 0L, -1, nowMs = 1000L))
    }

    @Test
    fun `no emission within 100ms when percent unchanged`() {
        // 50 ms after the last emission, same percent: silent.
        assertFalse(shouldEmitProgress(500L, 1000L, 1000L, 50, nowMs = 1050L))
    }

    @Test
    fun `emission after 100ms even when percent unchanged`() {
        assertTrue(shouldEmitProgress(500L, 1000L, 1000L, 50, nowMs = 1100L))
    }

    @Test
    fun `emission when the whole percent changes`() {
        // 50 ms after the last emission, but the percent moved 50 -> 51.
        assertTrue(shouldEmitProgress(510L, 1000L, 1000L, 50, nowMs = 1050L))
    }

    @Test
    fun `unknown total throttles by time only`() {
        // No total: the percent never changes, so only the 100 ms
        // rule paces emissions.
        assertFalse(shouldEmitProgress(500L, -1L, 1000L, -1, nowMs = 1050L))
        assertTrue(shouldEmitProgress(500L, -1L, 1000L, -1, nowMs = 1100L))
    }

    @Test
    fun `simulated 6_5GB download emits about 1000 times not 850k`() {
        // A 6.5 GB file read in 64 KB chunks, one emission per 100 ms
        // or per 1% step. Simulate a steady 50 MB/s: each chunk takes
        // ~1.3 ms, so the 100 ms rule fires roughly every 77 chunks
        // (~1,300 times over the 130 s download) and the 1% rule fires
        // 100 times (mostly overlapping). Either way the count is
        // ~1,000 — three orders of magnitude below the ~850,000
        // emissions the old 8 KB unthrottled loop produced.
        val total = 6_500_000_000L
        val chunk = 64L * 1024
        var readSoFar = 0L
        var nowMs = 0L
        var lastEmitMs = 0L
        var lastPercent = -1
        var emissions = 0
        val chunkMs = 1.3
        while (readSoFar < total) {
            readSoFar = minOf(total, readSoFar + chunk)
            nowMs = (nowMs + chunkMs).toLong()
            if (shouldEmitProgress(readSoFar, total, lastEmitMs, lastPercent, nowMs)) {
                emissions++
                lastEmitMs = nowMs
                lastPercent = ((readSoFar * 100) / total).toInt().coerceIn(0, 100)
            }
        }
        // Plus the final emission in the download loop.
        emissions++
        assertTrue("emissions=$emissions, expected ~800-1500", emissions in 800..1500)
        assertTrue("emissions=$emissions, must be far below 850k", emissions < 2000)
        assertEquals(PROGRESS_EMIT_INTERVAL_MS, 100L)
    }
}
