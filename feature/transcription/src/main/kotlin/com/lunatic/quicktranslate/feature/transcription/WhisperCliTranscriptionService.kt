package com.lunatic.quicktranslate.feature.transcription

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WhisperCliTranscriptionService(
    private val config: WhisperCliConfig
) : TranscriptionService {
    private val progressRegex = Regex("""progress\s*=\s*(\d{1,3})%""")

    override suspend fun transcribe(
        mediaPath: String,
        onProgress: ((Int) -> Unit)?
    ): List<TranscriptionSegment> {
        require(mediaPath.isNotBlank()) { "Media path is required for transcription." }
        require(config.isValid) {
            "Whisper CLI config is invalid. Please set cli path and model path."
        }
        return withContext(Dispatchers.IO) {
            val mediaFile = File(mediaPath)
            require(mediaFile.exists()) { "Media file does not exist: $mediaPath" }

            val outputDir = File(
                System.getProperty("java.io.tmpdir")
                    ?: mediaFile.parentFile?.absolutePath
                    ?: "."
            ).also { it.mkdirs() }
            val outputBase = File(
                outputDir,
                "qt_whisper_${mediaFile.nameWithoutExtension}_${System.currentTimeMillis()}"
            )
            val outputSrt = File(outputBase.absolutePath + ".srt")
            val cliArgs = listOf(
                "-m", config.modelPath,
                "-f", mediaFile.absolutePath,
                "-l", config.language,
                "--print-progress",
                "-osrt",
                "-of", outputBase.absolutePath
            )
            val command = if (config.cliPath.endsWith(".so")) {
                listOf("/system/bin/linker64", config.cliPath) + cliArgs
            } else {
                listOf(config.cliPath) + cliArgs
            }
            val process = try {
                ProcessBuilder(command).apply {
                    if (config.cliPath.endsWith(".so")) {
                        val libDir = File(config.cliPath).parentFile?.absolutePath.orEmpty()
                        if (libDir.isNotBlank()) {
                            environment()["LD_LIBRARY_PATH"] = libDir
                        }
                    }
                    redirectErrorStream(true)
                }.start()
            } catch (error: Exception) {
                throw IllegalStateException(
                    "Failed to start whisper process. Command: ${command.joinToString(" ")}",
                    error
                )
            }
            val logsBuilder = StringBuilder()
            process.inputStream.bufferedReader().use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    logsBuilder.appendLine(line)
                    parseProgress(line)?.let { onProgress?.invoke(it) }
                }
            }
            val logs = logsBuilder.toString()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                throw IllegalStateException(
                    "whisper.cpp exited with code=$exitCode. Output: ${logs.take(400)}"
                )
            }
            val resolvedSrt = resolveOutputSrt(outputBase, outputSrt)
            val segments = SrtParser.parse(resolvedSrt)
            if (segments.isEmpty()) {
                throw IllegalStateException(
                    "Whisper completed but produced no subtitles. " +
                        "Output file: ${resolvedSrt.absolutePath}. " +
                        "Logs: ${logs.take(500)}"
                )
            }
            onProgress?.invoke(100)
            cleanupSrtArtifacts(outputBase.parentFile ?: File("."), outputBase.name)
            segments
        }
    }

    private fun parseProgress(line: String): Int? {
        val value = progressRegex.find(line)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
        return value?.coerceIn(0, 100)
    }

    private fun resolveOutputSrt(outputBase: File, preferred: File): File {
        if (preferred.exists()) {
            return preferred
        }
        val parent = outputBase.parentFile ?: return preferred
        return parent.listFiles()
            ?.filter { it.isFile && it.extension.equals("srt", ignoreCase = true) }
            ?.firstOrNull { it.name.startsWith(outputBase.name) }
            ?: preferred
    }

    private fun cleanupSrtArtifacts(parent: File, prefix: String) {
        parent.listFiles()
            ?.filter { it.isFile && it.extension.equals("srt", ignoreCase = true) }
            ?.filter { it.name.startsWith(prefix) }
            ?.forEach { it.delete() }
    }
}
