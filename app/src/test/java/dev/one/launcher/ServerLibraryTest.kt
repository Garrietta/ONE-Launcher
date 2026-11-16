package dev.one.launcher

import dev.one.launcher.data.MatchKind
import dev.one.launcher.data.PortMatcher
import dev.one.launcher.data.RemoteEntry
import dev.one.launcher.model.Port
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Server library section (v1.4.3): remote directory entries matched to
 * a port, detail page only. The matching itself is the existing
 * PortMatcher — these tests pin the server-specific rules around it:
 * folders are never listed, name-only applies to format-less ports,
 * and ports with formats keep their strict matching.
 */
class ServerLibraryTest {

    private val pokopia = Port(
        id = "banananative-compilationfix",
        title = "Pokopia",
        game = "Pokopia",
        system = "Nintendo Switch 2",
        type = "Recompilation",
        repo = "Wojnstup/BananaNative-CompilationFix",
        releasesUrl = "https://github.com/Wojnstup/BananaNative-CompilationFix/releases",
        packageNames = listOf("dev.nxnative.android.pokopia"),
        fileExtensions = emptyList(),
        fileNameHints = listOf("pokopia", "banananative-compilationfix"),
        distinctiveNameHints = listOf("pokopia", "banananative-compilationfix"),
        requiredDump = "Your own Switch 2 game dump as a folder (with base/)."
    )

    private val oot = Port(
        id = "soh",
        title = "Ship of Harkinian",
        game = "The Legend of Zelda: Ocarina of Time",
        system = "Nintendo 64",
        type = "Decompilation",
        repo = "linkzenic/Shipwright-Ports",
        releasesUrl = "https://github.com/linkzenic/Shipwright-Ports/releases",
        packageNames = listOf("com.linkzenic.soh"),
        fileExtensions = listOf(".z64", ".n64", ".v64"),
        fileNameHints = listOf("zelootd", "oot", "ocarina"),
        requiredDump = "Your own Ocarina of Time ROM (.z64)."
    )

    private fun file(name: String, url: String = "http://localhost/$name") =
        RemoteEntry(name = name, url = url, isFolder = false)

    private fun folder(name: String, url: String = "http://localhost/$name/") =
        RemoteEntry(name = name, url = url, isFolder = true)

    private fun magnet(name: String, url: String) =
        RemoteEntry(name = name, url = url, isFolder = false, kind = "magnet")

    private val magnetUri =
        "magnet:?xt=urn:btih:9c6e87aaca8a26f4755d44079d5575b1732428f6" +
            "&dn=Acorn%20-%20Archimedes"

    @Test
    fun pokopiaNupMatchesNameOnly() {
        val entries = listOf(
            file("Pokémon Pokopia [0400A3A01EE4C000][v655360].nup")
        )
        val matched = PortMatcher.matchRemoteEntries(entries, pokopia)
        assertEquals(1, matched.size)
        assertEquals(MatchKind.NAME_ONLY, matched[0].second)
        assertEquals(
            "Pokémon Pokopia [0400A3A01EE4C000][v655360].nup",
            matched[0].first.name
        )
    }

    @Test
    fun pokopiaNupDoesNotMatchOtherPorts() {
        val entries = listOf(
            file("Pokémon Pokopia [0400A3A01EE4C000][v655360].nup")
        )
        assertTrue(PortMatcher.matchRemoteEntries(entries, oot).isEmpty())
    }

    @Test
    fun foldersAreNeverListedEvenWhenTheirNameHits() {
        // A per-game folder named after the port must not appear as a
        // file: the section lists files the user can open, not folders.
        val entries = listOf(folder("Pokémon Pokopia [NUP]"))
        assertTrue(PortMatcher.matchRemoteEntries(entries, pokopia).isEmpty())
    }

    @Test
    fun magnetEntriesNeverMatchAnyPort() {
        // A magnet entry is a copyable link, not a file: it must
        // never appear in the Server library, even when its name
        // ends in a game extension (.zip) or names a port outright.
        // This covers both the isMagnet skip in matchRemoteEntries
        // and the magnet: exclusion in the matching gate.
        val entries = listOf(
            magnet("Acorn - Archimedes", magnetUri),
            magnet("Redump/Microsoft - Xbox 360/007 - Blood Stone (Japan).zip", magnetUri),
            magnet("Pokémon Pokopia [0400A3A01EE4C000][v655360].nup", magnetUri),
            magnet("Legend of Zelda, The - Ocarina of Time (USA).zip", magnetUri)
        )
        assertTrue(PortMatcher.matchRemoteEntries(entries, pokopia).isEmpty())
        assertTrue(PortMatcher.matchRemoteEntries(entries, oot).isEmpty())
    }

    @Test
    fun ootZipStillMatchesSohOnly() {
        val entries = listOf(
            file("Legend of Zelda, The - Ocarina of Time (USA).zip")
        )
        val matched = PortMatcher.matchRemoteEntries(entries, oot)
        assertEquals(1, matched.size)
        assertEquals(MatchKind.ARCHIVE_PROVISIONAL, matched[0].second)
        // The same zip must not match a format-less port by accident.
        assertTrue(PortMatcher.matchRemoteEntries(entries, pokopia).isEmpty())
    }

    @Test
    fun genericSuffixedFileMatchesNothing() {
        val entries = listOf(
            file("Some Random Game (USA).zip"),
            file("Game (USA).nup")
        )
        assertTrue(PortMatcher.matchRemoteEntries(entries, pokopia).isEmpty())
        assertTrue(PortMatcher.matchRemoteEntries(entries, oot).isEmpty())
    }

    @Test
    fun mixedListingKeepsOnlyMatchingFiles() {
        val entries = listOf(
            folder("Pokémon Pokopia [NUP]"),
            file("Pokémon Pokopia [0400A3A01EE4C000][v655360].nup"),
            file("readme.txt"),
            file("Super Mario 64 (USA).zip")
        )
        val matched = PortMatcher.matchRemoteEntries(entries, pokopia)
        assertEquals(1, matched.size)
        assertEquals(MatchKind.NAME_ONLY, matched[0].second)
    }
}
