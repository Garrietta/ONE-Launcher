package dev.one.launcher

import dev.one.launcher.data.CURATED_CATALOG_IDS
import dev.one.launcher.data.MatchKind
import dev.one.launcher.data.PortMatcher
import dev.one.launcher.data.RecompaAsset
import dev.one.launcher.data.RecompaFork
import dev.one.launcher.data.RecompaIndex
import dev.one.launcher.data.RecompaPlatform
import dev.one.launcher.data.RecompaPort
import dev.one.launcher.data.hintsFromGameTitle
import dev.one.launcher.data.mapCatalogPort
import dev.one.launcher.data.mergeCatalogs
import dev.one.launcher.data.parseRecompaIndex
import dev.one.launcher.data.titleSimilarity
import dev.one.launcher.data.titleWords
import dev.one.launcher.data.withDistinctiveHints
import dev.one.launcher.model.Port
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

@OptIn(ExperimentalSerializationApi::class)
class RecompaCatalogTest {

    // Small canned index: a multi-fork port (soh), a second port
    // (2ship2harkinian), starship, and one port with no assets[] at all.
    private val cannedIndex = """
    {
      "schema_version": 1,
      "generated_at": "2026-11-16T00:18:07Z",
      "platforms": [
        {"id": "n64", "name": "Nintendo 64"},
        {"id": "snes", "name": "Super Nintendo"}
      ],
      "ports": [
        {
          "id": "soh",
          "name": "Ship of Harkinian (catalog name)",
          "game": "The Legend of Zelda: Ocarina of Time",
          "original_platform": "n64",
          "type": "recomp",
          "description": "A port of Ocarina of Time.",
          "forks": [
            {
              "repo": "linkzenic/Shipwright-Ports",
              "package": "com.linkzenic.soh",
              "verified": true,
              "release": {
                "tag": "v9.3.1-ports.2",
                "asset": {
                  "name": "soh_release_v9.3.1-android.4.apk",
                  "url": "https://github.com/linkzenic/Shipwright-Ports/releases/download/v9.3.1-ports.2/soh_release_v9.3.1-android.4.apk",
                  "size": 56100000,
                  "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                }
              }
            },
            {
              "repo": "Waterdish/Shipwright-Android",
              "package": "com.dishii.soh",
              "release": {
                "tag": "v9.3.1",
                "asset": {
                  "name": "soh.apk",
                  "url": "https://github.com/Waterdish/Shipwright-Android/releases/download/v9.3.1/soh.apk",
                  "size": 55000000,
                  "sha256": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
                }
              }
            }
          ],
          "assets": [
            {
              "id": "rom",
              "label": "Ocarina of Time ROM (N64)",
              "accepted": [{"label": "US 1.0", "format": "z64"}],
              "convert_from": ["v64", "n64"],
              "zip_ok": true
            }
          ],
          "art": {"cover": "https://cdn.steamgriddb.com/cover/soh.png"}
        },
        {
          "id": "2ship2harkinian",
          "name": "2Ship2Harkinian",
          "game": "The Legend of Zelda: Majora's Mask",
          "original_platform": "n64",
          "type": "recomp",
          "description": "A port of Majora's Mask.",
          "forks": [
            {
              "repo": "linkzenic/2ship2harkinian-Android",
              "package": "com.twoshipfork.mm",
              "release": {
                "tag": "v5.0.1-android.2",
                "asset": {
                  "name": "2ship.apk",
                  "url": "https://github.com/linkzenic/2ship2harkinian-Android/releases/download/v5.0.1-android.2/2ship.apk",
                  "size": 42000000,
                  "sha256": "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
                }
              }
            }
          ],
          "assets": [
            {
              "id": "rom",
              "label": "Majora's Mask ROM (N64)",
              "accepted": [{"label": "US 1.0", "format": "z64"}],
              "convert_from": ["n64"],
              "zip_ok": true
            }
          ],
          "art": {"cover": "https://cdn.steamgriddb.com/cover/2ship.png"}
        },
        {
          "id": "starship",
          "name": "Starship",
          "game": "Star Fox 64",
          "original_platform": "n64",
          "type": "decomp",
          "description": "A port of Star Fox 64.",
          "forks": [
            {
              "repo": "izzy2lost/Starship",
              "package": "com.starship.android",
              "verified": true,
              "release": {
                "tag": "v2.0.0",
                "asset": {
                  "name": "starship-v2.0.0.apk",
                  "url": "https://github.com/izzy2lost/Starship/releases/download/v2.0.0/starship-v2.0.0.apk",
                  "size": 30000000,
                  "sha256": "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"
                }
              }
            }
          ],
          "assets": [
            {
              "id": "rom",
              "label": "Star Fox 64 ROM (N64)",
              "accepted": [{"label": "US 1.0", "format": "z64"}],
              "convert_from": [],
              "zip_ok": true
            }
          ]
        },
        {
          "id": "noassets",
          "name": "No Assets Port",
          "game": "Some Game",
          "original_platform": "snes",
          "type": "recomp",
          "description": "A port with no file info in the catalog.",
          "forks": [
            {
              "repo": "someone/noassets",
              "release": {
                "tag": "v1.0",
                "asset": {
                  "name": "noassets.apk",
                  "url": "https://github.com/someone/noassets/releases/download/v1.0/noassets.apk",
                  "size": 1000,
                  "sha256": "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"
                }
              }
            }
          ]
        }
      ]
    }
    """.trimIndent()

