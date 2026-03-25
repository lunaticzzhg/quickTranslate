package com.lunatic.quicktranslate.data.project.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "project_playback_states")
data class ProjectPlaybackStateEntity(
    @PrimaryKey
    val projectId: Long,
    val playbackPositionMs: Long,
    val updatedAtEpochMs: Long
)
