package com.lunatic.quicktranslate.feature.transcription

import java.io.File

object SrtParser {
    private val timingPattern = Regex(
        pattern = "(\\d{2}):(\\d{2}):(\\d{2}),(\\d{3})\\s+-->\\s+(\\d{2}):(\\d{2}):(\\d{2}),(\\d{3})"
    )

    fun parse(file: File): List<TranscriptionSegment> {
        if (!file.exists()) {
            return emptyList()
        }
        val blocks = file.readText()
            .trim()
            .split(Regex("\\r?\\n\\r?\\n"))
        return blocks.mapNotNull { block ->
            val lines = block.lines().map { it.trim() }.filter { it.isNotBlank() }
            if (lines.size < 2) {
                return@mapNotNull null
            }
            val timingLine = lines[1]
            val match = timingPattern.find(timingLine) ?: return@mapNotNull null
            val startMs = match.groups.toTimestampMs(
                hourIdx = 1,
                minuteIdx = 2,
                secondIdx = 3,
                millisIdx = 4
            )
            val endMs = match.groups.toTimestampMs(
                hourIdx = 5,
                minuteIdx = 6,
                secondIdx = 7,
                millisIdx = 8
            )
            val text = lines.drop(2).joinToString(separator = " ").trim()
            if (text.isBlank()) {
                return@mapNotNull null
            }
            TranscriptionSegment(
                startMs = startMs,
                endMs = endMs,
                text = text
            )
        }
    }

    private fun MatchGroupCollection.toTimestampMs(
        hourIdx: Int,
        minuteIdx: Int,
        secondIdx: Int,
        millisIdx: Int
    ): Long {
        val hours = this[hourIdx]?.value?.toLongOrNull() ?: 0L
        val minutes = this[minuteIdx]?.value?.toLongOrNull() ?: 0L
        val seconds = this[secondIdx]?.value?.toLongOrNull() ?: 0L
        val millis = this[millisIdx]?.value?.toLongOrNull() ?: 0L
        return ((hours * 3600L + minutes * 60L + seconds) * 1000L) + millis
    }
}
