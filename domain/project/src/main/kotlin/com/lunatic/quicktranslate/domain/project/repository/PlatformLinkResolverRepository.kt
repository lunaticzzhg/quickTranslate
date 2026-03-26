package com.lunatic.quicktranslate.domain.project.repository

import com.lunatic.quicktranslate.domain.project.model.PlatformLinkResolveResult

interface PlatformLinkResolverRepository {
    suspend fun resolve(sourceUrl: String): PlatformLinkResolveResult
}

