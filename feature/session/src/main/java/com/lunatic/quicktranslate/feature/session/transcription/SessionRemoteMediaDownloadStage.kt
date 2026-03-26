package com.lunatic.quicktranslate.feature.session.transcription

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class DownloadedTranscriptionMedia(
    val localPath: String,
    val mimeType: String?,
    val downloadedFromRemote: Boolean
)

class SessionRemoteMediaDownloadStage(
    private val appContext: Context,
    private val okHttpClient: OkHttpClient
) {
    suspend fun ensureLocalMedia(
        projectId: Long,
        mediaUri: String
    ): DownloadedTranscriptionMedia {
        return withContext(Dispatchers.IO) {
            val uri = Uri.parse(mediaUri)
            val scheme = uri.scheme?.lowercase().orEmpty()
            if (scheme != "http" && scheme != "https") {
                return@withContext DownloadedTranscriptionMedia(
                    localPath = mediaUri,
                    mimeType = null,
                    downloadedFromRemote = false
                )
            }

            val request = Request.Builder()
                .url(mediaUri)
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            response.use { networkResponse ->
                if (!networkResponse.isSuccessful) {
                    throw IOException("Download failed with code ${networkResponse.code}.")
                }
                val body = networkResponse.body ?: throw IOException("Download body is empty.")
                val contentType = body.contentType()?.toString()
                val outputDirectory = File(
                    appContext.filesDir,
                    "projects/$projectId/media"
                )
                if (!outputDirectory.exists()) {
                    outputDirectory.mkdirs()
                }
                val extension = resolveExtension(uri = uri, contentType = contentType)
                val targetFile = File(outputDirectory, "source$extension")
                val tempFile = File(outputDirectory, "source.download")
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                if (tempFile.exists()) {
                    tempFile.delete()
                }
                body.byteStream().use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                if (!tempFile.renameTo(targetFile)) {
                    throw IOException("Unable to move downloaded file into project directory.")
                }
                return@withContext DownloadedTranscriptionMedia(
                    localPath = targetFile.absolutePath,
                    mimeType = contentType,
                    downloadedFromRemote = true
                )
            }
        }
    }

    private fun resolveExtension(uri: Uri, contentType: String?): String {
        val path = uri.path.orEmpty()
        val suffix = path.substringAfterLast('.', "")
            .lowercase()
            .takeIf { it.isNotBlank() && it.length <= 8 }
        if (suffix != null) {
            return ".$suffix"
        }
        return when {
            contentType?.startsWith("audio/") == true -> ".m4a"
            contentType?.startsWith("video/") == true -> ".mp4"
            else -> ".media"
        }
    }
}
