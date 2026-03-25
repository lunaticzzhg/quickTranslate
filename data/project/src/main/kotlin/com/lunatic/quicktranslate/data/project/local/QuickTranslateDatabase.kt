package com.lunatic.quicktranslate.data.project.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProjectEntity::class,
        ProjectSubtitleEntity::class,
        ProjectLoopConfigEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class QuickTranslateDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun projectSubtitleDao(): ProjectSubtitleDao
    abstract fun projectLoopConfigDao(): ProjectLoopConfigDao
}
