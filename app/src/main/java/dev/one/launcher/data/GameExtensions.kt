package dev.one.launcher.data

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
