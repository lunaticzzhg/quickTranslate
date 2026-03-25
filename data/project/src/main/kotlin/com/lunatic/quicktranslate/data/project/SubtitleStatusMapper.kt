package com.lunatic.quicktranslate.data.project

import com.lunatic.quicktranslate.domain.project.model.SubtitleStatus

fun String.toSubtitleStatus(): SubtitleStatus {
    return SubtitleStatus.entries.firstOrNull { it.name == this } ?: SubtitleStatus.NOT_STARTED
}
