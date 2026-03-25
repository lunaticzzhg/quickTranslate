package com.lunatic.quicktranslate.feature.home.di

import com.lunatic.quicktranslate.feature.home.HomeViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val homeModule = module {
    viewModelOf(::HomeViewModel)
}
