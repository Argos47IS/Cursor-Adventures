package com.geodetect.app.models

import android.content.Context
import com.geodetect.app.R

data class PhotoMetadata(
    val fileName: String = "",
    val fileSize: Long = 0,
    val mimeType: String = "",

    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude: Double? = null,
    val locationDescription: String? = null,

    val dateTaken: String? = null,
    val dateModified: String? = null,
    val dateDigitized: String? = null,
    val gpsDateStamp: String? = null,
    val gpsTimeStamp: String? = null,

    val cameraMake: String? = null,
    val cameraModel: String? = null,
    val lens: String? = null,
    val focalLength: String? = null,
    val aperture: String? = null,
    val iso: String? = null,
    val exposureTime: String? = null,
    val flash: String? = null,
    val whiteBalance: String? = null,

    val imageWidth: Int? = null,
    val imageHeight: Int? = null,
    val orientation: String? = null,
    val colorSpace: String? = null,
    val bitsPerSample: String? = null,
    val compression: String? = null,

    val software: String? = null,
    val xmpCreatorTool: String? = null,
    val xmpModifyDate: String? = null,
    val xmpHistory: String? = null,
    val photoshopAppVersion: String? = null,

    val editDetectionResult: EditDetectionResult = EditDetectionResult(),

    val rawExifData: Map<String, String> = emptyMap()
)

data class EditDetectionResult(
    val isLikelyEdited: Boolean = false,
    val confidenceLevel: ConfidenceLevel = ConfidenceLevel.UNKNOWN,
    val reasons: List<String> = emptyList(),
    val editingSoftware: String? = null
)

enum class ConfidenceLevel(private val labelResId: Int) {
    HIGH(R.string.confidence_high),
    MEDIUM(R.string.confidence_medium),
    LOW(R.string.confidence_low),
    UNKNOWN(R.string.confidence_unknown);

    fun getLocalizedLabel(context: Context): String = context.getString(labelResId)
}
