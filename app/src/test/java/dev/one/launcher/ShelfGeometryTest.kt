package dev.one.launcher

import dev.one.launcher.ui.LibraryFit
import dev.one.launcher.ui.ShelfMetrics
import dev.one.launcher.ui.libraryFit
import dev.one.launcher.ui.shelfLefts
import dev.one.launcher.ui.shelfOpenness
import dev.one.launcher.ui.shelfWidth
import dev.one.launcher.ui.stepMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ShelfGeometryTest {

    private val m = ShelfMetrics(spine = 30f, cover = 140f, gap = 3f, padStart = 20f, lead = 3)

    @Test
    fun onlyTheGameInTheSlotIsOpen() {
        assertEquals(1f, shelfOpenness(5, 5f), 0f)
        assertEquals(0f, shelfOpenness(4, 5f), 0f)
        assertEquals(0f, shelfOpenness(6, 5f), 0f)
        assertEquals(0.75f, shelfOpenness(5, 5.25f), 1e-6f)
        assertEquals(0.25f, shelfOpenness(6, 5.25f), 1e-6f)
    }

    @Test
    fun halfwayBothGamesAreHalfOpen() {
        assertEquals(85f, shelfWidth(5, 5.5f, m), 1e-4f)
        assertEquals(85f, shelfWidth(6, 5.5f, m), 1e-4f)
        assertEquals(30f, shelfWidth(7, 5.5f, m), 1e-4f)
    }

    @Test
    fun atTheStartTheFirstCoverSitsAtThePadding() {
        val lefts = shelfLefts(0, 3, 0f, m)
        assertEquals(20f, lefts[0], 1e-4f)
        assertEquals(20f + 140f + 3f, lefts[1], 1e-4f)
        assertEquals(20f + 140f + 3f + 33f, lefts[2], 1e-4f)
    }

    @Test
    fun theFirstGameStaysAtTheEdgeWhileTheSlotMovesIn() {
        for (shown in listOf(0.3f, 1f, 1.7f, 2.5f)) {
            assertEquals("shown $shown", 20f, shelfLefts(0, 0, shown, m)[0], 1e-3f)
        }
    }

    @Test
    fun pastTheStartTheOpenGameHasThreeSpinesBeforeIt() {
        val lefts = shelfLefts(2, 6, 5f, m)
        val slot = 20f + 3 * 33f
        assertEquals(slot, lefts[5 - 2], 1e-4f)
        assertEquals(slot - 33f, lefts[4 - 2], 1e-4f)
        assertEquals(slot - 99f, lefts[2 - 2], 1e-4f)
        assertEquals(slot + 140f + 3f, lefts[6 - 2], 1e-4f)
    }

    @Test
    fun aStepPullsTheNextGameIntoTheSlot() {
        val slot = 20f + 3 * 33f
        assertEquals(slot + 143f, shelfLefts(6, 6, 5f, m)[0], 1e-4f)
        assertEquals(slot, shelfLefts(6, 6, 6f, m)[0], 1e-4f)
        // The game leaving folds one spine to the left of the slot.
        assertEquals(slot - 33f, shelfLefts(5, 5, 6f, m)[0], 1e-4f)
    }

    @Test
    fun everyEdgeMovesContinuously() {
        // Just below and at each whole position, including the lead, the
        // edges agree: no jump when the anchor game changes.
        for (whole in listOf(1, 2, 3, 4, 10)) {
            val below = shelfLefts(0, 14, whole - 0.0005f, m)
            val at = shelfLefts(0, 14, whole.toFloat(), m)
            for (k in below.indices) {
                assertTrue("game $k at $whole: ${below[k]} vs ${at[k]}", abs(below[k] - at[k]) < 0.2f)
            }
        }
    }

    @Test
    fun gamesNeverOverlap() {
        var shown = 0f
        while (shown <= 12f) {
            val lefts = shelfLefts(0, 14, shown, m)
            for (k in 0 until lefts.size - 1) {
                val right = lefts[k] + shelfWidth(k, shown, m)
                assertEquals("shown $shown game $k", lefts[k + 1], right + m.gap, 1e-3f)
            }
            shown += 0.125f
        }
    }

    @Test
    fun aLongerJumpTakesLongerUpToACap() {
        assertEquals(160, stepMillis(1f))
        assertEquals(160, stepMillis(0.4f))
        assertTrue(stepMillis(3f) > stepMillis(1f))
        assertEquals(420, stepMillis(40f))
    }

    // ---- The page fits without scrolling ----

    private fun fit(height: Float, hasCollections: Boolean = true) = libraryFit(
        height = height, fixed = 140f, titleLine = 38f, collections = 96f, status = 30f, actions = 60f,
        hasCollections = hasCollections, minShelf = 190f
    )

    @Test
    fun aTallPageShowsEverything() {
        assertEquals(LibraryFit(2, collections = true, status = true), fit(800f))
    }

    @Test
    fun theCatalogLineGoesFirst() {
        // 200 + 76 + 96 + 30 + 190 = 592 needed for everything.
        assertEquals(LibraryFit(2, collections = true, status = false), fit(580f))
    }

    @Test
    fun thenTheTitlesSecondLine() {
        assertEquals(LibraryFit(1, collections = true, status = false), fit(530f))
    }

    @Test
    fun thenTheCollections() {
        assertEquals(LibraryFit(1, collections = false, status = false), fit(450f))
    }

    @Test
    fun lastThePillsMoveBesideTheTitle() {
        assertEquals(LibraryFit(1, collections = false, status = false, inlineActions = true), fit(400f))
        // Nothing left to give: the shelf takes what remains.
        assertEquals(LibraryFit(1, collections = false, status = false, inlineActions = true), fit(200f))
    }

    @Test
    fun noCollectionsLeavesMoreForTheTitle() {
        assertEquals(LibraryFit(2, collections = false, status = true), fit(500f, hasCollections = false))
    }
}
