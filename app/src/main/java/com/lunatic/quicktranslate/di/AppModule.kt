package com.lunatic.quicktranslate.di

import com.lunatic.quicktranslate.data.project.di.projectDataModule
import com.lunatic.quicktranslate.domain.project.di.projectDomainModule
import com.lunatic.quicktranslate.domain.project.repository.ProjectTranscodeQueueEngine
import com.lunatic.quicktranslate.feature.home.di.homeModule
import com.lunatic.quicktranslate.feature.session.di.sessionModule
import com.lunatic.quicktranslate.queue.AppProjectTranscodeQueueEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.qualifier.named
import org.koin.dsl.module

private val appScopeQualifier = named("app_scope")

val appModule = module {
    includes(
        projectDataModule,
        projectDomainModule,
        homeModule,
        sessionModule
    )
    single(appScopeQualifier) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
    single<ProjectTranscodeQueueEngine> {
        AppProjectTranscodeQueueEngine(
            appScope = get(appScopeQualifier),
            taskRepository = get(),
            taskExecutor = get()
        )
    }
}
