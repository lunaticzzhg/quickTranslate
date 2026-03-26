package com.lunatic.quicktranslate.data.project.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "project_transcode_tasks",
    indices = [
        Index("projectId"),
        Index(value = ["projectId", "taskType"])
    ]
)
data class ProjectTranscodeTaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val projectId: Long,
    val mediaUri: String,
    val taskType: String,
    val status: String,
    val basePriority: Int,
    val boostSeq: Long,
    val retryCount: Int,
    val stage: String,
    val progress: Int?,
    val errorMessage: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val startedAtEpochMs: Long?,
    val finishedAtEpochMs: Long?
)
