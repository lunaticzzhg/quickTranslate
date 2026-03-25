package com.lunatic.quicktranslate.feature.session.di

import com.lunatic.quicktranslate.feature.session.SessionViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val sessionModule = module {
    viewModelOf(::SessionViewModel)
}
