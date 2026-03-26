package com.lunatic.quicktranslate.domain.project.di

import com.lunatic.quicktranslate.domain.project.usecase.CreateProjectUseCase
import com.lunatic.quicktranslate.domain.project.usecase.CreateProjectFromUrlUseCase
import com.lunatic.quicktranslate.domain.project.usecase.DeleteProjectUseCase
import com.lunatic.quicktranslate.domain.project.usecase.BumpProjectTranscodeTaskPriorityUseCase
import com.lunatic.quicktranslate.domain.project.usecase.EnqueueProjectTranscodeTaskUseCase
import com.lunatic.quicktranslate.domain.project.usecase.GetProjectLoopConfigUseCase
import com.lunatic.quicktranslate.domain.project.usecase.GetProjectByIdUseCase
import com.lunatic.quicktranslate.domain.project.usecase.GetProjectByMediaUriUseCase
import com.lunatic.quicktranslate.domain.project.usecase.GetProjectPlaybackPositionUseCase
import com.lunatic.quicktranslate.domain.project.usecase.GetProjectSubtitlesUseCase
import com.lunatic.quicktranslate.domain.project.usecase.ObserveRecentProjectsUseCase
import com.lunatic.quicktranslate.domain.project.usecase.ObserveProjectTranscodeTaskUseCase
import com.lunatic.quicktranslate.domain.project.usecase.ReplaceProjectSubtitlesUseCase
import com.lunatic.quicktranslate.domain.project.usecase.ResolvePlatformLinkUseCase
import com.lunatic.quicktranslate.domain.project.usecase.RestoreAndResumeProjectTranscodeQueueUseCase
import com.lunatic.quicktranslate.domain.project.usecase.SaveProjectLoopConfigUseCase
import com.lunatic.quicktranslate.domain.project.usecase.ObserveTranscodeDashboardTasksUseCase
import com.lunatic.quicktranslate.domain.project.usecase.UpdateProjectPlaybackPositionUseCase
import com.lunatic.quicktranslate.domain.project.usecase.UpdateProjectMediaSourceUseCase
import com.lunatic.quicktranslate.domain.project.usecase.UpdateProjectSubtitleStatusUseCase
import org.koin.dsl.module

val projectDomainModule = module {
    factory { CreateProjectUseCase(get()) }
    factory { ResolvePlatformLinkUseCase(get()) }
    factory { GetProjectByIdUseCase(get()) }
    factory { CreateProjectFromUrlUseCase(get(), get(), get(), get()) }
    factory { GetProjectByMediaUriUseCase(get()) }
    factory { ObserveRecentProjectsUseCase(get()) }
    factory { DeleteProjectUseCase(get()) }
    factory { UpdateProjectSubtitleStatusUseCase(get()) }
    factory { UpdateProjectMediaSourceUseCase(get()) }
    factory { GetProjectPlaybackPositionUseCase(get()) }
    factory { UpdateProjectPlaybackPositionUseCase(get()) }
    factory { GetProjectSubtitlesUseCase(get()) }
    factory { ReplaceProjectSubtitlesUseCase(get()) }
    factory { GetProjectLoopConfigUseCase(get()) }
    factory { SaveProjectLoopConfigUseCase(get()) }
    factory { EnqueueProjectTranscodeTaskUseCase(get(), get()) }
    factory { BumpProjectTranscodeTaskPriorityUseCase(get(), get()) }
    factory { ObserveProjectTranscodeTaskUseCase(get()) }
    factory { ObserveTranscodeDashboardTasksUseCase(get()) }
    factory { RestoreAndResumeProjectTranscodeQueueUseCase(get()) }
}
