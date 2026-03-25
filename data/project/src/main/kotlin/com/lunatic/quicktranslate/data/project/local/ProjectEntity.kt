package com.lunatic.quicktranslate.data.project.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val displayName: String,
    val mediaUri: String,
    val mimeType: String,
    val durationMs: Long,
    val updatedAtEpochMs: Long
)
