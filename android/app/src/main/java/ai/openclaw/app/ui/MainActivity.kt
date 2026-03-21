package ai.openclaw.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import ai.openclaw.app.OpenClawApplication
import ai.openclaw.app.network.ConnectionState
import ai.openclaw.app.service.GatewayConnectionService
import ai.openclaw.app.service.ServiceLocator
import ai.openclaw.app.ui.chat.ChatScreen
import ai.openclaw.app.ui.connect.ConnectScreen
import ai.openclaw.app.ui.onboarding.OnboardingScreen
import ai.openclaw.app.ui.screen.ScreenTab
import ai.openclaw.app.ui.settings.SettingsScreen
import ai.openclaw.app.ui.theme.OpenClawTheme
import ai.openclaw.app.ui.voice.VoiceScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        ServiceLocator.nodeCommandHandler.start()

        setContent {
            OpenClawTheme {
                val prefs = OpenClawApplication.instance.securePreferences
                var showOnboarding by remember { mutableStateOf(!prefs.onboardingCompleted) }

                if (showOnboarding) {
                    OnboardingScreen(
                        onComplete = {
                            prefs.onboardingCompleted = true
                            showOnboarding = false
                        }
                    )
                } else {
                    MainScreen()
                }
            }
        }
    }

    override fun onDestroy() {
        ServiceLocator.nodeCommandHandler.stop()
        super.onDestroy()
    }

    fun startConnectionService(host: String, port: Int, tls: Boolean, token: String?) {
        val intent = Intent(this, GatewayConnectionService::class.java).apply {
            action = GatewayConnectionService.ACTION_CONNECT
            putExtra(GatewayConnectionService.EXTRA_HOST, host)
            putExtra(GatewayConnectionService.EXTRA_PORT, port)
            putExtra(GatewayConnectionService.EXTRA_TLS, tls)
            putExtra(GatewayConnectionService.EXTRA_TOKEN, token)
        }
        startForegroundService(intent)
    }

    fun stopConnectionService() {
        val intent = Intent(this, GatewayConnectionService::class.java).apply {
            action = GatewayConnectionService.ACTION_DISCONNECT
        }
        startService(intent)
    }
}

enum class Tab(val title: String, val icon: ImageVector) {
    CONNECT("Connect", Icons.Default.Wifi),
    CHAT("Chat", Icons.AutoMirrored.Filled.Chat),
    VOICE("Voice", Icons.Default.Mic),
    SCREEN("Screen", Icons.Default.Smartphone),
    SETTINGS("Settings", Icons.Default.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    var selectedTab by remember { mutableStateOf(Tab.CONNECT) }
    val connectionState by ServiceLocator.gatewayClient.connectionState.collectAsState()

    val effectiveTab = if (connectionState == ConnectionState.CONNECTED && selectedTab == Tab.CONNECT) {
        Tab.CHAT
    } else {
        selectedTab
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    val isSelected = effectiveTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTab = tab },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (tab == Tab.CONNECT) {
                                        val badgeColor = when (connectionState) {
                                            ConnectionState.CONNECTED -> MaterialTheme.colorScheme.primary
                                            ConnectionState.CONNECTING,
                                            ConnectionState.WAITING_CHALLENGE,
                                            ConnectionState.AUTHENTICATING -> MaterialTheme.colorScheme.tertiary
                                            ConnectionState.PAIRING_REQUIRED -> MaterialTheme.colorScheme.error
                                            else -> MaterialTheme.colorScheme.outline
                                        }
                                        Badge(containerColor = badgeColor)
                                    }
                                }
                            ) {
                                Icon(tab.icon, contentDescription = tab.title)
                            }
                        },
                        label = { Text(tab.title) },
                    )
                }
            }
        },
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            color = MaterialTheme.colorScheme.background,
        ) {
            when (effectiveTab) {
                Tab.CONNECT -> ConnectScreen()
                Tab.CHAT -> ChatScreen()
                Tab.VOICE -> VoiceScreen()
                Tab.SCREEN -> ScreenTab()
                Tab.SETTINGS -> SettingsScreen()
            }
        }
    }
}
