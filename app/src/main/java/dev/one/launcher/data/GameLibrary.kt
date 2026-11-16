package dev.one.launcher.data

import dev.one.launcher.model.Port
import kotlinx.serialization.Serializable

/**
 * The game-centric Library (v1.6.0): the Library lists GAMES, not
 * ports. A game is a title + platform (e.g. "The Legend of Zelda:
 * Ocarina of Time", Nintendo 64) and holds every recomp / decomp /
 * source-port for that game. Emulators are excluded — this app is
 * recomps/decomps, not simple emulation. This file is pure Kotlin (no
 * Android, no network) so the grouping, the game-level file matching,
 * and the star ranking are unit-testable. The GitHub star fetch and its
 * cache live in [GitHubStarsApi] and [StarCacheStore]; the game page
 * rendering lives in the UI.
 */

/** One game in the Library: a title + platform and its ports. */
data class GameEntry(
    val title: String,
    val platform: String,
    /** Ports for this game, in fallback order (catalog downloads, then title). */
    val ports: List<Port>
) {
    /** Stable, URL-safe id for navigation: slug(title) + "--" + slug(platform). */
    val key: String get() = gameKey(title, platform)

    /**
     * Cover art for the game: the cover of the port that ranks first by
     * the stable fallback order (catalog downloads). Stars can reorder
     * the ports on the game page once they load, but the cover stays
     * fixed here so the shelf never flickers between images.
     */
    val coverArtUrl: String
        get() = ports.maxByOrNull { it.catalogDownloads ?: -1L }
            ?.coverArtUrl.orEmpty()
}

/** URL-safe key for a game: slug(title) + "--" + slug(platform). */
fun gameKey(title: String, platform: String): String =
    slugify(title) + "--" + slugify(platform)

private fun slugify(text: String): String =
    text.lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')

fun findGame(games: List<GameEntry>, key: String): GameEntry? =
    games.firstOrNull { it.key == key }

/** True when a port is an emulator — excluded from the game view. */
fun isEmulator(port: Port): Boolean =
    port.type.equals("emulator", ignoreCase = true)

/**
 * Group ports into games by (game title, platform). A port with no
 * usable game title falls back to its own title as the game title
 * (the catalog never leaves `game` blank, but a curated-only port
 * could). The platform is the catalog platform name, falling back to
 * the port's system name when the catalog gave none — so a
 * curated-only port groups with its catalog sibling (Zelda64Recompiled
 * groups with 2Ship under Majora's Mask, Zelda3 with moonpearl under
 * A Link to the Past). Games sort by title, then platform; ports
 * within a game sort by catalog downloads descending (null last),
 * then title — the stable fallback order the star ranking refines.
 *
 * Against the live catalog (536 ports): 529 games, 525 with one port
 * and 4 with two (Majora's Mask, A Link to the Past, Donkey Kong 64,
 * Castlevania: Symphony of the Night); 5 emulators excluded.
 */
fun groupPortsByGame(ports: List<Port>): List<GameEntry> {
    val groups = linkedMapOf<Pair<String, String>, MutableList<Port>>()
    for (port in ports) {
        if (isEmulator(port)) continue
        val title = port.game.ifBlank { port.title }
        // The platform is the catalog's name as-is (so platform id
        // "other" shows "Otra", the catalog's own name — v1.7.0 reverted
        // the v1.6.1 "Other" display mapping, which Garrett never
        // asked for), falling back to the port's system name when the
        // catalog gave none.
        val platform = port.platformName.ifBlank { port.system }
        groups.getOrPut(title to platform) { mutableListOf() }.add(port)
    }
    return groups.entries
        .map { (key, gamePorts) ->
            GameEntry(
                title = key.first,
                platform = key.second,
                ports = gamePorts.sortedWith(
                    compareByDescending<Port> { it.catalogDownloads ?: -1L }
                        .thenBy { it.title.lowercase() }
                )
            )
        }
        .sortedWith(
            compareBy<GameEntry> { it.title.lowercase() }
                .thenBy { it.platform.lowercase() }
        )
}

/**
 * Grid columns for the unfolded Library (v1.7.0): 3 wide while the
 * detail pane is open, 5 wide when it is closed. A pure function of
 * the detail pane state, so it is unit-testable; the grid rendering
 * itself is Compose UI and is not JVM-testable.
 */
