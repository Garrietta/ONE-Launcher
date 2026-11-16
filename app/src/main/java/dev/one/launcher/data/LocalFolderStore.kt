package dev.one.launcher.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

val Context.localFolderDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "local_folder"
)

/** The user's chosen local game-files folder (a SAF tree URI). */
data class LocalFolder(val uri: String, val displayName: String)

/**
 * Persists the local game-files folder choice and the detected
 * file/port pairs found in it, as JSON in DataStore. The cache is what
 * makes badges show instantly on a cold start: the ViewModel loads it
 * into state before the background re-scan replaces it with fresh
 * results. A stale cache (folder unavailable) is kept, never dropped —
 * clearing it is only done when the user clears the folder itself.
 */
class LocalFolderStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    // Explicit serializer: the reified encodeToString/decodeFromString
    // overloads do not resolve for a Map value here.
    private val detectedSerializer =
        MapSerializer(String.serializer(), ListSerializer(DetectedFile.serializer()))

    private val folderUriKey = stringPreferencesKey("folder_uri")
    private val folderNameKey = stringPreferencesKey("folder_name")
    private val detectedJsonKey = stringPreferencesKey("detected_json")

    val folder: Flow<LocalFolder?> =
        context.localFolderDataStore.data.map { prefs ->
            val uri = prefs[folderUriKey] ?: return@map null
            LocalFolder(uri = uri, displayName = prefs[folderNameKey] ?: uri)
        }

    val cachedDetected: Flow<Map<String, List<DetectedFile>>> =
        context.localFolderDataStore.data.map { prefs ->
            decodeDetected(prefs[detectedJsonKey])
        }

    suspend fun loadFolder(): LocalFolder? = folder.first()

    suspend fun loadCachedDetected(): Map<String, List<DetectedFile>> =
        cachedDetected.first()

    suspend fun saveFolder(folder: LocalFolder?) {
        context.localFolderDataStore.edit { prefs ->
            if (folder == null) {
                prefs.remove(folderUriKey)
                prefs.remove(folderNameKey)
            } else {
                prefs[folderUriKey] = folder.uri
                prefs[folderNameKey] = folder.displayName
            }
        }
    }

    suspend fun saveDetected(detected: Map<String, List<DetectedFile>>) {
        context.localFolderDataStore.edit { prefs ->
            prefs[detectedJsonKey] = json.encodeToString(detectedSerializer, detected)
        }
    }

    suspend fun clearDetected() {
        context.localFolderDataStore.edit { prefs ->
            prefs.remove(detectedJsonKey)
        }
    }

    private fun decodeDetected(raw: String?): Map<String, List<DetectedFile>> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            json.decodeFromString(detectedSerializer, raw)
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
