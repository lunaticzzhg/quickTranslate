package com.lunatic.quicktranslate.data.project

import com.lunatic.quicktranslate.data.project.local.ProjectTranscodeTaskDao
import com.lunatic.quicktranslate.data.project.local.ProjectTranscodeTaskEntity
import com.lunatic.quicktranslate.domain.project.model.ProjectTranscodeTask
import com.lunatic.quicktranslate.domain.project.model.ProjectTranscodeTaskStage
import com.lunatic.quicktranslate.domain.project.model.ProjectTranscodeTaskStatus
import com.lunatic.quicktranslate.domain.project.repository.ProjectTranscodeTaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomProjectTranscodeTaskRepository(
    private val transcodeTaskDao: ProjectTranscodeTaskDao
) : ProjectTranscodeTaskRepository {
    override suspend fun enqueueOrRefresh(
        projectId: Long,
        mediaUri: String,
        taskType: String
    ) {
        val now = System.currentTimeMillis()
        val nextBoost = (transcodeTaskDao.getMaxBoostSeq() ?: 0L) + 1L
        val active = transcodeTaskDao.getActiveTask(
            projectId = projectId,
            taskType = taskType
        )
        if (active != null) {
            if (active.status == ProjectTranscodeTaskStatus.PENDING.name) {
                transcodeTaskDao.updatePendingTask(
                    taskId = active.id,
                    mediaUri = mediaUri,
                    boostSeq = nextBoost,
                    updatedAtEpochMs = now
                )
            } else {
                transcodeTaskDao.updateRunningTaskMedia(
                    taskId = active.id,
                    mediaUri = mediaUri,
                    updatedAtEpochMs = now
                )
            }
            return
        }
        transcodeTaskDao.insert(
            ProjectTranscodeTaskEntity(
                projectId = projectId,
                mediaUri = mediaUri,
                taskType = taskType,
                status = ProjectTranscodeTaskStatus.PENDING.name,
                basePriority = 0,
                boostSeq = nextBoost,
                retryCount = 0,
                stage = ProjectTranscodeTaskStage.QUEUED.name,
                progress = null,
                errorMessage = null,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
                startedAtEpochMs = null,
                finishedAtEpochMs = null
            )
        )
    }

    override suspend fun bumpPendingTaskPriority(
        projectId: Long,
        taskType: String
    ): Boolean {
        val now = System.currentTimeMillis()
        val nextBoost = (transcodeTaskDao.getMaxBoostSeq() ?: 0L) + 1L
        val updated = transcodeTaskDao.bumpPendingTask(
            projectId = projectId,
            taskType = taskType,
            boostSeq = nextBoost,
            updatedAtEpochMs = now
        )
        return updated > 0
    }

    override suspend fun claimNextPendingTask(nowEpochMs: Long): ProjectTranscodeTask? {
        return transcodeTaskDao.claimNextPendingTask(nowEpochMs)?.toDomain()
    }

    override suspend fun markTaskSucceeded(taskId: Long, finishedAtEpochMs: Long) {
        transcodeTaskDao.markSucceeded(
            taskId = taskId,
            finishedAtEpochMs = finishedAtEpochMs
        )
    }

    override suspend fun markTaskFailed(taskId: Long, message: String?, finishedAtEpochMs: Long) {
        transcodeTaskDao.markFailed(
            taskId = taskId,
            errorMessage = message,
            finishedAtEpochMs = finishedAtEpochMs
        )
    }

    override suspend fun updateRunningTaskProgress(
        taskId: Long,
        stage: ProjectTranscodeTaskStage,
        progress: Int?
    ) {
        transcodeTaskDao.updateRunningTaskProgress(
            taskId = taskId,
            stage = stage.name,
            progress = progress?.coerceIn(0, 100),
            updatedAtEpochMs = System.currentTimeMillis()
        )
    }

    override suspend fun restoreRunningTasksToPending(updatedAtEpochMs: Long) {
        transcodeTaskDao.restoreRunningToPending(
            updatedAtEpochMs = updatedAtEpochMs
        )
    }

    override fun observeProjectTask(projectId: Long, taskType: String): Flow<ProjectTranscodeTask?> {
        return transcodeTaskDao.observeLatestByProject(
            projectId = projectId,
            taskType = taskType
        ).map { it?.toDomain() }
    }

    override fun observeTasksForDashboard(taskType: String): Flow<List<ProjectTranscodeTask>> {
        return transcodeTaskDao.observeTasksForDashboard(taskType)
            .map { list -> list.map { it.toDomain() } }
    }
}
