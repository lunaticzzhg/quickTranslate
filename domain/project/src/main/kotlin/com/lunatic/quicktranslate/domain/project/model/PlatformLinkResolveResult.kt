package com.lunatic.quicktranslate.domain.project.model

data class PlatformLinkResolvedItem(
    val id: String,
    val label: String,
    val resolvedMediaUrl: String,
    val mimeType: String?,
    val estimatedBytes: Long?
)

data class PlatformLinkResolvedMedia(
    val requestUrl: String,
    val suggestedProjectName: String,
    val items: List<PlatformLinkResolvedItem>,
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
