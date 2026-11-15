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
 * Downloads a file the user already owns in their Premiumize cloud, via
 * the `link` returned by /item/details, into the app's own Downloads/ONE
 * folder. Nothing is downloaded from anywhere else.
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
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            readSoFar += read
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
