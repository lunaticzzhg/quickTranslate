package com.lunatic.quicktranslate.domain.project.repository

interface ProjectTranscodeQueueEngine {
    fun signal()
    suspend fun restoreAndSignal()
}
