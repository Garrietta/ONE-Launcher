package dev.one.launcher.data

import dev.one.launcher.model.Port
import dev.one.launcher.model.PortFork
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The Recompa catalog (https://recompa.app/index.json): a free,
 * open-source (GPL-3.0) index of native game ports maintained by the
 * Recompa project. ONE fetches it, maps it onto [Port] entries, and
 * merges it with the curated ports.json — the curated 5 stay canonical.
 * This file is pure Kotlin (no Android, no network) so the mapping and
 * the merge are unit-testable.
 *
 * The catalog lists ports; it never hosts game files, and neither does
 * ONE. Anything the catalog does not provide stays empty, and the UI
 * says so plainly instead of inventing a value.
 */

/** Schema version ONE understands. Anything else is refused outright. */
const val RECOMPA_SCHEMA_VERSION = 1

@Serializable
data class RecompaIndex(
    val schema_version: Int = 0,
    val generated_at: String = "",
    val ports: List<RecompaPort> = emptyList(),
    val platforms: List<RecompaPlatform> = emptyList()
)

@Serializable
data class RecompaPlatform(
    val id: String = "",
    val name: String = ""
)

@Serializable
data class RecompaPort(
    val id: String = "",
    val name: String = "",
    val game: String = "",
    val original_platform: String = "",
    val type: String = "",
    val description: String = "",
    val forks: List<RecompaFork> = emptyList(),
    val requirements: RecompaRequirements? = null,
    val assets: List<RecompaAsset>? = null,
    val art: RecompaArt? = null
)

@Serializable
data class RecompaRequirements(
    val abi: List<String> = emptyList(),
    val min_sdk: Int? = null,
    val vulkan: Boolean? = null
)

@Serializable
data class RecompaFork(
    val repo: String = "",
    val name: String? = null,
    @SerialName("package") val packageName: String? = null,
    val signer_sha256: String? = null,
    val verified: Boolean? = null,
    val release: RecompaRelease? = null
)

@Serializable
data class RecompaRelease(
    val tag: String = "",
    val asset: RecompaReleaseAsset? = null
)

@Serializable
data class RecompaReleaseAsset(
    val name: String = "",
    val url: String = "",
    val size: Long = 0,
    val sha256: String = ""
)

@Serializable
data class RecompaAsset(
    val id: String = "",
    val label: String = "",
    val accepted: List<RecompaAccepted> = emptyList(),
    val convert_from: List<String> = emptyList(),
    val zip_ok: Boolean = false
)

@Serializable
data class RecompaAccepted(
    val label: String? = null,
    val format: String = ""
)

@Serializable
data class RecompaArt(
    val cover: String? = null
)

private val recompaJson = Json { ignoreUnknownKeys = true }

/**
 * Parse a fetched index.json. Returns null when the text is not valid
 * JSON or when schema_version is anything other than
 * [RECOMPA_SCHEMA_VERSION] — a schema change must never crash the app
 * or silently feed wrong data into the Library; the caller falls back
 * to the curated catalog instead.
 */
fun parseRecompaIndex(text: String): RecompaIndex? {
    return try {
        val index = recompaJson.decodeFromString(RecompaIndex.serializer(), text)
        if (index.schema_version != RECOMPA_SCHEMA_VERSION) null else index
    } catch (e: Exception) {
        null
    }
}

/**
 * Curated port id -> catalog port id, for the 5 curated ports only.
 * No fuzzy matching by name anywhere: a curated port matches a catalog
 * port by overlapping fork repo first, and only then by this map.
 */
val CURATED_CATALOG_IDS = mapOf(
    "soh" to "soh",
    "2ship" to "2ship2harkinian",
    "starship" to "starship"
)

private val HINT_STOPWORDS = setOf(
    "the", "and", "for", "with", "of", "a", "an", "remake", "edition",
    "definitive", "remastered", "collection", "version"
)

/**
 * Filename hints derived from a game title: lowercase word tokens of
 * length 3+, minus stopwords, plus the catalog id itself. These are
 * hints only — a file still needs a matching extension (or an archive
 * name hit) to match.
 */
fun hintsFromGameTitle(game: String, catalogId: String): List<String> {
    val words = game.lowercase()
        .split(Regex("[^a-z0-9]+"))
        .filter { it.length >= 3 && it !in HINT_STOPWORDS }
    return (words + catalogId.lowercase()).distinct()
}

/** Map one catalog port onto a [Port]. Catalog-provided values only. */
fun mapCatalogPort(port: RecompaPort, platformName: String): Port {
    val extensions = port.assets.orEmpty().flatMap { asset ->
        (asset.accepted.map { it.format } + asset.convert_from)
            .map { it.lowercase() }
            .filter { it.isNotBlank() }
            .map { ".$it" }
    }.distinct()
    val zipOk = port.assets.orEmpty().any { it.zip_ok }
    val requiredDump = port.assets.orEmpty()
        .mapNotNull { it.label.takeIf { label -> label.isNotBlank() } }
        .distinct()
        .let { labels ->
            if (labels.isEmpty()) {
                "Your own game files for ${port.game}. " +
                    "File info not listed in the catalog."
            } else {
                "Your own ${labels.joinToString(" / ")}."
            }
        }
    val firstFork = port.forks.firstOrNull()
    return Port(
        id = port.id,
        title = port.name.ifBlank { port.game },
        game = port.game,
        system = platformName.ifBlank { port.original_platform },
        type = port.type,
        repo = firstFork?.repo.orEmpty(),
        releasesUrl = firstFork?.repo
            ?.takeIf { it.isNotBlank() }
            ?.let { "https://github.com/$it/releases" }.orEmpty(),
        packageNames = port.forks.mapNotNull { it.packageName }
            .filter { it.isNotBlank() }.distinct(),
        fileExtensions = extensions,
        fileNameHints = hintsFromGameTitle(port.game, port.id),
        requiredDump = requiredDump,
        minAndroid = port.requirements?.min_sdk ?: 24,
        notes = "From the Recompa catalog (recompa.app).",
        catalogId = port.id,
        platformId = port.original_platform,
        platformName = platformName,
        description = port.description,
        coverArtUrl = port.art?.cover.orEmpty(),
        forks = port.forks.map { mapFork(it) },
        archiveOk = zipOk
    )
}

fun mapFork(fork: RecompaFork): PortFork {
    val asset = fork.release?.asset
    return PortFork(
        repo = fork.repo,
        displayName = fork.name?.takeIf { it.isNotBlank() }
            ?: fork.repo.substringAfter('/', fork.repo),
        packageName = fork.packageName?.takeIf { it.isNotBlank() },
        latestTag = fork.release?.tag.orEmpty(),
        latestAssetName = asset?.name.orEmpty(),
        latestAssetUrl = asset?.url.orEmpty(),
        latestAssetSize = asset?.size ?: 0,
        latestAssetSha256 = asset?.sha256.orEmpty(),
        signerSha256 = fork.signer_sha256?.takeIf { it.isNotBlank() },
        verified = fork.verified
    )
}

/** Result of merging the Recompa catalog into the curated catalog. */
data class CatalogMerge(
    /** Curated ports first (canonical), then catalog-only ports. */
    val merged: List<Port>,
    /** Curated port ids with no catalog match — reported, never guessed. */
    val unmatchedCuratedIds: List<String>,
    /** Catalog port ids whose assets[] is missing — file matching off. */
    val portsWithoutFileInfo: List<String>
)

/**
 * Merge the catalog ports into the curated list. A catalog port that
 * matches a curated port (overlapping fork repo, else the explicit
 * [CURATED_CATALOG_IDS] map) keeps the curated entry's title, notes,
 * packageNames and file info as canonical and gains the catalog's fork
 * list, latest-release data and cover art. Catalog ports with no
 * curated match are appended as new entries.
 */
fun mergeCatalogs(curated: List<Port>, catalog: List<Port>): CatalogMerge {
    val byId = catalog.associateBy { it.catalogId }
    val byRepo = mutableMapOf<String, Port>()
    for (port in catalog) {
        for (fork in port.forks) {
            if (fork.repo.isNotBlank()) byRepo.putIfAbsent(fork.repo, port)
        }
    }
    val unmatched = mutableListOf<String>()
    val matchedCatalogIds = mutableSetOf<String>()
    val merged = mutableListOf<Port>()
    for (curatedPort in curated) {
        val match = byRepo[curatedPort.repo]
            ?: CURATED_CATALOG_IDS[curatedPort.id]?.let { byId[it] }
        if (match == null) {
            unmatched.add(curatedPort.id)
            merged.add(curatedPort)
        } else {
            matchedCatalogIds.add(match.catalogId)
            merged.add(
                curatedPort.copy(
                    catalogId = match.catalogId,
                    platformId = match.platformId,
                    platformName = match.platformName,
                    description = match.description,
                    coverArtUrl = match.coverArtUrl,
                    forks = match.forks
                )
            )
        }
    }
    val catalogOnly = catalog.filter { it.catalogId !in matchedCatalogIds }
    merged.addAll(catalogOnly)
    val withoutFileInfo = catalog
        .filter { it.fileExtensions.isEmpty() }
        .map { it.catalogId }
    return CatalogMerge(
        merged = merged,
        unmatchedCuratedIds = unmatched,
        portsWithoutFileInfo = withoutFileInfo
    )
}
