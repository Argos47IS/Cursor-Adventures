package com.fileextchanger.app

import android.net.Uri

data class FileItem(
    val uri: Uri,
    val originalName: String,
    val size: Long,
    val newExtension: String = "",
    val isProcessed: Boolean = false
) {
    val currentExtension: String
        get() {
            val dotIndex = originalName.lastIndexOf('.')
            return if (dotIndex >= 0) originalName.substring(dotIndex + 1) else ""
        }

    val nameWithoutExtension: String
        get() {
            val dotIndex = originalName.lastIndexOf('.')
            return if (dotIndex >= 0) originalName.substring(0, dotIndex) else originalName
        }

    val targetFileName: String
        get() {
            val ext = newExtension.ifBlank { currentExtension }
            return if (ext.isNotBlank()) "$nameWithoutExtension.$ext" else nameWithoutExtension
        }

    val formattedSize: String
        get() {
            if (size < 0) return "Unknown"
            val kb = size / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1.0 -> "%.2f GB".format(gb)
                mb >= 1.0 -> "%.2f MB".format(mb)
                kb >= 1.0 -> "%.1f KB".format(kb)
                else -> "$size B"
            }
        }
}
