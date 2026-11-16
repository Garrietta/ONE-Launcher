package dev.one.launcher.model

import kotlinx.serialization.Serializable

/** One fork (build variant) of a port, from the Recompa catalog. */
@Serializable
data class PortFork(
    /** "owner/repo" on GitHub. */
    val repo: String,
    /** Display name; falls back to the repo's second half. */
    val displayName: String,
    val packageName: String? = null,
    val latestTag: String = "",
    val latestAssetName: String = "",
    val latestAssetUrl: String = "",
    val latestAssetSize: Long = 0,
    /** Catalog SHA-256 of the release asset, "" when the catalog has none. */
    val latestAssetSha256: String = "",
    val signerSha256: String? = null,
    val verified: Boolean? = null
)

@Serializable
data class Port(
    val id: String,
    val title: String,
    val game: String,
    val system: String,
    val type: String,
    val repo: String,
    val releasesUrl: String,
    val packageNames: List<String> = emptyList(),
    val fileExtensions: List<String> = emptyList(),
    val fileNameHints: List<String> = emptyList(),
    val fileNameRegex: String? = null,
    val requiredDump: String,
    val minAndroid: Int = 24,
    val notes: String = "",
    // ---- Recompa catalog fields (v1.3, all optional) ----
    /** Catalog id, e.g. "soh". Empty for curated-only ports. */
    val catalogId: String = "",
    /** The catalog's download count for this port, for star-ranking fallback. Null for curated-only ports. */
    val catalogDownloads: Long? = null,
    /** Original platform id from the catalog, e.g. "n64". */
    val platformId: String = "",
    /** Original platform name from the catalog, e.g. "Nintendo 64". */
    val platformName: String = "",
    val description: String = "",
    /** SteamGridDB cover URL from the catalog, "" when none. */
    val coverArtUrl: String = "",
    /** Forks from the catalog; empty for curated-only ports. */
    val forks: List<PortFork> = emptyList(),
    /**
     * True when an archive (.zip/.7z/.rar) may match this port by name.
     * False when the catalog says zip_ok is false for every required
     * file, or when the port has no file info at all.
     */
    val archiveOk: Boolean = true,
    /**
     * The catalog's own instructions for the port's required files
     * (instructions_md), joined when a port has several assets. "" when
     * the catalog provides none. Shown verbatim on the detail page —
     * never invented.
     */
    val fileInstructions: String = "",
    /**
     * Hints that identify this port alone: hints appearing in exactly
     * one port's hint set across the whole merged catalog, at least 4
     * characters long. These drive a NAME_ONLY match on their own — a
     * shared hint ("pokémon", "new", "men") must never match a file to
     * the wrong port by itself. (A file can also match by name when it
     * hits at least two of the port's filename hints, each 4+ chars.)
     * Set by [dev.one.launcher.data.withDistinctiveHints] at merge
     * time; empty for curated-only ports (they all have formats, so
     * they never reach the NAME_ONLY branch).
     */
    val distinctiveNameHints: List<String> = emptyList(),
    /**
     * True when the catalog provides any file info for this port
     * (assets[] present and non-empty). False only when there is truly
     * no asset info — the detail page says "File info not listed in the
     * catalog." then, and only then. A port can have file info (a label,
     * instructions) and still have no formats.
     */
    val hasFileInfo: Boolean = true
)

@Serializable
data class PortCatalog(
    val catalogNote: String = "",
    val ports: List<Port> = emptyList()
)
