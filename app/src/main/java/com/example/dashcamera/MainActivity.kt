/**
 *  File Name: MainActivity.kt
 *  Project Name: DashCamera
 *  Copyright @ Hanlin Hu 2019
 */

package com.example.dashcamera

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.preference.PreferenceManager
import android.provider.Settings
import android.widget.Toast

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener

import androidx.core.app.ActivityCompat

import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.*

class MainActivity : AppCompatActivity(), SensorEventListener {
    // Request Permission
    private val multiplePermissionResponseCode = 10
    private val codeRequestPermissionToMuteSystemSound = 10001
    private val codeRequestPermissionDrawOverApps = 10002

    private var permissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE
    )

    // sensor variables
    // for gryo
    val EPSILON = 0.000000001f
    val TIME_CONSTANT = 10
    private val NS2S = 1.0f / 1000000000.0f
    private var count = 1
    private var pitchOut: Float = 0.toFloat()
    private var rollOut:Float = 0.toFloat()
    private var yawOut:Float = 0.toFloat()

    // counter for sensor fusion
    private var overYaw = 0
    private var overPitch = 0
    //counter for quaternion
    private var overYawQ = 0
    private var overPitchQ = 0

    // final pitch and yaw values
    private var finalOverYaw = 0
    private var finalOverPitch = 0

    //counter for accelerometer reading
    private var overX = 0
    private var overY = 0
    private var mMagneticField: FloatArray? = null
    private var mGravity: FloatArray? = null
    private var d = NumberFormat.getNumberInstance(Locale.ENGLISH) as DecimalFormat
    private var getPitch: Float? = 0f
    private var getRoll: Float? = 0f
    private var getYaw: Float? = 0f
    private var getPitchQ: Float? = 0f
    private var getRollQ: Float? = 0f
    private var getYawQ: Float? = 0f

    // normal - sensor fusion, Q - denotes quaternion
    private var newPitchOut: Float? = 0f
    private var newRollOut: Float? = 0f
    private var newYawOut: Float? = 0f

    private var newPitchOutQ: Float? = 0f
    private var newRollOutQ: Float? = 0f
    private var newYawOutQ: Float? = 0f
    private var mPitch: Float = 0f
    private var mRoll:Float = 0f
    private var mYaw:Float = 0f

    // for accelerometer
    private var xAccelerometer: Float = 0f
    private var yAccelerometer: Float = 0f
    private var zAccelerometer: Float = 0f
    private var xPreviousAcc: Float = 0f
    private var yPreviousAcc: Float = 0f
    private var zPreviousAcc: Float = 0f
    private var xAccCalibrated = 0f
    private var yAccCalibrated = 0f
    private var zAccCalibrated = 0f
    private var writeCheck = false

    private var mSensorManager: SensorManager? = null
    // angular speeds from gyro
    private val gyro = FloatArray(3)
    // rotation matrix from gyro data
    private var gyroMatrix = FloatArray(9)
    // orientation angles from gyro matrix
    private val gyroOrientation = FloatArray(3)
    // magnetic field vector
    private val magnet = FloatArray(3)
    // accelerometer vector
    private val accel = FloatArray(3)
    // orientation angles from accel and magnet
    private val accMagOrientation = FloatArray(3)
    // final orientation angles from sensor fusion
    private val fusedOrientation = FloatArray(3)
    // accelerometer and magnetometer based rotation matrix
    private val rotationMatrix = FloatArray(9)
    private var timestamp: Float = 0.toFloat()
    private var initState = true
    private val fuseTimer = Timer()

    private var mInitialized = false
    private var yAccChange: Boolean? = false
    private var xAccChange: Boolean? = false

    // for 30 sec sensor values reset
    private var getFinalOverYaw = 0
    private var getFinalOverPitch = 0
    private var getFinalOverX = 0
    private var getFinalOverY = 0
    private var unsafeScore = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // init application
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

        // computing sensor values
        gyroOrientation[0] = 0.0f
        gyroOrientation[1] = 0.0f
        gyroOrientation[2] = 0.0f

        // initialise gyroMatrix with identity matrix
        gyroMatrix[0] = 1.0f
        gyroMatrix[1] = 0.0f
        gyroMatrix[2] = 0.0f
        gyroMatrix[3] = 0.0f
        gyroMatrix[4] = 1.0f
        gyroMatrix[5] = 0.0f
        gyroMatrix[6] = 0.0f
        gyroMatrix[7] = 0.0f
        gyroMatrix[8] = 1.0f

        // get sensorManager and initialise sensor listeners
        mSensorManager = this.getSystemService(Activity.SENSOR_SERVICE) as SensorManager
        initListeners()
        // wait for one second until gyroscope and magnetometer/accelerometer
        // data is initialised then schedule the complementary filter task
        fuseTimer.scheduleAtFixedRate(
            calculateFusedOrientationTask(),
            2000, TIME_CONSTANT.toLong()
        )
        // analysing behavior every 2 sec
        fuseTimer.scheduleAtFixedRate(BehaviorAnalysis(), 0, 2000)

        // Start Sensor Detection for determining recording time
        if (unsafeScore > 0) {
            // Start recording video
            val videoIntent = Intent(applicationContext, BackgroundVideoRecorder::class.java)
            startService(videoIntent)
        }

        // Start rootView service (display the widgets)
