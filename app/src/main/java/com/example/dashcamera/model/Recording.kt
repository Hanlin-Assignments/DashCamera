package com.example.dashcamera.model

import android.text.TextUtils
import com.example.dashcamera.DBHelper
import com.example.dashcamera.DashCamera
import com.example.dashcamera.Util
import java.io.File
import java.text.SimpleDateFormat
import java.util.*


class Recording(id: Int = 0, filePath: String?) {
    /**
     * Constructor for selecting rows from SQLite
     *
     * @param id       Unique id
     * @param filePath String
     */
    private var id = id
    private var filePath: String? = filePath
    private var filename: String? = File(filePath).name
    private var dateSaved: String? = null
    private var timeSaved: String? = null
    private var dbHelper: DBHelper? = DBHelper.getInstance(DashCamera.appContext)

    private val DATE_FORMAT = SimpleDateFormat("EEE MMM d")
    private val TIME_FORMAT = SimpleDateFormat("HH:mm:ss")

    init {
        getDatesFromFile()
    }

    /**
     * Constructor for create a new recording from Video Recorder
     *
     * @param filePath String
     */
    constructor(filePath: String?): this(-1, filePath)

    fun getFilePath(): String? {
        return if (!TextUtils.isEmpty(filePath)) filePath else ""
    }

    fun getFileName(): String? {
        return if (!TextUtils.isEmpty(filename)) filename else ""
    }

    fun getDateSaved(): String? {
        return dateSaved
    }

    fun getTimeSaved(): String? {
        return timeSaved
    }

    fun isStarred(): Boolean {
        return dbHelper.isRecordingStarred(this)
    }

    /**
     * Checks/unchecks a recording as starred in DB. Intended to be called by
     * OnCheckedChangeListener when video is starred/unstarred by the user.
     *
     * @param isChecked Whether or not checkbox was marked as checked
     * @return True when marked as checked in DB, False otherwise
     */
    fun toggleStar(isChecked: Boolean): Boolean { //this item will be updated in the UI when asynctask will be finished
        Util.updateStar(this)
        return true
    }

    private fun getDatesFromFile() {
        if (filePath != null && !filePath!!.isEmpty()) {
            val file = File(filePath)
            val lastModDate = Date(file.lastModified())
            dateSaved = this@Recording.DATE_FORMAT.format(lastModDate)
            timeSaved = this@Recording.TIME_FORMAT.format(lastModDate)
        } else {
            dateSaved = "Video $id"
            timeSaved = ""
        }
    }

    fun getId(): Int {
        return id
    }

}