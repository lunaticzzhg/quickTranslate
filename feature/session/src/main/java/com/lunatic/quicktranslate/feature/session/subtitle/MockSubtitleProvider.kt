package com.lunatic.quicktranslate.feature.session.subtitle

object MockSubtitleProvider {
    fun provide(): List<SubtitleSegment> {
        return listOf(
            SubtitleSegment(1, 0L, 3000L, "Welcome to QuickTranslate practice session."),
            SubtitleSegment(2, 3000L, 6500L, "Choose a sentence and replay it many times."),
            SubtitleSegment(3, 6500L, 9800L, "Focus on pronunciation, rhythm, and stress."),
            SubtitleSegment(4, 9800L, 13000L, "You can click subtitles to jump quickly."),
            SubtitleSegment(5, 13000L, 17000L, "Consistent review helps long-term listening growth.")
        )
    }
}
