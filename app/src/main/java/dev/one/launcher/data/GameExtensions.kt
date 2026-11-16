package dev.one.launcher.data

/**
 * Hoisted regexes (v1.8.7): [platformTagFromFileName] ran inside the
 * matcher hot path, compiling both patterns on every call. Hoisted
 * to top-level constants; a compiled Regex is immutable and
 * thread-safe.
 */
private val PAREN_GROUP_REGEX = Regex("\\(([^)]*)\\)")
private val WHITESPACE_HYPHEN_REGEX = Regex("[\\s\\-]+")

/**
 * Platform -> game-file extensions dictionary (v1.6.1). The single
 * source of truth for "is this a game file".
 *
 * Seeded from the live Recompa catalog's accepted formats per
 * platform (verified 2026-11-16 against https://recompa.app/index.json):
 * 32x, ds, gamecube, gb, gba, gbc, n64, pc, ps2, snes, wii, xbox and
 * xbox360 are exactly the catalog's accepted formats for those
 * platforms. The 13 platforms the catalog lists no formats for carry
 * the standard formats for that platform instead (3ds, amiga, dos,
 * genesis, nes, ps1, ps3, ps4, psp, switch, switch2, wiiu, and
 * "other" — explicit empty). Mastersystem and Dreamcast have no ports
 * in the catalog yet but are in its platform list, so they are covered
 * too. GameCube and Wii also list the common compressed formats the
 * catalog does not (rvz, gcz, wbfs) — all game-only formats, so they
 * cannot admit a movie or document by accident.
 *
 * Extensions are bare and lowercase, no leading dot. Archives
 * (.zip/.7z/.rar) are platform-agnostic — they can contain a game
 * for any platform — and are handled separately from the per-platform
 * lists. This file is pure Kotlin (no Android, no network) so the
 * dictionary and the game-file check are unit-testable.
 */
val GAME_FILE_EXTENSIONS: Map<String, Set<String>> = mapOf(
    // From the catalog's accepted formats (2026-11-16).
    "32x" to setOf("32x"),
    "ds" to setOf("nds"),
    "gamecube" to setOf("iso", "rvz", "gcz"),
    "gb" to setOf("gb", "gba", "gbc"),
    "gba" to setOf("gba"),
    "gbc" to setOf("gbc"),
    "n64" to setOf("n64", "v64", "z64"),
    "pc" to setOf("hog", "pig"),
    "ps2" to setOf("iso"),
    "snes" to setOf("sfc"),
    "wii" to setOf("wad", "rvz", "wbfs", "gcz"),
    "xbox" to setOf("xbe"),
    "xbox360" to setOf("xex"),
    // The catalog lists no formats for these platforms: standard ones.
    "3ds" to setOf("3ds", "cia", "cci"),
    "amiga" to setOf("adf", "lha", "ipf"),
    "dos" to setOf("exe", "com"),
    "dreamcast" to setOf("gdi", "cdi", "chd"),
    "genesis" to setOf("md", "smd", "bin", "gen"),
    "mastersystem" to setOf("sms"),
    "nes" to setOf("nes"),
    "other" to emptySet(),
    "ps1" to setOf("bin", "cue", "iso", "chd", "pbp"),
    "ps3" to setOf("iso", "pkg"),
    "ps4" to setOf("pkg"),
    "psp" to setOf("iso", "cso"),
    "switch" to setOf("nsp", "xci", "nca"),
    "switch2" to setOf("nup"),
    "wiiu" to setOf("wud", "wux")
)

/** Archive extensions, platform-agnostic. */
val ARCHIVE_EXTENSIONS_SET: Set<String> = setOf("zip", "7z", "rar")

/** Every game extension in the dictionary, across all platforms. */
val ALL_GAME_EXTENSIONS: Set<String> =
    GAME_FILE_EXTENSIONS.values.flatten().toSet()

/**
 * The game extensions for a platform id. A null, blank, or "other"
 * platform — or an id the dictionary does not know — accepts any
 * dictionary extension (the union across platforms): a port whose
 * platform is unknown must not lose its files to a narrow guess.
 */
