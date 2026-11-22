package dev.one.launcher

import dev.one.launcher.data.PlatformBox
import dev.one.launcher.ui.shelfScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformBoxTest {

    private fun box(name: String) = PlatformBox.forPlatform(name)

    @Test
    fun aHandheldCaseIsShorterThanAPlayStationCase() {
        assertTrue(box("Nintendo 3DS").height < box("PlayStation 2").height)
        assertTrue(box("Nintendo DS").height < box("PlayStation 4").height)
        assertTrue(box("PlayStation Vita").height < box("PlayStation Portable").height)
    }

    @Test
    fun switchIsAsTallAsAPs4CaseButNarrower() {
        assertEquals(box("PlayStation 4").height, box("Switch").height, 0f)
        assertTrue(box("Switch").width < box("PlayStation 4").width)
        assertEquals(box("Switch"), box("Switch 2"))
    }

    @Test
    fun cartridgeBoxesOfTheNinetiesAreLandscape() {
        assertTrue(box("Nintendo 64").aspect > 1f)
        assertTrue(box("Super Nintendo").aspect > 1f)
        assertTrue(box("Nintendo Entertainment System").aspect < 1f)
    }

    @Test
    fun discGamesUseTheirCase() {
        assertEquals(PlatformBox.JEWEL, box("PlayStation"))
        assertEquals(PlatformBox.DVD, box("PlayStation 2"))
        assertEquals(PlatformBox.BLU_RAY, box("PlayStation 3"))
        assertEquals(PlatformBox.BLU_RAY, box("PlayStation 5"))
        assertEquals(PlatformBox.BLU_RAY, box("Xbox One"))
        assertEquals(PlatformBox.DVD, box("Xbox 360"))
        assertEquals(PlatformBox.DVD, box("GameCube"))
        assertEquals(PlatformBox.BLU_RAY, box("Wii U"))
        assertEquals(PlatformBox.DVD, box("Wii"))
        assertEquals(PlatformBox.JEWEL, box("Dreamcast"))
    }

    @Test
    fun bigBoxPcGamesAreTheTallest() {
        assertEquals(PlatformBox.BIG_BOX, box("DOS"))
        assertEquals(PlatformBox.BIG_BOX, box("Amiga"))
        assertTrue(box("DOS").height > box("PlayStation 2").height)
    }

    @Test
    fun anythingElseGetsADvdCase() {
        assertEquals(PlatformBox.DVD, box("PC"))
        assertEquals(PlatformBox.DVD, box("Arcade"))
        assertEquals(PlatformBox.DVD, box(""))
        assertEquals(PlatformBox.DVD, PlatformBox.forPlatform(null))
    }

    @Test
    fun theTallestBoxFillsTheShelfUnlessTheWidestCoverWouldNotFit() {
        // 200 px of shelf, a 235 mm big box the tallest: 0.851 px/mm.
        assertEquals(200f / 235f, shelfScale(heightPx = 200f, coverCapPx = 400f, tallestMm = 235f, widestMm = 185f), 1e-6f)
        // A narrow window: the 185 mm SNES box has to fit in 120 px.
        assertEquals(120f / 185f, shelfScale(heightPx = 200f, coverCapPx = 120f, tallestMm = 235f, widestMm = 185f), 1e-6f)
    }
}
