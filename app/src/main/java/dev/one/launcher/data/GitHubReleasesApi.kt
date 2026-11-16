package dev.one.launcher.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * GitHub Releases client — metadata only, fetched on demand when a port's
 * detail page opens (and on "Check for updates"). No auth token, so the
 * unauthenticated limit (60 requests/hour) applies; results are cached
 * per port in the ViewModel for the session so reopening a detail page
 * does not re-hit the API. Nothing is ever downloaded from here except
 * the APK the user explicitly taps Download on — and only from the port
 * project's own release, never a third-party hoster.
 */
@Serializable
data class GitHubAsset(
    val name: String = "",
    val browser_download_url: String = "",
    val size: Long = 0
)

@Serializable
data class GitHubRelease(
    val tag_name: String = "",
    val name: String = "",
    val html_url: String = "",
    val assets: List<GitHubAsset> = emptyList()
)

sealed class ReleaseResult {
    data class Success(val release: GitHubRelease) : ReleaseResult()
    data class Failure(val message: String) : ReleaseResult()
}

class GitHubReleasesApi {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun latestRelease(ownerRepo: String): ReleaseResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://api.github.com/repos/$ownerRepo/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext when (response.code) {
                        404 -> ReleaseResult.Failure(
                            "No releases found for $ownerRepo."
                        )
                        403 -> ReleaseResult.Failure(
                            "GitHub rate limit reached (60 requests/hour without " +
                                "sign-in). Try again later."
                        )
                        else -> ReleaseResult.Failure(
                            "GitHub returned HTTP ${response.code}"
                        )
                    }
                }
                try {
                    ReleaseResult.Success(
                        json.decodeFromString(GitHubRelease.serializer(), body)
                    )
                } catch (e: Exception) {
                    ReleaseResult.Failure("Could not read the GitHub release response")
                }
            }
        } catch (e: Exception) {
            ReleaseResult.Failure("Network error: ${e.message ?: "unknown"}")
        }
    }
}

/**
 * Pick the APK to offer from a release: .apk assets only, preferring one
 * whose name contains "android", else the first .apk. Returns null when
 * the release has no .apk at all — the UI must say so plainly and never
 * claim an APK exists for a port whose upstream ships none.
 */
fun pickApkAsset(release: GitHubRelease): GitHubAsset? {
    val apks = release.assets.filter { it.name.lowercase().endsWith(".apk") }
    if (apks.isEmpty()) return null
    return apks.firstOrNull { it.name.lowercase().contains("android") } ?: apks.first()
}

/**
 * Compare two version strings by their numeric core, and say how:
 * strip a leading "v", drop any suffix after the first "-" (so
 * "v5.0.1-android.2" compares as "5.0.1"), split on ".", and compare each
 * numeric segment in order; a missing segment counts as 0 and a
 * non-numeric segment counts as 0. Returns > 0 when [a] is newer than
 * [b], 0 when equal, < 0 when older. Used to decide "Update available"
 * from the installed versionName vs the release tag.
 */
fun compareVersions(a: String, b: String): Int {
    fun core(version: String): List<Int> {
        val stripped = version.trim().removePrefix("v").substringBefore('-')
        if (stripped.isEmpty()) return emptyList()
        return stripped.split('.').map { it.toIntOrNull() ?: 0 }
    }
    val left = core(a)
    val right = core(b)
    val length = maxOf(left.size, right.size)
    for (i in 0 until length) {
        val l = left.getOrElse(i) { 0 }
        val r = right.getOrElse(i) { 0 }
        if (l != r) return l.compareTo(r)
    }
    return 0
}
