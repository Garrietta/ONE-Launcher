package dev.one.launcher.data

import kotlinx.serialization.Serializable

/**
 * The pairing cache (v1.8.2): the computed file↔game pairings, saved
 * on-device so the Library opens pre-populated instead of re-running
 * the match every time.
 *
 * On app open the ViewModel loads the cache first and the Library and
 * "Has files" show from it immediately. In the background the app
 * re-runs the matching when the Recompa catalog, the Premiumize
 * library, or a JSON remote folder changes, and overwrites the cache
 * with the new list — which becomes the new default pre-populated
 * cache. The cache is local-only, never synced, never committed.
 *
 * A cached file carries the display data (name, match kind, source,
 * where) and the action data (the Premiumize item id for Download,
 * the remote URL for Open in browser, the detected file's SAF URI for
 * Copy) — so a game page populated from cache can still act on its
 * files before the live sources finish loading. The Premiumize
 * download link is NOT stored (it is re-fetched via itemDetails when
 * the user taps Download), so a stale link can never be served.
 */

/**
 * Cache/matcher version. Bump this whenever a matcher rule changes
 * (platform-consistency, 90% similarity, extension gate) — a cache
 * written under old rules is ignored and the matching re-runs, so a
 * stale cache built under old rules can never survive a rule change.
 */
const val PAIRING_CACHE_VERSION: Int = 1

/**
 * One cached file for a game. `kind` and `source` are the enum names
 * ([MatchKind], [GameFileSource]) as strings, so an unknown value from
 * a future version decodes as "not this file" instead of crashing the
 * whole cache.
 */
@Serializable
data class CachedGameFile(
    val name: String,
    val kind: String,
    val source: String,
    val portId: String,
    val where: String,
    val remoteSourceName: String? = null,
    val remoteUrl: String? = null,
    val premiumizeItem: PremiumizeItem? = null,
    val detected: DetectedFile? = null,
    val downloadedInnerEntry: String? = null
)

/** One cached magnet link for a game (v1.8.3). `magnetUrl` is the
 *  full magnet: URI to copy. `kind` is the [MatchKind] name as a
 *  string, so an unknown value from a future version decodes as "not
 *  this link" instead of crashing the whole cache. */
@Serializable
data class CachedMagnetLink(
    val name: String,
    val magnetUrl: String,
    val kind: String,
    val portId: String,
    val where: String,
    val remoteSourceName: String? = null
)

/** The whole cache: version, when it was computed, game key -> files.
 *  `magnetPairings` is the magnet channel (v1.8.3): game key -> magnet
 *  links, cached alongside the downloadable files so the game page
 *  pre-populates its Magnet links too. It defaults to empty, so a
 *  cache written by v1.8.2 (which has no such field) still decodes —
 *  the background refresh then fills it in. */
@Serializable
data class PairingCache(
    val version: Int,
    val computedAtMs: Long,
    val pairings: Map<String, List<CachedGameFile>>,
    val magnetPairings: Map<String, List<CachedMagnetLink>> = emptyMap()
)

/** True when a loaded cache may be used: present and written under
 *  the current matcher rules. A wrong-version cache is stale by
 *  definition — the matching re-runs instead of trusting it. */
fun isPairingCacheUsable(cache: PairingCache?): Boolean =
    cache != null && cache.version == PAIRING_CACHE_VERSION

/** A [GameFile] as a cache record. The Premiumize download link is
 *  dropped — Download re-fetches it via itemDetails. */
fun GameFile.toCached(): CachedGameFile =
    CachedGameFile(
        name = name,
        kind = kind.name,
        source = source.name,
        portId = portId,
        where = where,
        remoteSourceName = remoteSourceName,
        remoteUrl = remoteUrl,
        premiumizeItem = premiumizeItem?.copy(link = null),
        detected = detected,
        downloadedInnerEntry = downloadedInnerEntry
    )

/** A cache record back to a [GameFile]. Null when the stored kind or
 *  source is not a known enum value — the record is dropped, never
 *  allowed to poison the game page. */
fun CachedGameFile.toGameFile(): GameFile? {
    val kind = try {
        MatchKind.valueOf(kind)
    } catch (e: IllegalArgumentException) {
        return null
    }
    val source = try {
        GameFileSource.valueOf(source)
    } catch (e: IllegalArgumentException) {
        return null
    }
    return GameFile(
        name = name,
        kind = kind,
        source = source,
        portId = portId,
        where = where,
        premiumizeItem = premiumizeItem,
        remoteSourceName = remoteSourceName,
        remoteUrl = remoteUrl,
        detected = detected,
        downloadedInnerEntry = downloadedInnerEntry
    )
}

/** A [GameMagnetLink] as a cache record. */
fun GameMagnetLink.toCached(): CachedMagnetLink =
    CachedMagnetLink(
        name = name,
        magnetUrl = magnetUrl,
        kind = kind.name,
        portId = portId,
        where = where,
        remoteSourceName = remoteSourceName
    )

/** A cache record back to a [GameMagnetLink]. Null when the stored
 *  kind is not a known enum value, or the magnet URL is blank — the
 *  record is dropped, never allowed to poison the game page. */
fun CachedMagnetLink.toGameMagnetLink(): GameMagnetLink? {
    if (magnetUrl.isBlank()) return null
    val kind = try {
        MatchKind.valueOf(kind)
    } catch (e: IllegalArgumentException) {
        return null
    }
    return GameMagnetLink(
        name = name,
        magnetUrl = magnetUrl,
        kind = kind,
        portId = portId,
        where = where,
        remoteSourceName = remoteSourceName
    )
}
