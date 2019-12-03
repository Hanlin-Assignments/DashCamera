package com.example.dashcamera

import android.app.Application
import android.content.Context


class DashCamera : Application() {

    override fun onCreate() {
        super.onCreate()

        if (sApp == null) {
            sApp = this
        }
    }

    companion object {
        private var sApp: DashCamera? = null

        /**
         * Get app context
         *
         * @return Context
         */
        val appContext: Context
            get() = sApp!!.applicationContext
    }
}