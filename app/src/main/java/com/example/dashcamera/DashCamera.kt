package com.example.dashcamera

import android.app.Application
import android.content.Context


class DashCamera :Application() {
    companion object {
        var contextApp: Application? = null
        fun getContext(): Context {
            return contextApp!!
        }

    }

    override fun onCreate() {
        super.onCreate()
        contextApp = this
    }
}