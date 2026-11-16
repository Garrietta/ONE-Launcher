package dev.one.launcher.data

import android.content.Context
import dev.one.launcher.model.Port
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream

/**
 * Fetches the Recompa catalog index (https://recompa.app/index.json)
 * and caches the raw JSON in the app cache dir with its generated_at.
 *
 * Stale-while-revalidate: [loadCached] serves the last good fetch
 * immediately on cold start; [refresh] fetches in the background and
 * replaces the cache. On fetch failure with no cache, the caller falls
 * back to the curated ports.json and says so — never a crash.
 *
 * Signer verification is a follow-up, not this round: the catalog's
 * signer_sha256 is stored on each fork but not yet checked at install.
 */
@OptIn(ExperimentalSerializationApi::class)
class RecompaCatalogApi(private val context: Context) {
    private val client = OkHttpClient()

    companion object {
        const val INDEX_URL = "https://recompa.app/index.json"
        const val CACHE_FILE = "recompa-index.json"
        const val PARSED_CACHE_FILE = "recompa-parsed.json"
    }

    private fun cacheFile(): File = File(context.cacheDir, CACHE_FILE)

    private val recompaJson = Json { ignoreUnknownKeys = true }

    /**
     * Decode one index document from a stream and enforce the schema
     * version — the same rule as [parseRecompaIndex], but without
     * materializing the whole document as a String first. Returns
     * null on bad JSON or an unsupported schema_version.
     */
    private fun decodeRecompaIndex(stream: InputStream): RecompaIndex? {
        return try {
            val index = recompaJson.decodeFromStream<RecompaIndex>(stream)
            if (index.schema_version != RECOMPA_SCHEMA_VERSION) null else index
        } catch (e: Exception) {
            null
        }
    }

    /**
     * The last successfully fetched index, or null when never fetched.
     *
     * Streaming (v1.8.7): decoded straight from the cache file
     * stream — the old readText() allocated the whole 5.5 MB index
     * as one String before parsing.
     */
    fun loadCached(): RecompaIndex? {
        val file = cacheFile()
        if (!file.isFile) return null
        return try {
            file.inputStream().buffered().use { decodeRecompaIndex(it) }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Fetch the index over the network and replace the cache. Returns
     * the parsed index, or null on any failure (network, HTTP error, bad
     * JSON, or an unsupported schema_version) — the old cache is left
     * untouched so the caller can keep serving it.
     *
     * Streaming (v1.8.7): the index is decoded straight from the
     * response byte stream — the old response.body?.string()
     * allocated the whole 5.5 MB index as one String before parsing.
     * The raw cache file now holds the re-serialized index rather
     * than the verbatim server bytes; it parses identically (the
     * loader ignores unknown fields either way), and the file name
     * and fallback order are unchanged.
     */
    suspend fun refresh(): RecompaIndex? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(INDEX_URL).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val stream = response.body?.byteStream() ?: return@withContext null
                val index = stream.buffered().use { decodeRecompaIndex(it) }
                    ?: return@withContext null
                try {
                    cacheFile().outputStream().buffered().use { out ->
                        recompaJson.encodeToStream(index, out)
                    }
                } catch (e: Exception) {
                    // Cache write is best-effort; the parsed data is still good.
                }
                index
            }
        } catch (e: Exception) {
            null
        }
    }

    // ---- Parsed catalog cache (v1.8.5) ----
    //
    // The raw-JSON cache above still costs a full re-parse + re-map of
    // the 5.5 MB index on every cold start (hundreds of milliseconds
    // to seconds on a phone). The parsed cache stores the MERGED port
    // list — curated ports + catalog forks + cover art, exactly what
    // the Library displays — so a cold start can show all 529 games
    // without touching the network or re-parsing the index. The
    // background refresh re-parses + re-maps and overwrites this
    // cache; the raw JSON stays as the fallback when the parsed cache
    // is absent or corrupt.

    private fun parsedCacheFile(): File = File(context.cacheDir, PARSED_CACHE_FILE)

    private val parsedJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * The last merged catalog, or null when never saved / corrupt.
     *
     * Streaming (v1.8.7): decoded straight from the file stream.
     * Same file layout as before, so a cache written by v1.8.5/v1.8.6
     * still loads.
     */
    fun loadParsedCached(): CachedCatalog? {
        val file = parsedCacheFile()
        if (!file.isFile) return null
        return try {
            file.inputStream().buffered().use { parsedJson.decodeFromStream<CachedCatalog>(it) }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Persist the merged catalog. Best-effort — never throws.
     *
     * Streaming (v1.8.7): encoded straight to the file stream.
     * Same file layout as before.
     */
    fun saveParsed(cached: CachedCatalog) {
        try {
            parsedCacheFile().outputStream().buffered().use { parsedJson.encodeToStream(cached, it) }
        } catch (e: Exception) {
            // Cache write is best-effort; the in-memory data is still good.
        }
    }
}

/**
 * The merged catalog as the Library displays it (v1.8.5): the full
 * merged [Port] list (curated first, then catalog ports, with
 * distinctive hints already computed), the catalog's platform list,
 * and the index's generated_at so the UI can say when it was
 * updated. Stored by [RecompaCatalogApi.saveParsed] and read by
 * [RecompaCatalogApi.loadParsedCached]. [Port] is @Serializable, so
 * the list round-trips; a field added later decodes with its
 * default (ignoreUnknownKeys + encodeDefaults), so an older cache
 * still loads after an app update.
 */
@Serializable
data class CachedCatalog(
    val ports: List<Port>,
    val platforms: List<RecompaPlatform> = emptyList(),
    val generatedAt: String = ""
)
