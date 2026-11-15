package dev.one.launcher.data

import dev.one.launcher.model.Port

/**
 * Conservative matcher between a file name and a port entry.
 *
 * A file matches when its extension is one the port accepts AND its name
 * contains one of the port's filename hints (or matches the port's regex
 * when one is present). Hints are deliberately short, so an ambiguous
 * name can match more than one port — the detail screen lets the user
 * assign it to the right port. This never deletes or moves user files.
 */
object PortMatcher {
    fun matches(fileName: String, port: Port): Boolean {
        val lower = fileName.lowercase()
        val extensionOk = port.fileExtensions.any { lower.endsWith(it.lowercase()) }
        if (!extensionOk) return false
        if (port.fileNameHints.isEmpty() && port.fileNameRegex == null) return true
        if (port.fileNameHints.any { hint -> lower.contains(hint.lowercase()) }) return true
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

    fun matchingPorts(fileName: String, ports: List<Port>): List<Port> =
        ports.filter { matches(fileName, it) }
}
