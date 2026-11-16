package dev.one.launcher.data

import dev.one.launcher.model.Port
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

/** How a file relates to a port. */
enum class MatchKind {
    /** A ROM file in its own right: extension + hint both hit. */
    DIRECT,
    /** An archive whose name hits a hint and whose zip contents were
     *  inspected locally and contain a file that matches the port directly. */
    ARCHIVE_CONFIRMED,
    /** An archive whose name hits a hint. It probably contains the port's
     *  file, but the contents were not (or could not be) inspected — a
     *  .7z/.rar can never be inspected here, and a .zip is only inspected
     *  once it is on this device. */
    ARCHIVE_PROVISIONAL
}

data class PortMatch(
    val port: Port,
    val kind: MatchKind,
    /** For ARCHIVE_CONFIRMED: the matching entry inside the zip. */
    val innerEntry: String? = null
)

/**
 * Conservative matcher between a file name and a port entry.
 *
 * A file matches directly when its extension is one the port accepts AND
 * its name contains one of the port's filename hints (or matches the
 * port's regex when one is present). Hints are matched against whole
 * words of the file name — never as substrings — so "end" cannot match
 * inside "legend" and "day" cannot match inside "daytona". Hints are
 * deliberately short, so an ambiguous name can match more than one port
 * — the detail screen lets the user assign it to the right port. This
 * never deletes or moves user files.
 *
 * Archives (.zip, .7z, .rar) are matched on name only: an archive matches
 * a port when its base name (minus the extension) contains one of the
 * port's hints. A .zip that is already on this device can be confirmed by
 * inspecting its entries with [inspectZip]; .7z/.rar are never inspected
 * (no 7z library is bundled) and stay provisional.
 */
object PortMatcher {
    val ARCHIVE_EXTENSIONS = listOf(".zip", ".7z", ".rar")

    fun isArchive(fileName: String): Boolean {
        val lower = fileName.lowercase()
        return ARCHIVE_EXTENSIONS.any { lower.endsWith(it) }
    }

    /** Lowercase word tokens of a file name, split on non-alphanumerics. */
    private fun fileNameWords(fileName: String): List<String> =
        fileName.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }

    /**
     * A hint matches when its words appear in the file name: a single-word
     * hint must be a whole word, except hints of 5+ letters which may also
     * be a word prefix ("starfox" in "starfox64", "majora" in
     * "majorasmask", "ocarina" in "ocarinaoftime"); a multi-word hint must
     * appear as a contiguous phrase ("star fox", "link to the past").
     * Short hints are never prefixes — "day" must not match "daytona",
     * "mm" must not match "mmo".
     */
    private fun hintMatchesFileName(hint: String, words: List<String>): Boolean {
        val hintWords = hint.lowercase()
            .split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
        if (hintWords.isEmpty()) return false
        if (hintWords.size == 1) {
            val word = hintWords[0]
            return if (word.length >= 5) {
                words.any { it == word || it.startsWith(word) }
            } else {
                words.any { it == word }
            }
        }
        return words.windowed(hintWords.size).any { it == hintWords }
    }

    fun matches(fileName: String, port: Port): Boolean {
        // No file info (e.g. a catalog port whose assets[] is missing):
        // matching is disabled rather than guessed.
        if (port.fileExtensions.isEmpty()) return false
        val lower = fileName.lowercase()
        val extensionOk = port.fileExtensions.any { lower.endsWith(it.lowercase()) }
        if (!extensionOk) return false
        if (port.fileNameHints.isEmpty() && port.fileNameRegex == null) return true
        val words = fileNameWords(fileName)
        if (port.fileNameHints.any { hint -> hintMatchesFileName(hint, words) }) return true
        val regex = port.fileNameRegex
        if (regex != null) {
            return try {
                Regex(regex, RegexOption.IGNORE_CASE).containsMatchIn(fileName)
            } catch (e: Exception) {
                false
            }
        }
        return false
    }

    /**
     * Name-based match kind. DIRECT for a ROM file, ARCHIVE_PROVISIONAL
     * for an archive whose base name hits a hint, null for no match.
     * A provisional archive can be upgraded to ARCHIVE_CONFIRMED by
     * [inspectZip] once the file is on this device. Never inspects file
     * contents — pass a File to [inspectZip] for that.
     */
    fun matchKind(fileName: String, port: Port): MatchKind? {
        if (matches(fileName, port)) return MatchKind.DIRECT
        if (!isArchive(fileName)) return null
        // Archives only match by name when the catalog says the port's
        // files may come in an archive (zip_ok), or for curated ports.
        if (!port.archiveOk) return null
        val baseName = fileName.substringBeforeLast('.', fileName)
        if (nameHitsHints(baseName, port)) return MatchKind.ARCHIVE_PROVISIONAL
        return null
    }

    private fun nameHitsHints(name: String, port: Port): Boolean {
        if (port.fileNameHints.isEmpty() && port.fileNameRegex == null) return true
        val words = fileNameWords(name)
        if (port.fileNameHints.any { hint -> hintMatchesFileName(hint, words) }) return true
        val regex = port.fileNameRegex
        if (regex != null) {
            return try {
                Regex(regex, RegexOption.IGNORE_CASE).containsMatchIn(name)
            } catch (e: Exception) {
                false
            }
        }
        return false
    }

    fun matchingPorts(fileName: String, ports: List<Port>): List<Port> =
        ports.filter { matchKind(fileName, it) != null }

    fun matchKinds(fileName: String, ports: List<Port>): List<PortMatch> =
        ports.mapNotNull { port ->
            matchKind(fileName, port)?.let { PortMatch(port, it) }
        }

    /**
     * Inspect a .zip already on this device: returns the name of the first
     * entry that matches the port directly (extension + hint), or null when
     * no entry matches or the file cannot be read as a zip. Only the entry
     * list is read — nothing is extracted here.
     */
    fun inspectZip(zipFile: File, port: Port): String? {
        if (!zipFile.isFile) return null
        return try {
            ZipFile(zipFile).use { zip ->
                val entries = zip.entries().toList()
                val matched = entries.firstOrNull { entry ->
                    matches(entry.name.substringAfterLast('/'), port)
                }
                matched?.name
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extract exactly one entry from a .zip the user already owns into
     * [destDir]. Never extracts the whole archive. Returns the extracted
     * file, or null when the entry is missing, unreadable, or would escape
     * [destDir] (zip-slip guard).
     */
    fun extractZipEntry(zipFile: File, entryName: String, destDir: File): File? {
        if (!zipFile.isFile) return null
        return try {
            ZipFile(zipFile).use { zip ->
                val entry = zip.getEntry(entryName) ?: return null
                val dest = File(destDir, entry.name.substringAfterLast('/'))
                val destCanonical = dest.canonicalFile
                if (!destCanonical.path.startsWith(destDir.canonicalFile.path)) return null
                destCanonical.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input ->
                    FileOutputStream(destCanonical).use { output ->
                        input.copyTo(output)
                    }
                }
                destCanonical
            }
        } catch (e: Exception) {
            null
        }
    }
}
