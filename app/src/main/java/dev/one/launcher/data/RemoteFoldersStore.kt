package dev.one.launcher.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class RemoteFolderSource(val id: String, val name: String, val url: String)

/**
 * User-configured remote folder sources, stored only on this device in
 * DataStore Preferences as a JSON list. Never synced, never in the repo,
 * never logged, nothing prefilled. A source is just a name + URL the
 * user typed; the app fetches it only when the user opens it, and the
 * entries it returns are never merged into the Library or any port
 * matching.
 */
class RemoteFoldersStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val key = stringPreferencesKey("remote_folders_json")

    val sources: Flow<List<RemoteFolderSource>> =
        context.portSettingsDataStore.data.map { prefs -> decode(prefs[key]) }

    private fun decode(raw: String?): List<RemoteFolderSource> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString<List<RemoteFolderSource>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun add(source: RemoteFolderSource) {
        context.portSettingsDataStore.edit { prefs ->
            prefs[key] = json.encodeToString(decode(prefs[key]) + source)
        }
    }

    suspend fun remove(source: RemoteFolderSource) {
        context.portSettingsDataStore.edit { prefs ->
            prefs[key] = json.encodeToString(decode(prefs[key]) - source)
        }
    }
}

/**
 * Optional HTTP Basic auth credentials per remote folder source, held
 * in EncryptedSharedPreferences keyed by source id. Never logged, never
 * written to a plain file, never committed. Cleared when the source is
 * removed.
 */
class RemoteFolderCredentials(context: Context) {
    private val prefs = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "one_remote_folders",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun load(sourceId: String): Pair<String, String>? {
        val username = prefs.getString("remote_${sourceId}_username", null) ?: return null
        val password = prefs.getString("remote_${sourceId}_password", null) ?: return null
        return username to password
    }

    fun save(sourceId: String, username: String, password: String) {
        prefs.edit()
            .putString("remote_${sourceId}_username", username)
            .putString("remote_${sourceId}_password", password)
            .apply()
    }

    fun clear(sourceId: String) {
        prefs.edit()
            .remove("remote_${sourceId}_username")
            .remove("remote_${sourceId}_password")
            .apply()
    }
}
