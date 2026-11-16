package dev.one.launcher.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

val Context.pairingCacheDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "pairing_cache"
)

/** File name of the pairing cache inside [Context.getFilesDir]. */
internal const val PAIRING_CACHE_FILE_NAME = "pairing-cache.json"

/** Key the v1.8.6 DataStore stored the cache under ("pairings_json"). */
private val pairingCacheJsonKey = stringPreferencesKey("pairings_json")

/** One read of the legacy v1.8.6 DataStore value, or null when it is
 *  absent or unreadable. Top-level (not a member) so the store's
 *  secondary constructors can reference it without capturing `this`
 *  before the instance is initialized. */
private suspend fun readLegacyPairingCache(context: Context): String? = try {
    context.pairingCacheDataStore.data.map { prefs ->
        prefs[pairingCacheJsonKey]
    }.first()
} catch (e: Exception) {
    null
}

/** Drop the legacy v1.8.6 DataStore entry. */
private suspend fun clearLegacyPairingCache(context: Context) {
    context.pairingCacheDataStore.edit { prefs ->
        prefs.remove(pairingCacheJsonKey)
    }
}

/**
 * Persistent pairing cache (v1.8.7): the computed file↔game pairings
 * as one JSON document in a plain file in [Context.getFilesDir]
 * ("pairing-cache.json") — NOT the cache dir, so the system never
 * evicts it. The cache is what makes the Library open pre-populated:
 * the ViewModel loads it into state before the background refresh
 * replaces it with fresh results. A corrupt, missing, or
 * wrong-version cache just reads as null — the caller falls back to
 * computing the pairings live, and the background refresh writes a
 * fresh cache. Local-only, never synced.
 *
 * The JSON shape is unchanged from v1.8.6 (version + pairings, same
 * [PairingCache] model), so [PAIRING_CACHE_VERSION] stays 1 — only
 * the storage moved. v1.8.6 kept the same document as one JSON
 * string under key "pairings_json" in a Preferences DataStore named
 * "pairing_cache"; on the first load after the move, if the file
 * does not exist yet, that DataStore value is read once, written to
 * the file, and the DataStore entry is cleared, so the migration
 * runs exactly once and a user's cached pairings are never silently
 * lost. If the DataStore value is absent or unusable (wrong version,
 * corrupt), the cache simply starts empty and the first background
 * refresh re-populates the file.
 *
 * Writes are atomic: the document is streamed to a ".tmp" sibling
 * (buffered, fsynced), then renamed over the cache file, so a crash
 * mid-write leaves either the old cache or the new one, never a
 * torn file. All file I/O runs on [Dispatchers.IO] inside the
 * store; callers may call from any dispatcher.
 *
 * Note on streaming: kotlinx-serialization-json 1.7.3 (the pinned
 * dependency — build.gradle.kts is out of scope for this change)
 * has no Json.encodeToStream/decodeFromStream (verified against
 * the 1.7.3 jar; those arrived in 1.8.0), so the file is streamed
 * through a buffered reader/writer and serialized with
 * encodeToString/decodeFromString — the same pattern
 * RemoteListingsCache uses, and the same JSON either way.
 */
