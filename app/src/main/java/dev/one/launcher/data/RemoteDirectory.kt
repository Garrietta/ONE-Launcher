package dev.one.launcher.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * A generic remote directory browser. It understands exactly two server
 * shapes, both of them plain directory listings:
 *
 *  - Standard HTTP directory listings (Apache mod_autoindex / nginx
 *    autoindex HTML): links to subfolders and files.
 *  - A plain JSON file list: an array of objects with `name` and `url`.
 *
 * It deliberately does NOT understand catalog feeds: no hierarchical
 * categories/pages/sections/rows JSON, no flat items[] schema with
 * title/category/region/fileSize/downloadUrl, and no megathread-style
 * page scraping. A server returning one of those shapes gets the plain
 * error below, not a parsed list. Remote entries are never merged into
 * the Library, its search, or any port matching — the browser is an
 * isolated screen, and a downloaded file only becomes visible to the
 * app once it lands on disk, through the existing local-folder scan.
 */
data class RemoteEntry(val name: String, val url: String, val isFolder: Boolean)

sealed class RemoteListingResult {
    data class Listing(val entries: List<RemoteEntry>) : RemoteListingResult()
    data class Error(val message: String) : RemoteListingResult()
}

const val REMOTE_FOLDER_ERROR =
    "Couldn't read this folder — the server needs to serve a standard " +
    "directory listing or a simple file list."

/**
 * Normalize a user-entered remote-folder URL. Trims whitespace, accepts
 * http/https only, and assumes http:// when no scheme is given — home
 * servers usually serve plain http. Returns null when the input is
 * blank, has no host, or uses any other scheme. Pure function —
 * unit-tested.
 */
fun normalizeRemoteFolderUrl(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    // A "scheme:" prefix that is not http(s) is rejected outright, the
    // same guard as normalizeCustomLinkUrl: "host:port" is not a scheme
    // (the part after the colon is all digits).
    val colon = trimmed.indexOf(':')
    if (colon > 0) {
        val slash = trimmed.indexOf('/')
        val beforeSlash = if (slash >= 0) trimmed.substring(0, slash) else trimmed
        if (beforeSlash.contains(':')) {
            val prefix = trimmed.substring(0, colon)
            val after = trimmed.substring(colon + 1).substringBefore('/')
            val isPort = after.isNotEmpty() && after.all { it.isDigit() }
            if (!isPort &&
                !prefix.equals("http", ignoreCase = true) &&
                !prefix.equals("https", ignoreCase = true)
            ) {
                return null
            }
        }
    }
    val candidate = if (trimmed.contains("://")) trimmed else "http://$trimmed"
    return try {
        val uri = URI(candidate)
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (uri.host.isNullOrBlank()) return null
        uri.toString()
    } catch (e: Exception) {
        null
    }
}

/** Build the GET for a folder URL, with Basic auth when configured. */
fun buildRemoteRequest(url: String, username: String?, password: String?): Request {
    val builder = Request.Builder().url(url)
    if (!username.isNullOrBlank() && password != null) {
        builder.header("Authorization", Credentials.basic(username, password))
    }
    return builder.build()
}

private fun resolveUrl(base: String, ref: String): String? {
    return try {
        URI(base).resolve(ref).toString()
    } catch (e: Exception) {
        null
    }
}

private fun unescapeHtml(text: String): String {
    var out = text
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
    out = Regex("&#(\\d+);").replace(out) { match ->
        val code = match.groupValues[1].toIntOrNull()
        if (code != null && code in 1..0xFFFF) code.toChar().toString() else match.value
    }
    return out
}

