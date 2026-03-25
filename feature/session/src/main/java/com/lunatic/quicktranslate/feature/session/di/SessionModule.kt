package com.lunatic.quicktranslate.feature.session.di

import android.util.Log
import com.lunatic.quicktranslate.feature.session.BuildConfig
import com.lunatic.quicktranslate.feature.session.SessionViewModel
import com.lunatic.quicktranslate.feature.session.loop.SessionLoopController
import com.lunatic.quicktranslate.feature.session.playback.SessionPlaybackCoordinator
import com.lunatic.quicktranslate.feature.session.transcription.EmbeddedWhisperConfigProvider
import com.lunatic.quicktranslate.feature.session.transcription.SessionTranscriptionCoordinator
import com.lunatic.quicktranslate.feature.session.transcription.SessionMediaPrepareStage
import com.lunatic.quicktranslate.feature.session.transcription.SessionProjectTranscodeTaskExecutor
import com.lunatic.quicktranslate.feature.session.transcription.SessionSubtitlePersistStage
import com.lunatic.quicktranslate.feature.session.transcription.SessionTranscriptionExecuteStage
import com.lunatic.quicktranslate.feature.session.transcription.SessionTranscriptionPipeline
import com.lunatic.quicktranslate.domain.project.repository.ProjectTranscodeTaskExecutor
import com.lunatic.quicktranslate.feature.transcription.MockTranscriptionService
import com.lunatic.quicktranslate.feature.transcription.TranscriptionService
import com.lunatic.quicktranslate.feature.transcription.WhisperCliConfig
import com.lunatic.quicktranslate.feature.transcription.WhisperCliTranscriptionService
import com.lunatic.quicktranslate.player.core.di.playerModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

private const val TAG = "SessionModule"

val sessionModule = module {
    includes(playerModule)
    factory<TranscriptionService> {
        if (BuildConfig.TRANSCRIPTION_MODE.equals("real", ignoreCase = true)) {
            runCatching {
                val language = BuildConfig.WHISPER_LANGUAGE.ifBlank { "en" }
                val config = if (
                    BuildConfig.WHISPER_CLI_PATH.isNotBlank() &&
                    BuildConfig.WHISPER_MODEL_PATH.isNotBlank()
                ) {
                    WhisperCliConfig(
                        cliPath = BuildConfig.WHISPER_CLI_PATH,
                        modelPath = BuildConfig.WHISPER_MODEL_PATH,
                        language = language
                    )
                } else {
                    EmbeddedWhisperConfigProvider(androidContext()).resolve(language)
                }
                WhisperCliTranscriptionService(
                    config = config
                )
            }.getOrElse { error ->
                Log.e(TAG, "Real transcription init failed, fallback to mock.", error)
                MockTranscriptionService()
            }
        } else {
            MockTranscriptionService()
        }
    }
    factory { SessionLoopController(get(), get()) }
    factory { SessionPlaybackCoordinator(get(), get()) }
    factory { SessionMediaPrepareStage(androidContext()) }
    factory { SessionTranscriptionExecuteStage(get()) }
    factory { SessionSubtitlePersistStage(get(), get()) }
    factory {
        SessionTranscriptionPipeline(
            prepareStage = get(),
            executeStage = get(),
            persistStage = get()
        )
    }
    factory<ProjectTranscodeTaskExecutor> { SessionProjectTranscodeTaskExecutor(get()) }
    factory { SessionTranscriptionCoordinator(get(), get()) }
    viewModelOf(::SessionViewModel)
}
