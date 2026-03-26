package com.lunatic.quicktranslate.feature.session.transcription

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DownloadedTranscriptionMedia(
    val localPath: String,
    val mimeType: String?,
    val downloadedFromRemote: Boolean
)

class SessionRemoteMediaDownloadStage(
    private val sourceResolver: SessionRemoteMediaSourceResolver,
    private val ytDlpMediaDownloader: SessionYtDlpMediaDownloader,
    private val directHttpMediaDownloader: SessionDirectHttpMediaDownloader
) {
    suspend fun ensureLocalMedia(
        projectId: Long,
        mediaUri: String,
        onProgress: ((Int) -> Unit)? = null
    ): DownloadedTranscriptionMedia {
        return withContext(Dispatchers.IO) {
            val source = sourceResolver.resolve(mediaUri)
            when (source.strategy) {
                SessionRemoteDownloadStrategy.LOCAL_PASSTHROUGH -> DownloadedTranscriptionMedia(
                    localPath = mediaUri,
                    mimeType = null,
                    downloadedFromRemote = false
                )

                SessionRemoteDownloadStrategy.YTDLP_YOUTUBE -> ytDlpMediaDownloader.downloadYouTube(
                    projectId = projectId,
                    sourceUrl = source.sourceUrl,
                    onProgress = onProgress
                )

                SessionRemoteDownloadStrategy.YTDLP_GENERIC -> ytDlpMediaDownloader.downloadGeneric(
                    projectId = projectId,
                    sourceUrl = source.sourceUrl,
                    platformLabel = source.platformLabel ?: source.host,
                    onProgress = onProgress
                )

                SessionRemoteDownloadStrategy.HTTP_DIRECT -> directHttpMediaDownloader.download(
                    projectId = projectId,
                    sourceUrl = source.downloadUrl,
                    host = source.host,
                    onProgress = onProgress
                )
            }
        }
    }

    companion object {
        fun create(
            sourceResolver: SessionRemoteMediaSourceResolver,
            ytDlpMediaDownloader: SessionYtDlpMediaDownloader,
            directHttpMediaDownloader: SessionDirectHttpMediaDownloader
        ): SessionRemoteMediaDownloadStage {
            return SessionRemoteMediaDownloadStage(
                sourceResolver = sourceResolver,
                ytDlpMediaDownloader = ytDlpMediaDownloader,
                directHttpMediaDownloader = directHttpMediaDownloader
            )
        }
    }
}