    private fun curated(
        id: String, title: String, repo: String, packages: List<String>
    ) = Port(
        id = id,
        title = title,
        game = "game-$id",
        system = "System",
        type = "Decompilation",
        repo = repo,
        releasesUrl = "https://github.com/$repo/releases",
        packageNames = packages,
        fileExtensions = listOf(".z64"),
        fileNameHints = listOf(id),
        requiredDump = "Your own file.",
        notes = "curated note for $id"
    )

    private val curatedFive = listOf(
        curated("soh", "Ship of Harkinian", "linkzenic/Shipwright-Ports",
            listOf("com.linkzenic.soh", "com.dishii.soh")),
        curated("2ship", "2Ship2Harkinian", "linkzenic/2ship2harkinian-Android",
            listOf("com.dishii.mm")),
        curated("starship", "Starship", "izzy2lost/Starship", emptyList()),
        curated("zelda64recomp", "Zelda 64: Recompiled",
            "Mr-Wiseguy/Zelda64Recompiled", emptyList()),
        curated("zelda3", "Zelda3", "snesrev/zelda3", emptyList())
    )

    @Test
    fun parsesCannedIndex() {
        val index = parseRecompaIndex(cannedIndex)
        assertNotNull(index)
        assertEquals(1, index!!.schema_version)
        assertEquals("2026-11-16T00:18:07Z", index.generated_at)
        assertEquals(4, index.ports.size)
        assertEquals(2, index.platforms.size)
    }

    @Test
    fun rejectsOtherSchemaVersion() {
        val other = cannedIndex.replace("\"schema_version\": 1", "\"schema_version\": 2")
        assertNull(parseRecompaIndex(other))
    }

    @Test
    fun rejectsMalformedJson() {
        assertNull(parseRecompaIndex("{ not json"))
        assertNull(parseRecompaIndex(""))
    }

    @Test
    fun mapsExtensionsFromAssets() {
        val index = parseRecompaIndex(cannedIndex)!!
        val soh = mapCatalogPort(index.ports.first { it.id == "soh" }, "Nintendo 64")
        // accepted format + convert_from, each dotted.
        assertEquals(listOf(".z64", ".v64", ".n64"), soh.fileExtensions)
        assertTrue(soh.archiveOk)
        assertEquals("soh", soh.catalogId)
        assertEquals("n64", soh.platformId)
        assertEquals("Nintendo 64", soh.platformName)
        assertEquals("https://cdn.steamgriddb.com/cover/soh.png", soh.coverArtUrl)
        assertEquals("A port of Ocarina of Time.", soh.description)
        assertTrue(soh.requiredDump.contains("Ocarina of Time ROM (N64)"))
    }

