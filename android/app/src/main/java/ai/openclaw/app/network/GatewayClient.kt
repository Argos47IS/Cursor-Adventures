package ai.openclaw.app.network

import android.util.Log
import ai.openclaw.app.crypto.DeviceIdentity
import ai.openclaw.app.data.SecurePreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.*
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    WAITING_CHALLENGE,
    AUTHENTICATING,
    CONNECTED,
    PAIRING_REQUIRED,
    ERROR,
}

sealed class GatewayEvent {
    data class ChatMessageReceived(val message: ChatMessage) : GatewayEvent()
    data class ChatStreamChunk(val chunk: String, val messageId: String?) : GatewayEvent()
    data class NodeCommandReceived(val id: String, val command: String, val params: JsonObject) : GatewayEvent()
    data class Error(val message: String, val code: String? = null) : GatewayEvent()
    data class StateChanged(val state: ConnectionState) : GatewayEvent()
    data object Connected : GatewayEvent()
    data object Disconnected : GatewayEvent()
    data object PairingRequired : GatewayEvent()
}

class GatewayClient(
    private val prefs: SecurePreferences,
    private val deviceIdentity: DeviceIdentity,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    private val client = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val requestIdCounter = AtomicLong(0)
    private val pendingRequests = mutableMapOf<String, CompletableDeferred<GatewayFrame>>()
    private var tickJob: Job? = null
    private var tickIntervalMs: Long = 15000
    private var challengeNonce: String? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _events = MutableSharedFlow<GatewayEvent>(replay = 0, extraBufferCapacity = 64)
    val events: SharedFlow<GatewayEvent> = _events.asSharedFlow()

    private val nodeCapabilities = listOf("camera", "canvas", "location", "voice")
    private val nodeCommands = listOf(
        "camera.snap",
        "camera.clip",
        "canvas.navigate",
        "canvas.eval",
        "canvas.snapshot",
        "canvas.a2ui.push",
        "canvas.a2ui.reset",
        "device.status",
        "device.info",
        "device.permissions",
        "device.health",
        "notifications.list",
        "notifications.actions",
        "photos.latest",
        "contacts.search",
        "contacts.add",
        "calendar.events",
        "calendar.add",
        "motion.activity",
        "motion.pedometer",
    )

    fun connect(host: String? = null, port: Int? = null, tls: Boolean? = null, token: String? = null) {
        val effectiveHost = host ?: prefs.gatewayHost ?: return
        val effectivePort = port ?: prefs.gatewayPort
        val effectiveTls = tls ?: prefs.tlsEnabled
        val effectiveToken = token ?: prefs.gatewayToken

        if (host != null) prefs.gatewayHost = host
        if (port != null) prefs.gatewayPort = port
        if (tls != null) prefs.tlsEnabled = tls
        if (token != null) prefs.gatewayToken = token

        disconnect()

        _connectionState.value = ConnectionState.CONNECTING
        emitEvent(GatewayEvent.StateChanged(ConnectionState.CONNECTING))

        val scheme = if (effectiveTls) "wss" else "ws"
        val url = "$scheme://$effectiveHost:$effectivePort"

        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = client.newWebSocket(request, createWebSocketListener(effectiveToken))
    }

    fun connectWithSetupCode(setupCodeBase64: String) {
        try {
            val decoded = android.util.Base64.decode(setupCodeBase64.trim(), android.util.Base64.DEFAULT)
            val setupCode = json.decodeFromString<SetupCode>(String(decoded))

            val uri = java.net.URI(setupCode.url)
            val host = uri.host
            val port = if (uri.port > 0) uri.port else SecurePreferences.DEFAULT_PORT
            val tls = uri.scheme == "wss"

            connect(host, port, tls, setupCode.bootstrapToken)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse setup code", e)
            _connectionState.value = ConnectionState.ERROR
            emitEvent(GatewayEvent.Error("Invalid setup code: ${e.message}"))
        }
    }

    fun disconnect() {
        tickJob?.cancel()
        tickJob = null
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        pendingRequests.values.forEach {
            it.completeExceptionally(Exception("Disconnected"))
        }
        pendingRequests.clear()
        challengeNonce = null
        _connectionState.value = ConnectionState.DISCONNECTED
        emitEvent(GatewayEvent.Disconnected)
    }

    suspend fun sendChatMessage(content: String, session: String = "main"): GatewayFrame? {
        val params = json.encodeToJsonElement(ChatSendParams(content, session))
        return sendRequest("chat.send", params)
    }

    suspend fun getChatHistory(session: String = "main", limit: Int = 50): GatewayFrame? {
        val params = json.encodeToJsonElement(ChatHistoryParams(session, limit))
        return sendRequest("chat.history", params)
    }

    suspend fun subscribeToChatEvents(session: String = "main"): GatewayFrame? {
        val params = buildJsonObject {
            put("session", session)
        }
        return sendRequest("chat.subscribe", params)
    }

    suspend fun respondToNodeCommand(requestId: String, result: JsonElement) {
        val frame = GatewayFrame(
            type = "res",
            id = requestId,
            ok = true,
            payload = result,
        )
        sendFrame(frame)
    }

    suspend fun respondToNodeCommandError(requestId: String, error: String) {
        val frame = GatewayFrame(
            type = "res",
            id = requestId,
            ok = false,
            error = JsonPrimitive(error),
        )
        sendFrame(frame)
    }

    private suspend fun sendRequest(method: String, params: JsonElement? = null): GatewayFrame? {
        val id = "req-${requestIdCounter.incrementAndGet()}"
        val frame = GatewayFrame(
            type = "req",
            id = id,
            method = method,
            params = params,
        )

        val deferred = CompletableDeferred<GatewayFrame>()
        pendingRequests[id] = deferred

        sendFrame(frame)

        return try {
            withTimeout(30_000) {
                deferred.await()
            }
        } catch (e: Exception) {
            pendingRequests.remove(id)
            Log.e(TAG, "Request $method timed out or failed", e)
            null
        }
    }

    private fun sendFrame(frame: GatewayFrame) {
        val text = json.encodeToString(frame)
        Log.d(TAG, ">>> $text")
        webSocket?.send(text)
    }

    private fun createWebSocketListener(token: String?): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket opened")
                _connectionState.value = ConnectionState.WAITING_CHALLENGE
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "<<< $text")
                handleMessage(text, token)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                _connectionState.value = ConnectionState.ERROR
                emitEvent(GatewayEvent.Error("Connection failed: ${t.message}"))
                scheduleReconnect()
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code $reason")
                _connectionState.value = ConnectionState.DISCONNECTED
                emitEvent(GatewayEvent.Disconnected)
                if (code != 1000) {
                    scheduleReconnect()
                }
            }
        }
    }

    private fun handleMessage(text: String, authToken: String?) {
        try {
            val frame = json.decodeFromString<GatewayFrame>(text)

            when (frame.type) {
                "event" -> handleEvent(frame, authToken)
                "res" -> handleResponse(frame)
                "req" -> handleNodeRequest(frame)
                else -> Log.w(TAG, "Unknown frame type: ${frame.type}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse message: $text", e)
        }
    }

    private fun handleEvent(frame: GatewayFrame, authToken: String?) {
        when (frame.event) {
            "connect.challenge" -> {
                val challenge = frame.payload?.let {
                    json.decodeFromJsonElement<ConnectChallenge>(it)
                }
                if (challenge != null) {
                    challengeNonce = challenge.nonce
                    _connectionState.value = ConnectionState.AUTHENTICATING
                    sendConnectRequest(challenge.nonce, authToken)
                }
            }
            "chat" -> {
                frame.payload?.let { payload ->
                    try {
                        val msg = json.decodeFromJsonElement<ChatMessage>(payload)
                        emitEvent(GatewayEvent.ChatMessageReceived(msg))
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse chat event", e)
                    }
                }
            }
            "chat.stream" -> {
                frame.payload?.let { payload ->
                    val obj = payload as? JsonObject
                    val chunk = obj?.get("content")?.jsonPrimitive?.contentOrNull ?: ""
                    val messageId = obj?.get("id")?.jsonPrimitive?.contentOrNull
                    emitEvent(GatewayEvent.ChatStreamChunk(chunk, messageId))
                }
            }
            else -> {
                Log.d(TAG, "Unhandled event: ${frame.event}")
            }
        }
    }

    private fun handleResponse(frame: GatewayFrame) {
        val id = frame.id ?: return

        if (id.startsWith("connect-")) {
            handleConnectResponse(frame)
            return
        }

        pendingRequests.remove(id)?.complete(frame)
    }

    private fun handleConnectResponse(frame: GatewayFrame) {
        if (frame.ok == true) {
            val helloOk = frame.payload?.let {
                json.decodeFromJsonElement<HelloOkPayload>(it)
            }

            helloOk?.auth?.deviceToken?.let { token ->
                prefs.deviceToken = token
            }

            helloOk?.policy?.tickIntervalMs?.let { interval ->
                tickIntervalMs = interval
            }

            _connectionState.value = ConnectionState.CONNECTED
            emitEvent(GatewayEvent.Connected)
            startTickLoop()

            scope.launch {
                subscribeToChatEvents()
            }
        } else {
            val errorPayload = frame.error
            val errorMsg = when {
                errorPayload is JsonPrimitive -> errorPayload.content
                errorPayload is JsonObject -> errorPayload["message"]?.jsonPrimitive?.contentOrNull ?: "Unknown error"
                else -> "Connection rejected"
            }

            if (errorMsg.contains("pairing", ignoreCase = true)) {
                _connectionState.value = ConnectionState.PAIRING_REQUIRED
                emitEvent(GatewayEvent.PairingRequired)
            } else {
                _connectionState.value = ConnectionState.ERROR
                emitEvent(GatewayEvent.Error(errorMsg))
            }
        }
    }

    private fun handleNodeRequest(frame: GatewayFrame) {
        val id = frame.id ?: return
        val command = frame.method ?: return
        val params = (frame.params as? JsonObject) ?: JsonObject(emptyMap())

        emitEvent(GatewayEvent.NodeCommandReceived(id, command, params))
    }

    private fun sendConnectRequest(nonce: String, authToken: String?) {
        val effectiveToken = authToken ?: prefs.deviceToken

        val (signature, signedAt) = deviceIdentity.signConnectPayload(
            nonce = nonce,
            clientId = "android-node",
            role = "node",
            scopes = emptyList(),
            token = effectiveToken,
        )

        val connectParams = ConnectParams(
            client = ClientInfo(),
            role = "node",
            caps = nodeCapabilities,
            commands = nodeCommands,
            permissions = mapOf(
                "camera.capture" to true,
                "screen.record" to false,
            ),
            auth = AuthInfo(
                token = authToken,
                deviceToken = prefs.deviceToken,
            ),
            device = DeviceInfo(
                id = deviceIdentity.deviceId,
                publicKey = deviceIdentity.publicKeyBase64,
                signature = signature,
                signedAt = signedAt,
                nonce = nonce,
            ),
        )

        val frame = GatewayFrame(
            type = "req",
            id = "connect-${UUID.randomUUID()}",
            method = "connect",
            params = json.encodeToJsonElement(connectParams),
        )

        sendFrame(frame)
    }

    private fun startTickLoop() {
        tickJob?.cancel()
        tickJob = scope.launch {
            while (isActive) {
                delay(tickIntervalMs)
                if (_connectionState.value == ConnectionState.CONNECTED) {
                    val frame = GatewayFrame(
                        type = "req",
                        id = "tick-${requestIdCounter.incrementAndGet()}",
                        method = "tick",
                    )
                    sendFrame(frame)
                }
            }
        }
    }

    private fun scheduleReconnect() {
        scope.launch {
            delay(5000)
            if (_connectionState.value != ConnectionState.CONNECTED) {
                val host = prefs.gatewayHost
                if (host != null) {
                    connect()
                }
            }
        }
    }

    private fun emitEvent(event: GatewayEvent) {
        scope.launch {
            _events.emit(event)
        }
    }

    companion object {
        private const val TAG = "GatewayClient"
    }
}
