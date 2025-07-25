// MainActivity.kt
package com.example.emergencydropalert

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.provider.Settings
import android.content.Intent

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.rotationMatrix
import okhttp3.*
import ui.ContactsFragment
import java.io.IOException
import kotlin.math.roundToInt

fun Double.format(digits: Int = 2): String = "%.${digits}f".format(this)

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var gyroscope: Sensor? = null
    private var isFallDetected = false
    private lateinit var cancelButton: Button
    private lateinit var statusText: TextView

    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private var isMonitoring = false
    private lateinit var speedText: TextView
    private lateinit var locationSettingsLauncher: ActivityResultLauncher<Intent>

    private var latestspeed = 0
//    private var lastTime: Long = 0
//    private var lastX = 0f
//    private var lastY = 0f
//    private var lastZ = 0f
    private var accelerometer: Sensor? = null
    private var rotSensor: Sensor? = null

    private var fallTimer: CountDownTimer? = null

    private val client = OkHttpClient()
    private val apiUrl = "https://pblrepo.onrender.com/emergency";

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        locationSettingsLauncher = registerForActivityResult( ActivityResultContracts.StartActivityForResult()){
                    checkLocationPermission()
        }
        setContentView(R.layout.activity_main)

        cancelButton = findViewById(R.id.cancel_button)
        statusText = findViewById(R.id.status_text)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        if (gyroscope == null) {
            Toast.makeText(this, "Gyroscope not available on this device", Toast.LENGTH_LONG).show()
        }
        cancelButton.setOnClickListener {
            isFallDetected = false
            fallTimer?.cancel()
            statusText.text = "Alert canceled"
        }
        startButton = findViewById(R.id.start_button)
        stopButton = findViewById(R.id.stop_button)

        startButton.setOnClickListener {
            checkLocationPermission()
            isMonitoring = true
            statusText.text = "Monitoring started"
        }

        stopButton.setOnClickListener {
            isMonitoring = false
            fallTimer?.cancel()
            statusText.text = "Monitoring stopped"
        }
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        speedText = findViewById(R.id.speed_text)
        rotSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        val contactsButton = findViewById<Button>(R.id.openContactsButton)
        contactsButton.setOnClickListener {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container , ContactsFragment() )
                .addToBackStack(null)
                .commit()
        }
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 100)
        } else {
            checkLocationEnabled()
        }
    }

    private fun checkLocationEnabled() {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        if (!gpsEnabled) {
            Toast.makeText(this, "Please enable location", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            locationSettingsLauncher.launch(intent)
        }
        else {
            startLocationUpdates()
        }
    }

    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L, // 1 second
                0f,
                locationListener
            )
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                checkLocationEnabled()
            } else {
                Toast.makeText(this, "Location permission is mandatory!", Toast.LENGTH_LONG).show()
                // Close the app or block interaction
                finish() // closes app
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkLocationEnabled()
        gyroscope?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        accelerometer?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        rotSensor?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }
    val buffer = mutableListOf<Double>()
    private var crashScore = 0
    private var gyroCrash = false
    private var speedCrash = false
    private var orientationCrash = false
    // Declare outside of the sensor type blocks:
    var yaw = 0.0
    var pitch = 0.0
    var roll = 0.0
    var rotationRate = 0.0
    private val threshold = 50
    private val yawThreshold = threshold
    private val pitchThreshold = threshold - 15
    private val rollThreshold = threshold - 25

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (event.sensor.type == Sensor.TYPE_GYROSCOPE ){

                rotationRate =  Math.sqrt(
                (it.values[0] * it.values[0] +
                        it.values[1] * it.values[1] +
                        it.values[2] * it.values[2]).toDouble()
            )
                buffer.add(rotationRate)

                gyroCrash = rotationRate > 1
                if (buffer.size >= 5){
                    val avgRot = buffer.average()
                    buffer.clear()
                   // Log.d( "Gyro Avg : " , " Avg rotationRate : ${avgRot.format() }")
                }

                //if (!isMonitoring) return
//                Log.d("THRESHOLD_GYRO", "RotationRate: ${rotationRate.format()} > 1 ? ${rotationRate > 1 }")
//                if (rotationRate > 1 ) {//&& !isFallDetected    3 is bit high val but realistic for demo purposes putted 1
//                // Gyroscope threshold
//                gyroCrash=true
////                isFallDetected = true
////                statusText.text = "Fall detected! Sending alert in 10s..."
//                }
//                else {
//                    gyroCrash = false
//                }
                if (event.sensor.type == Sensor.TYPE_ACCELEROMETER ) {
//
//                val curTime = System.currentTimeMillis()
//                if ((curTime - lastTime) > 1000) {
//                    val x = event.values[0]
//                    val y = event.values[1]
//                    val z = event.values[2]
//                    val speed = Math.abs(x + y + z - lastX - lastY - lastZ) / (curTime - lastTime) * 10000
//                    val displaySpeed = if (speed < 0.5) 0.0 else speed
//                    speedText.text = "Speed: ${String.format("%.2f", displaySpeed)} km/h"
//
//                    //Log.d("THRESHOLD_SPEED", "Speed: ${(displaySpeed.toDouble()).format()} > 15")
//
//                    // Convert speed to km/h (roughly estimated)
                    if (latestspeed > 10) {// && !isMonitoring
                        // Accelerometer threshold
                        speedCrash = true

                        isMonitoring = true
                        runOnUiThread { statusText.text = "Speed threshold reached. Monitoring auto-started." }
                    }
                    else {
                        speedCrash = false
                    }
//
//
//                    lastX = x
//                    lastY = y
//                    lastZ = z
//                    lastTime = curTime
//                }
                }
            }