    @Test
    fun mapsForkFields() {
        val index = parseRecompaIndex(cannedIndex)!!
        val soh = mapCatalogPort(index.ports.first { it.id == "soh" }, "Nintendo 64")
        assertEquals(2, soh.forks.size)
        val linkzenic = soh.forks.first { it.repo == "linkzenic/Shipwright-Ports" }
        assertEquals("Shipwright-Ports", linkzenic.displayName)
        assertEquals("com.linkzenic.soh", linkzenic.packageName)
        assertEquals("v9.3.1-ports.2", linkzenic.latestTag)
        assertEquals("soh_release_v9.3.1-android.4.apk", linkzenic.latestAssetName)
        assertTrue(linkzenic.latestAssetUrl.contains("github.com"))
        assertEquals(56100000L, linkzenic.latestAssetSize)
        assertEquals(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            linkzenic.latestAssetSha256
        )
        assertEquals(true, linkzenic.verified)
        // Catalog package names are data, not guesses.
        assertEquals(
            listOf("com.linkzenic.soh", "com.dishii.soh"),
            soh.packageNames
        )
    }

    @Test
    fun portWithNoAssetsHasNoFileInfo() {
        val index = parseRecompaIndex(cannedIndex)!!
        // Distinctive hints come from the merge, not the mapper alone:
        // run the mapped port through withDistinctiveHints, as
        // mergeCatalogs does. Its hints are "some" (from "Some Game")
        // and "noassets" (its id) — both distinctive in this list.
        val port = withDistinctiveHints(
            listOf(mapCatalogPort(index.ports.first { it.id == "noassets" }, "Super Nintendo"))
        ).single()
        assertTrue(port.fileExtensions.isEmpty())
        assertFalse(port.archiveOk)
        assertFalse(port.hasFileInfo)
        // The "not listed" phrase lives on the detail page now (gated on
        // hasFileInfo), not embedded in requiredDump.
        assertFalse(port.requiredDump.contains("File info not listed in the catalog"))
        // The strict extension check still refuses it...
        assertFalse(PortMatcher.matches("somegame.sfc", port))
        // ...but a file named after the game matches provisionally, by
        // name only — the v1.4.1 fallback for format-less ports.
        assertEquals(MatchKind.NAME_ONLY, PortMatcher.matchKind("Some Game.zip", port))
        assertEquals(MatchKind.NAME_ONLY, PortMatcher.matchKind("Some Game.sfc", port))
    }

    @Test
    fun mapsPokopiaInstructionsAndNoFormats() {
        // The real catalog entry, constructed directly: one manual asset,
        // an empty accepted list, instructions_md carrying the folder
        // requirement. No formats anywhere.
        val pokopiaIndex = RecompaPort(
            id = "banananative-compilationfix",
            name = "Pokopia",
            game = "Pokopia",
            original_platform = "switch2",
            type = "recomp",
            description = "Pokopia port.",
            forks = listOf(
                RecompaFork(
                    repo = "Wojnstup/BananaNative-CompilationFix",
                    packageName = "dev.nxnative.android.pokopia"
                )
            ),
            assets = listOf(
                RecompaAsset(
                    id = "game",
                    label = "Your Switch 2 game dump as a folder (with base/)",
                    accepted = emptyList(),
                    manual = true,
                    instructionsMd = "You need your own Switch 2 game dumped " +
                        "as a folder (with base/). .nsp and .xci files are " +
                        "not supported, and keys are not required."
                )
            )
        )
        val port = withDistinctiveHints(listOf(mapCatalogPort(pokopiaIndex, "Switch 2"))).single()
        assertTrue(port.fileExtensions.isEmpty())
        // There IS file info (a label and instructions) — just no formats.
        assertTrue(port.hasFileInfo)
        assertTrue(
            port.requiredDump.contains(
                "Your Switch 2 game dump as a folder (with base/)"
            )
        )
        // No doubled "Your own Your" and no "not listed" text.
        assertFalse(port.requiredDump.contains("Your own Your"))
        assertFalse(port.requiredDump.contains("File info not listed"))
        assertTrue(port.fileInstructions.contains("folder (with base/)"))
        assertTrue(port.fileInstructions.contains(".nsp"))
        // And the .nup matches by name only, through the mapper's hints.
        assertEquals(
            MatchKind.NAME_ONLY,
            PortMatcher.matchKind(
                "Pokémon Pokopia [0400A3A01EE4C000][v655360].nup", port
            )
        )
    }

