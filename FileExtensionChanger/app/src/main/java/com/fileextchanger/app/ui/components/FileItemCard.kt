package com.fileextchanger.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fileextchanger.app.FileItem
import com.fileextchanger.app.R

@Composable
fun FileItemCard(
    file: FileItem,
    onExtensionChanged: (String) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasError = file.errorMessage != null
    val cardColor by animateColorAsState(
        targetValue = when {
            hasError -> MaterialTheme.colorScheme.errorContainer
            file.isProcessed -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        label = "cardColor"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = when {
                        hasError -> Icons.Filled.Error
                        file.isProcessed -> Icons.Filled.CheckCircle
                        else -> Icons.Filled.InsertDriveFile
                    },
                    contentDescription = null,
                    tint = when {
                        hasError -> MaterialTheme.colorScheme.error
                        file.isProcessed -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(32.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.originalName,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = file.formattedSize,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.remove),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = ".${file.currentExtension.ifEmpty { stringResource(R.string.no_extension) }}",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Icon(
                    imageVector = Icons.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.padding(horizontal = 8.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = file.newExtension,
                    onValueChange = onExtensionChanged,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.new_ext_hint)) },
                    prefix = { Text(".") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    isError = hasError
                )
            }

            if (file.isProcessed) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.saved_as, file.targetFileName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (hasError) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = file.errorMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
