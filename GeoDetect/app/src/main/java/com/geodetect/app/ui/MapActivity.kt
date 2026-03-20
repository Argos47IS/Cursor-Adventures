package com.geodetect.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.geodetect.app.R
import com.geodetect.app.databinding.ActivityMapBinding
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import java.util.Locale

class MapActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_LATITUDE = "latitude"
        const val EXTRA_LONGITUDE = "longitude"
        const val EXTRA_ALTITUDE = "altitude"
        const val EXTRA_DATE = "date"
    }

    private lateinit var binding: ActivityMapBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().userAgentValue = packageName

        binding = ActivityMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val lat = intent.getDoubleExtra(EXTRA_LATITUDE, 0.0)
        val lon = intent.getDoubleExtra(EXTRA_LONGITUDE, 0.0)
        val alt = intent.getDoubleExtra(EXTRA_ALTITUDE, 0.0)
        val date = intent.getStringExtra(EXTRA_DATE) ?: ""

        binding.toolbar.setNavigationOnClickListener { finish() }

        setupMap(lat, lon)
        setupInfo(lat, lon, alt, date)

        binding.btnOpenExternal.setOnClickListener {
            val gmmIntentUri = Uri.parse("geo:$lat,$lon?q=$lat,$lon(Photo Location)")
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
            if (mapIntent.resolveActivity(packageManager) != null) {
                startActivity(mapIntent)
            } else {
                val browserIntent = Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://maps.google.com/?q=$lat,$lon"))
                startActivity(browserIntent)
            }
        }
    }

    private fun setupMap(lat: Double, lon: Double) {
        val map = binding.mapView
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)

        val controller = map.controller
        controller.setZoom(15.0)
        val point = GeoPoint(lat, lon)
        controller.setCenter(point)

        val marker = Marker(map)
        marker.position = point
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.title = getString(R.string.map_title)
        marker.snippet = String.format(Locale.US, "%.6f, %.6f", lat, lon)
        map.overlays.add(marker)
    }

    private fun setupInfo(lat: Double, lon: Double, alt: Double, date: String) {
        binding.tvCoordinates.text = String.format(
            Locale.US, "%.6f°, %.6f°", lat, lon
        )

        val details = mutableListOf<String>()
        if (alt != 0.0) {
            details.add(String.format(Locale.US, "Altitude: %.1f m", alt))
        }
        if (date.isNotBlank()) {
            details.add("Date: $date")
        }
        binding.tvLocationDetails.text = details.joinToString(" • ")
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }
}
