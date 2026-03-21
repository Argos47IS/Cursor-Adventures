# OpenClaw Android App

An Android companion node app for the [OpenClaw](https://github.com/openclaw/openclaw) open-source AI assistant platform. This app connects your Android device to an OpenClaw Gateway as a node, enabling chat, voice, canvas, camera, and device capabilities.

## Architecture

This app is a **companion node** — it does not host the Gateway itself. It connects to a running OpenClaw Gateway via WebSocket and registers as a node with device capabilities.

```
Android App  <-->  (mDNS/NSD + WebSocket)  <-->  OpenClaw Gateway
  (node)                                           (host machine)
```

### Key components

| Component | Description |
|---|---|
| `GatewayClient` | WebSocket client implementing the OpenClaw Gateway Protocol v3 |
| `DeviceIdentity` | EC P-256 keypair management and challenge-response signing |
| `SecurePreferences` | Encrypted persistence for auth tokens and gateway config |
| `GatewayDiscovery` | mDNS/NSD service discovery for `_openclaw-gw._tcp` |
| `NodeCommandHandler` | Handles node commands invoked by the gateway (camera, canvas, device, etc.) |
| `GatewayConnectionService` | Foreground service maintaining persistent gateway connection |

### UI Tabs

| Tab | Purpose |
|---|---|
| **Connect** | Setup Code or Manual gateway connection, mDNS discovery |
| **Chat** | Chat with your AI assistant (history, streaming, sessions) |
| **Voice** | Speech-to-text input with TTS response playback |
| **Screen** | Canvas WebView for gateway-served web content and A2UI |
| **Settings** | Device info, connection status, node capabilities, reset |

## Prerequisites

- Android device or emulator running **Android 12+ (API 31+)**
- OpenClaw Gateway running on your main machine
- Both devices on the same network (LAN or Tailscale tailnet)
- Java 17 and Android SDK for building

## Build & Run

```bash
cd android

# Debug build (Play flavor - Google Play safe)
./gradlew :app:assemblePlayDebug
./gradlew :app:installPlayDebug

# Debug build (Third-party flavor - full permissions)
./gradlew :app:assembleThirdPartyDebug
./gradlew :app:installThirdPartyDebug
```

## Product Flavors

| Flavor | SMS/Call Log | Description |
|---|---|---|
| `play` | Disabled | Google Play compliant, restricted permissions removed |
| `thirdParty` | Enabled | Full permissions for sideloading / F-Droid |

## Quick Start

### 1. Start the Gateway

On your main machine:

```bash
openclaw gateway --port 18789 --verbose
```

### 2. Connect from Android

**Option A: Setup Code**
1. Generate a setup code on the gateway machine (e.g., via Telegram `/pair`)
2. Paste the base64 setup code in the Connect tab
3. Tap "Connect with Setup Code"

**Option B: Manual**
1. Open Connect tab, select "Manual"
2. Enter your gateway's IP address and port (default: 18789)
3. Tap "Connect"

### 3. Approve Pairing

On the gateway machine:

```bash
openclaw devices list
openclaw devices approve <requestId>
```

### 4. Chat

Switch to the Chat tab and start talking to your AI assistant.

## USB Testing (No LAN Required)

Use `adb reverse` to tunnel the gateway port over USB:

```bash
# Terminal 1: Start gateway
openclaw gateway --port 18789 --verbose

# Terminal 2: USB tunnel
adb reverse tcp:18789 tcp:18789
```

Then in the app, connect to `127.0.0.1:18789` with TLS off.

## Node Capabilities

When connected, the device advertises these capabilities to the gateway:

- **Camera**: `camera.snap` (JPEG), `camera.clip` (MP4)
- **Canvas**: `canvas.navigate`, `canvas.eval`, `canvas.snapshot`, `canvas.a2ui.push`, `canvas.a2ui.reset`
- **Device**: `device.status`, `device.info`, `device.permissions`, `device.health`
- **Notifications**: `notifications.list`, `notifications.actions`
- **Contacts**: `contacts.search`, `contacts.add`
- **Calendar**: `calendar.events`, `calendar.add`
- **Photos**: `photos.latest`
- **Motion**: `motion.activity`, `motion.pedometer`

## Gateway Protocol

The app implements **Gateway Protocol v3** with:

- WebSocket transport with JSON frames
- Challenge-response authentication with EC P-256 signatures
- Device identity fingerprinting
- Encrypted token persistence
- Automatic reconnection
- Tick keepalive

## Security

- All credentials stored in Android EncryptedSharedPreferences (AES-256-GCM)
- EC P-256 device keypair for challenge-response auth
- TLS support for WebSocket connections
- Device token rotation support

## Permissions

| Permission | Purpose |
|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | Gateway WebSocket connection |
| `NEARBY_WIFI_DEVICES` / `ACCESS_FINE_LOCATION` | mDNS/NSD gateway discovery |
| `FOREGROUND_SERVICE` | Persistent gateway connection |
| `POST_NOTIFICATIONS` | Connection status notification |
| `CAMERA`, `RECORD_AUDIO` | Camera capture commands |
| `READ_CONTACTS`, `READ_CALENDAR` | Contact/calendar query commands |
| `ACTIVITY_RECOGNITION` | Motion/pedometer commands |

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **Networking**: OkHttp WebSocket
- **Crypto**: BouncyCastle (EC P-256)
- **Serialization**: kotlinx.serialization
- **Storage**: EncryptedSharedPreferences
- **Discovery**: Android NSD Manager + dnsjava
- **Camera**: CameraX
- **Canvas**: Android WebView

## License

Same as the OpenClaw project — see [LICENSE](https://github.com/openclaw/openclaw/blob/main/LICENSE).
