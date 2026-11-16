package dev.one.launcher.data

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

sealed class DownloadState {
    data class Progress(val bytesRead: Long, val totalBytes: Long) : DownloadState()
    data class Done(val file: File) : DownloadState()
    data class Error(val message: String) : DownloadState()
}

/**
 * Downloads a file into the app's own Downloads/ONE folder. Two sources
 * only: a file the user already owns in their Premiumize cloud, via the
 * `link` returned by /item/details; or a release APK the user explicitly
 * tapped Download on, from the port project's own GitHub release.
 * Nothing else is ever downloaded.
 */
class DownloadRepository(private val context: Context) {
    private val client = OkHttpClient()

    fun downloadsDir(): File {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "ONE")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun listDownloadedFiles(): List<File> = downloadsDir().listFiles()?.toList().orEmpty()

    fun download(url: String, fileName: String): Flow<DownloadState> = flow {
        val target = File(downloadsDir(), fileName)
        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    emit(DownloadState.Error("Download failed: HTTP ${response.code}"))
                    return@flow
                }
                val body = response.body
                if (body == null) {
                    emit(DownloadState.Error("Empty download response"))
                    return@flow
                }
                val total = body.contentLength()
                var readSoFar = 0L
                body.byteStream().use { input ->
                    FileOutputStream(target).use { output ->
                        // 64 KB buffer, and progress is throttled
                        // (v1.8.7): emitting Progress on every 8 KB
                        // read was ~850,000 StateFlow updates for a
                        // 6.5 GB file, flooding the main thread with
                        // recompositions. Now at most one emission
                        // per 100 ms, or when the whole-percent
                        // progress changes — the UI still moves
                        // smoothly, without the stutter.
                        val buffer = ByteArray(64 * 1024)
                        var read: Int
                        var lastEmitMs = 0L
                        var lastPercent = -1
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            readSoFar += read
                            if (shouldEmitProgress(
                                    readSoFar, total, lastEmitMs, lastPercent
                                )
                            ) {
                                emit(DownloadState.Progress(readSoFar, total))
                                lastEmitMs = System.currentTimeMillis()
                                lastPercent = progressPercent(readSoFar, total)
                            }
                        }
                        // The final state is always emitted, so the
                        // UI never sticks at 99%.
                        if (lastPercent != progressPercent(readSoFar, total)) {
                            emit(DownloadState.Progress(readSoFar, total))
                        }
                    }
                }
                emit(DownloadState.Done(target))
            }
        } catch (e: Exception) {
            emit(DownloadState.Error(e.message ?: "Download failed"))
        }
    }.flowOn(Dispatchers.IO)
}

/** Whole-percent progress (0-100), or -1 when the total is unknown. */
private fun progressPercent(bytesRead: Long, totalBytes: Long): Int {
    if (totalBytes <= 0) return -1
    return ((bytesRead * 100) / totalBytes).toInt().coerceIn(0, 100)
}

/**
 * Whether a Progress emission is due (v1.8.7): at most once per
 * [PROGRESS_EMIT_INTERVAL_MS], or when the whole-percent progress
 * changed. Pure, so the throttle is unit-testable without a download.
 * [nowMs] is injected for tests (defaults to the wall clock). When
 * the total is unknown the percent never changes, so the time rule
 * alone paces the emissions.
 */
const val PROGRESS_EMIT_INTERVAL_MS: Long = 100L

fun shouldEmitProgress(
    bytesRead: Long,
    totalBytes: Long,
    lastEmitMs: Long,
    lastPercent: Int,
    nowMs: Long = System.currentTimeMillis()
): Boolean {
    if (nowMs - lastEmitMs >= PROGRESS_EMIT_INTERVAL_MS) return true
    val percent = progressPercent(bytesRead, totalBytes)
    return percent >= 0 && percent != lastPercent
}
