package dev.one.launcher.data

import java.io.File
import java.security.MessageDigest

/** Lowercase hex SHA-256 of a file, streamed so large APKs stay cheap. */
fun sha256Hex(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(8192)
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

/** True when [expected] (hex, case-insensitive) is the SHA-256 of [file]. */
fun sha256Matches(file: File, expected: String): Boolean {
    if (expected.isBlank()) return false
    return try {
        sha256Hex(file).equals(expected.trim(), ignoreCase = true)
    } catch (e: Exception) {
        false
    }
}
