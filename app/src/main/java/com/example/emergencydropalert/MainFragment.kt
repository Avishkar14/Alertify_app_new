package com.example.emergencydropalert.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.emergencydropalert.R
import okhttp3.*
import java.io.IOException
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.Intent
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat.getSystemService
import android.provider.Settings
import android.telephony.SmsManager
import android.util.Log
import  androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
//import com.google.android.ads.mediationtestsuite.viewmodels.ViewModelFactory
import data.AppDatabase
import data.EmergencyContact
import viewmodel.ContactsViewModel
import viewmodel.ContactsViewModelFactory
import kotlin.math.roundToInt

private lateinit var locationManager: LocationManager
private lateinit var locationListener: LocationListener
private lateinit var locationSettingsLauncher: ActivityResultLauncher<Intent>
private var isMonitoring = false
private lateinit var contactsViewModel: ContactsViewModel

private var gyroscope: Sensor? = null
private var rotSensor: Sensor? = null
private lateinit var speedText: TextView

private var latestSpeed = 0
//private var isFallDetected = false
private val buffer = mutableListOf<Double>()
private var gyroCrash = false
private var speedCrash = false
private var orientationCrash = false

private var yaw = 0.0
private var pitch = 0.0
private var roll = 0.0
private var rotationRate = 0.0

private val threshold = 50
private val yawThreshold = threshold
private val pitchThreshold = threshold - 15
private val rollThreshold = threshold - 25
private fun Double.format(digits: Int = 2) = "%.${digits}f".format(this)
private var isMonitoringActive = false
private val PERMISSION_REQUEST_CODE = 1001

private lateinit var sensorManager: SensorManager
private var accelerometer: Sensor? = null

private var isFallDetected = false
private var countDownTimer: CountDownTimer? = null

private lateinit var statusText: TextView
private lateinit var cancelButton: Button


