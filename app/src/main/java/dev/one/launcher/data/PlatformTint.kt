package dev.one.launcher.data

/**
 * The background tint for a platform (Marquee redesign, v1.9.0).
 *
 * The Library and game pages wash the whole screen in one deep color
 * per console — Switch red, N64 green, PlayStation blue — so the
 * screen itself tells you which shelf you are on. The platform name
 * goes through [CategoryPlatform.platformIdForCategory] (it already
 * maps display names like "Nintendo 64" or "Super Nintendo" to catalog
 * ids), then through a fixed table. Unknown platforms get a stable
 * hue derived from the name, kept dark enough for white text.
 *
 * Returns ARGB as a Long so this stays a pure, JVM-testable function.
 */
object PlatformTint {

    /** The neutral tint for "All games" and for an empty platform. */
    const val NEUTRAL: Long = 0xFF3A3632

    private val BY_ID: Map<String, Long> = mapOf(
        "switch" to 0xFF8E0E16,
        "switch2" to 0xFF9A1020,
        "wiiu" to 0xFF0F5E7A,
        "wii" to 0xFF4E5866,
        "gamecube" to 0xFF3A2C78,
        "n64" to 0xFF1E5A34,
        "snes" to 0xFF4A3D86,
        "nes" to 0xFF7A1C1C,
        "gb" to 0xFF5A6B1E,
        "gbc" to 0xFF6A2A72,
        "gba" to 0xFF3E2A7A,
        "ds" to 0xFF4A4F57,
        "3ds" to 0xFF7E1426,
        "ps1" to 0xFF2E3442,
        "ps2" to 0xFF1D2B5C,
        "ps3" to 0xFF22262E,
        "ps4" to 0xFF123E7A,
        "psp" to 0xFF2A2A30,
        "psvita" to 0xFF1A3A6E,
        "xbox" to 0xFF1F5A1F,
        "xbox360" to 0xFF3E6A12,
        "dreamcast" to 0xFF9A4A10,
        "genesis" to 0xFF2A2A2A,
        "mastersystem" to 0xFF6A1414,
        "32x" to 0xFF5A1A1A
    )

    /** Tint for a platform display name ("Nintendo 64", "Switch", "PC" …). */
    fun forPlatform(platform: String?): Long {
        if (platform.isNullOrBlank()) return NEUTRAL
        CategoryPlatform.platformIdForCategory(platform)?.let { id ->
            BY_ID[id]?.let { return it }
        }
        val lower = platform.lowercase()
        if (lower == "pc" || "windows" in lower) return 0xFF33404E
        if ("android" in lower) return 0xFF2C5A3A
        return hashed(lower)
    }

    /**
     * A stable, dark, moderately saturated color from the name: hue from
     * the hash, saturation 0.55, value 0.42 — always dark enough for
     * white text on top.
     */
    private fun hashed(name: String): Long {
        val hue = ((name.hashCode().toLong() and 0x7fffffff) % 360).toFloat()
        return hsvToArgb(hue, 0.55f, 0.42f)
    }

    internal fun hsvToArgb(h: Float, s: Float, v: Float): Long {
        val c = v * s
        val x = c * (1 - kotlin.math.abs((h / 60f) % 2 - 1))
        val m = v - c
        val (r, g, b) = when {
            h < 60 -> Triple(c, x, 0f)
            h < 120 -> Triple(x, c, 0f)
            h < 180 -> Triple(0f, c, x)
            h < 240 -> Triple(0f, x, c)
            h < 300 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        fun ch(f: Float) = ((f + m) * 255f).toInt().coerceIn(0, 255).toLong()
        return (0xFFL shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }

    /** Relative luminance 0..1 (sRGB, approximate) — used by the tests. */
    internal fun luminance(argb: Long): Double {
        fun lin(c: Long): Double {
            val v = c / 255.0
            return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        }
        val r = lin((argb shr 16) and 0xFF)
        val g = lin((argb shr 8) and 0xFF)
        val b = lin(argb and 0xFF)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }
}
