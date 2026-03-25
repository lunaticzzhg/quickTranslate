package com.lunatic.quicktranslate.data.project.di

import androidx.room.Room
import com.lunatic.quicktranslate.data.project.RoomProjectRepository
import com.lunatic.quicktranslate.data.project.local.QuickTranslateDatabase
import com.lunatic.quicktranslate.domain.project.repository.ProjectRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val projectDataModule = module {
    single {
        Room.databaseBuilder(
            androidContext(),
            QuickTranslateDatabase::class.java,
            "quick_translate.db"
        ).build()
    }
    single { get<QuickTranslateDatabase>().projectDao() }
    single<ProjectRepository> { RoomProjectRepository(get()) }
}
