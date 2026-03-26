package com.lunatic.quicktranslate.feature.session.transcription

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

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
        mediaUri: String,
        onProgress: ((Int) -> Unit)? = null
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
                .applyRequestHeadersForHost(uri.host?.lowercase().orEmpty())
                .build()

            val firstResponse = okHttpClient.newCall(request).execute()
            if (firstResponse.code == 403) {
                firstResponse.close()
                val retryRequest = Request.Builder()
                    .url(mediaUri)
                    .get()
                    .applyRequestHeadersForHost(uri.host?.lowercase().orEmpty())
                    .header("Range", "bytes=0-")
                    .build()
                return@withContext executeDownloadRequest(
                    response = okHttpClient.newCall(retryRequest).execute(),
                    projectId = projectId,
                    uri = uri,
                    onProgress = onProgress
                )
            }
            return@withContext executeDownloadRequest(
                response = firstResponse,
                projectId = projectId,
                uri = uri,
                onProgress = onProgress
            )
        }
    }

    private fun executeDownloadRequest(
        response: Response,
        projectId: Long,
        uri: Uri,
        onProgress: ((Int) -> Unit)?
    ): DownloadedTranscriptionMedia {
        response.use { networkResponse ->
            if (!networkResponse.isSuccessful) {
                throw IOException("Download failed with code ${networkResponse.code}.")
            }
            val body = networkResponse.body ?: throw IOException("Download body is empty.")
            val contentType = body.contentType()?.toString()
            val totalBytes = body.contentLength().takeIf { it > 0L }
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
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var bytesCopied = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) {
                            break
                        }
                        output.write(buffer, 0, read)
                        bytesCopied += read
                        if (totalBytes != null && onProgress != null) {
                            val percent = ((bytesCopied * 100L) / totalBytes)
                                .toInt()
                                .coerceIn(0, 100)
                            onProgress(percent)
                        }
                    }
                }
            }
            if (!tempFile.renameTo(targetFile)) {
                throw IOException("Unable to move downloaded file into project directory.")
            }
            return DownloadedTranscriptionMedia(
                localPath = targetFile.absolutePath,
                mimeType = contentType,
                downloadedFromRemote = true
            )
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

    private fun Request.Builder.applyRequestHeadersForHost(host: String): Request.Builder {
        header("User-Agent", DEFAULT_USER_AGENT)
        header("Accept", "*/*")
        if (host.contains("bilivideo.com") || host.contains("bilibili.com")) {
            header("Referer", "https://www.bilibili.com/")
            header("Origin", "https://www.bilibili.com")
        } else if (host.contains("douyin.com") || host.contains("bytecdn.cn")) {
            header("Referer", "https://www.douyin.com/")
            header("Origin", "https://www.douyin.com")
        }
        return this
    }

    companion object {
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
    }
}
