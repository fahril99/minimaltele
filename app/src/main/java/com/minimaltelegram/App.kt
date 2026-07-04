package com.minimaltelegram

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        TdClient.init(this)
    }
}
