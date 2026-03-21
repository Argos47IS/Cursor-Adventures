package com.geodetect.app.analyzer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import com.geodetect.app.models.PhotoMetadata
import java.io.InputStream

class MetadataExtractor(private val context: Context) {

    fun extract(uri: Uri): PhotoMetadata {
        val fileName = getFileName(uri)
        val fileSize = getFileSize(uri)
        val mimeType = context.contentResolver.getType(uri) ?: "unknown"

        var metadata = PhotoMetadata(
            fileName = fileName,
            fileSize = fileSize,
            mimeType = mimeType
        )

        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        inputStream?.use { stream ->
            try {
                val exif = ExifInterface(stream)
                metadata = extractExifData(exif, metadata)
            } catch (_: Exception) { }
        }

        return metadata
    }

    private fun extractExifData(exif: ExifInterface, base: PhotoMetadata): PhotoMetadata {
        val latLong = exif.latLong
        val rawExif = extractAllRawExif(exif)

        return base.copy(
            latitude = latLong?.get(0),
            longitude = latLong?.get(1),
            altitude = exif.getAltitude(Double.NaN).takeIf { !it.isNaN() },

            dateTaken = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exif.getAttribute(ExifInterface.TAG_DATETIME),
            dateModified = exif.getAttribute(ExifInterface.TAG_DATETIME),
            dateDigitized = exif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED),
            gpsDateStamp = exif.getAttribute(ExifInterface.TAG_GPS_DATESTAMP),
            gpsTimeStamp = exif.getAttribute(ExifInterface.TAG_GPS_TIMESTAMP),

            cameraMake = exif.getAttribute(ExifInterface.TAG_MAKE),
            cameraModel = exif.getAttribute(ExifInterface.TAG_MODEL),
            lens = exif.getAttribute(ExifInterface.TAG_LENS_MAKE)?.let { make ->
                val model = exif.getAttribute(ExifInterface.TAG_LENS_MODEL) ?: ""
                "$make $model".trim()
            } ?: exif.getAttribute(ExifInterface.TAG_LENS_MODEL),
            focalLength = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH),
            aperture = exif.getAttribute(ExifInterface.TAG_F_NUMBER)
                ?: exif.getAttribute(ExifInterface.TAG_APERTURE_VALUE),
            iso = exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)
                ?: @Suppress("DEPRECATION") exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS),
            exposureTime = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME),
            flash = decodeFlash(exif.getAttributeInt(ExifInterface.TAG_FLASH, -1)),
            whiteBalance = decodeWhiteBalance(
                exif.getAttributeInt(ExifInterface.TAG_WHITE_BALANCE, -1)
            ),

            imageWidth = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0).takeIf { it > 0 }
                ?: exif.getAttributeInt(ExifInterface.TAG_PIXEL_X_DIMENSION, 0).takeIf { it > 0 },
            imageHeight = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0).takeIf { it > 0 }
                ?: exif.getAttributeInt(ExifInterface.TAG_PIXEL_Y_DIMENSION, 0).takeIf { it > 0 },
            orientation = decodeOrientation(
                exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            ),
            colorSpace = exif.getAttribute(ExifInterface.TAG_COLOR_SPACE),
            bitsPerSample = exif.getAttribute(ExifInterface.TAG_BITS_PER_SAMPLE),
            compression = exif.getAttribute(ExifInterface.TAG_COMPRESSION),

            software = exif.getAttribute(ExifInterface.TAG_SOFTWARE),

            rawExifData = rawExif
        )
    }

    private fun extractAllRawExif(exif: ExifInterface): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val tags = listOf(
            ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_DATETIME_DIGITIZED, ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL, ExifInterface.TAG_SOFTWARE,
            ExifInterface.TAG_IMAGE_WIDTH, ExifInterface.TAG_IMAGE_LENGTH,
            ExifInterface.TAG_ORIENTATION, ExifInterface.TAG_F_NUMBER,
            ExifInterface.TAG_EXPOSURE_TIME, ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
            @Suppress("DEPRECATION") ExifInterface.TAG_ISO_SPEED_RATINGS, ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_FLASH, ExifInterface.TAG_WHITE_BALANCE,
            ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_DATESTAMP, ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_COLOR_SPACE, ExifInterface.TAG_COMPRESSION,
            ExifInterface.TAG_BITS_PER_SAMPLE, ExifInterface.TAG_PIXEL_X_DIMENSION,
            ExifInterface.TAG_PIXEL_Y_DIMENSION, ExifInterface.TAG_LENS_MAKE,
            ExifInterface.TAG_LENS_MODEL, ExifInterface.TAG_SCENE_TYPE,
            ExifInterface.TAG_METERING_MODE, ExifInterface.TAG_EXPOSURE_MODE,
            ExifInterface.TAG_EXPOSURE_PROGRAM, ExifInterface.TAG_SHUTTER_SPEED_VALUE,
            ExifInterface.TAG_APERTURE_VALUE, ExifInterface.TAG_BRIGHTNESS_VALUE,
            ExifInterface.TAG_DIGITAL_ZOOM_RATIO, ExifInterface.TAG_CONTRAST,
            ExifInterface.TAG_SATURATION, ExifInterface.TAG_SHARPNESS,
            ExifInterface.TAG_SUBJECT_DISTANCE, ExifInterface.TAG_LIGHT_SOURCE,
            ExifInterface.TAG_IMAGE_DESCRIPTION, ExifInterface.TAG_ARTIST,
            ExifInterface.TAG_COPYRIGHT, ExifInterface.TAG_EXIF_VERSION,
            ExifInterface.TAG_GPS_PROCESSING_METHOD, ExifInterface.TAG_SCENE_CAPTURE_TYPE,
            ExifInterface.TAG_USER_COMMENT, ExifInterface.TAG_IMAGE_UNIQUE_ID,
            ExifInterface.TAG_BODY_SERIAL_NUMBER, ExifInterface.TAG_LENS_SERIAL_NUMBER,
            ExifInterface.TAG_CAMERA_OWNER_NAME, ExifInterface.TAG_GAMMA,
            ExifInterface.TAG_GPS_SPEED, ExifInterface.TAG_GPS_TRACK,
            ExifInterface.TAG_GPS_IMG_DIRECTION, ExifInterface.TAG_GPS_MAP_DATUM,
            ExifInterface.TAG_THUMBNAIL_IMAGE_LENGTH, ExifInterface.TAG_THUMBNAIL_IMAGE_WIDTH,
            ExifInterface.TAG_XMP
        )
        for (tag in tags) {
            exif.getAttribute(tag)?.let { value ->
                if (value.isNotBlank() && value != "0" && tag != ExifInterface.TAG_XMP) {
                    map[tag] = value
                }
                if (tag == ExifInterface.TAG_XMP && value.isNotBlank()) {
                    map["XMP Data"] = if (value.length > 2000) value.take(2000) + "..." else value
                }
            }
        }
        return map.toSortedMap()
    }

    private fun decodeFlash(flashValue: Int): String? {
        if (flashValue < 0) return null
        return when (flashValue) {
            0x00 -> "No Flash"
            0x01 -> "Fired"
            0x05 -> "Fired, Return not detected"
            0x07 -> "Fired, Return detected"
            0x08 -> "On, Did not fire"
            0x09 -> "On, Fired"
            0x0D -> "On, Return not detected"
            0x0F -> "On, Return detected"
            0x10 -> "Off, Did not fire"
            0x14 -> "Off, Did not fire, Return not detected"
            0x18 -> "Auto, Did not fire"
            0x19 -> "Auto, Fired"
            0x1D -> "Auto, Fired, Return not detected"
            0x1F -> "Auto, Fired, Return detected"
            0x20 -> "No flash function"
            0x30 -> "Off, No flash function"
            0x41 -> "Fired, Red-eye reduction"
            0x45 -> "Fired, Red-eye reduction, Return not detected"
            0x47 -> "Fired, Red-eye reduction, Return detected"
            0x49 -> "On, Red-eye reduction"
            0x4D -> "On, Red-eye reduction, Return not detected"
            0x4F -> "On, Red-eye reduction, Return detected"
            0x59 -> "Auto, Fired, Red-eye reduction"
            0x5D -> "Auto, Fired, Red-eye reduction, Return not detected"
            0x5F -> "Auto, Fired, Red-eye reduction, Return detected"
            else -> "Unknown ($flashValue)"
        }
    }

    private fun decodeWhiteBalance(wb: Int): String? {
        return when (wb) {
            0 -> "Auto"
            1 -> "Manual"
            else -> null
        }
    }

    private fun decodeOrientation(orient: Int): String {
        return when (orient) {
            ExifInterface.ORIENTATION_NORMAL -> "Normal (0°)"
            ExifInterface.ORIENTATION_ROTATE_90 -> "Rotated 90° CW"
            ExifInterface.ORIENTATION_ROTATE_180 -> "Rotated 180°"
            ExifInterface.ORIENTATION_ROTATE_270 -> "Rotated 270° CW"
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> "Flipped Horizontal"
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> "Flipped Vertical"
            ExifInterface.ORIENTATION_TRANSPOSE -> "Transposed"
            ExifInterface.ORIENTATION_TRANSVERSE -> "Transversed"
            else -> "Unknown"
        }
    }

    private fun getFileName(uri: Uri): String {
        var name = "unknown"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                name = cursor.getString(idx)
            }
        }
        return name
    }

    private fun getFileSize(uri: Uri): Long {
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (idx >= 0 && cursor.moveToFirst()) {
                size = cursor.getLong(idx)
            }
        }
        return size
    }
}
