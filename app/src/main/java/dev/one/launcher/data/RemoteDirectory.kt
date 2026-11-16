package dev.one.launcher.data

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromStream
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * A generic remote directory browser. It understands exactly two server
 * shapes, both of them plain directory listings:
 *
 *  - Standard HTTP directory listings (Apache mod_autoindex / nginx
 *    autoindex HTML): links to subfolders and files.
 *  - A plain JSON file list: an array of objects with `name` and `url`
 *    (an entry may also carry `kind` "magnet" for a magnet link).
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
/**
 * One parsed entry. `sizeBytes` is known only when the server's file
 * list includes it (the plain JSON shape may carry an optional numeric
 * `size` field); standard directory listings do not provide one
 * through this parser, so it stays null there and the browser simply
 * shows no size.
 *
 * Magnet entries (v1.8.0): a remote JSON list may carry magnet links
 * (kind "magnet"). A magnet entry is a copyable link, not a file:
 * tapping it copies the magnet URI (its `url`, a full magnet: URI),
 * it never downloads and never matches a game. `kind` is null for
 * autoindex entries and for plain file entries that do not carry it.
 *
 * `category` (v1.8.5) is the feed's category for the entry (e.g.
 * "Nintendo - Wii - NKit RVZ [zstd-19-128k]" for a Minerva magnet,
 * "Microsoft - Xbox" for an Xbox one) — the JSON lists carry it and
 * the parser keeps it so magnet dedup can prefer the entry whose
 * category platform matches the game's platform. Null for autoindex
 * entries (a directory listing has no categories) and for feeds
 * that do not carry one.
 *
 * RemoteEntry is @Serializable so the parsed listings can be cached
 * to disk (v1.8.5, [RemoteListingsCache]) — the computed `isMagnet`
 * is derived from `kind`/`url` and is not part of the stored form.
 */
@Serializable
data class RemoteEntry(
    val name: String,
    val url: String,
    val isFolder: Boolean,
    val sizeBytes: Long? = null,
    val kind: String? = null,
    val category: String? = null
) {
    /**
     * True for a magnet entry: the JSON said kind "magnet", or the
     * URL itself is a magnet: URI (the fallback, so a magnet entry
     * works even when "kind" is missing). A magnet entry is never a
     * game file, whatever its name ends in.
     */
    val isMagnet: Boolean
        get() = kind.equals("magnet", ignoreCase = true) ||
            isMagnetUrl(url)
}

/** True when a URL is a magnet: URI (ignoring surrounding space). */
fun isMagnetUrl(url: String): Boolean =
    url.trim().startsWith("magnet:", ignoreCase = true)

/**
 * The magnet URI to copy for a magnet entry, or null when it is not
 * a magnet entry. A magnet entry's `url` is its full magnet URI, so
 * this is just that URL. Pure — unit-tested; the clipboard write
 * itself is UI and is not JVM-testable.
 */
fun magnetLinkToCopy(entry: RemoteEntry): String? {
    if (!entry.isMagnet) return null
    return entry.url.takeIf { it.isNotBlank() }
}

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

private val HTML_ENTITY_REGEX = Regex("&#(\\d+);")
private val STRIP_TAGS_REGEX = Regex("<[^>]*>")

private fun unescapeHtml(text: String): String {
    var out = text
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
    out = HTML_ENTITY_REGEX.replace(out) { match ->
        val code = match.groupValues[1].toIntOrNull()
        if (code != null && code in 1..0xFFFF) code.toChar().toString() else match.value
    }
    return out
}

