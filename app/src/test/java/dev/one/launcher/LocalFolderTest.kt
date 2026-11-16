package dev.one.launcher

import dev.one.launcher.data.DetectedFile
import dev.one.launcher.data.MatchKind
import dev.one.launcher.data.detectPairs
import dev.one.launcher.data.resolveDetected
import dev.one.launcher.model.Port
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.2: local game-files folder presearch and its cache merge.
 *
 * The SAF scan itself cannot run on the JVM (DocumentFile needs an
 * Android ContentResolver), so these tests cover the pure layers the
 * scan feeds: file-name list -> detected pairs, and cached pairs vs
 * fresh scan results. The scan's only job is listing (name, uri) pairs
 * and handing them to [detectPairs].
 */
class LocalFolderTest {

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

    private val starfox = Port(
        id = "starship",
        title = "Starship",
        game = "Star Fox 64",
        system = "Nintendo 64",
        type = "Decompilation",
        repo = "izzy2lost/Starship",
        releasesUrl = "https://github.com/izzy2lost/Starship/releases",
        packageNames = emptyList(),
        fileExtensions = listOf(".z64"),
        fileNameHints = listOf("starfox", "star fox", "sf64"),
        requiredDump = "Your own Star Fox 64 ROM (.z64)."
    )

    private val ports = listOf(oot, starfox)

    // ---- detectPairs ----

    @Test
    fun garrettsOotZipIsDetectedAsProvisionalArchive() {
        val detected = detectPairs(
            listOf(
                "Legend of Zelda, The - Ocarina of Time (USA).zip" to "content://folder/1"
            ),
            ports
        )
        val soh = detected["soh"] ?: error("no soh pair")
        assertEquals(1, soh.size)
        assertEquals(
            "Legend of Zelda, The - Ocarina of Time (USA).zip", soh[0].name
        )
        assertEquals("content://folder/1", soh[0].uri)
        assertEquals(MatchKind.ARCHIVE_PROVISIONAL, soh[0].kind)
    }

    @Test
    fun directRomIsDetectedAsDirect() {
        val detected = detectPairs(
            listOf("ZELDA OOT.z64" to "content://folder/2"),
            ports
        )
        assertEquals(MatchKind.DIRECT, detected["soh"]!![0].kind)
    }

    @Test
    fun nonMatchingFilesProduceNoPairs() {
        val detected = detectPairs(
            listOf(
                "mario-kart.zip" to "content://folder/3",
                "readme.txt" to "content://folder/4",
                "Star Fox 64.zip" to "content://folder/5"
            ),
            ports
        )
        // Star Fox 64.zip matches starship; the other two match nothing.
        assertTrue(detected["soh"].isNullOrEmpty())
        assertEquals(1, detected["starship"]!!.size)
        assertEquals(MatchKind.ARCHIVE_PROVISIONAL, detected["starship"]!![0].kind)
    }

    @Test
    fun oneFileCanMatchMoreThanOnePort() {
        // "mm" is a hint on 2ship and zelda64recomp in the real catalog;
        // here "ocarina" is only on soh, so use a name hitting both test
        // ports via a shared hint-free path: a .z64 named for both games.
        val both = Port(
            id = "both",
            title = "Both",
            game = "Test",
            system = "Nintendo 64",
            type = "Decompilation",
            repo = "test/test",
            releasesUrl = "https://example.com",
            fileExtensions = listOf(".z64"),
            fileNameHints = listOf("oot"),
            requiredDump = "test"
        )
        val detected = detectPairs(
            listOf("ZELDA OOT.z64" to "content://folder/6"),
            listOf(oot, both)
        )
        assertEquals(1, detected["soh"]!!.size)
        assertEquals(1, detected["both"]!!.size)
    }

    @Test
    fun emptyFolderGivesEmptyPairs() {
        assertTrue(detectPairs(emptyList(), ports).isEmpty())
    }

    // ---- resolveDetected (cache merge) ----

    private val cached = mapOf(
        "soh" to listOf(
            DetectedFile(
                "Legend of Zelda, The - Ocarina of Time (USA).zip",
                "content://folder/1",
                MatchKind.ARCHIVE_PROVISIONAL
            )
        )
    )
    private val fresh = mapOf(
        "starship" to listOf(
            DetectedFile(
                "Star Fox 64.z64",
                "content://folder/9",
                MatchKind.DIRECT
            )
        )
    )

    @Test
    fun cachedPairsShownOnStartBeforeAnyScan() {
        // Cold start: folder set, scan not finished yet.
        assertEquals(cached, resolveDetected(cached, fresh = null, folderAvailable = true))
    }

    @Test
    fun freshScanReplacesCachedPairsOutright() {
        // A file the user moved away must stop matching.
        assertEquals(fresh, resolveDetected(cached, fresh = fresh, folderAvailable = true))
    }

    @Test
    fun staleCacheKeptWhenFolderUnavailable() {
        // Permission lost / folder gone: keep showing what was found,
        // never silently drop it — even if a scan result somehow exists.
        assertEquals(cached, resolveDetected(cached, fresh = fresh, folderAvailable = false))
        assertEquals(cached, resolveDetected(cached, fresh = null, folderAvailable = false))
    }

    @Test
    fun emptyCacheAndEmptyScanStayEmpty() {
        assertEquals(
            emptyMap<String, List<DetectedFile>>(),
            resolveDetected(emptyMap(), fresh = emptyMap(), folderAvailable = true)
        )
    }
}
