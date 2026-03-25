package com.lunatic.quicktranslate.di

import com.lunatic.quicktranslate.feature.home.di.homeModule
import org.koin.dsl.module

val appModule = module {
    includes(homeModule)
}
