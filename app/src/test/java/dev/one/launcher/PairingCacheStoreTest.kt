package dev.one.launcher

import dev.one.launcher.data.DetectedFile
import dev.one.launcher.data.GameFile
import dev.one.launcher.data.GameFileSource
import dev.one.launcher.data.GameMagnetLink
import dev.one.launcher.data.MatchKind
import dev.one.launcher.data.PAIRING_CACHE_FILE_NAME
import dev.one.launcher.data.PAIRING_CACHE_VERSION
import dev.one.launcher.data.PairingCache
import dev.one.launcher.data.PairingCacheStore
import dev.one.launcher.data.PremiumizeItem
import dev.one.launcher.data.toCached
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Pairing cache storage (v1.8.7 audit item 6): the cache moved off
 * Preferences DataStore onto a plain file
 * ("pairing-cache.json" in filesDir), keeping the same PairingCache
 * JSON shape. These tests cover the round-trip, the one-time
 * migration of a v1.8.6 DataStore value, version handling, and
 * clear().
 *
 * The migration is exercised through the real load() path with the
 * legacy value injected as the exact JSON string v1.8.6 stored
 * under "pairings_json" — the store's test-only constructor stands
 * in for the DataStore read. The on-device DataStore read itself
 * (context.pairingCacheDataStore.data) is NOT JVM-testable: it needs
 * an Android Context, and Robolectric is not a dependency. Only
 * that read is untested; the migration logic around it (decode the
 * legacy string, validate the version, write the file, clear the
 * legacy entry exactly once) is fully covered here.
 */
class PairingCacheStoreTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun tempDir(): File = Files.createTempDirectory("portside-pairing-cache-test").toFile()

    /** A cache built the way the ViewModel builds it: GameFiles and
     *  magnet links converted with toCached(), so the Premiumize
     *  download link is dropped before anything is stored — exactly
     *  as v1.8.6 stored it. */
    private fun sampleCache(): PairingCache {
        val local = GameFile(
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
        )
        val cloud = GameFile(
            name = "Pokémon Pokopia [0400A3A01EE4C000][v655360].nup",
            kind = MatchKind.NAME_ONLY,
            source = GameFileSource.CLOUD,
            portId = "banananative-compilationfix",
            where = "Premiumize",
            premiumizeItem = PremiumizeItem(
                id = "abc",
                name = "Pokémon Pokopia [0400A3A01EE4C000][v655360].nup",
                link = "https://example.com/session-download-link"
            )
        )
        val server = GameFile(
            name = "Pokemon Black (NDS) (USA)",
            kind = MatchKind.NAME_ONLY,
            source = GameFileSource.SERVER,
            portId = "eidos",
            where = "json",
            remoteSourceName = "json",
            remoteUrl = "https://example.com/pokemon-black.zip"
        )
        val magnet = GameMagnetLink(
            name = "PokePark Wii - Pikachu's Adventure (USA).zip",
            magnetUrl = "magnet:?xt=urn:btih:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb&so=123",
            kind = MatchKind.DIRECT,
            portId = "pokepark-wii",
            where = "json",
            remoteSourceName = "json"
        )
        return PairingCache(
            version = PAIRING_CACHE_VERSION,
            computedAtMs = 1_792_000_000_000L,
            pairings = mapOf(
                "Pokémon Black & White" to listOf(local, server).map { it.toCached() },
                "Pokopia" to listOf(cloud).map { it.toCached() }
            ),
            magnetPairings = mapOf(
                "PokePark Wii" to listOf(magnet.toCached())
            )
        )
    }

    @Test
    fun fileCacheRoundTripsFilesAndMagnets() = runBlocking {
        val dir = tempDir()
        val store = PairingCacheStore(dir)
        assertNull(store.load())
        val cache = sampleCache()
        store.save(cache)
        val loaded = store.load()
        assertEquals(cache, loaded)
        // All three sources survive, with the detected local file.
        assertEquals(2, loaded!!.pairings["Pokémon Black & White"]!!.size)
        assertEquals("content://example/pokemon-black", loaded.pairings["Pokémon Black & White"]!![0].detected!!.uri)
        assertEquals("https://example.com/pokemon-black.zip", loaded.pairings["Pokémon Black & White"]!![1].remoteUrl)
        // The magnet channel survives alongside the files.
        assertEquals(1, loaded.magnetPairings["PokePark Wii"]!!.size)
        assertEquals(
            "magnet:?xt=urn:btih:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb&so=123",
            loaded.magnetPairings["PokePark Wii"]!![0].magnetUrl
        )
    }

    @Test
    fun premiumizeDownloadLinkIsDroppedOnRoundTrip() = runBlocking {
        // toCached() drops the session link before storage (a stale
        // link must never be served); the id and name survive, and
        // the file round-trip keeps it dropped.
        val cloud = GameFile(
            name = "Pokémon Pokopia [0400A3A01EE4C000][v655360].nup",
            kind = MatchKind.NAME_ONLY,
            source = GameFileSource.CLOUD,
            portId = "banananative-compilationfix",
            where = "Premiumize",
            premiumizeItem = PremiumizeItem(
                id = "abc",
                name = "Pokémon Pokopia [0400A3A01EE4C000][v655360].nup",
                link = "https://example.com/session-download-link"
            )
        )
        assertNull(cloud.toCached().premiumizeItem!!.link)
        val cache = PairingCache(
            version = PAIRING_CACHE_VERSION,
            computedAtMs = 1L,
            pairings = mapOf("Pokopia" to listOf(cloud.toCached()))
        )
        val dir = tempDir()
        val store = PairingCacheStore(dir)
        store.save(cache)
        val loaded = store.load()!!
        val item = loaded.pairings["Pokopia"]!![0].premiumizeItem!!
        assertNull(item.link)
        assertEquals("abc", item.id)
        assertEquals("Pokémon Pokopia [0400A3A01EE4C000][v655360].nup", item.name)
    }

    @Test
    fun saveOverwritesPreviousCache() = runBlocking {
        val dir = tempDir()
        val store = PairingCacheStore(dir)
        store.save(sampleCache())
        assertEquals(2, store.load()!!.pairings.size)
        val smaller = PairingCache(
            version = PAIRING_CACHE_VERSION,
            computedAtMs = 2L,
            pairings = mapOf("Pokopia" to sampleCache().pairings.getValue("Pokopia"))
        )
        store.save(smaller)
        assertEquals(smaller, store.load())
    }

    @Test
    fun corruptFileReadsAsAbsentAndSaveRecovers() = runBlocking {
        val dir = tempDir()
        val store = PairingCacheStore(dir)
        store.save(sampleCache())
        // Corrupt the file in place — a torn write or disk error
        // must read as "no cache", never throw.
        File(dir, PAIRING_CACHE_FILE_NAME).writeText("{not valid json")
        assertNull(store.load())
        // Saving again overwrites the corrupt file.
        store.save(sampleCache())
        assertEquals(sampleCache(), store.load())
    }

    @Test
    fun versionMismatchedFileCacheIsUnusable() = runBlocking {
        val dir = tempDir()
        val store = PairingCacheStore(dir)
        // A cache written under old matcher rules is stale by
        // definition — the matching re-runs instead of trusting it.
        store.save(sampleCache().copy(version = PAIRING_CACHE_VERSION + 1))
        assertNull(store.load())
        store.save(sampleCache().copy(version = 0))
        assertNull(store.load())
    }

    @Test
    fun legacyDataStoreCacheIsMigratedToFileOnFirstLoad() = runBlocking {
        val dir = tempDir()
        val cache = sampleCache()
        // The exact value v1.8.6 stored under "pairings_json" in the
        // "pairing_cache" DataStore: the whole PairingCache as one
        // JSON string, same shape as the file format.
        val legacyJson = json.encodeToString(PairingCache.serializer(), cache)
        var legacyClearedCount = 0
        val store = PairingCacheStore(dir, legacyJson) { legacyClearedCount++ }

        // First load migrates: same data, no loss.
        assertEquals(cache, store.load())
        // The file now holds it — a fresh store with no legacy
        // source at all loads the same cache from the file.
        assertTrue(File(dir, PAIRING_CACHE_FILE_NAME).exists())
        assertEquals(cache, PairingCacheStore(dir).load())
        // And the legacy entry was dropped exactly once.
        assertEquals(1, legacyClearedCount)
        // A second load is served from the file — the migration
        // does not run again.
        assertEquals(cache, store.load())
        assertEquals(1, legacyClearedCount)
    }

    @Test
    fun legacyMigrationWithNoLegacyValueStartsEmpty() = runBlocking {
        val dir = tempDir()
        var legacyClearedCount = 0
        val store = PairingCacheStore(dir, null) { legacyClearedCount++ }
        assertNull(store.load())
        assertFalse(File(dir, PAIRING_CACHE_FILE_NAME).exists())
        assertEquals(0, legacyClearedCount)
    }

    @Test
    fun legacyMigrationRejectsWrongVersionAndCorruptValues() = runBlocking {
        // Wrong version: not migrated, no file written, legacy left
        // alone (nothing usable to migrate).
        val wrongVersionJson = json.encodeToString(
            PairingCache.serializer(),
            sampleCache().copy(version = PAIRING_CACHE_VERSION + 1)
        )
        val dir = tempDir()
        var cleared = 0
        assertNull(PairingCacheStore(dir, wrongVersionJson) { cleared++ }.load())
        assertFalse(File(dir, PAIRING_CACHE_FILE_NAME).exists())
        assertEquals(0, cleared)
        // Corrupt JSON: same — start empty, first background
        // refresh re-populates the file.
        val corruptDir = tempDir()
        assertNull(PairingCacheStore(corruptDir, "{not valid json").load())
        assertFalse(File(corruptDir, PAIRING_CACHE_FILE_NAME).exists())
    }

    @Test
    fun decodeLegacyJsonHelper() {
        val dir = tempDir()
        val store = PairingCacheStore(dir)
        assertEquals(sampleCache(), store.decodeLegacyJson(json.encodeToString(PairingCache.serializer(), sampleCache())))
        assertNull(store.decodeLegacyJson(null))
        assertNull(store.decodeLegacyJson(""))
        assertNull(store.decodeLegacyJson("   "))
        assertNull(store.decodeLegacyJson("{not valid json"))
        assertNull(
            store.decodeLegacyJson(
                json.encodeToString(
                    PairingCache.serializer(),
                    sampleCache().copy(version = 999)
                )
            )
        )
    }

    @Test
    fun clearRemovesFileAndLegacy() = runBlocking {
        val dir = tempDir()
        val cache = sampleCache()
        val legacyJson = json.encodeToString(PairingCache.serializer(), cache)
        // The simulated legacy entry is a mutable cell: clearing it
        // (migration or clear()) makes later reads return null, like
        // the real DataStore entry being removed.
        val legacyCell = java.util.concurrent.atomic.AtomicReference<String?>(legacyJson)
        var legacyClearedCount = 0
        val store = PairingCacheStore(dir, legacyCell) { legacyClearedCount++ }
        // Migrate first so both the file and the legacy entry exist.
        assertEquals(cache, store.load())
        assertEquals(1, legacyClearedCount)
        assertTrue(File(dir, PAIRING_CACHE_FILE_NAME).exists())

        store.clear()
        assertNull(store.load())
        assertFalse(File(dir, PAIRING_CACHE_FILE_NAME).exists())
        // clear() also drops the legacy entry, so a stale DataStore
        // value can never resurrect after the file is gone.
        assertEquals(2, legacyClearedCount)
    }

    @Test
    fun clearOnEmptyStoreIsSafe() = runBlocking {
        val dir = tempDir()
        val store = PairingCacheStore(dir)
        store.clear()
        assertNull(store.load())
    }
}
