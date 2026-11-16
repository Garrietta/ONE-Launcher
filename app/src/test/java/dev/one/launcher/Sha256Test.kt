package dev.one.launcher

import dev.one.launcher.data.sha256Hex
import dev.one.launcher.data.sha256Matches
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class Sha256Test {

    @Test
    fun hashesKnownContent() {
        val file = Files.createTempFile("one-sha-test", ".bin").toFile()
        file.writeText("abc")
        // SHA-256("abc"), a published test vector.
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256Hex(file)
        )
    }

    @Test
    fun matchesCorrectHashAndRejectsWrongHash() {
        val file = Files.createTempFile("one-sha-test", ".bin").toFile()
        file.writeText("abc")
        val good = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        assertTrue(sha256Matches(file, good))
        assertTrue(sha256Matches(file, good.uppercase()))
        assertFalse(sha256Matches(file, "00" + good.substring(2)))
        assertFalse(sha256Matches(file, ""))
        assertFalse(sha256Matches(file, "not-a-hash"))
    }
}
