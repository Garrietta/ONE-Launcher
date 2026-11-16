package dev.one.launcher

import dev.one.launcher.data.MatchKind
import dev.one.launcher.data.PortMatcher
import dev.one.launcher.data.RemoteEntry
import dev.one.launcher.data.withDistinctiveHints
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
        platformId = "n64",
        platformName = "Nintendo 64",
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
    fun magnetUrlNeverMatchesEvenWithGameExtension() {
        // The matching gate excludes a magnet: URL before checking
        // name or extension: a magnet entry whose name ends in a
        // game extension (.zip) must not match, or it would appear
        // as a game file.
        val magnetUri =
            "magnet:?xt=urn:btih:9c6e87aaca8a26f4755d44079d5575b1732428f6" +
                "&dn=Acorn%20-%20Archimedes"
        assertNull(
            PortMatcher.matchKind(
                "Legend of Zelda, The - Ocarina of Time (USA).zip",
                oot, magnetUri
            )
        )
        assertFalse(PortMatcher.matches("OcarinaOfTime.z64", oot, magnetUri))
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
        // Both hints are distinctive in the real catalog: no other
        // port's hint set contains either.
        distinctiveNameHints = listOf("pokopia", "banananative-compilationfix"),
        platformId = "switch2",
        platformName = "Switch 2",
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
        // In the real catalog "donkey" and "kong" are shared with other
        // Donkey Kong ports, so only "bananza" and the id slug are
        // distinctive — set exactly that, as withDistinctiveHints does.
        distinctiveNameHints = listOf("bananza", "donkey-kong-bananza"),
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

    // ---- Distinctive-hint NAME_ONLY matching (v1.5.0) ----
    //
    // A name-only match now needs a DISTINCTIVE hint: one that appears
    // in exactly one port's hint set across the catalog and is at least
    // 4 characters long. The three cases below are Garrett's v1.4.3
    // device report — each file matched the wrong port on a single
    // shared hint ("pokémon", "new", "men") and must match nothing now.

    private val arceus = Port(
        id = "arceus-native",
        title = "Pokémon Legends: Arceus",
        game = "Pokémon Legends: Arceus",
        system = "Switch 2",
        type = "Recompilation",
        repo = "example/arceus",
        releasesUrl = "https://github.com/example/arceus/releases",
        packageNames = emptyList(),
        fileExtensions = emptyList(),
        // Hints exactly as hintsFromGameTitle produces: "pok" and "mon"
        // are the tokenized "pokémon", shared by every Pokémon port.
        fileNameHints = listOf("pok", "mon", "legends", "arceus", "arceus-native"),
        requiredDump = "Your own Arceus game dump."
    )

    private val acnh = Port(
        id = "acnh-recomp",
        title = "Animal Crossing: New Horizons",
        game = "Animal Crossing: New Horizons",
        system = "Switch 2",
        type = "Recompilation",
        repo = "example/acnh",
        releasesUrl = "https://github.com/example/acnh/releases",
        packageNames = emptyList(),
        fileExtensions = emptyList(),
        fileNameHints = listOf("animal", "crossing", "new", "horizons", "acnh-recomp"),
        requiredDump = "Your own New Horizons game dump."
    )

    private val armyMen = Port(
        id = "army-men-rts-android-evolved",
        title = "Army Men RTS Evolved",
        game = "Army Men RTS Evolved",
        system = "Android",
        type = "Recompilation",
        repo = "example/armymen",
        releasesUrl = "https://github.com/example/armymen/releases",
        packageNames = emptyList(),
        fileExtensions = emptyList(),
        // "men" (3 letters) and "rts" (3 letters) are too short to be
        // distinctive, whatever they share with.
        fileNameHints = listOf("army", "men", "rts", "evolved", "army-men-rts-android-evolved"),
        requiredDump = "Your own Army Men files."
    )

    private val raymanLegends = Port(
        id = "rayman-legends",
        title = "Rayman Legends",
        game = "Rayman Legends",
        system = "Switch 2",
        type = "Recompilation",
        repo = "example/rayman",
        releasesUrl = "https://github.com/example/rayman/releases",
        packageNames = emptyList(),
        fileExtensions = emptyList(),
        fileNameHints = listOf("rayman", "legends", "rayman-legends"),
        requiredDump = "Your own Rayman Legends game dump."
    )

    // A second port sharing "new", so it is not distinctive for ACNH.
    private val newSuperMarioBrosU = Port(
        id = "new-super-mario-bros-u",
        title = "New Super Mario Bros. U",
        game = "New Super Mario Bros. U",
        system = "Wii U",
        type = "Recompilation",
        repo = "example/nsmbu",
        releasesUrl = "https://github.com/example/nsmbu/releases",
        packageNames = emptyList(),
        fileExtensions = emptyList(),
        fileNameHints = listOf("new", "mario", "bros", "u", "new-super-mario-bros-u"),
        requiredDump = "Your own New Super Mario Bros. U game dump."
    )

    @Test
    fun pokopiaNupNoLongerMatchesArceus() {
        // Rayman Legends is in the list so "legends" is shared, as in
        // the real catalog — otherwise it would be distinctive here
        // and the test would prove nothing about shared hints.
        val ports = withDistinctiveHints(listOf(arceus, pokopia, raymanLegends))
        val arceusDistinctive = ports.first { it.id == "arceus-native" }
        // "pok"/"mon" are shared (and 3 letters); "legends" is shared
        // with Rayman Legends — none of them may match. Only "arceus"
        // itself is distinctive.
        assertEquals(
            listOf("arceus", "arceus-native"),
            arceusDistinctive.distinctiveNameHints
        )
        val kinds = PortMatcher.matchKinds(
            "Pokémon Pokopia [0400A3A01EE4C000][v655360].nup", ports
        )
        assertEquals(1, kinds.size)
        assertEquals("banananative-compilationfix", kinds.single().port.id)
        assertEquals(MatchKind.NAME_ONLY, kinds.single().kind)
    }

    @Test
    fun newReleasesTxtNoLongerMatchesAcnh() {
        val ports = withDistinctiveHints(listOf(acnh, newSuperMarioBrosU))
        // "new" is shared by both ports, so it is distinctive for
        // neither — the .txt matches nothing at all.
        assertFalse(
            ports.first { it.id == "acnh-recomp" }
                .distinctiveNameHints.contains("new")
        )
        assertTrue(
            PortMatcher.matchKinds(
                "NEW upcoming releases by Xclusive.txt", ports
            ).isEmpty()
        )
    }

    @Test
    fun xMenMkvsNoLongerMatchArmyMen() {
        val ports = withDistinctiveHints(listOf(armyMen))
        // "men" is 3 letters: too short to be distinctive even though
        // no other port in this list shares it.
        assertFalse(
            ports.single().distinctiveNameHints.contains("men")
        )
        listOf(
            "X-Men '97 S01E01.mkv",
            "X-Men '97 (2024) S01E02.mkv",
            "X-Men 97 - S01E03.mkv"
        ).forEach { file ->
            assertTrue(
                "expected no match for $file",
                PortMatcher.matchKinds(file, ports).isEmpty()
            )
        }
    }

    @Test
    fun sharedHintDoesNotMatchButDistinctiveHintDoes() {
        val ports = withDistinctiveHints(listOf(arceus, raymanLegends))
        // "legends" is shared by Arceus and Rayman Legends, so it
        // matches neither; "rayman" is Rayman Legends' alone.
        val kinds = PortMatcher.matchKinds("Rayman Legends.zip", ports)
        assertEquals(1, kinds.size)
        assertEquals("rayman-legends", kinds.single().port.id)
        assertEquals(MatchKind.NAME_ONLY, kinds.single().kind)
    }

    // ---- Title-similarity gate for NAME_ONLY (v1.8.0) ----
    //
    // Garrett's 2026-11-16 report: four .zip game files matched the
    // wrong port by a single distinctive word. The similarity gate
    // (titleSimilarity >= 0.90) blocks them — the file's title must
    // be mostly explained by the game's title.

    private val blueDragon = Port(
        id = "blue-dragon",
        title = "Blue Dragon",
        game = "Blue Dragon",
        system = "Xbox 360",
        type = "Recompilation",
        repo = "example/blue-dragon",
        releasesUrl = "https://github.com/example/blue-dragon/releases",
        packageNames = emptyList(),
        fileExtensions = emptyList(),
        fileNameHints = listOf("blue", "dragon", "blue-dragon"),
        requiredDump = "Your own Blue Dragon game dump."
    )

    private val marbleBlastGold = Port(
        id = "marble-blast-gold",
        title = "Marble Blast Gold",
        game = "Marble Blast Gold",
        system = "PC",
        type = "Recompilation",
        repo = "example/marble-blast-gold",
        releasesUrl = "https://github.com/example/marble-blast-gold/releases",
        packageNames = emptyList(),
        fileExtensions = emptyList(),
        fileNameHints = listOf("marble", "blast", "gold", "marble-blast-gold"),
        requiredDump = "Your own Marble Blast Gold game dump."
    )

    private val splinterCellDoubleAgent = Port(
        id = "splinter-cell-double-agent",
        title = "Tom Clancy's Splinter Cell: Double Agent",
        game = "Tom Clancy's Splinter Cell: Double Agent",
        system = "Xbox 360",
        type = "Recompilation",
        repo = "example/splinter-cell-double-agent",
        releasesUrl = "https://github.com/example/splinter-cell-double-agent/releases",
        packageNames = emptyList(),
        fileExtensions = emptyList(),
        fileNameHints = listOf(
            "tom", "clancy", "splinter", "cell", "double", "agent",
            "splinter-cell-double-agent"
        ),
        requiredDump = "Your own Splinter Cell: Double Agent game dump."
    )

    private val systemShock = Port(
        id = "system-shock",
        title = "System Shock",
        game = "System Shock",
        system = "PC",
        type = "Recompilation",
        repo = "example/system-shock",
        releasesUrl = "https://github.com/example/system-shock/releases",
        packageNames = emptyList(),
        fileExtensions = emptyList(),
        fileNameHints = listOf("system", "shock", "system-shock"),
        requiredDump = "Your own System Shock game dump."
    )

    @Test
    fun singleWordFalsePositivesBlockedBySimilarityGate() {
        val ports = withDistinctiveHints(
            listOf(blueDragon, marbleBlastGold, splinterCellDoubleAgent, systemShock)
        )
        val blue = ports.first { it.id == "blue-dragon" }
        val gold = ports.first { it.id == "marble-blast-gold" }
        val cell = ports.first { it.id == "splinter-cell-double-agent" }
        val shock = ports.first { it.id == "system-shock" }
        // Each file hits a distinctive hint, so without the
        // similarity gate it would match. The gate blocks all four:
        // the file's title is mostly NOT the game's title (0.5,
        // 0.5, 0.25, 0.5 — all below 0.90).
        assertTrue(blue.distinctiveNameHints.contains("blue"))
        assertNull(PortMatcher.matchKind("Pokemon Blue (GB) (USA).zip", blue))
        assertTrue(gold.distinctiveNameHints.contains("gold"))
        assertNull(PortMatcher.matchKind("Pokemon Gold (GBC) (USA).zip", gold))
        assertTrue(cell.distinctiveNameHints.contains("double"))
        assertNull(
            PortMatcher.matchKind("Mario Kart Double Dash (GC) (USA).zip", cell)
        )
        assertTrue(shock.distinctiveNameHints.contains("system"))
        assertNull(
            PortMatcher.matchKind("Sega - Master System - Mark III.zip", shock)
        )
    }

    @Test
    fun singleWordFalsePositivesStillBlockedForMagnets() {
        // The four v1.8.0 false positives, as magnet entries (v1.8.3):
        // the same 90% similarity gate blocks them — a magnet whose
        // name is mostly another game's title is not this game's
        // link. Magnet matching must not re-open the false positives
        // the gate closed for downloadable files.
        val ports = withDistinctiveHints(
            listOf(blueDragon, marbleBlastGold, splinterCellDoubleAgent, systemShock)
        )
        val blue = ports.first { it.id == "blue-dragon" }
        val gold = ports.first { it.id == "marble-blast-gold" }
        val cell = ports.first { it.id == "splinter-cell-double-agent" }
        val shock = ports.first { it.id == "system-shock" }
        val magnetUrl =
            "magnet:?xt=urn:btih:9c6e87aaca8a26f4755d44079d5575b1732428f6" +
                "&dn=Minerva_Myrient&tr=udp://tracker.opentrackr.org:1337/announce"
        fun magnet(name: String) =
            RemoteEntry(name, magnetUrl, isFolder = false, kind = "magnet")
        assertNull(PortMatcher.matchMagnetKind(magnet("Pokemon Blue (GB) (USA).zip"), blue))
        assertNull(PortMatcher.matchMagnetKind(magnet("Pokemon Gold (GBC) (USA).zip"), gold))
        assertNull(
            PortMatcher.matchMagnetKind(
                magnet("Mario Kart Double Dash (GC) (USA).zip"), cell
            )
        )
        assertNull(
            PortMatcher.matchMagnetKind(
                magnet("Sega - Master System - Mark III.zip"), shock
            )
        )
    }

    // ---- Platform-consistency gate (v1.8.1) ----
    //
    // Garrett's 2026-11-16 report: Breath of the Wild (Switch)
    // matched "Legend of Zelda Breath of the Wild (Wii U) (USA)"
    // (json · name only). The file is a .7z archive, so the v1.6.1
    // gate treats it as platform-agnostic, and the titles are
    // identical (similarity 1.0) — only the "(Wii U)" tag tells
    // them apart. A file tagged for a different platform than the
    // port's must never match, on any match kind.

    // Mirrors the real catalog entry for Breath of the Wild
    // (Switch): no catalog formats, so it matches by name only.
    // Hints as hintsFromGameTitle produces.
    private val botw = Port(
        id = "breath-of-the-wild",
        title = "The Legend of Zelda: Breath of the Wild",
        game = "The Legend of Zelda: Breath of the Wild",
        system = "Switch",
        type = "Recompilation",
        repo = "example/botw",
        releasesUrl = "https://github.com/example/botw/releases",
        packageNames = emptyList(),
        fileExtensions = emptyList(),
        fileNameHints = listOf("breath", "wild", "breath-of-the-wild"),
        distinctiveNameHints = listOf("breath", "wild", "breath-of-the-wild"),
        platformId = "switch",
        platformName = "Switch",
        requiredDump = "Your own Breath of the Wild game dump."
    )

    @Test
    fun wiiUFileDoesNotMatchSwitchBotw() {
        // The exact file from Garrett's report: the name carries
        // "(Wii U)", the URL is the archive.org Wii U retail .7z.
        // The similarity gate alone cannot save this one (the
        // titles are identical, 1.0) — the platform tag must block
        // it.
        val url = "https://archive.org/download/wii-u-retail-nus-usa/" +
            "The%20Legend%20of%20Zelda%20Breath%20of%20the%20Wild%20" +
            "%5B00050000101C9400%5D.7z"
        assertNull(
            PortMatcher.matchKind(
                "Legend of Zelda Breath of the Wild (Wii U) (USA)", botw, url
            )
        )
        // Without the tag, the same file WOULD match — the platform
        // check is what blocks it, not the similarity gate. (The
        // URL's folder name "wii-u-retail-nus-usa" is not read: only
        // the last path segment is, and it carries no tag.)
        assertEquals(
            MatchKind.NAME_ONLY,
            PortMatcher.matchKind(
                "Legend of Zelda Breath of the Wild (USA)", botw, url
            )
        )
    }

    @Test
    fun matchingPlatformTagStillMatches() {
        // A file tagged for the port's own platform still matches,
        // and a region-only tag never blocks: "(USA)" is a region,
        // not a platform.
        val url = "https://example.com/botw-switch.7z"
        assertEquals(
            MatchKind.NAME_ONLY,
            PortMatcher.matchKind(
                "The Legend of Zelda Breath of the Wild (Switch) (USA)", botw, url
            )
        )
        assertEquals(
            MatchKind.NAME_ONLY,
            PortMatcher.matchKind(
                "The Legend of Zelda Breath of the Wild (USA)", botw, url
            )
        )
    }

    @Test
    fun directMatchBlockedByConflictingPlatformTag() {
        // The platform check applies to DIRECT too, not just
        // NAME_ONLY: a .z64 tagged for another platform is not this
        // port's file, however well the extension and hint fit.
        assertNull(PortMatcher.matchKind("Ocarina of Time (Wii U).z64", oot))
        assertFalse(PortMatcher.matches("Ocarina of Time (Wii U).z64", oot))
        // The same file without the tag still matches directly.
        assertEquals(MatchKind.DIRECT, PortMatcher.matchKind("Ocarina of Time.z64", oot))
    }

    @Test
    fun unrecognizedPlatformTagDoesNotBlock() {
        // "(SAT)" is not a verified platform tag (the catalog has no
        // "saturn" platform id), so it must not block a match — an
        // unrecognized tag is ignored, never a veto. A made-up tag
        // is ignored the same way.
        assertEquals(
            MatchKind.ARCHIVE_PROVISIONAL,
            PortMatcher.matchKind("Ocarina of Time (SAT).zip", oot)
        )
        assertEquals(
            MatchKind.ARCHIVE_PROVISIONAL,
            PortMatcher.matchKind("Ocarina of Time (XYZ).zip", oot)
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
