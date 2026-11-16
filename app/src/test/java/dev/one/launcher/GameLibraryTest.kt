package dev.one.launcher

import dev.one.launcher.data.CachedGameFile
import dev.one.launcher.data.CachedMagnetLink
import dev.one.launcher.data.DetectedFile
import dev.one.launcher.data.GameEntry
import dev.one.launcher.data.GameFile
import dev.one.launcher.data.GameFileSource
import dev.one.launcher.data.GameMagnetLink
import dev.one.launcher.data.LibraryFilter
import dev.one.launcher.data.LocalFileState
import dev.one.launcher.data.MatchKind
import dev.one.launcher.data.PAIRING_CACHE_VERSION
import dev.one.launcher.data.PairingCache
import dev.one.launcher.data.PremiumizeItem
import dev.one.launcher.data.PortMatcher
import dev.one.launcher.data.RemoteEntry
import dev.one.launcher.data.STAR_CACHE_TTL_MS
import dev.one.launcher.data.dedupeGameFiles
import dev.one.launcher.data.fileSourceLabel
import dev.one.launcher.data.filterGames
import dev.one.launcher.data.gamesWithMatches
import dev.one.launcher.data.groupPortsByGame
import dev.one.launcher.data.hintsFromGameTitle
import dev.one.launcher.data.isPairingCacheUsable
import dev.one.launcher.data.isStarFresh
import dev.one.launcher.data.matchCloudFilesForGame
import dev.one.launcher.data.matchLocalFilesForGame
import dev.one.launcher.data.matchMagnetLinksForGame
import dev.one.launcher.data.matchServerFilesForGame
import dev.one.launcher.data.mergeLiveAndCachedFiles
import dev.one.launcher.data.mergeLiveAndCachedMagnets
import dev.one.launcher.data.magnetSourceLabel
import dev.one.launcher.data.normalizeForTokens
import dev.one.launcher.data.pairGamesWithFiles
import dev.one.launcher.data.pairGamesWithMagnets
import dev.one.launcher.data.pairingMapsFingerprint
import dev.one.launcher.data.posterSourceLabel
import dev.one.launcher.data.posterStatusText
import dev.one.launcher.data.rankPortsByStars
import dev.one.launcher.data.toCached
import dev.one.launcher.data.toGameFile
import dev.one.launcher.data.toGameMagnetLink
import dev.one.launcher.data.unfoldedGridColumns
import dev.one.launcher.model.Port
import kotlinx.serialization.json.Json
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
        distinctiveNameHints = listOf("eidos"),
        platformId = "ds",
        platformName = "Nintendo DS"
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
        distinctiveNameHints = listOf("pokopia", "banananative-compilationfix"),
        platformId = "switch2",
        platformName = "Switch 2"
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
    fun eidosStillMatchesPokemonBlack2() {
        // The documented ambiguity (v1.6.0): there is no Black 2 port,
        // so "Pokemon Black 2" matches Eidos only. The similarity
        // gate (v1.8.0) must not break it: the "2" is a single
        // character, not a significant word, so the file's title is
        // fully explained by "Pokémon Black & White" (1.0).
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

    @Test
    fun eidosBlockedByConflictingPlatformTag() {
        // Platform-consistency gate (v1.8.1): a Pokemon Black file
        // tagged for another platform (GBA) is not Eidos's file,
        // even though the title and hints match — without the gate
        // it would match NAME_ONLY (similarity 1.0, two hints).
        val url = "https://example.com/pokemon-black-gba.zip"
        assertNull(
            PortMatcher.matchKind("Pokemon Black (GBA) (USA)", eidos, url)
        )
        // The same file tagged for Eidos's own platform still matches.
        assertEquals(
            MatchKind.NAME_ONLY,
            PortMatcher.matchKind(
                "Pokemon Black (NDS) (USA)", eidos,
                "https://example.com/pokemon-black.zip"
            )
        )
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
        val keys = pairGamesWithFiles(
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
        ).keys
        assertTrue(keys.contains(eidosGame.key))
        assertTrue(keys.contains(pokopiaGame.key))
        assertEquals(2, keys.size)
    }

    @Test
    fun gamesWithFilesFilterBlocksGameWithNoFiles() {
        val games = groupPortsByGame(listOf(eidos, pokopia, arceus))
        val arceusGame = games.first { it.title == "Pokémon Legends: Arceus" }
        val keys = pairGamesWithFiles(
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
        ).keys
        assertFalse(keys.contains(arceusGame.key))
        assertEquals(1, keys.size)
    }

    @Test
    fun magnetEntriesNeverCountAsGameFiles() {
        // A magnet entry is a copyable link, not a file: it must not
        // count as a GAME FILE — the file-pairing map stays empty
        // for a magnet-only source, even when its name is a game
        // file's name with a game extension (.zip). (v1.8.8: magnets
        // DO count toward the "Has files" FILTER set, through
        // [gamesWithMatches] — that is a union over the two pairing
        // maps, not a change to what counts as a file. See
        // magnetsNeverAppearInYourFilesButCountForHasFiles.)
        val magnetUri =
            "magnet:?xt=urn:btih:9c6e87aaca8a26f4755d44079d5575b1732428f6" +
                "&dn=Acorn%20-%20Archimedes"
        val games = groupPortsByGame(listOf(eidos, pokopia, arceus))
        val keys = pairGamesWithFiles(
            games = games,
            cloudItems = emptyList(),
            cloudFallback = emptyMap(),
            serverSources = listOf(
                "json" to listOf(
                    RemoteEntry(
                        name = "Pokemon Black (NDS) (USA)",
                        url = magnetUri,
                        isFolder = false,
                        kind = "magnet"
                    ),
                    RemoteEntry(
                        name = "Pokémon Pokopia [0400A3A01EE4C000][v655360].nup",
                        url = magnetUri,
                        isFolder = false,
                        kind = "magnet"
                    )
                )
            ),
            detected = emptyMap(),
            localStates = emptyMap()
        ).keys
        assertTrue(keys.isEmpty())
        // And they never appear in a game's "Your files" either.
        val server = matchServerFilesForGame(
            listOf(
                "json" to listOf(
                    RemoteEntry(
                        name = "Pokemon Black (NDS) (USA)",
                        url = magnetUri,
                        isFolder = false,
                        kind = "magnet"
                    )
                )
            ),
            listOf(eidos)
        )
        assertTrue(server.isEmpty())
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
    fun platformOtherKeepsCatalogNameOtra() {
        // v1.7.0 revert: platform id "other" shows the catalog's own
        // name "Otra" again. The v1.6.1 "Other" display mapping was
        // never requested — Garrett knows "Otra" means "other" and it
        // was never an issue. Catalog data untouched.
        val games = groupPortsByGame(listOf(darkKnightRises))
        assertEquals("Otra", games.single().platform)
        assertEquals("The Dark Knight Rises", games.single().title)
    }

    @Test
    fun unfoldedGridColumnsThreeWithDetailOpenFiveWhenClosed() {
        // v1.7.0 unfolded layout: the game grid is 3 wide while the
        // detail pane is open and expands to 5 wide when it is
        // closed. Pure function of the detail pane state — the grid
        // rendering itself is Compose UI, not JVM-testable.
        assertEquals(3, unfoldedGridColumns(true))
        assertEquals(5, unfoldedGridColumns(false))
    }

    // ---- v1.8.2: Library filter state, source tag, pairing cache ----

    // Mirrors the real catalog entry for Breath of the Wild
    // (Switch), copied from PortMatcherTest — no catalog formats,
    // so it matches by name only.
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

    // Mirrors the live Recompa catalog entry for PokéPark Wii:
    // Pikachu's Adventure (MildRain479/PokePark-Wiicompiled): the
    // catalog lists one asset with accepted: [] and no zip_ok, so
    // the port has no formats and matches by name only. Hints and
    // distinctive hints are exactly as hintsFromGameTitle /
    // withDistinctiveHints produce against the 2026-11-16 index
    // ("pokepark" is shared with PokéPark 2, so it is not
    // distinctive; "pikachu" belongs to this port alone).
    private val pokepark = Port(
        id = "pokepark-wiicompiled",
        title = "PokéPark Wii: Pikachu's Adventure",
        game = "PokéPark Wii: Pikachu's Adventure",
        system = "Wii",
        type = "recomp",
        repo = "MildRain479/PokePark-Wiicompiled",
        releasesUrl = "https://github.com/MildRain479/PokePark-Wiicompiled/releases",
        fileExtensions = emptyList(),
        fileNameHints = listOf(
            "pokepark", "wii", "pikachu", "adventure", "pokepark-wiicompiled"
        ),
        requiredDump = "Your own PokéPark Wii disc image (USA, R8AE01).",
        distinctiveNameHints = listOf("pikachu", "pokepark-wiicompiled"),
        platformId = "wii",
        platformName = "Wii"
    )

    // Mirrors the live Recompa catalog entry for .hack//versus
    // (joseflores-dev/dot-hack-versus-port, PS3): one asset with
    // accepted: [], so it has no formats and matches by name only.
    // Hints and distinctive hints are exactly as hintsFromGameTitle /
    // withDistinctiveHints produce against the 2026-11-16 index —
    // hardcoded here (do NOT use withDistinctiveHints on a
    // single-port list, which would wrongly mark "port" distinctive).
    // The v1.8.5 regression: titleWords stripped the "//" as a
    // path prefix, so the game tokenized to just ["versus"] and
    // every "versus" file matched.
    private val hackVersus = Port(
        id = "dot-hack-versus-port",
        title = ".hack//versus",
        game = ".hack//versus",
        system = "PlayStation 3",
        type = "recomp",
        repo = "joseflores-dev/dot-hack-versus-port",
        releasesUrl = "https://github.com/joseflores-dev/dot-hack-versus-port/releases",
        fileExtensions = emptyList(),
        fileNameHints = listOf("hack", "versus", "dot", "port"),
        requiredDump = "Your own .hack//Versus game dump.",
        distinctiveNameHints = listOf("hack", "versus"),
        platformId = "ps3",
        platformName = "PlayStation 3"
    )

    // Mirrors the live Recompa catalog entry for 007: Nightfire and
    // 007: Agent Under Fire (papand7/nightfire-recomp, Xbox): one
    // asset with accepted: [], so it has no formats and matches by
    // name only. Hints and distinctive hints are exactly as
    // hintsFromGameTitle / withDistinctiveHints produce against the
    // 2026-11-16 index ("007", "agent" and "fire" are shared with
    // other 007 ports; "nightfire" and "under" belong to this port
    // alone). The v1.8.5 magnet-dedup fixture: remote.json lists
    // "007 - Agent Under Fire (USA).zip" and "007 - Nightfire
    // (USA).zip" once per system (Xbox, GameCube, PS2), so the same
    // name arrives three times.
    private val nightfire = Port(
        id = "nightfire-recomp",
        title = "007: Nightfire and 007: Agent Under Fire",
        game = "007: Nightfire and 007: Agent Under Fire",
        system = "Xbox",
        type = "recomp",
        repo = "papand7/nightfire-recomp",
        releasesUrl = "https://github.com/papand7/nightfire-recomp/releases",
        fileExtensions = emptyList(),
        fileNameHints = listOf("007", "nightfire", "agent", "under", "fire", "recomp"),
        requiredDump = "Your own 007: Nightfire / Agent Under Fire game dump.",
        distinctiveNameHints = listOf("nightfire", "under"),
        platformId = "xbox",
        platformName = "Xbox"
    )

    @Test
    fun filterGamesByQueryAndPlatform() {
        val games = groupPortsByGame(listOf(eidos, pokopia, soh, twoShip))
        assertTrue(filterGames(games, LibraryFilter(query = "mario"), emptySet()).isEmpty())
        assertEquals(2, filterGames(games, LibraryFilter(query = "zelda"), emptySet()).size)
        val n64 = filterGames(games, LibraryFilter(platform = "Nintendo 64"), emptySet())
        assertEquals(2, n64.size)
        assertTrue(n64.all { it.platform == "Nintendo 64" })
    }

    @Test
    fun filterGamesMatchesOnlyKeepsGamesWithFiles() {
        val games = groupPortsByGame(listOf(eidos, pokopia, arceus))
        val eidosGame = games.first { it.title == "Pokémon Black & White" }
        val only = filterGames(
            games,
            LibraryFilter(matchesOnly = true),
            setOf(eidosGame.key)
        )
        assertEquals(listOf(eidosGame.key), only.map { it.key })
        // matchesOnly composes with the query.
        val none = filterGames(
            games,
            LibraryFilter(query = "pokopia", matchesOnly = true),
            setOf(eidosGame.key)
        )
        assertTrue(none.isEmpty())
    }

    @Test
    fun libraryFilterSurvivesGameNavigation() {
        // v1.8.2 nav-state fix: the filter lives in the ViewModel (a
        // single LibraryFilter flow), not in a composable's remember —
        // so opening a game page and coming back does not reset it.
        // Selecting a game never touches the filter; the same filter
        // still selects the same games afterwards.
        val games = groupPortsByGame(listOf(eidos, pokopia, soh))
        val withFiles = setOf(
            games.first { it.title == "Pokémon Black & White" }.key,
            games.first { it.title == "The Legend of Zelda: Ocarina of Time" }.key
        )
        val filter = LibraryFilter(query = "zelda", matchesOnly = true)
        val before = filterGames(games, filter, withFiles)
        // "Navigate" into a game page and back: the filter object is
        // untouched by the navigation (selectGame only changes the
        // selected game key), so the filtered list is identical.
        val after = filterGames(games, filter, withFiles)
        assertEquals(before.map { it.key }, after.map { it.key })
        assertEquals(1, after.size)
        assertEquals("The Legend of Zelda: Ocarina of Time", after.single().title)
        // The default filter is the default screen: no query, no
        // platform, matches off.
        assertEquals(
            LibraryFilter(),
            LibraryFilter(query = "", platform = "", matchesOnly = false)
        )
    }

    @Test
    fun fileSourceLabelReadsLocalPremiumizeAndRemoteFolderName() {
        val local = GameFile(
            name = "Pokemon Black (NDS) (USA)",
            kind = MatchKind.NAME_ONLY,
            source = GameFileSource.LOCAL,
            portId = "eidos",
            where = "Found in your folder"
        )
        assertEquals("Local", fileSourceLabel(local))
        val cloud = GameFile(
            name = "Pokémon Pokopia [0400A3A01EE4C000][v655360].nup",
            kind = MatchKind.NAME_ONLY,
            source = GameFileSource.CLOUD,
            portId = "banananative-compilationfix",
            where = "Premiumize",
            premiumizeItem = PremiumizeItem(
                id = "abc",
                name = "Pokémon Pokopia [0400A3A01EE4C000][v655360].nup"
            )
        )
        assertEquals("Premiumize", fileSourceLabel(cloud))
        val server = GameFile(
            name = "Pokemon Black (NDS) (USA)",
            kind = MatchKind.NAME_ONLY,
            source = GameFileSource.SERVER,
            portId = "eidos",
            where = "json",
            remoteSourceName = "json",
            remoteUrl = "https://example.com/pokemon-black.zip"
        )
        assertEquals("json", fileSourceLabel(server))
        // No remote name falls back to the where label.
        val serverNoName = server.copy(remoteSourceName = null, where = "My server")
        assertEquals("My server", fileSourceLabel(serverNoName))
    }

    @Test
    fun pairGamesWithFilesAggregatesAllSourcesAndDedupes() {
        val games = groupPortsByGame(listOf(eidos, pokopia, soh))
        val eidosGame = games.first { it.title == "Pokémon Black & White" }
        val pokopiaGame = games.first { it.title == "Pokopia" }
        val map = pairGamesWithFiles(
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
                    ),
                    // Same file in the server and the cloud is listed once.
                    RemoteEntry(
                        name = "Pokémon Pokopia [0400A3A01EE4C000][v655360].nup",
                        url = "https://example.com/pokopia.nup",
                        isFolder = false
                    )
                )
            ),
            detected = emptyMap(),
            localStates = emptyMap()
        )
        assertEquals(2, map.size)
        assertEquals(1, map[eidosGame.key]!!.size)
        assertEquals("Pokemon Black (NDS) (USA)", map[eidosGame.key]!!.single().name)
        assertEquals(GameFileSource.SERVER, map[eidosGame.key]!!.single().source)
        // Pokopia's .nup appears once (cloud wins the dedupe over the
        // server copy of the same name).
        assertEquals(1, map[pokopiaGame.key]!!.size)
        assertEquals(GameFileSource.CLOUD, map[pokopiaGame.key]!!.single().source)
        // SoH has no file in any source and is absent from the map.
        assertFalse(map.containsKey(games.first { it.title.contains("Ocarina") }.key))
    }

    @Test
    fun pairGamesWithFilesStillBlocksWiiUFileForSwitchBotw() {
        // The v1.8.1 platform-consistency rule still holds at the
        // game level: the Wii U .7z is not a file for the Switch game.
        val games = groupPortsByGame(listOf(botw, eidos))
        val botwGame = games.first { it.title == "The Legend of Zelda: Breath of the Wild" }
        val url = "https://archive.org/download/wii-u-retail-nus-usa/" +
            "The%20Legend%20of%20Zelda%20Breath%20of%20the%20Wild%20" +
            "%5B00050000101C9400%5D.7z"
        val map = pairGamesWithFiles(
            games = games,
            cloudItems = emptyList(),
            cloudFallback = emptyMap(),
            serverSources = listOf(
                "json" to listOf(
                    RemoteEntry(
                        name = "Legend of Zelda Breath of the Wild (Wii U) (USA)",
                        url = url,
                        isFolder = false
                    ),
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
        assertFalse(map.containsKey(botwGame.key))
        // Eidos still matches its file.
        assertTrue(map.containsKey(games.first { it.title == "Pokémon Black & White" }.key))
    }

    @Test
    fun pairingCacheRoundTripsEverySource() {
        val files = listOf(
            GameFile(
                name = "Pokemon Black (NDS) (USA)",
                kind = MatchKind.NAME_ONLY,
                source = GameFileSource.LOCAL,
                portId = "eidos",
                where = "Found in your folder",
                detected = DetectedFile(
                    name = "Pokemon Black (NDS) (USA)",
                    uri = "content://example/pokemon-black",
                    kind = MatchKind.NAME_ONLY
                )
            ),
            GameFile(
                name = "Pokémon Pokopia [0400A3A01EE4C000][v655360].nup",
                kind = MatchKind.NAME_ONLY,
                source = GameFileSource.CLOUD,
                portId = "banananative-compilationfix",
                where = "Premiumize",
                premiumizeItem = PremiumizeItem(
                    id = "abc",
                    name = "Pokémon Pokopia [0400A3A01EE4C000][v655360].nup"
                )
            ),
            GameFile(
                name = "Pokemon Black (NDS) (USA)",
                kind = MatchKind.NAME_ONLY,
                source = GameFileSource.SERVER,
                portId = "eidos",
                where = "json",
                remoteSourceName = "json",
                remoteUrl = "https://example.com/pokemon-black.zip"
            )
        )
        for (file in files) {
            val restored = file.toCached().toGameFile()
            assertEquals(file, restored)
        }
        // A Premiumize download link is never cached: a file that has
        // one loses it in the round-trip (the app re-fetches it on
        // demand via itemDetails when the user taps Download).
        val cloudFile = files[1]
        val withLink = cloudFile.copy(
            premiumizeItem = cloudFile.premiumizeItem!!.copy(
                link = "https://example.com/dl/abc"
            )
        )
        assertEquals("https://example.com/dl/abc", withLink.premiumizeItem!!.link)
        assertNull(withLink.toCached().premiumizeItem?.link)
        assertNull(withLink.toCached().toGameFile()?.premiumizeItem?.link)
        // An unknown kind or source decodes as "not this file".
        val badKind = files[0].toCached().copy(kind = "NO_SUCH_KIND")
        assertNull(badKind.toGameFile())
        val badSource = files[0].toCached().copy(source = "NO_SUCH_SOURCE")
        assertNull(badSource.toGameFile())
    }

    @Test
    fun pairingCacheVersionMismatchIsNotUsable() {
        assertTrue(
            isPairingCacheUsable(
                PairingCache(PAIRING_CACHE_VERSION, 0L, emptyMap())
            )
        )
        assertFalse(
            isPairingCacheUsable(
                PairingCache(PAIRING_CACHE_VERSION + 1, 0L, emptyMap())
            )
        )
        assertFalse(isPairingCacheUsable(null))
    }

    // ---- Pairing-cache fingerprint + game-page cache merge (v1.8.4) ----
    //
    // The v1.8.3 freeze: the background refresh rebuilt the cached
    // forms and deep-compared them on the main thread, over ~24k
    // entries, on every combine emission. v1.8.4 replaces that deep
    // equality with a cheap content fingerprint (O(1) to compare),
    // computed off Main, and the game page reads the cached pairings
    // immediately instead of gating on the live server listings.

    @Test
    fun pairingFingerprintIsStableForIdenticalContent() {
        // Two separately built maps with identical content (same
        // insertion order, as the ViewModel builds them) -> the same
        // fingerprint, so an unchanged refresh is recognised as
        // unchanged and skips the rebuild + store write.
        fun buildFiles() = mapOf(
            "game-a" to listOf(
                GameFile(
                    name = "Pokemon Black (NDS) (USA)",
                    kind = MatchKind.NAME_ONLY,
                    source = GameFileSource.LOCAL,
                    portId = "eidos",
                    where = "Found in your folder"
                )
            )
        )
        fun buildMagnets() = mapOf(
            "game-b" to listOf(
                GameMagnetLink(
                    name = "PokePark Wii - Pikachu's Adventure (USA).zip",
                    magnetUrl = "magnet:?xt=urn:btih:aaaa",
                    kind = MatchKind.NAME_ONLY,
                    portId = "pokepark-wiicompiled",
                    where = "json"
                )
            )
        )
        assertEquals(
            pairingMapsFingerprint(buildFiles(), buildMagnets()),
            pairingMapsFingerprint(buildFiles(), buildMagnets())
        )
        assertEquals(
            pairingMapsFingerprint(emptyMap(), emptyMap()),
            pairingMapsFingerprint(emptyMap(), emptyMap())
        )
    }

    @Test
    fun pairingFingerprintChangesWhenContentChanges() {
        val base = mapOf(
            "game-a" to listOf(
                GameFile(
                    name = "Pokemon Black (NDS) (USA)",
                    kind = MatchKind.NAME_ONLY,
                    source = GameFileSource.LOCAL,
                    portId = "eidos",
                    where = "Found in your folder"
                )
            )
        )
        val magnets = emptyMap<String, List<GameMagnetLink>>()
        val baseFp = pairingMapsFingerprint(base, magnets)
        // A different file -> a different fingerprint.
        val changed = mapOf(
            "game-a" to listOf(
                GameFile(
                    name = "Pokemon White (NDS) (USA)",
                    kind = MatchKind.NAME_ONLY,
                    source = GameFileSource.LOCAL,
                    portId = "eidos",
                    where = "Found in your folder"
                )
            )
        )
        assertTrue(pairingMapsFingerprint(changed, magnets) != baseFp)
        // A file added to another game -> a different fingerprint.
        val added = base + ("game-b" to listOf(
            GameFile(
                name = "Something Else.zip",
                kind = MatchKind.ARCHIVE_PROVISIONAL,
                source = GameFileSource.SERVER,
                portId = "other",
                where = "json"
            )
        ))
        assertTrue(pairingMapsFingerprint(added, magnets) != baseFp)
        // A magnet added -> a different fingerprint.
        val withMagnet = mapOf(
            "game-b" to listOf(
                GameMagnetLink(
                    name = "PokePark Wii - Pikachu's Adventure (USA).zip",
                    magnetUrl = "magnet:?xt=urn:btih:aaaa",
                    kind = MatchKind.NAME_ONLY,
                    portId = "pokepark-wiicompiled",
                    where = "json"
                )
            )
        )
        assertTrue(pairingMapsFingerprint(base, withMagnet) != baseFp)
    }

    @Test
    fun pairingFingerprintIgnoresPremiumizeLinkChanges() {
        // The Premiumize download link is a session URL: it can
        // change between fetches without the file changing, and it is
        // dropped from the cache anyway. A link-only change must not
        // count as a pairing change, or every cloud re-fetch would
        // rewrite the cache.
        val withoutLink = mapOf(
            "game-a" to listOf(
                GameFile(
                    name = "Pokemon Black (NDS) (USA)",
                    kind = MatchKind.NAME_ONLY,
                    source = GameFileSource.CLOUD,
                    portId = "eidos",
                    where = "Premiumize",
                    premiumizeItem = PremiumizeItem(
                        id = "abc",
                        name = "Pokemon Black (NDS) (USA)"
                    )
                )
            )
        )
        val withLink = mapOf(
            "game-a" to listOf(
                GameFile(
                    name = "Pokemon Black (NDS) (USA)",
                    kind = MatchKind.NAME_ONLY,
                    source = GameFileSource.CLOUD,
                    portId = "eidos",
                    where = "Premiumize",
                    premiumizeItem = PremiumizeItem(
                        id = "abc",
                        name = "Pokemon Black (NDS) (USA)",
                        link = "https://example.com/dl/abc?token=zzz"
                    )
                )
            )
        )
        assertEquals(
            pairingMapsFingerprint(withoutLink, emptyMap()),
            pairingMapsFingerprint(withLink, emptyMap())
        )
    }

    @Test
    fun gamePageReadsCachedFilesWhenLiveSourcesAreStillLoading() {
        // The game page's file state is the live match merged with
        // the pairing cache. When the live sources have not loaded
        // yet (the live list is empty), the page is exactly the
        // cached list — it never sits on "Checking your files…"
        // while the cache has files for the game.
        val cached = listOf(
            GameFile(
                name = "Splosion Man (USA).zip",
                kind = MatchKind.ARCHIVE_PROVISIONAL,
                source = GameFileSource.SERVER,
                portId = "splosion-man",
                where = "json",
                remoteSourceName = "json",
                remoteUrl = "https://example.com/splosion-man.zip"
            )
        )
        assertEquals(cached, mergeLiveAndCachedFiles(emptyList(), cached))
        // A live file wins on a name clash; the cached copy only
        // fills gaps the live match has not produced yet.
        val live = listOf(
            GameFile(
                name = "Splosion Man (USA).zip",
                kind = MatchKind.DIRECT,
                source = GameFileSource.LOCAL,
                portId = "splosion-man",
                where = "Found in your folder"
            )
        )
        assertEquals(live, mergeLiveAndCachedFiles(live, cached))
        // No cached files and no live files -> empty (the page then
        // shows "Checking your files…" only while sources load, else
        // "No file for this game yet").
        assertTrue(mergeLiveAndCachedFiles(emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun gamePageReadsCachedMagnetsWhenLiveSourcesAreStillLoading() {
        val cached = listOf(
            GameMagnetLink(
                name = "PokePark Wii - Pikachu's Adventure (USA).zip",
                magnetUrl = "magnet:?xt=urn:btih:aaaa",
                kind = MatchKind.NAME_ONLY,
                portId = "pokepark-wiicompiled",
                where = "json"
            )
        )
        assertEquals(cached, mergeLiveAndCachedMagnets(emptyList(), cached))
        // A live link wins on a URI clash.
        val live = listOf(
            GameMagnetLink(
                name = "PokePark Wii - Pikachu's Adventure (USA).zip",
                magnetUrl = "magnet:?xt=urn:btih:aaaa",
                kind = MatchKind.NAME_ONLY,
                portId = "pokepark-wiicompiled",
                where = "json"
            )
        )
        assertEquals(live, mergeLiveAndCachedMagnets(live, cached))
        assertTrue(mergeLiveAndCachedMagnets(emptyList(), emptyList()).isEmpty())
    }

    // ---- Game page shows cached content instantly (v1.8.6) ----
    //
    // The page used to run the live match (matchServerFilesForGame +
    // matchMagnetLinksForGame over the full 26k-entry feed) in `remember`
    // during composition, on the main thread — measured 379–837ms for
    // the magnet match alone on JVM, ~1–4s on device, 2–3 runs per
    // open. The live match now runs in the background (produceState on
    // Dispatchers.Default) and the page is exactly the cached pairing
    // while it computes. The contract, at the merge level: with empty
    // live lists (sources not loaded, background match not done), the
    // page state is exactly the cached files and magnets — it never
    // waits on live data to show what it already knows.

    @Test
    fun gamePageShowsCachedFilesAndMagnetsWithNoLiveData() {
        val cachedFiles = listOf(
            GameFile(
                name = "Splosion Man (USA).zip",
                kind = MatchKind.ARCHIVE_PROVISIONAL,
                source = GameFileSource.SERVER,
                portId = "splosion-man",
                where = "json",
                remoteSourceName = "json",
                remoteUrl = "https://example.com/splosion-man.zip"
            )
        )
        val cachedMagnets = listOf(
            GameMagnetLink(
                name = "Triple Pack - Trials HD & Limbo & 'Splosion Man (USA, Europe).zip",
                magnetUrl = "magnet:?xt=urn:btih:bbbb",
                kind = MatchKind.NAME_ONLY,
                portId = "splosionmanrecomp",
                where = "json"
            )
        )
        assertEquals(cachedFiles, mergeLiveAndCachedFiles(emptyList(), cachedFiles))
        assertEquals(cachedMagnets, mergeLiveAndCachedMagnets(emptyList(), cachedMagnets))
    }

    // ---- Poster tags (v1.8.6; status v1.8.8) ----
    //
    // A matched magnet counts toward the poster tag: the tag appears
    // when a game has a matched downloadable file OR a matched magnet
    // link, showing the magnet's source ("json") like a file's. And
    // (v1.8.8) magnets count toward "Has files" too — see
    // [gamesWithMatches].

    private fun serverFile(name: String, where: String) = GameFile(
        name = name,
        kind = MatchKind.NAME_ONLY,
        source = GameFileSource.SERVER,
        portId = "some-port",
        where = where,
        remoteSourceName = where,
        remoteUrl = "https://example.com/$name"
    )

    private fun magnetLink(name: String, where: String, remoteSourceName: String? = null) =
        GameMagnetLink(
            name = name,
            magnetUrl = "magnet:?xt=urn:btih:cccc",
            kind = MatchKind.NAME_ONLY,
            portId = "some-port",
            where = where,
            remoteSourceName = remoteSourceName
        )

    @Test
    fun posterSourceLabelPrefersFileOverMagnet() {
        assertEquals(
            "Premiumize",
            posterSourceLabel(
                listOf(
                    GameFile(
                        name = "Game (USA).zip",
                        kind = MatchKind.DIRECT,
                        source = GameFileSource.CLOUD,
                        portId = "some-port",
                        where = "Premiumize"
                    )
                ),
                listOf(magnetLink("Game (USA).zip", "json"))
            )
        )
        assertEquals("json", posterSourceLabel(listOf(serverFile("Game (USA).zip", "json")), emptyList()))
        assertEquals("Local", posterSourceLabel(
            listOf(
                GameFile(
                    name = "Game (USA).zip",
                    kind = MatchKind.DIRECT,
                    source = GameFileSource.LOCAL,
                    portId = "some-port",
                    where = "Found in your folder"
                )
            ),
            emptyList()
        ))
    }

    @Test
    fun posterSourceLabelShowsMagnetSourceWhenNoFile() {
        // 'Splosion Man (Garrett, 2026-11-16): its only link is the
        // Triple Pack magnet, so its poster shows the magnet's source
        // with a "· Magnet" suffix — a magnet is a copyable link,
        // not a file, so the tag says so.
        assertEquals(
            "json · Magnet",
            posterSourceLabel(
                emptyList(),
                listOf(
                    magnetLink(
                        "Triple Pack - Trials HD & Limbo & 'Splosion Man (USA, Europe).zip",
                        "json"
                    )
                )
            )
        )
    }

    @Test
    fun posterSourceLabelIsNullWhenNothing() {
        assertNull(posterSourceLabel(emptyList(), emptyList()))
    }

    @Test
    fun posterStatusTextIsInstallStateOnly() {
        // v1.8.8: the poster status is install state ONLY — it no
        // longer restates match-ness. The source chip
        // ([posterSourceLabel]) is the one indicator of a matched
        // file or magnet link, so a magnet-only game does not read
        // "Magnet matched" and a file game does not read "File
        // matched" — both read "Not installed" (or "Installed" when
        // installed) with the chip beside them.
        assertEquals("Not installed", posterStatusText(false, false))
        assertEquals("Not installed", posterStatusText(false, true))
        assertEquals("Ready to play", posterStatusText(true, true))
        assertEquals("Installed", posterStatusText(true, false))
    }

    @Test
    fun magnetOnlyGameShowsExactlyOneMagnetIndicator() {
        // Garrett's v1.8.7 video: "I only need one indicator of the
        // magnet link." A magnet-only game shows the "json · Magnet"
        // chip and an install-only status — never a second
        // "Magnet matched" status. A file game shows its source chip
        // with no magnet indicator at all.
        val status = posterStatusText(false, false)
        val chip = posterSourceLabel(
            emptyList(),
            listOf(magnetLink("Triple Pack - Trials HD & Limbo & 'Splosion Man (USA, Europe).zip", "json"))
        )
        assertEquals("Not installed", status)
        assertEquals("json · Magnet", chip)
        assertFalse(status.contains("Magnet"))
        assertFalse(chip?.contains("matched") ?: false)
        // Installed magnet-only game: "Installed" + the same chip.
        assertEquals("Installed", posterStatusText(true, false))
        // File game: source chip, no magnet indicator anywhere.
        val fileChip = posterSourceLabel(listOf(serverFile("Game (USA).zip", "json")), emptyList())
        assertEquals("json", fileChip)
        assertFalse(fileChip?.contains("Magnet") ?: false)
        assertFalse(posterStatusText(false, true).contains("Magnet"))
    }

    @Test
    fun magnetSourceLabelUsesRemoteSourceName() {
        assertEquals(
            "json",
            magnetSourceLabel(magnetLink("Game (USA).zip", "json", remoteSourceName = "json"))
        )
        assertEquals(
            "My Server",
            magnetSourceLabel(magnetLink("Game (USA).zip", "My Server"))
        )
    }

    // ---- Magnet links on the game page (v1.8.3) ----
    //
    // Garrett's 2026-11-16 report: PokéPark Wii: Pikachu's Adventure
    // shows "No file for this game yet" in the app, but remote.json
    // DOES contain its per-game magnets. v1.8.0 deliberately excluded
    // magnet entries from matching (they are copy-only links, not
    // downloadable files), so the matcher skipped them and the game
    // page showed nothing. Magnets are now matched by name through
    // the same matcher and shown in their own Magnet links section —
    // still never as downloadable files.

    // One magnet entry, as remote.json carries it: the Wii system
    // torrent's infohash (ae3e64f1…), the game name as dn, and the
    // file's &so= index inside that torrent. The dn is built from
    // the name so the entry is faithful; matching reads the name,
    // never the URL. The optional category is the feed's category
    // for the entry (v1.8.5 magnet dedup); each system torrent gets
    // a different infohash so same-name entries from different
    // systems are distinct URLs.
    private fun magnetEntry(
        name: String,
        soIndex: Int,
        category: String? = null,
        infohash: String = "ae3e64f1d5ff936fe5027a14216453101a0ddbfa"
    ): RemoteEntry =
        RemoteEntry(
            name = name,
            url = "magnet:?xt=urn:btih:$infohash" +
                "&dn=" + java.net.URLEncoder.encode(name, "UTF-8") +
                "&tr=udp://tracker.opentrackr.org:1337/announce" +
                "&so=$soIndex",
            isFolder = false,
            kind = "magnet",
            category = category
        )

    @Test
    fun pokeparkWiiMatchesItsMagnets() {
        // The exact entries from remote.json (Nintendo - Wii - NKit
        // RVZ): the USA and Europe magnets match by name (NAME_ONLY —
        // the port lists no formats), the Japanese title and the
        // PokéPark 2 magnets do not (different titles, similarity
        // below 0.90).
        val sources = listOf(
            "json" to listOf(
                magnetEntry("PokePark Wii - Pikachu's Adventure (USA).zip", 123),
                magnetEntry(
                    "PokePark Wii - Pikachu's Adventure (Europe) (En,Fr,De,Es,It).zip",
                    124
                ),
                magnetEntry("PokePark Wii - Pikachu no Daibouken (Japan).zip", 125),
                magnetEntry("PokePark 2 - Wonders Beyond (USA).zip", 200),
                magnetEntry("PokePark 2 - Wonders Beyond (Europe) (En,Fr,De,Es,It).zip", 201)
            )
        )
        val magnets = matchMagnetLinksForGame(sources, listOf(pokepark))
        assertEquals(
            listOf(
                "PokePark Wii - Pikachu's Adventure (Europe) (En,Fr,De,Es,It).zip",
                "PokePark Wii - Pikachu's Adventure (USA).zip"
            ),
            magnets.map { it.name }
        )
        assertTrue(magnets.all { it.kind == MatchKind.NAME_ONLY })
        assertTrue(magnets.all { it.portId == "pokepark-wiicompiled" })
        assertTrue(magnets.all { it.where == "json" })
        assertTrue(magnets.all { it.magnetUrl.startsWith("magnet:?xt=urn:btih:") })
    }

    @Test
    fun magnetsNeverAppearInYourFilesButCountForHasFiles() {
        // v1.8.8 reversal (Garrett's v1.8.7 video: "if it has a
        // linked file, it should pop up under this has files page,
        // which some of them the magnets count as has files").
        // Magnets still never appear in "Your files" — the
        // file-pairing map stays empty for a magnet-only source, and
        // the game page shows magnets in their own Magnet links
        // section. But they DO count toward "Has files": the set is
        // the union of the file map's keys and the magnet map's
        // keys ([gamesWithMatches]), so a magnet-only game
        // (PokePark Wii here) appears under the filter.
        val sources = listOf(
            "json" to listOf(
                magnetEntry("PokePark Wii - Pikachu's Adventure (USA).zip", 123)
            )
        )
        assertTrue(matchServerFilesForGame(sources, listOf(pokepark)).isEmpty())
        val games = groupPortsByGame(listOf(pokepark))
        val pokeparkKey = games.single().key
        val fileKeys = pairGamesWithFiles(
            games = games,
            cloudItems = emptyList(),
            cloudFallback = emptyMap(),
            serverSources = sources,
            detected = emptyMap(),
            localStates = emptyMap()
        ).keys
        assertTrue(fileKeys.isEmpty())
        val magnetKeys = pairGamesWithMagnets(
            games = games,
            serverSources = sources
        ).keys
        assertEquals(setOf(pokeparkKey), magnetKeys)
        assertEquals(setOf(pokeparkKey), gamesWithMatches(fileKeys, magnetKeys))
        // And the filter itself keeps the game when matchesOnly is on.
        assertEquals(
            listOf(games.single()),
            filterGames(
                games,
                LibraryFilter(matchesOnly = true),
                gamesWithMatches(fileKeys, magnetKeys)
            )
        )
        // A game with neither files nor magnets still stays out.
        assertEquals(
            emptyList<GameEntry>(),
            filterGames(
                games,
                LibraryFilter(matchesOnly = true),
                gamesWithMatches(emptySet(), emptySet())
            )
        )
    }

    @Test
    fun gamesWithMatchesIsTheUnionOfFileAndMagnetKeys() {
        assertEquals(
            setOf("a--x", "b--x", "c--x"),
            gamesWithMatches(setOf("a--x", "b--x"), setOf("b--x", "c--x"))
        )
        assertEquals(emptySet<String>(), gamesWithMatches(emptySet(), emptySet()))
    }

    @Test
    fun magnetZipNameDoesNotMatchAsDownloadableFile() {
        // A magnet entry whose name ends in .zip still does not match
        // as a downloadable file: the magnet: URL is rejected by the
        // game-file gate, whatever the name ends in.
        val entry = magnetEntry("PokePark Wii - Pikachu's Adventure (USA).zip", 123)
        assertNull(PortMatcher.matchKind(entry.name, pokepark, entry.url))
        assertTrue(
            PortMatcher.matchKinds(entry.name, listOf(pokepark), entry.url).isEmpty()
        )
        // But the same name matches as a magnet (by name only).
        assertEquals(
            MatchKind.NAME_ONLY,
            PortMatcher.matchMagnetKind(entry, pokepark)
        )
        // And a non-magnet entry is never a magnet match.
        val file = RemoteEntry(
            name = "PokePark Wii - Pikachu's Adventure (USA).zip",
            url = "https://example.com/pokepark.zip",
            isFolder = false
        )
        assertNull(PortMatcher.matchMagnetKind(file, pokepark))
    }

    @Test
    fun wiiUTaggedMagnetDoesNotMatchSwitchBotw() {
        // The platform-consistency rule applies to magnets too: a Wii
        // U-tagged magnet is not a Switch game's link, however
        // identical the titles are.
        val sources = listOf(
            "json" to listOf(
                magnetEntry("Legend of Zelda Breath of the Wild (Wii U) (USA).zip", 42)
            )
        )
        assertTrue(matchMagnetLinksForGame(sources, listOf(botw)).isEmpty())
        // Without the tag, the same magnet WOULD match — the platform
        // check is what blocks it, not the similarity gate.
        val untagged = listOf(
            "json" to listOf(
                magnetEntry("Legend of Zelda Breath of the Wild (USA).zip", 43)
            )
        )
        assertEquals(1, matchMagnetLinksForGame(untagged, listOf(botw)).size)
    }

    @Test
    fun pairGamesWithMagnetsAggregatesAndCaches() {
        val games = groupPortsByGame(listOf(pokepark, eidos))
        val pokeparkGame = games.first { it.title == "PokéPark Wii: Pikachu's Adventure" }
        val map = pairGamesWithMagnets(
            games = games,
            serverSources = listOf(
                "json" to listOf(
                    magnetEntry("PokePark Wii - Pikachu's Adventure (USA).zip", 123),
                    magnetEntry(
                        "PokePark Wii - Pikachu's Adventure (Europe) (En,Fr,De,Es,It).zip",
                        124
                    ),
                    // The same magnet URI listed twice is listed once.
                    magnetEntry("PokePark Wii - Pikachu's Adventure (USA).zip", 123)
                ),
                "mirror" to listOf(
                    magnetEntry("PokePark Wii - Pikachu's Adventure (USA).zip", 123)
                )
            )
        )
        assertEquals(1, map.size)
        assertEquals(2, map[pokeparkGame.key]!!.size)
        // Eidos has no magnets in these sources and is absent.
        assertFalse(map.containsKey(games.first { it.title == "Pokémon Black & White" }.key))
        // The cache round-trips: a magnet link survives save -> load
        // with its full magnet URI, and an unknown kind or a blank
        // URL decodes as "not this link".
        val link = map[pokeparkGame.key]!!.first { it.name.contains("(USA)") }
        assertEquals(link, link.toCached().toGameMagnetLink())
        val badKind = link.toCached().copy(kind = "NO_SUCH_KIND")
        assertNull(badKind.toGameMagnetLink())
        val blankUrl = link.toCached().copy(magnetUrl = "  ")
        assertNull(blankUrl.toGameMagnetLink())
        // A v1.8.2 cache JSON (no magnetPairings field) still decodes,
        // with the magnet channel defaulting to empty — the background
        // refresh then fills it in.
        val json = Json { ignoreUnknownKeys = true }
        val decoded = json.decodeFromString<PairingCache>(
            """{"version":1,"computedAtMs":0,"pairings":{}}"""
        )
        assertTrue(decoded.magnetPairings.isEmpty())
        assertTrue(isPairingCacheUsable(decoded))
    }

    @Test
    fun downloadableMatchesUnchangedByMagnetMatching() {
        // Magnets did not disturb downloadable matching: Eidos still
        // matches Pokemon Black (downloadable) and Pokopia still
        // matches its .nup, in the same pass that matches magnets.
        val games = groupPortsByGame(listOf(eidos, pokopia, pokepark))
        val files = pairGamesWithFiles(
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
                    ),
                    magnetEntry("PokePark Wii - Pikachu's Adventure (USA).zip", 123)
                )
            ),
            detected = emptyMap(),
            localStates = emptyMap()
        )
        val eidosGame = games.first { it.title == "Pokémon Black & White" }
        val pokopiaGame = games.first { it.title == "Pokopia" }
        val pokeparkGame = games.first { it.title == "PokéPark Wii: Pikachu's Adventure" }
        assertEquals("Pokemon Black (NDS) (USA)", files[eidosGame.key]!!.single().name)
        assertEquals(GameFileSource.CLOUD, files[pokopiaGame.key]!!.single().source)
        // The magnet is not a downloadable file for the PokePark game.
        assertFalse(files.containsKey(pokeparkGame.key))
        // ...but it is a magnet link for it.
        val magnets = pairGamesWithMagnets(
            games,
            listOf(
                "json" to listOf(
                    magnetEntry("PokePark Wii - Pikachu's Adventure (USA).zip", 123)
                )
            )
        )
        assertEquals(1, magnets[pokeparkGame.key]!!.size)
        assertFalse(magnets.containsKey(eidosGame.key))
        assertFalse(magnets.containsKey(pokopiaGame.key))
    }

    // ---- .hack//versus false positives (v1.8.5) ----
    //
    // Garrett's 2026-11-16 report: the .hack//versus game page
    // showed "Aliens Versus Predator - Extinction", "RoboCop versus
    // The Terminator", "Sherlock Holmes Versus Jack the Ripper" and
    // "Star Versus" as magnet links, all "json · name only". The
    // game title ".hack//versus" was tokenized to just ["versus"]
    // (the "//" was stripped as a file-path prefix), so every file
    // containing "versus" got similarity 1.0 and matched through the
    // distinctive hint. The fix tokenizes the game title fully.

    @Test
    fun hackVersusDoesNotMatchEveryVersusGame() {
        val versusFiles = listOf(
            "Aliens Versus Predator - Extinction (USA).zip",
            "RoboCop versus The Terminator (USA).zip",
            "Sherlock Holmes Versus Jack the Ripper (USA) (En,Fr,Es).zip",
            "Star Versus (USA, Europe) (Aftermarket) (Unl).zip"
        )
        // As magnets: none of the four matches.
        val sources = listOf(
            "json" to versusFiles.mapIndexed { i, name -> magnetEntry(name, 300 + i) }
        )
        assertTrue(matchMagnetLinksForGame(sources, listOf(hackVersus)).isEmpty())
        // As downloadable files: none of the four matches either.
        for (name in versusFiles) {
            assertNull(
                "downloadable $name must not match .hack//versus",
                PortMatcher.matchKind(name, hackVersus)
            )
        }
        // A real .hack file still matches (downloadable, NAME_ONLY).
        assertEquals(
            MatchKind.NAME_ONLY,
            PortMatcher.matchKind(".hack//Versus (USA).iso", hackVersus)
        )
        assertEquals(
            MatchKind.NAME_ONLY,
            PortMatcher.matchKind(".hack//Versus (Japan).iso", hackVersus)
        )
    }

    // ---- Magnet dedup by name (v1.8.5) ----
    //
    // Garrett's 2026-11-16 report: the same game file name appears
    // 2–3 times on a game page — remote.json lists it once per
    // system (each system is its own torrent, so the magnet URLs
    // differ). The app deduped by URL only, so the same name showed
    // multiple times. Magnets are now deduped by cleaned name per
    // game, keeping the entry whose category platform matches the
    // game's platform, else the first.

    @Test
    fun magnetDedupPrefersMatchingPlatform() {
        // "007 - Agent Under Fire (USA).zip" and "007 - Nightfire
        // (USA).zip" each arrive three times — Xbox, GameCube, PS2 —
        // with a different magnet URL per system torrent. The 007
        // game is an Xbox port, so the Xbox entry wins for both.
        val xboxHash = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        val gcHash = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        val ps2Hash = "cccccccccccccccccccccccccccccccccccccccc"
        val sources = listOf(
            "json" to listOf(
                magnetEntry(
                    "007 - Agent Under Fire (USA).zip", 10,
                    category = "Microsoft - Xbox", infohash = xboxHash
                ),
                magnetEntry(
                    "007 - Agent Under Fire (USA).zip", 11,
                    category = "Nintendo - GameCube - NKit RVZ [zstd-19-128k]",
                    infohash = gcHash
                ),
                magnetEntry(
                    "007 - Agent Under Fire (USA).zip", 12,
                    category = "Sony - PlayStation 2", infohash = ps2Hash
                ),
                magnetEntry(
                    "007 - Nightfire (USA).zip", 20,
                    category = "Microsoft - Xbox", infohash = xboxHash
                ),
                magnetEntry(
                    "007 - Nightfire (USA).zip", 21,
                    category = "Nintendo - GameCube - NKit RVZ [zstd-19-128k]",
                    infohash = gcHash
                ),
                magnetEntry(
                    "007 - Nightfire (USA).zip", 22,
                    category = "Sony - PlayStation 2", infohash = ps2Hash
                )
            )
        )
        val magnets = matchMagnetLinksForGame(sources, listOf(nightfire))
        assertEquals(2, magnets.size)
        val auf = magnets.single { it.name.startsWith("007 - Agent Under Fire") }
        val nightfireLink = magnets.single { it.name.startsWith("007 - Nightfire") }
        assertTrue(auf.magnetUrl.contains(xboxHash))
        assertTrue(nightfireLink.magnetUrl.contains(xboxHash))
        assertTrue(magnets.all { it.kind == MatchKind.NAME_ONLY })
        assertTrue(magnets.all { it.portId == "nightfire-recomp" })
    }

    @Test
    fun magnetDedupTwoCategoriesShowsOnce() {
        // The same name from two categories shows once. Here neither
        // category matches the game's platform (GameCube + PS2 for an
        // Xbox game), so the first in feed order wins.
        val gcHash = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        val ps2Hash = "cccccccccccccccccccccccccccccccccccccccc"
        val sources = listOf(
            "json" to listOf(
                magnetEntry(
                    "007 - Agent Under Fire (USA).zip", 11,
                    category = "Nintendo - GameCube - NKit RVZ [zstd-19-128k]",
                    infohash = gcHash
                ),
                magnetEntry(
                    "007 - Agent Under Fire (USA).zip", 12,
                    category = "Sony - PlayStation 2", infohash = ps2Hash
                )
            )
        )
        val magnets = matchMagnetLinksForGame(sources, listOf(nightfire))
        assertEquals(1, magnets.size)
        assertTrue(magnets.single().magnetUrl.contains(gcHash))
    }

    @Test
    fun mergeLiveAndCachedMagnetsDedupesStaleCacheDuplicates() {
        // A stale v1.8.4 cache carries the same magnet name two or
        // three times (one per platform), because v1.8.4 deduped by
        // URL only. The merge collapses it on display even before
        // the first background refresh overwrites the cache: a live
        // link wins a name clash, and a cached duplicate only fills
        // a gap the live list has not produced.
        val live = listOf(
            GameMagnetLink(
                name = "007 - Agent Under Fire (USA).zip",
                magnetUrl = "magnet:?xt=urn:btih:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa&so=10",
                kind = MatchKind.NAME_ONLY,
                portId = "nightfire-recomp",
                where = "json"
            )
        )
        val cached = listOf(
            GameMagnetLink(
                name = "007 - Agent Under Fire (USA).zip",
                magnetUrl = "magnet:?xt=urn:btih:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb&so=11",
                kind = MatchKind.NAME_ONLY,
                portId = "nightfire-recomp",
                where = "json"
            ),
            GameMagnetLink(
                name = "007 - Agent Under Fire (USA).zip",
                magnetUrl = "magnet:?xt=urn:btih:cccccccccccccccccccccccccccccccccccccccc&so=12",
                kind = MatchKind.NAME_ONLY,
                portId = "nightfire-recomp",
                where = "json"
            ),
            GameMagnetLink(
                name = "007 - Nightfire (USA).zip",
                magnetUrl = "magnet:?xt=urn:btih:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb&so=21",
                kind = MatchKind.NAME_ONLY,
                portId = "nightfire-recomp",
                where = "json"
            )
        )
        val merged = mergeLiveAndCachedMagnets(live, cached)
        assertEquals(2, merged.size)
        // The live Xbox link wins the "Agent Under Fire" name clash.
        assertTrue(
            merged.single { it.name.startsWith("007 - Agent Under Fire") }
                .magnetUrl.contains("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
        )
        // The cached Nightfire link fills the gap (no live copy).
        assertEquals(1, merged.count { it.name.startsWith("007 - Nightfire") })
    }
}

