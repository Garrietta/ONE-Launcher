package dev.one.launcher

import dev.one.launcher.data.DetectedFile
import dev.one.launcher.data.GameEntry
import dev.one.launcher.data.GameFileSource
import dev.one.launcher.data.LocalFileState
import dev.one.launcher.data.MatchKind
import dev.one.launcher.data.PremiumizeItem
import dev.one.launcher.data.PortMatcher
import dev.one.launcher.data.RemoteEntry
import dev.one.launcher.data.STAR_CACHE_TTL_MS
import dev.one.launcher.data.dedupeGameFiles
import dev.one.launcher.data.displayPlatformName
import dev.one.launcher.data.gamesWithFiles
import dev.one.launcher.data.groupPortsByGame
import dev.one.launcher.data.hintsFromGameTitle
import dev.one.launcher.data.isStarFresh
import dev.one.launcher.data.matchCloudFilesForGame
import dev.one.launcher.data.matchLocalFilesForGame
import dev.one.launcher.data.matchServerFilesForGame
import dev.one.launcher.data.normalizeForTokens
import dev.one.launcher.data.rankPortsByStars
import dev.one.launcher.model.Port
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Game-centric Library (v1.6.0): grouping, game-level file matching,
 * star ranking, the Eidos matcher fix, and the "Games with matches"
 * filter. The GitHub star fetch, its cache, and the game page
 * rendering are device/runtime behavior, not JVM-testable — only the
 * pure ranking sort and the cache freshness rule are tested here.
 *
 * Matcher fixtures use the live-catalog values verified by computation
 * against the 2026-11-16 index (schema 1, 536 ports): hints and
 * distinctive hints are the real ones for those ports.
 */
class GameLibraryTest {

    // ---- Fixtures: live-catalog values (2026-11-16 index) ----

    private val eidos = Port(
        id = "eidos",
        title = "Eidos",
        game = "Pokémon Black & White",
        system = "Nintendo DS",
        type = "recomp",
        repo = "Project-Holon/eidos",
        releasesUrl = "https://github.com/Project-Holon/eidos/releases",
        fileExtensions = emptyList(),
        fileNameHints = listOf("pokemon", "black", "white", "eidos"),
        requiredDump = "Pokémon Black or White ROM (DS)",
        distinctiveNameHints = listOf("eidos")
    )

    private val pokopia = Port(
        id = "banananative-compilationfix",
        title = "Pokopia",
        game = "Pokopia",
        system = "Nintendo Switch 2",
        type = "recomp",
        repo = "Wojnstup/BananaNative-CompilationFix",
        releasesUrl = "https://github.com/Wojnstup/BananaNative-CompilationFix/releases",
        fileExtensions = emptyList(),
        fileNameHints = listOf("pokopia", "banananative-compilationfix"),
        requiredDump = "Your Switch 2 game dump as a folder (with base/)",
        distinctiveNameHints = listOf("pokopia", "banananative-compilationfix")
    )

    private val arceus = Port(
        id = "arceus-native",
        title = "Arceus",
        game = "Pokémon Legends: Arceus",
        system = "Nintendo Switch",
        type = "recomp",
        repo = "arceus-native/arceus",
        releasesUrl = "https://github.com/arceus-native/arceus/releases",
        fileExtensions = emptyList(),
        fileNameHints = listOf("pokemon", "legends", "arceus", "arceus-native"),
        requiredDump = "Pokémon Legends: Arceus",
        distinctiveNameHints = listOf("arceus", "arceus-native")
    )

    private val acnh = Port(
        id = "acnh-recomp",
        title = "Animal Crossing: New Horizons",
        game = "Animal Crossing: New Horizons",
        system = "Nintendo Switch",
        type = "recomp",
        repo = "acnh-recomp/acnh",
        releasesUrl = "https://github.com/acnh-recomp/acnh/releases",
        fileExtensions = emptyList(),
        fileNameHints = listOf("animal", "crossing", "new", "horizons", "acnh-recomp"),
        requiredDump = "Animal Crossing: New Horizons",
        distinctiveNameHints = listOf("horizons", "acnh-recomp")
    )

