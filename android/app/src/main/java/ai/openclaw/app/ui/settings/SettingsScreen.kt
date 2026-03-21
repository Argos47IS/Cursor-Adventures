package ai.openclaw.app.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ai.openclaw.app.OpenClawApplication
import ai.openclaw.app.crypto.DeviceIdentity
import ai.openclaw.app.network.ConnectionState
import ai.openclaw.app.service.ServiceLocator

@Composable
fun SettingsScreen() {
    val prefs = OpenClawApplication.instance.securePreferences
    val connectionState by ServiceLocator.gatewayClient.connectionState.collectAsState()
    val context = LocalContext.current
    var showResetDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineMedium,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Connection status
        SettingsSection("Connection") {
            SettingsItem(
                icon = Icons.Default.Wifi,
                title = "Status",
                subtitle = when (connectionState) {
                    ConnectionState.CONNECTED -> "Connected"
                    ConnectionState.CONNECTING -> "Connecting…"
                    ConnectionState.WAITING_CHALLENGE -> "Waiting for challenge…"
                    ConnectionState.AUTHENTICATING -> "Authenticating…"
                    ConnectionState.PAIRING_REQUIRED -> "Pairing required"
                    ConnectionState.ERROR -> "Error"
                    ConnectionState.DISCONNECTED -> "Disconnected"
                },
            )

            prefs.gatewayHost?.let { host ->
                SettingsItem(
                    icon = Icons.Default.Dns,
                    title = "Gateway",
                    subtitle = "$host:${prefs.gatewayPort}",
                )
            }

            SettingsItem(
                icon = Icons.Default.Security,
                title = "TLS",
                subtitle = if (prefs.tlsEnabled) "Enabled" else "Disabled",
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Device info
        SettingsSection("Device") {
            SettingsItem(
                icon = Icons.Default.Smartphone,
                title = "Model",
                subtitle = "${Build.MANUFACTURER} ${Build.MODEL}",
            )

            SettingsItem(
                icon = Icons.Default.Android,
                title = "Android Version",
                subtitle = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            )

            SettingsItem(
                icon = Icons.Default.Fingerprint,
                title = "Device ID",
                subtitle = ServiceLocator.deviceIdentity.deviceId.take(16) + "…",
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Node capabilities
        SettingsSection("Node Capabilities") {
            SettingsItem(
                icon = Icons.Default.CameraAlt,
                title = "Camera",
                subtitle = "camera.snap, camera.clip",
            )
            SettingsItem(
                icon = Icons.Default.Web,
                title = "Canvas",
                subtitle = "canvas.navigate, canvas.eval, canvas.snapshot, A2UI",
            )
            SettingsItem(
                icon = Icons.Default.LocationOn,
                title = "Location",
                subtitle = "location.get",
            )
            SettingsItem(
                icon = Icons.Default.Mic,
                title = "Voice",
                subtitle = "Transcript capture + TTS playback",
            )
            SettingsItem(
                icon = Icons.Default.PhoneAndroid,
                title = "Device",
                subtitle = "device.status, device.info, device.health",
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // About
        SettingsSection("About") {
            SettingsItem(
                icon = Icons.Default.Info,
                title = "Version",
                subtitle = "1.0.0 (Build 1)",
            )
            SettingsItem(
                icon = Icons.Default.Code,
                title = "OpenClaw",
                subtitle = "Open source AI assistant platform",
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Reset button
        OutlinedButton(
            onClick = { showResetDialog = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
        ) {
            Icon(Icons.Default.DeleteForever, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Reset All Data")
        }

        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                title = { Text("Reset All Data?") },
                text = {
                    Text("This will clear all gateway connection settings, device keys, and authentication tokens. You will need to re-pair with the gateway.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            ServiceLocator.gatewayClient.disconnect()
                            prefs.clearAll()
                            showResetDialog = false
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text("Reset")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetDialog = false }) {
                        Text("Cancel")
                    }
                },
            )
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
            content = content,
        )
    }
}

@Composable
fun SettingsItem(icon: ImageVector, title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
