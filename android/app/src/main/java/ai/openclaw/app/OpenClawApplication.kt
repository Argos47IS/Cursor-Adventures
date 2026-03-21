package ai.openclaw.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import ai.openclaw.app.data.SecurePreferences

class OpenClawApplication : Application() {

    lateinit var securePreferences: SecurePreferences
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        securePreferences = SecurePreferences(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows gateway connection status"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "openclaw_gateway"
        const val NOTIFICATION_ID = 1001

        lateinit var instance: OpenClawApplication
            private set
    }
}