    private val armyMen = Port(
        id = "army-men-rts-android-evolved",
        title = "Army Men RTS Evolved",
        game = "Army Men RTS Evolved",
        system = "PC",
        type = "recomp",
        repo = "army-men-rts/army-men-rts-android-evolved",
        releasesUrl = "https://github.com/army-men-rts/army-men-rts-android-evolved/releases",
        fileExtensions = emptyList(),
        fileNameHints = listOf("army", "men", "rts", "evolved", "army-men-rts-android-evolved"),
        requiredDump = "Army Men RTS",
        distinctiveNameHints = listOf("army", "army-men-rts-android-evolved")
    )

    private val soh = Port(
        id = "soh",
        title = "Ship of Harkinian",
        game = "The Legend of Zelda: Ocarina of Time",
        system = "Nintendo 64",
        type = "decomp",
        repo = "linkzenic/Shipwright-Ports",
        releasesUrl = "https://github.com/linkzenic/Shipwright-Ports/releases",
        fileExtensions = listOf(".z64", ".n64", ".v64"),
        fileNameHints = listOf("zelootd", "oot", "ocarina"),
        requiredDump = "The Legend of Zelda: Ocarina of Time (USA) ROM",
        platformName = "Nintendo 64",
        catalogDownloads = 10_000
    )

    private val oot3d = Port(
        id = "oot3d-recomp",
        title = "Ocarina of Time 3D Recompilation",
        game = "The Legend of Zelda: Ocarina of Time 3D",
        system = "Nintendo 3DS",
        type = "recomp",
        repo = "oot3d-recomp/oot3d-recomp",
        releasesUrl = "https://github.com/oot3d-recomp/oot3d-recomp/releases",
        fileExtensions = emptyList(),
        fileNameHints = listOf("ocarina", "oot3d-recomp"),
        requiredDump = "The Legend of Zelda: Ocarina of Time 3D",
        distinctiveNameHints = listOf("oot3d-recomp")
    )

    private val twoShip = Port(
        id = "2ship",
        title = "2Ship2Harkinian",
        game = "The Legend of Zelda: Majora's Mask",
        system = "Nintendo 64",
        type = "decomp",
        repo = "linkzenic/2ship2harkinian-Android",
        releasesUrl = "https://github.com/linkzenic/2ship2harkinian-Android/releases",
        fileExtensions = listOf(".z64", ".n64", ".v64"),
        fileNameHints = listOf("mm", "majora"),
        requiredDump = "The Legend of Zelda: Majora's Mask (USA) ROM",
        platformName = "Nintendo 64",
        catalogDownloads = 8_000
    )

    private val zelda64Recomp = Port(
        id = "zelda64recomp",
        title = "Zelda64Recompiled",
        game = "The Legend of Zelda: Majora's Mask",
        system = "Nintendo 64",
        type = "recomp",
        repo = "Mr-Wiseguy/Zelda64Recompiled",
        releasesUrl = "https://github.com/Mr-Wiseguy/Zelda64Recompiled/releases",
        fileExtensions = emptyList(),
        fileNameHints = listOf("majora", "zelda64recomp"),
        requiredDump = "The Legend of Zelda: Majora's Mask (USA) ROM",
        platformName = "Nintendo 64",
        distinctiveNameHints = listOf("zelda64recomp")
    )

    private val zelda3 = Port(
        id = "zelda3",
        title = "Zelda3",
        game = "The Legend of Zelda: A Link to the Past",
        system = "Super Nintendo",
        type = "decomp",
        repo = "snesrev/zelda3",
        releasesUrl = "https://github.com/snesrev/zelda3/releases",
        fileExtensions = listOf(".sfc", ".smc"),
        fileNameHints = listOf("zelda3", "alttp"),
        requiredDump = "The Legend of Zelda: A Link to the Past (USA) ROM",
        platformName = "Super Nintendo"
    )

    private val moonpearl = Port(
        id = "moonpearl",
        title = "Moonpearl",
        game = "The Legend of Zelda: A Link to the Past",
        system = "Super Nintendo",
        type = "decomp",
        repo = "moonpearl/moonpearl",
        releasesUrl = "https://github.com/moonpearl/moonpearl/releases",
        fileExtensions = emptyList(),
        fileNameHints = listOf("link", "past", "moonpearl"),
        requiredDump = "The Legend of Zelda: A Link to the Past (USA) ROM",
        platformName = "Super Nintendo",
        catalogDownloads = 50,
        distinctiveNameHints = listOf("moonpearl")
    )

