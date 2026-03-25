package com.lunatic.quicktranslate.feature.session.di

import com.lunatic.quicktranslate.feature.session.SessionViewModel
import com.lunatic.quicktranslate.feature.session.loop.SessionLoopController
import com.lunatic.quicktranslate.feature.session.playback.SessionPlaybackCoordinator
import com.lunatic.quicktranslate.feature.session.transcription.SessionTranscriptionCoordinator
import com.lunatic.quicktranslate.feature.transcription.MockTranscriptionService
import com.lunatic.quicktranslate.player.core.di.playerModule
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val sessionModule = module {
    includes(playerModule)
    factory { MockTranscriptionService() }
    factory { SessionLoopController(get(), get()) }
    factory { SessionPlaybackCoordinator(get(), get()) }
    factory { SessionTranscriptionCoordinator(get(), get(), get(), get()) }
    viewModelOf(::SessionViewModel)
}
