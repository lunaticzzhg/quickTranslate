package com.lunatic.quicktranslate.data.project.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectTranscodeTaskDao {
    @Query(
        """
        SELECT * FROM project_transcode_tasks
        WHERE projectId = :projectId AND taskType = :taskType
          AND status IN ('PENDING', 'RUNNING')
        ORDER BY updatedAtEpochMs DESC
        LIMIT 1
        """
    )
    suspend fun getActiveTask(projectId: Long, taskType: String): ProjectTranscodeTaskEntity?

    @Query(
        """
        SELECT * FROM project_transcode_tasks
        WHERE projectId = :projectId AND taskType = :taskType
        ORDER BY updatedAtEpochMs DESC
        LIMIT 1
        """
    )
    fun observeLatestByProject(projectId: Long, taskType: String): Flow<ProjectTranscodeTaskEntity?>

    @Query(
        """
        SELECT * FROM project_transcode_tasks
        WHERE taskType = :taskType
          AND status IN ('RUNNING', 'PENDING', 'FAILED')
        ORDER BY
            CASE status
                WHEN 'RUNNING' THEN 0
                WHEN 'PENDING' THEN 1
                WHEN 'FAILED' THEN 2
                ELSE 3
            END ASC,
            boostSeq DESC,
            updatedAtEpochMs DESC
        """
    )
    fun observeTasksForDashboard(taskType: String): Flow<List<ProjectTranscodeTaskEntity>>

    @Query("SELECT MAX(boostSeq) FROM project_transcode_tasks")
    suspend fun getMaxBoostSeq(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: ProjectTranscodeTaskEntity): Long

    @Query(
        """
        UPDATE project_transcode_tasks
        SET mediaUri = :mediaUri,
            boostSeq = :boostSeq,
            stage = 'QUEUED',
            progress = NULL,
            errorMessage = NULL,
            updatedAtEpochMs = :updatedAtEpochMs
        WHERE id = :taskId
          AND status = 'PENDING'
        """
    )
    suspend fun updatePendingTask(
        taskId: Long,
        mediaUri: String,
        boostSeq: Long,
        updatedAtEpochMs: Long
    ): Int

    @Query(
        """
        UPDATE project_transcode_tasks
        SET mediaUri = :mediaUri,
            updatedAtEpochMs = :updatedAtEpochMs
        WHERE id = :taskId
          AND status = 'RUNNING'
        """
    )
    suspend fun updateRunningTaskMedia(
        taskId: Long,
        mediaUri: String,
        updatedAtEpochMs: Long
    ): Int

    @Query(
        """
        UPDATE project_transcode_tasks
        SET boostSeq = :boostSeq,
            updatedAtEpochMs = :updatedAtEpochMs
        WHERE projectId = :projectId
          AND taskType = :taskType
          AND status = 'PENDING'
        """
    )
    suspend fun bumpPendingTask(
        projectId: Long,
        taskType: String,
        boostSeq: Long,
        updatedAtEpochMs: Long
    ): Int

    @Query(
        """
        SELECT * FROM project_transcode_tasks
        WHERE status = 'PENDING'
        ORDER BY boostSeq DESC, basePriority DESC, createdAtEpochMs ASC
        LIMIT 1
        """
    )
    suspend fun getTopPendingTask(): ProjectTranscodeTaskEntity?

    @Query(
        """
        UPDATE project_transcode_tasks
        SET status = 'RUNNING',
            stage = 'RESOLVING',
            progress = 0,
            startedAtEpochMs = :startedAtEpochMs,
            updatedAtEpochMs = :startedAtEpochMs
        WHERE id = :taskId AND status = 'PENDING'
        """
    )
    suspend fun markRunningIfPending(taskId: Long, startedAtEpochMs: Long): Int

    @Query(
        """
        UPDATE project_transcode_tasks
        SET status = 'SUCCEEDED',
            stage = 'SUCCEEDED',
            progress = 100,
            errorMessage = NULL,
            finishedAtEpochMs = :finishedAtEpochMs,
            updatedAtEpochMs = :finishedAtEpochMs
        WHERE id = :taskId
        """
    )
    suspend fun markSucceeded(taskId: Long, finishedAtEpochMs: Long)

    @Query(
        """
        UPDATE project_transcode_tasks
        SET status = 'FAILED',
            stage = 'FAILED',
            errorMessage = :errorMessage,
            finishedAtEpochMs = :finishedAtEpochMs,
            updatedAtEpochMs = :finishedAtEpochMs
        WHERE id = :taskId
        """
    )
    suspend fun markFailed(taskId: Long, errorMessage: String?, finishedAtEpochMs: Long)

    @Query(
        """
        UPDATE project_transcode_tasks
        SET status = 'PENDING',
            stage = 'QUEUED',
            progress = NULL,
            errorMessage = NULL,
            startedAtEpochMs = NULL,
            finishedAtEpochMs = NULL,
            updatedAtEpochMs = :updatedAtEpochMs
        WHERE status = 'RUNNING'
        """
    )
    suspend fun restoreRunningToPending(updatedAtEpochMs: Long)

    @Query(
        """
        UPDATE project_transcode_tasks
        SET stage = :stage,
            progress = :progress,
            updatedAtEpochMs = :updatedAtEpochMs
        WHERE id = :taskId
          AND status = 'RUNNING'
        """
    )
    suspend fun updateRunningTaskProgress(
        taskId: Long,
        stage: String,
        progress: Int?,
        updatedAtEpochMs: Long
    )

    @Transaction
    suspend fun claimNextPendingTask(nowEpochMs: Long): ProjectTranscodeTaskEntity? {
        repeat(8) {
            val candidate = getTopPendingTask() ?: return null
            val updatedRows = markRunningIfPending(
                taskId = candidate.id,
                startedAtEpochMs = nowEpochMs
            )
            if (updatedRows > 0) {
                return candidate.copy(
                    status = "RUNNING",
                    stage = "RESOLVING",
                    progress = 0,
                    startedAtEpochMs = nowEpochMs,
                    updatedAtEpochMs = nowEpochMs
                )
            }
        }
        return null
    }
}