    // ---- Grouping ----

    @Test
    fun groupingGroupsByTitleAndPlatform() {
        val games = groupPortsByGame(
            listOf(soh, twoShip, zelda64Recomp, zelda3, moonpearl, eidos)
        )
        assertEquals(4, games.size)
        val mm = games.first { it.title == "The Legend of Zelda: Majora's Mask" }
        assertEquals("Nintendo 64", mm.platform)
        assertEquals(listOf("2ship", "zelda64recomp"), mm.ports.map { it.id })
        val alttp = games.first { it.title == "The Legend of Zelda: A Link to the Past" }
        assertEquals("Super Nintendo", alttp.platform)
        // Moonpearl first: it has catalog downloads (50), Zelda3 has none.
        assertEquals(listOf("moonpearl", "zelda3"), alttp.ports.map { it.id })
        val oot = games.first { it.title == "The Legend of Zelda: Ocarina of Time" }
        assertEquals(listOf("soh"), oot.ports.map { it.id })
        val bw = games.first { it.title == "Pokémon Black & White" }
        assertEquals(listOf("eidos"), bw.ports.map { it.id })
    }

    @Test
    fun groupingExcludesEmulatorsButKeepsSourcePorts() {
        val emulator = Port(
            id = "p4g",
            title = "Persona 4 Golden (emulator)",
            game = "Persona 4 Golden",
            system = "PlayStation Vita",
            type = "emulator",
            repo = "emu/p4g",
            releasesUrl = "https://github.com/emu/p4g/releases",
            fileExtensions = emptyList(),
            requiredDump = "Persona 4 Golden"
        )
        val sourcePort = Port(
            id = "source-port-example",
            title = "Example Source Port",
            game = "Example Game",
            system = "PC",
            type = "source-port",
            repo = "example/source-port",
            releasesUrl = "https://github.com/example/source-port/releases",
            fileExtensions = emptyList(),
            requiredDump = "Example Game"
        )
        val games = groupPortsByGame(listOf(emulator, sourcePort, soh))
        assertEquals(2, games.size)
        assertTrue(games.none { it.title == "Persona 4 Golden" })
        assertTrue(games.any { it.title == "Example Game" })
    }

    @Test
    fun groupingFallsBackToPortTitleAndSystem() {
        val blank = Port(
            id = "blank-game",
            title = "Mystery Port",
            game = "",
            system = "PC",
            type = "recomp",
            repo = "example/mystery",
            releasesUrl = "https://github.com/example/mystery/releases",
            fileExtensions = emptyList(),
            requiredDump = "Mystery"
        )
        val games = groupPortsByGame(listOf(blank))
        assertEquals(1, games.size)
        assertEquals("Mystery Port", games[0].title)
        assertEquals("PC", games[0].platform)
    }

    // ---- Game-level file matching ----

    @Test
    fun gameLevelMatchingFileMatchingAnyPortShowsOnce() {
        // Majora's Mask has two ports; the .z64 matches 2Ship by
        // extension and the archive matches 2Ship by name — each file
        // is listed once, against its first matching port.
        val games = groupPortsByGame(listOf(twoShip, zelda64Recomp))
        val mm = games.single()
        val cloud = matchCloudFilesForGame(
            listOf(
                PremiumizeItem(name = "Majora's Mask (USA).z64"),
                PremiumizeItem(name = "Majora's Mask (USA).zip")
            ),
            mm.ports
        )
        assertEquals(2, cloud.size)
        assertEquals("2ship", cloud[0].portId)
        assertEquals(MatchKind.DIRECT, cloud[0].kind)
        assertEquals("2ship", cloud[1].portId)
        assertEquals(MatchKind.ARCHIVE_PROVISIONAL, cloud[1].kind)
    }

