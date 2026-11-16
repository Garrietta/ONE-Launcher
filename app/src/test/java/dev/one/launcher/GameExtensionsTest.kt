package dev.one.launcher

import dev.one.launcher.data.ALL_GAME_EXTENSIONS
import dev.one.launcher.data.GAME_FILE_EXTENSIONS
import dev.one.launcher.data.RemoteEntry
import dev.one.launcher.data.RemoteListingResult
import dev.one.launcher.data.extensionOf
import dev.one.launcher.data.extensionsForPlatform
import dev.one.launcher.data.filterRemoteEntriesToGameFiles
import dev.one.launcher.data.isArchiveExtension
import dev.one.launcher.data.isGameFile
import dev.one.launcher.data.isGameFileExtension
import dev.one.launcher.data.isGameFileRemoteEntry
import dev.one.launcher.data.parsePlainFileList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Platform -> game-extensions dictionary and the game-file gate
 * (v1.6.1). The dictionary is seeded from the live Recompa catalog's
 * accepted formats per platform (verified 2026-11-16); the snapshot
 * test below pins the remote-folder filter against the live
 * remote.json as of the same day.
 */
class GameExtensionsTest {

    @Test
    fun dictionaryCoversEveryCatalogPlatform() {
        // Every id in the live catalog's platforms list (28, verified
        // 2026-11-16) has an entry. "other" is explicit empty — the
        // union applies at lookup time, not in the map.
        val platformIds = listOf(
            "n64", "gamecube", "wiiu", "wii", "ds", "switch", "gba",
            "gbc", "gb", "snes", "nes", "genesis", "32x", "ps1", "ps2",
            "ps3", "psp", "xbox360", "xbox", "amiga", "pc", "dos",
            "ps4", "3ds", "switch2", "mastersystem", "dreamcast", "other"
        )
        assertEquals(28, platformIds.size)
        for (id in platformIds) {
            assertTrue("missing dictionary entry for $id", id in GAME_FILE_EXTENSIONS)
        }
        assertTrue(GAME_FILE_EXTENSIONS["other"]!!.isEmpty())
        // And the seeded values are the catalog's accepted formats.
        assertEquals(setOf("n64", "v64", "z64"), GAME_FILE_EXTENSIONS["n64"])
        assertEquals(setOf("nds"), GAME_FILE_EXTENSIONS["ds"])
        assertEquals(setOf("gb", "gba", "gbc"), GAME_FILE_EXTENSIONS["gb"])
        assertEquals(setOf("hog", "pig"), GAME_FILE_EXTENSIONS["pc"])
        assertEquals(setOf("sfc"), GAME_FILE_EXTENSIONS["snes"])
        assertEquals(setOf("xbe"), GAME_FILE_EXTENSIONS["xbox"])
        assertEquals(setOf("xex"), GAME_FILE_EXTENSIONS["xbox360"])
        assertEquals(setOf("32x"), GAME_FILE_EXTENSIONS["32x"])
        assertEquals(setOf("iso"), GAME_FILE_EXTENSIONS["ps2"])
        assertEquals(setOf("wad", "rvz", "wbfs", "gcz"), GAME_FILE_EXTENSIONS["wii"])
        assertEquals(setOf("nup"), GAME_FILE_EXTENSIONS["switch2"])
        assertEquals(setOf("sms"), GAME_FILE_EXTENSIONS["mastersystem"])
        assertEquals(setOf("gdi", "cdi", "chd"), GAME_FILE_EXTENSIONS["dreamcast"])
        assertTrue(ALL_GAME_EXTENSIONS.containsAll(listOf("z64", "nds", "nup", "pkg", "cia")))
    }

    @Test
    fun extensionOfExtractsExtensions() {
        assertEquals("z64", extensionOf("file.z64"))
        assertEquals("z64", extensionOf("FILE.Z64"))
        assertEquals("7z", extensionOf("game.7z"))
        assertEquals("zip", extensionOf("https://example.com/a/b.zip/"))
        assertEquals("zip", extensionOf("https://example.com/f.zip?x=1"))
        assertEquals("org", extensionOf("Microsoft Xbox - A - Redump.org"))
        // A URL's host is not a file extension.
        assertNull(extensionOf("https://www.myabandonware.com/"))
        assertNull(extensionOf("http://www.ovagames.com/"))
        assertNull(extensionOf("https://example.com/download/x"))
        // A dot in the middle of a name is not an extension.
        assertNull(extensionOf("New Super Mario Bros. Wii (Wii) (USA)"))
        assertNull(extensionOf("eXoWin3 2.0"))
        assertNull(extensionOf("file"))
        assertNull(extensionOf("file."))
        assertNull(extensionOf(".z64"))
        assertNull(extensionOf(""))
    }

