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

/**
 * Minimum title similarity for a NAME_ONLY match (v1.8.0): the
 * file's title must be at least 90% explained by the game's title
 * ([titleSimilarity]). DIRECT matches are unaffected.
 */
const val NAME_ONLY_MIN_TITLE_SIMILARITY = 0.90

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
 * Before anything else, a file must be a game file
 * ([isGameFileForPort], v1.6.1): its extension — from the file name
 * or the URL, either side may carry it — is an archive or one of the
 * port's platform's game extensions ([GAME_FILE_EXTENSIONS]), or one
 * of the port's own catalog formats. A movie (.mkv), a document
 * (.txt, .html), or a collection index with no extension on either
 * side can never match, however many of the port's hints its name
 * contains — a name-only match is no longer a free pass for non-game
 * files. The gate is a precondition on every match kind (DIRECT,
 * ARCHIVE_*, NAME_ONLY); it does not replace the hint/extension
 * logic below it.
 *
 * Next, the file's platform tag must agree with the port's platform
 * ([isPlatformConsistent], v1.8.1): a file whose name carries a
 * parenthesized system tag for a different platform than the game's
 * — "(Wii U)" on a Switch port's file — never matches, on any match
 * kind. Archives are platform-agnostic as containers, but the tag
 * on the archive's name still names the system its contents are
 * for. A file with no recognizable platform tag, and a port whose
 * platform is unknown, are unaffected — the check only vetoes a
 * positive conflict.
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

    /**
     * The game-file gate (v1.6.1): true when the file is a game file
     * for this port. The extension may come from the file name or
     * the URL — either side may carry it (a remote entry's name is
     * often extension-less while its URL ends in ".zip/"). An
     * archive is always a game file; otherwise the extension must
     * be one of the port's own catalog formats — a future catalog
     * format the dictionary does not list yet must never be blocked
     * by the gate — or one of the port's platform's game extensions
     * (the union across platforms when the platform is unknown,
     * "other", or unlisted). A file with no extension on either side
     * is never a game file.
     */
    private fun isGameFileForPort(
        fileName: String,
        port: Port,
        fileUrl: String? = null
    ): Boolean {
        // A magnet link is never a game file, whatever its name ends
        // in: a magnet entry's name is a file path (often ".zip"),
        // so the extension check below would pass it. The gate
        // excludes magnets before any name/extension check. (The
        // remote call sites also skip entry.isMagnet outright — see
        // matchRemoteEntries and matchServerFilesForGame.)
        if (fileUrl != null && isMagnetUrl(fileUrl)) return false
        val exts = listOfNotNull(
            extensionOf(fileName),
            fileUrl?.let { extensionOf(it) }
        )
        if (exts.isEmpty()) return false
        val ownFormats = port.fileExtensions.map { it.trimStart('.').lowercase() }
        val platformFormats = extensionsForPlatform(port.platformId)
        return exts.any { ext ->
            isArchiveExtension(ext) ||
                ext in ownFormats ||
                ext in platformFormats
        }
    }

    fun matches(fileName: String, port: Port, fileUrl: String? = null): Boolean {
        if (!isGameFileForPort(fileName, port, fileUrl)) return false
        // The platform-consistency check (v1.8.1): a file tagged for
        // a different platform than the port's never matches,
        // however identical the title and however well the extension
        // fits — a Wii U release is not the Switch port's file.
        if (!isPlatformConsistent(fileName, port.platformId, port.game, fileUrl)) return false
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
     * The game-file gate runs first (v1.6.1): a file that is not a
     * game file returns null here, whatever its name contains. The
     * platform-consistency check (v1.8.1) runs next: a file tagged
     * for a different platform than the port's returns null here too,
     * on every match kind — NAME_ONLY, DIRECT, and ARCHIVE alike.
     * [fileUrl] carries the extension when the name does not — a
     * remote entry's name is often extension-less while its URL ends
     * in ".zip/"; either side saying "game file" is enough, so a
     * local or cloud file whose name already has its extension needs
     * no URL at all.
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
     *
     * Both name-only ways also require the title-similarity gate
     * (v1.8.0, [titleSimilarity] >= 0.90): a distinctive single word
     * can still be a common word in file names ("blue", "gold",
     * "double", "system"), so the file's title must be mostly
     * explained by the game's title. DIRECT matches are unchanged —
     * the gate applies only to NAME_ONLY.
     */
    fun matchKind(fileName: String, port: Port, fileUrl: String? = null): MatchKind? {
        // The game-file gate first: a non-game file matches nothing,
        // whatever its name contains.
        if (!isGameFileForPort(fileName, port, fileUrl)) return null
        // The platform-consistency check (v1.8.1): a file tagged for
        // a different platform than the port's never matches, on any
        // match kind — the game-file gate above cannot save an
        // archive whose title is identical but whose tag names
        // another system (a Wii U .7z is not a Switch port's file).
        if (!isPlatformConsistent(fileName, port.platformId, port.game, fileUrl)) return null
        if (matches(fileName, port, fileUrl)) return MatchKind.DIRECT
        if (port.fileExtensions.isEmpty()) {
            // No formats in the catalog for this port: match on the
            // game's name only. A port with no distinctive hint and
            // fewer than two long hints hitting matches nothing —
            // safe failure, better than a false match. And the
            // title-similarity gate (v1.8.0): the file's title must
            // be at least 90% explained by the game's title, or the
            // shared word is a coincidence ("blue" in "Pokemon Blue"
            // is not Blue Dragon's file).
            if (titleSimilarity(fileName, port.game) < NAME_ONLY_MIN_TITLE_SIMILARITY) {
                return null
            }
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

    fun matchingPorts(
        fileName: String,
        ports: List<Port>,
        fileUrl: String? = null
    ): List<Port> =
        ports.filter { matchKind(fileName, it, fileUrl) != null }

    fun matchKinds(
        fileName: String,
        ports: List<Port>,
        fileUrl: String? = null
    ): List<PortMatch> =
        ports.mapNotNull { port ->
            matchKind(fileName, port, fileUrl)?.let { PortMatch(port, it) }
        }

    /**
     * Match one remote directory's FILE entries against a port, for the
     * detail page's Server library section. Folders are never returned:
     * a folder name can hit a hint, but the section lists files the
     * user can open in the browser. The entry's URL is passed through
     * too — either side may carry the extension, and a remote entry's
     * name is often extension-less while its URL ends in ".zip/".
     * This is the ONLY place remote entries are matched to ports — the
     * Library, its search, and the home page never see remote entries.
     */
    fun matchRemoteEntries(
        entries: List<RemoteEntry>,
        port: Port
    ): List<Pair<RemoteEntry, MatchKind>> =
        entries.filter { !it.isFolder && !it.isMagnet }.mapNotNull { entry ->
            matchKind(entry.name, port, entry.url)?.let { entry to it }
        }

    /**
     * Match one magnet entry against a port, for the game page's
     * Magnet links section (v1.8.3). A magnet is a copyable link,
     * not a file, so it is matched by its NAME only — the magnet:
     * URI carries no file extension and must never be passed to the
     * gate (it would reject every magnet outright, which is why the
     * v1.8.0 game page showed "No file for this game yet" for games
     * whose only links are magnets). The same rules as [matchKind]
     * apply: the game-file gate on the name's extension (magnets
     * are .zip-named, so the archive / name-only path applies), the
     * platform-consistency check (a Wii U-tagged magnet is not a
     * Switch game's link), and the 90% title-similarity gate for
     * NAME_ONLY. A magnet whose name has no game-file extension
     * matches nothing. The result is a display-only copy link — the
     * caller must keep it in the magnet display, out of "Your
     * files" and "Has files" (see [matchRemoteEntries] and
     * [matchServerFilesForGame]).
     */
    fun matchMagnetKind(entry: RemoteEntry, port: Port): MatchKind? {
        if (!entry.isMagnet) return null
        return matchKind(entry.name, port, fileUrl = null)
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

