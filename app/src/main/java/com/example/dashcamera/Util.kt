package com.example.dashcamera


import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.widget.Toast
import android.os.CountDownTimer
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.dashcamera.model.Recording
import android.content.ActivityNotFoundException
import android.hardware.Camera
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.os.Environment
import android.text.TextUtils
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.os.EnvironmentCompat

import java.io.File
import java.lang.Double


class Util {
    var ACTION_UPDATE_RECORDINGS_LIST = "update.recordings.list"
    val FOREGROUND_NOTIFICATION_ID = 51288

    private val NOTIFICATIONS_CHANNEL_ID_MAIN_NOTIFICATIONS = "1001"
    private val NOTIFICATIONS_CHANNEL_NAME_MAIN_NOTIFICATIONS = "Main notifications"

    private val QUOTA = 1000 // megabytes
    private val QUOTA_WARNING_THRESHOLD = 200 // megabytes
    private val MAX_DURATION = 300000 // 300 seconds equals 5 mins

    fun getVideosDirectoryPath(): File? {
        //remove an old directory if exists
        val oldDirectory =
            File(Environment.getExternalStorageDirectory().toString() + "/DashCamera/")
        removeNonEmptyDirectory(oldDirectory)

        //New directory
        val appVideosFolder = getAppPrivateVideosFolder(DashCamera.appContext)

        if (appVideosFolder != null) {
            //create app-private folder if not exists
            if (!appVideosFolder.exists()) appVideosFolder.mkdir()
            return appVideosFolder
        }

        return null
    }

    fun getQuota(): Int {
        return QUOTA
    }

    fun getQuotaWarningThreshold(): Int {
        return QUOTA_WARNING_THRESHOLD
    }

    fun getMaxDuration(): Int {
        return MAX_DURATION
    }

    /**
     * Displays toast message of LONG length
     *
     * @param context Application context
     * @param msg     Message to display
     */
    fun showToast(context: Context, msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
    }

    /**
     * Display a 9-seconds-long toast.
     * Inspired by https://stackoverflow.com/a/7173248
     *
     * @param context Application context
     * @param msg     Message to display
     */
    fun showToastLong(context: Context, msg: String) {
        val tag = Toast.makeText(context, msg, Toast.LENGTH_SHORT)

        tag.show()

        object : CountDownTimer(9000, 1000) {

            override fun onTick(millisUntilFinished: Long) {
                tag.show()
            }

            override fun onFinish() {
                tag.show()
            }

        }.start()
    }

