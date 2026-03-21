package ai.openclaw.app.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class GatewayFrame(
    val type: String,
    val id: String? = null,
    val method: String? = null,
    val params: JsonElement? = null,
    val event: String? = null,
    val payload: JsonElement? = null,
    val ok: Boolean? = null,
    val error: JsonElement? = null,
    val seq: Long? = null,
    val stateVersion: Long? = null,
)

@Serializable
data class ConnectParams(
    val minProtocol: Int = PROTOCOL_VERSION,
    val maxProtocol: Int = PROTOCOL_VERSION,
    val client: ClientInfo,
    val role: String,
    val scopes: List<String> = emptyList(),
    val caps: List<String> = emptyList(),
    val commands: List<String> = emptyList(),
    val permissions: Map<String, Boolean> = emptyMap(),
    val auth: AuthInfo? = null,
    val locale: String = "en-US",
    val userAgent: String = "openclaw-android/${BuildConfig.VERSION_NAME}",
    val device: DeviceInfo? = null,
) {
    companion object {
        const val PROTOCOL_VERSION = 3
    }
}

@Serializable
data class ClientInfo(
    val id: String = "android-node",
    val version: String = BuildConfig.VERSION_NAME,
    val platform: String = "android",
    val mode: String = "node",
)

@Serializable
data class AuthInfo(
    val token: String? = null,
    val deviceToken: String? = null,
)

@Serializable
data class DeviceInfo(
    val id: String,
    val publicKey: String,
    val signature: String,
    val signedAt: Long,
    val nonce: String,
)

@Serializable
data class HelloOkPayload(
    val type: String? = null,
    val protocol: Int? = null,
    val policy: PolicyInfo? = null,
    val auth: HelloAuth? = null,
)

@Serializable
data class PolicyInfo(
    val tickIntervalMs: Long = 15000,
)

@Serializable
data class HelloAuth(
    val deviceToken: String? = null,
    val role: String? = null,
    val scopes: List<String>? = null,
)

@Serializable
data class ConnectChallenge(
    val nonce: String,
    val ts: Long,
)

@Serializable
data class ChatMessage(
    val id: String? = null,
    val role: String,
    val content: String,
    val timestamp: Long? = null,
    val session: String? = null,
    val streaming: Boolean? = null,
)

@Serializable
data class ChatSendParams(
    val content: String,
    val session: String = "main",
)

@Serializable
data class ChatHistoryParams(
    val session: String = "main",
    val limit: Int = 50,
)

@Serializable
data class NodeInvokeParams(
    val command: String,
    val params: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class SetupCode(
    val url: String,
    val bootstrapToken: String? = null,
)

object BuildConfig {
    const val VERSION_NAME = "1.0.0"
    const val VERSION_CODE = 1
}
