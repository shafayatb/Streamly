package com.shafayatb.streamly

import android.app.Application
import com.shafayatb.streamly.data.di.dataModule
import com.shafayatb.streamly.di.presentationModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class StreamlyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@StreamlyApplication)
            modules(dataModule, presentationModule)
        }
    }
}
