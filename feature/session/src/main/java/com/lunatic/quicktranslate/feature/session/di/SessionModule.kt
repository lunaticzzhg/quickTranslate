package com.lunatic.quicktranslate.feature.session.di

import android.util.Log
import com.lunatic.quicktranslate.feature.session.BuildConfig
import com.lunatic.quicktranslate.feature.session.SessionViewModel
import com.lunatic.quicktranslate.feature.session.loop.SessionLoopController
import com.lunatic.quicktranslate.feature.session.playback.SessionPlaybackCoordinator
import com.lunatic.quicktranslate.feature.session.transcription.EmbeddedWhisperConfigProvider
import com.lunatic.quicktranslate.feature.session.transcription.SessionTranscriptionCoordinator
import com.lunatic.quicktranslate.feature.session.transcription.SessionDirectHttpMediaDownloader
import com.lunatic.quicktranslate.feature.session.transcription.SessionMediaPrepareStage
import com.lunatic.quicktranslate.feature.session.transcription.SessionRemoteMediaDownloadStage
import com.lunatic.quicktranslate.feature.session.transcription.SessionRemoteMediaSourceResolver
import com.lunatic.quicktranslate.feature.session.transcription.SessionProjectTranscodeChain
import com.lunatic.quicktranslate.feature.session.transcription.SessionProjectTranscodeTaskExecutor
import com.lunatic.quicktranslate.feature.session.transcription.SessionEnsureLocalMediaStep
import com.lunatic.quicktranslate.feature.session.transcription.SessionMarkResolvingStep
import com.lunatic.quicktranslate.feature.session.transcription.SessionSubtitlePersistStage
import com.lunatic.quicktranslate.feature.session.transcription.SessionSyncProjectMediaSourceStep
import com.lunatic.quicktranslate.feature.session.transcription.SessionTranscribeStep
import com.lunatic.quicktranslate.feature.session.transcription.SessionTranscriptionExecuteStage
import com.lunatic.quicktranslate.feature.session.transcription.SessionTranscriptionPipeline
import com.lunatic.quicktranslate.feature.session.transcription.SessionYtDlpMediaDownloader
import com.lunatic.quicktranslate.domain.project.repository.ProjectTranscodeTaskExecutor
import com.lunatic.quicktranslate.feature.transcription.MockTranscriptionService
import com.lunatic.quicktranslate.feature.transcription.TranscriptionService
import com.lunatic.quicktranslate.feature.transcription.WhisperCliConfig
import com.lunatic.quicktranslate.feature.transcription.WhisperCliTranscriptionService
import com.lunatic.quicktranslate.player.core.di.playerModule
import okhttp3.OkHttpClient
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
                val threads = BuildConfig.WHISPER_THREADS
                val config = if (
                    BuildConfig.WHISPER_CLI_PATH.isNotBlank() &&
                    BuildConfig.WHISPER_MODEL_PATH.isNotBlank()
                ) {
                    WhisperCliConfig(
                        cliPath = BuildConfig.WHISPER_CLI_PATH,
                        modelPath = BuildConfig.WHISPER_MODEL_PATH,
                        language = language,
                        threads = threads
                    )
                } else {
                    EmbeddedWhisperConfigProvider(androidContext()).resolve(language, threads)
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
    single { OkHttpClient.Builder().build() }
    factory {
        SessionRemoteMediaDownloadStage(
            sourceResolver = SessionRemoteMediaSourceResolver(get()),
            ytDlpMediaDownloader = SessionYtDlpMediaDownloader(
                appContext = androidContext(),
                ytDlpCookiesPath = BuildConfig.YTDLP_COOKIES_PATH,
                ytDlpExtractorArgs = BuildConfig.YTDLP_EXTRACTOR_ARGS
            ),
            directHttpMediaDownloader = SessionDirectHttpMediaDownloader(
                appContext = androidContext(),
                okHttpClient = get()
            )
        )
    }
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
    factory {
        SessionProjectTranscodeChain(
            steps = listOf(
                SessionMarkResolvingStep(),
                SessionEnsureLocalMediaStep(get()),
                SessionSyncProjectMediaSourceStep(get()),
                SessionTranscribeStep(get())
            )
        )
    }
    factory<ProjectTranscodeTaskExecutor> { SessionProjectTranscodeTaskExecutor(get(), get()) }
    factory { SessionTranscriptionCoordinator(get(), get()) }
    viewModelOf(::SessionViewModel)
}
