package com.example.dashcamera

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.preference.PreferenceManager
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.ActivityCompat
import java.util.ArrayList

class MainActivity : AppCompatActivity() {

    private val multiplePermissionResponseCode = 10
    private val codeRequestPermissionToMuteSystemSound = 10001
    private val codeRequestPermissionDrawOverApps = 10002

    private var permissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        init()
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            codeRequestPermissionToMuteSystemSound ->
                //if user has not allowed this permission close the app, otherwise continue
                if (isPermissionToMuteSystemSoundGranted()) {
                    init()
                } else {
                    finish()
                }
            codeRequestPermissionDrawOverApps ->
                //if user has not allowed this permission close the app, otherwise continue
                if (Settings.canDrawOverlays(this)) {
                    init()
                } else {
                    finish()
                }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun init() {
        // Check permissions to draw over apps
        if (!checkDrawPermission()) return

        //check permission to mute system audio on Android 7 (AudioManager setStreamVolume)
        //java.lang.SecurityException: Not allowed to change Do Not Disturb state
        if (!checkPermissionToMuteSystemSound()) return

        if (checkPermissions()) {
            startApp()
        }
    }

    private fun startApp() {
        var util = Util()
        if (!isEnoughStorage(util)){

            util.showToastLong(this.applicationContext,
                "Not enough storage to run the app (Need " + util.getQuota().toString()
                + "MB). Clean up space for recordings."
            )
        }
            // Launch navigation app, if settings say so
            val settings = PreferenceManager.getDefaultSharedPreferences(this)
            if (settings.getBoolean("start_maps_in_background", true)) {
                launchNavigation()
            }

            // Start Sensor Detection for determining recording time


            // Start recording video
            val videoIntent = Intent(applicationContext, BackgroundVideoRecorder::class.java)
            startService(videoIntent)


            // Start rootView service (display the widgets)
//            val i = Intent(applicationContext, WidgetService::class.java)
//            startService(i)

    }

    private fun checkDrawPermission(): Boolean {
        // for Marshmallow (SDK 23) and newer versions, get overlay permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                /** if not construct intent to request permission  */
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                /** request permission via start activity for result  */
                startActivityForResult(intent, codeRequestPermissionDrawOverApps)

                Toast.makeText(
                    this@MainActivity,
                    "Draw over apps permission needed",
                    Toast.LENGTH_LONG
                )
                    .show()

                Toast.makeText(this@MainActivity, "Allow and click \"Back\"", Toast.LENGTH_LONG)
                    .show()

                Toast.makeText(
                    this@MainActivity,
                    "Then restart the Open Dash Cam app",
                    Toast.LENGTH_LONG
                )
                    .show()

                return false
            }
        }
        return true
    }

    private fun checkPermissions(): Boolean {
        var result: Int
        val listPermissionsNeeded = ArrayList<String>()
        for (p in permissions) {
            result = ActivityCompat.checkSelfPermission(this@MainActivity, p)
            if (result != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(p)
            }
        }
        if (listPermissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                listPermissionsNeeded.toTypedArray(),
                multiplePermissionResponseCode
            )
            return false
        }
        return true
    }

    /**
     * Check and ask permission to set "Do not Disturb"
     * Note: it uses in BackgroundVideoRecorder : audio.setStreamVolume()
     *
     * @return True - granted
     */
    private fun checkPermissionToMuteSystemSound(): Boolean {

        if (!isPermissionToMuteSystemSoundGranted()) {
            val intent = Intent(
                Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS
            )
            startActivityForResult(intent, codeRequestPermissionToMuteSystemSound)
            return false
        }
        return true
    }

    private fun isPermissionToMuteSystemSoundGranted(): Boolean {
        //Android 7+ needs this permission (but Samsung devices may work without it)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return true

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        return notificationManager.isNotificationPolicyAccessGranted
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            multiplePermissionResponseCode -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permissions granted
                    startApp()
                } else {
                    // permissions not granted
                    Toast.makeText(
                        this@MainActivity,
                        "Permissions denied. The app cannot start.",
                        Toast.LENGTH_LONG
                    )
                        .show()

                    Toast.makeText(
                        this@MainActivity,
                        "Please re-start Open Dash Cam app and grant the requested permissions.",
                        Toast.LENGTH_LONG
                    )
                        .show()

                    finish()
                }
                return
            }
        }
    }

    /**
     * Starts Google Maps in driving mode.
     */
    private fun launchNavigation() {
        val googleMapsPackage = "com.google.android.apps.maps"

        try {
            val intent = packageManager.getLaunchIntentForPackage(googleMapsPackage)
            intent!!.action = Intent.ACTION_VIEW
            intent.data = Uri.parse("google.navigation:/?free=1&mode=d&entry=fnls")
            startActivity(intent)
        } catch (e: Exception) {
            return
        }

    }

    private fun isEnoughStorage(util: Util): Boolean {
        val videosFolder = util.getVideosDirectoryPath() ?: return false

        val appVideosFolderSize = util.getFolderSize(videosFolder)
        val storageFreeSize = util.getFreeSpaceExternalStorage(videosFolder)
        //check enough space
        return storageFreeSize + appVideosFolderSize >= util.getQuota()
    }

}