    @Test
    fun hintsComeFromGameTitleTokens() {
        val hints = hintsFromGameTitle("The Legend of Zelda: Ocarina of Time", "soh")
        assertTrue(hints.contains("ocarina"))
        assertTrue(hints.contains("soh"))
        assertFalse(hints.contains("the"))
        assertFalse(hints.contains("of"))
        // Franchise-generic words are not hints: an Ocarina of Time file
        // must not match every Zelda port.
        assertFalse(hints.contains("zelda"))
        assertFalse(hints.contains("legend"))
        assertFalse(hints.contains("time"))
    }

    @Test
    fun regionTokensNeverBecomeHints() {
        val hints = hintsFromGameTitle("Daytona USA", "daytona-usa")
        assertTrue(hints.contains("daytona"))
        assertTrue(hints.contains("daytona-usa"))
        assertFalse(hints.contains("usa"))
        val generic = hintsFromGameTitle("Some Random Game", "some-game")
        assertFalse(generic.contains("game"))
    }

    @Test
    fun titleSimilarityIsSymmetricContainment() {
        // The NAME_ONLY gate (v1.8.0): 1.0 when the shorter title is
        // fully contained in the longer one, in either direction.
        // A file that is a subset of a longer game title passes…
        assertEquals(
            1.0, titleSimilarity("Pokemon Black (NDS) (USA).zip", "Pokémon Black & White"), 0.001
        )
        // …and a game title that is a subset of a longer file title
        // passes too (Pokopia: the game is "Pokopia", the file is
        // "Pokémon Pokopia").
        assertEquals(
            1.0,
            titleSimilarity(
                "Pokémon Pokopia [0400A3A01EE4C000][v655360].nup", "Pokopia"
            ),
            0.001
        )
        // A single shared word is not enough — the four reported
        // false positives all sit at 0.5 or below.
        assertEquals(
            0.5, titleSimilarity("Pokemon Blue (GB) (USA).zip", "Blue Dragon"), 0.001
        )
        assertEquals(
            0.5, titleSimilarity("Pokemon Gold (GBC) (USA).zip", "Marble Blast Gold"), 0.001
        )
        assertEquals(
            0.25,
            titleSimilarity(
                "Mario Kart Double Dash (GC) (USA).zip",
                "Tom Clancy's Splinter Cell: Double Agent"
            ),
            0.001
        )
        assertEquals(
            0.5,
            titleSimilarity("Sega - Master System - Mark III.zip", "System Shock"),
            0.001
        )
        // No significant words on either side is 0.0, never a match.
        assertEquals(0.0, titleSimilarity("Game (USA).zip", "Some Random Game"), 0.001)
        // A title that itself contains dots is not truncated at the
        // last dot: the extension strip only fires when the suffix
        // looks like an extension, so "S.T.A.L.K.E.R." and "Dr."
        // keep the rest of the title (the sweep over the live
        // catalog caught this — both were 0.0 before the fix).
        assertEquals(
            1.0,
            titleSimilarity(
                "S.T.A.L.K.E.R.: Shadow of Chernobyl.zip",
                "S.T.A.L.K.E.R.: Shadow of Chernobyl"
            ),
            0.001
        )
        assertEquals(
            1.0,
            titleSimilarity(
                "Dr. Robotnik's Ring Racers.zip", "Dr. Robotnik's Ring Racers"
            ),
            0.001
        )
    }

