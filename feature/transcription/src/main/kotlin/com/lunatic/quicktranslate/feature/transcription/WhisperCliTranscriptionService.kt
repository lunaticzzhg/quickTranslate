package com.lunatic.quicktranslate.feature.transcription

import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WhisperCliTranscriptionService(
    private val config: WhisperCliConfig
) : TranscriptionService {
    private val progressRegex = Regex("""progress\s*=\s*(\d{1,3})%""")
    private val segmentRegex = Regex(
        """^\[(\d{2}:\d{2}:\d{2}[.,]\d{3})\s*-->\s*(\d{2}:\d{2}:\d{2}[.,]\d{3})\]\s*(.*)$"""
    )

    override suspend fun transcribe(
        mediaPath: String,
        onProgress: ((Int) -> Unit)?,
        onPartialResult: ((List<TranscriptionSegment>) -> Unit)?
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
            val logReadDone = AtomicBoolean(false)
            val partialFromStdout = AtomicReference<List<TranscriptionSegment>>(emptyList())
            val logsBuilder = StringBuilder()
            val logThread = Thread {
                process.inputStream.bufferedReader().use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        logsBuilder.appendLine(line)
                        parseProgress(line)?.let { onProgress?.invoke(it) }
                        parseSegmentLine(line)?.let { segment ->
                            val updated = partialFromStdout.get() + segment
                            partialFromStdout.set(updated)
                            onPartialResult?.invoke(updated)
                        }
                    }
                }
                logReadDone.set(true)
            }.apply { start() }

            var emittedSignature = ""
            while (process.isAlive) {
                val partial = SrtParser.parse(resolveOutputSrt(outputBase, outputSrt))
                val signature = buildSegmentsSignature(partial)
                if (partial.isNotEmpty() && signature != emittedSignature) {
                    emittedSignature = signature
                    onPartialResult?.invoke(partial)
                }
                Thread.sleep(250L)
            }
            while (!logReadDone.get()) {
                Thread.sleep(20L)
            }
            logThread.join(1000L)
            val logs = logsBuilder.toString()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                throw IllegalStateException(
                    "whisper.cpp exited with code=$exitCode. Output: ${logs.take(400)}"
                )
            }
            val resolvedSrt = resolveOutputSrt(outputBase, outputSrt)
            val segments = if (resolvedSrt.exists()) {
                SrtParser.parse(resolvedSrt)
            } else {
                partialFromStdout.get()
            }
            if (segments.isEmpty()) {
                val stdoutSegments = partialFromStdout.get()
                if (stdoutSegments.isNotEmpty()) {
                    onPartialResult?.invoke(stdoutSegments)
                    onProgress?.invoke(100)
                    return@withContext stdoutSegments
                }
            }
            if (segments.isNotEmpty()) {
                onPartialResult?.invoke(segments)
            }
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

    private fun parseSegmentLine(line: String): TranscriptionSegment? {
        val match = segmentRegex.find(line.trim()) ?: return null
        val start = parseTimestampMs(match.groupValues[1]) ?: return null
        val end = parseTimestampMs(match.groupValues[2]) ?: return null
        val text = match.groupValues[3].trim()
        if (text.isBlank()) return null
        return TranscriptionSegment(
            startMs = start,
            endMs = end,
            text = text
        )
    }

    private fun parseTimestampMs(raw: String): Long? {
        val normalized = raw.replace(',', '.')
        val parts = normalized.split(':')
        if (parts.size != 3) return null
        val secParts = parts[2].split('.')
        if (secParts.size != 2) return null
        val h = parts[0].toLongOrNull() ?: return null
        val m = parts[1].toLongOrNull() ?: return null
        val s = secParts[0].toLongOrNull() ?: return null
        val ms = secParts[1].toLongOrNull() ?: return null
        return (((h * 60 + m) * 60) + s) * 1000 + ms
    }

    private fun buildSegmentsSignature(segments: List<TranscriptionSegment>): String {
        if (segments.isEmpty()) return ""
        val last = segments.last()
        return "${segments.size}:${last.startMs}:${last.endMs}:${last.text.hashCode()}"
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
