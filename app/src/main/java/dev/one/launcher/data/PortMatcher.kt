package dev.one.launcher.data

import dev.one.launcher.model.Port
import java.io.File
import java.io.FileOutputStream
import java.text.Normalizer
import java.util.zip.ZipFile

/**
 * Fold diacritics for tokenization: "pokémon" and "pokemon" must be
 * the same token, on both sides of a match — the game title a hint
 * comes from and the file name it is checked against. NFD splits a
 * letter from its accent marks; the marks (Unicode category Mn) are
 * dropped, leaving the base letter. Lowercases as well, so callers
 * get a fully folded string. Without this, "Pokémon" tokenizes to
 * "pok" + "mon" and no hint ever matches a real file name.
 */
fun normalizeForTokens(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()

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
    ARCHIVE_PROVISIONAL,
    /**
     * Name-only, provisional: the catalog lists no file formats for this
     * port (464/536 catalog ports have none), so there is no extension to
     * verify — the file's name hits one of the port's DISTINCTIVE hints
     * (a hint no other port in the catalog shares, at least 4
     * characters), or at least two of its filename hints of 4+
     * characters, through the same whole-word rules, and nothing more
     * is claimed. The format was never verified; the UI labels it "name
     * only" so the user is not misled.
     */
    NAME_ONLY
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
 *
 * Ports the catalog lists no formats for (most of the catalog) match by
 * name only, through the same whole-word hint rules — on a DISTINCTIVE
 * hint, one no other port in the catalog shares
 * ([Port.distinctiveNameHints]), or on at least two of the port's
 * filename hints of 4+ characters — and are labeled provisional
 * ([MatchKind.NAME_ONLY]) — the format was never verified, so no
 * extension is invented for them.
 */
object PortMatcher {
    val ARCHIVE_EXTENSIONS = listOf(".zip", ".7z", ".rar")

    fun isArchive(fileName: String): Boolean {
        val lower = fileName.lowercase()
        return ARCHIVE_EXTENSIONS.any { lower.endsWith(it) }
    }

    /** Lowercase word tokens of a file name, split on non-alphanumerics. */
    private fun fileNameWords(fileName: String): List<String> =
        normalizeForTokens(fileName).split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }

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
        val hintWords = normalizeForTokens(hint)
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
     * for an archive whose base name hits a hint, NAME_ONLY for a file
     * whose name hits a DISTINCTIVE hint of a port the catalog lists no
     * formats for, null for no match. A provisional archive can be
     * upgraded to ARCHIVE_CONFIRMED by [inspectZip] once the file is on
     * this device. A name-only match is never upgraded — there is no
     * format to verify against. Never inspects file contents — pass a
     * File to [inspectZip] for that.
     *
     * The name-only branch applies ONLY when the port has no formats
     * at all, and only in two ways: (1) a distinctive hint — one no
     * other port in the catalog shares, at least 4 characters long —
     * or (2) at least two of the port's filename hints, each at least
     * 4 characters long, hitting the file name. One shared hint is
     * never enough ("pokémon" alone must not match every Pokémon
     * port), but two together identify the game ("pokemon" + "black"
     * is Eidos's file, not Arceus's). Ports with formats keep the
     * strict extension-gated matching above, unchanged.
     */
    fun matchKind(fileName: String, port: Port): MatchKind? {
        if (matches(fileName, port)) return MatchKind.DIRECT
        if (port.fileExtensions.isEmpty()) {
            // No formats in the catalog for this port: match on the
            // game's name only. A port with no distinctive hint and
            // fewer than two long hints hitting matches nothing —
            // safe failure, better than a false match.
            if (port.distinctiveNameHints.isNotEmpty() &&
                nameHitsHints(fileName, port.distinctiveNameHints, null)
            ) {
                return MatchKind.NAME_ONLY
            }
            if (nameHitsTwoHints(fileName, port.fileNameHints)) {
                return MatchKind.NAME_ONLY
            }
            return null
        }
        if (!isArchive(fileName)) return null
        // Archives only match by name when the catalog says the port's
        // files may come in an archive (zip_ok), or for curated ports.
        if (!port.archiveOk) return null
        val baseName = fileName.substringBeforeLast('.', fileName)
        if (nameHitsHints(baseName, port.fileNameHints, port.fileNameRegex)) {
            return MatchKind.ARCHIVE_PROVISIONAL
        }
        return null
    }

    private fun nameHitsHints(name: String, hints: List<String>, regex: String?): Boolean {
        if (hints.isEmpty() && regex == null) return true
        val words = fileNameWords(name)
        if (hints.any { hint -> hintMatchesFileName(hint, words) }) return true
        if (regex != null) {
            return try {
                Regex(regex, RegexOption.IGNORE_CASE).containsMatchIn(name)
            } catch (e: Exception) {
                false
            }
        }
        return false
    }

    /**
     * True when the file name hits at least two of the port's hints,
     * each at least 4 characters long, by the same whole-word / prefix
     * rules as [hintMatchesFileName]. Short hints ("new", "men", "pok")
     * never count — a single shared word is never enough, and two short
     * ones are not either.
     */
    private fun nameHitsTwoHints(name: String, hints: List<String>): Boolean {
        val words = fileNameWords(name)
        var hits = 0
        for (hint in hints) {
            if (hint.length < 4) continue
            if (hintMatchesFileName(hint, words)) {
                hits++
                if (hits >= 2) return true
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
     * Match one remote directory's FILE entries against a port, for the
     * detail page's Server library section. Folders are never returned:
     * a folder name can hit a hint, but the section lists files the
     * user can open in the browser. This is the ONLY place remote
     * entries are matched to ports — the Library, its search, and the
     * home page never see remote entries.
     */
    fun matchRemoteEntries(
        entries: List<RemoteEntry>,
        port: Port
    ): List<Pair<RemoteEntry, MatchKind>> =
        entries.filter { !it.isFolder }.mapNotNull { entry ->
            matchKind(entry.name, port)?.let { entry to it }
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
