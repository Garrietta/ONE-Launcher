package dev.one.launcher

import dev.one.launcher.data.MatchKind
import dev.one.launcher.data.PortMatcher
import dev.one.launcher.model.Port
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PortMatcherTest {

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

    @Test
    fun matchesOotRom() {
        assertTrue(PortMatcher.matches("ZELDA OOT.z64", oot))
    }

    @Test
    fun matchesCaseInsensitiveHint() {
        assertTrue(PortMatcher.matches("OcarinaOfTime.n64", oot))
    }

    @Test
    fun rejectsWrongExtension() {
        assertFalse(PortMatcher.matches("oot.sfc", oot))
    }

    @Test
    fun rejectsUnrelatedName() {
        assertFalse(PortMatcher.matches("mario.z64", oot))
    }

    @Test
    fun matchingPortsFindsCandidate() {
        val ports = listOf(oot)
        assertTrue(PortMatcher.matchingPorts("zelootd.z64", ports).size == 1)
        assertTrue(PortMatcher.matchingPorts("mario.z64", ports).isEmpty())
    }

    // ---- Archive matching (v1.1) ----

    @Test
    fun zipNamedLikeGarrettsOotFileMatchesSohAsProvisionalArchive() {
        val kind = PortMatcher.matchKind(
            "Legend of Zelda, The - Ocarina of Time (USA).zip", oot
        )
        assertEquals(MatchKind.ARCHIVE_PROVISIONAL, kind)
        assertTrue(
            PortMatcher.matchingPorts(
                "Legend of Zelda, The - Ocarina of Time (USA).zip", listOf(oot)
            ).size == 1
        )
    }

    @Test
    fun zipWithNoHintDoesNotMatch() {
        assertNull(PortMatcher.matchKind("mario-kart.zip", oot))
        assertTrue(
            PortMatcher.matchingPorts("mario-kart.zip", listOf(oot)).isEmpty()
        )
    }

    // ---- False-positive matches (v1.3.1) ----

    // Hints exactly as hintsFromGameTitle("Daytona USA", "daytona-usa")
    // produces after the generic-token filter: "usa" is a region tag,
    // never a hint by itself.
    private val daytona = Port(
        id = "daytona-usa",
        title = "Daytona USA",
        game = "Daytona USA",
        system = "Sega Saturn",
        type = "Recompilation",
        repo = "example/daytona",
        releasesUrl = "https://github.com/example/daytona/releases",
        packageNames = listOf("com.example.daytona"),
        fileExtensions = listOf(".cue", ".bin"),
        fileNameHints = listOf("daytona", "daytona-usa"),
        requiredDump = "Your own Daytona USA disc image."
    )

    private val alttpCatalog = Port(
        id = "moonpearl",
        title = "The Legend of Zelda: A Link to the Past",
        game = "The Legend of Zelda: A Link to the Past",
        system = "Super Nintendo",
        type = "Recompilation",
        repo = "example/moonpearl",
        releasesUrl = "https://github.com/example/moonpearl/releases",
        packageNames = emptyList(),
        fileExtensions = listOf(".sfc", ".smc"),
        fileNameHints = listOf("link", "past", "moonpearl"),
        requiredDump = "Your own A Link to the Past ROM."
    )

    private val oraclesCatalog = Port(
        id = "oracles",
        title = "Oracles",
        game = "The Legend of Zelda: Oracle of Ages",
        system = "Game Boy Color",
        type = "Recompilation",
        repo = "example/oracles",
        releasesUrl = "https://github.com/example/oracles/releases",
        packageNames = emptyList(),
        fileExtensions = listOf(".gbc"),
        fileNameHints = listOf("oracle", "ages", "oracles"),
        requiredDump = "Your own Oracle of Ages ROM."
    )

    // ---- Name-only matching for format-less ports (v1.4.1) ----

    // Mirrors the real catalog entry (id banananative-compilationfix):
    // one manual asset, an empty accepted list, no formats at all.
    // Hints exactly as hintsFromGameTitle("Pokopia",
    // "banananative-compilationfix") produces.
    private val pokopia = Port(
        id = "banananative-compilationfix",
        title = "Pokopia",
        game = "Pokopia",
        system = "Switch 2",
        type = "Recompilation",
        repo = "Wojnstup/BananaNative-CompilationFix",
        releasesUrl = "https://github.com/Wojnstup/BananaNative-CompilationFix/releases",
        packageNames = listOf("dev.nxnative.android.pokopia"),
        fileExtensions = emptyList(),
        fileNameHints = listOf("pokopia", "banananative-compilationfix"),
        requiredDump = "Your Switch 2 game dump as a folder (with base/)."
    )

    private val bananza = Port(
        id = "donkey-kong-bananza",
        title = "Donkey Kong Bananza",
        game = "Donkey Kong Bananza",
        system = "Switch 2",
        type = "Recompilation",
        repo = "example/bananza",
        releasesUrl = "https://github.com/example/bananza/releases",
        packageNames = emptyList(),
        fileExtensions = emptyList(),
        fileNameHints = listOf("donkey", "kong", "bananza", "donkey-kong-bananza"),
        requiredDump = "Your own Donkey Kong Bananza game dump."
    )

    @Test
    fun nupMatchesPokopiaByNameOnly() {
        val ports = listOf(oot, daytona, alttpCatalog, oraclesCatalog, pokopia)
        val kinds = PortMatcher.matchKinds(
            "Pokémon Pokopia [0400A3A01EE4C000][v655360].nup", ports
        )
        assertEquals(1, kinds.size)
        assertEquals("banananative-compilationfix", kinds.single().port.id)
        assertEquals(MatchKind.NAME_ONLY, kinds.single().kind)
        // The strict extension check still refuses it: the match is
        // name-only, never a verified format. No extension is invented.
        assertFalse(
            PortMatcher.matches(
                "Pokémon Pokopia [0400A3A01EE4C000][v655360].nup", pokopia
            )
        )
    }

    @Test
    fun nameOnlyFallbackAppliesOnlyToFormatLessPorts() {
        // A file named after another format-less port matches that port
        // only, and the Pokopia file does not match it.
        val kinds = PortMatcher.matchKinds(
            "Donkey Kong Bananza [0100B3D01F2C000][v0].nup",
            listOf(pokopia, bananza)
        )
        assertEquals(1, kinds.size)
        assertEquals("donkey-kong-bananza", kinds.single().port.id)
        assertEquals(MatchKind.NAME_ONLY, kinds.single().kind)
        assertTrue(
            PortMatcher.matchKinds(
                "Pokémon Pokopia [0400A3A01EE4C000][v655360].nup",
                listOf(bananza)
            ).isEmpty()
        )
    }

    @Test
    fun genericTokensMatchNothingEvenForFormatLessPorts() {
        val ports = listOf(pokopia, oot, daytona)
        assertTrue(
            PortMatcher.matchKinds("Some Random Game (USA).zip", ports).isEmpty()
        )
        assertTrue(
            PortMatcher.matchKinds("Game (USA).nup", listOf(pokopia)).isEmpty()
        )
    }

    @Test
    fun ootZipMatchesSohOnlyNotDaytonaUsa() {
        val ports = listOf(oot, daytona, alttpCatalog, oraclesCatalog)
        val kinds = PortMatcher.matchKinds(
            "Legend of Zelda, The - Ocarina of Time (USA).zip", ports
        )
        // v1.3.0 returned 9 matches here (Daytona via "usa", other Zelda
        // ports via "legend"/"zelda", Project 2099 via "time", Phantasy
        // Star IV via "end" inside "legend"). Only distinctive tokens
        // count now.
        assertEquals(1, kinds.size)
        assertEquals("soh", kinds.single().port.id)
        assertEquals(MatchKind.ARCHIVE_PROVISIONAL, kinds.single().kind)
    }

    @Test
    fun usaSuffixedFileDoesNotMatchDaytonaUsa() {
        assertNull(PortMatcher.matchKind("Some Random Game (USA).zip", daytona))
        assertTrue(
            PortMatcher.matchKinds("Some Random Game (USA).zip", listOf(daytona)).isEmpty()
        )
        // The file's own game still matches when its distinctive token is there.
        assertEquals(
            MatchKind.ARCHIVE_PROVISIONAL,
            PortMatcher.matchKind("Daytona USA (USA).zip", daytona)
        )
    }

    @Test
    fun shortHintsNeverMatchAsWordPrefixes() {
        val dayPort = Port(
            id = "some-day-game",
            title = "Some Day Game",
            game = "Some Day Game",
            system = "PC",
            type = "Recompilation",
            repo = "example/day",
            releasesUrl = "",
            packageNames = emptyList(),
            fileExtensions = listOf(".bin"),
            fileNameHints = listOf("day"),
            requiredDump = "Your own files."
        )
        // "day" must not match inside "daytona".
        assertNull(PortMatcher.matchKind("daytona.zip", dayPort))
        // ...but it still matches as a whole word.
        assertEquals(
            MatchKind.ARCHIVE_PROVISIONAL,
            PortMatcher.matchKind("some-day-game.zip", dayPort)
        )
        // "mm" (2 letters) must not match inside "mmo".
        val twoShip = Port(
            id = "2ship",
            title = "2Ship2Harkinian",
            game = "The Legend of Zelda: Majora's Mask",
            system = "Nintendo 64",
            type = "Decompilation",
            repo = "linkzenic/2ship2harkinian-Android",
            releasesUrl = "",
            packageNames = listOf("com.dishii.mm"),
            fileExtensions = listOf(".z64", ".n64"),
            fileNameHints = listOf("mm", "majora"),
            requiredDump = "Your own Majora's Mask ROM."
        )
        assertFalse(PortMatcher.matches("mmo-game.z64", twoShip))
        assertTrue(PortMatcher.matches("mm.z64", twoShip))
    }

    @Test
    fun longHintsMayMatchAsWordPrefixes() {
        val starship = Port(
            id = "starship",
            title = "Starship",
            game = "Star Fox 64",
            system = "Nintendo 64",
            type = "Decompilation",
            repo = "izzy2lost/Starship",
            releasesUrl = "",
            packageNames = emptyList(),
            fileExtensions = listOf(".z64"),
            fileNameHints = listOf("starfox", "star fox", "sf64"),
            requiredDump = "Your own Star Fox 64 ROM."
        )
        assertTrue(PortMatcher.matches("starfox64.z64", starship))
        assertTrue(PortMatcher.matches("Star Fox 64.z64", starship))
    }

    @Test
    fun substringHintsDoNotMatchInsideOtherWords() {
        val phantasyStar = Port(
            id = "phantasy-star-iv",
            title = "Phantasy Star IV: The End of the Millenium",
            game = "Phantasy Star IV: The End of the Millenium",
            system = "Mega Drive",
            type = "Recompilation",
            repo = "example/ps4",
            releasesUrl = "",
            packageNames = emptyList(),
            fileExtensions = listOf(".bin", ".md"),
            fileNameHints = listOf("phantasy", "star", "end", "millenium"),
            requiredDump = "Your own Phantasy Star IV ROM."
        )
        // "end" must not match inside "legend", and "star" (4 letters)
        // is not a prefix match either.
        assertNull(
            PortMatcher.matchKind(
                "Legend of Zelda, The - Ocarina of Time (USA).zip", phantasyStar
            )
        )
    }

    @Test
    fun sevenZipAndRarAreProvisionalOnly() {
        assertEquals(
            MatchKind.ARCHIVE_PROVISIONAL,
            PortMatcher.matchKind("Ocarina of Time.7z", oot)
        )
        assertEquals(
            MatchKind.ARCHIVE_PROVISIONAL,
            PortMatcher.matchKind("Ocarina of Time.rar", oot)
        )
    }

    @Test
    fun directRomStillMatchesDirectly() {
        assertEquals(MatchKind.DIRECT, PortMatcher.matchKind("ZELDA OOT.z64", oot))
    }

    @Test
    fun unrelatedZipDoesNotMatch() {
        assertNull(PortMatcher.matchKind("Star Fox 64.zip", oot))
    }

    @Test
    fun zipContainingMatchingRomIsConfirmed() {
        val zip = zipWithEntries("Ocarina of Time.z64" to "fake-rom-bytes")
        assertEquals(
            "Ocarina of Time.z64",
            PortMatcher.inspectZip(zip, oot)
        )
    }

    @Test
    fun zipWithNoMatchingEntryStaysProvisional() {
        val zip = zipWithEntries("readme.txt" to "hello", "mario.z64" to "nope")
        assertNull(PortMatcher.inspectZip(zip, oot))
        // Name alone still matches provisionally; contents do not confirm it.
        assertEquals(
            MatchKind.ARCHIVE_PROVISIONAL,
            PortMatcher.matchKind("Ocarina of Time (USA).zip", oot)
        )
    }

    @Test
    fun extractPullsOnlyTheMatchingEntry() {
        val zip = zipWithEntries(
            "Ocarina of Time.z64" to "rom-bytes",
            "readme.txt" to "do-not-extract-me"
        )
        val dest = Files.createTempDirectory("one-extract-test").toFile()
        val extracted = PortMatcher.extractZipEntry(
            zip, "Ocarina of Time.z64", dest
        )
        assertNotNull(extracted)
        assertEquals("Ocarina of Time.z64", extracted!!.name)
        assertEquals("rom-bytes", extracted.readText())
        assertFalse(File(dest, "readme.txt").exists())
    }

    @Test
    fun extractMissingEntryReturnsNull() {
        val zip = zipWithEntries("Ocarina of Time.z64" to "rom-bytes")
        val dest = Files.createTempDirectory("one-extract-missing").toFile()
        assertNull(PortMatcher.extractZipEntry(zip, "nope.z64", dest))
    }

    private fun zipWithEntries(vararg entries: Pair<String, String>): File {
        val file = Files.createTempFile("one-test", ".zip").toFile()
        ZipOutputStream(file.outputStream()).use { out ->
            for ((name, content) in entries) {
                out.putNextEntry(ZipEntry(name))
                out.write(content.toByteArray())
                out.closeEntry()
            }
        }
        return file
    }
}
