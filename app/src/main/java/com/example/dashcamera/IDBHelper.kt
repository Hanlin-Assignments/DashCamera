/**
 *  File Name: IDBHelper.kt
 *  Project Name: DashCamera
 *  Copyright @ Hanlin Hu 2019
 */

package com.example.dashcamera

import com.example.dashcamera.model.Recording
import java.util.ArrayList

/**
 * Abstract Class: IDBHelper
 *
 * */
internal interface IDBHelper {
    // Select All Recordings List
    fun selectAllRecordingsList(): ArrayList<Recording?>

    // Insert a New Recording
    fun insertNewRecording(recording: Recording?): Boolean

    // Delete a Recording
    fun deleteRecording(recording: Recording): Boolean

    // Delete All Recordings
    fun deleteAllRecordings(): Boolean

    // Update the Star by tapping the flag
    fun updateStar(recording: Recording): Boolean

    // Flag indicates if the Recording is starred
    fun isRecordingStarred(recording: Recording?): Boolean
}