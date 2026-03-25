package com.lunatic.quicktranslate.feature.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lunatic.quicktranslate.domain.project.model.ProjectLoopConfig
import com.lunatic.quicktranslate.domain.project.model.ProjectSubtitle
import com.lunatic.quicktranslate.domain.project.model.SubtitleStatus
import com.lunatic.quicktranslate.domain.project.usecase.GetProjectLoopConfigUseCase
import com.lunatic.quicktranslate.domain.project.usecase.GetProjectSubtitlesUseCase
import com.lunatic.quicktranslate.domain.project.usecase.ReplaceProjectSubtitlesUseCase
import com.lunatic.quicktranslate.domain.project.usecase.SaveProjectLoopConfigUseCase
import com.lunatic.quicktranslate.domain.project.usecase.UpdateProjectSubtitleStatusUseCase
import com.lunatic.quicktranslate.player.core.SessionPlayer
import com.lunatic.quicktranslate.feature.session.subtitle.SubtitleSegment
import com.lunatic.quicktranslate.feature.session.subtitle.SubtitleMatcher
import com.lunatic.quicktranslate.feature.transcription.MockTranscriptionService
import com.lunatic.quicktranslate.feature.transcription.TranscriptionStatus
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
    private val transcriptionService: MockTranscriptionService,
    private val updateProjectSubtitleStatusUseCase: UpdateProjectSubtitleStatusUseCase,
    private val getProjectSubtitlesUseCase: GetProjectSubtitlesUseCase,
    private val replaceProjectSubtitlesUseCase: ReplaceProjectSubtitlesUseCase,
    private val getProjectLoopConfigUseCase: GetProjectLoopConfigUseCase,
    private val saveProjectLoopConfigUseCase: SaveProjectLoopConfigUseCase
) : ViewModel() {
    private data class LoopSession(
        val startMs: Long,
        val endMs: Long,
        val totalRepeatCount: Int?,
        val remainingCount: Int?
    )

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
    private var loopSession: LoopSession? = null
    private var loopJumpInProgress = false
    private var transcriptionJob: Job? = null
    private var restoredLoopConfig: ProjectLoopConfig? = null

    init {
        if (importedMedia.uri.isNotBlank()) {
            sessionPlayer.setMedia(importedMedia.uri)
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
        persistLoopConfig()
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
        persistLoopConfig()
    }

    private fun startLoop() {
        val currentState = mutableState.value
        val segmentIndexByCurrentPosition = SubtitleMatcher.findActiveIndex(
            segments = currentState.subtitles,
            positionMs = currentState.currentPositionMs
        )
        val selectedStart = currentState.selectedRangeStartIndex
        val selectedEnd = currentState.selectedRangeEndIndex
        val fallbackSingleIndex = if (segmentIndexByCurrentPosition >= 0) {
            segmentIndexByCurrentPosition
        } else {
            currentState.activeSubtitleIndex
        }

        val (loopStartIndex, loopEndIndex) = if (selectedStart != null && selectedEnd != null) {
            minOf(selectedStart, selectedEnd) to maxOf(selectedStart, selectedEnd)
        } else {
            val idx = fallbackSingleIndex
            if (idx < 0 || idx >= currentState.subtitles.size) {
                return
            }
            idx to idx
        }

        val startSegment = currentState.subtitles.getOrNull(loopStartIndex) ?: return
        val endSegment = currentState.subtitles.getOrNull(loopEndIndex) ?: return

        val repeatCount = currentState.loopCountOption.repeatCount
        loopSession = LoopSession(
            startMs = startSegment.startMs,
            endMs = endSegment.endMs,
            totalRepeatCount = repeatCount,
            remainingCount = repeatCount
        )
        loopJumpInProgress = false
        mutableState.value = mutableState.value.copy(
            selectedRangeStartIndex = loopStartIndex,
            selectedRangeEndIndex = loopEndIndex,
            isLooping = true,
            loopRemainingCount = repeatCount
        )
        persistLoopConfig()
        sessionPlayer.seekTo(startSegment.startMs)
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
        val startMs = activeLoop.startMs
        val endMs = activeLoop.endMs

        if (currentPositionMs <= startMs + 150L) {
            loopJumpInProgress = false
        }

        if (!mutableState.value.isLooping || loopJumpInProgress) {
            return
        }

        if (currentPositionMs < endMs) {
            return
        }

        loopJumpInProgress = true
        val remaining = activeLoop.remainingCount
        if (remaining == null) {
            sessionPlayer.seekTo(startMs)
            return
        }

        val updatedRemaining = remaining - 1
        if (updatedRemaining <= 0) {
            sessionPlayer.pause()
            sessionPlayer.seekTo(startMs)
            stopLoop()
            return
        }

        loopSession = activeLoop.copy(remainingCount = updatedRemaining)
        mutableState.value = mutableState.value.copy(
            loopRemainingCount = updatedRemaining
        )
        sessionPlayer.seekTo(startMs)
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
            restoredLoopConfig = loadSavedLoopConfig()
            applyLoopOptionFromSavedConfig(restoredLoopConfig)
            val restored = restoreSavedSubtitles()
            if (!restored) {
                runMockTranscription()
            }
        }
    }

    private suspend fun restoreSavedSubtitles(): Boolean {
        val projectId = importedMedia.projectId
        if (projectId <= 0L) {
            return false
        }
        val storedSubtitles = runCatching {
            getProjectSubtitlesUseCase(projectId)
        }.getOrDefault(emptyList())
        if (storedSubtitles.isEmpty()) {
            return false
        }
        mutableState.value = mutableState.value.copy(
            transcriptionStatus = TranscriptionStatus.SUCCESS,
            transcriptionError = null,
            subtitles = storedSubtitles.map { it.toUiSubtitle() },
            activeSubtitleIndex = -1,
            selectedRangeStartIndex = null,
            selectedRangeEndIndex = null
        )
        applySelectionFromSavedConfig(
            config = restoredLoopConfig,
            subtitleCount = storedSubtitles.size
        )
        return true
    }

    private suspend fun runMockTranscription() {
        stopLoop()
        mutableState.value = mutableState.value.copy(
            transcriptionStatus = TranscriptionStatus.QUEUED,
            transcriptionError = null,
            subtitles = emptyList(),
            activeSubtitleIndex = -1,
            selectedRangeStartIndex = null,
            selectedRangeEndIndex = null
        )
        delay(250L)
        mutableState.value = mutableState.value.copy(
            transcriptionStatus = TranscriptionStatus.PROCESSING
        )
        persistSubtitleStatus(SubtitleStatus.PROCESSING)
        runCatching {
            transcriptionService.transcribe(importedMedia.uri)
        }.onSuccess { segments ->
            val uiSubtitles = segments.mapIndexed { index, segment ->
                SubtitleSegment(
                    id = index + 1L,
                    startMs = segment.startMs,
                    endMs = segment.endMs,
                    text = segment.text
                )
            }
            mutableState.value = mutableState.value.copy(
                transcriptionStatus = TranscriptionStatus.SUCCESS,
                transcriptionError = null,
                subtitles = uiSubtitles
            )
            applySelectionFromSavedConfig(
                config = restoredLoopConfig,
                subtitleCount = uiSubtitles.size
            )
            persistProjectSubtitles(uiSubtitles)
            persistSubtitleStatus(SubtitleStatus.COMPLETED)
        }.onFailure { throwable ->
            mutableState.value = mutableState.value.copy(
                transcriptionStatus = TranscriptionStatus.FAILED,
                transcriptionError = throwable.message ?: "Transcription failed. Please retry.",
                subtitles = emptyList(),
                activeSubtitleIndex = -1
            )
            persistSubtitleStatus(SubtitleStatus.FAILED)
        }
    }

    private fun persistSubtitleStatus(status: SubtitleStatus) {
        val projectId = importedMedia.projectId
        if (projectId <= 0L) {
            return
        }
        viewModelScope.launch {
            runCatching {
                updateProjectSubtitleStatusUseCase(
                    projectId = projectId,
                    status = status
                )
            }
        }
    }

    private suspend fun persistProjectSubtitles(subtitles: List<SubtitleSegment>) {
        val projectId = importedMedia.projectId
        if (projectId <= 0L) {
            return
        }
        runCatching {
            replaceProjectSubtitlesUseCase(
                projectId = projectId,
                subtitles = subtitles.map { it.toProjectSubtitle() }
            )
        }
    }

    private suspend fun loadSavedLoopConfig(): ProjectLoopConfig? {
        val projectId = importedMedia.projectId
        if (projectId <= 0L) {
            return null
        }
        return runCatching {
            getProjectLoopConfigUseCase(projectId)
        }.getOrNull()
    }

    private fun applyLoopOptionFromSavedConfig(config: ProjectLoopConfig?) {
        val option = config?.loopCountOptionName?.let { name ->
            LoopCountOption.entries.firstOrNull { it.name == name }
        } ?: return
        mutableState.value = mutableState.value.copy(
            loopCountOption = option
        )
    }

    private fun applySelectionFromSavedConfig(
        config: ProjectLoopConfig?,
        subtitleCount: Int
    ) {
        if (subtitleCount <= 0) {
            return
        }
        val start = config?.selectedRangeStartIndex
        val end = config?.selectedRangeEndIndex
        if (start == null || end == null) {
            return
        }
        val rangeStart = minOf(start, end)
        val rangeEnd = maxOf(start, end)
        if (rangeStart < 0 || rangeEnd >= subtitleCount) {
            return
        }
        mutableState.value = mutableState.value.copy(
            selectedRangeStartIndex = start,
            selectedRangeEndIndex = end
        )
    }

    private fun persistLoopConfig() {
        val projectId = importedMedia.projectId
        if (projectId <= 0L) {
            return
        }
        val stateSnapshot = mutableState.value
        viewModelScope.launch {
            runCatching {
                saveProjectLoopConfigUseCase(
                    projectId = projectId,
                    config = ProjectLoopConfig(
                        selectedRangeStartIndex = stateSnapshot.selectedRangeStartIndex,
                        selectedRangeEndIndex = stateSnapshot.selectedRangeEndIndex,
                        loopCountOptionName = stateSnapshot.loopCountOption.name
                    )
                )
            }
        }
    }

    private fun ProjectSubtitle.toUiSubtitle(): SubtitleSegment {
        return SubtitleSegment(
            id = sequenceIndex + 1L,
            startMs = startMs,
            endMs = endMs,
            text = text
        )
    }

    private fun SubtitleSegment.toProjectSubtitle(): ProjectSubtitle {
        return ProjectSubtitle(
            sequenceIndex = (id - 1L).toInt(),
            startMs = startMs,
            endMs = endMs,
            text = text
        )
    }

    override fun onCleared() {
        transcriptionJob?.cancel()
        sessionPlayer.release()
        super.onCleared()
    }
}
