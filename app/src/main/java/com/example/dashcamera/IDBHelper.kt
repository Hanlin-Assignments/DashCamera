package com.example.dashcamera

import com.example.dashcamera.model.Recording
import java.util.ArrayList

/**
 * Abstract Class: IDBHelper
 *
 * */

internal interface IDBHelper {
    fun selectAllRecordingsList(): ArrayList<Recording?>

    fun insertNewRecording(recording: Recording?): Boolean

    fun deleteRecording(recording: Recording): Boolean

    fun deleteAllRecordings(): Boolean

    fun updateStar(recording: Recording): Boolean

    fun isRecordingStarred(recording: Recording?): Boolean

}