    @Test
    fun gameLevelMatchingDedupesAcrossSources() {
        val games = groupPortsByGame(listOf(eidos))
        val game = games.single()
        val cloud = matchCloudFilesForGame(
            listOf(
                PremiumizeItem(
                    name = "Pokemon Black (NDS) (USA)",
                    link = "https://example.com/pokemon-black.zip"
                )
            ),
            game.ports
        )
        val server = matchServerFilesForGame(
            listOf(
                "json" to listOf(
                    RemoteEntry(
                        name = "Pokemon Black (NDS) (USA)",
                        url = "https://example.com/pokemon-black.zip",
                        isFolder = false
                    )
                )
            ),
            game.ports
        )
        val local = matchLocalFilesForGame(
            mapOf("eidos" to listOf(
                DetectedFile(
                    name = "Pokemon Black (NDS) (USA)",
                    uri = "content://local/pokemon-black",
                    kind = MatchKind.NAME_ONLY
                )
            )),
            emptyMap(),
            game.ports
        )
        val deduped = dedupeGameFiles(cloud + server + local)
        assertEquals(1, deduped.size)
        // On-device beats cloud beats server for the same file.
        assertEquals(GameFileSource.LOCAL, deduped[0].source)
        val cloudOnly = dedupeGameFiles(cloud + server)
        assertEquals(1, cloudOnly.size)
        assertEquals(GameFileSource.CLOUD, cloudOnly[0].source)
    }

    @Test
    fun gameLevelMatchingExcludesNonMatchingFiles() {
        val games = groupPortsByGame(listOf(eidos, pokopia))
        val eidosGame = games.first { it.title == "Pokémon Black & White" }
        val cloud = matchCloudFilesForGame(
            listOf(PremiumizeItem(name = "Super Mario 64 (USA).zip")),
            eidosGame.ports
        )
        assertTrue(cloud.isEmpty())
        val server = matchServerFilesForGame(
            listOf(
                "json" to listOf(
                    RemoteEntry(
                        name = "Super Mario 64 (USA).zip",
                        url = "https://example.com/mario64",
                        isFolder = false
                    )
                )
            ),
            eidosGame.ports
        )
        assertTrue(server.isEmpty())
    }

    // ---- Star ranking ----

    @Test
    fun starRankingStarsFirstThenDownloadsThenNeither() {
        val starred = soh.copy(repo = "a/starred", catalogDownloads = 0)
        val starredLess = twoShip.copy(repo = "b/less-starred", catalogDownloads = 10_000)
        val downloadsOnly = moonpearl.copy(repo = "c/downloads", catalogDownloads = 500)
        val neither = zelda3.copy(repo = "d/neither", catalogDownloads = null)
        val ranked = rankPortsByStars(
            listOf(neither, downloadsOnly, starredLess, starred),
            mapOf("a/starred" to 100, "b/less-starred" to 50)
        )
        assertEquals(
            listOf("soh", "2ship", "moonpearl", "zelda3"),
            ranked.map { it.id }
        )
    }

    @Test
    fun starCacheFreshness() {
        val now = 1_800_000_000_000L
        assertTrue(isStarFresh(now - 6 * 24 * 60 * 60 * 1000, now))
        assertTrue(isStarFresh(now - STAR_CACHE_TTL_MS + 1, now))
        assertFalse(isStarFresh(now - STAR_CACHE_TTL_MS, now))
        assertFalse(isStarFresh(now - 8 * 24 * 60 * 60 * 1000, now))
        // A fetch timestamp in the future is never fresh.
        assertFalse(isStarFresh(now + 1_000, now))
    }

    // ---- Matcher: Eidos fix (diacritics + two-hint NAME_ONLY) ----

    @Test
    fun diacriticNormalizationFoldsPokemon() {
        assertEquals("pokemon", normalizeForTokens("Pokémon"))
        val hints = hintsFromGameTitle("Pokémon Black & White", "eidos")
        assertTrue(hints.contains("pokemon"))
        assertFalse(hints.contains("pok"))
        assertFalse(hints.contains("mon"))
        assertEquals(listOf("pokemon", "black", "white", "eidos"), hints)
    }