//        val widgetIntent = Intent(applicationContext, WidgetService::class.java)
//        startService(widgetIntent)

        //resetting the sensor values every 50 sec
        fuseTimer.scheduleAtFixedRate(ResetSensorValues(), 1000, 50000)
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

    // ----------  Sensor Fusion ------------
    // initializing the sensors
    fun initListeners() {
        mSensorManager!!.registerListener(
            this,
            mSensorManager!!.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
            SensorManager.SENSOR_DELAY_FASTEST
        )

        mSensorManager!!.registerListener(
            this,
            mSensorManager!!.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
            SensorManager.SENSOR_DELAY_FASTEST
        )

        mSensorManager!!.registerListener(
            this,
            mSensorManager!!.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
            SensorManager.SENSOR_DELAY_FASTEST
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Do Nothing
    }

    override fun onSensorChanged(event: SensorEvent?) {
        updateValues()
        when (event!!.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                mGravity = event.values
                xAccelerometer = event.values[0]
                yAccelerometer = event.values[1]
                zAccelerometer = event.values[2]
                calibrateAccelerometer()
                // copy new accelerometer data into accel array
                // then calculate new orientation
                System.arraycopy(event.values, 0, accel, 0, 3)
                calculateAccMagOrientation()
            }

            Sensor.TYPE_GYROSCOPE ->
                // process gyro data
                gyroFunction(event)

            Sensor.TYPE_MAGNETIC_FIELD -> {
                // copy new magnetometer data into magnet array
                mMagneticField = event.values
                System.arraycopy(event.values, 0, magnet, 0, 3)
            }
        }
        computeQuaternion()
    }

    // getting accelerometer values and calibrating the accelerometer
    private fun calibrateAccelerometer() {
        if (!mInitialized) {
            xPreviousAcc = xAccelerometer
            yPreviousAcc = yAccelerometer
            zPreviousAcc = zAccelerometer
            mInitialized = true
        } else {
            xAccCalibrated = xPreviousAcc - xAccelerometer
            yAccCalibrated = yPreviousAcc - yAccelerometer
            zAccCalibrated = zPreviousAcc - zAccelerometer
            xPreviousAcc = xAccelerometer
            yPreviousAcc = yAccelerometer
            zPreviousAcc = zAccelerometer
        }
    }

    // computing quaternion values
    private fun computeQuaternion() {
        val R = FloatArray(9)
        val I = FloatArray(9)
        if (mMagneticField != null && mGravity != null) {
            val success = SensorManager.getRotationMatrix(R, I, mGravity, mMagneticField)
            if (success) {
                val mOrientation = FloatArray(3)
                val mQuaternion = FloatArray(4)
                SensorManager.getOrientation(R, mOrientation)

                SensorManager.getQuaternionFromVector(mQuaternion, mOrientation)

                mYaw = mQuaternion[1] // orientation contains: azimuth(yaw), pitch and Roll
                mPitch = mQuaternion[2]
                mRoll = mQuaternion[3]

                newPitchOutQ = getPitchQ!! - mPitch
                newRollOutQ = getRollQ!! - mRoll
                newYawOutQ = getYawQ!! - mYaw

                getPitchQ = mPitch
                getRollQ = mRoll
                getYawQ = mYaw
            }
        }
    }

    // updating the values for accelerometer, sensor fusion and quaternion
    // and computing the counters for quaternion, sensor fusion, accelerometer
    private fun updateValues() {

        if (newPitchOut != 0f || newPitchOutQ != 0f
            || newYawOut != 0f || newYawOutQ != 0f
            || xAccCalibrated != 0f || yAccCalibrated != 0f) {
            writeCheck = false
            xAccChange = false
            yAccChange = false
            count = count + 1
            if (count == 2250) {
                count = 1
            }

            if (newYawOut!! > .30 || newYawOut!! < -.30) {
                overYaw = overYaw + 1
                writeCheck = true
            }

            if (newPitchOut!! > .12 || newPitchOut!! < -.12) {
                overPitch = overPitch + 1
                writeCheck = true
            }

            if (newYawOutQ!! > .30 || newYawOutQ!! < -.30) {
                overYawQ = overYawQ + 1
                writeCheck = true
            }

            if (newPitchOutQ!! > .12 || newPitchOutQ!! < -.12) {
                overPitchQ = overPitchQ + 1
                writeCheck = true
            }

            if (xAccCalibrated > 3 || xAccCalibrated < -3) {
                overX = overX + 1
                writeCheck = true
                xAccChange = true
            }

            if (yAccCalibrated > 2.5 || yAccCalibrated < -2.5) {
                overY = overY + 1
                writeCheck = true
                yAccChange = true
            }

            // computing final values for pitch and yaw counters
            if (overPitch != 0 || overPitchQ != 0) {
                finalOverPitch = (overPitch + 0.3 * overPitchQ).toInt()
            }

            if (overYaw != 0 || overYawQ != 0) {
                finalOverYaw = (overYaw + 0.4 * overYawQ).toInt()
            }
        }
    }

    // accelerometer
    fun calculateAccMagOrientation() {
        if (SensorManager.getRotationMatrix(rotationMatrix, null, accel, magnet)) {
            SensorManager.getOrientation(rotationMatrix, accMagOrientation)
        }
    }

    // gyroscope
    fun gyroFunction(event: SensorEvent) {
        // don't start until first accelerometer/magnetometer orientation has been acquired
        if (accMagOrientation == null)
            return

        // initialisation of the gyroscope based rotation matrix
        if (initState) {
            var initMatrix = FloatArray(9)
            initMatrix = getRotationMatrixFromOrientation(accMagOrientation)
            val test = FloatArray(3)
            SensorManager.getOrientation(initMatrix, test)
            gyroMatrix = matrixMultiplication(gyroMatrix, initMatrix)
            initState = false
        }

        // copy the new gyro values into the gyro array
        // convert the raw gyro data into a rotation vector
        val deltaVector = FloatArray(4)
        if (timestamp != 0f) {
            val dT = (event.timestamp - timestamp) * NS2S
            System.arraycopy(event.values, 0, gyro, 0, 3)
            getRotationVectorFromGyro(gyro, deltaVector, dT / 2.0f)
        }

        // measurement done, save current time for next interval
        timestamp = event.timestamp.toFloat()

        // convert rotation vector into rotation matrix
        val deltaMatrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(deltaMatrix, deltaVector)

        // apply the new rotation interval on the gyroscope based rotation matrix
        gyroMatrix = matrixMultiplication(gyroMatrix, deltaMatrix)

        // get the gyroscope based orientation from the rotation matrix
        SensorManager.getOrientation(gyroMatrix, gyroOrientation)
    }

    private fun matrixMultiplication(A: FloatArray, B: FloatArray): FloatArray {
        val result = FloatArray(9)

        result[0] = A[0] * B[0] + A[1] * B[3] + A[2] * B[6]
        result[1] = A[0] * B[1] + A[1] * B[4] + A[2] * B[7]
        result[2] = A[0] * B[2] + A[1] * B[5] + A[2] * B[8]

        result[3] = A[3] * B[0] + A[4] * B[3] + A[5] * B[6]
        result[4] = A[3] * B[1] + A[4] * B[4] + A[5] * B[7]
        result[5] = A[3] * B[2] + A[4] * B[5] + A[5] * B[8]

        result[6] = A[6] * B[0] + A[7] * B[3] + A[8] * B[6]
        result[7] = A[6] * B[1] + A[7] * B[4] + A[8] * B[7]
        result[8] = A[6] * B[2] + A[7] * B[5] + A[8] * B[8]

        return result
    }

    // gyroscope
    private fun getRotationVectorFromGyro(
        gyroValues: FloatArray,
        deltaRotationVector: FloatArray,
        timeFactor: Float
    ) {
        val normValues = FloatArray(3)

        // Calculate the angular speed of the sample
        val omegaMagnitude = Math.sqrt(
            (gyroValues[0] * gyroValues[0] +
                    gyroValues[1] * gyroValues[1] +
                    gyroValues[2] * gyroValues[2]).toDouble()
        ).toFloat()

        // Normalize the rotation vector if it's big enough to get the axis
        if (omegaMagnitude > EPSILON) {
            normValues[0] = gyroValues[0] / omegaMagnitude
            normValues[1] = gyroValues[1] / omegaMagnitude
            normValues[2] = gyroValues[2] / omegaMagnitude
        }

        // Integrate around this axis with the angular speed by the timestamp
        // in order to get a delta rotation from this sample over the timestamp
        // We will convert this axis-angle representation of the delta rotation
        // into a quaternion before turning it into the rotation matrix.
        val thetaOverTwo = omegaMagnitude * timeFactor
        val sinThetaOverTwo = Math.sin(thetaOverTwo.toDouble()).toFloat()
        val cosThetaOverTwo = Math.cos(thetaOverTwo.toDouble()).toFloat()
        deltaRotationVector[0] = sinThetaOverTwo * normValues[0]
        deltaRotationVector[1] = sinThetaOverTwo * normValues[1]
        deltaRotationVector[2] = sinThetaOverTwo * normValues[2]
        deltaRotationVector[3] = cosThetaOverTwo
    }

    private fun getRotationMatrixFromOrientation(o: FloatArray): FloatArray {
        val xM = FloatArray(9)
        val yM = FloatArray(9)
        val zM = FloatArray(9)

        val sinX = Math.sin(o[1].toDouble()).toFloat()
        val cosX = Math.cos(o[1].toDouble()).toFloat()
        val sinY = Math.sin(o[2].toDouble()).toFloat()
        val cosY = Math.cos(o[2].toDouble()).toFloat()
        val sinZ = Math.sin(o[0].toDouble()).toFloat()
        val cosZ = Math.cos(o[0].toDouble()).toFloat()

        // rotation about x-axis (displayPitch)
        xM[0] = 1.0f
        xM[1] = 0.0f
        xM[2] = 0.0f
        xM[3] = 0.0f
        xM[4] = cosX
        xM[5] = sinX
        xM[6] = 0.0f
        xM[7] = -sinX
        xM[8] = cosX

        // rotation about y-axis (displayRoll)
        yM[0] = cosY
        yM[1] = 0.0f
        yM[2] = sinY
        yM[3] = 0.0f
        yM[4] = 1.0f
        yM[5] = 0.0f
        yM[6] = -sinY
        yM[7] = 0.0f
        yM[8] = cosY

        // rotation about z-axis (azimuth)
        zM[0] = cosZ
        zM[1] = sinZ
        zM[2] = 0.0f
        zM[3] = -sinZ
        zM[4] = cosZ
        zM[5] = 0.0f
        zM[6] = 0.0f
        zM[7] = 0.0f
        zM[8] = 1.0f

        // rotation order is y, x, z (displayRoll, displayPitch, azimuth)
        var resultMatrix = matrixMultiplication(xM, yM)
        resultMatrix = matrixMultiplication(zM, resultMatrix)
        return resultMatrix
    }

    // sensor fusion values are computed at every 10 sec as initialized earlier
    private inner class calculateFusedOrientationTask : TimerTask() {
        internal var filter_coefficient = 0.85f
        internal var oneMinusCoeff = 1.0f - filter_coefficient

        override fun run() {
            // Azimuth
            if (gyroOrientation[0] < -0.5 * Math.PI && accMagOrientation[0] > 0.0) {
                fusedOrientation[0] =
                    (filter_coefficient * (gyroOrientation[0] + 2.0 * Math.PI) + oneMinusCoeff * accMagOrientation[0]).toFloat()
                fusedOrientation[0] -= if (fusedOrientation[0] > Math.PI) (2.0 * Math.PI).toFloat() else 0.toFloat()
            } else if (accMagOrientation[0] < -0.5 * Math.PI && gyroOrientation[0] > 0.0) {
                fusedOrientation[0] =
                    (filter_coefficient * gyroOrientation[0] + oneMinusCoeff * (accMagOrientation[0] + 2.0 * Math.PI)).toFloat()
                fusedOrientation[0] -= if (fusedOrientation[0] > Math.PI) (2.0 * Math.PI).toFloat() else 0.toFloat()
            } else
                fusedOrientation[0] =
                    filter_coefficient * gyroOrientation[0] + oneMinusCoeff * accMagOrientation[0]

            // Pitch
            if (gyroOrientation[1] < -0.5 * Math.PI && accMagOrientation[1] > 0.0) {
                fusedOrientation[1] =
                    (filter_coefficient * (gyroOrientation[1] + 2.0 * Math.PI) + oneMinusCoeff * accMagOrientation[1]).toFloat()
                fusedOrientation[1] -= if (fusedOrientation[1] > Math.PI) (2.0 * Math.PI).toFloat() else 0.toFloat()
            } else if (accMagOrientation[1] < -0.5 * Math.PI && gyroOrientation[1] > 0.0) {
                fusedOrientation[1] =
                    (filter_coefficient * gyroOrientation[1] + oneMinusCoeff * (accMagOrientation[1] + 2.0 * Math.PI)).toFloat()
                fusedOrientation[1] -= if (fusedOrientation[1] > Math.PI) (2.0 * Math.PI).toFloat() else 0.toFloat()
            } else
                fusedOrientation[1] =
                    filter_coefficient * gyroOrientation[1] + oneMinusCoeff * accMagOrientation[1]

            // Roll
            if (gyroOrientation[2] < -0.5 * Math.PI && accMagOrientation[2] > 0.0) {
                fusedOrientation[2] =
                    (filter_coefficient * (gyroOrientation[2] + 2.0 * Math.PI) + oneMinusCoeff * accMagOrientation[2]).toFloat()
                fusedOrientation[2] -= if (fusedOrientation[2] > Math.PI) (2.0 * Math.PI).toFloat() else 0.toFloat()
            } else if (accMagOrientation[2] < -0.5 * Math.PI && gyroOrientation[2] > 0.0) {
                fusedOrientation[2] =
                    (filter_coefficient * gyroOrientation[2] + oneMinusCoeff * (accMagOrientation[2] + 2.0 * Math.PI)).toFloat()
                fusedOrientation[2] -= if (fusedOrientation[2] > Math.PI) (2.0 * Math.PI).toFloat() else 0.toFloat()
            } else
                fusedOrientation[2] =
                    filter_coefficient * gyroOrientation[2] + oneMinusCoeff * accMagOrientation[2]

            // Overwrite gyro matrix and orientation with fused orientation to comensate gyro drift
            gyroMatrix = getRotationMatrixFromOrientation(fusedOrientation)
            System.arraycopy(fusedOrientation, 0, gyroOrientation, 0, 3)

            pitchOut = fusedOrientation[1]
            rollOut = fusedOrientation[2]
            yawOut = fusedOrientation[0]

            // present instance values
            newPitchOut = getPitch!! - pitchOut
            newRollOut = getRoll!! - rollOut
            newYawOut = getYaw!! - yawOut

            // saving values for calibration
            getPitch = pitchOut
            getRoll = rollOut
            getYaw = yawOut
        }
    }

    // analysis of driver behavior, computation is done at every 2 sec
    private inner class BehaviorAnalysis : TimerTask() {
        // factors needed for analysis
        internal var factorAcceleration = 0
        internal var factorTurn = 0

        //calculate rateOverYaw and rateOverPitch by taking the division of pitch/yaw over 30 sec interval
        internal var rateOverPitch = (finalOverPitch / count).toDouble()
        internal var rateOverYaw = (finalOverYaw / count).toDouble()

        override fun run() {
            // Hardcode a threshold
            if (rateOverPitch < 0.04) {
                if (xAccChange == true) {
                    // likely unsafe
                    factorAcceleration = 8
                } else {
                    // likely safe
                    factorAcceleration = 2
                }
            } else {
                if (xAccChange == true) {
                    // definitely unsafe
                    factorAcceleration = 10
                } else {
                    // probably unsafe
                    factorAcceleration = 8
                }
            }

            if (rateOverYaw < 0.01) {
                if (yAccChange == true) {
                    // likely unsafe
                    factorTurn = 8
                } else {
                    // likely safe
                    factorTurn = 2
                }
            } else {
                if (yAccChange == true) {
                    // definitely unsafe
                    factorTurn = 10
                } else {
                    // probably unsafe
                    factorTurn = 8
                }
            }

            // Calculate unsafeScore
            unsafeScore = 0.2 * factorAcceleration + 0.2 * factorTurn
        }
    }

    // sensor values computed for the last 30 sec
    private inner class ResetSensorValues : TimerTask() {
        override fun run() {
            finalOverYaw = finalOverYaw - getFinalOverYaw
            finalOverPitch = finalOverPitch - getFinalOverPitch
            overX = overX - getFinalOverX
            overY = overY - getFinalOverY

            getFinalOverPitch = finalOverPitch
            getFinalOverYaw = finalOverYaw
            getFinalOverX = overX
            getFinalOverY = overY
        }
    }
}