fun unfoldedGridColumns(detailOpen: Boolean): Int = if (detailOpen) 3 else 5

// ---- Game-level file matching ----

/** Where a game file came from. */
enum class GameFileSource {
    /** Already on this device: local folder, Downloads/ONE, picked file. */
    LOCAL,
    /** The user's own Premiumize cloud. */
    CLOUD,
    /** The user's own remote folder (server). */
    SERVER
}

/**
 * One file for a game, from any of the three owned sources. `portId`
 * is the port it matched (the first matching port, in the game's
 * port order). The action depends on the source: LOCAL files are
 * already here (Copy to Downloads/ONE for a detected non-direct file,
 * Extract for a downloaded confirmed archive); CLOUD files download
 * via [dev.one.launcher.ui.OneViewModel.downloadItem]; SERVER files
 * open in the browser at [remoteUrl]. [where] is the plain label
 * shown on the row ("Downloads/ONE", "Premiumize", a server name).
 */
data class GameFile(
    val name: String,
    val kind: MatchKind,
    val source: GameFileSource,
    val portId: String,
    val where: String,
    val premiumizeItem: PremiumizeItem? = null,
    val remoteSourceName: String? = null,
    val remoteUrl: String? = null,
    val detected: DetectedFile? = null,
    /** For a downloaded confirmed archive: the matching entry inside it. */
    val downloadedInnerEntry: String? = null
)

/**
 * Premiumize files matching any of the game's ports. Folders are
 * dropped — only files can match. A file matching several of the
 * game's ports is listed once, against the first matching port.
 */
fun matchCloudFilesForGame(
    items: List<PremiumizeItem>,
    ports: List<Port>
): List<GameFile> =
    items.filter { it.type != "folder" }.mapNotNull { item ->
        val match = ports.firstNotNullOfOrNull { port ->
            PortMatcher.matchKind(item.name, port, item.link)?.let { port to it }
        } ?: return@mapNotNull null
        GameFile(
            name = item.name,
            kind = match.second,
            source = GameFileSource.CLOUD,
            portId = match.first.id,
            where = "Premiumize",
            premiumizeItem = item
        )
    }

/**
 * Remote-folder files matching any of the game's ports. `sources` is
 * (source name, file entries) per configured remote folder. Folders
 * are never listed. A file matching several of the game's ports is
 * listed once, against the first matching port.
 */
fun matchServerFilesForGame(
    sources: List<Pair<String, List<RemoteEntry>>>,
    ports: List<Port>
): List<GameFile> {
    val out = mutableListOf<GameFile>()
    for ((sourceName, entries) in sources) {
        for (entry in entries) {
            if (entry.isFolder) continue
            // Magnet entries are copyable links, not files — they
            // never match a game and never appear in "Your files".
            if (entry.isMagnet) continue
            val match = ports.firstNotNullOfOrNull { port ->
                PortMatcher.matchKind(entry.name, port, entry.url)?.let { port to it }
            } ?: continue
            out.add(
                GameFile(
                    name = entry.name,
                    kind = match.second,
                    source = GameFileSource.SERVER,
                    portId = match.first.id,
                    where = sourceName,
                    remoteSourceName = sourceName,
                    remoteUrl = entry.url
                )
            )
        }
    }
    return out
}

/**
 * One magnet link for a game, from a remote folder (v1.8.3). A magnet
 * is a copyable link, not a file: `magnetUrl` is the full magnet: URI
 * to copy (never a download, never opened in the browser). `kind` is
 * the match kind it matched by (usually NAME_ONLY — most ports list
 * no formats). `where` is the remote folder's configured name.
 */
data class GameMagnetLink(
    val name: String,
    val magnetUrl: String,
    val kind: MatchKind,
    val portId: String,
    val where: String,
    val remoteSourceName: String? = null
)

/**
 * Remote-folder magnet links matching any of the game's ports
 * (v1.8.3). `sources` is (source name, file entries) per configured
 * remote folder. Only magnet entries are considered, matched by name
 * through the same matcher (platform-consistency, 90% similarity,
 * extension gate on the name) — the magnet: URL is never passed to
 * the gate. A magnet matching several of the game's ports is listed
 * once, against the first matching port; the same magnet URI listed
 * in two sources is listed once. The result is ordered by name.
 * These are display-only copy links: they never enter "Your files"
 * or "Has files" — see [matchServerFilesForGame].
 */
