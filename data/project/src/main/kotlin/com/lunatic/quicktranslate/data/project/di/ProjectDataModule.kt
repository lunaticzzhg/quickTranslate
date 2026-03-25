package com.lunatic.quicktranslate.data.project.di

import androidx.room.Room
import com.lunatic.quicktranslate.data.project.RoomProjectLoopConfigRepository
import com.lunatic.quicktranslate.data.project.RoomProjectRepository
import com.lunatic.quicktranslate.data.project.RoomProjectSubtitleRepository
import com.lunatic.quicktranslate.data.project.local.QuickTranslateDatabase
import com.lunatic.quicktranslate.domain.project.repository.ProjectLoopConfigRepository
import com.lunatic.quicktranslate.domain.project.repository.ProjectRepository
import com.lunatic.quicktranslate.domain.project.repository.ProjectSubtitleRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val projectDataModule = module {
    single {
        Room.databaseBuilder(
            androidContext(),
            QuickTranslateDatabase::class.java,
            "quick_translate.db"
        )
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }
    single { get<QuickTranslateDatabase>().projectDao() }
    single { get<QuickTranslateDatabase>().projectSubtitleDao() }
    single { get<QuickTranslateDatabase>().projectLoopConfigDao() }
    single<ProjectRepository> { RoomProjectRepository(get()) }
    single<ProjectSubtitleRepository> { RoomProjectSubtitleRepository(get()) }
    single<ProjectLoopConfigRepository> { RoomProjectLoopConfigRepository(get()) }
}
