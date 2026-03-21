package ai.openclaw.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecurePreferences(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "openclaw_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var gatewayHost: String?
        get() = prefs.getString(KEY_GATEWAY_HOST, null)
        set(value) = prefs.edit().putString(KEY_GATEWAY_HOST, value).apply()

    var gatewayPort: Int
        get() = prefs.getInt(KEY_GATEWAY_PORT, DEFAULT_PORT)
        set(value) = prefs.edit().putInt(KEY_GATEWAY_PORT, value).apply()

    var gatewayToken: String?
        get() = prefs.getString(KEY_GATEWAY_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_GATEWAY_TOKEN, value).apply()

    var gatewayPassword: String?
        get() = prefs.getString(KEY_GATEWAY_PASSWORD, null)
        set(value) = prefs.edit().putString(KEY_GATEWAY_PASSWORD, value).apply()

    var deviceToken: String?
        get() = prefs.getString(KEY_DEVICE_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_DEVICE_TOKEN, value).apply()

    var deviceId: String?
        get() = prefs.getString(KEY_DEVICE_ID, null)
        set(value) = prefs.edit().putString(KEY_DEVICE_ID, value).apply()

    var devicePublicKey: String?
        get() = prefs.getString(KEY_DEVICE_PUBLIC_KEY, null)
        set(value) = prefs.edit().putString(KEY_DEVICE_PUBLIC_KEY, value).apply()

    var devicePrivateKey: String?
        get() = prefs.getString(KEY_DEVICE_PRIVATE_KEY, null)
        set(value) = prefs.edit().putString(KEY_DEVICE_PRIVATE_KEY, value).apply()

    var tlsEnabled: Boolean
        get() = prefs.getBoolean(KEY_TLS_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_TLS_ENABLED, value).apply()

    var onboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, value).apply()

    var lastSessionId: String
        get() = prefs.getString(KEY_LAST_SESSION, "main") ?: "main"
        set(value) = prefs.edit().putString(KEY_LAST_SESSION, value).apply()

    fun clearAuth() {
        prefs.edit()
            .remove(KEY_GATEWAY_TOKEN)
            .remove(KEY_GATEWAY_PASSWORD)
            .remove(KEY_DEVICE_TOKEN)
            .apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val DEFAULT_PORT = 18789
        private const val KEY_GATEWAY_HOST = "gateway_host"
        private const val KEY_GATEWAY_PORT = "gateway_port"
        private const val KEY_GATEWAY_TOKEN = "gateway_token"
        private const val KEY_GATEWAY_PASSWORD = "gateway_password"
        private const val KEY_DEVICE_TOKEN = "device_token"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DEVICE_PUBLIC_KEY = "device_public_key"
        private const val KEY_DEVICE_PRIVATE_KEY = "device_private_key"
        private const val KEY_TLS_ENABLED = "tls_enabled"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_LAST_SESSION = "last_session"
    }
}
