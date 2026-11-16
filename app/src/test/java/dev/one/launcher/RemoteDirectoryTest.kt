package dev.one.launcher

import dev.one.launcher.data.REMOTE_FOLDER_ERROR
import dev.one.launcher.data.RemoteListingResult
import dev.one.launcher.data.buildRemoteRequest
import dev.one.launcher.data.normalizeRemoteFolderUrl
import dev.one.launcher.data.parseDirectoryListing
import dev.one.launcher.data.parsePlainFileList
import okhttp3.Credentials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteDirectoryTest {

    private val apacheListing = """
        <!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 3.2 Final//EN">
        <html><head><title>Index of /files</title></head><body>
        <h1>Index of /files</h1>
        <table>
        <tr><th><a href="?C=N;O=D">Name</a></th><th><a href="?C=M;O=A">Last modified</a></th></tr>
        <tr><th colspan="5"><hr></th></tr>
        <tr><td><img src="/icons/back.gif" alt="[PARENTDIR]"></td><td><a href="/">Parent Directory</a></td></tr>
        <tr><td><img src="/icons/folder.gif" alt="[DIR]"></td><td><a href="snes/">snes/</a></td></tr>
        <tr><td><img src="/icons/unknown.gif" alt="[   ]"></td><td><a href="game.zip">game.zip</a></td></tr>
        <tr><td><img src="/icons/unknown.gif" alt="[   ]"></td><td><a href="readme.txt">readme.txt</a></td></tr>
        </table>
        </body></html>
    """.trimIndent()

    private val nginxListing = """
        <html><head><title>Index of /roms/</title></head><body>
        <h1>Index of /roms/</h1><hr><pre><a href="../">../</a>
        <a href="n64/">n64/</a>                                              01-Jan-2026 00:00                   -
        <a href="zelda.z64">zelda.z64</a>                                        01-Jan-2026 00:00            33554432
        </pre><hr></body></html>
    """.trimIndent()

    @Test
    fun apacheListingParsesFoldersAndFiles() {
        val entries = parseDirectoryListing("http://192.168.1.10/files/", apacheListing)
        assertEquals(
            listOf(
                "snes" to true,
                "game.zip" to false,
                "readme.txt" to false
            ),
            entries.map { it.name to it.isFolder }
        )
        assertEquals("http://192.168.1.10/files/snes/", entries[0].url)
        assertEquals("http://192.168.1.10/files/game.zip", entries[1].url)
    }

    @Test
    fun nginxListingParsesFoldersAndFiles() {
        val entries = parseDirectoryListing("http://192.168.1.10/roms/", nginxListing)
        assertEquals(
            listOf("n64" to true, "zelda.z64" to false),
            entries.map { it.name to it.isFolder }
        )
        assertEquals("http://192.168.1.10/roms/n64/", entries[0].url)
        assertEquals("http://192.168.1.10/roms/zelda.z64", entries[1].url)
    }

    @Test
    fun listingSkipsSortHeadersParentAndOffHostLinks() {
        val html = """
            <html><body>
            <a href="?C=N;O=D">Name</a>
            <a href="../">../</a>
            <a href="/">Parent Directory</a>
            <a href="https://other.example.com/evil.zip">evil.zip</a>
            <a href="ok.zip">ok.zip</a>
            </body></html>
        """.trimIndent()
        val entries = parseDirectoryListing("http://192.168.1.10/files/", html)
        assertEquals(listOf("ok.zip"), entries.map { it.name })
    }

    @Test
    fun listingDedupesRepeatedLinks() {
        val html = """
            <html><body>
            <a href="game.zip"><img src="/icons/zip.gif"></a>
            <a href="game.zip">game.zip</a>
            </body></html>
        """.trimIndent()
        val entries = parseDirectoryListing("http://192.168.1.10/files/", html)
        assertEquals(1, entries.size)
        assertEquals("game.zip", entries[0].name)
    }

    @Test
    fun plainFileListParsesNameAndUrl() {
        val feed = """
            [
              {"name": "a.zip", "url": "http://192.168.1.10/a.zip", "size": 123},
              {"name": "b.bin", "url": "/b.bin"}
            ]
        """.trimIndent()
        val result = parsePlainFileList("http://192.168.1.10/list.json", feed)
        assertTrue(result is RemoteListingResult.Listing)
        val entries = (result as RemoteListingResult.Listing).entries
        assertEquals(
            listOf("a.zip" to "http://192.168.1.10/a.zip", "b.bin" to "http://192.168.1.10/b.bin"),
            entries.map { it.name to it.url }
        )
        assertTrue(entries.all { !it.isFolder })
    }

    @Test
    fun plainFileListReadsOptionalSize() {
        val feed = """
            [
              {"name": "a.zip", "url": "http://192.168.1.10/a.zip", "size": 123},
              {"name": "b.bin", "url": "/b.bin"},
              {"name": "c.bin", "url": "/c.bin", "size": "big"},
              {"name": "d.bin", "url": "/d.bin", "size": -5}
            ]
        """.trimIndent()
        val result = parsePlainFileList("http://192.168.1.10/list.json", feed)
        assertTrue(result is RemoteListingResult.Listing)
        val entries = (result as RemoteListingResult.Listing).entries
        assertEquals(123L, entries[0].sizeBytes)
        assertNull(entries[1].sizeBytes)
        assertNull(entries[2].sizeBytes)
        assertNull(entries[3].sizeBytes)
    }

    @Test
    fun plainFileListSkipsEntriesMissingNameOrUrl() {
        val feed = """
            [
              {"name": "good.zip", "url": "http://192.168.1.10/good.zip"},
              {"name": "no-url"},
              {"url": "http://192.168.1.10/no-name.zip"},
              {"name": "", "url": "http://192.168.1.10/blank.zip"},
              {"name": 42, "url": "http://192.168.1.10/number.zip"}
            ]
        """.trimIndent()
        val result = parsePlainFileList("http://192.168.1.10/list.json", feed)
        assertTrue(result is RemoteListingResult.Listing)
        val entries = (result as RemoteListingResult.Listing).entries
        assertEquals(listOf("good.zip"), entries.map { it.name })
    }

    @Test
    fun emptyFileListIsAnEmptyListing() {
        val result = parsePlainFileList("http://192.168.1.10/list.json", "[]")
        assertTrue(result is RemoteListingResult.Listing)
        assertTrue((result as RemoteListingResult.Listing).entries.isEmpty())
    }

    @Test
    fun hierarchicalCatalogJsonIsRejected() {
        val catalog = """
            {"categories": [{"name": "Nintendo 64", "pages": [{"sections": [{"rows": [{"title": "Game", "links": [{"host": "x", "url": "http://192.168.1.10/g.zip"}]}]}]}]}]}
        """.trimIndent()
        val result = parsePlainFileList("http://192.168.1.10/catalog.json", catalog)
        assertTrue(result is RemoteListingResult.Error)
        assertEquals(
            REMOTE_FOLDER_ERROR,
            (result as RemoteListingResult.Error).message
        )
    }

    @Test
    fun flatItemsSchemaIsRejected() {
        val items = """
            [
              {"title": "Game", "category": "Nintendo 64", "region": "USA", "fileSize": 123, "downloadUrl": "http://192.168.1.10/g.zip"}
            ]
        """.trimIndent()
        val result = parsePlainFileList("http://192.168.1.10/items.json", items)
        assertTrue(result is RemoteListingResult.Error)
        assertEquals(
            REMOTE_FOLDER_ERROR,
            (result as RemoteListingResult.Error).message
        )
    }

    @Test
    fun malformedJsonIsRejected() {
        val result = parsePlainFileList("http://192.168.1.10/list.json", "not json at all")
        assertTrue(result is RemoteListingResult.Error)
    }

    @Test
    fun htmlWithNoLinksParsesToNothing() {
        val entries = parseDirectoryListing(
            "http://192.168.1.10/files/",
            "<html><body><p>hello, no listing here</p></body></html>"
        )
        assertTrue(entries.isEmpty())
    }

    @Test
    fun authHeaderAttachedWhenConfigured() {
        val withAuth = buildRemoteRequest("http://192.168.1.10/files/", "user", "pass")
        assertEquals(Credentials.basic("user", "pass"), withAuth.header("Authorization"))
        val withoutAuth = buildRemoteRequest("http://192.168.1.10/files/", null, null)
        assertNull(withoutAuth.header("Authorization"))
        val noUser = buildRemoteRequest("http://192.168.1.10/files/", "", "pass")
        assertNull(noUser.header("Authorization"))
    }

    @Test
    fun normalizeRemoteFolderUrl() {
        assertEquals(
            "http://192.168.1.5:8080/files",
            normalizeRemoteFolderUrl("192.168.1.5:8080/files")
        )
        assertEquals(
            "https://example.com/files/",
            normalizeRemoteFolderUrl("https://example.com/files/")
        )
        assertEquals(
            "http://example.com/files",
            normalizeRemoteFolderUrl("  example.com/files  ")
        )
        assertNull(normalizeRemoteFolderUrl("ftp://example.com/files"))
        assertNull(normalizeRemoteFolderUrl("javascript:alert(1)"))
        assertNull(normalizeRemoteFolderUrl(""))
        assertNull(normalizeRemoteFolderUrl("http://"))
    }
}
