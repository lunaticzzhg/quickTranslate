package com.lunatic.quicktranslate.feature.session.loop

import com.lunatic.quicktranslate.domain.project.model.ProjectLoopConfig
import com.lunatic.quicktranslate.domain.project.usecase.GetProjectLoopConfigUseCase
import com.lunatic.quicktranslate.domain.project.usecase.SaveProjectLoopConfigUseCase
import com.lunatic.quicktranslate.feature.session.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class SessionLoopController(
    private val getProjectLoopConfigUseCase: GetProjectLoopConfigUseCase,
    private val saveProjectLoopConfigUseCase: SaveProjectLoopConfigUseCase
) {
    private data class LoopSession(
        val startMs: Long,
        val endMs: Long,
        val remainingCount: Int?
    )

    data class StartResult(
        val state: SessionState,
        val seekToMs: Long
    )

    data class EvaluationResult(
        val state: SessionState,
        val seekToMs: Long? = null,
        val shouldPause: Boolean = false
    )

    private var loopSession: LoopSession? = null
    private var loopJumpInProgress = false
    private var restoredLoopConfig: ProjectLoopConfig? = null

    fun start(
        state: SessionState,
        fallbackSingleIndex: Int
    ): StartResult? {
        val selectedStart = state.selectedRangeStartIndex
        val selectedEnd = state.selectedRangeEndIndex

        val (loopStartIndex, loopEndIndex) = if (selectedStart != null && selectedEnd != null) {
            minOf(selectedStart, selectedEnd) to maxOf(selectedStart, selectedEnd)
        } else {
            val idx = fallbackSingleIndex
            if (idx < 0 || idx >= state.subtitles.size) {
                return null
            }
            idx to idx
        }

        val startSegment = state.subtitles.getOrNull(loopStartIndex) ?: return null
        val endSegment = state.subtitles.getOrNull(loopEndIndex) ?: return null
        val repeatCount = state.loopCountOption.repeatCount

        loopSession = LoopSession(
            startMs = startSegment.startMs,
            endMs = endSegment.endMs,
            remainingCount = repeatCount
        )
        loopJumpInProgress = false
        return StartResult(
            state = state.copy(
                selectedRangeStartIndex = loopStartIndex,
                selectedRangeEndIndex = loopEndIndex,
                isLooping = true,
                loopRemainingCount = repeatCount
            ),
            seekToMs = startSegment.startMs
        )
    }

    fun stop(state: SessionState): SessionState {
        clear()
        return state.copy(
            isLooping = false,
            loopRemainingCount = null
        )
    }

    fun evaluate(
        state: SessionState,
        currentPositionMs: Long
    ): EvaluationResult {
        val activeLoop = loopSession ?: return EvaluationResult(state = state)
        val startMs = activeLoop.startMs
        val endMs = activeLoop.endMs

        if (currentPositionMs <= startMs + 150L) {
            loopJumpInProgress = false
        }

        if (!state.isLooping || loopJumpInProgress) {
            return EvaluationResult(state = state)
        }

        if (currentPositionMs < endMs) {
            return EvaluationResult(state = state)
        }

        loopJumpInProgress = true
        val remaining = activeLoop.remainingCount
        if (remaining == null) {
            return EvaluationResult(
                state = state,
                seekToMs = startMs
            )
        }

        val updatedRemaining = remaining - 1
        if (updatedRemaining <= 0) {
            val stoppedState = stop(state)
            return EvaluationResult(
                state = stoppedState,
                seekToMs = startMs,
                shouldPause = true
            )
        }

        loopSession = activeLoop.copy(remainingCount = updatedRemaining)
        return EvaluationResult(
            state = state.copy(loopRemainingCount = updatedRemaining),
            seekToMs = startMs
        )
    }

    fun clear() {
        loopSession = null
        loopJumpInProgress = false
    }

    suspend fun restorePersistedConfig(
        projectId: Long,
        state: SessionState
    ): SessionState {
        if (projectId <= 0L) {
            restoredLoopConfig = null
            return state
        }
        restoredLoopConfig = runCatching {
            getProjectLoopConfigUseCase(projectId)
        }.getOrNull()
        val option = restoredLoopConfig?.loopCountOptionName?.let { name ->
            com.lunatic.quicktranslate.feature.session.LoopCountOption.entries.firstOrNull {
                it.name == name
            }
        } ?: return state
        return state.copy(loopCountOption = option)
    }

    fun applyRestoredSelectionIfValid(state: SessionState): SessionState {
        val config = restoredLoopConfig ?: return state
        val subtitleCount = state.subtitles.size
        if (subtitleCount <= 0) {
            return state
        }
        val start = config.selectedRangeStartIndex
        val end = config.selectedRangeEndIndex
        if (start == null || end == null) {
            return state
        }
        val rangeStart = minOf(start, end)
        val rangeEnd = maxOf(start, end)
        if (rangeStart < 0 || rangeEnd >= subtitleCount) {
            return state
        }
        return state.copy(
            selectedRangeStartIndex = start,
            selectedRangeEndIndex = end
        )
    }

    fun persistConfigAsync(
        projectId: Long,
        state: SessionState,
        scope: CoroutineScope
    ) {
        if (projectId <= 0L) {
            return
        }
        scope.launch {
            runCatching {
                saveProjectLoopConfigUseCase(
                    projectId = projectId,
                    config = ProjectLoopConfig(
                        selectedRangeStartIndex = state.selectedRangeStartIndex,
                        selectedRangeEndIndex = state.selectedRangeEndIndex,
                        loopCountOptionName = state.loopCountOption.name
                    )
                )
            }
        }
    }
}