    @Test
    fun slashInGameTitleIsNotAPathPrefix() {
        // The .hack//versus false positives (v1.8.5, Garrett's
        // 2026-11-16 report): titleWords stripped a file-path prefix
        // with substringAfterLast('/'), which also stripped the game
        // title ".hack//versus" down to just "versus" — so every
        // "versus" file got similarity 1.0 and matched. A "/" in a
        // game title is part of the title; only the FILE side strips
        // a path prefix.
        assertEquals(listOf("hack", "versus"), titleWords(".hack//versus"))
        assertEquals(
            listOf("kingdom", "hearts", "358", "days"),
            titleWords("Kingdom Hearts 358/2 Days")
        )
        assertEquals(listOf("void", "tension"), titleWords("The Void/Tension"))
        // The four reported "versus" files are now blocked at 0.5.
        assertEquals(
            0.5,
            titleSimilarity(
                "Aliens Versus Predator - Extinction (USA).zip", ".hack//versus"
            ),
            0.001
        )
        assertEquals(
            0.5,
            titleSimilarity(
                "RoboCop versus The Terminator (USA).zip", ".hack//versus"
            ),
            0.001
        )
        assertEquals(
            0.5,
            titleSimilarity(
                "Sherlock Holmes Versus Jack the Ripper (USA) (En,Fr,Es).zip",
                ".hack//versus"
            ),
            0.001
        )
        assertEquals(
            0.5,
            titleSimilarity(
                "Star Versus (USA, Europe) (Aftermarket) (Unl).zip", ".hack//versus"
            ),
            0.001
        )
        // A real .hack file still matches: the file side strips its
        // path prefix, the game side keeps both words, and the
        // symmetric overlap is 1.0.
        assertEquals(
            1.0,
            titleSimilarity(".hack//Versus (USA).iso", ".hack//versus"),
            0.001
        )
        // The other slash titles tokenize fully and still match.
        assertEquals(
            1.0,
            titleSimilarity(
                "Kingdom Hearts 358/2 Days (USA).zip", "Kingdom Hearts 358/2 Days"
            ),
            0.001
        )
        assertEquals(
            1.0,
            titleSimilarity("The Void - Tension (USA).zip", "The Void/Tension"),
            0.001
        )
    }

    @Test
    fun mergeKeepsCuratedCanonicalAndAppendsCatalogOnly() {
        val index = parseRecompaIndex(cannedIndex)!!
        val platformNames = index.platforms.associate { it.id to it.name }
        val catalogPorts = index.ports.map {
            mapCatalogPort(it, platformNames[it.original_platform].orEmpty())
        }
        val merge = mergeCatalogs(curatedFive, catalogPorts)
        // 5 curated + 1 catalog-only (noassets) = 6.
        assertEquals(6, merge.merged.size)
        // Curated entries win on conflict: title, notes, packageNames.
        val soh = merge.merged.first { it.id == "soh" }
        assertEquals("Ship of Harkinian", soh.title)
        assertEquals("curated note for soh", soh.notes)
        assertEquals(listOf("com.linkzenic.soh", "com.dishii.soh"), soh.packageNames)
        // ...and gain the catalog's forks, cover art and catalog id.
        assertEquals("soh", soh.catalogId)
        assertEquals(2, soh.forks.size)
        assertEquals("https://cdn.steamgriddb.com/cover/soh.png", soh.coverArtUrl)
        // 2ship matched by overlapping fork repo, starship by repo too.
        val twoShip = merge.merged.first { it.id == "2ship" }
        assertEquals("2ship2harkinian", twoShip.catalogId)
        assertEquals(1, twoShip.forks.size)
        val starship = merge.merged.first { it.id == "starship" }
        assertEquals("starship", starship.catalogId)
        assertEquals("com.starship.android", starship.forks.single().packageName)
        assertEquals(true, starship.forks.single().verified)
        // Catalog-only port appended.
        assertTrue(merge.merged.any { it.id == "noassets" })
        // Unmatched curated ports are reported, not guessed.
        assertEquals(listOf("zelda64recomp", "zelda3"), merge.unmatchedCuratedIds)
        // Ports without file info are reported.
        assertEquals(listOf("noassets"), merge.portsWithoutFileInfo)
    }

