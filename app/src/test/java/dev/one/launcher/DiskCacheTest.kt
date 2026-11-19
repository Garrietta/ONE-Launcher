package dev.one.launcher

import dev.one.launcher.data.CachedCatalog
import dev.one.launcher.data.RecompaPlatform
import dev.one.launcher.data.RemoteEntry
import dev.one.launcher.data.RemoteListingsCache
import dev.one.launcher.model.Port
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * On-disk caches (v1.8.5 loading fix): the parsed remote listings
 * and the parsed catalog are cached to disk so a cold start loads
 * them instantly and refreshes in the background, instead of
 * re-fetching + re-parsing remote.json (11 MB) and the Recompa index
 * (5.5 MB) on every cold start. These tests cover the round-trip —
 * save then load returns the same entries / ports — and the
 * failure modes (no cache, corrupt cache) that must degrade to
 * "no cache", never a crash. The ViewModel's cache-first wiring
 * (serve disk, refresh in background, keep cached entries on fetch
 * error) is runtime behavior, not JVM-testable here.
 */
@OptIn(ExperimentalSerializationApi::class)
class DiskCacheTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun tempDir(): File = Files.createTempDirectory("marquee-cache-test").toFile()

    private val sampleEntries = listOf(
        RemoteEntry(
            name = "007 - Agent Under Fire (USA).zip",
            url = "magnet:?xt=urn:btih:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa&so=10",
            isFolder = false,
            kind = "magnet",
            category = "Microsoft - Xbox"
        ),
        RemoteEntry(
            name = "PokePark Wii - Pikachu's Adventure (USA).zip",
            url = "magnet:?xt=urn:btih:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb&so=123",
            isFolder = false,
            kind = "magnet",
            category = "Nintendo - Wii - NKit RVZ [zstd-19-128k]"
        ),
        RemoteEntry(
            name = "Pokemon Black (NDS) (USA)",
            url = "https://example.com/pokemon-black.zip",
            isFolder = false,
            sizeBytes = 123456L
        )
    )

    @Test
    fun remoteListingsCacheRoundTrips() {
        val dir = tempDir()
        val cache = RemoteListingsCache(dir)
        assertNull(cache.load("json"))
        cache.save("json", sampleEntries)
        val loaded = cache.load("json")
        assertEquals(sampleEntries, loaded)
        // A magnet entry's category survives the round-trip — the
        // dedup needs it after a cold start, not just in memory.
        assertEquals("Microsoft - Xbox", loaded!![0].category)
        assertEquals("magnet", loaded[0].kind)
        // A different source id is a different cache file.
        assertNull(cache.load("mirror"))
        cache.save("mirror", sampleEntries.take(1))
        assertEquals(1, cache.load("mirror")!!.size)
        assertEquals(3, cache.load("json")!!.size)
    }

    @Test
    fun remoteListingsCacheClearDropsTheFile() {
        val dir = tempDir()
        val cache = RemoteListingsCache(dir)
        cache.save("json", sampleEntries)
        assertTrue(cache.load("json")!!.isNotEmpty())
        cache.clear("json")
        assertNull(cache.load("json"))
    }

    @Test
    fun remoteListingsCacheCorruptFileReadsAsAbsent() {
        val dir = tempDir()
        val cache = RemoteListingsCache(dir)
        cache.save("json", sampleEntries)
        // Corrupt the file in place — a truncated write, a disk
        // error, or an older format must read as "no cache", never
        // throw.
        val file = File(dir, "remote-listings-json.json")
        file.writeText("{not valid json")
        assertNull(cache.load("json"))
        // And saving again overwrites the corrupt file.
        cache.save("json", sampleEntries)
        assertEquals(sampleEntries, cache.load("json"))
    }

    @Test
    fun cachedCatalogRoundTrips() {
        // The parsed catalog cache: the merged Port list (with
        // distinctive hints already computed) + platforms +
        // generated_at, serialized as one JSON document. A field
        // added later decodes with its default, so an older cache
        // still loads after an app update.
        val ports = listOf(
            Port(
                id = "eidos",
                title = "Eidos",
                game = "Pokémon Black & White",
                system = "Nintendo DS",
                type = "recomp",
                repo = "Project-Holon/eidos",
                releasesUrl = "https://github.com/Project-Holon/eidos/releases",
                requiredDump = "Pokémon Black or White ROM (DS)",
                fileNameHints = listOf("pokemon", "black", "white", "eidos"),
                distinctiveNameHints = listOf("eidos"),
                platformId = "ds",
                platformName = "Nintendo DS"
            )
        )
        val cached = CachedCatalog(
            ports = ports,
            platforms = listOf(RecompaPlatform("ds", "Nintendo DS")),
            generatedAt = "2026-11-16T00:18:07Z"
        )
        val text = json.encodeToString(cached)
        val decoded = json.decodeFromString<CachedCatalog>(text)
        assertEquals(cached, decoded)
        // An older cache without the platforms/generatedAt fields
        // still decodes (defaults), so a v1.8.5 cache loads after a
        // future field is added.
        val minimal = json.decodeFromString<CachedCatalog>(
            """{"ports":${json.encodeToString(ports)}}"""
        )
        assertEquals(ports, minimal.ports)
        assertTrue(minimal.platforms.isEmpty())
        assertEquals("", minimal.generatedAt)
    }

    // ---- Streaming I/O (v1.8.7) ----
    //
    // Both caches now read/write through streams (decodeFromStream /
    // encodeToStream) instead of materializing the whole document as
    // a String. These tests prove the streamed paths round-trip a
    // full-size listing and the catalog, and that the file layout is
    // unchanged — a cache written by the old String path still loads,
    // and vice versa.

    /** 26,710 entries, the size of the live remote.json. */
    private fun largeListing(): List<RemoteEntry> =
        (0 until 26_710).map { i ->
            if (i % 3 == 0) {
                RemoteEntry(
                    name = "Game $i (USA).zip",
                    url = "magnet:?xt=urn:btih:${"a".repeat(40)}&dn=Game+$i&so=${1000 + i}",
                    isFolder = false,
                    sizeBytes = 1_000_000L + i,
                    kind = "magnet",
                    category = "Nintendo - Wii - NKit RVZ [zstd-19-128k]"
                )
            } else {
                RemoteEntry(
                    name = "Game $i (USA).zip",
                    url = "https://example.com/files/game-$i.zip",
                    isFolder = false,
                    sizeBytes = 2_000_000L + i
                )
            }
        }

    @Test
    fun remoteListingsCacheRoundTripsLargeListingViaStream() {
        val dir = tempDir()
        val cache = RemoteListingsCache(dir)
        val entries = largeListing()
        cache.save("json", entries)
        // The cache file is a multi-MB document, like the live feed.
        val file = File(dir, "remote-listings-json.json")
        assertTrue(file.length() > 1_000_000)
        val loaded = cache.load("json")
        assertEquals(entries, loaded)
        assertEquals(26_710, loaded!!.size)
        // A magnet entry's category and size survive the round-trip.
        assertEquals("Nintendo - Wii - NKit RVZ [zstd-19-128k]", loaded[0].category)
        assertEquals("magnet", loaded[0].kind)
        assertEquals(1_000_000L, loaded[0].sizeBytes)
        assertEquals(2_000_001L, loaded[1].sizeBytes)
    }

    @Test
    fun remoteListingsCacheLoadsOldStringFormatAndViceVersa() {
        val dir = tempDir()
        val cache = RemoteListingsCache(dir)
        // A cache written the old way (encodeToString + writeText,
        // v1.8.5/v1.8.6) still loads through the streamed load().
        val file = File(dir, "remote-listings-json.json")
        file.writeText(json.encodeToString(sampleEntries))
        assertEquals(sampleEntries, cache.load("json"))
        // And a cache written the new way (streamed save) still
        // decodes through the old String path — same file layout.
        cache.save("json", sampleEntries)
        assertEquals(
            sampleEntries,
            json.decodeFromString<List<RemoteEntry>>(file.readText())
        )
    }

    @Test
    fun cachedCatalogRoundTripsViaStream() {
        // The same codecs RecompaCatalogApi.saveParsed /
        // loadParsedCached use (same Json config: ignoreUnknownKeys
        // + encodeDefaults), exercised through file streams on a
        // 200-port catalog.
        val ports = (0 until 200).map { i ->
            Port(
                id = "port-$i",
                title = "Port $i",
                game = "Game $i",
                system = "Nintendo 64",
                type = "recomp",
                repo = "example/port-$i",
                releasesUrl = "https://github.com/example/port-$i/releases",
                requiredDump = "Your own files.",
                fileNameHints = listOf("game", "port-$i"),
                distinctiveNameHints = listOf("port-$i"),
                platformId = "n64",
                platformName = "Nintendo 64"
            )
        }
        val cached = CachedCatalog(
            ports = ports,
            platforms = listOf(RecompaPlatform("n64", "Nintendo 64")),
            generatedAt = "2026-11-16T00:18:07Z"
        )
        val dir = tempDir()
        val file = File(dir, "recompa-parsed.json")
        file.outputStream().buffered().use { json.encodeToStream(cached, it) }
        val loaded = file.inputStream().buffered()
            .use { json.decodeFromStream<CachedCatalog>(it) }
        assertEquals(cached, loaded)
        assertEquals(200, loaded.ports.size)
        // The streamed bytes are the same document the old String
        // path wrote — a v1.8.5/v1.8.6 parsed cache still loads.
        assertEquals(
            cached,
            json.decodeFromString<CachedCatalog>(file.readText())
        )
    }
}
