/**
 *  File Name: ViewRecordingsActivity.kt
 *  Project Name: DashCamera
 *  Copyright @ Hanlin Hu 2019
 */

package com.example.dashcamera

import android.app.Activity
import android.os.Bundle

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import com.example.dashcamera.model.Recording
import com.example.dashcamera.presenter.IViewRecordings
import com.example.dashcamera.presenter.ViewRecordingsPresenter

import java.util.ArrayList

/**
 * Activity to view video recordings produced by this dash cam application
 * Displays all videos from paths matching %OpenDashCam%
 */

class ViewRecordingsActivity : AppCompatActivity(), IViewRecordings.View {

    // Declare variables
    private var mRecyclerView: RecyclerView? = null
    private var mAdapter: ViewRecordingsRecyclerViewAdapter? = null
    private var mLayoutListEmpty: View? = null

    private var mPresenter: IViewRecordings.Presenter? = null

    override val activity: Activity
        get() = this

    // Override methods
    override protected fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_recordings)

        initRecyclerView()

        mPresenter = ViewRecordingsPresenter(this)
    }

    override protected fun onStart() {
        super.onStart()
        mPresenter!!.onStartView()
    }

    override protected fun onStop() {
        mPresenter!!.onStopView()
        super.onStop()
    }

    override fun updateRecordingsList(recordingsList: ArrayList<Recording?>) {
        if (mRecyclerView!!.getScrollState() !== RecyclerView.SCROLL_STATE_IDLE) return

        if (mAdapter != null) {
            mAdapter!!.populateList(recordingsList)
        }

        if (mRecyclerView != null && mLayoutListEmpty != null) {
            if (recordingsList == null || recordingsList.isEmpty()) {
                //show message "no video recordings yet ..."
                mRecyclerView!!.setVisibility(View.GONE)
                mLayoutListEmpty!!.visibility = View.VISIBLE
            } else {
                //show non-empty videos list
                mRecyclerView!!.setVisibility(View.VISIBLE)
                mLayoutListEmpty!!.visibility = View.GONE
            }
        }

    }

    /**
     * Set recycler view for gallery
     */
    private fun initRecyclerView() {
        mRecyclerView = findViewById(R.id.recycler_view) as RecyclerView
        mLayoutListEmpty = findViewById(R.id.layout_list_empty)

        val layoutManager = LinearLayoutManager(this)
        mRecyclerView!!.setLayoutManager(layoutManager)

        // Inline function to initiate a Recycler Adapter
        mAdapter = ViewRecordingsRecyclerViewAdapter(
            this,
            object : ViewRecordingsRecyclerViewAdapter.RecordingListener{
                override fun onItemClick(recording: Recording?) {
                    mPresenter!!.onRecordingsItemPressed(recording)
                }
            })

        // Add the Adapter to the view
        mRecyclerView!!.setAdapter(mAdapter)
    }
}