    @Test
    fun distinctiveHintsNeedOneOwnerAndFourLetters() {
        val a = Port(
            id = "a", title = "A", game = "Shared Game", system = "PC",
            type = "recomp", repo = "example/a", releasesUrl = "",
            fileNameHints = listOf("shared", "alpha", "abc"),
            requiredDump = "Your own files."
        )
        val b = Port(
            id = "b", title = "B", game = "Shared Other", system = "PC",
            type = "recomp", repo = "example/b", releasesUrl = "",
            fileNameHints = listOf("shared", "beta"),
            requiredDump = "Your own files."
        )
        val distinct = withDistinctiveHints(listOf(a, b))
        // "shared" has two owners — distinctive for neither. "abc" is
        // 3 letters — too short. Only the single-owner 4+ hints survive.
        assertEquals(listOf("alpha"), distinct[0].distinctiveNameHints)
        assertEquals(listOf("beta"), distinct[1].distinctiveNameHints)
    }

    @Test
    fun curatedCatalogIdMapCoversTheThreeMatches() {
        assertEquals("soh", CURATED_CATALOG_IDS["soh"])
        assertEquals("2ship2harkinian", CURATED_CATALOG_IDS["2ship"])
        assertEquals("starship", CURATED_CATALOG_IDS["starship"])
        assertNull(CURATED_CATALOG_IDS["zelda64recomp"])
        assertNull(CURATED_CATALOG_IDS["zelda3"])
    }

    // ---- Streaming I/O (v1.8.7) ----
    //
    // RecompaCatalogApi now decodes the index straight from the
    // cache file / response byte stream (decodeFromStream) and writes
    // the raw cache with encodeToStream. The Api itself needs a
    // Context, so these tests exercise the exact same codecs and
    // Json config (ignoreUnknownKeys) through file streams, and
    // prove the streamed decode matches parseRecompaIndex exactly.

    private val streamJson = Json { ignoreUnknownKeys = true }

    private fun streamDecode(text: String): RecompaIndex? {
        return try {
            val index = streamJson.decodeFromStream<RecompaIndex>(
                text.toByteArray(Charsets.UTF_8).inputStream()
            )
            if (index.schema_version != 1) null else index
        } catch (e: Exception) {
            null
        }
    }

    @Test
    fun indexDecodesFromStreamIdentically() {
        val fromString = parseRecompaIndex(cannedIndex)
        val fromStream = streamDecode(cannedIndex)
        assertNotNull(fromString)
        assertEquals(fromString, fromStream)
        assertEquals(4, fromStream!!.ports.size)
        assertEquals(2, fromStream.platforms.size)
        assertEquals("soh", fromStream.ports[0].id)
        assertEquals("2026-11-16T00:18:07Z", fromStream.generated_at)
    }

    @Test
    fun streamDecodeRejectsBadSchemaAndMalformedJson() {
        val other = cannedIndex.replace("\"schema_version\": 1", "\"schema_version\": 2")
        assertNull(streamDecode(other))
        assertNull(streamDecode("{ not json"))
        assertNull(streamDecode(""))
        // The decoder itself throws on malformed input — the Api's
        // try/catch is what turns that into null, never a crash.
        assertThrows(
            kotlinx.serialization.SerializationException::class.java
        ) {
            streamJson.decodeFromStream<RecompaIndex>(
                "{ not json".toByteArray(Charsets.UTF_8).inputStream()
            )
        }
    }

    @Test
    fun rawIndexCacheRoundTripsViaStream() {
        // The same write/read pair refresh() + loadCached() use:
        // encode the parsed index to a file stream, decode it back
        // from the file stream, and get the identical index.
        val index = parseRecompaIndex(cannedIndex)!!
        val dir = Files.createTempDirectory("portside-index-test").toFile()
        val file = File(dir, "recompa-index.json")
        file.outputStream().buffered().use { streamJson.encodeToStream(index, it) }
        val loaded = file.inputStream().buffered()
            .use { streamJson.decodeFromStream<RecompaIndex>(it) }
        assertEquals(index, loaded)
        assertEquals(4, loaded.ports.size)
        // The streamed bytes are the same document the old String
        // path wrote — a v1.8.6 raw cache still parses.
        assertEquals(index, parseRecompaIndex(file.readText()))
    }
}
