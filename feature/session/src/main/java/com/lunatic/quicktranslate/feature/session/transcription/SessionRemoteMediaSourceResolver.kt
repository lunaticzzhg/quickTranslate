package com.lunatic.quicktranslate.feature.session.transcription

import android.net.Uri
import com.lunatic.quicktranslate.domain.project.model.PlatformLinkResolveResult
import com.lunatic.quicktranslate.domain.project.model.PlatformLinkResolvedItem
import com.lunatic.quicktranslate.domain.project.usecase.ResolvePlatformLinkUseCase

enum class SessionRemoteDownloadStrategy {
    LOCAL_PASSTHROUGH,
    YTDLP_YOUTUBE,
    YTDLP_GENERIC,
    HTTP_DIRECT
}

data class SessionResolvedRemoteMediaSource(
    val strategy: SessionRemoteDownloadStrategy,
    val sourceUrl: String,
    val downloadUrl: String,
    val host: String,
    val platformLabel: String? = null
)

class SessionRemoteMediaSourceResolver(
    private val resolvePlatformLinkUseCase: ResolvePlatformLinkUseCase
) {
    suspend fun resolve(mediaUri: String): SessionResolvedRemoteMediaSource {
        val uri = Uri.parse(mediaUri)
        val scheme = uri.scheme?.lowercase().orEmpty()
        if (scheme != "http" && scheme != "https") {
            return SessionResolvedRemoteMediaSource(
                strategy = SessionRemoteDownloadStrategy.LOCAL_PASSTHROUGH,
                sourceUrl = mediaUri,
                downloadUrl = mediaUri,
                host = uri.host?.lowercase().orEmpty()
            )
        }

        val host = uri.host?.lowercase().orEmpty()
        if (isYouTubeHost(host)) {
            return SessionResolvedRemoteMediaSource(
                strategy = SessionRemoteDownloadStrategy.YTDLP_YOUTUBE,
                sourceUrl = mediaUri,
                downloadUrl = mediaUri,
                host = host,
                platformLabel = "youtube"
            )
        }
        if (isBilibiliHost(host) || isDouyinHost(host)) {
            return SessionResolvedRemoteMediaSource(
                strategy = SessionRemoteDownloadStrategy.YTDLP_GENERIC,
                sourceUrl = mediaUri,
                downloadUrl = mediaUri,
                host = host,
                platformLabel = when {
                    isBilibiliHost(host) -> "bilibili"
                    isDouyinHost(host) -> "douyin"
                    else -> host
                }
            )
        }

        val resolvedDownloadUrl = resolveDownloadUrl(mediaUri)
        val resolvedHost = Uri.parse(resolvedDownloadUrl).host?.lowercase().orEmpty()
        return SessionResolvedRemoteMediaSource(
            strategy = SessionRemoteDownloadStrategy.HTTP_DIRECT,
            sourceUrl = mediaUri,
            downloadUrl = resolvedDownloadUrl,
            host = resolvedHost
        )
    }

    private suspend fun resolveDownloadUrl(mediaUri: String): String {
        val uri = Uri.parse(mediaUri)
        val host = uri.host?.lowercase().orEmpty()
        if (!isPlatformPageHost(host)) {
            return mediaUri
        }
        return when (val result = resolvePlatformLinkUseCase(mediaUri)) {
            is PlatformLinkResolveResult.Success -> {
                selectPreferredCandidate(result.media.items)?.resolvedMediaUrl ?: mediaUri
            }

            is PlatformLinkResolveResult.Failure -> mediaUri
        }
    }

    private fun selectPreferredCandidate(
        items: List<PlatformLinkResolvedItem>
    ): PlatformLinkResolvedItem? {
        if (items.isEmpty()) {
            return null
        }
        return items.firstOrNull { it.id.startsWith("durl_") }
            ?: items.firstOrNull { it.mimeType?.startsWith("audio/") == true }
            ?: items.first()
    }

    private fun isPlatformPageHost(host: String): Boolean {
        if (host.isBlank()) return false
        return host.contains("bilibili.com") ||
            host == "b23.tv" ||
            host.contains("youtube.com") ||
            host == "youtu.be" ||
            host.contains("douyin.com")
    }

    private fun isYouTubeHost(host: String): Boolean {
        return host.contains("youtube.com") || host == "youtu.be"
    }

    private fun isBilibiliHost(host: String): Boolean {
        return host.contains("bilibili.com") || host == "b23.tv"
    }

    private fun isDouyinHost(host: String): Boolean {
        return host.contains("douyin.com") || host.contains("iesdouyin.com")
    }
}
