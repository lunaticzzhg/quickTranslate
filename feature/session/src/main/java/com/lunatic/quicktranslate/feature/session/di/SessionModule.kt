package com.lunatic.quicktranslate.feature.session.di

import com.lunatic.quicktranslate.feature.session.SessionViewModel
import com.lunatic.quicktranslate.feature.transcription.MockTranscriptionService
import com.lunatic.quicktranslate.player.core.di.playerModule
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val sessionModule = module {
    includes(playerModule)
    factory { MockTranscriptionService() }
    viewModelOf(::SessionViewModel)
}
