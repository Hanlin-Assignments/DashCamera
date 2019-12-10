/**
 *  File Name: WidgetService.kt
 *  Project Name: DashCamera
 *  Copyright @ Hanlin Hu 2019
 */

package com.example.dashcamera

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.view.WindowManager

import com.example.dashcamera.model.Widget

class WidgetService : Service() {
    // Declare variables
    private var overlayWidget: Widget? = null
    private var mWakeLock: PowerManager.WakeLock? = null
    private var util_ = Util()

    override fun onBind(intent: Intent): IBinder? {
        // Return the communication channel to the service.
        // Not used
        return null
    }

    override fun onCreate() {
        super.onCreate()

        // Create a widget with WindowManager
        overlayWidget = Widget(util_, this, getSystemService(Context.WINDOW_SERVICE) as WindowManager)
        overlayWidget!!.show()

        // Start in foreground to avoid unexpected kill
        startForeground(
            util_.FOREGROUND_NOTIFICATION_ID,
            util_.createStatusBarNotification(this)
        )

        // Prevent going to sleep mode while service is working
        // https://developer.android.com/reference/android/os/PowerManager.html
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (powerManager != null) {
            mWakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                WidgetService::class.java.simpleName
            )
            mWakeLock!!.acquire()
        }
    }

    override fun onDestroy() {

        // Remove rootView views from display
        if (overlayWidget != null) {
            overlayWidget!!.hide()
        }

        // Close DB connection
        val dbHelper = DBHelper.getInstance(this)
        dbHelper!!.close()

        // Return to home screen
        val startMain = Intent(Intent.ACTION_MAIN)
        startMain.addCategory(Intent.CATEGORY_HOME)
        startMain.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(startMain)

        //remove wakelock
        if (mWakeLock != null) {
            mWakeLock!!.release()
        }
        stopForeground(true)

    }
}