    @Test
    fun gameFileExtensionAcceptsGameFormats() {
        // Every format the brief names, on its own platform and on
        // the union (unknown platform).
        val cases = listOf(
            "z64" to "n64", "nds" to "ds", "iso" to "ps2", "nsp" to "switch",
            "nup" to "switch2", "pkg" to "ps4", "cia" to "3ds",
            "xbe" to "xbox", "xex" to "xbox360"
        )
        for ((ext, platform) in cases) {
            assertTrue("$ext on $platform", isGameFileExtension(ext, platform))
            assertTrue("$ext on union", isGameFileExtension(ext, null))
            assertTrue("$ext for $platform", isGameFile("game.$ext", platform))
        }
        // Archives are platform-agnostic.
        for (ext in listOf("zip", "7z", "rar")) {
            assertTrue(ext, isGameFileExtension(ext, "n64"))
            assertTrue(ext, isGameFileExtension(ext, "other"))
            assertTrue(ext, isGameFile("game.$ext", "ds"))
        }
        assertTrue(isArchiveExtension("zip"))
        assertTrue(isArchiveExtension("ZIP"))
        assertFalse(isArchiveExtension("mkv"))
    }

    @Test
    fun gameFileExtensionRejectsNonGameFormats() {
        for (ext in listOf("mkv", "mp4", "txt", "org", "html")) {
            assertFalse("$ext on n64", isGameFileExtension(ext, "n64"))
            assertFalse("$ext on union", isGameFileExtension(ext, null))
            assertFalse("$ext for ds", isGameFile("file.$ext", "ds"))
        }
        assertFalse(isGameFile("noextension", "n64"))
        assertFalse(isGameFile("noextension", null))
        assertFalse(isGameFile("", null))
    }

    @Test
    fun gameFileExtensionIsPlatformSpecific() {
        // .nds is a DS format, not an N64 one.
        assertTrue(isGameFile("game.nds", "ds"))
        assertFalse(isGameFile("game.nds", "n64"))
        assertTrue(isGameFile("game.iso", "ps2"))
        assertFalse(isGameFile("game.iso", "n64"))
        // "other", an unknown id, and a blank platform all use the
        // union across platforms.
        assertTrue(isGameFile("game.nds", "other"))
        assertTrue(isGameFile("game.nup", "other"))
        assertTrue(isGameFile("game.nds", "unknown-platform"))
        assertTrue(isGameFile("game.nds", ""))
        assertTrue(isGameFile("game.nds", null))
        // But the union never admits a non-game format.
        assertFalse(isGameFile("movie.mkv", "other"))
        assertFalse(isGameFile("movie.mkv", null))
        assertEquals(ALL_GAME_EXTENSIONS, extensionsForPlatform("other"))
        assertEquals(ALL_GAME_EXTENSIONS, extensionsForPlatform("unknown-platform"))
        assertEquals(ALL_GAME_EXTENSIONS, extensionsForPlatform(null))
        assertEquals(setOf("nds"), extensionsForPlatform("ds"))
    }

