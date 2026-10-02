package com.nsd.geoforge

import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class MainActivity : android.app.Activity() {
    private lateinit var latInput: EditText
    private lateinit var lonInput: EditText
    private lateinit var statusText: TextView
    private lateinit var locationManager: LocationManager

    private val prefs by lazy { getSharedPreferences("geoforge", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        latInput = EditText(this).apply {
            hint = "Latitude  (e.g. 17.3850)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
            setText(prefs.getString("lat", "17.3850"))
        }

        lonInput = EditText(this).apply {
            hint = "Longitude (e.g. 78.4867)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
            setText(prefs.getString("lon", "78.4867"))
        }

        statusText = TextView(this).apply {
            text = "Ready. Select GeoForge as your mock-location app in Developer Options."
            textSize = 14f
            setTextColor(0xFFAAB1C2.toInt())
            setPadding(0, 18, 0, 18)
        }

        val title = TextView(this).apply {
            text = "GeoForge"
            textSize = 34f
            gravity = Gravity.CENTER_HORIZONTAL
            setTextColor(0xFFF8F9FF.toInt())
            setPadding(0, 24, 0, 8)
        }

        val subtitle = TextView(this).apply {
            text = "Mock Location • Development & QA"
            textSize = 15f
            gravity = Gravity.CENTER_HORIZONTAL
            setTextColor(0xFF56E0FF.toInt())
            setPadding(0, 0, 0, 24)
        }

        val applyButton = Button(this).apply {
            text = "Apply Mock Location"
            setOnClickListener { applyMockLocation() }
        }

        val settingsButton = Button(this).apply {
            text = "Open Developer Options"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
            }
        }

        val clearButton = Button(this).apply {
            text = "Clear Mock Provider"
            setOnClickListener { clearMockProvider() }
        }

        val note = TextView(this).apply {
            text = "GeoForge uses Android's standard test-location provider. It does not hide mock status or bypass anti-spoofing checks."
            textSize = 12f
            setTextColor(0xFF7D859A.toInt())
            setPadding(0, 26, 0, 10)
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 18, 28, 26)
            addView(title)
            addView(subtitle)
            addView(latInput, fieldParams())
            addView(lonInput, fieldParams())
            addView(applyButton, buttonParams())
            addView(settingsButton, buttonParams())
            addView(clearButton, buttonParams())
            addView(statusText)
            addView(note)
        }

        setContentView(ScrollView(this).apply { addView(content) })
    }

    private fun fieldParams() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = 10 }

    private fun buttonParams() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = 10 }

    private fun applyMockLocation() {
        val lat = latInput.text.toString().trim().toDoubleOrNull()
        val lon = lonInput.text.toString().trim().toDoubleOrNull()

        if (lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
            Toast.makeText(this, "Enter valid latitude and longitude.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            for (provider in providers) {
                ensureTestProvider(provider)
                locationManager.setTestProviderEnabled(provider, true)
                val location = Location(provider).apply {
                    latitude = lat
                    longitude = lon
                    accuracy = 3f
                    altitude = 0.0
                    time = System.currentTimeMillis()
                    elapsedRealtimeNanos = android.os.SystemClock.elapsedRealtimeNanos()
                }
                locationManager.setTestProviderLocation(provider, location)
            }

            prefs.edit()
                .putString("lat", String.format(Locale.US, "%.7f", lat))
                .putString("lon", String.format(Locale.US, "%.7f", lon))
                .apply()

            statusText.text = "Mock location applied: %.6f, %.6f".format(Locale.US, lat, lon)
            Toast.makeText(this, "Mock location applied.", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            statusText.text = "Android rejected the test provider. Select GeoForge in Developer Options, then try again."
            Toast.makeText(this, "Select GeoForge as the mock-location app.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            statusText.text = "Could not apply location: ${e.message ?: "unknown error"}"
        }
    }

    private fun ensureTestProvider(provider: String) {
        try {
            locationManager.addTestProvider(
                provider,
                false,
                false,
                false,
                false,
                true,
                true,
                true,
                android.location.Criteria.POWER_LOW,
                android.location.Criteria.ACCURACY_FINE
            )
        } catch (_: IllegalArgumentException) {
            // Already present.
        }
    }

    private fun clearMockProvider() {
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        for (provider in providers) {
            try {
                locationManager.setTestProviderEnabled(provider, false)
                locationManager.removeTestProvider(provider)
            } catch (_: Exception) {
                // Ignore provider cleanup failures.
            }
        }
        statusText.text = "Mock providers cleared."
    }
}
