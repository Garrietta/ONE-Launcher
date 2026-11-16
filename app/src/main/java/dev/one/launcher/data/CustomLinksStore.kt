package dev.one.launcher.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class CustomLink(val name: String, val url: String)

/**
 * User-entered shortcuts to the user's own pages, stored only on this
 * device in DataStore Preferences as a JSON list. Never synced, never in
 * the repo, never logged. ONE only opens these in the external browser;
 * it never downloads, fetches, or matches anything from them.
 */
class CustomLinksStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val key = stringPreferencesKey("custom_links_json")

    val links: Flow<List<CustomLink>> = context.portSettingsDataStore.data.map { prefs ->
        decode(prefs[key])
    }

    private fun decode(raw: String?): List<CustomLink> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString<List<CustomLink>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun add(link: CustomLink) {
        context.portSettingsDataStore.edit { prefs ->
            prefs[key] = json.encodeToString(decode(prefs[key]) + link)
        }
    }

    suspend fun remove(link: CustomLink) {
        context.portSettingsDataStore.edit { prefs ->
            prefs[key] = json.encodeToString(decode(prefs[key]) - link)
        }
    }
}

/**
 * Normalize a user-entered link URL. Trims whitespace, prepends https://
 * when no scheme is present, and accepts http/https only. Returns the
 * normalized URL, or null when the input is blank, has no host, or uses
 * any other scheme (ftp, javascript, ...). Pure function — unit-tested.
 */
fun normalizeCustomLinkUrl(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    // A "scheme:" prefix that is not http(s) is rejected outright — this
    // keeps javascript:, ftp:, and friends out even though prepending
    // "https://" would otherwise disguise them. A "host:port" shape is
    // not a scheme: the part after the colon is all digits.
    val colon = trimmed.indexOf(':')
    if (colon > 0) {
        val slash = trimmed.indexOf('/')
        val beforeSlash = if (slash >= 0) trimmed.substring(0, slash) else trimmed
        if (beforeSlash.contains(':')) {
            val prefix = trimmed.substring(0, colon)
            val after = trimmed.substring(colon + 1).substringBefore('/')
            val isPort = after.isNotEmpty() && after.all { it.isDigit() }
            if (!isPort &&
                !prefix.equals("http", ignoreCase = true) &&
                !prefix.equals("https", ignoreCase = true)
            ) {
                return null
            }
        }
    }
    // Try as typed first, then with https:// prepended — but only when
    // the input has no "://" already, so a broken "https://" can never
    // turn into a bogus "https://https://".
    val candidates = if (trimmed.contains("://")) {
        listOf(trimmed)
    } else {
        listOf(trimmed, "https://$trimmed")
    }
    for (candidate in candidates) {
        val normalized = tryParseHttpUrl(candidate)
        if (normalized != null) return normalized
    }
    return null
}

private fun tryParseHttpUrl(candidate: String): String? {
    return try {
        val uri = java.net.URI(candidate)
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (uri.host.isNullOrBlank()) return null
        uri.toString()
    } catch (e: Exception) {
        null
    }
}
