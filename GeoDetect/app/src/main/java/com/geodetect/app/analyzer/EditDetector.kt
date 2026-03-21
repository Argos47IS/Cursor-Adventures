package com.geodetect.app.analyzer

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.geodetect.app.R
import com.geodetect.app.models.ConfidenceLevel
import com.geodetect.app.models.EditDetectionResult
import com.geodetect.app.models.PhotoMetadata
import java.text.SimpleDateFormat
import java.util.Locale

class EditDetector(private val context: Context) {

    companion object {
        private val KNOWN_EDITORS = listOf(
            "photoshop", "lightroom", "gimp", "snapseed", "vsco",
            "afterlight", "pixlr", "canva", "picmonkey", "fotor",
            "befunky", "polarr", "darkroom", "luminar", "capture one",
            "affinity", "paint.net", "paintshop", "corel", "acdsee",
            "photoscape", "picasa", "fotojet", "ribbet", "ipiccy",
            "photopea", "pixelmator", "sketch", "figma", "illustrator",
            "indesign", "premiere", "after effects", "davinci",
            "facetune", "meitu", "beautycam", "b612", "snow",
            "airbrush", "picsart", "photo editor", "image editor",
            "retouch", "facetune2", "faceapp", "remini",
            "instagram", "snapchat", "tiktok", "wechat"
        )

        private val KNOWN_CAMERA_SOFTWARE = listOf(
            "camera", "gcam", "opencamera", "dng", "raw",
            "samsung", "huawei", "xiaomi", "oneplus", "pixel",
            "iphone", "ios", "android"
        )

        private val ADOBE_PATTERNS = listOf(
            "adobe", "photoshop", "lightroom", "bridge",
            "camera raw", "elements"
        )
    }

    fun analyze(metadata: PhotoMetadata, uri: Uri): EditDetectionResult {
        val reasons = mutableListOf<String>()
        var score = 0
        var editingSoftware: String? = null

        metadata.software?.let { sw ->
            val swLower = sw.lowercase()
            val matchedEditor = KNOWN_EDITORS.find { swLower.contains(it) }
            if (matchedEditor != null) {
                score += 40
                editingSoftware = sw
                reasons.add(context.getString(R.string.reason_software_editor, sw))
            } else if (!isLikelyCameraSoftware(swLower)) {
                score += 10
                reasons.add(context.getString(R.string.reason_non_camera_sw, sw))
            }

            if (ADOBE_PATTERNS.any { swLower.contains(it) }) {
                score += 15
                editingSoftware = sw
                reasons.add(context.getString(R.string.reason_adobe_product, sw))
            }
        }

        metadata.rawExifData["XMP Data"]?.let { xmp ->
            val xmpLower = xmp.lowercase()

            if (xmpLower.contains("photoshop") || xmpLower.contains("adobe")) {
                score += 30
                reasons.add(context.getString(R.string.reason_xmp_adobe))
            }

            if (xmpLower.contains("history") || xmpLower.contains("stEvt:action")) {
                score += 25
                reasons.add(context.getString(R.string.reason_xmp_history))
            }

            if (xmpLower.contains("creatortool")) {
                val toolMatch = Regex("creatortool[\"=>\\s]*([^\"<]+)", RegexOption.IGNORE_CASE)
                    .find(xmpLower)
                toolMatch?.groupValues?.get(1)?.let { tool ->
                    if (KNOWN_EDITORS.any { tool.contains(it) }) {
                        score += 25
                        editingSoftware = editingSoftware ?: tool.trim()
                        reasons.add(context.getString(R.string.reason_xmp_creator, tool.trim()))
                    }
                }
            }

            if (xmpLower.contains("derivedFrom") || xmpLower.contains("derivedfrom")) {
                score += 20
                reasons.add(context.getString(R.string.reason_xmp_derived))
            }
        }

        checkDateInconsistencies(metadata)?.let { (reason, _) ->
            score += 15
            reasons.add(reason)
        }

        val missingCount = checkMissingCriticalExif(metadata)
        if (missingCount >= 4) {
            score += 15
            reasons.add(context.getString(R.string.reason_missing_exif, missingCount))
        }

        checkFileSizeAnomaly(metadata, uri)?.let { reason ->
            score += 10
            reasons.add(reason)
        }

        metadata.rawExifData[androidx.exifinterface.media.ExifInterface.TAG_THUMBNAIL_IMAGE_LENGTH]?.let {
            if (metadata.rawExifData[androidx.exifinterface.media.ExifInterface.TAG_THUMBNAIL_IMAGE_WIDTH] == null) {
                score += 5
                reasons.add(context.getString(R.string.reason_thumbnail_mismatch))
            }
        }

        val confidence = when {
            score >= 50 -> ConfidenceLevel.HIGH
            score >= 25 -> ConfidenceLevel.MEDIUM
            score > 0 -> ConfidenceLevel.LOW
            else -> ConfidenceLevel.UNKNOWN
        }

        if (reasons.isEmpty()) {
            reasons.add(context.getString(R.string.reason_no_edits))
        }

        return EditDetectionResult(
            isLikelyEdited = score >= 25,
            confidenceLevel = confidence,
            reasons = reasons,
            editingSoftware = editingSoftware
        )
    }

    private fun isLikelyCameraSoftware(software: String): Boolean {
        return KNOWN_CAMERA_SOFTWARE.any { software.contains(it) }
    }

    private fun checkDateInconsistencies(metadata: PhotoMetadata): Pair<String, Long>? {
        val dateOriginal = metadata.dateTaken ?: return null
        val dateModified = metadata.dateModified ?: return null

        if (dateOriginal == dateModified) return null

        try {
            val sdf = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
            val original = sdf.parse(dateOriginal) ?: return null
            val modified = sdf.parse(dateModified) ?: return null

            val diffMs = modified.time - original.time
            val diffHours = diffMs / (1000 * 60 * 60)

            if (diffHours > 24) {
                val msg = context.getString(R.string.reason_date_mismatch, dateOriginal, dateModified, diffHours)
                return msg to diffHours
            }
        } catch (_: Exception) { }

        return null
    }

    private fun checkMissingCriticalExif(metadata: PhotoMetadata): Int {
        var missing = 0
        if (metadata.cameraMake == null) missing++
        if (metadata.cameraModel == null) missing++
        if (metadata.dateTaken == null) missing++
        if (metadata.focalLength == null) missing++
        if (metadata.aperture == null) missing++
        if (metadata.iso == null) missing++
        return missing
    }

    private fun checkFileSizeAnomaly(metadata: PhotoMetadata, uri: Uri): String? {
        if (metadata.fileSize <= 0) return null

        try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            val width = options.outWidth
            val height = options.outHeight
            if (width <= 0 || height <= 0) return null

            val megapixels = (width.toLong() * height.toLong()) / 1_000_000.0
            val fileSizeMB = metadata.fileSize / (1024.0 * 1024.0)

            val isJpeg = metadata.mimeType.contains("jpeg") || metadata.mimeType.contains("jpg")
            if (isJpeg && megapixels > 0) {
                val ratio = fileSizeMB / megapixels
                if (ratio > 3.0) {
                    val sizeStr = String.format(Locale.US, "%.1f MB", fileSizeMB)
                    val mpStr = String.format(Locale.US, "%.1f MP", megapixels)
                    return context.getString(R.string.reason_large_file, sizeStr, mpStr)
                }
                if (ratio < 0.1 && megapixels > 2) {
                    return context.getString(R.string.reason_small_file)
                }
            }
        } catch (_: Exception) { }

        return null
    }
}
