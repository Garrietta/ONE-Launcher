package dev.one.launcher.data

/**
 * Maps a remote feed's category string to a catalog platform id, so
 * magnet dedup can prefer the entry whose category platform matches
 * the game's platform (v1.8.5).
 *
 * A feed category names the system its entries belong to — the
 * Minerva system name ("Nintendo - Wii - NKit RVZ [zstd-19-128k]",
 * "Sony - PlayStation 2", "Microsoft - Xbox") or an r-roms category
 * ("Nintendo - GameCube - NKit RVZ [zstd-19-128k]"). The same game
 * name appears once per system because each system's entries are a
 * separate torrent, so a game page shows the same name two or three
 * times — once per system — unless the duplicates are collapsed.
 * Collapsing keeps the entry whose category platform matches the
 * game's platform (the right file for that port); when none of the
 * duplicates matches the game's platform, or the category is
 * unmapped, the first entry wins.
 *
 * The mapping is a static longest-phrase-first list, matched as a
 * word-phrase against the normalized category string (lowercase,
 * diacritic-folded). Longer phrases come first so "nintendo ds"
 * beats "ds", "playstation 2" beats "playstation", and "wii u"
 * beats "wii" — a naive substring search would match the shorter
 * phrase inside the longer one. Platforms with no catalog id
 * (Sega - Saturn, Sega - Mega CD & Sega CD) map to null: no port
 * exists for them, so no preference is needed and the dedup just
 * collapses to the first entry.
 *
 * Derived from the live Recompa platforms list (29 ids) and the
 * live remote.json categories (57). Unmapped categories degrade
 * gracefully to "first" — the dedup never drops a name because its
 * category is unknown.
 *
 * Pure functions — unit-tested.
 */
object CategoryPlatform {

    /** One phrase → one catalog platform id, longest phrase first. */
    private val PHRASE_TO_PLATFORM: List<Pair<String, String>> = listOf(
        // Multi-word platform names first — "nintendo ds" must beat
        // "ds", "playstation 2" must beat "playstation", "wii u"
        // must beat "wii". "super nintendo" comes before "nintendo
        // entertainment system": the category "Nintendo - Super
        // Nintendo Entertainment System" contains "Nintendo
        // Entertainment System" as a word-phrase (after "super "),
        // so the NES phrase would win if it were checked first.
        "super nintendo" to "snes",
        "nintendo entertainment system" to "nes",
        "nintendo ds" to "ds",
        "nintendo 3ds" to "3ds",
        "nintendo 64" to "n64",
        "game boy advance" to "gba",
        "game boy color" to "gbc",
        "game boy" to "gb",
        "gamecube" to "gamecube",
        "playstation portable" to "psp",
        "playstation vita" to "psvita",
        "ps vita" to "psvita",
        "playstation 4" to "ps4",
        "playstation 3" to "ps3",
        "playstation 2" to "ps2",
        "playstation" to "ps1",
        "xbox 360" to "xbox360",
        "xbox" to "xbox",
        "dreamcast" to "dreamcast",
        "mega drive" to "genesis",
        "master system" to "mastersystem",
        "switch 2" to "switch2",
        "switch" to "switch",
        "wii u" to "wiiu",
        "wii" to "wii",
        // Single tokens, matched as whole words in the normalized
        // category (a bare "32x" never appears inside a longer
        // word in these categories).
        "32x" to "32x",
        "n64" to "n64",
        "snes" to "snes",
        "nes" to "nes",
        "gba" to "gba",
        "gbc" to "gbc",
        "ps3" to "ps3",
        "ps2" to "ps2",
        "psp" to "psp",
        "ps4" to "ps4",
        "ds" to "ds",
        "3ds" to "3ds"
    )

    /**
     * The catalog platform id for a feed category, or null when the
     * category names no catalog platform (Sega - Saturn, Sega - Mega
     * CD & Sega CD, an unmapped or absent category). The phrase is
     * matched as a whole word-phrase against the normalized category
     * string — "wii" matches "Nintendo - Wii - NKit RVZ" but never
     * "wii u", because "wii u" is listed first and "wii" is only
     * matched on a word boundary (so it cannot match the "wii" in
     * "wii u" either).
     */
    fun platformIdForCategory(category: String?): String? {
        if (category.isNullOrBlank()) return null
        val normalized = " " + normalizeForTokens(category) + " "
        for ((phrase, platformId) in PHRASE_TO_PLATFORM) {
            if (normalized.contains(" $phrase ")) return platformId
        }
        return null
    }
}
