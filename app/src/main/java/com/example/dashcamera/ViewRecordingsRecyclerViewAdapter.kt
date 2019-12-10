/**
 *  File Name: ViewRecordingsRecyclerViewAdapter.kt
 *  Project Name: DashCamera
 *  Copyright @ Hanlin Hu 2019
 */

package com.example.dashcamera

import android.content.Context

import android.text.TextUtils
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.NonNull
import androidx.recyclerview.widget.RecyclerView

import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.dashcamera.model.Recording

import java.util.ArrayList

/**
 * Adapter to display video mRecordingsList in ViewRecordingsActivity.
 * Supplies data to the RecyclerView for display.
 */

class ViewRecordingsRecyclerViewAdapter constructor(
    private val mContext: Context,
    private val mRecordingsListener: RecordingListener?
) : RecyclerView.Adapter<ViewRecordingsRecyclerViewAdapter.RecordingHolder>() {

    // Declare variables
    private val mRecordingsList = ArrayList<Recording?>()
    private val mWidth: Int
    private val mHeight: Int

    // Get Size of Recording List
    override fun getItemCount(): Int {
        return mRecordingsList.size
    }

    // Implement onClick Listener
    interface RecordingListener {
        fun onItemClick(recording: Recording?)
    }

    init {
        // Initiate Width and Height
        mWidth = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            150f,
            mContext.resources.displayMetrics
        ).toInt()
        mHeight = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            100f,
            mContext.resources.displayMetrics
        ).toInt()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RecordingHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.view_recordings_row, parent, false)

        return RecordingHolder(view)
    }

    override fun onBindViewHolder(holder: RecordingHolder, position: Int) {
        val adapterPosition = holder.getAdapterPosition()
        val recItem = mRecordingsList[adapterPosition] ?: return

        holder.label.setText(recItem.getDateSaved())
        holder.dateTime.setText(recItem.getTimeSaved())
        holder.starred.isChecked = recItem.isStarred()

        //action on item clicked
        holder.itemView.setOnClickListener(View.OnClickListener {
            mRecordingsListener?.onItemClick(recItem)
        })

        //Action to happen when starred/unstarred
        holder.starred.setOnCheckedChangeListener { buttonView, isChecked ->
            if (buttonView.isPressed) {
                recItem.toggleStar(isChecked)
            }
        }

        //show thumbnail for video file
        showVideoThumbnail(holder, recItem.getFilePath())
    }

    /**
     * Populate list
     *
     */
    internal fun populateList(myDataset: ArrayList<Recording?>) {
        mRecordingsList.clear()
        mRecordingsList.addAll(myDataset)
        notifyDataSetChanged()
    }

    fun addItem(recording: Recording, index: Int) {
        mRecordingsList.add(index, recording)
        notifyItemInserted(index)
    }

    fun deleteItem(index: Int) {
        mRecordingsList.removeAt(index)
        notifyItemRemoved(index)
    }

    /**
     * Show image for preview
     *
     * @param holder        ViewHolder
     * @param videoLocalUrl Local video url
     */
    private fun showVideoThumbnail(@NonNull holder: RecordingHolder, videoLocalUrl: String?) {
        if (TextUtils.isEmpty(videoLocalUrl)) return

        Glide.with(mContext)
            .load(videoLocalUrl)
            .dontAnimate()
            .centerCrop()
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .placeholder(R.drawable.ic_videocam_red_128dp)
            .override(mWidth, mHeight)
            .into(holder.thumbnail)
    }

    /**
     *  RecyclerView of Videos
     * */
    inner class RecordingHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var thumbnail: ImageView
        var label: TextView
        var dateTime: TextView
        var starred: CheckBox

        init {
            thumbnail = itemView.findViewById(R.id.thumbnail) as ImageView
            label = itemView.findViewById(R.id.recordingDate) as TextView
            dateTime = itemView.findViewById(R.id.recordingTime) as TextView
            starred = itemView.findViewById(R.id.starred) as CheckBox
        }
    }
}