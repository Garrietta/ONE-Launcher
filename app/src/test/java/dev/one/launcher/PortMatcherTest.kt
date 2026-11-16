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

    // ---- ARCHIVE_PROVISIONAL tightening (v1.8.4) ----

    // Fixtures mirroring the live catalog ports (hints exactly as
    // hintsFromGameTitle builds them, plus the catalog id).
    private val fireEmblem = Port(
        id = "fire-emblem-ss-recomp",
        title = "Fire Emblem The Sacred Stones",
        game = "Fire Emblem The Sacred Stones",
        system = "Game Boy Advance",
        type = "Recompilation",
        repo = "example/fire-emblem-ss-recomp",
        releasesUrl = "",
        packageNames = emptyList(),
        fileExtensions = listOf(".gba"),
        fileNameHints = listOf("fire", "emblem", "sacred", "stones", "fire-emblem-ss-recomp"),
        platformId = "gba",
        requiredDump = "Your own Fire Emblem: The Sacred Stones ROM."
    )

    private val megaMan64 = Port(
        id = "megaman64recompiled-mobile",
        title = "Mega Man 64",
        game = "Mega Man 64",
        system = "Nintendo 64",
        type = "Recompilation",
        repo = "example/megaman64",
        releasesUrl = "",
        packageNames = emptyList(),
        fileExtensions = listOf(".z64"),
        fileNameHints = listOf("mega", "man", "megaman64recompiled-mobile"),
        platformId = "n64",
        requiredDump = "Your own Mega Man 64 ROM."
    )

    private val starFox64 = Port(
        id = "starship",
        title = "Starship",
        game = "Star Fox 64",
        system = "Nintendo 64",
        type = "Decompilation",
        repo = "izzy2lost/Starship",
        releasesUrl = "",
        packageNames = emptyList(),
        fileExtensions = listOf(".z64"),
        fileNameHints = listOf("star", "fox", "starship"),
        platformId = "n64",
        requiredDump = "Your own Star Fox 64 ROM."
    )

    private val mario64 = Port(
        id = "ghostship",
        title = "Ghostship",
        game = "Super Mario 64",
        system = "Nintendo 64",
        type = "Decompilation",
        repo = "example/ghostship",
        releasesUrl = "",
        packageNames = emptyList(),
        fileExtensions = listOf(".z64"),
        fileNameHints = listOf("mario", "ghostship"),
        platformId = "n64",
        requiredDump = "Your own Super Mario 64 ROM."
    )

    // ---- ARCHIVE_PROVISIONAL similarity guard (v1.8.8, corrected) ----
    //
    // Fixtures mirroring the live catalog ports. Guitar Hero Series
    // (prj-gd-engine, Xbox 360): the live asset is a manual disc
    // asset with no accepted formats and no zip_ok, so the LIVE port
    // (guitarHeroSeriesLive: no file extensions, archiveOk false)
    // matches by NAME_ONLY — DJ Hero / Super Hero / World Hero
    // match it that way, NOT via ARCHIVE_PROVISIONAL, which is why
    // the first v1.8.8 guard (archive-only) did not fix Guitar Hero
    // at all. The archive-shape fixture (guitarHeroSeriesArchive:
    // .xex + archiveOk) is kept alongside it so the guard's
    // ARCHIVE_PROVISIONAL leg is still exercised — the genuine
    // Guitar Hero archives pass it there.
    private val guitarHeroSeriesLive = Port(
        id = "prj-gd-engine",
        title = "Guitar Hero Series",
        game = "Guitar Hero Series",
        system = "Xbox 360",
        type = "Recompilation",
        repo = "example/prj-gd-engine",
        releasesUrl = "",
        packageNames = emptyList(),
        fileExtensions = emptyList(),
        fileNameHints = listOf("guitar", "hero", "series", "prj-gd-engine"),
        distinctiveNameHints = listOf("guitar", "hero", "series", "prj-gd-engine"),
        platformId = "xbox360",
        archiveOk = false,
        requiredDump = "Your own Guitar Hero discs."
    )

    private val guitarHeroSeriesArchive = Port(
        id = "prj-gd-engine",
        title = "Guitar Hero Series",
        game = "Guitar Hero Series",
        system = "Xbox 360",
        type = "Recompilation",
        repo = "example/prj-gd-engine",
        releasesUrl = "",
        packageNames = emptyList(),
        fileExtensions = listOf(".xex"),
        fileNameHints = listOf("guitar", "hero", "series", "prj-gd-engine"),
        platformId = "xbox360",
        archiveOk = true,
        requiredDump = "Your own Guitar Hero discs."
    )

    private val metroidZeroMission = Port(
        id = "metroid-zm-recomp",
        title = "Metroid Zero Mission",
        game = "Metroid Zero Mission",
        system = "Game Boy Advance",
        type = "Recompilation",
        repo = "example/metroid-zm-recomp",
        releasesUrl = "",
        packageNames = emptyList(),
        fileExtensions = listOf(".gba"),
        fileNameHints = listOf("metroid", "zero", "mission", "metroid-zm-recomp"),
        platformId = "gba",
        archiveOk = true,
        requiredDump = "Your own Metroid: Zero Mission ROM."
    )

    // GoldenEye (N64) ROM port (project-midas, live shape): game
    // "GoldenEye (N64)" — the "(N64)" is a paren group, so the
    // title words are just ["goldeneye"]. Asset accepted [{z64}],
    // zip_ok true.
    private val goldenEyeN64 = Port(
        id = "project-midas",
        title = "GoldenEye (N64)",
        game = "GoldenEye (N64)",
        system = "Nintendo 64",
        type = "Recompilation",
        repo = "example/project-midas",
        releasesUrl = "",
        packageNames = emptyList(),
        fileExtensions = listOf(".z64"),
        fileNameHints = listOf("goldeneye", "project-midas"),
        distinctiveNameHints = listOf("project-midas"),
        platformId = "n64",
        archiveOk = true,
        requiredDump = "Your own GoldenEye 007 ROM."
    )

    // GoldenEye 007 recomp port (goldeneye-recomp, live shape): a
    // NAME_ONLY port (manual asset, no accepted formats). The
    // "GoldenEye 007 (USA).zip" file must NOT match it — its
    // distinctive hint "goldeneye-recomp" does not hit, and only
    // "goldeneye" of its hints hits — so the file matches ONLY via
    // project-midas above.
    private val goldenEye007Recomp = Port(
        id = "goldeneye-recomp",
        title = "GoldenEye 007",
        game = "GoldenEye 007",
        system = "Nintendo 64",
        type = "Recompilation",
        repo = "example/goldeneye-recomp",
        releasesUrl = "",
        packageNames = emptyList(),
        fileExtensions = emptyList(),
        fileNameHints = listOf("goldeneye", "007", "goldeneye-recomp"),
        distinctiveNameHints = listOf("goldeneye-recomp"),
        platformId = "n64",
        archiveOk = false,
        requiredDump = "Your own GoldenEye 007 game files."
    )

    // SimCity SNES (urbanrecomp, live shape): game "SimCity SNES",
    // title words ["simcity", "snes"]. Asset accepted [{sfc}],
    // zip_ok true.
    private val simCitySnes = Port(
        id = "urbanrecomp",
        title = "SimCity SNES",
        game = "SimCity SNES",
        system = "Super Nintendo",
        type = "Recompilation",
        repo = "example/urbanrecomp",
        releasesUrl = "",
        packageNames = emptyList(),
        fileExtensions = listOf(".sfc"),
        fileNameHints = listOf("simcity", "snes", "urbanrecomp"),
        distinctiveNameHints = listOf("simcity", "snes", "urbanrecomp"),
        platformId = "snes",
        archiveOk = true,
        requiredDump = "Your own SimCity ROM."
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

    // ---- ARCHIVE_PROVISIONAL tightening (v1.8.4) ----

    @Test
    fun archiveProvisionalSingleCommonWordNoLongerMatches() {
        // The three v1.8.3 false positives: a single shared word hit
        // ("fire", "man", "star") matched an archive to the wrong
        // game. Each must be gone for BOTH magnets and downloadable
        // files — the matcher rule is the same for both.
        val magnetUri = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567" +
            "&dn=Test&tr=udp://tracker.example.com:1337/announce"
        // 007 - Agent Under Fire vs Fire Emblem: The Sacred Stones.
        assertNull(PortMatcher.matchKind("007 - Agent Under Fire (USA).zip", fireEmblem))
        assertNull(
            PortMatcher.matchKind(
                "007 - Agent Under Fire (USA).zip", fireEmblem,
                "https://example.com/007-agent-under-fire.zip"
            )
        )
        assertNull(
            PortMatcher.matchMagnetKind(
                RemoteEntry(
                    name = "007 - Agent Under Fire (USA).zip",
                    url = magnetUri, isFolder = false, kind = "magnet"
                ),
                fireEmblem
            )
        )
        // 100 Man Yen Quiz Hunter vs Mega Man 64.
        assertNull(PortMatcher.matchKind("100 Man Yen Quiz Hunter.zip", megaMan64))
        assertNull(
            PortMatcher.matchKind(
                "100 Man Yen Quiz Hunter.zip", megaMan64,
                "https://example.com/100-man-yen-quiz-hunter.zip"
            )
        )
        assertNull(
            PortMatcher.matchMagnetKind(
                RemoteEntry(
                    name = "100 Man Yen Quiz Hunter.zip",
                    url = magnetUri, isFolder = false, kind = "magnet"
                ),
                megaMan64
            )
        )
        // 10101 - Will the Starship vs Star Fox 64. This one still
        // matched under a bare distinctive-or-two-hints bar, through
        // the port's id hint ("starship" is distinctive but also a
        // common word) — the title-similarity path is what blocks it
        // (the file's title is nothing like "Star Fox 64").
        assertNull(PortMatcher.matchKind("10101 - Will the Starship.zip", starFox64))
        assertNull(
            PortMatcher.matchKind(
                "10101 - Will the Starship.zip", starFox64,
                "https://example.com/10101-will-the-starship.zip"
            )
        )
        assertNull(
            PortMatcher.matchMagnetKind(
                RemoteEntry(
                    name = "10101 - Will the Starship.zip",
                    url = magnetUri, isFolder = false, kind = "magnet"
                ),
                starFox64
            )
        )
    }

    @Test
    fun archiveProvisionalCanonicalArchivesStillMatch() {
        // The tightening must not lose the ports' own archives: a
        // file named after the game passes on title similarity, even
        // when the game's title reduces to a single shared word
        // ("ocarina" is shared, "mario" is shared, "star" is shared).
        assertEquals(
            MatchKind.ARCHIVE_PROVISIONAL,
            PortMatcher.matchKind("The Legend of Zelda - Ocarina of Time (USA).zip", oot)
        )
        assertEquals(
            MatchKind.ARCHIVE_PROVISIONAL,
            PortMatcher.matchKind("Super Mario 64 (USA).zip", mario64)
        )
        assertEquals(
            MatchKind.ARCHIVE_PROVISIONAL,
            PortMatcher.matchKind("Star Fox 64 (USA).zip", starFox64)
        )
        assertEquals(
            MatchKind.ARCHIVE_PROVISIONAL,
            PortMatcher.matchKind("Mega Man 64 (USA).zip", megaMan64)
        )
        // And the same files as magnets.
        val magnetUri = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567" +
            "&dn=Test&tr=udp://tracker.example.com:1337/announce"
        assertEquals(
            MatchKind.ARCHIVE_PROVISIONAL,
            PortMatcher.matchMagnetKind(
                RemoteEntry(
                    name = "Star Fox 64 (USA).zip",
                    url = magnetUri, isFolder = false, kind = "magnet"
                ),
                starFox64
            )
        )
    }

    // ---- ARCHIVE_PROVISIONAL similarity guard (v1.8.8) ----

    @Test
    fun archiveProvisionalSingleWordTitleNoLongerMatches() {
        // Garrett's v1.8.7 video: DJ Hero, Super Hero and World Hero
        // all matched the Guitar Hero Series Xbox 360 port, and
        // F-Zero matched Metroid Zero Mission. Each file's title
        // reduces to ONE significant word ("hero", "zero") shared
        // with the game title, so the overlap coefficient is 1.0
        // (1/1) — similarity alone cannot tell it from a file named
        // after the game. The guard blocks them: shared 1 < 2, the
        // game's words are not all in the file's, and the file's
        // words are the game's minus words that are not the
        // platform id. Each must be gone for BOTH magnets and
        // downloadable files — the matcher rule is the same for
        // both. The Guitar Hero cases run against the LIVE-shape
        // port (no formats, archiveOk false): they arrive through
        // NAME_ONLY, which the first v1.8.8 guard did not touch —
        // that is the defect this correction fixes. The same three
        // names are also blocked on the archive-shape fixture, so
        // the guard's ARCHIVE_PROVISIONAL leg is covered too.
        val magnetUri = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567" +
            "&dn=Test&tr=udp://tracker.example.com:1337/announce"
        for (name in listOf(
            "DJ Hero (USA).zip",
            "Super Hero (China) (En) (Pirate).zip",
            "World Hero (Asia) (En) (Pirate).zip"
        )) {
            assertNull(PortMatcher.matchKind(name, guitarHeroSeriesLive))
            assertNull(
                PortMatcher.matchKind(
                    name, guitarHeroSeriesLive,
                    "https://example.com/${name.lowercase().replace(' ', '-')}"
                )
            )
            assertNull(
                PortMatcher.matchMagnetKind(
                    RemoteEntry(
                        name = name,
                        url = magnetUri, isFolder = false, kind = "magnet"
                    ),
                    guitarHeroSeriesLive
                )
            )
            assertNull(PortMatcher.matchKind(name, guitarHeroSeriesArchive))
            assertNull(
                PortMatcher.matchMagnetKind(
                    RemoteEntry(
                        name = name,
                        url = magnetUri, isFolder = false, kind = "magnet"
                    ),
                    guitarHeroSeriesArchive
                )
            )
        }
        for (name in listOf(
            "F-Zero (USA).zip",
            "F-Zero - GP Legend (USA).zip",
            "F-Zero - Maximum Velocity (USA, Europe).zip",
            "F-Zero GX (USA).zip",
            "F-Zero X (USA).zip"
        )) {
            assertNull(PortMatcher.matchKind(name, metroidZeroMission))
            assertNull(
                PortMatcher.matchMagnetKind(
                    RemoteEntry(
                        name = name,
                        url = magnetUri, isFolder = false, kind = "magnet"
                    ),
                    metroidZeroMission
                )
            )
        }
        // "F-Zero - Maximum Velocity" never matched even before the
        // guard (three significant words, similarity 0.33) — it stays
        // blocked, and the v1.8.4 cases stay blocked too.
        assertNull(PortMatcher.matchKind("007 - Agent Under Fire (USA).zip", fireEmblem))
        assertNull(PortMatcher.matchKind("100 Man Yen Quiz Hunter.zip", megaMan64))
        assertNull(PortMatcher.matchKind("10101 - Will the Starship.zip", starFox64))
    }

    @Test
    fun archiveProvisionalGenuineGuitarHeroFilesStillMatch() {
        // The real Guitar Hero files share TWO words with the game
        // title ("guitar" + "hero"), so they pass the guard — via the
        // two-hints leg on the archive-shape port, and via NAME_ONLY
        // (leg (a), shared 2) on the live-shape port.
        assertEquals(
            MatchKind.ARCHIVE_PROVISIONAL,
            PortMatcher.matchKind("Guitar Hero (USA).zip", guitarHeroSeriesArchive)
        )
        assertEquals(
            MatchKind.NAME_ONLY,
            PortMatcher.matchKind("Guitar Hero (USA).zip", guitarHeroSeriesLive)
        )
        assertEquals(
            MatchKind.ARCHIVE_PROVISIONAL,
            PortMatcher.matchKind(
                "Guitar Hero - Aerosmith (USA) (En,Fr).zip", guitarHeroSeriesArchive
            )
        )
        // The live-shape port never matched Aerosmith, before or
        // after the guard: its title similarity is 2/3 = 0.667, below
        // the pre-existing v1.8.0 0.90 gate — that is the similarity
        // gate, not the guard, and it is unchanged here.
        assertNull(
            PortMatcher.matchKind(
                "Guitar Hero - Aerosmith (USA) (En,Fr).zip", guitarHeroSeriesLive
            )
        )
        // And the game-⊆-file leg keeps the single-word games: the
        // file's words contain the game's words (covered for SM64 by
        // archiveProvisionalCanonicalArchivesStillMatch; asserted
        // here for the guard's own leg).
        assertEquals(
            MatchKind.ARCHIVE_PROVISIONAL,
            PortMatcher.matchKind("Super Mario 64 (USA).zip", mario64)
        )
    }

    @Test
    fun correctedGuardKeepsGoldenEye007SimCityAndMasterQuest() {
        // The first v1.8.8 guard's exact-title leg lost these three
        // true positives (sweep: ARCHIVE_PROVISIONAL → None). The
        // corrected guard keeps them all, each on a different leg.
        val magnetUri = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567" +
            "&dn=Test&tr=udp://tracker.example.com:1337/announce"
        fun magnet(name: String) =
            RemoteEntry(name, magnetUri, isFolder = false, kind = "magnet")
        // GoldenEye 007 (USA).zip vs project-midas, leg (b): the
        // game's title words ["goldeneye"] are all in the file's
        // ["goldeneye", "007"] — as a file and as a magnet.
        assertEquals(
            MatchKind.ARCHIVE_PROVISIONAL,
            PortMatcher.matchKind("GoldenEye 007 (USA).zip", goldenEyeN64)
        )
        assertEquals(
            MatchKind.ARCHIVE_PROVISIONAL,
            PortMatcher.matchMagnetKind(magnet("GoldenEye 007 (USA).zip"), goldenEyeN64)
        )
        assertEquals(
            MatchKind.ARCHIVE_PROVISIONAL,
            PortMatcher.matchKind("GoldenEye 007 (USA) (En,Fr).zip", goldenEyeN64)
        )
        // ...and it matches ONLY via project-midas: the
        // goldeneye-recomp NAME_ONLY port must not match it (its
        // distinctive hint "goldeneye-recomp" does not hit, and only
        // "goldeneye" of its hints hits — fewer than the two long
        // hints NAME_ONLY requires).
        assertNull(PortMatcher.matchKind("GoldenEye 007 (USA).zip", goldenEye007Recomp))
        assertNull(
            PortMatcher.matchMagnetKind(
                magnet("GoldenEye 007 (USA).zip"), goldenEye007Recomp
            )
        )
        // SimCity (USA).zip vs urbanrecomp, leg (c): the file's
        // words ["simcity"] are the game's ["simcity", "snes"]
        // minus only the platform word "snes".
        assertEquals(
            MatchKind.ARCHIVE_PROVISIONAL,
            PortMatcher.matchKind("SimCity (USA).zip", simCitySnes)
        )
        assertEquals(
            MatchKind.ARCHIVE_PROVISIONAL,
            PortMatcher.matchMagnetKind(magnet("SimCity (USA).zip"), simCitySnes)
        )
        assertEquals(
            MatchKind.ARCHIVE_PROVISIONAL,
            PortMatcher.matchKind("SimCity (USA) (Proto).zip", simCitySnes)
        )
        // The OoT Master Quest magnet vs SoH, leg (b): the game's
        // title words ["ocarina"] are all in the file's
        // ["ocarina", "master", "quest"]. Exact magnet name from
        // the v1.8.8 sweep diff.
        assertEquals(
            MatchKind.ARCHIVE_PROVISIONAL,
            PortMatcher.matchMagnetKind(
                magnet("Legend of Zelda, The - Ocarina of Time & Master Quest (USA).zip"),
                oot
            )
        )
    }

    @Test
    fun marioAndLuigiStillMatchesSm64AsKnownAccepted() {
        // KNOWN ACCEPTED, not a regression: "Mario & Luigi" matched
        // Super Mario 64 under v1.8.7 too, and the corrected guard
        // keeps it — the game's title words ["mario"] are all in
        // the file's ["mario", "luigi"] (leg (b)). The sweep keeps
        // it for the same reason; it is a pre-existing single-word
        // containment, not a new false positive.
        assertEquals(
            MatchKind.ARCHIVE_PROVISIONAL,
            PortMatcher.matchKind("Mario & Luigi (USA) (Demo) (Kiosk, E3 2003).zip", mario64)
        )
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
