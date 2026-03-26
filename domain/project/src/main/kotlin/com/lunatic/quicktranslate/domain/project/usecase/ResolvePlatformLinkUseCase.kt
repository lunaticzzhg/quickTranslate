package com.lunatic.quicktranslate.domain.project.usecase

import com.lunatic.quicktranslate.domain.project.model.PlatformLinkResolveResult
import com.lunatic.quicktranslate.domain.project.repository.PlatformLinkResolverRepository

class ResolvePlatformLinkUseCase(
    private val repository: PlatformLinkResolverRepository
) {
    suspend operator fun invoke(sourceUrl: String): PlatformLinkResolveResult {
        return repository.resolve(sourceUrl)
    }
}