class MainFragment : Fragment(), SensorEventListener {

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                checkLocationEnabled()
            } else {
                Toast.makeText(requireContext(), "Location permission is mandatory!", Toast.LENGTH_LONG).show()
                requireActivity().finish()
            }
        }
    private val requestSmsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d("SMS", "Permission granted")

            contactsViewModel.allContacts.asLiveData().observe(viewLifecycleOwner) { contacts ->
                Sms( contacts ) }// ✅ Call your function to send SMS here}
        } else {
            Toast.makeText(context, "SMS permission denied", Toast.LENGTH_SHORT).show()
        }
    }



    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.main_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val dao = AppDatabase.getDatabase(requireContext().applicationContext).emergencyContactDao()
        val viewModelFactory = ContactsViewModelFactory(dao)
        val viewModel = ViewModelProvider(this, viewModelFactory).get(ContactsViewModel::class.java)

        locationManager = context?.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        checkLocationPermission()
        checkAndRequestPermissions()
        contactsViewModel = ViewModelProvider(
            this,
            ContactsViewModelFactory(dao)
        ).get(ContactsViewModel::class.java)


        contactsViewModel.allContacts.asLiveData().observe(viewLifecycleOwner) { contacts ->
            if (contacts.isNotEmpty()) {
                Sms(contacts)  // ✅ Use real saved contacts here
            } else {
                Toast.makeText(context, "No emergency contacts found", Toast.LENGTH_SHORT).show()
            }
        }

        contactsViewModel.allContacts.asLiveData().observe(viewLifecycleOwner) { contacts ->
            Sms( contacts )}
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.SEND_SMS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            contactsViewModel.allContacts.asLiveData().observe(viewLifecycleOwner) { contacts ->
                if (contacts.isNotEmpty()) {
                    Sms(contacts)
                } else {
                    Toast.makeText(context, "No emergency contacts saved", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            requestSmsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
        }

        statusText = view.findViewById(R.id.status_text)
        cancelButton = view.findViewById(R.id.cancel_button)

        val startButton = view.findViewById<Button>(R.id.start_button)
        val stopButton = view.findViewById<Button>(R.id.stop_button)
        //val openContactsButton = view.findViewById<Button>(R.id.openContactsButton)

        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

//        locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager

        locationSettingsLauncher = registerForActivityResult( ActivityResultContracts.StartActivityForResult() ){
            checkLocationPermission()
        }
        startButton.setOnClickListener {
            if (!isMonitoringActive) {
                isMonitoring = true
                isMonitoringActive = true
                checkLocationPermission()
                statusText.text = "Monitoring started"
                Toast.makeText(context, "Monitoring started", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Already monitoring", Toast.LENGTH_SHORT).show()
            }
        }


        stopButton.setOnClickListener {
            if (isMonitoringActive) {
                isMonitoring = false
                isMonitoringActive = false
                countDownTimer?.cancel()
                if (::locationListener.isInitialized) {
                    locationManager.removeUpdates(locationListener)
                }
                sensorManager.unregisterListener(this)
                statusText.text = "Monitoring stopped"
                Toast.makeText(context, "Monitoring stopped", Toast.LENGTH_SHORT).show()
            }
        }

//        cancelButton.visibility = View.GONE

        cancelButton.setOnClickListener {
            cancelAlert()
        }
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        rotSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        speedText = view.findViewById(R.id.speed_text)

    }

    private fun checkLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION )
                    == PackageManager.PERMISSION_GRANTED -> { checkLocationEnabled() }

            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }

            else -> {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    private fun checkLocationEnabled() {
        //val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        if (!gpsEnabled) {
            Toast.makeText(requireContext(), "Please enable location", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            locationSettingsLauncher.launch(intent)
        }
        else {
            startLocationUpdates()
        }
    }

    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            locationListener = LocationListener { location ->
                val speedMps = location.speed
                val speedKmph = speedMps * 3.6
                latestSpeed = speedKmph.roundToInt()
                val displaySpeed = if (latestSpeed < 0.5) 0.0 else latestSpeed
                speedText.text = "Speed: ${String.format("%.2f", displaySpeed)} km/h"

                Log.d("SpeedCheck", "Speed: $speedKmph kmph, Monitoring started? $isMonitoring")

                if (latestSpeed > 10 && !isMonitoring) {
                    isMonitoring = true
                    isMonitoringActive = true
                    statusText.text = "Speed threshold reached. Auto monitoring started."
                    Toast.makeText(context, "Monitoring started due to speed", Toast.LENGTH_SHORT).show()
                }

            }


            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L, // 1 second
                0f,
                locationListener
            )
        }
    }


    override fun onResume() {
        super.onResume()
        checkLocationPermission()
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        rotSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        countDownTimer?.cancel()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            when (event.sensor.type) {
                Sensor.TYPE_GYROSCOPE -> {
                    rotationRate = Math.sqrt((it.values[0] * it.values[0] +
                            it.values[1] * it.values[1] +
                            it.values[2] * it.values[2]).toDouble())
                    buffer.add(rotationRate)
                    gyroCrash = rotationRate > 0.2
                    if (buffer.size >= 5) buffer.clear()
                }

                Sensor.TYPE_ACCELEROMETER -> {
                    if (latestSpeed > 10) {
                        speedCrash = true
                        if (!isMonitoring) {
                            isMonitoring = true
                            statusText.text = "Speed threshold reached. Monitoring auto-started."
                        }
                    } else {
                        speedCrash = false
                    }
                }

                Sensor.TYPE_ROTATION_VECTOR -> {
                    val rotMatrix = FloatArray(9)
                    val orientationAngles = FloatArray(3)

                    SensorManager.getRotationMatrixFromVector(rotMatrix, event.values)
                    SensorManager.getOrientation(rotMatrix, orientationAngles)

                    yaw = Math.toDegrees(orientationAngles[0].toDouble()) //azimuth
                    pitch = Math.toDegrees(orientationAngles[1].toDouble())
                    roll = Math.toDegrees(orientationAngles[2].toDouble())

                 if ( Math.abs(yaw) > yawThreshold || Math.abs(pitch) > pitchThreshold || Math.abs(roll) > rollThreshold ){
                     orientationCrash = true
                 }
                    else {
                        orientationCrash = false
                 }
                }
            }

            val crashScore = listOf(gyroCrash, speedCrash, orientationCrash).count { it }
            Log.d("CRASH_SCORE",
                "Gyro: $gyroCrash (rotationRate = ${rotationRate.format()} > 0.2?), " +
                        "Speed: $speedCrash (latestSpeed = ${latestSpeed.toDouble().format()} > 15?), " +
                        "Orientation: $orientationCrash " +
                        "[yaw = ${Math.abs(yaw).format()} > $yawThreshold? ${Math.abs(yaw) > yawThreshold}, " +
                        "pitch = ${Math.abs(pitch).format()} > $pitchThreshold? ${Math.abs(pitch) > pitchThreshold}, " +
                        "roll = ${Math.abs(roll).format()} > $rollThreshold? ${Math.abs(roll) > rollThreshold}]"
            )
            if (crashScore >= 2 && !isFallDetected && isMonitoring) {
                isFallDetected = true
//                startCountdown() // Reuse your countdown logic
                statusText.text = "🚨 Accident detected! Sending alert in 10s..."


                // Start timer
                countDownTimer = object : CountDownTimer(10000, 1000) {
                    override fun onTick(millisUntilFinished: Long) {
                        statusText.text = "Sending in ${millisUntilFinished / 1000} seconds... Tap cancel if mistake!"
                    }

                    override fun onFinish() {
                        if (isFallDetected)
                            sendEmergencyAlert()

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


    private fun cancelAlert() {
        countDownTimer?.cancel()
        isFallDetected  = false
        statusText.text = "Alert cancelled. You're safe."
        //cancelButton.visibility = View.GONE
    }

    private fun sendEmergencyAlert() {
        statusText.text = "Sending emergency alert..."
       // cancelButton.visibility = View.GONE

        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://your-api-endpoint.com/emergency") // ✅ Replace with your real URL
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    statusText.text = "Failed to send alert!"
                    Toast.makeText(requireContext(), "Alert failed", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                activity?.runOnUiThread {
                    statusText.text = "Alert sent successfully!"
                    Toast.makeText(requireContext(), "Alert sent", Toast.LENGTH_SHORT).show()
                }
            }
        })

        isFallDetected  = false
    }

    fun checkAndRequestPermissions(){
        val permissions = arrayOf(
            Manifest.permission.SEND_SMS, Manifest.permission.CALL_PHONE  )
        val missingPermissions = permissions.filter{
            ContextCompat.checkSelfPermission(requireContext() , it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()){
            ActivityCompat.requestPermissions((requireActivity()) , missingPermissions.toTypedArray() , PERMISSION_REQUEST_CODE)
        }
    }

    fun Sms(contacts: List<EmergencyContact>){
        val smsManager = SmsManager.getDefault()
        val msg = "Emergency!! Possible accident detected. Please check on me."

        for (contact in contacts){
            try {
                smsManager.sendTextMessage( contact.phone , null , msg , null , null)
            } catch (e: Exception){
                Log.e("SMS_ERROR", "Failed to send to ${contact.phone}: ${e.message}")
            }
        }

    }

    fun call( contact: EmergencyContact){
        val intent = Intent(Intent.ACTION_CALL)
        intent.data = Uri.parse("tel:${contact.phone}")
        if (ContextCompat.checkSelfPermission(requireContext() , Manifest.permission.CALL_PHONE ) == PackageManager.PERMISSION_GRANTED){
         startActivity(intent)
        }
        else {
            Toast.makeText( context , "Call permission not granted " , Toast.LENGTH_SHORT).show()
        }
    }
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("SMS", "Permission granted")
            } else {
                Toast.makeText(context, "SMS permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

}
