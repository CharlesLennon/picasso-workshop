package com.picassoworkshop.app

import android.app.Application
import timber.log.Timber

class PicassoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        Timber.i("PicassoWorkshop Application initialized")
    }
}