private val LINK_REGEX = Regex(
    """<a\s+[^>]*?href\s*=\s*"([^"]+)"[^>]*>(.*?)</a>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOTALL)
)

/**
 * Parse an Apache mod_autoindex / nginx autoindex HTML listing into
 * folder and file entries. Skips sort-header links ("?C=N;O=D"),
 * parent-directory links ("../", "Parent Directory"), links off to
 * other hosts, and links with no usable name. Folders come first, then
 * files, each alphabetical. Pure function — unit-tested.
 */
fun parseDirectoryListing(baseUrl: String, html: String): List<RemoteEntry> {
    val base = try {
        URI(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
    } catch (e: Exception) {
        return emptyList()
    }
    val seen = mutableSetOf<String>()
    val entries = mutableListOf<RemoteEntry>()
    for (match in LINK_REGEX.findAll(html)) {
        val href = match.groupValues[1].trim()
        if (href.isEmpty()) continue
        if (href.startsWith("?") || href.startsWith("#")) continue
        if (href.startsWith("javascript:", ignoreCase = true)) continue
        if (href == "../" || href == "..") continue
        val resolved = try {
            base.resolve(href)
        } catch (e: Exception) {
            continue
        }
        val scheme = resolved.scheme?.lowercase() ?: continue
        if (scheme != "http" && scheme != "https") continue
        if (!resolved.host.equals(base.host, ignoreCase = true)) continue
        if (resolved.toString() == base.toString()) continue
        val url = resolved.toString()
        if (!seen.add(url)) continue
        val inner = unescapeHtml(
            match.groupValues[2].replace(Regex("<[^>]*>"), "")
        ).trim()
        var name = inner
        if (name.isEmpty()) {
            val segment = resolved.path.substringAfterLast('/').trim()
            name = try {
                java.net.URLDecoder.decode(segment, "UTF-8")
            } catch (e: Exception) {
                segment
            }
        }
        if (name.isEmpty()) continue
        if (name.equals("Parent Directory", ignoreCase = true)) continue
        val isFolder = href.endsWith("/") || resolved.path.endsWith("/")
        if (isFolder) name = name.trimEnd('/')
        if (name.isEmpty()) continue
        entries.add(RemoteEntry(name = name, url = url, isFolder = isFolder))
    }
    return entries.sortedWith(
        compareByDescending<RemoteEntry> { it.isFolder }
            .thenBy { it.name.lowercase() }
    )
}

/**
 * Parse a plain JSON file list: an array of objects with `name` and
 * `url` (both required strings). Unknown fields are ignored; an entry
 * missing either field is skipped, never crashed on. Anything that is
 * not such an array — a JSON object (e.g. a categories[] catalog), a
 * malformed document, or an array whose entries carry no usable
 * name/url (e.g. the flat items[] schema with title/downloadUrl) — is
 * rejected with the plain error, not interpreted as a file list. Pure
 * function — unit-tested.
 */
fun parsePlainFileList(feedUrl: String, rawJson: String): RemoteListingResult {
    val json = Json { ignoreUnknownKeys = true }
    val element = try {
        json.parseToJsonElement(rawJson)
    } catch (e: Exception) {
        return RemoteListingResult.Error(REMOTE_FOLDER_ERROR)
    }
    if (element !is JsonArray) return RemoteListingResult.Error(REMOTE_FOLDER_ERROR)
    if (element.isEmpty()) return RemoteListingResult.Listing(emptyList())
    val entries = element.mapNotNull { item ->
        if (item !is JsonObject) return@mapNotNull null
        val name = (item["name"] as? JsonPrimitive)?.contentOrNull
            ?.takeIf { it.isNotBlank() }?.trim() ?: return@mapNotNull null
        val url = (item["url"] as? JsonPrimitive)?.contentOrNull
            ?.takeIf { it.isNotBlank() }?.trim() ?: return@mapNotNull null
        val resolved = resolveUrl(feedUrl, url) ?: return@mapNotNull null
        RemoteEntry(name = name, url = resolved, isFolder = false)
    }
    if (entries.isEmpty()) return RemoteListingResult.Error(REMOTE_FOLDER_ERROR)
    return RemoteListingResult.Listing(entries.sortedBy { it.name.lowercase() })
}

/** Fetches one remote folder URL and parses it as a listing or file list. */
class RemoteDirectoryApi(private val client: OkHttpClient = defaultClient()) {
    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    /** Blocking — call on Dispatchers.IO. */
    fun fetch(url: String, username: String?, password: String?): RemoteListingResult {
        return try {
            client.newCall(buildRemoteRequest(url, username, password)).execute().use { response ->
                if (!response.isSuccessful) {
                    if (response.code == 401) {
                        return RemoteListingResult.Error(
                            "Couldn't read this folder — check the username " +
                                "and password for this source."
                        )
                    }
                    return RemoteListingResult.Error(REMOTE_FOLDER_ERROR)
                }
                val body = response.body?.string()
                    ?: return RemoteListingResult.Error(REMOTE_FOLDER_ERROR)
                val trimmed = body.trimStart()
                if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
                    parsePlainFileList(url, body)
                } else {
                    val entries = parseDirectoryListing(url, body)
                    if (entries.isEmpty()) {
                        RemoteListingResult.Error(REMOTE_FOLDER_ERROR)
                    } else {
                        RemoteListingResult.Listing(entries)
                    }
                }
            }
        } catch (e: Exception) {
            RemoteListingResult.Error(REMOTE_FOLDER_ERROR)
        }
    }
}