    /**
     * Starts new activity to open speicified file
     *
     * @param file     File to open
     * @param mimeType Mime type of the file to open
     */
    fun openFile(context: Context, file: Uri, mimeType: String) {
        val openFile = Intent(Intent.ACTION_VIEW)
        openFile.setDataAndType(file, mimeType)
        openFile.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        openFile.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(openFile)
        } catch (e: ActivityNotFoundException) {
            Log.i("OpenDashCam", "Cannot open file.")
        }

    }

    /**
     * Calculates the size of a directory in megabytes
     *
     * @param file The directory to calculate the size of
     * @return size of a directory in megabytes
     */
    fun getFolderSize(file: File): Long {
        var size: Long = 0
        if (file.isDirectory) {
            for (fileInDirectory in file.listFiles()!!) {
                size += getFolderSize(fileInDirectory)
            }
        } else {
            size = file.length()
        }
        return size / 1024
    }

    /**
     * Get available space on the device
     *
     * @return
     */
    fun getFreeSpaceExternalStorage(storagePath: File?): Long {
        return if (storagePath == null || !storagePath.isDirectory) 0 else storagePath.freeSpace / 1024 / 1024
    }

    /**
     * Delete all recordings from storage and sqlite
     *
     *
     * NOTE: called from UI settings (here uses asynctask for background operation)
     */
    fun deleteRecordings() {
        AsyncTaskCompat.executeParallel(DeleteRecordingsTask())
    }

    /**
     * Star/unstar recording
     *
     *
     * NOTE: called from UI (uses asynctasks)
     *
     * @param recording
     */
    fun updateStar(recording: Recording) {
        AsyncTaskCompat.executeParallel(UpdateStarTask(recording))
    }


    /**
     * Delete single recording from storage and SQLite
     *
     *
     * NOTE: called from background thread (BackgroundVideoRecorder)
     *
     * @param recording Recording
     */
    fun deleteSingleRecording(recording: Recording?) {
        if (recording == null) return
        //delete from storage
        File(recording.getFilePath()).delete()

        //delete from db
        DBHelper.getInstance(DashCamera.appContext).deleteRecording(
            Recording(recording.getFilePath())
        )

        //broadcast for updating videos list in UI
        LocalBroadcastManager.getInstance(DashCamera.appContext).sendBroadcast(
            Intent(ACTION_UPDATE_RECORDINGS_LIST)
        )
    }

    /**
     * Insert new recording to SQLite
     *
     *
     * NOTE: called from background thread (BackgroundVideoRecorder)
     *
     * @param recording Recording
     */
    fun insertNewRecording(recording: Recording?) {
        if (recording == null) return
        DBHelper.getInstance(DashCamera.appContext).insertNewRecording(recording)

        //broadcast for updating videos list in UI
        LocalBroadcastManager.getInstance(DashCamera.appContext).sendBroadcast(
            Intent(ACTION_UPDATE_RECORDINGS_LIST)
        )
    }

    /**
     * Iterate over supported camera video sizes to see which one best fits the
     * dimensions of the given view while maintaining the aspect ratio. If none can,
     * be lenient with the aspect ratio.
     *
     * @param supportedVideoSizes Supported camera video sizes.
     * @param previewSizes        Supported camera preview sizes.
     * @param w                   The width of the view.
     * @param h                   The height of the view.
     * @return Best match camera video size to fit in the view.
     */
    fun getOptimalVideoSize(
        supportedVideoSizes: List<Camera.Size>?,
        previewSizes: List<Camera.Size>, w: Int, h: Int
    ): Camera.Size? {
        // Use a very small tolerance because we want an exact match.
        val ASPECT_TOLERANCE = 0.1
        val targetRatio = 16.toDouble() / 9//(double) w / h;

        // Supported video sizes list might be null, it means that we are allowed to use the preview
        // sizes
        val videoSizes: List<Camera.Size>
        if (supportedVideoSizes != null) {
            videoSizes = supportedVideoSizes
        } else {
            videoSizes = previewSizes
        }
        var optimalSize: Camera.Size? = null

        // Start with max value and refine as we iterate over available video sizes. This is the
        // minimum difference between view and camera height.
        var minDiff = Double.MAX_VALUE

        // Target view height

        // Try to find a video size that matches aspect ratio and the target view size.
        // Iterate over all available sizes and pick the largest size that can fit in the view and
        // still maintain the aspect ratio.
        for (size in videoSizes) {
            //we need max size 1280x720
            if (size.width == 1920) continue

            val ratio = size.width.toDouble() / size.height

            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE)
                continue

            if (Math.abs(size.height - h) < minDiff && previewSizes.contains(size)) {
                optimalSize = size
                minDiff = Math.abs(size.height - h).toDouble()
            }
        }

        // Cannot find video size that matches the aspect ratio, ignore the requirement
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE
            for (size in videoSizes) {
                if (Math.abs(size.height - h) < minDiff && previewSizes.contains(size)) {
                    optimalSize = size
                    minDiff = Math.abs(size.height - h).toDouble()
                }
            }
        }

        return optimalSize
    }

    /**
     * Create notification for status bar
     *
     * @param context Context
     * @return Notification
     */
    fun createStatusBarNotification(context: Context): Notification {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notificationBuilder = NotificationCompat.Builder(
            context,
            NOTIFICATIONS_CHANNEL_ID_MAIN_NOTIFICATIONS
        )
            .setContentTitle(context.resources.getString(R.string.notification_title))
            .setContentText(context.resources.getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_videocam_red_128dp)
            .setAutoCancel(false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && notificationManager != null) {
            val channel = NotificationChannel(
                NOTIFICATIONS_CHANNEL_ID_MAIN_NOTIFICATIONS,
                NOTIFICATIONS_CHANNEL_NAME_MAIN_NOTIFICATIONS,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            channel.enableVibration(false)
            channel.vibrationPattern = null
            channel.setSound(null, null)
            notificationManager.createNotificationChannel(channel)
        }

        return notificationBuilder.build()
    }

    /**
     * Get path to app-private folder (Android/data/[app name]/files)
     *
     * @param context Context
     * @return Folder
     */
    private fun getAppPrivateVideosFolder(context: Context): File? {
        try {
            val extAppFolders =
                ContextCompat.getExternalFilesDirs(context, Environment.DIRECTORY_MOVIES)

            for (file in extAppFolders) {
                if (file != null) {
                    //find external app-private folder (emulated - it's internal storage)
                    if (!file!!.getAbsolutePath().toLowerCase().contains("emulated") && isStorageMounted(
                            file
                        )
                    ) {
                        return file
                    }
                }
            }

            //if external storage is not found
            if (extAppFolders.isNotEmpty()) {
                var appFolder: File?
                //get available app-private folder form the list
                var i = extAppFolders.size - 1
                val j = 0
                while (i >= j) {
                    appFolder = extAppFolders[i]
                    if (appFolder != null && isStorageMounted(appFolder)) {
                        return appFolder
                    }
                    i--
                }
            } else {
                return null
            }
        } catch (e: Exception) {
            Log.e(
                Util::class.java.simpleName,
                "getAppPrivateVideosFolder: Exception - " + e.localizedMessage,
                e
            )
        }

        return null
    }

    /**
     * Check if storage mounted and has read/write access.
     *
     * @param storagePath Storage path
     * @return True - can write data
     */
    private fun isStorageMounted(storagePath: File): Boolean {
        val storageState = EnvironmentCompat.getStorageState(storagePath)
        return storageState == Environment.MEDIA_MOUNTED
    }

    /**
     * Remove non-empty directory
     *
     * @param path Directory path
     * @return True - Removed
     */
    private fun removeNonEmptyDirectory(path: File): Boolean {
        if (path.exists()) {
            for (file in path.listFiles()!!) {
                if (file.isDirectory) {
                    removeNonEmptyDirectory(file)
                } else {
                    file.delete()
                }
            }
        }
        return path.delete()
    }

    /**
     * AsyncTask for delete recordings from storage and SQLite
     */
    private inner class DeleteRecordingsTask : AsyncTask<Void, Void, Boolean>() {

        override fun doInBackground(vararg voids: Void): Boolean? {
            val dbHelper = DBHelper.getInstance(DashCamera.appContext)

            //select all saved recordings for removing files from storage
            val recordingsList = dbHelper.selectAllRecordingsList()

            //remove items from SQLite database
            val result = dbHelper.deleteAllRecordings()

            if (result) {
                var videoFile: File?
                //remove files from storage
                for (recording in recordingsList) {
                    videoFile =
                        if (!TextUtils.isEmpty(recording.getFilePath())) File(recording.getFilePath()) else null
                    videoFile?.delete()
                }
            }

            return result
        }

        override fun onPostExecute(aBoolean: Boolean) {
            val context = DashCamera.appContext
            val res = context.getResources()
            this@Util.showToastLong(
                context,
                if (aBoolean)
                    res.getString(R.string.pref_delete_recordings_confirmation)
                else
                    res.getString(R.string.recordings_list_empty_message_title)
            )
        }
    }

    /**
     * AsyncTask for star/unstar
     */
    private inner class UpdateStarTask internal constructor(private val mRecording: Recording) :
        AsyncTask<Void, Void, Void>() {

        override fun doInBackground(vararg voids: Void): Void? {
            val dbHelper = DBHelper.getInstance(DashCamera.appContext)
            //insert or delete star
            dbHelper.updateStar(mRecording)
            return null
        }

        override fun onPostExecute(aVoid: Void) {
            //broadcast for updating videos list in UI
            LocalBroadcastManager.getInstance(DashCamera.appContext).sendBroadcast(
                Intent(this@Util.ACTION_UPDATE_RECORDINGS_LIST)
            )
        }
    }


}