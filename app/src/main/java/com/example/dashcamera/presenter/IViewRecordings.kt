package com.example.dashcamera.presenter

import android.app.Activity
import com.example.dashcamera.model.Recording
import java.util.ArrayList

interface IViewRecordings {
    interface View {
        val activity: Activity

        /**
         * Populate list
         *
         * @param recordingsList List of recordings
         */
        fun updateRecordingsList(recordingsList: ArrayList<Recording?>)
    }

    interface Presenter {
        fun onStartView()

        fun onStopView()

        fun onRecordingsItemPressed(recordingItem: Recording?)
    }
}