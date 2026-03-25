package com.lunatic.quicktranslate.feature.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lunatic.quicktranslate.feature.session.loop.SessionLoopController
import com.lunatic.quicktranslate.feature.session.playback.SessionPlaybackCoordinator
import com.lunatic.quicktranslate.player.core.SessionPlayer
import com.lunatic.quicktranslate.feature.session.subtitle.SubtitleSegment
import com.lunatic.quicktranslate.feature.session.subtitle.SubtitleMatcher
import com.lunatic.quicktranslate.feature.session.transcription.SessionTranscriptionCoordinator
import com.lunatic.quicktranslate.feature.transcription.TranscriptionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SessionViewModel(
    savedStateHandle: SavedStateHandle,
    private val sessionPlayer: SessionPlayer,
    private val transcriptionCoordinator: SessionTranscriptionCoordinator,
    private val loopController: SessionLoopController,
    private val playbackCoordinator: SessionPlaybackCoordinator,
) : ViewModel() {
    private val importedMedia = ImportedSessionMedia(
        projectId = savedStateHandle[SessionNav.projectIdArg] ?: -1L,
        uri = savedStateHandle[SessionNav.uriArg] ?: "",
        displayName = savedStateHandle[SessionNav.nameArg] ?: "Imported media",
        mimeType = savedStateHandle[SessionNav.mimeArg] ?: "unknown",
        durationMs = savedStateHandle[SessionNav.durationArg] ?: -1L
    )

    private val mutableState = MutableStateFlow(
        SessionState(
            importedName = importedMedia.displayName,
            importedMime = importedMedia.mimeType,
            importedDuration = formatDuration(importedMedia.durationMs)
        )
    )
    val state: StateFlow<SessionState> = mutableState.asStateFlow()

    private val mutableEffect = MutableSharedFlow<SessionEffect>()
    val effect: SharedFlow<SessionEffect> = mutableEffect.asSharedFlow()
    val player = sessionPlayer.player
    private var transcriptionJob: Job? = null

    init {
        if (importedMedia.uri.isNotBlank()) {
            sessionPlayer.setMedia(importedMedia.uri)
            restoreLastPlaybackPosition()
            restoreSavedSubtitlesOrTranscribe()
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
            SessionIntent.RetryTranscriptionClicked -> startMockTranscription()
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

    private fun onSubtitleClicked(segment: SubtitleSegment) {
        if (mutableState.value.isLooping) {
            stopLoop()
        }
        val subtitles = mutableState.value.subtitles
        val clickedIndex = subtitles.indexOfFirst { it.id == segment.id }
        if (clickedIndex < 0) {
            return
        }

        val start = mutableState.value.selectedRangeStartIndex
        val end = mutableState.value.selectedRangeEndIndex

        val (newStart, newEnd) = when {
            start == null -> clickedIndex to clickedIndex
            end != null && start != end -> clickedIndex to clickedIndex
            else -> minOf(start, clickedIndex) to maxOf(start, clickedIndex)
        }

        mutableState.value = mutableState.value.copy(
            selectedRangeStartIndex = newStart,
            selectedRangeEndIndex = newEnd
        )
        loopController.persistConfigAsync(
            projectId = importedMedia.projectId,
            state = mutableState.value,
            scope = viewModelScope
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
        loopController.persistConfigAsync(
            projectId = importedMedia.projectId,
            state = mutableState.value,
            scope = viewModelScope
        )
    }

    private fun startLoop() {
        val currentState = mutableState.value
        val segmentIndexByCurrentPosition = SubtitleMatcher.findActiveIndex(
            segments = currentState.subtitles,
            positionMs = currentState.currentPositionMs
        )
        val fallbackSingleIndex = if (segmentIndexByCurrentPosition >= 0) {
            segmentIndexByCurrentPosition
        } else {
            currentState.activeSubtitleIndex
        }
        val startResult = loopController.start(
            state = currentState,
            fallbackSingleIndex = fallbackSingleIndex
        ) ?: return
        mutableState.value = startResult.state
        loopController.persistConfigAsync(
            projectId = importedMedia.projectId,
            state = mutableState.value,
            scope = viewModelScope
        )
        sessionPlayer.seekTo(startResult.seekToMs)
        sessionPlayer.play()
    }

    private fun stopLoop() {
        mutableState.value = loopController.stop(mutableState.value)
    }

    private fun evaluateLoop(currentPositionMs: Long) {
        val evaluation = loopController.evaluate(
            state = mutableState.value,
            currentPositionMs = currentPositionMs
        )
        if (evaluation.state != mutableState.value) {
            mutableState.value = evaluation.state
        }
        if (evaluation.shouldPause) {
            sessionPlayer.pause()
        }
        evaluation.seekToMs?.let(sessionPlayer::seekTo)
    }

    private fun startMockTranscription() {
        if (transcriptionJob?.isActive == true) {
            return
        }
        transcriptionJob = viewModelScope.launch {
            runMockTranscription()
        }
    }

    private fun restoreSavedSubtitlesOrTranscribe() {
        if (transcriptionJob?.isActive == true) {
            return
        }
        transcriptionJob = viewModelScope.launch {
            mutableState.value = loopController.restorePersistedConfig(
                projectId = importedMedia.projectId,
                state = mutableState.value
            )
            val restored = restoreSavedSubtitles()
            if (!restored) {
                runMockTranscription()
            }
        }
    }

    private fun restoreLastPlaybackPosition() {
        val projectId = importedMedia.projectId
        if (projectId <= 0L) {
            return
        }
        viewModelScope.launch {
            val targetPosition = playbackCoordinator.restorePlaybackPosition(projectId) ?: return@launch
            if (targetPosition > 0L) {
                sessionPlayer.seekTo(targetPosition)
            }
        }
    }

    private suspend fun restoreSavedSubtitles(): Boolean {
        val storedSubtitles = transcriptionCoordinator.restorePersistedSubtitles(importedMedia.projectId)
        if (storedSubtitles.isEmpty()) {
            return false
        }
        mutableState.value = mutableState.value.copy(
            transcriptionStatus = TranscriptionStatus.SUCCESS,
            transcriptionProgress = null,
            transcriptionError = null,
            subtitles = storedSubtitles,
            activeSubtitleIndex = -1,
            selectedRangeStartIndex = null,
            selectedRangeEndIndex = null
        )
        mutableState.value = loopController.applyRestoredSelectionIfValid(mutableState.value)
        return true
    }

    private suspend fun runMockTranscription() {
        stopLoop()
        mutableState.value = mutableState.value.copy(
            transcriptionStatus = TranscriptionStatus.QUEUED,
            transcriptionProgress = 0,
            transcriptionError = null,
            subtitles = emptyList(),
            activeSubtitleIndex = -1,
            selectedRangeStartIndex = null,
            selectedRangeEndIndex = null
        )
        delay(250L)
        mutableState.value = mutableState.value.copy(
            transcriptionStatus = TranscriptionStatus.PROCESSING,
            transcriptionProgress = 0
        )
        transcriptionCoordinator.transcribeAndPersist(
            projectId = importedMedia.projectId,
            mediaUri = importedMedia.uri,
            onProgress = { progress ->
                mutableState.value = mutableState.value.copy(
                    transcriptionStatus = TranscriptionStatus.PROCESSING,
                    transcriptionProgress = progress.coerceIn(0, 100)
                )
            }
        ).onSuccess { uiSubtitles ->
            val isEmptyResult = uiSubtitles.isEmpty()
            mutableState.value = mutableState.value.copy(
                transcriptionStatus = TranscriptionStatus.SUCCESS,
                transcriptionProgress = 100,
                transcriptionError = if (isEmptyResult) {
                    "Transcription finished, but no usable subtitles were detected."
                } else {
                    null
                },
                subtitles = uiSubtitles
            )
            mutableState.value = loopController.applyRestoredSelectionIfValid(mutableState.value)
        }.onFailure { throwable ->
            mutableState.value = mutableState.value.copy(
                transcriptionStatus = TranscriptionStatus.FAILED,
                transcriptionProgress = null,
                transcriptionError = throwable.message ?: "Transcription failed. Please retry.",
                subtitles = emptyList(),
                activeSubtitleIndex = -1
            )
        }
    }

    override fun onCleared() {
        persistPlaybackPosition()
        loopController.clear()
        transcriptionJob?.cancel()
        sessionPlayer.release()
        super.onCleared()
    }

    private fun persistPlaybackPosition() {
        val projectId = importedMedia.projectId
        if (projectId <= 0L) {
            return
        }
        val position = mutableState.value.currentPositionMs.coerceAtLeast(0L)
        CoroutineScope(Dispatchers.IO).launch {
            playbackCoordinator.persistPlaybackPosition(
                projectId = projectId,
                playbackPositionMs = position
            )
        }
    }
}