    @Test
    fun eidosMatchesPokemonBlack() {
        // The real remote.json entry's name has no extension — its URL
        // ends in ".zip", and that is what the game-file gate reads
        // (v1.6.1). A bare extension-less name with no URL matches
        // nothing; see extensionlessNameWithoutUrlDoesNotMatch.
        val url = "https://example.com/pokemon-black.zip"
        assertEquals(
            MatchKind.NAME_ONLY,
            PortMatcher.matchKind("Pokemon Black (NDS) (USA)", eidos, url)
        )
        // Only Eidos, across the Pokémon ports in the fixture set.
        val matches = PortMatcher.matchKinds(
            "Pokemon Black (NDS) (USA)",
            listOf(eidos, arceus, pokopia, acnh, armyMen),
            url
        )
        assertEquals(listOf("eidos"), matches.map { it.port.id })
    }

    @Test
    fun pokopiaStillMatchesPokopiaOnly() {
        val file = "Pokémon Pokopia [0400A3A01EE4C000][v655360].nup"
        assertEquals(MatchKind.NAME_ONLY, PortMatcher.matchKind(file, pokopia))
        // Arceus shares only "pokemon" with this file — one shared
        // hint is never enough.
        assertNull(PortMatcher.matchKind(file, arceus))
        val matches = PortMatcher.matchKinds(
            file,
            listOf(eidos, arceus, pokopia, acnh, armyMen)
        )
        assertEquals(listOf("banananative-compilationfix"), matches.map { it.port.id })
    }

    @Test
    fun ootZipStillMatchesSohOnly() {
        val file = "Legend of Zelda, The - Ocarina of Time (USA).zip"
        assertEquals(MatchKind.ARCHIVE_PROVISIONAL, PortMatcher.matchKind(file, soh))
        // Ocarina of Time 3D hits only "ocarina" — one hint, and its
        // catalog id never appears in the file name.
        assertNull(PortMatcher.matchKind(file, oot3d))
        val matches = PortMatcher.matchKinds(file, listOf(soh, oot3d))
        assertEquals(listOf("soh"), matches.map { it.port.id })
    }

    @Test
    fun v150FalsePositivesStillDoNotMatch() {
        // ACNH vs the "NEW upcoming releases" .txt: "new" is 3
        // letters, so it never counts toward the two-hint rule, and
        // no other ACNH hint appears in the file name.
        assertNull(
            PortMatcher.matchKind("NEW upcoming releases by Xclusive.txt", acnh)
        )
        assertTrue(
            PortMatcher.matchKinds(
                "NEW upcoming releases by Xclusive.txt",
                listOf(eidos, arceus, pokopia, acnh, armyMen)
            ).isEmpty()
        )
        // Army Men vs the X-Men '97 .mkv files: "men" is 3 letters,
        // "army" never appears in the file name.
        assertNull(
            PortMatcher.matchKind("X-Men '97 - S01E01.mkv", armyMen)
        )
        assertTrue(
            PortMatcher.matchKinds(
                "X-Men '97 - S01E02.mkv",
                listOf(eidos, arceus, pokopia, acnh, armyMen)
            ).isEmpty()
        )
    }

    @Test
    fun black2MatchesEidosIsDocumentedAmbiguity() {
        // "Pokemon Black 2 (NDS) (USA)" hits "pokemon" + "black", so
        // it matches Eidos by the same two-hint rule — a different
        // game (the sequel) that Eidos does not cover. This is the
        // same class of provisional ambiguity the matcher already
        // tolerates, and the match is labeled "name only". No Black 2
        // port exists in the catalog (verified against the live index),
        // so the file matches Eidos only. The .zip URL is what the
        // v1.6.1 game-file gate reads — the name has no extension.
        val url = "https://example.com/pokemon-black-2.zip"
        assertEquals(
            MatchKind.NAME_ONLY,
            PortMatcher.matchKind("Pokemon Black 2 (NDS) (USA)", eidos, url)
        )
        val matches = PortMatcher.matchKinds(
            "Pokemon Black 2 (NDS) (USA)",
            listOf(eidos, arceus, pokopia, acnh, armyMen),
            url
        )
        assertEquals(listOf("eidos"), matches.map { it.port.id })
    }

    // ---- "Games with matches" filter ----

