package com.lunatic.quicktranslate.data.project.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ProjectEntity::class],
    version = 2,
    exportSchema = false
)
abstract class QuickTranslateDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
}