fun extensionsForPlatform(platformId: String?): Set<String> {
    val id = platformId?.trim()?.lowercase()
    if (id.isNullOrEmpty() || id == "other") return ALL_GAME_EXTENSIONS
    return GAME_FILE_EXTENSIONS[id] ?: ALL_GAME_EXTENSIONS
}

/**
 * The extension (no dot, lowercase) of a file name or a URL, or null
 * when there is none. For a URL only the path counts — the host
 * ("….com" in "https://www.myabandonware.com/") is not a file
 * extension. Trailing slashes, query strings, and fragments are
 * stripped first, so a URL ending in ".zip/" still yields "zip". An
 * extension must look like one (letters and digits only, at most 10
 * characters, at least one letter): a name like "New Super Mario
 * Bros. Wii (Wii) (USA)" has a dot in "Bros." but no extension — the
 * text after it is not one — "2.0" in "eXoWin3 2.0" is a version
 * number, not an extension, and a name with no dot, a trailing dot,
 * or a leading dot and nothing else (".z64") has none either.
 */
fun extensionOf(text: String): String? {
    var s = text.trim()
    if (s.isEmpty()) return null
    s = s.substringBefore('?').substringBefore('#')
    // A URL's host is not a file location: strip scheme://host so
    // only the path is read. A URL with no path has no extension.
    val schemeSep = s.indexOf("://")
    if (schemeSep >= 0) {
        val pathStart = s.indexOf('/', schemeSep + 3)
        if (pathStart < 0) return null
        s = s.substring(pathStart)
    }
    while (s.endsWith("/")) s = s.dropLast(1)
    if (s.isEmpty()) return null
    val segment = s.substringAfterLast('/')
    val dot = segment.lastIndexOf('.')
    if (dot <= 0 || dot == segment.length - 1) return null
    val ext = segment.substring(dot + 1).lowercase()
    // A real extension is short, letters and digits only, and holds
    // at least one letter — "2.0" in "eXoWin3 2.0" is a version
    // number, not an extension.
    if (ext.length > 10 || ext.none { it.isLetter() } ||
        ext.any { !it.isLetterOrDigit() }
    ) return null
    return ext
}

/** True for an archive extension (zip, 7z, rar), any case. */
fun isArchiveExtension(ext: String): Boolean =
    ext.lowercase() in ARCHIVE_EXTENSIONS_SET

/**
 * True when an extension (no dot, any case) is a game file for the
 * platform: an archive, or one of the platform's game extensions
 * (the union when the platform is unknown, "other", or unlisted).
 * A movie (.mkv, .mp4), a document (.txt, .html), or a collection
 * index (.org) is never a game extension for any platform.
 */
fun isGameFileExtension(ext: String, platformId: String?): Boolean {
    val e = ext.lowercase()
    if (isArchiveExtension(e)) return true
    return e in extensionsForPlatform(platformId)
}

/**
 * True when a file is a game file for the platform. The extension
 * may come from the file name or the URL — either side may carry it,
 * and either side saying "game file" is enough (a remote entry's
 * name is often extension-less while its URL ends in ".zip/", and a
 * name like "New Super Mario Bros. Wii (Wii) (USA)" has no extension
 * at all even though it contains a dot). A file with no extension on
 * either side is never a game file — it cannot match any port, in
 * any source.
 */
fun isGameFile(
    fileName: String,
    platformId: String?,
    fileUrl: String? = null
): Boolean {
    val exts = listOfNotNull(
        extensionOf(fileName),
        fileUrl?.let { extensionOf(it) }
    )
    return exts.any { isGameFileExtension(it, platformId) }
}

/**
 * True when a remote-folder entry belongs in the browser: folders are
 * always kept (they are navigation), magnet entries are always kept
 * (they are copyable links, not files — the v1.6.1 game-file rule
 * does not apply to them), and a file is kept only when it is a game
 * file — the union of all platforms' extensions plus archives, from
 * its name or URL. A Redump/TOSEC index, a collection page, or a
 * firmware link has no game extension in either place and is hidden.
 */
