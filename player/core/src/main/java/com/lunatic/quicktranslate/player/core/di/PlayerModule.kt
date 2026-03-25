package com.lunatic.quicktranslate.player.core.di

import com.lunatic.quicktranslate.player.core.ExoSessionPlayer
import com.lunatic.quicktranslate.player.core.SessionPlayer
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val playerModule = module {
    factory<SessionPlayer> {
        ExoSessionPlayer(androidContext())
    }
}
