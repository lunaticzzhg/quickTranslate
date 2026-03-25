package com.lunatic.quicktranslate.data.project.local

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "project_subtitles",
    primaryKeys = ["projectId", "sequenceIndex"],
    indices = [Index("projectId")]
)
data class ProjectSubtitleEntity(
    val projectId: Long,
    val sequenceIndex: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String
)
