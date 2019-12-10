/**
 *  File Name: BackgroundVideoRecorder.kt
 *  Project Name: DashCamera
 *  Copyright @ Hanlin Hu 2019
 */

package com.example.dashcamera

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.hardware.Camera

import android.media.AudioManager
import android.media.CamcorderProfile
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.preference.PreferenceManager
import android.text.format.DateFormat
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager

import com.example.dashcamera.model.Recording

import java.io.File
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Background video recording service.
 * Inspired by
 * https://stackoverflow.com/questions/15049041/background-video-recording-in-android-4-0
 * https://stackoverflow.com/questions/21264592/android-split-video-during-capture
 */

class BackgroundVideoRecorder : Service(), SurfaceHolder.Callback {
    private var windowManager: WindowManager? = null
    private var surfaceView: SurfaceView? = null
    @Volatile
    private var camera: Camera? = null
    @Volatile
    private var mediaRecorder: MediaRecorder? = null
    private var currentVideoFile = "null"
    private var sharedPref: SharedPreferences? = null
    private var thread: HandlerThread? = null
    private var backgroundThread: Handler? = null
    private var settings: SharedPreferences? = null
    private var editor: SharedPreferences.Editor? = null
    private var mainThread: Handler? = Handler(Looper.getMainLooper())
    private var mRecordingsDirectory: File? = null
    private var util_= Util()

