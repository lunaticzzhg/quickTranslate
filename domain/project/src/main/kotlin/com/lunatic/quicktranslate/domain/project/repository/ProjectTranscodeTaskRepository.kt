package com.lunatic.quicktranslate.domain.project.repository

import com.lunatic.quicktranslate.domain.project.model.ProjectTranscodeTask
import kotlinx.coroutines.flow.Flow

interface ProjectTranscodeTaskRepository {
    suspend fun enqueueOrRefresh(
        projectId: Long,
        mediaUri: String,
        taskType: String
    )

    suspend fun bumpPendingTaskPriority(
        projectId: Long,
        taskType: String
    ): Boolean

    suspend fun claimNextPendingTask(nowEpochMs: Long): ProjectTranscodeTask?

    suspend fun markTaskSucceeded(taskId: Long, finishedAtEpochMs: Long)

    suspend fun markTaskFailed(taskId: Long, message: String?, finishedAtEpochMs: Long)

    suspend fun restoreRunningTasksToPending(updatedAtEpochMs: Long)

    fun observeProjectTask(
        projectId: Long,
        taskType: String
    ): Flow<ProjectTranscodeTask?>

    fun observeTasksForDashboard(taskType: String): Flow<List<ProjectTranscodeTask>>
}
