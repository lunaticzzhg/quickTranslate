package com.lunatic.quicktranslate.feature.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lunatic.quicktranslate.domain.project.model.ProjectTranscodeTaskStatus
import com.lunatic.quicktranslate.domain.project.usecase.BumpProjectTranscodeTaskPriorityUseCase
import com.lunatic.quicktranslate.domain.project.usecase.EnqueueProjectTranscodeTaskUseCase
import com.lunatic.quicktranslate.domain.project.usecase.ObserveProjectTranscodeTaskUseCase
import com.lunatic.quicktranslate.feature.session.loop.SessionLoopController
import com.lunatic.quicktranslate.feature.session.playback.SessionPlaybackCoordinator
import com.lunatic.quicktranslate.player.core.SessionPlayer
import com.lunatic.quicktranslate.feature.session.subtitle.SubtitleSegment
import com.lunatic.quicktranslate.feature.session.subtitle.SubtitleMatcher
import com.lunatic.quicktranslate.feature.session.transcription.SessionTranscriptionCoordinator
import com.lunatic.quicktranslate.feature.transcription.TranscriptionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
    private val enqueueProjectTranscodeTaskUseCase: EnqueueProjectTranscodeTaskUseCase,
    private val bumpProjectTranscodeTaskPriorityUseCase: BumpProjectTranscodeTaskPriorityUseCase,
    private val observeProjectTranscodeTaskUseCase: ObserveProjectTranscodeTaskUseCase
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
    private var hasAutoStartedPlaybackForCurrentTranscription = false

    init {
        if (importedMedia.uri.isNotBlank()) {
            sessionPlayer.setMedia(importedMedia.uri)
            restoreLastPlaybackPosition()
            observeProjectTranscodeTask()
            viewModelScope.launch {
                val projectId = importedMedia.projectId
                if (projectId > 0L) {
                    bumpProjectTranscodeTaskPriorityUseCase(projectId)
                }
            }
            restoreSavedSubtitlesOrQueue()
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
            SessionIntent.RetryTranscriptionClicked -> startTranscription()
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

    private fun startTranscription() {
        viewModelScope.launch {
            queueTranscodeAndPrioritize()
        }
    }

    private fun restoreSavedSubtitlesOrQueue() {
        viewModelScope.launch {
            mutableState.value = loopController.restorePersistedConfig(
                projectId = importedMedia.projectId,
                state = mutableState.value
            )
            val restored = restoreSavedSubtitles()
            if (!restored) {
                mutableState.value = mutableState.value.copy(
                    transcriptionStatus = TranscriptionStatus.QUEUED,
                    transcriptionProgress = null,
                    transcriptionError = null
                )
                queueTranscodeAndPrioritize()
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
        autoStartPlaybackIfNeeded(storedSubtitles)
        return true
    }

    private suspend fun queueTranscodeAndPrioritize() {
        val projectId = importedMedia.projectId
        if (projectId <= 0L) {
            return
        }
        stopLoop()
        hasAutoStartedPlaybackForCurrentTranscription = false
        enqueueProjectTranscodeTaskUseCase(
            projectId = projectId,
            mediaUri = importedMedia.uri
        )
        bumpProjectTranscodeTaskPriorityUseCase(projectId)
    }

    private fun observeProjectTranscodeTask() {
        val projectId = importedMedia.projectId
        if (projectId <= 0L) {
            return
        }
        viewModelScope.launch {
            observeProjectTranscodeTaskUseCase(projectId).collect { task ->
                if (task == null) {
                    return@collect
                }
                when (task.status) {
                    ProjectTranscodeTaskStatus.PENDING -> {
                        mutableState.value = mutableState.value.copy(
                            transcriptionStatus = TranscriptionStatus.QUEUED,
                            transcriptionProgress = null
                        )
                    }

                    ProjectTranscodeTaskStatus.RUNNING -> {
                        mutableState.value = mutableState.value.copy(
                            transcriptionStatus = TranscriptionStatus.PROCESSING,
                            transcriptionProgress = null,
                            transcriptionError = null
                        )
                    }

                    ProjectTranscodeTaskStatus.SUCCEEDED -> {
                        val restored = restoreSavedSubtitles()
                        if (!restored) {
                            mutableState.value = mutableState.value.copy(
                                transcriptionStatus = TranscriptionStatus.FAILED,
                                transcriptionProgress = null,
                                transcriptionError = "Transcription finished, but no usable subtitles were detected."
                            )
                        }
                    }

                    ProjectTranscodeTaskStatus.FAILED -> {
                        mutableState.value = mutableState.value.copy(
                            transcriptionStatus = TranscriptionStatus.FAILED,
                            transcriptionProgress = null,
                            transcriptionError = task.errorMessage ?: "Transcription failed. Please retry."
                        )
                    }

                    ProjectTranscodeTaskStatus.CANCELED -> {
                        mutableState.value = mutableState.value.copy(
                            transcriptionStatus = TranscriptionStatus.FAILED,
                            transcriptionProgress = null,
                            transcriptionError = "Transcription was canceled."
                        )
                    }
                }
            }
        }
    }

    private fun autoStartPlaybackIfNeeded(subtitles: List<SubtitleSegment>) {
        if (hasAutoStartedPlaybackForCurrentTranscription || subtitles.isEmpty()) {
            return
        }
        hasAutoStartedPlaybackForCurrentTranscription = true
        val firstStart = subtitles.first().startMs.coerceAtLeast(0L)
        sessionPlayer.seekTo(firstStart)
        sessionPlayer.play()
    }

    override fun onCleared() {
        persistPlaybackPosition()
        loopController.clear()
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
