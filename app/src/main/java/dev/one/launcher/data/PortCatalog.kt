package dev.one.launcher.data

import android.content.Context
import dev.one.launcher.model.PortCatalog
import kotlinx.serialization.json.Json

class PortCatalogRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): PortCatalog {
        val text = context.assets.open("ports.json").bufferedReader().use { it.readText() }
        return json.decodeFromString(PortCatalog.serializer(), text)
    }
}