fun matchMagnetLinksForGame(
    sources: List<Pair<String, List<RemoteEntry>>>,
    ports: List<Port>
): List<GameMagnetLink> {
    val out = mutableListOf<GameMagnetLink>()
    val seenUrls = mutableSetOf<String>()
    for ((sourceName, entries) in sources) {
        for (entry in entries) {
            if (entry.isFolder || !entry.isMagnet) continue
            if (!seenUrls.add(entry.url)) continue
            val match = ports.firstNotNullOfOrNull { port ->
                PortMatcher.matchMagnetKind(entry, port)?.let { port to it }
            } ?: continue
            out.add(
                GameMagnetLink(
                    name = entry.name,
                    magnetUrl = entry.url,
                    kind = match.second,
                    portId = match.first.id,
                    where = sourceName,
                    remoteSourceName = sourceName
                )
            )
        }
    }
    return out.sortedBy { it.name.lowercase() }
}

/**
 * The local side of one port's files, as the game page needs it. The
 * UI maps each port's [dev.one.launcher.ui.PortStatus] onto this —
 * this file stays free of the UI layer.
 */
data class LocalFileState(
    val downloadedFileName: String?,
    val downloadedMatchKind: MatchKind?,
    val downloadedInnerEntry: String?,
    val assignedFileName: String?
)

/**
 * Local files matching any of the game's ports: files detected in the
 * user's local game-files folder, files already downloaded into
 * Downloads/ONE, and picked (assigned) files. A file matching several
 * of the game's ports is listed once, against the first matching port.
 */
fun matchLocalFilesForGame(
    detected: Map<String, List<DetectedFile>>,
    localStates: Map<String, LocalFileState>,
    ports: List<Port>
): List<GameFile> {
    val out = mutableListOf<GameFile>()
    for (port in ports) {
        for (file in detected[port.id].orEmpty()) {
            out.add(
                GameFile(
                    name = file.name,
                    kind = file.kind,
                    source = GameFileSource.LOCAL,
                    portId = port.id,
                    where = "Found in your folder",
                    detected = file
                )
            )
        }
        val state = localStates[port.id] ?: continue
        if (state.downloadedFileName != null) {
            out.add(
                GameFile(
                    name = state.downloadedFileName,
                    kind = state.downloadedMatchKind ?: MatchKind.DIRECT,
                    source = GameFileSource.LOCAL,
                    portId = port.id,
                    where = "Downloads/ONE",
                    downloadedInnerEntry = state.downloadedInnerEntry
                )
            )
        }
        if (state.assignedFileName != null) {
            out.add(
                GameFile(
                    name = state.assignedFileName,
                    kind = MatchKind.DIRECT,
                    source = GameFileSource.LOCAL,
                    portId = port.id,
                    where = "Picked file"
                )
            )
        }
    }
    return out
}

/**
 * Deduplicate one game's files by file name (case-insensitive): the
 * same file in several sources, or matching several of the game's
 * ports, is shown once. Priority is on-device first, then the cloud,
 * then the server — a file already here beats one that still needs
 * downloading, and a downloadable cloud copy beats a browser link.
 * The result is ordered by that priority, then by name.
 */
fun dedupeGameFiles(files: List<GameFile>): List<GameFile> {
    val priority = mapOf(
        GameFileSource.LOCAL to 0,
        GameFileSource.CLOUD to 1,
        GameFileSource.SERVER to 2
    )
    return files
        .groupBy { it.name.lowercase() }
        .values
        .map { same ->
            same.minWith(
                compareBy<GameFile> { priority[it.source] ?: 3 }
                    .thenBy { it.portId }
            )
        }
        .sortedWith(
            compareBy<GameFile> { priority[it.source] ?: 3 }
                .thenBy { it.name.lowercase() }
        )
}

/**
 * Merge one game's live files with its cached files (v1.8.4): the
 * game page shows the cached pairings immediately on open, and the
 * live match refreshes them when the sources land. Live files win
 * on a name clash — a cached copy only fills a gap the live match
 * has not produced yet. When the live sources have not loaded, the
 * live list is empty and the page is exactly the cached list, so
 * the page never sits on "Checking your files…" while the cache
 * has files for the game.
 */
