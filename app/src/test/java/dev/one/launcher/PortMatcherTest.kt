package dev.one.launcher

import dev.one.launcher.data.PortMatcher
import dev.one.launcher.model.Port
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PortMatcherTest {

    private val oot = Port(
        id = "soh",
        title = "Ship of Harkinian",
        game = "The Legend of Zelda: Ocarina of Time",
        system = "Nintendo 64",
        type = "Decompilation",
        repo = "linkzenic/Shipwright-Ports",
        releasesUrl = "https://github.com/linkzenic/Shipwright-Ports/releases",
        packageNames = listOf("com.linkzenic.soh"),
        fileExtensions = listOf(".z64", ".n64", ".v64"),
        fileNameHints = listOf("zelootd", "oot", "ocarina"),
        requiredDump = "Your own Ocarina of Time ROM (.z64)."
    )

    @Test
    fun matchesOotRom() {
        assertTrue(PortMatcher.matches("ZELDA OOT.z64", oot))
    }

    @Test
    fun matchesCaseInsensitiveHint() {
        assertTrue(PortMatcher.matches("OcarinaOfTime.n64", oot))
    }

    @Test
    fun rejectsWrongExtension() {
        assertFalse(PortMatcher.matches("oot.sfc", oot))
    }

    @Test
    fun rejectsUnrelatedName() {
        assertFalse(PortMatcher.matches("mario.z64", oot))
    }

    @Test
    fun matchingPortsFindsCandidate() {
        val ports = listOf(oot)
        assertTrue(PortMatcher.matchingPorts("zelootd.z64", ports).size == 1)
        assertTrue(PortMatcher.matchingPorts("mario.z64", ports).isEmpty())
    }
}
