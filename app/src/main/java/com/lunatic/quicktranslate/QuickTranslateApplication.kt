package com.lunatic.quicktranslate

import android.app.Application
import com.lunatic.quicktranslate.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class QuickTranslateApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@QuickTranslateApplication)
            modules(appModule)
        }
    }
}
