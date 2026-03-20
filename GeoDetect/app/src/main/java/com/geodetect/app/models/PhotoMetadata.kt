package com.geodetect.app.models

data class PhotoMetadata(
    val fileName: String = "",
    val fileSize: Long = 0,
    val mimeType: String = "",

    // Location
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude: Double? = null,
    val locationDescription: String? = null,

    // Date/Time
    val dateTaken: String? = null,
    val dateModified: String? = null,
    val dateDigitized: String? = null,
    val gpsDateStamp: String? = null,
    val gpsTimeStamp: String? = null,

    // Camera Info
    val cameraMake: String? = null,
    val cameraModel: String? = null,
    val lens: String? = null,
    val focalLength: String? = null,
    val aperture: String? = null,
    val iso: String? = null,
    val exposureTime: String? = null,
    val flash: String? = null,
    val whiteBalance: String? = null,

    // Image Info
    val imageWidth: Int? = null,
    val imageHeight: Int? = null,
    val orientation: String? = null,
    val colorSpace: String? = null,
    val bitsPerSample: String? = null,
    val compression: String? = null,

    // Software/Edit Info
    val software: String? = null,
    val xmpCreatorTool: String? = null,
    val xmpModifyDate: String? = null,
    val xmpHistory: String? = null,
    val photoshopAppVersion: String? = null,

    // Edit Detection
    val editDetectionResult: EditDetectionResult = EditDetectionResult(),

    // Raw EXIF map for full dump
    val rawExifData: Map<String, String> = emptyMap()
)

data class EditDetectionResult(
    val isLikelyEdited: Boolean = false,
    val confidenceLevel: ConfidenceLevel = ConfidenceLevel.UNKNOWN,
    val reasons: List<String> = emptyList(),
    val editingSoftware: String? = null
)

enum class ConfidenceLevel(val label: String, val labelRu: String) {
    HIGH("High", "Высокая"),
    MEDIUM("Medium", "Средняя"),
    LOW("Low", "Низкая"),
    UNKNOWN("Unknown", "Неизвестно")
}
