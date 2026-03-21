package ai.openclaw.app.commands

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import ai.openclaw.app.network.GatewayClient
import ai.openclaw.app.network.GatewayEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.json.*

class NodeCommandHandler(
    private val context: Context,
    private val gatewayClient: GatewayClient,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val json = Json { ignoreUnknownKeys = true }

    fun start() {
        scope.launch {
            gatewayClient.events.collectLatest { event ->
                if (event is GatewayEvent.NodeCommandReceived) {
                    handleCommand(event.id, event.command, event.params)
                }
            }
        }
    }

    fun stop() {
        scope.cancel()
    }

    private suspend fun handleCommand(requestId: String, command: String, params: JsonObject) {
        Log.d(TAG, "Handling command: $command")

        try {
            val result = when (command) {
                "device.status" -> handleDeviceStatus()
                "device.info" -> handleDeviceInfo()
                "device.permissions" -> handleDevicePermissions()
                "device.health" -> handleDeviceHealth()
                "canvas.navigate" -> handleCanvasNavigate(params)
                "canvas.eval" -> handleCanvasEval(params)
                "canvas.snapshot" -> handleCanvasSnapshot(params)
                "canvas.a2ui.push" -> handleA2UIPush(params)
                "canvas.a2ui.reset" -> handleA2UIReset()
                "camera.snap" -> handleCameraSnap(params)
                "notifications.list" -> handleNotificationsList()
                "contacts.search" -> handleContactsSearch(params)
                "calendar.events" -> handleCalendarEvents(params)
                else -> {
                    gatewayClient.respondToNodeCommandError(requestId, "Unknown command: $command")
                    return
                }
            }
            gatewayClient.respondToNodeCommand(requestId, result)
        } catch (e: Exception) {
            Log.e(TAG, "Command $command failed", e)
            gatewayClient.respondToNodeCommandError(requestId, "Command failed: ${e.message}")
        }
    }

    private fun handleDeviceStatus(): JsonElement {
        return buildJsonObject {
            put("status", "online")
            put("battery", getBatteryLevel())
            put("charging", isCharging())
            put("networkType", getNetworkType())
        }
    }

    private fun handleDeviceInfo(): JsonElement {
        return buildJsonObject {
            put("manufacturer", Build.MANUFACTURER)
            put("model", Build.MODEL)
            put("device", Build.DEVICE)
            put("brand", Build.BRAND)
            put("sdkVersion", Build.VERSION.SDK_INT)
            put("release", Build.VERSION.RELEASE)
            put("product", Build.PRODUCT)
            put("board", Build.BOARD)
            put("hardware", Build.HARDWARE)
        }
    }

    private fun handleDevicePermissions(): JsonElement {
        val permissions = mapOf(
            "camera" to hasPermission(android.Manifest.permission.CAMERA),
            "microphone" to hasPermission(android.Manifest.permission.RECORD_AUDIO),
            "location" to hasPermission(android.Manifest.permission.ACCESS_FINE_LOCATION),
            "contacts" to hasPermission(android.Manifest.permission.READ_CONTACTS),
            "calendar" to hasPermission(android.Manifest.permission.READ_CALENDAR),
            "notifications" to hasPermission(android.Manifest.permission.POST_NOTIFICATIONS),
        )
        return buildJsonObject {
            permissions.forEach { (key, value) ->
                put(key, value)
            }
        }
    }

    private fun handleDeviceHealth(): JsonElement {
        val runtime = Runtime.getRuntime()
        return buildJsonObject {
            put("freeMemory", runtime.freeMemory())
            put("totalMemory", runtime.totalMemory())
            put("maxMemory", runtime.maxMemory())
            put("availableProcessors", runtime.availableProcessors())
            put("uptime", android.os.SystemClock.elapsedRealtime())
        }
    }

    private fun handleCanvasNavigate(params: JsonObject): JsonElement {
        val url = params["url"]?.jsonPrimitive?.contentOrNull ?: ""
        canvasCallback?.onNavigate(url)
        return buildJsonObject { put("ok", true) }
    }

    private fun handleCanvasEval(params: JsonObject): JsonElement {
        val script = params["script"]?.jsonPrimitive?.contentOrNull ?: ""
        canvasCallback?.onEval(script)
        return buildJsonObject { put("ok", true) }
    }

    private fun handleCanvasSnapshot(@Suppress("UNUSED_PARAMETER") params: JsonObject): JsonElement {
        val snapshot = canvasCallback?.onSnapshot()
        return buildJsonObject {
            put("format", "jpeg")
            put("base64", snapshot ?: "")
        }
    }

    private fun handleA2UIPush(params: JsonObject): JsonElement {
        val data = params.toString()
        canvasCallback?.onA2UIPush(data)
        return buildJsonObject { put("ok", true) }
    }

    private fun handleA2UIReset(): JsonElement {
        canvasCallback?.onA2UIReset()
        return buildJsonObject { put("ok", true) }
    }

    private fun handleCameraSnap(@Suppress("UNUSED_PARAMETER") params: JsonObject): JsonElement {
        val snapshot = cameraCallback?.onSnap()
        return buildJsonObject {
            put("format", "jpeg")
            put("base64", snapshot ?: "")
        }
    }

    private fun handleNotificationsList(): JsonElement {
        return buildJsonObject {
            put("notifications", JsonArray(emptyList()))
        }
    }

    private fun handleContactsSearch(params: JsonObject): JsonElement {
        val query = params["query"]?.jsonPrimitive?.contentOrNull ?: ""
        return buildJsonObject {
            put("query", query)
            put("results", JsonArray(emptyList()))
        }
    }

    private fun handleCalendarEvents(params: JsonObject): JsonElement {
        val from = params["from"]?.jsonPrimitive?.contentOrNull
        val to = params["to"]?.jsonPrimitive?.contentOrNull
        return buildJsonObject {
            put("from", from ?: "")
            put("to", to ?: "")
            put("events", JsonArray(emptyList()))
        }
    }

    private fun getBatteryLevel(): Int {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
        return batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun isCharging(): Boolean {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
        return batteryManager.isCharging
    }

    private fun getNetworkType(): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = cm.activeNetwork ?: return "none"
        val caps = cm.getNetworkCapabilities(network) ?: return "none"
        return when {
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "unknown"
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    var canvasCallback: CanvasCallback? = null
    var cameraCallback: CameraCallback? = null

    interface CanvasCallback {
        fun onNavigate(url: String)
        fun onEval(script: String)
        fun onSnapshot(): String?
        fun onA2UIPush(data: String)
        fun onA2UIReset()
    }

    interface CameraCallback {
        fun onSnap(): String?
        fun onClip(): String?
    }

    companion object {
        private const val TAG = "NodeCommandHandler"
    }
}
