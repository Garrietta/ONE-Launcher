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
    /** Catalog download count, used as the star-ranking fallback. */
    val downloads: Long? = null,
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
    val zip_ok: Boolean = false,
    /** True when the file must be supplied by hand, not matched by format. */
    val manual: Boolean = false,
    /** Free-text instructions for supplying the file, "" when none. */
    @SerialName("instructions_md") val instructionsMd: String = ""
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
 * Tokens that must never become a filename hint by themselves. Region and
 * format tags appear in almost every file name ("(USA)"), and
 * franchise-generic words are shared by a whole series — a port titled
 * "Daytona USA" must not match every "(USA)" file, and an Ocarina of
 * Time file must not match every Zelda port. A match needs the game's
 * distinctive tokens ("ocarina", "daytona"). Every port keeps its catalog
 * id as a hint, so a port whose title is all generic words still matches
 * its own files by id.
 */
private val HINT_GENERIC_TOKENS = setOf(
    // Region / format tags.
    "usa", "us", "eur", "eu", "uk", "jp", "jpn", "pal", "ntsc", "world",
    "kor", "aus", "can", "bra", "ger", "fra", "spa", "ita", "rus", "chi", "chn",
    // Franchise-generic words and generic descriptors.
    "legend", "zelda", "time", "end", "dual", "screen", "super", "bros", "game"
)

/**
 * Filename hints derived from a game title: diacritics folded, then
 * lowercase word tokens of length 3+, minus stopwords and generic
 * tokens (region tags and franchise-generic words — see
 * [HINT_GENERIC_TOKENS]), plus the catalog id itself. These are hints
 * only — a file still needs a matching extension (or an archive name
 * hit) to match, except for ports the catalog lists no formats for,
 * which match by name only ([MatchKind.NAME_ONLY]).
 */
fun hintsFromGameTitle(game: String, catalogId: String): List<String> {
    val words = normalizeForTokens(game)
        .split(Regex("[^a-z0-9]+"))
        .filter { it.length >= 3 && it !in HINT_STOPWORDS && it !in HINT_GENERIC_TOKENS }
    return (words + normalizeForTokens(catalogId)).distinct()
}

/**
 * Title similarity for the NAME_ONLY gate (v1.8.0): the overlap
 * coefficient between a file's title words and a game's title words
 * — |intersection| / min(|file|, |game|). Both titles are cleaned the
 * same way: the file's base name only (a remote path prefix is not
 * part of the title), extension stripped, parenthesized and bracketed
 * tags removed ("(GB)", "(USA)", "[0400...]"), diacritics folded,
 * lowercased, split into words, and tokens shorter than 3 characters
 * plus stopwords and generic tokens dropped — the same "significant
 * word" rule as [hintsFromGameTitle]. Returns 0.0 when either side
 * has no significant words.
 *
 * The coefficient is symmetric: it is 1.0 when the shorter title is
 * fully contained in the longer one. That is the point — a file may
 * legitimately be a subset of a longer game title (Eidos: "Pokemon
 * Black" vs "Pokémon Black & White"), and a game title may be a
 * subset of a longer file title (Pokopia: game "Pokopia" vs file
 * "Pokémon Pokopia"). A one-directional file→game containment would
 * pass the first and fail the second, so the gate uses the symmetric
 * form. Either way a single shared word is not enough: "Pokemon
 * Blue" vs "Blue Dragon" shares only "blue" (0.5), "Pokemon Gold"
 * vs "Marble Blast Gold" only "gold" (0.5), "Mario Kart Double
 * Dash" vs "Tom Clancy's Splinter Cell: Double Agent" only "double"
 * (0.25), and "Sega - Master System - Mark III" vs "System Shock"
 * only "system" (0.5) — all below the 0.90 gate.
 */
fun titleSimilarity(fileName: String, gameTitle: String): Double {
    val fileWords = titleWords(fileName).toSet()
    val gameWords = titleWords(gameTitle).toSet()
    if (fileWords.isEmpty() || gameWords.isEmpty()) return 0.0
    val shared = fileWords.intersect(gameWords).size
    return shared.toDouble() / minOf(fileWords.size, gameWords.size)
}

