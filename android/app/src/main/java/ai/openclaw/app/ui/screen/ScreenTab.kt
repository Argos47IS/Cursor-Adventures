package ai.openclaw.app.ui.screen

import android.graphics.Bitmap
import android.util.Base64
import android.view.ViewGroup
import android.webkit.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import ai.openclaw.app.commands.NodeCommandHandler
import ai.openclaw.app.network.ConnectionState
import ai.openclaw.app.service.ServiceLocator
import java.io.ByteArrayOutputStream

@Composable
fun ScreenTab() {
    val connectionState by ServiceLocator.gatewayClient.connectionState.collectAsState()
    val commandHandler = ServiceLocator.nodeCommandHandler
    var webView by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        val callback = object : NodeCommandHandler.CanvasCallback {
            override fun onNavigate(url: String) {
                webView?.post { webView?.loadUrl(url) }
            }

            override fun onEval(script: String) {
                webView?.post {
                    webView?.evaluateJavascript(script, null)
                }
            }

            override fun onSnapshot(): String? {
                var result: String? = null
                webView?.let { wv ->
                    val bitmap = Bitmap.createBitmap(wv.width, wv.height, Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bitmap)
                    wv.draw(canvas)
                    val stream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
                    result = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                    bitmap.recycle()
                }
                return result
            }

            override fun onA2UIPush(data: String) {
                webView?.post {
                    val escaped = data.replace("\\", "\\\\").replace("'", "\\'")
                    webView?.evaluateJavascript(
                        "window.__openclaw_a2ui_push && window.__openclaw_a2ui_push('$escaped')",
                        null,
                    )
                }
            }

            override fun onA2UIReset() {
                webView?.post {
                    webView?.evaluateJavascript(
                        "window.__openclaw_a2ui_reset && window.__openclaw_a2ui_reset()",
                        null,
                    )
                }
            }
        }

        commandHandler.canvasCallback = callback

        onDispose {
            commandHandler.canvasCallback = null
        }
    }

    if (connectionState != ConnectionState.CONNECTED) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.WifiOff,
                    null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(64.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Connect to gateway first",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "The canvas screen shows web content served by the Gateway.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
                )
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 2.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Web,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = currentUrl.ifBlank { "Canvas" },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { webView?.reload() }) {
                    Icon(Icons.Default.Refresh, "Reload")
                }
            }
        }

        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        mediaPlaybackRequiresUserGesture = false
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            currentUrl = url ?: ""
                        }
                    }
                    webChromeClient = WebChromeClient()
                    webView = this

                    loadData(
                        """
                        <html>
                        <head>
                            <meta name="viewport" content="width=device-width, initial-scale=1.0">
                            <style>
                                body {
                                    font-family: system-ui, sans-serif;
                                    display: flex;
                                    align-items: center;
                                    justify-content: center;
                                    min-height: 100vh;
                                    margin: 0;
                                    background: #f8fafc;
                                    color: #475569;
                                    text-align: center;
                                    padding: 24px;
                                }
                                @media (prefers-color-scheme: dark) {
                                    body { background: #0f172a; color: #94a3b8; }
                                }
                                h2 { font-size: 1.25rem; margin-bottom: 0.5rem; }
                                p { font-size: 0.9rem; opacity: 0.8; }
                            </style>
                        </head>
                        <body>
                            <div>
                                <h2>Canvas Ready</h2>
                                <p>Waiting for gateway to navigate…</p>
                            </div>
                        </body>
                        </html>
                        """.trimIndent(),
                        "text/html",
                        "UTF-8",
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