fun mergeLiveAndCachedFiles(
    live: List<GameFile>,
    cached: List<GameFile>
): List<GameFile> {
    val liveFiles = dedupeGameFiles(live)
    val liveNames = liveFiles.map { it.name.lowercase() }.toSet()
    val extra = cached.filter { it.name.lowercase() !in liveNames }
    return dedupeGameFiles(liveFiles + extra)
}

/**
 * Merge one game's live magnet links with its cached magnets
 * (v1.8.4): the same immediate-from-cache rule as
 * [mergeLiveAndCachedFiles], keyed on the magnet URI (a magnet's
 * name can repeat across regions; the URI is the identity). Live
 * links win on a URI clash; cached links only fill gaps.
 */
fun mergeLiveAndCachedMagnets(
    live: List<GameMagnetLink>,
    cached: List<GameMagnetLink>
): List<GameMagnetLink> {
    val liveUrls = live.map { it.magnetUrl }.toSet()
    val extra = cached.filter { it.magnetUrl !in liveUrls }
    return (live + extra).sortedBy { it.name.lowercase() }
}

// ---- Star ranking ----

/** One cached star count: repo "owner/name" -> stars, fetched at epoch ms. */
@Serializable
data class StarEntry(
    val stars: Int,
    val fetchedAtMs: Long
)

/** Star cache lifetime: about 7 days. */
const val STAR_CACHE_TTL_MS: Long = 7L * 24 * 60 * 60 * 1000

/** True when a cached star count is still fresh enough to use. */
fun isStarFresh(fetchedAtMs: Long, nowMs: Long): Boolean =
    nowMs >= fetchedAtMs && nowMs - fetchedAtMs < STAR_CACHE_TTL_MS

/**
 * Rank a game's ports by GitHub stars (repo -> stargazers), falling
 * back to the catalog's download counts when stars are unknown, and
 * putting ports with neither at the bottom. Ties break by title so
 * the order is stable between opens.
 */
fun rankPortsByStars(
    ports: List<Port>,
    stars: Map<String, Int>
): List<Port> =
    ports.sortedWith(
        compareByDescending<Port> { stars[it.repo] ?: -1 }
            .thenByDescending { it.catalogDownloads ?: -1L }
            .thenBy { it.title.lowercase() }
    )

/**
 * The set of game keys with at least one matching file, for the
 * Library's "Games with matches" filter. One pass over the files: a
 * port counts as matched when any file (cloud, server, or local)
 * matches it, and a game counts when any of its ports is matched.
 * Uses the same game-level matching as the game page — no second
 * matcher. The cloud side mirrors the game page: the flat listAll
 * when present, else the per-port search fallbacks.
 */
fun gamesWithFiles(
    games: List<GameEntry>,
    cloudItems: List<PremiumizeItem>,
    cloudFallback: Map<String, List<PremiumizeItem>>,
    serverSources: List<Pair<String, List<RemoteEntry>>>,
    detected: Map<String, List<DetectedFile>>,
    localStates: Map<String, LocalFileState>
): Set<String> {
    val ports = games.flatMap { it.ports }
    val matchedPorts = mutableSetOf<String>()
    val cloud = if (cloudItems.isNotEmpty()) {
        cloudItems
    } else {
        games.flatMap { game ->
            game.ports.flatMap { cloudFallback[it.id].orEmpty() }
        }
    }
    for (item in cloud) {
        if (item.type == "folder") continue
        for (match in PortMatcher.matchKinds(item.name, ports, item.link)) {
            matchedPorts.add(match.port.id)
        }
    }
    for ((_, entries) in serverSources) {
        for (entry in entries) {
            if (entry.isFolder) continue
            if (entry.isMagnet) continue
            for (match in PortMatcher.matchKinds(entry.name, ports, entry.url)) {
                matchedPorts.add(match.port.id)
            }
        }
    }
    for ((portId, files) in detected) {
        if (files.isNotEmpty()) matchedPorts.add(portId)
    }
    for ((portId, state) in localStates) {
        if (state.downloadedFileName != null || state.assignedFileName != null) {
            matchedPorts.add(portId)
        }
    }
    return games
        .filter { game -> game.ports.any { it.id in matchedPorts } }
        .map { it.key }
        .toSet()
}

// ---- Library filter state (v1.8.2) ----

