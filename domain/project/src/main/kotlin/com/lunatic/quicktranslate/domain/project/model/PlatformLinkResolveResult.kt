package com.lunatic.quicktranslate.domain.project.model

data class PlatformLinkResolvedMedia(
    val requestUrl: String,
    val resolvedMediaUrl: String,
    val sourceHost: String,
    val isDirectMedia: Boolean
)

data class PlatformLinkResolveFailure(
    val type: PlatformLinkResolveFailureType,
    val message: String
)

sealed interface PlatformLinkResolveResult {
    data class Success(val media: PlatformLinkResolvedMedia) : PlatformLinkResolveResult
    data class Failure(val error: PlatformLinkResolveFailure) : PlatformLinkResolveResult
}

enum class PlatformLinkResolveFailureType {
    UNSUPPORTED_SITE,
    LOGIN_REQUIRED,
    REGION_RESTRICTED,
    DRM_PROTECTED,
    EXTRACT_FAILED
}

