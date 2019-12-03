package com.example.dashcamera.model

import android.app.Service
import android.view.Gravity
import android.view.WindowManager


class Widget {
    protected lateinit var service: Service
    protected  lateinit var windowManager: WindowManager

    //private lateinit var viewHolder: WidgetViewHolder

    private var layoutParams: WindowManager.LayoutParams? = null
    private var initGravity = Gravity.CENTER_VERTICAL and Gravity.START
    private var initX = 0
    private var initY = 0

    fun Widget(service: Service, windowManager: WindowManager) {
        this.service = service
        this.windowManager = windowManager
        //  this.viewHolder = WidgetViewHolder(service)
    }

    fun setPosition(gravity: Int, x: Int, y: Int) {
        this.initGravity = gravity
        this.initX = x
        this.initY = y
    }

}
