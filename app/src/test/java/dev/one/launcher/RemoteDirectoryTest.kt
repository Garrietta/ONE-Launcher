package dev.one.launcher

import dev.one.launcher.data.REMOTE_FOLDER_ERROR
import dev.one.launcher.data.RemoteListingResult
import dev.one.launcher.data.buildRemoteRequest
import dev.one.launcher.data.isMagnetUrl
import dev.one.launcher.data.looksLikeDirectoryListing
import dev.one.launcher.data.magnetLinkToCopy
import dev.one.launcher.data.normalizeRemoteFolderUrl
import dev.one.launcher.data.parseDirectoryListing
import dev.one.launcher.data.parsePlainFileList
import dev.one.launcher.data.parseRemoteBody
import dev.one.launcher.data.parseRemoteStream
import kotlinx.serialization.ExperimentalSerializationApi
import okhttp3.Credentials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalSerializationApi::class)
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

    // ---- Magnet entries (v1.8.0) ----

    private val magnetUri =
        "magnet:?xt=urn:btih:9c6e87aaca8a26f4755d44079d5575b1732428f6" +
            "&dn=Acorn%20-%20Archimedes" +
            "&tr=udp://tracker.opentrackr.org:1337/announce" +
            "&tr=udp://open.stealth.si:80/announce" +
            "&tr=udp://tracker.torrent.eu.org:451/announce"

    @Test
    fun magnetEntryDetectedByKind() {
        val feed = """
            [
              {"name": "Acorn - Archimedes", "url": "$magnetUri", "kind": "magnet", "category": "Redump", "size": 22774064087},
              {"name": "game.zip", "url": "http://192.168.1.10/game.zip", "kind": "file"},
              {"name": "plain.zip", "url": "http://192.168.1.10/plain.zip"}
            ]
        """.trimIndent()
        val result = parsePlainFileList("http://192.168.1.10/list.json", feed)
        assertTrue(result is RemoteListingResult.Listing)
        val entries = (result as RemoteListingResult.Listing).entries
        assertEquals(3, entries.size)
        // The magnet entry is detected, and its magnet: URI is used
        // as-is — never resolved against the feed URL.
        assertTrue(entries[0].isMagnet)
        assertEquals(magnetUri, entries[0].url)
        assertEquals(22774064087L, entries[0].sizeBytes)
        // A file entry, and one with no kind at all, are not magnets.
        assertFalse(entries[1].isMagnet)
        assertFalse(entries[2].isMagnet)
    }

    @Test
    fun magnetUrlWithoutKindIsStillDetected() {
        // The scheme fallback: a magnet: URL is a magnet entry even
        // when "kind" is missing.
        val feed = """
            [
              {"name": "Acorn - Archimedes", "url": "$magnetUri"}
            ]
        """.trimIndent()
        val result = parsePlainFileList("http://192.168.1.10/list.json", feed)
        assertTrue(result is RemoteListingResult.Listing)
        val entries = (result as RemoteListingResult.Listing).entries
        assertEquals(1, entries.size)
        assertTrue(entries[0].isMagnet)
        assertTrue(isMagnetUrl(entries[0].url))
        assertEquals(magnetUri, magnetLinkToCopy(entries[0]))
    }

    @Test
    fun magnetLinkToCopyReturnsFullMagnetOrNull() {
        val feed = """
            [
              {"name": "Acorn - Archimedes", "url": "$magnetUri", "kind": "magnet"},
              {"name": "game.zip", "url": "http://192.168.1.10/game.zip"}
            ]
        """.trimIndent()
        val result = parsePlainFileList("http://192.168.1.10/list.json", feed)
        val entries = (result as RemoteListingResult.Listing).entries
        // The magnet entry copies its full magnet URI.
        assertEquals(magnetUri, magnetLinkToCopy(entries[0]))
        // A file entry has nothing to copy.
        assertNull(magnetLinkToCopy(entries[1]))
    }

    @Test
    fun magnetEntryWithZipNameIsStillAMagnet() {
        // A magnet entry's name is a file path that may end in a
        // game extension (e.g. ".zip"). It must still be a magnet —
        // the matching gate relies on isMagnet, not on the name.
        val feed = """
            [
              {"name": "Redump/Microsoft - Xbox 360/007 - Blood Stone (Japan).zip", "url": "$magnetUri", "kind": "magnet"}
            ]
        """.trimIndent()
        val result = parsePlainFileList("http://192.168.1.10/list.json", feed)
        val entries = (result as RemoteListingResult.Listing).entries
        assertEquals(1, entries.size)
        assertTrue(entries[0].isMagnet)
        assertEquals(magnetUri, entries[0].url)
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

    // ---- Web-app shells are refused, never read as listings (v1.5.0) ----
    //
    // Garrett's own server is a JS web app, not a directory listing.
    // Its sidebar/nav chrome used to be extracted as file entries —
    // rows like "span:last-child]:truncate hover:bg-sidebar-accent
    // hov..." and a File name dialog showing raw sidebar-button HTML.
    // A page with none of the listing markers gets the plain error
    // and zero entries instead.

    private val reactAppShell = """
        <!doctype html><html><head><title>My Server</title>
        <link rel="stylesheet" href="/assets/index.css"></head><body>
        <div id="root"><div class="flex h-screen w-full">
        <aside class="hidden md:flex flex-col border-r bg-sidebar">
        <nav><a href="/browse" class="flex items-center gap-2 px-3 py-2 text-sm hover:bg-sidebar-accent hover:text-sidebar-accent-foreground">Browse</a>
        <a href="/games" class="flex items-center gap-2 px-3 py-2 text-sm hover:bg-sidebar-accent">Games</a>
        <button class="group flex w-full items-center gap-2 rounded-md p-2 text-left text-sm outline-hidden transition-[width,height,padding] focus-visible:ring-2 [&>span:last-child]:truncate hover:bg-sidebar-accent hover:text-sidebar-accent-foreground h-8 text-sm" data-slot="sidebar-menu-button" data-sidebar="menu-button" data-size="default" data-active="false">About RVZ files</button>
        </nav></aside>
        <main><h2>Welcome to my server</h2><p>Pick a section from the sidebar.</p></main>
        </div></div>
        <script type="module" src="/assets/index.js"></script>
        </body></html>
    """.trimIndent()

    @Test
    fun webAppShellIsRefusedWithPlainError() {
        assertFalse(looksLikeDirectoryListing(reactAppShell))
        val result = parseRemoteBody("http://192.168.1.10/", reactAppShell)
        assertTrue(result is RemoteListingResult.Error)
        assertEquals(
            REMOTE_FOLDER_ERROR,
            (result as RemoteListingResult.Error).message
        )
    }

    @Test
    fun standardListingsStillPassValidation() {
        assertTrue(looksLikeDirectoryListing(apacheListing))
        assertTrue(looksLikeDirectoryListing(nginxListing))
        val apache = parseRemoteBody("http://192.168.1.10/files/", apacheListing)
        assertTrue(apache is RemoteListingResult.Listing)
        assertEquals(3, (apache as RemoteListingResult.Listing).entries.size)
        val nginx = parseRemoteBody("http://192.168.1.10/roms/", nginxListing)
        assertTrue(nginx is RemoteListingResult.Listing)
        assertEquals(2, (nginx as RemoteListingResult.Listing).entries.size)
    }

    @Test
    fun plainJsonListStillPassesThroughParseRemoteBody() {
        val result = parseRemoteBody(
            "http://192.168.1.10/list.json",
            """[{"name": "a.zip", "url": "http://192.168.1.10/a.zip"}]"""
        )
        assertTrue(result is RemoteListingResult.Listing)
        assertEquals(
            listOf("a.zip"),
            (result as RemoteListingResult.Listing).entries.map { it.name }
        )
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

    // ---- Streaming JSON (v1.8.7) ----
    //
    // The feed is decoded straight into a DTO list (String path) or
    // straight from the response byte stream (stream path) — no
    // intermediate whole-body String, no JsonObject DOM. These tests
    // prove the streamed paths produce the identical RemoteEntry
    // list as the old DOM walk, on the real snapshot resource and on
    // a tricky feed, and that malformed input still errors, never
    // crashes.

    private fun snapshotText(): String {
        val stream = javaClass.classLoader
            ?.getResourceAsStream("remote-2026-11-16.json")
        requireNotNull(stream) { "test resource remote-2026-11-16.json missing" }
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun snapshotStream(): java.io.InputStream {
        val stream = javaClass.classLoader
            ?.getResourceAsStream("remote-2026-11-16.json")
        requireNotNull(stream) { "test resource remote-2026-11-16.json missing" }
        return stream
    }

    @Test
    fun snapshotFeedDecodesIdenticallyFromStringAndStream() {
        // The v1.6.1 snapshot: 376 entries, every one a plain
        // {name, url, category} object, no magnets, no sizes.
        val fromString = parsePlainFileList(
            "http://192.168.1.10/list.json", snapshotText()
        )
        assertTrue(fromString is RemoteListingResult.Listing)
        val stringEntries = (fromString as RemoteListingResult.Listing).entries
        assertEquals(376, stringEntries.size)

        val fromStream = parsePlainFileList(
            "http://192.168.1.10/list.json", snapshotStream()
        )
        assertTrue(fromStream is RemoteListingResult.Listing)
        val streamEntries = (fromStream as RemoteListingResult.Listing).entries
        // The stream path must produce the identical list.
        assertEquals(stringEntries, streamEntries)

        // The list is sorted by lowercase name, first and last pinned.
        assertEquals(
            stringEntries.map { it.name.lowercase() }.sorted(),
            stringEntries.map { it.name.lowercase() }
        )
        val first = stringEntries.first()
        assertEquals("3DS Content in CIA Format", first.name)
        assertEquals("https://hshop.erista.me/", first.url)
        assertEquals("Nintendo 3DS", first.category)
        assertNull(first.kind)
        assertNull(first.sizeBytes)
        assertFalse(first.isFolder)
        assertFalse(first.isMagnet)
        val tosec = stringEntries.first { it.name == "[TOSEC-ISO] 3DO" }
        assertEquals("https://archive.org/download/tosec-iso-3do", tosec.url)
        assertEquals("ROM Sets", tosec.category)
        val last = stringEntries.last()
        assertEquals("Xenoblade Chronicles X (Wii U) (USA)", last.name)
        assertEquals("Xenoblade", last.category)
        // Every entry is a plain file entry with a usable name/url.
        assertTrue(stringEntries.all { it.name.isNotBlank() && it.url.isNotBlank() })
        assertTrue(stringEntries.none { it.isFolder })
    }

    @Test
    fun streamParseMatchesStringParseOnTrickyFeed() {
        // Wrong-typed values, missing fields, a magnet, a relative
        // URL, an unknown extra field: the stream path must skip
        // exactly the entries the String path skips.
        val feed = """
            [
              {"name": "a.zip", "url": "http://192.168.1.10/a.zip", "size": 123, "extra": true},
              {"name": "b.bin", "url": "/b.bin", "size": "big"},
              {"name": "c.bin", "url": "/c.bin", "size": -5},
              {"name": "d.bin", "url": "/d.bin", "kind": "magnet"},
              {"name": 42, "url": "http://192.168.1.10/number.zip"},
              {"name": "no-url"},
              {"url": "http://192.168.1.10/no-name.zip"},
              {"name": "", "url": "http://192.168.1.10/blank.zip"}
            ]
        """.trimIndent()
        val fromString = parsePlainFileList("http://192.168.1.10/list.json", feed)
        val fromStream = parsePlainFileList(
            "http://192.168.1.10/list.json",
            feed.toByteArray(Charsets.UTF_8).inputStream()
        )
        assertTrue(fromString is RemoteListingResult.Listing)
        assertTrue(fromStream is RemoteListingResult.Listing)
        val stringEntries = (fromString as RemoteListingResult.Listing).entries
        val streamEntries = (fromStream as RemoteListingResult.Listing).entries
        assertEquals(stringEntries, streamEntries)
        assertEquals(
            listOf("a.zip", "b.bin", "c.bin", "d.bin"),
            streamEntries.map { it.name }
        )
        assertEquals(123L, streamEntries[0].sizeBytes)
        assertNull(streamEntries[1].sizeBytes)
        assertNull(streamEntries[2].sizeBytes)
        assertTrue(streamEntries[3].isMagnet)
    }

    @Test
    fun streamParseRejectsMalformedJson() {
        assertTrue(
            parsePlainFileList(
                "http://192.168.1.10/list.json",
                "not json at all".toByteArray(Charsets.UTF_8).inputStream()
            ) is RemoteListingResult.Error
        )
        assertTrue(
            parsePlainFileList(
                "http://192.168.1.10/list.json",
                "".toByteArray(Charsets.UTF_8).inputStream()
            ) is RemoteListingResult.Error
        )
        // A JSON object (a catalog shape) is not a file list.
        assertTrue(
            parsePlainFileList(
                "http://192.168.1.10/list.json",
                """{"categories": []}""".toByteArray(Charsets.UTF_8).inputStream()
            ) is RemoteListingResult.Error
        )
    }

    @Test
    fun remoteStreamSniffsJsonVsHtml() {
        // JSON bytes go down the JSON path, straight from the stream.
        val jsonResult = parseRemoteStream(
            "http://192.168.1.10/list.json",
            """[{"name": "a.zip", "url": "http://192.168.1.10/a.zip"}]"""
                .toByteArray(Charsets.UTF_8).inputStream()
        )
        assertTrue(jsonResult is RemoteListingResult.Listing)
        assertEquals(
            listOf("a.zip"),
            (jsonResult as RemoteListingResult.Listing).entries.map { it.name }
        )
        // Leading whitespace before the "[" is sniffed past.
        val paddedResult = parseRemoteStream(
            "http://192.168.1.10/list.json",
            "  \n\t [{\"name\": \"b.zip\", \"url\": \"/b.zip\"}]"
                .toByteArray(Charsets.UTF_8).inputStream()
        )
        assertTrue(paddedResult is RemoteListingResult.Listing)
        assertEquals(
            listOf("b.zip"),
            (paddedResult as RemoteListingResult.Listing).entries.map { it.name }
        )
        // HTML bytes go down the directory-listing path.
        val htmlResult = parseRemoteStream(
            "http://192.168.1.10/files/",
            apacheListing.toByteArray(Charsets.UTF_8).inputStream()
        )
        assertTrue(htmlResult is RemoteListingResult.Listing)
        assertEquals(3, (htmlResult as RemoteListingResult.Listing).entries.size)
        // A web-app shell is refused, and an empty body errors —
        // never a crash, never entries.
        val shellResult = parseRemoteStream(
            "http://192.168.1.10/",
            reactAppShell.toByteArray(Charsets.UTF_8).inputStream()
        )
        assertTrue(shellResult is RemoteListingResult.Error)
        val emptyResult = parseRemoteStream(
            "http://192.168.1.10/list.json",
            "".toByteArray(Charsets.UTF_8).inputStream()
        )
        assertTrue(emptyResult is RemoteListingResult.Error)
    }
}
