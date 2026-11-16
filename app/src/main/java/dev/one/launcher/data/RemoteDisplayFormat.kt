package dev.one.launcher.data

import kotlinx.serialization.Serializable

/**
 * One literal find → replace pair for a remote folder's display format.
 * Matching is literal text, never regex: a find string containing
 * regex characters (e.g. "[v1.0]") is matched as-is.
 */
@Serializable
data class FindReplace(val find: String, val replace: String)

/**
 * Per-source DISPLAY format for the remote-folder browser. Display
 * only: it changes how already-parsed entries are shown, never what
 * the app reads from the server. The parser, the download, and any
 * matching still use the real filename; this only reformats the title
 * shown on a row.
 *
 * Stored with the source in DataStore (not credentials). All rules
 * default off / show everything, so existing behavior is unchanged
 * until the user configures a source.
 */
@Serializable
data class RemoteDisplayFormat(
    /** Remove "[...]" segments from the displayed title. */
    val stripBrackets: Boolean = false,
    /** Remove short "(...)" segments from the displayed title (see below). */
    val stripParens: Boolean = false,
    /** Turn underscores into spaces in the displayed title. */
    val underscoresToSpaces: Boolean = false,
    /** Literal find → replace pairs, applied in order. */
    val replacements: List<FindReplace> = emptyList(),
    /** Show the file size under the title, when the server provided one. */
    val showSize: Boolean = true,
    /** File extensions (without the dot) to hide from the browser. */
    val hideExtensions: List<String> = emptyList()
)

/** A title after formatting: the clean title, plus the file extension kept apart. */
data class FormattedRemoteTitle(val title: String, val extension: String?)

/**
 * Parenthesized segments longer than this are treated as part of the
 * title, not a tag: "(USA)" and "(v1.2)" are tags, "(The Legend
 * Continues)" is a name. Bracketed segments have no such guard —
 * in these listings "[...]" is a tag (a hash, a version code, a
 * format label) far more often than a name.
 */
const val PAREN_TAG_MAX_LENGTH = 24

private val BRACKET_SEGMENT = Regex("""\[[^\]]*\]""")
private val PAREN_SEGMENT = Regex("""\(([^)]*)\)""")
private val MULTI_SPACE = Regex("""\s+""")

/** Split a file name into its stem and its extension (without the dot), or null. */
fun splitRemoteExtension(name: String): Pair<String, String?> {
    val dot = name.lastIndexOf('.')
    // A leading dot is a hidden file, not an extension; a trailing dot has no extension.
    if (dot <= 0 || dot == name.length - 1) return name to null
    return name.substring(0, dot) to name.substring(dot + 1)
}

/**
 * Format one parsed entry's name for display, in this order:
 *  1. strip "[...]" segments (when on)
 *  2. strip short "(...)" segments (when on; long ones are kept as part of the title)
 *  3. underscores → spaces (when on)
 *  4. literal find → replace pairs, in order
 *  5. collapse runs of whitespace and trim
 * The extension is split off first and returned apart, so it is never
 * eaten by a rule and can be shown as its own chip. Folders keep their
 * full name as the title (no extension chip). When the rules leave
 * nothing (e.g. a name that was only "[v1]"), the original stem is
 * kept so a row never renders blank.
 */
fun formatRemoteTitle(name: String, isFolder: Boolean, format: RemoteDisplayFormat): FormattedRemoteTitle {
    val (stem, extension) = if (isFolder) name to null else splitRemoteExtension(name)
    var title = stem
    if (format.stripBrackets) {
        title = BRACKET_SEGMENT.replace(title, "")
    }
    if (format.stripParens) {
        title = PAREN_SEGMENT.replace(title) { match ->
            if (match.groupValues[1].length <= PAREN_TAG_MAX_LENGTH) "" else match.value
        }
    }
    if (format.underscoresToSpaces) {
        title = title.replace('_', ' ')
    }
    for ((find, replace) in format.replacements) {
        if (find.isNotEmpty()) {
            title = title.replace(find, replace)
        }
    }
    title = MULTI_SPACE.replace(title.trim(), " ")
    if (title.isEmpty()) title = stem.trim()
    return FormattedRemoteTitle(title = title, extension = extension)
}

/** Normalize a stored hide list: lowercase, no leading dot, blank entries dropped. */
fun normalizeHiddenExtensions(extensions: List<String>): List<String> =
    extensions.map { it.trim().trimStart('.').lowercase() }
        .filter { it.isNotEmpty() }
        .distinct()

/** Parse the comma-separated hide-types text from the editor into a normalized list. */
fun parseHiddenExtensions(raw: String): List<String> =
    normalizeHiddenExtensions(raw.split(','))

/**
 * Whether a parsed entry is shown in the browser under this format.
 * Display filtering only — the parser still reads the entry, and the
 * download still uses the real filename. Folders are never hidden:
 * a hide list names file types, and hiding a folder would strand the
 * files inside it.
 */
fun shouldShowRemoteEntry(entry: RemoteEntry, format: RemoteDisplayFormat): Boolean {
    if (entry.isFolder) return true
    val hidden = normalizeHiddenExtensions(format.hideExtensions)
    if (hidden.isEmpty()) return true
    val extension = splitRemoteExtension(entry.name).second?.lowercase() ?: return true
    return extension !in hidden
}
