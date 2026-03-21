package ai.openclaw.app.ui.connect

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ai.openclaw.app.OpenClawApplication
import ai.openclaw.app.data.SecurePreferences
import ai.openclaw.app.discovery.DiscoveredGateway
import ai.openclaw.app.network.ConnectionState
import ai.openclaw.app.service.ServiceLocator

enum class ConnectMode { SETUP_CODE, MANUAL }

@Composable
fun ConnectScreen() {
    val prefs = OpenClawApplication.instance.securePreferences
    val gatewayClient = ServiceLocator.gatewayClient
    val discovery = ServiceLocator.gatewayDiscovery

    val connectionState by gatewayClient.connectionState.collectAsState()
    val discoveredGateways by discovery.discoveredGateways.collectAsState()

    var connectMode by remember { mutableStateOf(ConnectMode.SETUP_CODE) }
    var setupCode by remember { mutableStateOf("") }
    var host by remember { mutableStateOf(prefs.gatewayHost ?: "") }
    var port by remember { mutableStateOf(prefs.gatewayPort.toString()) }
    var token by remember { mutableStateOf(prefs.gatewayToken ?: "") }
    var password by remember { mutableStateOf(prefs.gatewayPassword ?: "") }
    var tlsEnabled by remember { mutableStateOf(prefs.tlsEnabled) }
    var showAdvanced by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        discovery.startDiscovery()
    }

    DisposableEffect(Unit) {
        onDispose { discovery.stopDiscovery() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Connect to Gateway",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(8.dp))

        ConnectionStatusCard(connectionState)

        Spacer(modifier = Modifier.height(24.dp))

        if (connectionState == ConnectionState.CONNECTED) {
            ConnectedView(
                host = prefs.gatewayHost ?: "",
                port = prefs.gatewayPort,
                onDisconnect = { gatewayClient.disconnect() },
            )
        } else {
            TabRow(
                selectedTabIndex = connectMode.ordinal,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Tab(
                    selected = connectMode == ConnectMode.SETUP_CODE,
                    onClick = { connectMode = ConnectMode.SETUP_CODE },
                    text = { Text("Setup Code") },
                    icon = { Icon(Icons.Default.QrCode, null) },
                )
                Tab(
                    selected = connectMode == ConnectMode.MANUAL,
                    onClick = { connectMode = ConnectMode.MANUAL },
                    text = { Text("Manual") },
                    icon = { Icon(Icons.Default.Edit, null) },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (connectMode) {
                ConnectMode.SETUP_CODE -> {
                    OutlinedTextField(
                        value = setupCode,
                        onValueChange = { setupCode = it },
                        label = { Text("Paste setup code") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 5,
                        leadingIcon = { Icon(Icons.Default.Key, null) },
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            gatewayClient.connectWithSetupCode(setupCode.trim())
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = setupCode.isNotBlank() &&
                            connectionState != ConnectionState.CONNECTING,
                    ) {
                        if (connectionState == ConnectionState.CONNECTING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Connect with Setup Code")
                    }
                }

                ConnectMode.MANUAL -> {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("Gateway Host") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Dns, null) },
                        placeholder = { Text("192.168.1.100 or hostname.local") },
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it.filter { c -> c.isDigit() } },
                        label = { Text("Port") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        leadingIcon = { Icon(Icons.Default.Numbers, null) },
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("TLS", style = MaterialTheme.typography.bodyLarge)
                        Switch(
                            checked = tlsEnabled,
                            onCheckedChange = { tlsEnabled = it },
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = { showAdvanced = !showAdvanced },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            null,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Advanced Options")
                    }

                    AnimatedVisibility(visible = showAdvanced) {
                        Column {
                            OutlinedTextField(
                                value = token,
                                onValueChange = { token = it },
                                label = { Text("Token (optional)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                leadingIcon = { Icon(Icons.Default.VpnKey, null) },
                                trailingIcon = {
                                    IconButton(onClick = { showPassword = !showPassword }) {
                                        Icon(
                                            if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            null,
                                        )
                                    }
                                },
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("Password (optional)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                leadingIcon = { Icon(Icons.Default.Lock, null) },
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            val effectivePort = port.toIntOrNull() ?: SecurePreferences.DEFAULT_PORT
                            val effectiveToken = token.ifBlank { null }
                            if (password.isNotBlank()) {
                                prefs.gatewayPassword = password
                            }
                            gatewayClient.connect(host, effectivePort, tlsEnabled, effectiveToken)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = host.isNotBlank() &&
                            connectionState != ConnectionState.CONNECTING,
                    ) {
                        if (connectionState == ConnectionState.CONNECTING ||
                            connectionState == ConnectionState.AUTHENTICATING
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Connect")
                    }
                }
            }

            if (discoveredGateways.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                DiscoveredGatewaysSection(
                    gateways = discoveredGateways,
                    onSelect = { gw ->
                        host = gw.host
                        port = gw.port.toString()
                        connectMode = ConnectMode.MANUAL
                    },
                )
            }
        }
    }
}

@Composable
fun ConnectionStatusCard(state: ConnectionState) {
    val (icon, text, color) = when (state) {
        ConnectionState.CONNECTED -> Triple(Icons.Default.CheckCircle, "Connected", MaterialTheme.colorScheme.primary)
        ConnectionState.CONNECTING,
        ConnectionState.WAITING_CHALLENGE,
        ConnectionState.AUTHENTICATING -> Triple(Icons.Default.Sync, "Connecting…", MaterialTheme.colorScheme.tertiary)
        ConnectionState.PAIRING_REQUIRED -> Triple(Icons.Default.Warning, "Pairing Required", MaterialTheme.colorScheme.error)
        ConnectionState.ERROR -> Triple(Icons.Default.Error, "Connection Error", MaterialTheme.colorScheme.error)
        ConnectionState.DISCONNECTED -> Triple(Icons.Default.WifiOff, "Disconnected", MaterialTheme.colorScheme.outline)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = color)
            Spacer(modifier = Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.titleMedium, color = color)
        }
    }
}

@Composable
fun ConnectedView(host: String, port: Int, onDisconnect: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Default.CheckCircle,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Connected to Gateway",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "$host:$port",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(onClick = onDisconnect) {
                Icon(Icons.Default.LinkOff, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Disconnect")
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = "Your device is connected as a node. " +
            "The gateway can now invoke commands on this device.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
fun DiscoveredGatewaysSection(
    gateways: List<DiscoveredGateway>,
    onSelect: (DiscoveredGateway) -> Unit,
) {
    Text(
        "Discovered Gateways",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Spacer(modifier = Modifier.height(8.dp))
    gateways.forEach { gw ->
        Card(
            onClick = { onSelect(gw) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Router, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(gw.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${gw.host}:${gw.port}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