/** Significant words of a title, cleaned as described above. */
fun titleWords(title: String): List<String> {
    val base = title.substringAfterLast('/').substringAfterLast('\\')
    // Strip a trailing file extension — but only when it looks like
    // one. A game title can itself contain dots ("S.T.A.L.K.E.R.",
    // "Dr. Robotnik's..."), and stripping at the last dot would
    // delete the rest of the title, leaving no significant words at
    // all. An extension is short, alphanumeric, and has a letter.
    val noExt = run {
        val dot = base.lastIndexOf('.')
        if (dot < 0) {
            base
        } else {
            val ext = base.substring(dot + 1)
            val looksLikeExtension = ext.isNotEmpty() && ext.length <= 10 &&
                ext.all { it.isLetterOrDigit() } && ext.any { it.isLetter() }
            if (looksLikeExtension) base.substring(0, dot) else base
        }
    }
    val noTags = noExt.replace(Regex("\\([^)]*\\)|\\[[^]]*\\]"), " ")
    return normalizeForTokens(noTags)
        .split(Regex("[^a-z0-9]+"))
        .filter { it.length >= 3 && it !in HINT_STOPWORDS && it !in HINT_GENERIC_TOKENS }
}

/** Map one catalog port onto a [Port]. Catalog-provided values only. */
fun mapCatalogPort(port: RecompaPort, platformName: String): Port {
    val assets = port.assets.orEmpty()
    val extensions = assets.flatMap { asset ->
        (asset.accepted.map { it.format } + asset.convert_from)
            .map { it.lowercase() }
            .filter { it.isNotBlank() }
            .map { ".$it" }
    }.distinct()
    val zipOk = assets.any { it.zip_ok }
    val labels = assets
        .mapNotNull { it.label.takeIf { label -> label.isNotBlank() } }
        .distinct()
    // The requirement is the catalog's own label text. A label that
    // already starts with "Your" ("Your Switch 2 game dump…") is shown
    // as-is — prefixing "Your own" would read "Your own Your…".
    // The "not listed" phrase is NOT embedded here: the detail page
    // renders it from hasFileInfo, so it appears only when there is
    // truly no asset info at all.
    val requiredDump = when {
        labels.isEmpty() -> "Your own game files for ${port.game}."
        labels.joinToString(" / ").startsWith("Your", ignoreCase = true) ->
            "${labels.joinToString(" / ")}."
        else -> "Your own ${labels.joinToString(" / ")}."
    }
    val instructions = assets
        .mapNotNull { it.instructionsMd.takeIf { text -> text.isNotBlank() } }
        .distinct()
        .joinToString("\n\n")
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
        catalogDownloads = port.downloads,
        platformId = port.original_platform,
        platformName = platformName,
        description = port.description,
        coverArtUrl = port.art?.cover.orEmpty(),
        forks = port.forks.map { mapFork(it) },
        archiveOk = zipOk,
        fileInstructions = instructions,
        hasFileInfo = assets.isNotEmpty()
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

/**
 * Fill in each port's [Port.distinctiveNameHints]: a hint is
 * distinctive when it appears in exactly one port's hint set across
 * the whole list and is at least 4 characters long. Franchise words
 * ("pokémon" — "pok"/"mon" after tokenizing — shared by every
 * Pokémon port) and common English words ("new", "men") are shared by
 * several ports, so they can never be distinctive; a 3-letter hint
 * ("men", "rts", "gt2") is too short to trust on its own. A port
 * whose hints are all shared or too short keeps an empty list and
 * matches nothing by name — safe failure, better than a false match.
 * The catalog id slug is a hint too, so a port whose title words are
 * all shared may keep only its id as distinctive — in practice that
 * never appears in a real file name, so such a port effectively loses
 * name matching and is assigned by hand instead.
 */
fun withDistinctiveHints(ports: List<Port>): List<Port> {
    val owners = mutableMapOf<String, MutableSet<String>>()
    for (port in ports) {
        for (hint in port.fileNameHints.toSet()) {
            owners.getOrPut(hint) { mutableSetOf() }.add(port.id)
        }
    }
    return ports.map { port ->
        val distinctive = port.fileNameHints
            .filter { hint -> hint.length >= 4 && owners[hint]?.size == 1 }
            .distinct()
        port.copy(distinctiveNameHints = distinctive)
    }
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
    // Distinctive hints are computed over the whole merged list, so a
    // hint shared by any two ports here can never drive a NAME_ONLY
    // match for either of them.
    return CatalogMerge(
        merged = withDistinctiveHints(merged),
        unmatchedCuratedIds = unmatched,
        portsWithoutFileInfo = withoutFileInfo
    )
}
