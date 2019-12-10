/**
 *  File Name: ViewRecordingsPresenter.kt
 *  Project Name: DashCamera
 *  Copyright @ Hanlin Hu 2019
 */

package com.example.dashcamera.presenter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.util.Log
import androidx.core.content.FileProvider
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.dashcamera.DBHelper
import com.example.dashcamera.DashCamera
import com.example.dashcamera.Util
import com.example.dashcamera.model.Recording
import java.io.File
import java.util.ArrayList

/**
 * The implementation of the presenter class
 * */
class ViewRecordingsPresenter(private val mView:IViewRecordings.View):IViewRecordings.Presenter {
    // Declare variables
    private var mUpdateListHandler: Handler? = Handler()
    private var mBroadcastReceiver: BroadcastReceiver? = null
    private var util_:Util = Util()
    /**
     * Populates array with Recording objects
     *
     * @return ArrayList<Recording>
    </Recording> */
    private val dataSet: ArrayList<Recording?>
        get() = DBHelper.getInstance(DashCamera.getContext())!!.selectAllRecordingsList()

    override fun onStartView() {
        mView.updateRecordingsList(dataSet)
        //receive broadcasts when videos list are changed in sqlite
        registerBroadcastReceiver()
    }

    override fun onStopView() {
        stopUpdateList()
        unRegisterBroadcastReceiver()
    }

    override fun onRecordingsItemPressed(recordingItem:Recording?) {
        if (recordingItem == null) return
         // Play recording on position
            util_.showToast(mView.activity, recordingItem!!.getDateSaved() +
        " - " +
        recordingItem!!.getTimeSaved())

        val fileUri = FileProvider.getUriForFile(
            mView.activity,
                DashCamera.getContext()
                .getApplicationContext()
                .getPackageName() + ".provider", File(recordingItem!!.getFilePath())
        )

        util_.openFile(
            mView.activity,
            fileUri,
            "video/mp4")
    }

    private fun stopUpdateList() {
        if (mUpdateListHandler != null) {
            mUpdateListHandler!!.removeCallbacksAndMessages(this)
            mUpdateListHandler = null
        }
    }

    private fun registerBroadcastReceiver() {
        LocalBroadcastManager.getInstance(mView.activity).registerReceiver(
            object: BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent?) {
                    val action = if (intent != null) intent!!.action else null
                    //update recordings list when video has been recorded
                    if (action != null && action == util_.ACTION_UPDATE_RECORDINGS_LIST)
                    {
                        mView.updateRecordingsList(
                            dataSet
                        )
                        Log.d(LOG_TAG_DEBUG, "ViewRecordingsPresenter.onReceive(): update recordings list")
                    }
                }
            }, IntentFilter(util_.ACTION_UPDATE_RECORDINGS_LIST))
    }

    private fun unRegisterBroadcastReceiver() {
        if (mBroadcastReceiver != null)
        {
            LocalBroadcastManager.getInstance(mView.activity).unregisterReceiver(object: BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent?) {
                    val action = if (intent != null) intent!!.action else null
                    //update recordings list when video has been recorded
                    if (action != null && action == util_.ACTION_UPDATE_RECORDINGS_LIST)
                    {
                        mView.updateRecordingsList(
                            dataSet
                        )
                        Log.d(LOG_TAG_DEBUG, "ViewRecordingsPresenter.onReceive(): update recordings list")
                    }
                }
            })
            mBroadcastReceiver = null
        }
    }

    companion object {
        private val LOG_TAG_DEBUG = ViewRecordingsPresenter::class.java!!.simpleName
    }
}