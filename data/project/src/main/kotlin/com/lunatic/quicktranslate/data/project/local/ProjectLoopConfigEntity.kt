package com.lunatic.quicktranslate.data.project.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "project_loop_configs")
data class ProjectLoopConfigEntity(
    @PrimaryKey
    val projectId: Long,
    val selectedRangeStartIndex: Int?,
    val selectedRangeEndIndex: Int?,
    val loopCountOptionName: String,
    val updatedAtEpochMs: Long
)
