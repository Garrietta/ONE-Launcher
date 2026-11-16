package dev.one.launcher.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

val Context.starCacheDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "github_stars"
)

/**
 * Persistent cache of GitHub star counts (repo "owner/name" ->
 * [StarEntry]), as JSON in DataStore. The cache is what keeps the
 * star ranking cheap: a game page reuses a fresh entry (~7 day TTL,
 * see [isStarFresh]) instead of re-hitting the unauthenticated
 * GitHub API (60 requests/hour). A corrupt or missing cache just
 * reads as empty — the caller falls back to download counts.
 */
class StarCacheStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    private val starsSerializer =
        MapSerializer(String.serializer(), StarEntry.serializer())

    private val starsJsonKey = stringPreferencesKey("stars_json")

    suspend fun load(): Map<String, StarEntry> =
        context.starCacheDataStore.data.map { prefs ->
            decode(prefs[starsJsonKey])
        }.first()

    suspend fun save(entries: Map<String, StarEntry>) {
        context.starCacheDataStore.edit { prefs ->
            prefs[starsJsonKey] = json.encodeToString(starsSerializer, entries)
        }
    }

    private fun decode(raw: String?): Map<String, StarEntry> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            json.decodeFromString(starsSerializer, raw)
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