private val LINK_REGEX = Regex(
    """<a\s+[^>]*?href\s*=\s*"([^"]+)"[^>]*>(.*?)</a>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)

/**
 * True when a fetched HTML page looks like a standard directory
 * listing (Apache mod_autoindex / nginx autoindex) rather than some
 * other web page. The markers are the shapes a listing always has:
 * a `<title>` or `<h1>` reading "Index of …" (Apache and nginx) or
 * "Directory listing for …" (Python's http.server and friends), an
 * Apache sort-header link (`?C=N;O=D`), or an nginx `<pre>` block of
 * links. A JS web app's shell has none of these — its nav/sidebar
 * chrome must never be read as file entries, so a page without a
 * marker is refused before any link is extracted. Pure function —
 * unit-tested.
 */
fun looksLikeDirectoryListing(html: String): Boolean {
    if (LISTING_HEADING_REGEX.containsMatchIn(html)) return true
    if (APACHE_SORT_REGEX.containsMatchIn(html)) return true
    if (NGINX_PRE_REGEX.containsMatchIn(html)) return true
    return false
}

private val LISTING_HEADING_REGEX = Regex(
    """<(?:title|h1)[^>]*>[^<]*(?:index of|directory listing)""",
    RegexOption.IGNORE_CASE
)
private val APACHE_SORT_REGEX = Regex(
    """\?C=[NMSD];O=[AD]""",
    RegexOption.IGNORE_CASE
)
private val NGINX_PRE_REGEX = Regex(
    """<pre[^>]*>.*?<a\s+[^>]*href\s*=""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)

/**
 * Parse a fetched remote-folder body as a listing or a file list.
 * JSON (leading `[` or `{`) goes to [parsePlainFileList]; anything
 * else must first prove it is a directory listing
 * ([looksLikeDirectoryListing]) before [parseDirectoryListing] runs —
 * a web app's HTML shell is refused with the plain error and zero
 * entries, never surfaced as filenames. Pure function — unit-tested.
 */
fun parseRemoteBody(url: String, body: String): RemoteListingResult {
    val trimmed = body.trimStart()
    if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
        return parsePlainFileList(url, body)
    }
    if (!looksLikeDirectoryListing(body)) {
        return RemoteListingResult.Error(REMOTE_FOLDER_ERROR)
    }
    val entries = parseDirectoryListing(url, body)
    return if (entries.isEmpty()) {
        RemoteListingResult.Error(REMOTE_FOLDER_ERROR)
    } else {
        RemoteListingResult.Listing(entries)
    }
}

/**
 * Parse an Apache mod_autoindex / nginx autoindex HTML listing into
 * folder and file entries. Skips sort-header links ("?C=N;O=D"),
 * parent-directory links ("../", "Parent Directory"), links off to
 * other hosts, and links with no usable name. Folders come first, then
 * files, each alphabetical. Pure function — unit-tested.
 *
 * NOTE: this extracts links from any HTML it is handed — callers must
 * check [looksLikeDirectoryListing] (or use [parseRemoteBody]) first,
 * so a web app's nav chrome is never read as a listing.
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
            match.groupValues[2].replace(STRIP_TAGS_REGEX, "")
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
 * Lightweight DTO for one entry of a plain JSON file list (v1.8.7):
 * the wire shape decoded directly by kotlinx.serialization, without
 * building a JsonObject DOM for the whole feed first. The fields are
 * [JsonElement]s, not plain Strings, on purpose: a feed entry with a
 * wrong-typed value (e.g. `"name": 42`, `"size": "big"`) must be
 * skipped like before, not fail the whole list — so the lenient
 * per-field checks from the old DOM walk live on in [dtosToListing]
 * below, unchanged.
 */
@Serializable
private data class RemoteEntryDto(
    val name: JsonElement? = null,
    val url: JsonElement? = null,
    val kind: JsonElement? = null,
    val category: JsonElement? = null,
    val size: JsonElement? = null
)

