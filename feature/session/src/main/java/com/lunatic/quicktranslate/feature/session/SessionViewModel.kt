package com.lunatic.quicktranslate.feature.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lunatic.quicktranslate.player.core.SessionPlayer
import com.lunatic.quicktranslate.feature.session.subtitle.MockSubtitleProvider
import com.lunatic.quicktranslate.feature.session.subtitle.SubtitleMatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class SessionViewModel(
    savedStateHandle: SavedStateHandle,
    private val sessionPlayer: SessionPlayer
) : ViewModel() {
    private data class LoopSession(
        val segment: com.lunatic.quicktranslate.feature.session.subtitle.SubtitleSegment,
        val totalRepeatCount: Int?,
        val remainingCount: Int?
    )

    private val importedMedia = ImportedSessionMedia(
        uri = savedStateHandle[SessionNav.uriArg] ?: "",
        displayName = savedStateHandle[SessionNav.nameArg] ?: "Imported media",
        mimeType = savedStateHandle[SessionNav.mimeArg] ?: "unknown",
        durationMs = savedStateHandle[SessionNav.durationArg] ?: -1L
    )

    private val mutableState = MutableStateFlow(
        SessionState(
            importedName = importedMedia.displayName,
            importedMime = importedMedia.mimeType,
            importedDuration = formatDuration(importedMedia.durationMs),
            subtitles = MockSubtitleProvider.provide()
        )
    )
    val state: StateFlow<SessionState> = mutableState.asStateFlow()

    private val mutableEffect = MutableSharedFlow<SessionEffect>()
    val effect: SharedFlow<SessionEffect> = mutableEffect.asSharedFlow()
    val player = sessionPlayer.player
    private var loopSession: LoopSession? = null
    private var loopJumpInProgress = false

    init {
        if (importedMedia.uri.isNotBlank()) {
            sessionPlayer.setMedia(importedMedia.uri)
        }
        viewModelScope.launch {
            sessionPlayer.state.collect { playback ->
                val currentState = mutableState.value
                val activeIndex = SubtitleMatcher.findActiveIndex(
                    segments = currentState.subtitles,
                    positionMs = playback.currentPositionMs
                )
                mutableState.value = mutableState.value.copy(
                    isPlaying = playback.isPlaying,
                    isLoading = playback.isLoading,
                    hasVideo = playback.hasVideo,
                    currentPositionMs = playback.currentPositionMs,
                    durationMs = playback.durationMs,
                    activeSubtitleIndex = activeIndex
                )
                evaluateLoop(playback.currentPositionMs)
            }
        }
    }

    fun onIntent(intent: SessionIntent) {
        when (intent) {
            SessionIntent.BackClicked -> emitEffect(SessionEffect.NavigateBack)
            SessionIntent.PlayPauseClicked -> onPlayPauseClicked()
            is SessionIntent.SeekToRequested -> onSeekRequested(intent.positionMs)
            is SessionIntent.SubtitleClicked -> onSubtitleClicked(intent.segment)
            is SessionIntent.LoopCountChanged -> onLoopCountChanged(intent.option)
            SessionIntent.StartLoopClicked -> startLoop()
            SessionIntent.StopLoopClicked -> stopLoop()
        }
    }

    private fun emitEffect(effect: SessionEffect) {
        viewModelScope.launch {
            mutableEffect.emit(effect)
        }
    }

    private fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0L) {
            return "Unknown duration"
        }
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }

    private fun onPlayPauseClicked() {
        if (mutableState.value.isPlaying) {
            sessionPlayer.pause()
        } else {
            sessionPlayer.play()
        }
    }

    private fun onSubtitleClicked(segment: com.lunatic.quicktranslate.feature.session.subtitle.SubtitleSegment) {
        if (mutableState.value.isLooping) {
            stopLoop()
        }
        mutableState.value = mutableState.value.copy(
            selectedSubtitleId = segment.id
        )
        sessionPlayer.seekTo(segment.startMs)
    }

    private fun onSeekRequested(positionMs: Long) {
        if (mutableState.value.isLooping) {
            stopLoop()
        }
        sessionPlayer.seekTo(positionMs)
    }

    private fun onLoopCountChanged(option: LoopCountOption) {
        if (mutableState.value.isLooping) {
            return
        }
        mutableState.value = mutableState.value.copy(
            loopCountOption = option
        )
    }

    private fun startLoop() {
        val currentState = mutableState.value
        val segmentByCurrentPosition = currentState.subtitles.getOrNull(
            SubtitleMatcher.findActiveIndex(
                segments = currentState.subtitles,
                positionMs = currentState.currentPositionMs
            )
        )
        val selectedSegment = segmentByCurrentPosition
            ?: currentState.subtitles.firstOrNull { it.id == currentState.selectedSubtitleId }
            ?: currentState.subtitles.getOrNull(currentState.activeSubtitleIndex)
            ?: return

        val repeatCount = currentState.loopCountOption.repeatCount
        loopSession = LoopSession(
            segment = selectedSegment,
            totalRepeatCount = repeatCount,
            remainingCount = repeatCount
        )
        loopJumpInProgress = false
        mutableState.value = mutableState.value.copy(
            selectedSubtitleId = selectedSegment.id,
            isLooping = true,
            loopRemainingCount = repeatCount
        )
        sessionPlayer.seekTo(selectedSegment.startMs)
        sessionPlayer.play()
    }

    private fun stopLoop() {
        loopSession = null
        loopJumpInProgress = false
        mutableState.value = mutableState.value.copy(
            isLooping = false,
            loopRemainingCount = null
        )
    }

    private fun evaluateLoop(currentPositionMs: Long) {
        val activeLoop = loopSession ?: return
        val segment = activeLoop.segment

        if (currentPositionMs <= segment.startMs + 150L) {
            loopJumpInProgress = false
        }

        if (!mutableState.value.isLooping || loopJumpInProgress) {
            return
        }

        if (currentPositionMs < segment.endMs) {
            return
        }

        loopJumpInProgress = true
        val remaining = activeLoop.remainingCount
        if (remaining == null) {
            sessionPlayer.seekTo(segment.startMs)
            return
        }

        val updatedRemaining = remaining - 1
        if (updatedRemaining <= 0) {
            sessionPlayer.pause()
            sessionPlayer.seekTo(segment.startMs)
            stopLoop()
            return
        }

        loopSession = activeLoop.copy(remainingCount = updatedRemaining)
        mutableState.value = mutableState.value.copy(
            loopRemainingCount = updatedRemaining
        )
        sessionPlayer.seekTo(segment.startMs)
    }

    override fun onCleared() {
        sessionPlayer.release()
        super.onCleared()
    }
}