fun isGameFileRemoteEntry(entry: RemoteEntry): Boolean =
    entry.isFolder || entry.isMagnet ||
        isGameFile(entry.name, platformId = null, fileUrl = entry.url)

/** Keep only the entries the remote folder browser shows. */
fun filterRemoteEntriesToGameFiles(entries: List<RemoteEntry>): List<RemoteEntry> =
    entries.filter { isGameFileRemoteEntry(it) }

// ---- Filename platform tags (v1.8.1) ----

/**
 * Filename platform tags (v1.8.1): the parenthesized system
 * abbreviations that appear in r-roms / Redump style file names —
 * "(Wii U)", "(Switch)", "(NDS)", "(GB)", "(GC)" — mapped to the
 * platform ids of the Recompa catalog (verified 2026-11-16 against
 * the live index: 29 platforms) and this dictionary. Keys are
 * normalized (lowercase, spaces and hyphens removed) so "Wii U",
 * "WiiU" and "WII-U" all map the same way, and full platform names
 * map too ("Nintendo DS" -> ds, "Master System" -> mastersystem,
 * "Mega Drive" -> genesis, "PS Vita" -> psvita).
 *
 * Only tags verified against a catalog platform id are listed:
 * "saturn" has no catalog platform id (the live index lists 29
 * platforms and none is saturn), so "(SAT)" is unmapped and never
 * blocks. Region/format tags ("USA", "EUR", "PAL", "NTSC", ...) are
 * not platform tags and are not in this map — they are skipped, as
 * are language/disc tags ("(En,Fr)", "(Disc 1)") and bracketed
 * title IDs ("[0400...]").
 */
val PLATFORM_FILENAME_TAGS: Map<String, String> = mapOf(
    "32x" to "32x",
    "3ds" to "3ds",
    "nintendo3ds" to "3ds",
    "amiga" to "amiga",
    "dos" to "dos",
    "dreamcast" to "dreamcast",
    "dc" to "dreamcast",
    "ds" to "ds",
    "nds" to "ds",
    "nintendods" to "ds",
    "gamecube" to "gamecube",
    "gc" to "gamecube",
    "ngc" to "gamecube",
    "gb" to "gb",
    "gameboy" to "gb",
    "gba" to "gba",
    "gameboyadvance" to "gba",
    "gbc" to "gbc",
    "gameboycolor" to "gbc",
    "genesis" to "genesis",
    "megadrive" to "genesis",
    "gen" to "genesis",
    "mastersystem" to "mastersystem",
    "sms" to "mastersystem",
    "n64" to "n64",
    "nintendo64" to "n64",
    "nes" to "nes",
    "pc" to "pc",
    "ps1" to "ps1",
    "ps" to "ps1",
    "psx" to "ps1",
    "playstation" to "ps1",
    "ps2" to "ps2",
    "playstation2" to "ps2",
    "ps3" to "ps3",
    "playstation3" to "ps3",
    "ps4" to "ps4",
    "playstation4" to "ps4",
    "psp" to "psp",
    "playstationportable" to "psp",
    "psvita" to "psvita",
    "psv" to "psvita",
    "vita" to "psvita",
    "snes" to "snes",
    "supernintendo" to "snes",
    "switch" to "switch",
    "switch2" to "switch2",
    "wii" to "wii",
    "wiiu" to "wiiu",
    "xbox" to "xbox",
    "xbox360" to "xbox360",
    "x360" to "xbox360"
)

