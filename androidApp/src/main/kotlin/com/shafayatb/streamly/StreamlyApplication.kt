package com.shafayatb.streamly

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import com.shafayatb.streamly.app.AppInfo
import com.shafayatb.streamly.core.media.di.mediaModule
import com.shafayatb.streamly.data.di.dataModule
import com.shafayatb.streamly.di.presentationModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.dsl.module

class StreamlyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@StreamlyApplication)
            modules(dataModule, mediaModule, presentationModule, module { single { appInfo() } })
        }
    }

    private fun appInfo(): AppInfo {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        return AppInfo(versionName = info.versionName.orEmpty(), versionCode = versionCode)
    }
}
