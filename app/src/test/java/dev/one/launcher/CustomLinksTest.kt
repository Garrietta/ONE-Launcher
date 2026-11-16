package dev.one.launcher

import dev.one.launcher.data.normalizeCustomLinkUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CustomLinksTest {

    @Test
    fun acceptsValidHttps() {
        assertEquals(
            "https://example.com/archive",
            normalizeCustomLinkUrl("https://example.com/archive")
        )
    }

    @Test
    fun acceptsHttpAndLocalHost() {
        assertEquals(
            "http://192.168.1.5:8080/files",
            normalizeCustomLinkUrl("http://192.168.1.5:8080/files")
        )
    }

    @Test
    fun missingSchemeGetsHttps() {
        assertEquals("https://example.com", normalizeCustomLinkUrl("example.com"))
        assertEquals(
            "https://example.com/files",
            normalizeCustomLinkUrl("  example.com/files  ")
        )
    }

    @Test
    fun hostWithPortAndNoSchemeGetsHttps() {
        assertEquals(
            "https://example.com:8080/files",
            normalizeCustomLinkUrl("example.com:8080/files")
        )
    }

    @Test
    fun rejectsFtpAndJavascript() {
        assertNull(normalizeCustomLinkUrl("ftp://example.com/files"))
        assertNull(normalizeCustomLinkUrl("javascript:alert(1)"))
        assertNull(normalizeCustomLinkUrl("mailto:foo@example.com"))
    }

    @Test
    fun rejectsBlank() {
        assertNull(normalizeCustomLinkUrl(""))
        assertNull(normalizeCustomLinkUrl("   "))
    }

    @Test
    fun rejectsSchemeWithoutHost() {
        assertNull(normalizeCustomLinkUrl("https://"))
        assertNull(normalizeCustomLinkUrl("https://   "))
    }
}
