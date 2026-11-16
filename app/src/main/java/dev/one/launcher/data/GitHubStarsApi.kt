package dev.one.launcher.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * GitHub repo metadata client — star counts only, for ranking a
 * game's ports. One unauthenticated GET per repo
 * (api.github.com/repos/{owner}/{repo} → stargazers_count), no token,
 * so the unauthenticated limit (60 requests/hour) applies. Callers
 * cache results ([StarCacheStore], ~7 day TTL) and only fetch what
 * the cache does not have fresh, so opening a game page costs at
 * most one request per port — usually one. Any failure (network,
 * 404, 403 rate limit, missing field) returns null and the caller
 * falls back to the catalog's download counts; a star count is never
 * invented.
 */
class GitHubStarsApi(private val client: OkHttpClient = OkHttpClient()) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun starsForRepo(ownerRepo: String): Int? = withContext(Dispatchers.IO) {
        if (ownerRepo.isBlank()) return@withContext null
        try {
            val request = Request.Builder()
                .url("https://api.github.com/repos/$ownerRepo")
                .header("Accept", "application/vnd.github+json")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                try {
                    json.parseToJsonElement(body).jsonObject["stargazers_count"]
                        ?.jsonPrimitive?.intOrNull
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
