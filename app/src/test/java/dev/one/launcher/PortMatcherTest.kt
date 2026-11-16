package dev.one.launcher

import dev.one.launcher.data.MatchKind
import dev.one.launcher.data.PortMatcher
import dev.one.launcher.model.Port
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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

    // ---- Archive matching (v1.1) ----

    @Test
    fun zipNamedLikeGarrettsOotFileMatchesSohAsProvisionalArchive() {
        val kind = PortMatcher.matchKind(
            "Legend of Zelda, The - Ocarina of Time (USA).zip", oot
        )
        assertEquals(MatchKind.ARCHIVE_PROVISIONAL, kind)
        assertTrue(
            PortMatcher.matchingPorts(
                "Legend of Zelda, The - Ocarina of Time (USA).zip", listOf(oot)
            ).size == 1
        )
    }

    @Test
    fun zipWithNoHintDoesNotMatch() {
        assertNull(PortMatcher.matchKind("mario-kart.zip", oot))
        assertTrue(
            PortMatcher.matchingPorts("mario-kart.zip", listOf(oot)).isEmpty()
        )
    }

    @Test
    fun sevenZipAndRarAreProvisionalOnly() {
        assertEquals(
            MatchKind.ARCHIVE_PROVISIONAL,
            PortMatcher.matchKind("Ocarina of Time.7z", oot)
        )
        assertEquals(
            MatchKind.ARCHIVE_PROVISIONAL,
            PortMatcher.matchKind("Ocarina of Time.rar", oot)
        )
    }

    @Test
    fun directRomStillMatchesDirectly() {
        assertEquals(MatchKind.DIRECT, PortMatcher.matchKind("ZELDA OOT.z64", oot))
    }

    @Test
    fun unrelatedZipDoesNotMatch() {
        assertNull(PortMatcher.matchKind("Star Fox 64.zip", oot))
    }

    @Test
    fun zipContainingMatchingRomIsConfirmed() {
        val zip = zipWithEntries("Ocarina of Time.z64" to "fake-rom-bytes")
        assertEquals(
            "Ocarina of Time.z64",
            PortMatcher.inspectZip(zip, oot)
        )
    }

    @Test
    fun zipWithNoMatchingEntryStaysProvisional() {
        val zip = zipWithEntries("readme.txt" to "hello", "mario.z64" to "nope")
        assertNull(PortMatcher.inspectZip(zip, oot))
        // Name alone still matches provisionally; contents do not confirm it.
        assertEquals(
            MatchKind.ARCHIVE_PROVISIONAL,
            PortMatcher.matchKind("Ocarina of Time (USA).zip", oot)
        )
    }

    @Test
    fun extractPullsOnlyTheMatchingEntry() {
        val zip = zipWithEntries(
            "Ocarina of Time.z64" to "rom-bytes",
            "readme.txt" to "do-not-extract-me"
        )
        val dest = Files.createTempDirectory("one-extract-test").toFile()
        val extracted = PortMatcher.extractZipEntry(
            zip, "Ocarina of Time.z64", dest
        )
        assertNotNull(extracted)
        assertEquals("Ocarina of Time.z64", extracted!!.name)
        assertEquals("rom-bytes", extracted.readText())
        assertFalse(File(dest, "readme.txt").exists())
    }

    @Test
    fun extractMissingEntryReturnsNull() {
        val zip = zipWithEntries("Ocarina of Time.z64" to "rom-bytes")
        val dest = Files.createTempDirectory("one-extract-missing").toFile()
        assertNull(PortMatcher.extractZipEntry(zip, "nope.z64", dest))
    }

    private fun zipWithEntries(vararg entries: Pair<String, String>): File {
        val file = Files.createTempFile("one-test", ".zip").toFile()
        ZipOutputStream(file.outputStream()).use { out ->
            for ((name, content) in entries) {
                out.putNextEntry(ZipEntry(name))
                out.write(content.toByteArray())
                out.closeEntry()
            }
        }
        return file
    }
}