/**
 * The platform id a file name claims, from a parenthesized system
 * tag — "(Wii U)", "(Switch)", "(NDS)", "(GB)" — or null when the
 * name carries no recognizable platform tag. Every parenthesized
 * group is tried in order and the first one that maps counts, so a
 * region tag before the platform tag ("(USA) (Wii U)") does not hide
 * it, and region/format tags are simply skipped. A tag is normalized
 * (lowercase, spaces and hyphens removed) before the lookup.
 * Bracketed groups ("[0400...]") are not platform tags and are
 * never read.
 *
 * Parenthesized groups that appear in the game's own title are
 * excluded ([gameTitle]): a title can itself name a platform —
 * "James Pond 2: Robocod (Amiga)" is an N64 port of an Amiga game,
 * "Tyrian (DOS)" a DOS port — and a file named after the game
 * inherits that group. It is the title talking, not the file, so it
 * must neither veto a true positive nor hide a real file tag that
 * follows it ("Tyrian (DOS) (Wii U)" is a Wii U file, and the
 * "(Wii U)" after the title's "(DOS)" is the tag that says so).
 */
fun platformTagFromFileName(fileName: String, gameTitle: String? = null): String? {
    val titleGroups = gameTitle?.let { title ->
        PAREN_GROUP_REGEX.findAll(title)
            .map { it.groupValues[1].lowercase().replace(WHITESPACE_HYPHEN_REGEX, "") }
            .toSet()
    }.orEmpty()
    for (match in PAREN_GROUP_REGEX.findAll(fileName)) {
        val key = match.groupValues[1].lowercase()
            .replace(WHITESPACE_HYPHEN_REGEX, "")
        if (key in titleGroups) continue
        val platform = PLATFORM_FILENAME_TAGS[key]
        if (platform != null) return platform
    }
    return null
}

/**
 * The platform id a URL claims, from a parenthesized system tag in
 * its last path segment (percent-decoded), or null when there is
 * none. Only used when the file name itself carries no tag — a
 * remote entry's name is often extension-less while its URL carries
 * the full file name. The host is never read (a URL with no path
 * has no tag). The game's title groups are excluded the same way
 * as in [platformTagFromFileName].
 */
fun platformTagFromUrl(url: String, gameTitle: String? = null): String? {
    val segment = urlDecodedLastSegment(url)
    return if (segment.isEmpty()) null else platformTagFromFileName(segment, gameTitle)
}

/** Percent-decode (%XX) a URL path segment; "+" stays a literal plus. */
private fun urlDecodedLastSegment(url: String): String {
    var s = url.substringBefore('?').substringBefore('#')
    val schemeSep = s.indexOf("://")
    if (schemeSep >= 0) {
        val pathStart = s.indexOf('/', schemeSep + 3)
        if (pathStart < 0) return ""
        s = s.substring(pathStart)
    }
    while (s.endsWith("/")) s = s.dropLast(1)
    if (s.isEmpty()) return ""
    val segment = s.substringAfterLast('/')
    val out = StringBuilder(segment.length)
    var i = 0
    while (i < segment.length) {
        val c = segment[i]
        if (c == '%' && i + 2 < segment.length) {
            val v = segment.substring(i + 1, i + 3).toIntOrNull(16)
            if (v != null) {
                out.append(v.toChar())
                i += 3
                continue
            }
        }
        out.append(c)
        i++
    }
    return out.toString()
}

/**
 * True when a file's platform tag (if it has one) agrees with the
 * port's platform (v1.8.1). A file whose name carries a platform tag
 * for a different platform than the game's must never match — a Wii
 * U release is not the Switch port's file, however identical the
 * titles are. The check does not apply when the file has no
 * recognizable platform tag (the tag is read from the file name,
 * falling back to the URL's last path segment when the name has
 * none), or when the port's platform is unknown, blank, or "other"
 * — then the existing rules decide. Region tags are not platform
 * tags and never block. Parenthesized groups in the game's own
 * title are excluded (see [platformTagFromFileName]) — they are
 * the title talking, not the file.
 */
fun isPlatformConsistent(
    fileName: String,
    platformId: String?,
    gameTitle: String? = null,
    fileUrl: String? = null
): Boolean {
    val portPlatform = platformId?.trim()?.lowercase()
    if (portPlatform.isNullOrEmpty() || portPlatform == "other") return true
    val tag = platformTagFromFileName(fileName, gameTitle)
        ?: fileUrl?.let { platformTagFromUrl(it, gameTitle) }
    if (tag == null) return true
    return tag == portPlatform
}
