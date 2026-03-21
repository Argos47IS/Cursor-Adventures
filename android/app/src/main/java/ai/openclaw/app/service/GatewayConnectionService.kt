package ai.openclaw.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import ai.openclaw.app.OpenClawApplication
import ai.openclaw.app.R
import ai.openclaw.app.network.ConnectionState
import ai.openclaw.app.ui.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

class GatewayConnectionService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        startForeground(OpenClawApplication.NOTIFICATION_ID, createNotification("Connecting to gateway…"))
        observeConnectionState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val host = intent.getStringExtra(EXTRA_HOST)
                val port = intent.getIntExtra(EXTRA_PORT, 18789)
                val tls = intent.getBooleanExtra(EXTRA_TLS, false)
                val token = intent.getStringExtra(EXTRA_TOKEN)
                ServiceLocator.gatewayClient.connect(host, port, tls, token)
            }
            ACTION_DISCONNECT -> {
                ServiceLocator.gatewayClient.disconnect()
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun observeConnectionState() {
        scope.launch {
            ServiceLocator.gatewayClient.connectionState.collectLatest { state ->
                val text = when (state) {
                    ConnectionState.CONNECTED -> getString(R.string.notification_text_connected)
                    ConnectionState.CONNECTING,
                    ConnectionState.WAITING_CHALLENGE,
                    ConnectionState.AUTHENTICATING -> getString(R.string.notification_text_connecting)
                    ConnectionState.PAIRING_REQUIRED -> getString(R.string.gateway_pairing)
                    else -> getString(R.string.notification_text_disconnected)
                }
                updateNotification(text)
            }
        }
    }

    private fun createNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, OpenClawApplication.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(OpenClawApplication.NOTIFICATION_ID, notification)
    }

    companion object {
        const val ACTION_CONNECT = "ai.openclaw.app.CONNECT"
        const val ACTION_DISCONNECT = "ai.openclaw.app.DISCONNECT"
        const val EXTRA_HOST = "host"
        const val EXTRA_PORT = "port"
        const val EXTRA_TLS = "tls"
        const val EXTRA_TOKEN = "token"
    }
}
