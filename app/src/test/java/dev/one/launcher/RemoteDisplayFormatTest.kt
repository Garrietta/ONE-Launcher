package dev.one.launcher

import dev.one.launcher.data.FindReplace
import dev.one.launcher.data.RemoteDisplayFormat
import dev.one.launcher.data.RemoteEntry
import dev.one.launcher.data.formatRemoteTitle
import dev.one.launcher.data.normalizeHiddenExtensions
import dev.one.launcher.data.parseHiddenExtensions
import dev.one.launcher.data.shouldShowRemoteEntry
import dev.one.launcher.data.splitRemoteExtension
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteDisplayFormatTest {

    private val pokopia = "Pokémon Pokopia [0400A3A01EE4C000][v655360].nup"

    @Test
    fun defaultsShowEverythingUnchanged() {
        val format = RemoteDisplayFormat()
        assertFalse(format.stripBrackets)
        assertFalse(format.stripParens)
        assertFalse(format.underscoresToSpaces)
        assertTrue(format.replacements.isEmpty())
        assertTrue(format.showSize)
        assertTrue(format.hideExtensions.isEmpty())
        val result = formatRemoteTitle(pokopia, isFolder = false, format)
        assertEquals(pokopia, result.title + "." + result.extension)
    }

    @Test
    fun stripBracketsRemovesMultipleAdjacentSegments() {
        val format = RemoteDisplayFormat(stripBrackets = true)
        val result = formatRemoteTitle(pokopia, isFolder = false, format)
        assertEquals("Pokémon Pokopia", result.title)
        assertEquals("nup", result.extension)
    }

    @Test
    fun stripParensRemovesShortTagsButKeepsLongOnes() {
        val format = RemoteDisplayFormat(stripParens = true)
        assertEquals(
            "Game",
            formatRemoteTitle("Game (USA).zip", isFolder = false, format).title
        )
        assertEquals(
            "Game",
            formatRemoteTitle("Game (USA) (v2).zip", isFolder = false, format).title
        )
        // Longer than a tag: part of the title, kept.
        assertEquals(
            "Game (The Legend Continues Forever)",
            formatRemoteTitle(
                "Game (The Legend Continues Forever).zip", isFolder = false, format
            ).title
        )
    }

    @Test
    fun underscoresBecomeSpaces() {
        val format = RemoteDisplayFormat(underscoresToSpaces = true)
        assertEquals(
            "Super Mario 64",
            formatRemoteTitle("Super_Mario_64.zip", isFolder = false, format).title
        )
    }

    @Test
    fun findReplaceIsLiteralAndOrdered() {
        // "[v1.0]" contains regex characters — it must match literally, not as a pattern.
        val format = RemoteDisplayFormat(
            replacements = listOf(
                FindReplace("[v1.0]", ""),
                FindReplace("foo", "bar"),
                FindReplace("bar", "baz")
            )
        )
        assertEquals(
            "Game",
            formatRemoteTitle("Game [v1.0].zip", isFolder = false, format).title
        )
        // Pairs apply in order: foo -> bar, then bar -> baz.
        assertEquals(
            "baz",
            formatRemoteTitle("foo.zip", isFolder = false, format).title
        )
        // An empty find string never matches.
        val emptyFind = RemoteDisplayFormat(replacements = listOf(FindReplace("", "x")))
        assertEquals(
            "Game",
            formatRemoteTitle("Game.zip", isFolder = false, emptyFind).title
        )
    }

    @Test
    fun whitespaceCollapsesAndTrims() {
        val format = RemoteDisplayFormat(stripBrackets = true)
        assertEquals(
            "Game Title",
            formatRemoteTitle("  Game   [x]  Title  .zip", isFolder = false, format).title
        )
    }

    @Test
    fun titleThatStripsToNothingFallsBackToOriginalStem() {
        val format = RemoteDisplayFormat(stripBrackets = true)
        // A row must never render blank.
        assertEquals(
            "[v1]",
            formatRemoteTitle("[v1].nup", isFolder = false, format).title
        )
    }

    @Test
    fun extensionSplitRules() {
        assertEquals("archive.tar" to "gz", splitRemoteExtension("archive.tar.gz"))
        assertEquals("game" to "zip", splitRemoteExtension("game.zip"))
        // A leading dot is a hidden file, not an extension.
        assertEquals(".hidden" to null, splitRemoteExtension(".hidden"))
        assertEquals("noext" to null, splitRemoteExtension("noext"))
        assertEquals("trailing." to null, splitRemoteExtension("trailing."))
        // Folders keep their full name as the title — no extension chip.
        val folder = formatRemoteTitle("My Folder.v2", isFolder = true, RemoteDisplayFormat())
        assertEquals("My Folder.v2", folder.title)
        assertNull(folder.extension)
    }

    @Test
    fun rulesApplyInOrder() {
        // Brackets stripped first, then underscores, then replacements.
        val format = RemoteDisplayFormat(
            stripBrackets = true,
            underscoresToSpaces = true,
            replacements = listOf(FindReplace("Pokopia", "Pokopia!"))
        )
        assertEquals(
            "Pokémon Pokopia!",
            formatRemoteTitle("Pokémon_Pokopia [x].nup", isFolder = false, format).title
        )
    }

    @Test
    fun hideTypesIsCaseInsensitiveAndDotOptional() {
        val format = RemoteDisplayFormat(hideExtensions = listOf("txt", ".NFO", " md "))
        val txt = RemoteEntry("readme.txt", "http://x/readme.txt", false)
        val nfo = RemoteEntry("INFO.NFO", "http://x/INFO.NFO", false)
        val nup = RemoteEntry("game.nup", "http://x/game.nup", false)
        val folder = RemoteEntry("docs", "http://x/docs/", true)
        assertFalse(shouldShowRemoteEntry(txt, format))
        assertFalse(shouldShowRemoteEntry(nfo, format))
        assertTrue(shouldShowRemoteEntry(nup, format))
        // Folders are never hidden.
        assertTrue(shouldShowRemoteEntry(folder, format))
        assertTrue(shouldShowRemoteEntry(txt, RemoteDisplayFormat()))
    }

    @Test
    fun parseHiddenExtensionsNormalizes() {
        assertEquals(
            listOf("txt", "nfo", "md"),
            parseHiddenExtensions("txt, .NFO, md, txt, , ")
        )
        assertEquals(emptyList<String>(), parseHiddenExtensions(""))
        assertEquals(
            listOf("txt", "nfo"),
            normalizeHiddenExtensions(listOf(" TXT ", ".nfo", "", "."))
        )
    }

    @Test
    fun displayFormatRoundTripsThroughJson() {
        // What DataStore stores must decode back identically, including
        // old sources that carry no displayFormat key at all.
        val json = Json { ignoreUnknownKeys = true }
        val format = RemoteDisplayFormat(
            stripBrackets = true,
            replacements = listOf(FindReplace("v655360", "")),
            hideExtensions = listOf("txt")
        )
        val decoded = json.decodeFromString<RemoteDisplayFormat>(
            json.encodeToString(format)
        )
        assertEquals(format, decoded)
        val legacy = json.decodeFromString<RemoteDisplayFormat>("{}")
        assertEquals(RemoteDisplayFormat(), legacy)
    }
}
