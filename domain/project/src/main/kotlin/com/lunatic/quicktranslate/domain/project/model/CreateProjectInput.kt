package com.lunatic.quicktranslate.domain.project.model

data class CreateProjectInput(
    val displayName: String,
    val mediaUri: String,
    val mimeType: String,
    val durationMs: Long
)