/**
 * Parse a plain JSON file list: an array of objects with `name` and
 * `url` (both required strings). An entry may also carry an optional
 * `kind` ("magnet" or "file"; absent means "file"), an optional
 * numeric `size`, and an optional `category` string (kept as-is).
 * Unknown fields are ignored; an entry missing a required field is
 * skipped, never crashed on. Anything that is not such an array — a
 * JSON object (e.g. a categories[] catalog), a malformed document, or
 * an array whose entries carry no usable name/url (e.g. the flat
 * items[] schema with title/downloadUrl) — is rejected with the
 * plain error, not interpreted as a file list. Pure function —
 * unit-tested.
 *
 * Magnet entries (v1.8.0): a magnet entry's `url` is its full
 * magnet: URI, used as-is (it is already absolute — it is never
 * resolved against the feed URL).
 *
 * Streaming (v1.8.7): the feed is decoded straight into a list of
 * [RemoteEntryDto]s — no intermediate ~22 MB String for an 11 MB
 * feed, no JsonObject DOM of 26,710 entries — then mapped to
 * [RemoteEntry] exactly as before.
 */
fun parsePlainFileList(feedUrl: String, rawJson: String): RemoteListingResult {
    val json = Json { ignoreUnknownKeys = true }
    val dtos = try {
        json.decodeFromString<List<RemoteEntryDto>>(rawJson)
    } catch (e: Exception) {
        return RemoteListingResult.Error(REMOTE_FOLDER_ERROR)
    }
    return dtosToListing(feedUrl, dtos)
}

/**
 * Stream overload of [parsePlainFileList]: decodes the JSON straight
 * from the response body stream ([Json.decodeFromStream]) instead of
 * materializing the whole body as a String first. Same output and
 * same error behavior as the String overload.
 */
@OptIn(ExperimentalSerializationApi::class)
fun parsePlainFileList(feedUrl: String, stream: InputStream): RemoteListingResult {
    val json = Json { ignoreUnknownKeys = true }
    val dtos = try {
        json.decodeFromStream<List<RemoteEntryDto>>(stream)
    } catch (e: Exception) {
        return RemoteListingResult.Error(REMOTE_FOLDER_ERROR)
    }
    return dtosToListing(feedUrl, dtos)
}

/**
 * Map decoded DTOs to [RemoteEntry]s with the exact rules the old
 * DOM walk applied: name/url must be non-blank strings (trimmed),
 * relative URLs resolve against the feed URL (magnet: URIs are used
 * as-is), kind/category are optional trimmed strings, size is an
 * optional non-negative number (a string or negative size means
 * "unknown"). Entries are sorted by lowercase name; an empty result
 * is the plain error, and an empty array is an empty listing.
 */
private fun dtosToListing(feedUrl: String, dtos: List<RemoteEntryDto>): RemoteListingResult {
    if (dtos.isEmpty()) return RemoteListingResult.Listing(emptyList())
    val entries = dtos.mapNotNull { dto ->
        val namePrimitive = dto.name as? JsonPrimitive
        val name = if (namePrimitive?.isString == true) {
            namePrimitive.content.takeIf { it.isNotBlank() }?.trim()
        } else {
            null
        }
        val urlPrimitive = dto.url as? JsonPrimitive
        val url = if (urlPrimitive?.isString == true) {
            urlPrimitive.content.takeIf { it.isNotBlank() }?.trim()
        } else {
            null
        }
        if (name == null || url == null) return@mapNotNull null
        // A magnet: URI is already absolute — use it as-is. Anything
        // else resolves against the feed URL.
        val resolved = if (isMagnetUrl(url)) url
        else resolveUrl(feedUrl, url) ?: return@mapNotNull null
        val kindPrimitive = dto.kind as? JsonPrimitive
        val kind = if (kindPrimitive?.isString == true) {
            kindPrimitive.content.takeIf { it.isNotBlank() }?.trim()
        } else {
            null
        }
        // Optional category, when the list carries one (the adapter's
        // remote.json does — the Minerva system name, e.g. "Nintendo
        // - Wii - NKit RVZ [zstd-19-128k]", or an r-roms category).
        // Kept so magnet dedup can prefer the entry whose category
        // platform matches the game's platform (v1.8.5).
        val categoryPrimitive = dto.category as? JsonPrimitive
        val category = if (categoryPrimitive?.isString == true) {
            categoryPrimitive.content.takeIf { it.isNotBlank() }?.trim()
        } else {
            null
        }
        // Optional numeric size, when the list carries one; anything
        // else (missing, a string, a negative) means "unknown".
        // JsonPrimitive has no longOrNull in kotlinx-serialization
        // 1.7.3, so parse the content text — guarded by isString, so a
        // JSON string like "123" is not mistaken for a size.
        val sizePrimitive = dto.size as? JsonPrimitive
        val size = if (sizePrimitive != null && !sizePrimitive.isString) {
            sizePrimitive.content.toLongOrNull()?.takeIf { it >= 0 }
        } else {
            null
        }
        RemoteEntry(
            name = name, url = resolved, isFolder = false,
            sizeBytes = size, kind = kind, category = category
        )
    }
    if (entries.isEmpty()) return RemoteListingResult.Error(REMOTE_FOLDER_ERROR)
    return RemoteListingResult.Listing(entries.sortedBy { it.name.lowercase() })
}

