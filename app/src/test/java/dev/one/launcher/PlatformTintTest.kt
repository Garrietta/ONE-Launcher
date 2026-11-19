package dev.one.launcher

import dev.one.launcher.data.PlatformTint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformTintTest {

    @Test
    fun knownPlatformsMapThroughCatalogIds() {
        assertEquals(PlatformTint.forPlatform("Switch"), PlatformTint.forPlatform("Nintendo - Switch"))
        assertNotEquals(PlatformTint.forPlatform("Wii"), PlatformTint.forPlatform("Wii U"))
        assertNotEquals(PlatformTint.forPlatform("PlayStation"), PlatformTint.forPlatform("PlayStation 2"))
        assertNotEquals(PlatformTint.forPlatform("Nintendo 64"), PlatformTint.NEUTRAL)
        assertNotEquals(PlatformTint.forPlatform("Super Nintendo"), PlatformTint.forPlatform("Nintendo Entertainment System"))
    }

    @Test
    fun emptyIsNeutralAndUnknownIsStable() {
        assertEquals(PlatformTint.NEUTRAL, PlatformTint.forPlatform(""))
        assertEquals(PlatformTint.NEUTRAL, PlatformTint.forPlatform(null))
        assertEquals(PlatformTint.forPlatform("Neo Geo"), PlatformTint.forPlatform("Neo Geo"))
    }

    @Test
    fun everyTintIsDarkEnoughForWhiteText() {
        val names = listOf(
            "Switch", "Switch 2", "Wii", "Wii U", "GameCube", "Nintendo 64", "Super Nintendo",
            "Nintendo Entertainment System", "Game Boy", "Game Boy Color", "Game Boy Advance",
            "Nintendo DS", "Nintendo 3DS", "PlayStation", "PlayStation 2", "PlayStation 3",
            "PlayStation 4", "PlayStation Portable", "PS Vita", "Xbox", "Xbox 360", "Dreamcast",
            "Mega Drive", "Master System", "32X", "PC", "Android", "Neo Geo", "Atari Jaguar", ""
        )
        names.forEach { name ->
            val l = PlatformTint.luminance(PlatformTint.forPlatform(name))
            // White on this color must reach ~4.5:1 contrast.
            val contrast = 1.05 / (l + 0.05)
            assertTrue("$name contrast $contrast", contrast >= 4.5)
        }
    }
}
