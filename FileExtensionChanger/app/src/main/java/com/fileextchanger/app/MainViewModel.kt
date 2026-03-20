package com.fileextchanger.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel : ViewModel() {

    val files = mutableStateListOf<FileItem>()

    var globalExtension by mutableStateOf("")
        private set

    var statusMessage by mutableStateOf<String?>(null)
        private set

    var isProcessing by mutableStateOf(false)
        private set

    var processedCount by mutableStateOf(0)
        private set

    var showConfirmDialog by mutableStateOf(false)
        private set

    fun requestProcess() {
        showConfirmDialog = true
    }

    fun dismissConfirmDialog() {
        showConfirmDialog = false
    }

    fun updateGlobalExtension(ext: String) {
        globalExtension = ext.replace(".", "").trim()
    }

    fun addFiles(context: Context, uris: List<Uri>) {
        viewModelScope.launch {
            for (uri in uris) {
                val existing = files.any { it.uri == uri }
                if (existing) continue

                val info = FileOperations.getFileInfo(context, uri)
                if (info != null) {
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: SecurityException) {
                        // Share intents don't grant persistable permissions — that's fine,
                        // the temporary read permission is enough for our use case.
                    }

                    files.add(
                        FileItem(
                            uri = uri,
                            originalName = info.first,
                            size = info.second
                        )
                    )
                }
            }
        }
    }

    fun updateFileExtension(index: Int, newExt: String) {
        if (index in files.indices) {
            files[index] = files[index].copy(newExtension = newExt.replace(".", "").trim())
        }
    }

    fun removeFile(index: Int) {
        if (index in files.indices) {
            files.removeAt(index)
        }
    }

    fun clearAll() {
        files.clear()
        statusMessage = null
        processedCount = 0
    }

    fun applyGlobalExtensionToAll() {
        if (globalExtension.isBlank()) return
        for (i in files.indices) {
            files[i] = files[i].copy(newExtension = globalExtension)
        }
    }

    fun confirmAndProcess(context: Context) {
        showConfirmDialog = false
        processFiles(context)
    }

    private fun processFiles(context: Context) {
        if (files.isEmpty()) {
            statusMessage = context.getString(R.string.status_no_files)
            return
        }

        if (isProcessing) return

        isProcessing = true
        processedCount = 0
        statusMessage = context.getString(R.string.status_processing)

        viewModelScope.launch {
            var successCount = 0
            var failCount = 0

            for (i in files.indices) {
                val file = files[i]
                val ext = file.newExtension.ifBlank { globalExtension }

                if (ext.isBlank()) {
                    files[i] = file.copy(errorMessage = context.getString(R.string.error_no_extension))
                    failCount++
                    continue
                }

                val result = withContext(Dispatchers.IO) {
                    FileOperations.saveFileWithNewExtension(
                        context = context,
                        sourceUri = file.uri,
                        originalName = file.originalName,
                        newExtension = ext
                    )
                }

                if (result.isSuccess) {
                    files[i] = file.copy(isProcessed = true, newExtension = ext, errorMessage = null)
                    successCount++
                } else {
                    val errorMsg = result.exceptionOrNull()?.localizedMessage
                        ?: context.getString(R.string.error_unknown)
                    files[i] = file.copy(errorMessage = errorMsg)
                    failCount++
                }

                processedCount = successCount + failCount
            }

            isProcessing = false
            statusMessage = buildString {
                append(context.getString(R.string.status_done))
                append(" ")
                append(context.getString(R.string.status_saved_count, successCount))
                if (failCount > 0) {
                    append(", ")
                    append(context.getString(R.string.status_failed_count, failCount))
                }
                append(" → Downloads/FileExtensionChanger")
            }
        }
    }
}
