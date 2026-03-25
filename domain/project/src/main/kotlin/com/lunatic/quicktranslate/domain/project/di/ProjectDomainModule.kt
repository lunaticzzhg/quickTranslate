package com.lunatic.quicktranslate.domain.project.di

import com.lunatic.quicktranslate.domain.project.usecase.CreateProjectUseCase
import com.lunatic.quicktranslate.domain.project.usecase.DeleteProjectUseCase
import com.lunatic.quicktranslate.domain.project.usecase.ObserveRecentProjectsUseCase
import org.koin.dsl.module

val projectDomainModule = module {
    factory { CreateProjectUseCase(get()) }
    factory { ObserveRecentProjectsUseCase(get()) }
    factory { DeleteProjectUseCase(get()) }
}
