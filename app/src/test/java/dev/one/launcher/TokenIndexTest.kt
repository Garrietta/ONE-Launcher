package dev.one.launcher

import dev.one.launcher.data.PortIndex
import dev.one.launcher.data.PortMatcher
import dev.one.launcher.data.RemoteEntry
import dev.one.launcher.data.withDistinctiveHints
import dev.one.launcher.model.Port
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Token Index (v1.8.7): the pairing no longer scans all 536 ports
 * for every one of the 26,710 remote entries (~14M matchKind calls).
 * A [PortIndex] maps each word token to the ports that could match
 * it, so each entry is only tested against candidate ports that
 * share at least one word token (plus the prefix rule for
 * single-word hints of 5+ letters). Every candidate is still
 * verified by the UNCHANGED [matchKind], so results are identical
 * to the old full scan. These tests prove that identity on a
 * representative fixture set, and that the candidate set is much
 * smaller than the full catalog.
 */
class TokenIndexTest {

    private fun port(
        id: String,
        game: String,
        platformId: String,
        hints: List<String>,
        extensions: List<String> = emptyList()
    ) = Port(
        id = id,
        title = game,
        game = game,
        system = platformId,
        type = "recomp",
        repo = "example/$id",
        releasesUrl = "https://github.com/example/$id/releases",
        requiredDump = "",
        platformId = platformId,
        platformName = platformId,
        fileNameHints = hints,
        fileExtensions = extensions,
        archiveOk = true
    )

    private fun catalog(): List<Port> = withDistinctiveHints(
        listOf(
            port("soh", "The Legend of Zelda: Ocarina of Time", "n64",
                listOf("ocarina", "time", "zelda", "soh"), listOf("z64")),
            port("eidos", "Pokémon Black & White", "ds",
                listOf("pokemon", "black", "white", "eidos")),
            port("pokopia", "Pokémon Pokopia", "switch2",
                listOf("pokemon", "pokopia")),
            port("starfox", "Star Fox 64", "n64",
                listOf("star", "fox", "starfox", "starship"), listOf("z64")),
            port("versus", ".hack//versus", "ps3",
                listOf("hack", "versus")),
            port("nightfire", "007: Nightfire and 007: Agent Under Fire", "xbox",
                listOf("007", "nightfire", "agent", "fire", "under")),
            port("botw", "The Legend of Zelda: Breath of the Wild", "switch",
                listOf("breath", "wild", "zelda", "botw"), listOf("nsp", "xci"))
        )
    )

    // A realistic entry: the name is the game file's display name and
    // the URL is where it lives. The game-file gate reads the extension
    // from the name OR the URL — a name with no extension (the real
    // "Pokemon Black (NDS) (USA)" from the feed) still passes because
    // its URL ends in .zip, like the real archive.org link.
    private fun entry(name: String, url: String? = null): RemoteEntry {
        val hasExt = name.substringAfterLast('.', "").isNotEmpty() &&
            name.substringAfterLast('/').contains('.')
        val resolvedUrl = url ?: "https://example.com/" +
            name.replace(' ', '_') + if (hasExt) "" else ".zip"
        return RemoteEntry(name = name, url = resolvedUrl, isFolder = false)
    }

    /** Brute force: every port, the old full scan. */
    private fun bruteForce(
        entries: List<RemoteEntry>,
        ports: List<Port>
    ): Map<String, List<String>> {
        val out = mutableMapOf<String, MutableList<String>>()
        for (e in entries) {
            for (p in ports) {
                if (PortMatcher.matchKind(e.name, p, e.url) != null) {
                    out.getOrPut(e.name) { mutableListOf() }.add(p.id)
                }
            }
        }
        return out
    }

    /** Indexed: only candidate ports per entry, same matchKind. */
    private fun indexed(
        entries: List<RemoteEntry>,
        ports: List<Port>
    ): Map<String, List<String>> {
        val index = PortIndex(ports)
        val out = mutableMapOf<String, MutableList<String>>()
        for (e in entries) {
            for (p in index.candidatesFor(e.name)) {
                if (PortMatcher.matchKind(e.name, p, e.url) != null) {
                    out.getOrPut(e.name) { mutableListOf() }.add(p.id)
                }
            }
        }
        return out
    }

