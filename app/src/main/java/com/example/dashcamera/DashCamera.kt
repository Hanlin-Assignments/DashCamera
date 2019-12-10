/**
 *  File Name: DashCamera.kt
 *  Project Name: DashCamera
 *  Copyright @ Hanlin Hu 2019
 */

package com.example.dashcamera

import android.app.Application
import android.content.Context

/**
 * Dash Camera Application which makes the application with Singleton access
 * In addition, getContext() allows other files read application general context
 * */
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