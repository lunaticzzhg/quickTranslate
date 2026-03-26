package com.lunatic.quicktranslate.data.project.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProjectEntity::class,
        ProjectSubtitleEntity::class,
        ProjectLoopConfigEntity::class,
        ProjectPlaybackStateEntity::class,
        ProjectTranscodeTaskEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class QuickTranslateDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun projectSubtitleDao(): ProjectSubtitleDao
    abstract fun projectLoopConfigDao(): ProjectLoopConfigDao
    abstract fun projectPlaybackStateDao(): ProjectPlaybackStateDao
    abstract fun projectTranscodeTaskDao(): ProjectTranscodeTaskDao
}
