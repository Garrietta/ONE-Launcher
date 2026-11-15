package dev.one.launcher.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.portSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "port_settings"
)

/**
 * Per-port preferences. These are stored wishes for the port (resolution
 * scale, FPS cap, touch-layout note) — ONE does not enforce settings
 * inside the port app itself.
 */
data class PortSettings(
    val fpsCap: String = "Uncapped",
    val resolutionScale: String = "1.0x",
    val touchLayoutNote: String = ""
)

class PortSettingsStore(private val context: Context) {
    fun settings(portId: String): Flow<PortSettings> =
        context.portSettingsDataStore.data.map { prefs ->
            PortSettings(
                fpsCap = prefs[stringPreferencesKey("${portId}_fps")] ?: "Uncapped",
                resolutionScale = prefs[stringPreferencesKey("${portId}_scale")] ?: "1.0x",
                touchLayoutNote = prefs[stringPreferencesKey("${portId}_touch")] ?: ""
            )
        }

    suspend fun save(portId: String, settings: PortSettings) {
        context.portSettingsDataStore.edit { prefs ->
            prefs[stringPreferencesKey("${portId}_fps")] = settings.fpsCap
            prefs[stringPreferencesKey("${portId}_scale")] = settings.resolutionScale
            prefs[stringPreferencesKey("${portId}_touch")] = settings.touchLayoutNote
        }
    }

    suspend fun clearAll() {
        context.portSettingsDataStore.edit { it.clear() }
    }
}