//

            if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR ){
                val rotMatrix = FloatArray(9)
                val orientationAngles = FloatArray(9)

                SensorManager.getRotationMatrixFromVector( rotMatrix , event.values )
                SensorManager.getOrientation( rotMatrix , orientationAngles)

                yaw = Math.toDegrees(orientationAngles[0].toDouble()) // azimuth
                pitch = Math.toDegrees(orientationAngles[1].toDouble())
                roll = Math.toDegrees(orientationAngles[2].toDouble())


                //Log.d("THRESHOLD_GYRO", "RotationRate: ${rotationRate.format()} > 1 ? ${rotationRate > 1 }")
//                Log.d("THRESHOLD_ORIENTATION",
//                    "Yaw: ${yaw.format()} (|${Math.abs(yaw).format()}| > $yawThreshold) ? ${Math.abs(yaw) > yawThreshold}, " +
//                            "Pitch: ${pitch.format()} (|${Math.abs(pitch).format()}| > $pitchThreshold) ? ${Math.abs(pitch) > pitchThreshold}, " +
//                            "Roll: ${roll.format()} (|${Math.abs(roll).format()}| > $rollThreshold) ? ${Math.abs(roll) > rollThreshold}"
//                )

                if (Math.abs(yaw) > yawThreshold || Math.abs(pitch) > pitchThreshold || Math.abs(roll) > rollThreshold ) {

// Orientation threshold
                orientationCrash = true
                }
                else {
                    orientationCrash = false
                }

            }
            val crashScore = listOf(gyroCrash, speedCrash, orientationCrash).count { it }

            Log.d("CRASH_SCORE",
                "Gyro: $gyroCrash (rotationRate = ${rotationRate.format()} > 1?), " +
                        "Speed: $speedCrash (latestSpeed = ${latestspeed.toDouble().format()} > 15?), " +
                        "Orientation: $orientationCrash " +
                        "[yaw = ${Math.abs(yaw).format()} > $yawThreshold? ${Math.abs(yaw) > yawThreshold}, " +
                        "pitch = ${Math.abs(pitch).format()} > $pitchThreshold? ${Math.abs(pitch) > pitchThreshold}, " +
                        "roll = ${Math.abs(roll).format()} > $rollThreshold? ${Math.abs(roll) > rollThreshold}]"
            )

            if (crashScore >= 2 && !isFallDetected && isMonitoring ) {
                isFallDetected = true
                statusText.text = "🚨 Accident detected! Sending alert in 10s..."


                // Start timer
                fallTimer = object : CountDownTimer(10000, 1000) {
                    override fun onTick(millisUntilFinished: Long) {
                        statusText.text = "Sending in ${millisUntilFinished / 1000} seconds... Tap cancel if mistake!"
                    }

                    override fun onFinish() {
                        if (isFallDetected)
                            sendEmergencyCall()

                        Handler(Looper.getMainLooper()).postDelayed({
                            isFallDetected = false
                        } , 10000)
                    }
                }.start()

                gyroCrash = false
                speedCrash = false
                orientationCrash = false
            }
        }
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun sendEmergencyCall() {
        val request = Request.Builder()
            .url(apiUrl)
            .post(FormBody.Builder().build())
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { statusText.text = "Failed to send alert" }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread { statusText.text = "Emergency call initiated!" }
            }
        })
    }
    private lateinit var locationManager: LocationManager
    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            Log.d("LOCATION_UPDATE", "Lat: ${location.latitude}, Lon: ${location.longitude}, Speed: ${location.speed}")
            val speedMps = location.speed  // meters/second
            val speedKmph = speedMps * 3.6
            latestspeed = speedKmph.roundToInt()
            val displaySpeed = if (speedKmph < 0.5) 0.0 else speedKmph
            speedText.text = "Speed: ${String.format("%.2f", displaySpeed)} km/h"

            if (!isMonitoring && speedKmph > 15) {
                isMonitoring = true
                statusText.text = "Speed threshold reached. Monitoring auto-started."
            }
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }


}
