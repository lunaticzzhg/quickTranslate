package com.lunatic.quicktranslate.feature.transcription

data class WhisperCliConfig(
    val cliPath: String,
    val modelPath: String,
    val language: String = "en",
    val threads: Int = 0
) {
    val isValid: Boolean
        get() = cliPath.isNotBlank() && modelPath.isNotBlank()
}
