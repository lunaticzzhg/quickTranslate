package com.lunatic.quicktranslate.di

import com.lunatic.quicktranslate.data.project.di.projectDataModule
import com.lunatic.quicktranslate.domain.project.di.projectDomainModule
import com.lunatic.quicktranslate.feature.home.di.homeModule
import com.lunatic.quicktranslate.feature.session.di.sessionModule
import org.koin.dsl.module

val appModule = module {
    includes(
        projectDataModule,
        projectDomainModule,
        homeModule,
        sessionModule
    )
}
