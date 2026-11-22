package dev.one.launcher.data

/**
 * The front of a retail game box, in millimetres (v1.9.4).
 *
 * The Library's shelf stands every game at its real box's height relative
 * to the others and opens its cover at the box's real shape: a 3DS case is
 * shorter than a PlayStation 2 case, a Switch case is as tall as a PS4
 * case but narrower, and Nintendo 64 and Super Nintendo boxes are wider
 * than they are tall, as they are on a real shelf.
 */
data class BoxSize(val width: Float, val height: Float) {
    val aspect: Float get() = width / height
}

/**
 * The box each platform's games are sold in. Sizes are the common North
 * American retail boxes, rounded to the millimetre; a platform with no
 * box of its own (arcade, an unknown name) gets a DVD case.
 */
object PlatformBox {

    /** DVD keep case: PS2, Xbox, Xbox 360, Wii, GameCube, boxed PC games. */
    val DVD = BoxSize(135f, 190f)

    /** The shorter Blu-ray style case: PS3, PS4, PS5, Wii U, Xbox One, Xbox Series. */
    val BLU_RAY = BoxSize(135f, 170f)

    /** Switch and Switch 2: as tall as a Blu-ray case, much narrower. */
    val SWITCH = BoxSize(105f, 170f)

    /** DS and 3DS: nearly square, the shortest plastic case. */
    val DS = BoxSize(125f, 137f)

    val VITA = BoxSize(105f, 135f)

    /** PSP's UMD case: tall and narrow. */
    val PSP = BoxSize(104f, 180f)

    /** CD jewel case, wider than tall: PlayStation, Dreamcast, Sega CD. */
    val JEWEL = BoxSize(142f, 125f)

    /** Cardboard, landscape. */
    val N64 = BoxSize(178f, 127f)
    val SNES = BoxSize(185f, 133f)

    /** Cardboard, portrait. */
    val NES = BoxSize(127f, 184f)

    /** Small cardboard boxes. */
    val GAME_BOY = BoxSize(114f, 127f)
    val GBA = BoxSize(127f, 127f)

    /** Plastic clamshell: Genesis, Master System, 32X. */
    val CLAMSHELL = BoxSize(135f, 190f)

    /** The PC big box of the DOS and Amiga years, the tallest on the shelf. */
    val BIG_BOX = BoxSize(190f, 235f)

    private val BY_ID: Map<String, BoxSize> = mapOf(
        "ps1" to JEWEL,
        "ps2" to DVD,
        "ps3" to BLU_RAY,
        "ps4" to BLU_RAY,
        "psp" to PSP,
        "psvita" to VITA,
        "xbox" to DVD,
        "xbox360" to DVD,
        "wii" to DVD,
        "gamecube" to DVD,
        "wiiu" to BLU_RAY,
        "switch" to SWITCH,
        "switch2" to SWITCH,
        "ds" to DS,
        "3ds" to DS,
        "n64" to N64,
        "snes" to SNES,
        "nes" to NES,
        "gb" to GAME_BOY,
        "gbc" to GAME_BOY,
        "gba" to GBA,
        "dreamcast" to JEWEL,
        "genesis" to CLAMSHELL,
        "mastersystem" to CLAMSHELL,
        "32x" to CLAMSHELL
    )

    /** The box for a platform display name ("Nintendo 3DS", "PlayStation 2", "DOS" ...). */
    fun forPlatform(platform: String?): BoxSize {
        if (platform.isNullOrBlank()) return DVD
        val lower = platform.lowercase()
        // Names the catalog ids do not cover, checked first so "PlayStation 5"
        // is not read as "PlayStation" and "Xbox One" not as "Xbox".
        when {
            "playstation 5" in lower || lower == "ps5" -> return BLU_RAY
            "xbox one" in lower || "xbox series" in lower -> return BLU_RAY
            lower == "dos" || "ms-dos" in lower || "amiga" in lower -> return BIG_BOX
            "sega cd" in lower || "mega cd" in lower -> return JEWEL
            "genesis" in lower -> return CLAMSHELL
        }
        CategoryPlatform.platformIdForCategory(platform)?.let { id -> BY_ID[id]?.let { return it } }
        return DVD
    }
}
