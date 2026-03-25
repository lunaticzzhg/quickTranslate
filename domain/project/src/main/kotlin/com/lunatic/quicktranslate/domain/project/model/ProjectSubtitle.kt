package com.lunatic.quicktranslate.domain.project.model

data class ProjectSubtitle(
    val sequenceIndex: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String
)
