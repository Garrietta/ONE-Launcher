package dev.one.launcher.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dev.one.launcher.model.Port
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/**
 * A file found in the user's chosen local game-files folder that matches
 * a port by name. The URI is a Storage Access Framework tree/document URI,
 * not a filesystem path — the file may live anywhere the user picked.
 */
@Serializable
data class DetectedFile(
    val name: String,
    val uri: String,
    val kind: MatchKind
)

/**
 * Pure name-to-port mapping for a folder listing: each (file name, uri)
 * pair is matched against every port with [PortMatcher.matchKinds], so
 * one file can be a candidate for more than one port and one port can
 * have several candidate files. The result is grouped by port id.
 *
 * Folder zips are name-based provisional matches only. A SAF file is not
 * a [java.io.File], so it cannot be opened with [PortMatcher.inspectZip]
 * here — confirmation happens once the file is copied into Downloads/ONE.
 */
fun detectPairs(
    files: List<Pair<String, String>>,
    ports: List<Port>
): Map<String, List<DetectedFile>> {
    val result = mutableMapOf<String, MutableList<DetectedFile>>()
    for ((name, uri) in files) {
        for (match in PortMatcher.matchKinds(name, ports)) {
            result.getOrPut(match.port.id) { mutableListOf() }
                .add(DetectedFile(name = name, uri = uri, kind = match.kind))
        }
    }
    return result
}

/**
 * Cache merge for detected pairs — the rule the ViewModel applies on
 * every load and every scan:
 * - Folder unavailable (permission lost / folder gone): keep the cached
 *   pairs visible, marked stale by the caller. Never silently drop them.
 * - Folder available and a fresh scan finished: the fresh scan replaces
 *   the cache outright (a file the user moved away must stop matching).
 * - Folder available but no scan result yet (cold start, scan still
 *   running): show the cache so badges render instantly.
 */
fun resolveDetected(
    cached: Map<String, List<DetectedFile>>,
    fresh: Map<String, List<DetectedFile>>?,
    folderAvailable: Boolean
): Map<String, List<DetectedFile>> {
    if (!folderAvailable) return cached
    return fresh ?: cached
}

/**
 * Scans the user's chosen local game-files folder: the top level plus
 * one level of subfolders, capped at [MAX_FILES] files, always on
 * [Dispatchers.IO] — never on the main thread. Returns null when the
 * folder cannot be read at all (persisted permission lost, folder
 * deleted), so the caller can keep the cached pairs and flag the folder
 * as unavailable instead of pretending the folder is empty.
 */
object LocalFolderScanner {
    const val MAX_FILES = 500

    suspend fun scan(
        context: Context,
        treeUri: Uri,
        ports: List<Port>
    ): Map<String, List<DetectedFile>>? = withContext(Dispatchers.IO) {
        try {
            val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext null
            if (!root.exists() || !root.canRead()) return@withContext null
            val found = mutableListOf<Pair<String, String>>()
            collectFiles(root, found, depth = 0)
            detectPairs(found, ports)
        } catch (e: Exception) {
            null
        }
    }

    private fun collectFiles(
        dir: DocumentFile,
        out: MutableList<Pair<String, String>>,
        depth: Int
    ) {
        if (out.size >= MAX_FILES) return
        val children = try {
            dir.listFiles()
        } catch (e: Exception) {
            return
        }
        for (child in children) {
            if (out.size >= MAX_FILES) return
            try {
                when {
                    child.isFile -> {
                        val name = child.name ?: continue
                        out.add(name to child.uri.toString())
                    }
                    // One level of subfolders only: a ROMs folder is
                    // usually flat or one folder per game.
                    child.isDirectory && depth == 0 -> collectFiles(child, out, depth + 1)
                }
            } catch (e: Exception) {
                // One unreadable entry must not kill the whole scan.
                continue
            }
        }
    }
}
