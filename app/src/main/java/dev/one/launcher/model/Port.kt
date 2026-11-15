package dev.one.launcher.model

import kotlinx.serialization.Serializable

@Serializable
data class Port(
    val id: String,
    val title: String,
    val game: String,
    val system: String,
    val type: String,
    val repo: String,
    val releasesUrl: String,
    val packageNames: List<String> = emptyList(),
    val fileExtensions: List<String> = emptyList(),
    val fileNameHints: List<String> = emptyList(),
    val fileNameRegex: String? = null,
    val requiredDump: String,
    val minAndroid: Int = 24,
    val notes: String = ""
)

@Serializable
data class PortCatalog(
    val catalogNote: String = "",
    val ports: List<Port> = emptyList()
)
