package com.fileextchanger.app

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream

object FileOperations {

    fun getFileInfo(context: Context, uri: Uri): Pair<String, Long>? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) else "unknown"
                    val size = if (sizeIdx >= 0) cursor.getLong(sizeIdx) else -1L
                    Pair(name, size)
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun saveFileWithNewExtension(
        context: Context,
        sourceUri: Uri,
        originalName: String,
        newExtension: String
    ): Result<Uri> {
        return try {
            val dotIndex = originalName.lastIndexOf('.')
            val baseName = if (dotIndex >= 0) originalName.substring(0, dotIndex) else originalName
            val targetName = if (newExtension.isNotBlank()) "$baseName.$newExtension" else baseName

            val inputStream = context.contentResolver.openInputStream(sourceUri)
                ?: return Result.failure(Exception("Cannot read source file"))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, targetName)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/FileExtensionChanger")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }

                val outputUri = context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    contentValues
                ) ?: return Result.failure(Exception("Cannot create output file"))

                context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                    inputStream.use { input -> input.copyTo(outputStream) }
                } ?: return Result.failure(Exception("Cannot write to output"))

                contentValues.clear()
                contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                context.contentResolver.update(outputUri, contentValues, null, null)

                Result.success(outputUri)
            } else {
                val downloadsDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "FileExtensionChanger"
                )
                if (!downloadsDir.exists()) downloadsDir.mkdirs()

                val outputFile = File(downloadsDir, targetName)
                FileOutputStream(outputFile).use { outputStream ->
                    inputStream.use { input -> input.copyTo(outputStream) }
                }

                Result.success(Uri.fromFile(outputFile))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