    @Test
    fun isGameFileReadsNameOrUrl() {
        // The Eidos case: the name has no extension, the URL ends
        // in ".zip" — either side saying "game file" is enough.
        assertTrue(
            isGameFile(
                "Pokemon Black (NDS) (USA)", "ds",
                "https://archive.org/download/pkmn_collection/NDS/Pokemon%20-%20Black%20Version%20%28USA%29.zip"
            )
        )
        // A name with a dot in it but no extension still reads the URL.
        assertTrue(
            isGameFile(
                "New Super Mario Bros. Wii (Wii) (USA)", "wii",
                "https://archive.org/download/Wii-Redump-2024-Individual-Games-Part4/New%20Super%20Mario%20Bros.%20Wii%20%28USA%29.zip"
            )
        )
        // A Redump index: no extension on either side.
        assertFalse(
            isGameFile(
                "Microsoft Xbox - A - Redump", "xbox",
                "https://example.com/download/microsoft_xbox_a"
            )
        )
        // Site links: the ".com" is the host, not a file extension —
        // even though "com" is a DOS extension.
        assertFalse(isGameFile("MyAbandonware", null, "https://www.myabandonware.com/"))
        assertFalse(isGameFile("Ovagames", null, "http://www.ovagames.com/"))
        assertFalse(isGameFile("VetusWare", null, "https://vetusware.com/"))
        // And a real DOS .com file still counts.
        assertTrue(isGameFile("game.com", "dos"))
    }

    @Test
    fun remoteBrowserFilterKeepsFoldersAndGameFilesOnly() {
        val entries = listOf(
            RemoteEntry("My Games", "https://example.com/games/", true),
            RemoteEntry("Pokemon Black (NDS) (USA)", "https://example.com/pokemon-black.zip", false),
            RemoteEntry("Ocarina of Time.z64", "https://example.com/oot.z64", false),
            RemoteEntry("The Last Airbender (2010).mkv", "https://example.com/airbender.mkv", false),
            RemoteEntry("Microsoft Xbox - A - Redump", "https://example.com/download/microsoft_xbox_a", false),
            RemoteEntry("[TOSEC-ISO] 3DO", "https://example.com/download/tosec_3do", false),
            RemoteEntry("readme.txt", "https://example.com/readme.txt", false),
            RemoteEntry("index.html", "https://example.com/index.html", false)
        )
        val shown = filterRemoteEntriesToGameFiles(entries)
        assertEquals(
            listOf("My Games", "Ocarina of Time.z64", "Pokemon Black (NDS) (USA)"),
            shown.map { it.name }.sorted()
        )
        // Folders are navigation, never filtered.
        assertTrue(isGameFileRemoteEntry(entries[0]))
        assertFalse(isGameFileRemoteEntry(entries[3]))
        assertFalse(isGameFileRemoteEntry(entries[4]))
    }

    @Test
    fun liveRemoteJsonFiltersTo73GameFiles() {
        // Snapshot of the live remote.json (Garrett's Pages file) as
        // of 2026-11-16: 376 entries. The browser shows only the 73
        // game files (70 .zip + 3 .7z, all archives) and hides 303 —
        // 301 with no URL extension (Redump, TOSEC, collections,
        // firmware, site links) plus 2 .html. The brief's "301
        // hidden" counted only the no-extension entries; the .html
        // two are hidden as well.
        val text = javaClass.classLoader
            ?.getResourceAsStream("remote-2026-11-16.json")
            ?.bufferedReader()?.readText()
            ?: error("remote-2026-11-16.json test resource missing")
        val result = parsePlainFileList(
            "https://garrietta.github.io/ONE-Launcher/remote.json", text
        )
        val entries = (result as RemoteListingResult.Listing).entries
        assertEquals(376, entries.size)
        val shown = filterRemoteEntriesToGameFiles(entries)
        assertEquals(73, shown.size)
        assertEquals(303, entries.size - shown.size)
        // Every shown entry is an archive.
        assertTrue(shown.all { isArchiveExtension(extensionOf(it.url) ?: "") })
        // The real game files survive.
        assertTrue(shown.any { it.name == "Pokemon Black (NDS) (USA)" })
        assertTrue(shown.any { it.name == "Pokemon Black 2 (NDS) (USA)" })
        assertTrue(shown.any { it.name == "Metroid Prime (GC) (USA)" })
        assertTrue(shown.any { it.name == "New Super Mario Bros. Wii (Wii) (USA)" })
        // The odd entries are gone.
        assertTrue(shown.none { "Redump" in it.name })
        assertTrue(shown.none { it.name.startsWith("[TOSEC-ISO]") })
        assertTrue(shown.none { it.name == "3DS Content in CIA Format" })
        assertTrue(shown.none { it.name == "MyAbandonware" })
        assertTrue(shown.none { it.name == "Firmwares" })
        assertTrue(shown.none { it.name == "Minerva Minerva Torrent Index" })
    }
}
