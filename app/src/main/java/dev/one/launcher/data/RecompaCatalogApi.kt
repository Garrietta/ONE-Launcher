package dev.one.launcher.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

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
class RecompaCatalogApi(private val context: Context) {
    private val client = OkHttpClient()

    companion object {
        const val INDEX_URL = "https://recompa.app/index.json"
        const val CACHE_FILE = "recompa-index.json"
    }

    private fun cacheFile(): File = File(context.cacheDir, CACHE_FILE)

    /** The last successfully fetched index, or null when never fetched. */
    fun loadCached(): RecompaIndex? {
        val file = cacheFile()
        if (!file.isFile) return null
        return try {
            parseRecompaIndex(file.readText())
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Fetch the index over the network and replace the cache. Returns
     * the parsed index, or null on any failure (network, HTTP error, bad
     * JSON, or an unsupported schema_version) — the old cache is left
     * untouched so the caller can keep serving it.
     */
    suspend fun refresh(): RecompaIndex? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(INDEX_URL).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val text = response.body?.string() ?: return@withContext null
                val index = parseRecompaIndex(text) ?: return@withContext null
                try {
                    cacheFile().writeText(text)
                } catch (e: Exception) {
                    // Cache write is best-effort; the parsed data is still good.
                }
                index
            }
        } catch (e: Exception) {
            null
        }
    }
}