    @Test
    fun gamesWithFilesFilterPassesGameWithMatchAndBlocksGameWithout() {
        val games = groupPortsByGame(listOf(eidos, pokopia, arceus))
        val eidosGame = games.first { it.title == "Pokémon Black & White" }
        val pokopiaGame = games.first { it.title == "Pokopia" }
        val keys = gamesWithFiles(
            games = games,
            cloudItems = listOf(
                PremiumizeItem(name = "Pokémon Pokopia [0400A3A01EE4C000][v655360].nup")
            ),
            cloudFallback = emptyMap(),
            serverSources = listOf(
                "json" to listOf(
                    RemoteEntry(
                        name = "Pokemon Black (NDS) (USA)",
                        url = "https://example.com/pokemon-black.zip",
                        isFolder = false
                    )
                )
            ),
            detected = emptyMap(),
            localStates = emptyMap()
        )
        assertTrue(keys.contains(eidosGame.key))
        assertTrue(keys.contains(pokopiaGame.key))
        assertEquals(2, keys.size)
    }

    @Test
    fun gamesWithFilesFilterBlocksGameWithNoFiles() {
        val games = groupPortsByGame(listOf(eidos, pokopia, arceus))
        val arceusGame = games.first { it.title == "Pokémon Legends: Arceus" }
        val keys = gamesWithFiles(
            games = games,
            cloudItems = emptyList(),
            cloudFallback = emptyMap(),
            serverSources = listOf(
                "json" to listOf(
                    RemoteEntry(
                        name = "Pokemon Black (NDS) (USA)",
                        url = "https://example.com/pokemon-black.zip",
                        isFolder = false
                    )
                )
            ),
            detected = emptyMap(),
            localStates = emptyMap()
        )
        assertFalse(keys.contains(arceusGame.key))
        assertEquals(1, keys.size)
    }

    // ---- Game-file gate (v1.6.1) ----
    //
    // Garrett's v1.6.0 device report: two movie files matched to
    // games, and the JSON folder was full of non-game entries. The
    // fixtures below use the live-catalog values verified by
    // computation against the 2026-11-16 index: both ports have no
    // accepted formats, so they match only by name — and their names
    // are shared with the movies that matched them.

    private val tlouRemastered = Port(
        id = "tlou-remastered",
        title = "The Last of Us Remastered",
        game = "The Last of Us Remastered",
        system = "PlayStation 4",
        type = "recomp",
        repo = "erickmendes22/tlou-remastered-android",
        releasesUrl = "https://github.com/erickmendes22/tlou-remastered-android/releases",
        fileExtensions = emptyList(),
        // Hints exactly as hintsFromGameTitle produces: "the"/"of"
        // are stopwords, "us" is a region/generic token, "remastered"
        // is a stopword — only "last" and the catalog id remain.
        // "last" has exactly one owner in the merged catalog, so it
        // is distinctive — and it is also a common word in movie file
        // names, which is how the .mkv matched.
        fileNameHints = listOf("last", "tlou-remastered"),
        requiredDump = "Your own The Last of Us Remastered files.",
        platformId = "ps4",
        platformName = "PlayStation 4",
        distinctiveNameHints = listOf("last", "tlou-remastered")
    )

    private val darkKnightRises = Port(
        id = "thedarkknightrisesrevived",
        title = "The Dark Knight Rises",
        game = "The Dark Knight Rises",
        system = "Other",
        type = "recomp",
        repo = "RedMemories/TheDarkKnightRisesRevived",
        releasesUrl = "https://github.com/RedMemories/TheDarkKnightRisesRevived/releases",
        fileExtensions = emptyList(),
        fileNameHints = listOf("dark", "knight", "rises", "thedarkknightrisesrevived"),
        requiredDump = "Your own The Dark Knight Rises files.",
        platformId = "other",
        platformName = "Otra",
        // Only the catalog id is distinctive: "dark" and "knight"
        // are shared with other ports in the real catalog, so the
        // movie matched by the two-hint rule instead.
        distinctiveNameHints = listOf("thedarkknightrisesrevived")
    )

    @Test
    fun lastAirbenderMkvDoesNotMatchTlou() {
        // Without the gate this matches NAME_ONLY on the distinctive
        // hint "last" — the exact false positive from Garrett's
        // v1.6.0 device report. A .mkv is not a game file for any
        // platform, so the gate rejects it before the hints run.
        val file = "The Last Airbender (2010).mkv"
        assertNull(PortMatcher.matchKind(file, tlouRemastered))
        assertTrue(
            PortMatcher.matchKinds(
                file,
                listOf(tlouRemastered, darkKnightRises, eidos, pokopia)
            ).isEmpty()
        )
    }

