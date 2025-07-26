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


class MainFragment : Fragment(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    private var isFallDetected = false
    private var countDownTimer: CountDownTimer? = null

    private lateinit var statusText: TextView
    private lateinit var cancelButton: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.main_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        statusText = view.findViewById(R.id.status_text)
        cancelButton = view.findViewById(R.id.cancel_button)

        val startButton = view.findViewById<Button>(R.id.start_button)
        val stopButton = view.findViewById<Button>(R.id.stop_button)
        //val openContactsButton = view.findViewById<Button>(R.id.openContactsButton)

        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        cancelButton.setOnClickListener {
            cancelAlert()
        }
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let {
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
            val x = it.values[0]
            val y = it.values[1]
            val z = it.values[2]
            val magnitude = Math.sqrt((x * x + y * y + z * z).toDouble())

            if (magnitude < 2.0 && !isFallDetected ) {
                isFallDetected  = true
                startCountdown()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun startCountdown() {
        statusText.text = "Fall detected! Sending alert in 10s..."
        cancelButton.visibility = View.VISIBLE

        countDownTimer = object : CountDownTimer(10000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                statusText.text = "Alert in ${millisUntilFinished / 1000}s... Tap Cancel if safe."
            }

            override fun onFinish() {
                sendEmergencyAlert()
            }
        }.start()
    }

    private fun cancelAlert() {
        countDownTimer?.cancel()
        isFallDetected  = false
        statusText.text = "Alert cancelled. You're safe."
        cancelButton.visibility = View.GONE
    }

    private fun sendEmergencyAlert() {
        statusText.text = "Sending emergency alert..."
        cancelButton.visibility = View.GONE

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
}
