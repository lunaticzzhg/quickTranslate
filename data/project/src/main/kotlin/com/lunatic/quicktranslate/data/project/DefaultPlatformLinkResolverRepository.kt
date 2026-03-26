package com.lunatic.quicktranslate.data.project

import com.lunatic.quicktranslate.domain.project.model.PlatformLinkResolveFailure
import com.lunatic.quicktranslate.domain.project.model.PlatformLinkResolveFailureType
import com.lunatic.quicktranslate.domain.project.model.PlatformLinkResolveResult
import com.lunatic.quicktranslate.domain.project.model.PlatformLinkResolvedMedia
import com.lunatic.quicktranslate.domain.project.repository.PlatformLinkResolverRepository
import java.net.URI

class DefaultPlatformLinkResolverRepository : PlatformLinkResolverRepository {
    override suspend fun resolve(sourceUrl: String): PlatformLinkResolveResult {
        val normalized = sourceUrl.trim()
        val parsedUri = runCatching { URI(normalized) }.getOrNull()
        val host = parsedUri?.host?.lowercase().orEmpty()

        if (host.isBlank()) {
            return PlatformLinkResolveResult.Failure(
                PlatformLinkResolveFailure(
                    type = PlatformLinkResolveFailureType.EXTRACT_FAILED,
                    message = "Invalid link host."
                )
            )
        }

        if (isLikelyDirectMediaLink(normalized)) {
            return PlatformLinkResolveResult.Success(
                PlatformLinkResolvedMedia(
                    requestUrl = normalized,
                    resolvedMediaUrl = normalized,
                    sourceHost = host,
                    isDirectMedia = true
                )
            )
        }

        if (host in SUPPORTED_PLATFORM_HOSTS) {
            return PlatformLinkResolveResult.Failure(
                PlatformLinkResolveFailure(
                    type = PlatformLinkResolveFailureType.EXTRACT_FAILED,
                    message = "Platform link is recognized but resolver is not implemented yet."
                )
            )
        }

        return PlatformLinkResolveResult.Failure(
            PlatformLinkResolveFailure(
                type = PlatformLinkResolveFailureType.UNSUPPORTED_SITE,
                message = "Unsupported site: $host"
            )
        )
    }

    private fun isLikelyDirectMediaLink(url: String): Boolean {
        val sanitized = url.substringBefore('#').substringBefore('?').lowercase()
        return DIRECT_MEDIA_SUFFIXES.any { suffix -> sanitized.endsWith(suffix) }
    }

    companion object {
        private val DIRECT_MEDIA_SUFFIXES = listOf(
            ".mp3",
            ".m4a",
            ".aac",
            ".wav",
            ".flac",
            ".ogg",
            ".opus",
            ".mp4",
            ".m4v",
            ".mov",
            ".mkv",
            ".webm",
            ".3gp"
        )

        private val SUPPORTED_PLATFORM_HOSTS = setOf(
            "www.bilibili.com",
            "m.bilibili.com",
            "b23.tv",
            "www.douyin.com",
            "v.douyin.com",
            "www.iesdouyin.com"
        )
    }
}