    @Test
    fun darkKnightMkvDoesNotMatchDarkKnightRises() {
        // Without the gate this matches NAME_ONLY on the two-hint
        // rule ("dark" + "knight") — the 2008 Batman movie, not the
        // game. The .mkv gate rejects it first.
        val file =
            "The.Dark.Knight.2008.2160p.MAX.WEB-DL.DDP.5.1.DV.HDR.H.265-PiRaTeS.mkv"
        assertNull(PortMatcher.matchKind(file, darkKnightRises))
        assertTrue(
            PortMatcher.matchKinds(
                file,
                listOf(tlouRemastered, darkKnightRises, eidos, pokopia)
            ).isEmpty()
        )
    }

    @Test
    fun extensionlessNameWithoutUrlDoesNotMatch() {
        // The v1.6.1 gate, stated plainly: a file with no extension
        // in its name and no URL to read one from can never match —
        // even when its name hits two of the port's hints. The Eidos
        // match works because the real remote entry carries a .zip
        // URL; see eidosMatchesPokemonBlack.
        assertNull(PortMatcher.matchKind("Pokemon Black (NDS) (USA)", eidos))
        assertTrue(
            PortMatcher.matchKinds(
                "Pokemon Black (NDS) (USA)",
                listOf(eidos, arceus, pokopia, acnh, armyMen)
            ).isEmpty()
        )
    }

    @Test
    fun redumpEntryMatchesNothing() {
        // A Redump/TOSEC index entry has no extension in its name or
        // its URL (a clean-path download URL) — it is not a game file,
        // so it matches no port, however many hints its name contains.
        val entries = listOf(
            RemoteEntry(
                name = "Microsoft Xbox - A - Redump",
                url = "https://example.com/download/microsoft_xbox_a",
                isFolder = false
            ),
            RemoteEntry(
                name = "[TOSEC-ISO] 3DO",
                url = "https://example.com/download/tosec_3do",
                isFolder = false
            ),
            RemoteEntry(
                name = "3DS Content in CIA Format",
                url = "https://example.com/download/3ds_cia",
                isFolder = false
            )
        )
        val ports = listOf(eidos, pokopia, soh, tlouRemastered, darkKnightRises)
        for (port in ports) {
            assertTrue(
                "expected no match for ${port.id}",
                PortMatcher.matchRemoteEntries(entries, port).isEmpty()
            )
        }
        // And the same entries match nothing at the game level either.
        val games = groupPortsByGame(ports)
        val server = matchServerFilesForGame(
            listOf("json" to entries),
            games.flatMap { it.ports }
        )
        assertTrue(server.isEmpty())
    }

    @Test
    fun gameLevelMatchingExcludesMkvFiles() {
        val games = groupPortsByGame(listOf(tlouRemastered, darkKnightRises))
        val cloud = matchCloudFilesForGame(
            listOf(
                PremiumizeItem(name = "The Last Airbender (2010).mkv"),
                PremiumizeItem(
                    name = "The.Dark.Knight.2008.2160p.MAX.WEB-DL.DDP.5.1.DV.HDR.H.265-PiRaTeS.mkv"
                )
            ),
            games.flatMap { it.ports }
        )
        assertTrue(cloud.isEmpty())
    }

    @Test
    fun displayPlatformNameMapsOtherToOther() {
        assertEquals("Other", displayPlatformName("other", "Otra"))
        assertEquals("Other", displayPlatformName("Other", "Otra"))
        assertEquals("Nintendo 64", displayPlatformName("n64", "Nintendo 64"))
        assertEquals("Nintendo DS", displayPlatformName("ds", "Nintendo DS"))
        // The game groups under "Other", so the shelf, the game page,
        // and the platform filter all show it.
        val games = groupPortsByGame(listOf(darkKnightRises))
        assertEquals("Other", games.single().platform)
        assertEquals("The Dark Knight Rises", games.single().title)
    }
}