    override fun onCreate() {
        //long startTime = System.currentTimeMillis();
        thread = HandlerThread("io_processor_thread")
        thread!!.start()
        backgroundThread = Handler(thread!!.looper)

        // Start in foreground to avoid unexpected kill
        startForeground(
            util_.FOREGROUND_NOTIFICATION_ID,
            util_.createStatusBarNotification(this)
        )

        sharedPref = this.applicationContext.getSharedPreferences(
            getString(R.string.current_recordings_preferences_key),
            Context.MODE_PRIVATE
        )
        editor = sharedPref!!.edit()
        settings = PreferenceManager.getDefaultSharedPreferences(this)

        // Create new SurfaceView, set its size to 1x1, move it to the top left corner and set this service as a callback
        windowManager = this.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        surfaceView = SurfaceView(this)

        var type = WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

        val layoutParams = WindowManager.LayoutParams(
            1, 1,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        layoutParams.gravity = Gravity.LEFT or Gravity.TOP
        windowManager!!.addView(surfaceView, layoutParams)
        surfaceView!!.holder.addCallback(this)

        // Set shutter sound based on preferences
        disableSound(editor)

        // Create directory for recordings if not exists
        mRecordingsDirectory = util_.getVideosDirectoryPath()
        if (!mRecordingsDirectory!!.isDirectory || !mRecordingsDirectory!!.exists()) {
            mRecordingsDirectory!!.mkdir()
        }

        //long elapsedTime = System.currentTimeMillis() - startTime;
        //Log.i("DEBUG", "onCreate Time: " + (TimeUnit.MILLISECONDS.convert(elapsedTime, TimeUnit.MILLISECONDS)) + " milliseconds");
    }

    // Method called right after Surface created (initializing and starting MediaRecorder)
    override fun surfaceCreated(surfaceHolder: SurfaceHolder) {
        backgroundThread!!.post {
            // Initialize Media Recorder
            initMediaRecorder(surfaceHolder)

            // Prepare
            try {
                mediaRecorder!!.prepare()
                mediaRecorder!!.start()
                Log.d("VIDEOCAPTURE", "BackgroundVideoRecorder.run(): start recording")
            } catch (e: Exception) {
                Log.e("VIDEOCAPTURE", "mediaRecorder.prepare() threw exception for some reason!", e)
            }
        }

    }

    private fun initMediaRecorder(surfaceHolder: SurfaceHolder) {
        rotateRecordings(this@BackgroundVideoRecorder, util_.getQuota())
        camera = Camera.open()
        val cameraParams = if (camera != null) camera!!.parameters else null
        if (camera != null) camera!!.unlock()

        //define video quality
        val videoQuality: Int
        if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_720P)) {
            videoQuality = CamcorderProfile.QUALITY_720P
        } else if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_480P)) {
            videoQuality = CamcorderProfile.QUALITY_480P
        } else {
            videoQuality = CamcorderProfile.QUALITY_HIGH
        }

        Log.d("VIDEOCAPTURE", "BackgroundVideoRecorder.initMediaRecorder(): quality " + videoQuality);

        //create camcorder profile and set optimal video size
        val camcorderProfile = CamcorderProfile.get(videoQuality)
        if (cameraParams != null) {
            val previewSizes = cameraParams.supportedPreviewSizes
            val videoSizes = cameraParams.supportedVideoSizes
            val window = getSystemService(Context.WINDOW_SERVICE) as WindowManager

            if (previewSizes != null && videoSizes != null) {
                val displayMetrics = DisplayMetrics()
                window.defaultDisplay.getMetrics(displayMetrics)

                //get and set optimal video size
                val videoSize = util_.getOptimalVideoSize(
                    videoSizes,
                    previewSizes,
                    displayMetrics.widthPixels,
                    displayMetrics.heightPixels
                )
                Log.d("VIDEOCAPTURE", "BackgroundVideoRecorder.initMediaRecorder(): optimal video size - " + videoSize!!.width + "x" + videoSize.height);

                camcorderProfile.videoFrameWidth = videoSize!!.width
                camcorderProfile.videoFrameHeight = videoSize.height
            }
        }

        mediaRecorder = MediaRecorder()
        mediaRecorder!!.setCamera(camera) // TODO See if we can remove this line. We can't, because media recorder should know what camera object will be used
        mediaRecorder!!.setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
        mediaRecorder!!.setVideoSource(MediaRecorder.VideoSource.CAMERA)
        mediaRecorder!!.setProfile(camcorderProfile)
        mediaRecorder!!.setVideoEncodingBitRate(3000000)
        mediaRecorder!!.setPreviewDisplay(surfaceHolder.surface)
        // Store previous and current recording filenames, so that they may be retrieved by the
        // SaveRecording button

        // previous recording = currentVideoFile
        editor!!.putString(
            getString(R.string.previous_recording_preferences_key),
            currentVideoFile
        )
        editor!!.apply()

        // Path to the file with the recording to be created
        currentVideoFile = mRecordingsDirectory!!.absolutePath + File.separator +
                DateFormat.format("yyyy-MM-dd_kk-mm-ss", Date().time) +
                ".mp4"

        // // current recording = currentVideoFile (after updated)
        editor!!.putString(
            getString(R.string.current_recording_preferences_key),
            currentVideoFile
        )
        editor!!.apply()

        mediaRecorder!!.setOutputFile(currentVideoFile)
        mediaRecorder!!.setMaxDuration(util_.getMaxDuration())

        // When maximum video length reached
        mediaRecorder!!.setOnInfoListener { mr, what, extra ->
            if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED && null != mediaRecorder) {
                mediaRecorder!!.setOnInfoListener(null)
                Log.d("VIDEOCAPTURE", "Maximum Duration Reached. Stop recording.")
                mediaRecorder!!.stop()
                mediaRecorder!!.reset()
                mediaRecorder!!.release()
                mediaRecorder = null
                if (null != camera) {
                    camera!!.lock()
                    camera!!.release()
                    camera = null
                }

                //insert new entry to SQLite
                util_.insertNewRecording(
                    Recording(currentVideoFile)
                )

                surfaceCreated(surfaceHolder)
            }
        }
    }

    // Stop recording and remove SurfaceView
    override fun onDestroy() {
        backgroundThread!!.post {
            try {
                if (mediaRecorder != null) {
                    mediaRecorder!!.stop()
                    mediaRecorder!!.reset()
                    mediaRecorder!!.release()
                    mediaRecorder!!.setOnInfoListener(null)
                    mediaRecorder = null
                }
                if (null != camera) {
                    camera!!.lock()
                    camera!!.release()
                    camera = null
                }
            } catch (e: RuntimeException) {
                Log.e(
                    "DashCam",
                    "BackgroundVideoRecorder.run: RuntimeException - " + e.localizedMessage,
                    e
                )
            }

            backgroundThread!!.removeCallbacksAndMessages(null)
            mainThread!!.removeCallbacksAndMessages(null)
            thread!!.quit()
            thread = null
            backgroundThread = null
            mainThread = null

            //insert new entry to SQLite
            util_.insertNewRecording(
                Recording(currentVideoFile)
            )

            reEnableSound()
        }

        windowManager!!.removeView(surfaceView)
        stopForeground(true)
    }

    /**
     * Removes old recordings to create space for the new ones in order to stay withing the
     * set app quota.
     *
     * @param quota Maximum size the recordings directory may reach in megabytes
     */
    private fun rotateRecordings(context: Context, quota: Int) {
        val startTime = System.currentTimeMillis()
        // Quota exceeded?
        while (util_.getFolderSize(mRecordingsDirectory) >= quota) {
            var oldestFile: File? = null
            var starred_videos_total_size = 0

            // Remove the oldest file in the directory
            for (fileInDirectory in mRecordingsDirectory!!.listFiles()!!) {
                // If this is the first run, assign the first file as the oldest
                if (oldestFile == null || oldestFile.lastModified() > fileInDirectory.lastModified()) {
                    // Skip starred recordings, we don't want to rotate those
                    val recording = Recording(fileInDirectory.absolutePath)
                    if (recording.isStarred()) {
                        starred_videos_total_size += (fileInDirectory.length() / (1024 * 1024)).toInt()
                        continue
                    }

                    // Otherwise if not starred
                    oldestFile = fileInDirectory
                }
            }

            if (quota - starred_videos_total_size < util_.getQuotaWarningThreshold()) {
                util_.showToastLong(
                    context.applicationContext,
                    "WARNING: Low on space quota.\n" + "Un-star videos to free up space."
                )
            }

            if (oldestFile == null) {
                return
            }

            //delete recording from storage and sqlite
            util_.deleteSingleRecording(
                Recording(oldestFile.absolutePath)
            )

        }
        val elapsedTime = System.currentTimeMillis() - startTime
        Log.d(
            "DEBUG",
            "rotateRecordings Time: " + TimeUnit.MILLISECONDS.convert(
                elapsedTime,
                TimeUnit.MILLISECONDS
            ) + " milliseconds"
        )
    }

    /**
     * Disable system sounds if set in preferences
     *
     * NOTE: From N onward, volume adjustments that would toggle Do Not Disturb are not allowed unless
     * the app has been granted Do Not Disturb Access.
     *
     * @param editor Editor for current recordings preference
     */
    private fun disableSound(editor: SharedPreferences.Editor?) {
        //        long startTime = System.currentTimeMillis();
        if (settings!!.getBoolean("disable_sound", true)) {
            // Record system volume before app was started
            val audio =
                this.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val volume = audio.getStreamVolume(AudioManager.STREAM_SYSTEM)
            editor!!.putInt(
                getString(R.string.pre_start_volume),
                volume
            )
            editor.apply()
            // Only make change if not in silent
            if (volume > 0) {
                // Set to silent & vibrate
                audio.setStreamVolume(
                    AudioManager.STREAM_SYSTEM,
                    0,
                    AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE
                )
            }
        }
        //        long elapsedTime = System.currentTimeMillis() - startTime;
        //        Log.i("DEBUG", "disableSound Time: " + (TimeUnit.MILLISECONDS.convert(elapsedTime, TimeUnit.MILLISECONDS)) + " milliseconds");
    }

    private fun reEnableSound() {
        //        long startTime = System.currentTimeMillis();
        // Record system volume before app was started
        val audio = this.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val volume = sharedPref!!.getInt(this.getString(R.string.pre_start_volume), 0)
        // Only make change if not in silent
        if (volume > 0) {
            // Set to silent & vibrate
            audio.setStreamVolume(
                AudioManager.STREAM_SYSTEM,
                volume,
                AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE
            )
        }
        //        long elapsedTime = System.currentTimeMillis() - startTime;
        //        Log.i("DEBUG", "reEnableSound Time: " + (TimeUnit.MILLISECONDS.convert(elapsedTime, TimeUnit.MILLISECONDS)) + " milliseconds");
    }


    override fun surfaceChanged(
        surfaceHolder: SurfaceHolder,
        format: Int,
        width: Int,
        height: Int
    ) {
    }

    override fun surfaceDestroyed(surfaceHolder: SurfaceHolder) {}

    override fun onBind(intent: Intent): IBinder? {
        return null
    }
}