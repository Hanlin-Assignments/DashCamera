package com.example.dashcamera.model

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.ScaleAnimation

import com.example.dashcamera.BackgroundVideoRecorder
import com.example.dashcamera.R
import com.example.dashcamera.Util
import com.example.dashcamera.ViewRecordingsActivity

import android.content.Intent.FLAG_ACTIVITY_NEW_TASK

class Widget(protected var service: Service, protected var windowManager: WindowManager) {
    private val viewHolder: WidgetViewHolder

    private var layoutParams: WindowManager.LayoutParams? = null
    private var gravity = Gravity.CENTER_VERTICAL or Gravity.START
    private var x = 0
    private var y = 0

    var util_:Util = Util()

    init {
        this.viewHolder = WidgetViewHolder(service)
    }

    /**
     * Displays the rootView on screen
     */
    fun show() {
        var type = WindowManager.LayoutParams.TYPE_PHONE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        // rootView.setImageResource(widgetDrawableResource);

        // Set position on screen
        layoutParams!!.gravity = this.gravity
        layoutParams!!.x = this.x
        layoutParams!!.y = this.y

        windowManager.addView(viewHolder.rootViewMenu, layoutParams)
        windowManager.addView(viewHolder.rootView, layoutParams)
    }

    /**
     * Removes the rootView from screen
     */
    fun hide() {
        //widget for "rec" button
        windowManager.removeView(viewHolder.rootView)
        //widget for menu
        windowManager.removeView(viewHolder.rootViewMenu)
    }

    private inner class WidgetViewHolder internal constructor(context: Context) :
        View.OnClickListener {
        internal var rootView: View
        internal var rootViewMenu: View
        internal var viewRecView: View
        internal var saveRecView: View
        internal var recView: View
        internal var settingsView: View
        internal var stopAndQuitView: View
        internal var layoutMenu: View
        internal var areSecondaryWidgetsShown = false

        init {
            rootView = LayoutInflater.from(context).inflate(R.layout.layout_widgets, null)
            recView = rootView.findViewById(R.id.rec_button)
            rootViewMenu = LayoutInflater.from(context).inflate(R.layout.layout_widget_menu, null)
            viewRecView = rootViewMenu.findViewById(R.id.view_recordings_button)
            saveRecView = rootViewMenu.findViewById(R.id.save_recording_button)
            settingsView = rootViewMenu.findViewById(R.id.settings_button)
            stopAndQuitView = rootViewMenu.findViewById(R.id.stop_and_quit_button)
            layoutMenu = rootViewMenu.findViewById(R.id.layout_menu)

            viewRecView.setOnClickListener(this)
            saveRecView.setOnClickListener(this)
            recView.setOnClickListener(this)
            settingsView.setOnClickListener(this)
            stopAndQuitView.setOnClickListener(this)
            hideSecondaryWidgets()
        }

        override fun onClick(v: View) {
            val id = v.id
            when (id) {
                R.id.view_recordings_button -> {
                    val viewRecordingsIntent = Intent(service, ViewRecordingsActivity::class.java)
                    viewRecordingsIntent.flags = FLAG_ACTIVITY_NEW_TASK
                    service.startActivity(viewRecordingsIntent)
                    hideSecondaryWidgets()
                }
                R.id.save_recording_button -> {
                    // Access shared references file
                    val sharedPref = service.applicationContext.getSharedPreferences(
                        service.getString(R.string.current_recordings_preferences_key),
                        Context.MODE_PRIVATE
                    )

                    // Save video that is being recorded now
                    val currentVideoRecording = sharedPref.getString(
                        service.getString(R.string.current_recording_preferences_key),
                        "null"
                    )

                    if (currentVideoRecording !== "null") {
                        // star current recording
                        val recording = Recording(currentVideoRecording)
                        recording.toggleStar(true)
                    }

                    // Save the oldest (previous) recording
                    val previousVideoRecording = sharedPref.getString(
                        service.getString(R.string.previous_recording_preferences_key),
                        "null"
                    )

                    if (previousVideoRecording !== "null") {
                        // star previous recording
                        val recording = Recording(0, previousVideoRecording)
                        recording.toggleStar(true)
                    }

                    // Show success message
                    util_.showToastLong(
                        service,
                        service.getString(R.string.save_recording_success_msg)
                    )
                }
                R.id.rec_button -> toggleSecondaryWidgets()
//                R.id.settings_button -> {
//                    val settingsIntent = Intent(service, SettingsActivity::class.java)
//                    settingsIntent.flags = FLAG_ACTIVITY_NEW_TASK
//                    service.startActivity(settingsIntent)
//                    // hide secondary widgets
//                    hideSecondaryWidgets()
//                }
                R.id.stop_and_quit_button -> {
                    // Stop video recording service
                    service.stopService(Intent(service, BackgroundVideoRecorder::class.java))
                    // Stop the rootView service
                    service.stopSelf()
                }
            }
        }

        private fun toggleSecondaryWidgets() {
            if (areSecondaryWidgetsShown) {
                hideSecondaryWidgets()
            } else {
                showSecondaryWidgets()
            }
        }

        private fun showSecondaryWidgets() {
            rootViewMenu.visibility = View.VISIBLE

            //show menu layout with animation
            val animation = ScaleAnimation(
                0f, 1f,
                0f, 1f,
                Animation.RELATIVE_TO_SELF, 0f,
                Animation.RELATIVE_TO_SELF, 0.5f
            )
            animation.fillAfter = true
            animation.duration = 200
            layoutMenu.startAnimation(animation)

            areSecondaryWidgetsShown = true
        }

        private fun hideSecondaryWidgets() {
            //hide menu layout with animation
            val animation = ScaleAnimation(
                1f, 0f,
                1f, 0f,
                Animation.RELATIVE_TO_SELF, 0f,
                Animation.RELATIVE_TO_SELF, 0.5f
            )
            //on the first start no need to show animation, set 0
            animation.duration = (if (areSecondaryWidgetsShown) 200 else 0).toLong()
            animation.fillAfter = true
            animation.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation) {
                    //do nothing
                }

                override fun onAnimationEnd(animation: Animation) {
                    rootViewMenu.visibility = View.GONE
                }

                override fun onAnimationRepeat(animation: Animation) {
                    //do nothing
                }
            })
            layoutMenu.startAnimation(animation)

            areSecondaryWidgetsShown = false
        }
    }
}
