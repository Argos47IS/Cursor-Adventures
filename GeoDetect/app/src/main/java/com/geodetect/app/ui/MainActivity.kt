package com.geodetect.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.geodetect.app.R
import com.geodetect.app.analyzer.EditDetector
import com.geodetect.app.analyzer.MetadataExtractor
import com.geodetect.app.databinding.ActivityMainBinding
import com.geodetect.app.models.ConfidenceLevel
import com.geodetect.app.models.PhotoMetadata
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var metadataExtractor: MetadataExtractor
    private lateinit var editDetector: EditDetector
    private var currentMetadata: PhotoMetadata? = null
    private var currentUri: Uri? = null
    private var isShowingResults = false

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { analyzePhoto(it) }
    }

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pickImage.launch("image/*")
        } else {
            Toast.makeText(this, getString(R.string.permission_denied), Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        metadataExtractor = MetadataExtractor(this)
        editDetector = EditDetector(this)

        setupToolbar()
        setupButtons()
        setupBackNavigation()

        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            uri?.let { analyzePhoto(it) }
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
    }

    private fun setupButtons() {
        binding.btnPickPhotoWelcome.setOnClickListener { launchPicker() }
        binding.btnAnalyzeAnother.setOnClickListener { launchPicker() }

        binding.btnViewMap.setOnClickListener {
            currentMetadata?.let { meta ->
                if (meta.latitude != null && meta.longitude != null) {
                    val intent = Intent(this, MapActivity::class.java).apply {
                        putExtra(MapActivity.EXTRA_LATITUDE, meta.latitude)
                        putExtra(MapActivity.EXTRA_LONGITUDE, meta.longitude)
                        putExtra(MapActivity.EXTRA_ALTITUDE, meta.altitude ?: 0.0)
                        putExtra(MapActivity.EXTRA_DATE, meta.dateTaken ?: "")
                    }
                    startActivity(intent)
                }
            }
        }

        binding.btnViewFullMetadata.setOnClickListener {
            currentMetadata?.let { meta ->
                val intent = Intent(this, FullMetadataActivity::class.java).apply {
                    putExtra(FullMetadataActivity.EXTRA_METADATA_JSON, metadataToJson(meta))
                }
                startActivity(intent)
            }
        }

        binding.btnShareResults.setOnClickListener {
            currentMetadata?.let { shareResults(it) }
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isShowingResults) {
                    showWelcome()
                    currentMetadata = null
                    currentUri = null
                    binding.imagePreview.setImageDrawable(null)
                    binding.scrollView.scrollTo(0, 0)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun launchPicker() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermission.launch(Manifest.permission.READ_MEDIA_IMAGES)
                return
            }
        }
        pickImage.launch("image/*")
    }

    private fun analyzePhoto(uri: Uri) {
        currentUri = uri
        showLoading()

        Thread {
            try {
                val metadata = metadataExtractor.extract(uri)
                val editResult = editDetector.analyze(metadata, uri)
                val fullMetadata = metadata.copy(editDetectionResult = editResult)
                currentMetadata = fullMetadata

                runOnUiThread { displayResults(fullMetadata, uri) }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, getString(R.string.error_loading), Toast.LENGTH_LONG).show()
                    showWelcome()
                }
            }
        }.start()
    }

    private fun displayResults(metadata: PhotoMetadata, uri: Uri) {
        showResults()

        loadPreviewBitmap(uri)

        binding.tvFileName.text = metadata.fileName
        binding.tvFileInfo.text = buildFileInfoString(metadata)

        displayEditDetection(metadata)
        displayLocation(metadata)
        displayDateTime(metadata)
        displayCamera(metadata)
        displayImageDetails(metadata)

        binding.scrollView.scrollTo(0, 0)
    }

    private fun loadPreviewBitmap(uri: Uri) {
        try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            val maxDim = 1200
            var sampleSize = 1
            while (options.outWidth / sampleSize > maxDim || options.outHeight / sampleSize > maxDim) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            contentResolver.openInputStream(uri)?.use { stream ->
                val bitmap = BitmapFactory.decodeStream(stream, null, decodeOptions)
                binding.imagePreview.setImageBitmap(bitmap)
            }
        } catch (_: Exception) {
            binding.imagePreview.setImageResource(R.drawable.ic_photo_library)
        }
    }

    private fun displayEditDetection(metadata: PhotoMetadata) {
        val result = metadata.editDetectionResult

        val (statusText, bgDrawable, textColor) = when {
            result.isLikelyEdited && result.confidenceLevel == ConfidenceLevel.HIGH ->
                Triple(getString(R.string.edit_likely), R.drawable.bg_status_danger, R.color.danger)
            result.isLikelyEdited ->
                Triple(getString(R.string.edit_likely), R.drawable.bg_status_warning, R.color.warning)
            result.confidenceLevel == ConfidenceLevel.UNKNOWN ->
                Triple(getString(R.string.edit_unknown), R.drawable.bg_status_info, R.color.info)
            else ->
                Triple(getString(R.string.edit_unlikely), R.drawable.bg_status_success, R.color.success)
        }

        binding.tvEditStatus.text = statusText
        binding.tvEditStatus.setTextColor(getColor(textColor))
        binding.editStatusBadge.setBackgroundResource(bgDrawable)

        val confLabel = result.confidenceLevel.getLocalizedLabel(this)
        binding.tvEditConfidence.text = "${getString(R.string.confidence)}: $confLabel"
        binding.tvEditConfidence.setTextColor(getColor(textColor))

        if (result.editingSoftware != null) {
            binding.tvEditSoftware.visibility = View.VISIBLE
            binding.tvEditSoftware.text = "${getString(R.string.editing_software)}: ${result.editingSoftware}"
        } else {
            binding.tvEditSoftware.visibility = View.GONE
        }

        val reasonsText = result.reasons.joinToString("\n") { "• $it" }
        binding.tvEditReasons.text = reasonsText
    }

    private fun displayLocation(metadata: PhotoMetadata) {
        if (metadata.latitude != null && metadata.longitude != null) {
            binding.locationDataContainer.visibility = View.VISIBLE
            binding.tvNoLocation.visibility = View.GONE

            setRowData(binding.rowLatitude.root,
                getString(R.string.label_latitude),
                String.format(Locale.US, "%.6f°", metadata.latitude))
            setRowData(binding.rowLongitude.root,
                getString(R.string.label_longitude),
                String.format(Locale.US, "%.6f°", metadata.longitude))

            if (metadata.altitude != null) {
                binding.rowAltitude.root.visibility = View.VISIBLE
                setRowData(binding.rowAltitude.root,
                    getString(R.string.label_altitude),
                    String.format(Locale.US, "%.1f m", metadata.altitude))
            } else {
                binding.rowAltitude.root.visibility = View.GONE
            }
        } else {
            binding.locationDataContainer.visibility = View.GONE
            binding.tvNoLocation.visibility = View.VISIBLE
        }
    }

    private fun displayDateTime(metadata: PhotoMetadata) {
        val container = binding.dateTimeDataContainer
        container.removeAllViews()

        val items = listOfNotNull(
            metadata.dateTaken?.let { getString(R.string.label_date_taken) to it },
            metadata.dateModified?.let { getString(R.string.label_date_modified) to it },
            metadata.dateDigitized?.let { getString(R.string.label_date_digitized) to it },
            metadata.gpsDateStamp?.let { getString(R.string.label_gps_date) to it },
            metadata.gpsTimeStamp?.let { getString(R.string.label_gps_time) to it }
        )

        if (items.isEmpty()) {
            addMetadataRow(container, getString(R.string.label_date_taken), getString(R.string.no_data))
        } else {
            items.forEach { (label, value) -> addMetadataRow(container, label, value) }
        }
    }

    private fun displayCamera(metadata: PhotoMetadata) {
        val container = binding.cameraDataContainer
        container.removeAllViews()

        val cameraName = listOfNotNull(metadata.cameraMake, metadata.cameraModel)
            .joinToString(" ")
            .takeIf { it.isNotBlank() }

        val items = listOfNotNull(
            cameraName?.let { getString(R.string.label_camera) to it },
            metadata.lens?.let { getString(R.string.label_lens) to it },
            metadata.focalLength?.let { getString(R.string.label_focal_length) to formatFocalLength(it) },
            metadata.aperture?.let { getString(R.string.label_aperture) to "f/$it" },
            metadata.iso?.let { getString(R.string.label_iso) to "ISO $it" },
            metadata.exposureTime?.let { getString(R.string.label_exposure) to formatExposure(it) },
            metadata.flash?.let { getString(R.string.label_flash) to it },
            metadata.whiteBalance?.let { getString(R.string.label_white_balance) to it },
            metadata.software?.let { getString(R.string.label_software) to it }
        )

        if (items.isEmpty()) {
            addMetadataRow(container, getString(R.string.label_camera), getString(R.string.no_data))
        } else {
            items.forEach { (label, value) -> addMetadataRow(container, label, value) }
        }
    }

    private fun displayImageDetails(metadata: PhotoMetadata) {
        val container = binding.imageDataContainer
        container.removeAllViews()

        val resolution = if (metadata.imageWidth != null && metadata.imageHeight != null) {
            "${metadata.imageWidth} × ${metadata.imageHeight}"
        } else null

        val items = listOfNotNull(
            resolution?.let { getString(R.string.label_resolution) to it },
            metadata.orientation?.let { getString(R.string.label_orientation) to it },
            metadata.colorSpace?.let { getString(R.string.label_color_space) to decodeColorSpace(it) },
            metadata.compression?.let { getString(R.string.label_compression) to it },
            getString(R.string.label_mime_type) to metadata.mimeType,
            getString(R.string.label_file_size) to formatFileSize(metadata.fileSize)
        )

        items.forEach { (label, value) -> addMetadataRow(container, label, value) }
    }

    private fun addMetadataRow(container: android.widget.LinearLayout, label: String, value: String) {
        val row = LayoutInflater.from(this).inflate(R.layout.item_metadata_row, container, false)
        row.findViewById<TextView>(R.id.tvLabel).text = label
        row.findViewById<TextView>(R.id.tvValue).text = value
        container.addView(row)
    }

    private fun setRowData(view: View, label: String, value: String) {
        view.findViewById<TextView>(R.id.tvLabel).text = label
        view.findViewById<TextView>(R.id.tvValue).text = value
    }

    private fun buildFileInfoString(metadata: PhotoMetadata): String {
        val parts = mutableListOf<String>()
        if (metadata.imageWidth != null && metadata.imageHeight != null) {
            parts.add("${metadata.imageWidth} × ${metadata.imageHeight}")
        }
        parts.add(formatFileSize(metadata.fileSize))
        parts.add(metadata.mimeType)
        return parts.joinToString(" • ")
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1_048_576 -> String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
            bytes >= 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    private fun formatFocalLength(fl: String): String {
        return try {
            if (fl.contains("/")) {
                val parts = fl.split("/")
                val mm = parts[0].toDouble() / parts[1].toDouble()
                String.format(Locale.US, "%.1f mm", mm)
            } else {
                "$fl mm"
            }
        } catch (_: Exception) { fl }
    }

    private fun formatExposure(exp: String): String {
        return try {
            val value = exp.toDouble()
            if (value < 1.0) {
                "1/${(1.0 / value).toInt()} s"
            } else {
                "${value}s"
            }
        } catch (_: Exception) { exp }
    }

    private fun decodeColorSpace(cs: String): String {
        return when (cs) {
            "1" -> "sRGB"
            "65535" -> "Uncalibrated"
            else -> cs
        }
    }

    private fun shareResults(metadata: PhotoMetadata) {
        val sb = StringBuilder()
        sb.appendLine("=== GeoDetect ===")
        sb.appendLine("${getString(R.string.label_file_name)}: ${metadata.fileName}")
        sb.appendLine("${getString(R.string.label_file_size)}: ${formatFileSize(metadata.fileSize)}")
        sb.appendLine()

        if (metadata.latitude != null && metadata.longitude != null) {
            sb.appendLine("--- ${getString(R.string.section_location)} ---")
            sb.appendLine("${getString(R.string.label_latitude)}: ${metadata.latitude}")
            sb.appendLine("${getString(R.string.label_longitude)}: ${metadata.longitude}")
            metadata.altitude?.let { sb.appendLine("${getString(R.string.label_altitude)}: ${it}m") }
            sb.appendLine("Google Maps: https://maps.google.com/?q=${metadata.latitude},${metadata.longitude}")
            sb.appendLine()
        }

        metadata.dateTaken?.let {
            sb.appendLine("--- ${getString(R.string.section_datetime)} ---")
            sb.appendLine("${getString(R.string.label_date_taken)}: $it")
            metadata.dateModified?.let { d -> sb.appendLine("${getString(R.string.label_date_modified)}: $d") }
            sb.appendLine()
        }

        val camera = listOfNotNull(metadata.cameraMake, metadata.cameraModel).joinToString(" ")
        if (camera.isNotBlank()) {
            sb.appendLine("--- ${getString(R.string.section_camera)} ---")
            sb.appendLine("${getString(R.string.label_camera)}: $camera")
            metadata.aperture?.let { sb.appendLine("${getString(R.string.label_aperture)}: f/$it") }
            metadata.iso?.let { sb.appendLine("ISO: $it") }
            sb.appendLine()
        }

        sb.appendLine("--- ${getString(R.string.section_edit_detection)} ---")
        val editResult = metadata.editDetectionResult
        sb.appendLine(if (editResult.isLikelyEdited) getString(R.string.edit_likely) else getString(R.string.edit_unlikely))
        sb.appendLine("${getString(R.string.confidence)}: ${editResult.confidenceLevel.getLocalizedLabel(this)}")
        editResult.editingSoftware?.let { sb.appendLine("${getString(R.string.editing_software)}: $it") }
        editResult.reasons.forEach { sb.appendLine("• $it") }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, sb.toString())
            putExtra(Intent.EXTRA_SUBJECT, "GeoDetect - ${metadata.fileName}")
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_results)))
    }

    private fun metadataToJson(metadata: PhotoMetadata): String {
        val gson = com.google.gson.Gson()
        return gson.toJson(metadata.rawExifData)
    }

    private fun showLoading() {
        isShowingResults = false
        binding.welcomeContainer.visibility = View.GONE
        binding.loadingContainer.visibility = View.VISIBLE
        binding.resultsContainer.visibility = View.GONE
    }

    private fun showResults() {
        isShowingResults = true
        binding.welcomeContainer.visibility = View.GONE
        binding.loadingContainer.visibility = View.GONE
        binding.resultsContainer.visibility = View.VISIBLE
    }

    private fun showWelcome() {
        isShowingResults = false
        binding.welcomeContainer.visibility = View.VISIBLE
        binding.loadingContainer.visibility = View.GONE
        binding.resultsContainer.visibility = View.GONE
    }
}
