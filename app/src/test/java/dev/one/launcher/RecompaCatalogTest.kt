package dev.one.launcher

import dev.one.launcher.data.CURATED_CATALOG_IDS
import dev.one.launcher.data.PortMatcher
import dev.one.launcher.data.hintsFromGameTitle
import dev.one.launcher.data.mapCatalogPort
import dev.one.launcher.data.mergeCatalogs
import dev.one.launcher.data.parseRecompaIndex
import dev.one.launcher.model.Port
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
        val port = mapCatalogPort(index.ports.first { it.id == "noassets" }, "Super Nintendo")
        assertTrue(port.fileExtensions.isEmpty())
        assertFalse(port.archiveOk)
        assertTrue(port.requiredDump.contains("File info not listed in the catalog"))
        // File matching is disabled rather than guessed.
        assertFalse(PortMatcher.matches("somegame.sfc", port))
        assertNull(PortMatcher.matchKind("Some Game.zip", port))
        assertNull(PortMatcher.matchKind("Some Game.sfc", port))
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
    fun curatedCatalogIdMapCoversTheThreeMatches() {
        assertEquals("soh", CURATED_CATALOG_IDS["soh"])
        assertEquals("2ship2harkinian", CURATED_CATALOG_IDS["2ship"])
        assertEquals("starship", CURATED_CATALOG_IDS["starship"])
        assertNull(CURATED_CATALOG_IDS["zelda64recomp"])
        assertNull(CURATED_CATALOG_IDS["zelda3"])
    }
}
