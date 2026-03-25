package com.lunatic.quicktranslate.feature.session

import com.lunatic.quicktranslate.feature.session.subtitle.SubtitleSegment
import com.lunatic.quicktranslate.feature.transcription.TranscriptionStatus

enum class LoopCountOption(
    val label: String,
    val repeatCount: Int?
) {
    ONE("1x", 1),
    THREE("3x", 3),
    FIVE("5x", 5),
    INFINITE("∞", null)
}

data class SessionState(
    val title: String = "Learning Session",
    val message: String = "Use the controls below to practice with this media file.",
    val importedName: String = "Unknown file",
    val importedMime: String = "unknown",
    val importedDuration: String = "Unknown duration",
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val hasVideo: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val subtitles: List<SubtitleSegment> = emptyList(),
    val activeSubtitleIndex: Int = -1,
    val selectedRangeStartIndex: Int? = null,
    val selectedRangeEndIndex: Int? = null,
    val loopCountOption: LoopCountOption = LoopCountOption.THREE,
    val isLooping: Boolean = false,
    val loopRemainingCount: Int? = null,
    val transcriptionStatus: TranscriptionStatus = TranscriptionStatus.IDLE,
    val transcriptionProgress: Int? = null,
    val transcriptionError: String? = null
)

data class ImportedSessionMedia(
    val projectId: Long,
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val durationMs: Long
)

sealed interface SessionIntent {
    data object BackClicked : SessionIntent
    data object PlayPauseClicked : SessionIntent
    data class SeekToRequested(val positionMs: Long) : SessionIntent
    data class SubtitleClicked(val segment: SubtitleSegment) : SessionIntent
    data class LoopCountChanged(val option: LoopCountOption) : SessionIntent
    data object StartLoopClicked : SessionIntent
    data object StopLoopClicked : SessionIntent
    data object RetryTranscriptionClicked : SessionIntent
}

sealed interface SessionEffect {
    data object NavigateBack : SessionEffect
}
