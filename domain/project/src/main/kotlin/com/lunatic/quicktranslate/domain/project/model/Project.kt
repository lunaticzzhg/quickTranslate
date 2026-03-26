package com.lunatic.quicktranslate.domain.project.model

data class Project(
    val id: Long,
    val displayName: String,
    val mediaUri: String,
    val sourceUri: String,
    val mimeType: String,
    val durationMs: Long,
    val subtitleStatus: SubtitleStatus,
    val updatedAtEpochMs: Long
)
