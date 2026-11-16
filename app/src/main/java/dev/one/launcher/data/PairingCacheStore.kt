package dev.one.launcher.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

val Context.pairingCacheDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "pairing_cache"
)

/**
 * Persistent pairing cache (v1.8.2): the computed file↔game pairings
 * as JSON in DataStore. The cache is what makes the Library open
 * pre-populated: the ViewModel loads it into state before the
 * background refresh replaces it with fresh results. A corrupt,
 * missing, or wrong-version cache just reads as null — the caller
 * falls back to computing the pairings live, and the background
 * refresh writes a fresh cache. Local-only, never synced.
 */
class PairingCacheStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    private val cacheJsonKey = stringPreferencesKey("pairings_json")

    suspend fun load(): PairingCache? =
        context.pairingCacheDataStore.data.map { prefs ->
            decode(prefs[cacheJsonKey])
        }.first()

    suspend fun save(cache: PairingCache) {
        context.pairingCacheDataStore.edit { prefs ->
            prefs[cacheJsonKey] = json.encodeToString(PairingCache.serializer(), cache)
        }
    }

    suspend fun clear() {
        context.pairingCacheDataStore.edit { prefs ->
            prefs.remove(cacheJsonKey)
        }
    }

    private fun decode(raw: String?): PairingCache? {
        if (raw.isNullOrBlank()) return null
        return try {
            val cache = json.decodeFromString(PairingCache.serializer(), raw)
            // A cache written under old matcher rules is stale by
            // definition — ignore it and let the matching re-run.
            if (isPairingCacheUsable(cache)) cache else null
        } catch (e: Exception) {
            null
        }
    }
}
