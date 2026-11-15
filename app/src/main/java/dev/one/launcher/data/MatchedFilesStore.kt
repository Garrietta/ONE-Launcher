package dev.one.launcher.data

import android.content.Context
import android.content.SharedPreferences

data class MatchedFile(val name: String, val uri: String? = null)

/**
 * Remembers which of the user's own files they assigned to which port.
 * Stores only the file name and the SAF URI string — never file contents.
 */
class MatchedFilesStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("one_matched_files", Context.MODE_PRIVATE)

    fun getAssigned(portId: String): MatchedFile? {
        val name = prefs.getString("${portId}_name", null) ?: return null
        return MatchedFile(name = name, uri = prefs.getString("${portId}_uri", null))
    }

    fun assign(portId: String, file: MatchedFile) {
        prefs.edit()
            .putString("${portId}_name", file.name)
            .putString("${portId}_uri", file.uri)
            .apply()
    }

    fun clear(portId: String) {
        prefs.edit()
            .remove("${portId}_name")
            .remove("${portId}_uri")
            .apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    fun all(): Map<String, MatchedFile> {
        val result = mutableMapOf<String, MatchedFile>()
        for ((key, value) in prefs.all) {
            if (key.endsWith("_name") && value is String) {
                val portId = key.removeSuffix("_name")
                val uri = prefs.getString("${portId}_uri", null)
                result[portId] = MatchedFile(name = value, uri = uri)
            }
        }
        return result
    }
}