    @Test
    fun `index produces identical results to the full scan`() {
        val ports = catalog()
        val entries = listOf(
            entry("The Legend of Zelda - Ocarina of Time (USA).z64"),
            entry("Pokemon Black (NDS) (USA)"),
            entry("Pokemon Black 2 (NDS) (USA)"),
            entry("Pokémon Pokopia [0400A3A01EE4C000][v655360].nup"),
            entry("Star Fox 64 (USA).z64"),
            entry("10101 - Will the Starship (USA).zip"),
            entry(".hack//Versus (USA).iso"),
            // A real feed entry: "RoboCop versus The Terminator" shares
            // only the word "versus" with .hack//versus, so its title
            // similarity is 0.5 and it must not match. (A single-word
            // "versus (USA).iso" would match — similarity 1.0 — but no
            // such file exists in the feed; the real versus files are
            // all multi-word titles like this one.)
            entry("RoboCop versus The Terminator (USA).zip"),
            entry("007 - Agent Under Fire (USA).zip"),
            entry("007 - Nightfire (USA).zip"),
            entry("007 - NightFire (USA, Europe) (En,Fr,De).zip"),
            entry("The Legend of Zelda - Breath of the Wild (Wii U) (USA).7z"),
            entry("The Legend of Zelda - Breath of the Wild (USA).nsp"),
            entry("The Last Airbender (2010).mkv"),
            entry("Some Random Game (USA).iso"),
            entry("Mario Kart Double Dash (GC) (USA)"),
            entry("Pokemon Gold (GBC) (USA)")
        )
        val expected = bruteForce(entries, ports)
        val actual = indexed(entries, ports)
        assertEquals(
            "indexed pairing must match the full scan exactly",
            expected, actual
        )
        // Spot-check the true positives the full catalog sweep guards.
        assertEquals(listOf("eidos"), expected["Pokemon Black (NDS) (USA)"])
        assertEquals(listOf("pokopia"), expected["Pokémon Pokopia [0400A3A01EE4C000][v655360].nup"])
        assertEquals(listOf("soh"), expected["The Legend of Zelda - Ocarina of Time (USA).z64"])
        // The known false positives stay blocked.
        assertEquals(null, expected["10101 - Will the Starship (USA).zip"])
        assertEquals(null, expected["RoboCop versus The Terminator (USA).zip"])
        assertEquals(null, expected["The Legend of Zelda - Breath of the Wild (Wii U) (USA).7z"])
        assertEquals(null, expected["The Last Airbender (2010).mkv"])
        assertEquals(null, expected["Mario Kart Double Dash (GC) (USA)"])
        assertEquals(null, expected["Pokemon Gold (GBC) (USA)"])
    }

    @Test
    fun `candidate set is much smaller than the full catalog`() {
        val ports = catalog()
        val index = PortIndex(ports)
        // A file about one game should only test a handful of ports,
        // not all 7 — and never zero when a match exists.
        val candidates = index.candidatesFor("Pokemon Black (NDS) (USA)")
        assertTrue(candidates.any { it.id == "eidos" })
        assertTrue("candidates=${candidates.size}, ports=${ports.size}",
            candidates.size < ports.size)
        // An unrelated file tests few or no ports.
        val unrelated = index.candidatesFor("Completely Unrelated Title (USA).iso")
        assertTrue(unrelated.size <= 2)
    }

    @Test
    fun `prefix rule is covered by the index`() {
        // "starfox64" contains the hint "starfox" as a prefix — the
        // index must surface the Star Fox port even though no word
        // equals "starfox" exactly.
        val ports = catalog()
        val index = PortIndex(ports)
        val candidates = index.candidatesFor("StarFox64 (USA).z64")
        assertTrue(candidates.any { it.id == "starfox" })
        val matched = candidates.mapNotNull { p ->
            PortMatcher.matchKind("StarFox64 (USA).z64", p, null)?.let { p.id }
        }
        assertEquals(listOf("starfox"), matched)
    }
}