/**
 * How many leading bytes [parseRemoteStream] may scan while sniffing
 * for the first non-whitespace byte. A JSON feed starts with `[` or
 * `{` after at most a few whitespace bytes; 64 KB covers any sane
 * leading whitespace, and anything beyond that is refused rather
 * than read into memory.
 */
private const val STREAM_SNIFF_LIMIT = 65536

/** True for the four JSON whitespace bytes (space, tab, LF, CR). */
private fun isJsonWhitespace(byte: Int): Boolean =
    byte == 0x20 || byte == 0x09 || byte == 0x0A || byte == 0x0D

/**
 * Parse a fetched remote-folder body straight from its stream
 * (v1.8.7): sniff the first non-whitespace byte (mark/reset, no
 * buffering of the whole body) to decide the shape, then decode a
 * JSON feed from the stream ([parsePlainFileList]) or read the body
 * as text for the HTML directory-listing path. Same result and same
 * error behavior as [parseRemoteBody] — a body that is neither a
 * JSON array/object nor a recognizable directory listing gets the
 * plain error. Pure function — unit-tested with byte streams.
 */
@OptIn(ExperimentalSerializationApi::class)
internal fun parseRemoteStream(feedUrl: String, stream: InputStream): RemoteListingResult {
    val buffered = stream.buffered()
    return try {
        buffered.mark(STREAM_SNIFF_LIMIT)
        var first = -1
        var byte: Int
        while (buffered.read().also { byte = it } != -1) {
            if (!isJsonWhitespace(byte)) {
                first = byte
                break
            }
        }
        buffered.reset()
        when {
            first == -1 -> RemoteListingResult.Error(REMOTE_FOLDER_ERROR)
            first == '['.code || first == '{'.code ->
                parsePlainFileList(feedUrl, buffered)
            else -> {
                val body = buffered.readBytes().toString(Charsets.UTF_8)
                if (!looksLikeDirectoryListing(body)) {
                    RemoteListingResult.Error(REMOTE_FOLDER_ERROR)
                } else {
                    val entries = parseDirectoryListing(feedUrl, body)
                    if (entries.isEmpty()) {
                        RemoteListingResult.Error(REMOTE_FOLDER_ERROR)
                    } else {
                        RemoteListingResult.Listing(entries)
                    }
                }
            }
        }
    } catch (e: Exception) {
        RemoteListingResult.Error(REMOTE_FOLDER_ERROR)
    }
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
                val bodyStream = response.body?.byteStream()
                    ?: return RemoteListingResult.Error(REMOTE_FOLDER_ERROR)
                // Streaming (v1.8.7): a JSON feed is decoded straight
                // from the response byte stream — the old
                // response.body?.string() allocated a ~22 MB UTF-16
                // String for the 11 MB remote.json before parsing
                // even started. The HTML listing path still reads
                // the body as text, inside parseRemoteStream.
                bodyStream.buffered().use { parseRemoteStream(url, it) }
            }
        } catch (e: Exception) {
            RemoteListingResult.Error(REMOTE_FOLDER_ERROR)
        }
    }
}
