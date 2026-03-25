package com.lunatic.quicktranslate.domain.project.model

data class Project(
    val id: Long,
    val displayName: String,
    val mediaUri: String,
    val mimeType: String,
    val durationMs: Long,
    val updatedAtEpochMs: Long
)