class PairingCacheStore private constructor(
    private val cacheFile: File,
    private val readLegacy: (suspend () -> String?)?,
    private val clearLegacy: (suspend () -> Unit)?
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * App constructor (unchanged public API): the cache file lives in
     * [Context.getFilesDir], and the legacy source is the v1.8.6
     * Preferences DataStore, read once for migration.
     */
    constructor(context: Context) : this(
        cacheFile = File(context.filesDir, PAIRING_CACHE_FILE_NAME),
        readLegacy = { readLegacyPairingCache(context) },
        clearLegacy = { clearLegacyPairingCache(context) }
    )

    /** Test-only: a file-backed store with no legacy source. */
    internal constructor(filesDir: File) : this(
        cacheFile = File(filesDir, PAIRING_CACHE_FILE_NAME),
        readLegacy = null,
        clearLegacy = null
    )

    /**
     * Test-only: a file-backed store whose legacy source is a
     * simulated v1.8.6 DataStore value — the exact JSON string that
     * was stored under "pairings_json". [onLegacyCleared] fires when
     * the store drops the legacy entry (migration or [clear]), so
     * tests can observe the once-only migration without a Context.
     * The on-device DataStore read itself is not JVM-testable (it
     * needs an Android Context); only that read is untested — the
     * migration logic around it (decode, validate, write the file,
     * clear the legacy entry) runs through this constructor.
     */
    internal constructor(
        filesDir: File,
        legacyJson: String?,
        onLegacyCleared: (() -> Unit)? = null
    ) : this(
        cacheFile = File(filesDir, PAIRING_CACHE_FILE_NAME),
        readLegacy = { legacyJson },
        clearLegacy = { onLegacyCleared?.invoke() }
    )

    /**
     * Test-only: a file-backed store with a fully simulated legacy
     * source held in [legacyCell]. Clearing the cell (migration or
     * [clear]) makes later reads return null, exactly like the real
     * DataStore entry being removed — so a later [load] returns null
     * instead of re-migrating a stale value.
     */
    internal constructor(
        filesDir: File,
        legacyCell: java.util.concurrent.atomic.AtomicReference<String?>,
        onLegacyCleared: (() -> Unit)? = null
    ) : this(
        cacheFile = File(filesDir, PAIRING_CACHE_FILE_NAME),
        readLegacy = { legacyCell.get() },
        clearLegacy = { legacyCell.set(null); onLegacyCleared?.invoke() }
    )

    suspend fun load(): PairingCache? {
        // The file is the source of truth once it exists.
        val fromFile = withContext(Dispatchers.IO) { loadFromFile() }
        if (fromFile != null) return fromFile
        // No usable file — one-time migration from the v1.8.6
        // DataStore. A corrupt file is rescued the same way: the
        // legacy value, if still present, rewrites it.
        val legacyReader = readLegacy ?: return null
        val migrated = try {
            decodeLegacyJson(legacyReader())
        } catch (e: Exception) {
            null
        } ?: return null
        withContext(Dispatchers.IO) { writeFile(migrated) }
        // Best-effort: if clearing fails, the next load migrates
        // again over the same file — harmless, same content.
        try {
            clearLegacy?.invoke()
        } catch (e: Exception) {
        }
        return migrated
    }

    suspend fun save(cache: PairingCache) {
        withContext(Dispatchers.IO) { writeFile(cache) }
    }

    suspend fun clear() {
        withContext(Dispatchers.IO) {
            try {
                cacheFile.delete()
            } catch (e: Exception) {
            }
            try {
                tempFile().delete()
            } catch (e: Exception) {
            }
        }
        // Drop the legacy v1.8.6 entry too, so a stale DataStore
        // value can never resurrect after the file is gone.
        try {
            clearLegacy?.invoke()
        } catch (e: Exception) {
        }
    }

    private fun tempFile(): File =
        File(cacheFile.parentFile, "${cacheFile.name}.tmp")

    /** The cached document, or null when there is no file, the file
     *  is corrupt, or it was written under old matcher rules. */
    private fun loadFromFile(): PairingCache? {
        if (!cacheFile.exists()) return null
        return try {
            val raw = cacheFile.inputStream().bufferedReader().use { it.readText() }
            decode(raw)
        } catch (e: Exception) {
            null
        }
    }

    /** Decode a JSON document into a usable cache, or null when it
     *  is blank, corrupt, or written under old matcher rules. */
    private fun decode(raw: String?): PairingCache? {
        if (raw.isNullOrBlank()) return null
        return try {
            val cache = json.decodeFromString(PairingCache.serializer(), raw)
            // A cache written under old matcher rules is stale by
            // definition — ignore it and let the matching re-run.
            if (isPairingCacheUsable(cache)) cache else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Decode a legacy v1.8.6 DataStore value (the raw JSON string
     * stored under "pairings_json") into a usable cache, or null when
     * it is absent, blank, corrupt, or written under old matcher
     * rules. Pure string -> cache conversion, so it is directly
     * unit-testable without a Context or a DataStore.
     */
    internal fun decodeLegacyJson(raw: String?): PairingCache? = decode(raw)

    /**
     * Write the cache atomically: stream the JSON to a ".tmp" sibling
     * in the same directory (buffered, flushed, fsynced), then rename
     * it over the cache file. The rename is atomic within one
     * directory, so a crash mid-write leaves either the old cache or
     * the new one, never a torn file. The stale ".tmp" from a crashed
     * write is overwritten by the next save.
     */
    private fun writeFile(cache: PairingCache) {
        cacheFile.parentFile?.mkdirs()
        val tmp = tempFile()
        FileOutputStream(tmp).use { fos ->
            fos.bufferedWriter().use { out ->
                out.write(json.encodeToString(PairingCache.serializer(), cache))
                out.flush()
                fos.fd.sync()
            }
        }
        if (!tmp.renameTo(cacheFile)) {
            // Should not happen (same directory, no concurrent
            // writers), but never leave a half-replaced cache: drop
            // the old file and retry once, else fail loudly so the
            // caller keeps its in-memory pairings.
            cacheFile.delete()
            if (!tmp.renameTo(cacheFile)) {
                tmp.delete()
                throw IOException("Failed to replace pairing cache file")
            }
        }
    }
}
