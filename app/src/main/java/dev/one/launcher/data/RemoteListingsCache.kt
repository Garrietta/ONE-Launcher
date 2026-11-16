package dev.one.launcher.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * On-disk cache for parsed remote folder listings (v1.8.5): one
 * compact JSON file per source id in the app's cache dir, holding the
 * parsed [RemoteEntry] list for that source. Loading the catalog +
 * remote entries from disk on startup is what makes a cold start
 * instant — re-fetching and re-parsing remote.json (11 MB, 26,710
 * records) on every cold start is the dominant load cost, and the
 * pairing cache (v1.8.2) already proves the cache-first pattern:
 * show the cached data immediately, refresh in the background.
 *
 * The cache is keyed by the source's stable id (the same id used in
 * the pairings). A source with no cached file simply has no entries
 * until its first fetch lands — the listing is loaded from the
 * network exactly as before, and the cache only ever speeds up the
 * second and later starts.
 *
 * The cache never holds a magnet's download link — it holds the
 * entry list, and a magnet entry's `url` is its magnet URI, which
 * is a link to copy, not a download. The Premiumize download link
 * is never in a remote entry at all.
 *
 * A corrupt or unreadable cache file is treated as absent (the
 * network fetch replaces it), never crashed on. A fetch that fails
 * leaves the cached entries in place — the caller keeps the old
 * list rather than overwriting it with nothing (see
 * [OneViewModel.ensureServerListings]).
 *
 * Pure file I/O, no Android framework — unit-tested with a temp dir.
 */
class RemoteListingsCache(
    private val cacheDir: File
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun fileFor(sourceId: String): File {
        val safe = sourceId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return File(cacheDir, "remote-listings-$safe.json")
    }

    /** The cached entries for a source, or null when there is no cache. */
    fun load(sourceId: String): List<RemoteEntry>? {
        val file = fileFor(sourceId)
        if (!file.exists()) return null
        return try {
            json.decodeFromString<List<RemoteEntry>>(file.readText())
        } catch (_: Exception) {
            null
        }
    }

    /** Persist a source's parsed entries. Overwrites any older cache. */
    fun save(sourceId: String, entries: List<RemoteEntry>) {
        val file = fileFor(sourceId)
        try {
            cacheDir.mkdirs()
            file.writeText(json.encodeToString(entries))
        } catch (_: Exception) {
            // A cache write must never break the listing itself.
        }
    }

    /** Drop one source's cached entries (used when a source is removed). */
    fun clear(sourceId: String) {
        try {
            fileFor(sourceId).delete()
        } catch (_: Exception) {
        }
    }
}
