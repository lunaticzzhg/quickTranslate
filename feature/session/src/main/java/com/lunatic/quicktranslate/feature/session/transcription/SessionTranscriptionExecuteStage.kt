package com.lunatic.quicktranslate.feature.session.transcription

import android.os.Handler
import android.os.Looper
import com.lunatic.quicktranslate.feature.session.subtitle.SubtitleSegment
import com.lunatic.quicktranslate.feature.transcription.TranscriptionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SessionTranscriptionExecuteStage(
    private val transcriptionService: TranscriptionService
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    suspend fun execute(
        prepared: PreparedTranscriptionMedia,
        onProgress: ((Int) -> Unit)? = null,
        onPartialSubtitles: ((List<SubtitleSegment>) -> Unit)? = null
    ): Result<List<SubtitleSegment>> {
        return withContext(Dispatchers.IO) {
            runCatching {
                transcriptionService.transcribe(
                    mediaPath = prepared.path,
                    onProgress = { progress ->
                        postToMain {
                            onProgress?.invoke(progress)
                        }
                    },
                    onPartialResult = { partial ->
                        val mapped = partial.mapIndexed { index, segment ->
                            SubtitleSegment(
                                id = index + 1L,
                                startMs = segment.startMs,
                                endMs = segment.endMs,
                                text = segment.text
                            )
                        }
                        postToMain {
                            onPartialSubtitles?.invoke(mapped)
                        }
                    }
                ).mapIndexed { index, segment ->
                    SubtitleSegment(
                        id = index + 1L,
                        startMs = segment.startMs,
                        endMs = segment.endMs,
                        text = segment.text
                    )
                }
            }.also {
                prepared.cleanup?.invoke()
            }
        }
    }

    private fun postToMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }
}
