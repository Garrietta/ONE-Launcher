package dev.one.launcher

import dev.one.launcher.data.CategoryPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Category → platform mapping (v1.8.5 magnet dedup): a feed category
 * names the system its entries belong to ("Microsoft - Xbox",
 * "Nintendo - GameCube - NKit RVZ [zstd-19-128k]", "Sony -
 * PlayStation 2"), and the dedup keeps the entry whose category
 * platform matches the game's platform. The mapping is derived from
 * the live Recompa platforms list and the live remote.json
 * categories (57). Longest phrase first, matched as a word-phrase,
 * so "nintendo ds" beats "ds" and "wii u" beats "wii".
 */
class CategoryPlatformTest {

    @Test
    fun minervaSystemCategoriesMapToPlatforms() {
        assertEquals(
            "wii",
            CategoryPlatform.platformIdForCategory("Nintendo - Wii - NKit RVZ [zstd-19-128k]")
        )
        assertEquals(
            "wiiu",
            CategoryPlatform.platformIdForCategory("Nintendo - Wii U - WUX")
        )
        assertEquals(
            "gamecube",
            CategoryPlatform.platformIdForCategory(
                "Nintendo - GameCube - NKit RVZ [zstd-19-128k]"
            )
        )
        assertEquals(
            "ps1",
            CategoryPlatform.platformIdForCategory("Sony - PlayStation")
        )
        assertEquals(
            "ps2",
            CategoryPlatform.platformIdForCategory("Sony - PlayStation 2")
        )
        assertEquals(
            "ps3",
            CategoryPlatform.platformIdForCategory("Sony - PlayStation 3")
        )
        assertEquals(
            "psp",
            CategoryPlatform.platformIdForCategory("Sony - PlayStation Portable")
        )
        assertEquals(
            "xbox",
            CategoryPlatform.platformIdForCategory("Microsoft - Xbox")
        )
        assertEquals(
            "xbox360",
            CategoryPlatform.platformIdForCategory("Microsoft - Xbox 360")
        )
        assertEquals(
            "dreamcast",
            CategoryPlatform.platformIdForCategory("Sega - Dreamcast")
        )
        assertEquals(
            "n64",
            CategoryPlatform.platformIdForCategory("Nintendo - Nintendo 64 (BigEndian)")
        )
        assertEquals(
            "ds",
            CategoryPlatform.platformIdForCategory("Nintendo - Nintendo DS (Decrypted)")
        )
        assertEquals(
            "3ds",
            CategoryPlatform.platformIdForCategory("Nintendo - Nintendo 3DS (Decrypted)")
        )
        assertEquals(
            "gba",
            CategoryPlatform.platformIdForCategory("Nintendo - Game Boy Advance")
        )
        assertEquals(
            "gbc",
            CategoryPlatform.platformIdForCategory("Nintendo - Game Boy Color")
        )
        assertEquals(
            "gb",
            CategoryPlatform.platformIdForCategory("Nintendo - Game Boy")
        )
        assertEquals(
            "snes",
            CategoryPlatform.platformIdForCategory(
                "Nintendo - Super Nintendo Entertainment System"
            )
        )
        assertEquals(
            "nes",
            CategoryPlatform.platformIdForCategory(
                "Nintendo - Nintendo Entertainment System (Headered)"
            )
        )
    }

    @Test
    fun longerPhraseWinsOverShorter() {
        // "wii u" must beat "wii" — a naive substring search would
        // match "wii" inside "wii u" and pick the wrong platform.
        assertEquals("wiiu", CategoryPlatform.platformIdForCategory("Nintendo - Wii U - WUX"))
        // "playstation 2" must beat "playstation".
        assertEquals("ps2", CategoryPlatform.platformIdForCategory("Sony - PlayStation 2"))
        // "nintendo ds" must beat "ds"; "nintendo 3ds" must beat both.
        assertEquals("ds", CategoryPlatform.platformIdForCategory("Nintendo - Nintendo DS"))
        assertEquals("3ds", CategoryPlatform.platformIdForCategory("Nintendo - Nintendo 3DS"))
        // "game boy advance" must beat "game boy".
        assertEquals("gba", CategoryPlatform.platformIdForCategory("Nintendo - Game Boy Advance"))
        // "xbox 360" must beat "xbox".
        assertEquals("xbox360", CategoryPlatform.platformIdForCategory("Microsoft - Xbox 360"))
        // "switch 2" must beat "switch".
        assertEquals("switch2", CategoryPlatform.platformIdForCategory("Nintendo - Switch 2"))
    }

    @Test
    fun unmappedCategoriesReturnNull() {
        // Sega - Saturn and Sega - Mega CD & Sega CD have no catalog
        // platform id (no ports exist for them), so no preference is
        // needed — the dedup just collapses to the first entry.
        assertNull(CategoryPlatform.platformIdForCategory("Sega - Saturn"))
        assertNull(CategoryPlatform.platformIdForCategory("Sega - Mega CD & Sega CD"))
        // An unknown or absent category degrades to "first", never
        // drops the entry.
        assertNull(CategoryPlatform.platformIdForCategory("Retro & Arcade"))
        assertNull(CategoryPlatform.platformIdForCategory(null))
        assertNull(CategoryPlatform.platformIdForCategory("   "))
    }

    @Test
    fun categoryParsingKeepsCategory() {
        // The JSON parser keeps the "category" field on an entry
        // (v1.8.5) so the dedup can read it; autoindex listings have
        // no category and parse to null.
        val parsed = dev.one.launcher.data.parsePlainFileList(
            "https://example.com/remote.json",
            """[{"name":"007 - Agent Under Fire (USA).zip","url":"magnet:?xt=urn:btih:aaaa&so=1","kind":"magnet","category":"Microsoft - Xbox"}]"""
        ) as dev.one.launcher.data.RemoteListingResult.Listing
        val entry = parsed.entries.single()
        assertEquals("Microsoft - Xbox", entry.category)
        assertEquals("xbox", CategoryPlatform.platformIdForCategory(entry.category))
        // A list without the field parses to a null category.
        val noCategory = dev.one.launcher.data.parsePlainFileList(
            "https://example.com/remote.json",
            """[{"name":"game.zip","url":"https://example.com/game.zip"}]"""
        ) as dev.one.launcher.data.RemoteListingResult.Listing
        assertNull(noCategory.entries.single().category)
    }
}
