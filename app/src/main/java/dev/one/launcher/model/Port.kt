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
    val archiveOk: Boolean = true
)

@Serializable
data class PortCatalog(
    val catalogNote: String = "",
    val ports: List<Port> = emptyList()
)