/**
 * The Library's filter state: search query, platform filter, and the
 * "Has files" toggle. It lives in the ViewModel, not in a composable's
 * `remember` — so navigating into a game page and back restores the
 * previous Library state instead of resetting to the default main
 * screen. A plain value object, so the filter itself is unit-testable
 * without the UI.
 */
data class LibraryFilter(
    val query: String = "",
    val platform: String = "",
    val matchesOnly: Boolean = false
)

/**
 * The Library's game filter, as a pure function of the games, the
 * filter state, and the set of games with files. Filters by game
 * title (or a port title inside a game) and by platform; "Has files"
 * keeps only games with at least one matching file. The same rule the
 * Library screen applies.
 */
fun filterGames(
    games: List<GameEntry>,
    filter: LibraryFilter,
    gamesWithFiles: Set<String>
): List<GameEntry> = games.filter { game ->
    (filter.platform.isEmpty() || game.platform == filter.platform) &&
        (!filter.matchesOnly || game.key in gamesWithFiles) &&
        (filter.query.isBlank() ||
            game.title.contains(filter.query, ignoreCase = true) ||
            game.ports.any { it.title.contains(filter.query, ignoreCase = true) })
}

// ---- File source label (v1.8.2) ----

/**
 * The clear source label for a file, for the poster tag and the game
 * page file row: where the file comes from. Local files read "Local";
 * cloud files read "Premiumize"; server files read the remote folder's
 * configured name (e.g. "json"). The match kind stays a secondary
 * label — the source is the primary tag.
 */
fun fileSourceLabel(file: GameFile): String = when (file.source) {
    GameFileSource.LOCAL -> "Local"
    GameFileSource.CLOUD -> "Premiumize"
    GameFileSource.SERVER ->
        file.remoteSourceName?.takeIf { it.isNotBlank() } ?: file.where
}

// ---- Full pairing computation (v1.8.2) ----

/**
 * The full pairing computation: every game's matched files, from all
 * three owned sources, aggregated and deduped by file name (on-device
 * first, then cloud, then server) — the same matching the game page
 * does for one game, run for the whole Library. The result is the
 * pairing cache's content: game key -> files, only for games with at
 * least one file. The cloud side mirrors [gamesWithFiles]: the flat
 * listAll when present, else the per-port search fallbacks. Pure, so
 * the background refresh can run it on a background thread and the
 * cache round-trip is unit-testable.
 */
fun pairGamesWithFiles(
    games: List<GameEntry>,
    cloudItems: List<PremiumizeItem>,
    cloudFallback: Map<String, List<PremiumizeItem>>,
    serverSources: List<Pair<String, List<RemoteEntry>>>,
    detected: Map<String, List<DetectedFile>>,
    localStates: Map<String, LocalFileState>
): Map<String, List<GameFile>> {
    val out = mutableMapOf<String, List<GameFile>>()
    for (game in games) {
        val cloud = matchCloudFilesForGame(
            if (cloudItems.isNotEmpty()) {
                cloudItems
            } else {
                game.ports.flatMap { cloudFallback[it.id].orEmpty() }
            },
            game.ports
        )
        val server = matchServerFilesForGame(serverSources, game.ports)
        val local = matchLocalFilesForGame(detected, localStates, game.ports)
        val files = dedupeGameFiles(local + cloud + server)
        if (files.isNotEmpty()) out[game.key] = files
    }
    return out
}

/**
 * The magnet pairing computation (v1.8.3): every game's matched
 * magnet links, from the remote folders only (magnets only ever come
 * from a remote folder's JSON list). Pure, so the background refresh
 * can run it alongside [pairGamesWithFiles] and the cache round-trip
 * is unit-testable. Only games with at least one magnet are present.
 * Magnet pairings are a separate channel from the downloadable-file
 * pairings — they never feed "Has files" or "Your files".
 */
fun pairGamesWithMagnets(
    games: List<GameEntry>,
    serverSources: List<Pair<String, List<RemoteEntry>>>
): Map<String, List<GameMagnetLink>> {
    val out = mutableMapOf<String, List<GameMagnetLink>>()
    for (game in games) {
        val magnets = matchMagnetLinksForGame(serverSources, game.ports)
        if (magnets.isNotEmpty()) out[game.key] = magnets
    }
    return out
}

