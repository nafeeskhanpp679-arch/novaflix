package com.example.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.data.model.MediaType
import com.example.data.model.VideoStreamSource
import com.example.data.stream.VidukiProvider
import com.example.ui.components.LocalIsTv
import com.example.ui.components.tvAutoFocus
import com.example.ui.components.tvFocusRing
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.io.ByteArrayInputStream

private val Accent = Color(0xFFE50914)

// Stream payload regex
private val StreamUrlPattern = Regex("""\.(m3u8|mp4|mkv)(\?|$)""", RegexOption.IGNORE_CASE)

// Ad / Tracker block regex
private val AdDomainRegex = Regex(
    """(doubleclick|googlesyndication|google-analytics|facebook|adservice|adsterra|bet365|popads|popcash|exoclick|juicyads|propellerads|adcash|hilltopads|monetag|coin-hive|onclick|redirect|ad-delivery|popunder|banner-ad|tracking|analytics)""",
    RegexOption.IGNORE_CASE
)

// Inject AdBlocker CSS/JS to eliminate popups and overlay click traps
private val AdBlockJs = """
    (function() {
        try {
            window.open = function() { return null; };
            window.alert = function() {};
            window.confirm = function() { return false; };
            var style = document.createElement('style');
            style.type = 'text/css';
            style.innerHTML = 'iframe[src*="ad"], div[class*="ad-"], div[id*="ad-"], div[class*="pop"], div[id*="pop"], a[target="_blank"], .ad-box, #popunder { display: none !important; visibility: hidden !important; pointer-events: none !important; opacity: 0 !important; }';
            if (document.head) { document.head.appendChild(style); }
            var removeAds = function() {
                var popovers = document.querySelectorAll('div[style*="z-index"], a[target="_blank"]');
                for (var i = 0; i < popovers.length; i++) {
                    var p = popovers[i];
                    if (p.style.zIndex > 1000 || p.target === '_blank') {
                        p.removeAttribute('target');
                        p.onclick = null;
                    }
                }
            };
            setInterval(removeAds, 1000);
        } catch(e) {}
    })();
""".trimIndent()

/**
 * WebView-based player for Viduki.net servers with built-in AdBlocker.
 */
@Composable
fun VidukiPlayerScreen(
    title: String,
    mediaType: MediaType,
    mediaId: String,
    season: Int,
    episode: Int,
    onBackClick: () -> Unit,
    onDirectPlayReady: ((VideoStreamSource) -> Unit)? = null
) {
    val context = LocalContext.current
    val isTv = LocalIsTv.current

    var currentApi by remember { mutableStateOf(VidukiProvider.Api.API_1) }
    var manualOverride by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var allServersFailed by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var extractedSource by remember { mutableStateOf<VideoStreamSource?>(null) }
    var extractedForApi by remember { mutableStateOf<VidukiProvider.Api?>(null) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    // Overlay controls (back/title, Native HD pill, Server 1/2/3/4 bar) auto-hide while
    // watching, and come back on any touch / remote key press.
    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteraction by remember { mutableStateOf(System.currentTimeMillis()) }
    fun pokeControls() {
        controlsVisible = true
        lastInteraction = System.currentTimeMillis()
    }

    LaunchedEffect(controlsVisible, lastInteraction, allServersFailed) {
        if (controlsVisible && !allServersFailed) {
            delay(3500)
            controlsVisible = false
        }
    }
    // a fresh "Native HD" source appeared -> make sure the user can see the pill
    LaunchedEffect(extractedSource) { if (extractedSource != null) pokeControls() }
    LaunchedEffect(allServersFailed) { if (allServersFailed) pokeControls() }

    fun currentUrl(api: VidukiProvider.Api) =
        VidukiProvider.buildUrl(api, mediaType, mediaId, season, episode)

    fun loadServer(api: VidukiProvider.Api, manual: Boolean) {
        currentApi = api
        manualOverride = manual
        allServersFailed = false
        isLoading = true
        extractedSource = null
        extractedForApi = null
        val url = currentUrl(api)
        val js = "document.getElementById('videoFrame').src = " + JSONObject.quote(url) + ";"
        webViewRef?.evaluateJavascript(js, null)
    }

    fun tryFallback(failedFrom: VidukiProvider.Api) {
        val order = VidukiProvider.Api.fallbackOrder
        val nextIndex = order.indexOf(failedFrom) + 1
        if (nextIndex < order.size) {
            loadServer(order[nextIndex], manual = false)
        } else {
            allServersFailed = true
            isLoading = false
        }
    }

    DisposableEffect(Unit) {
        val activity = context as? Activity
        val window = activity?.window
        val originalOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            activity?.requestedOrientation = originalOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            controller?.show(WindowInsetsCompat.Type.systemBars())
            webViewRef?.destroy()
        }
    }

    LaunchedEffect(mediaId, season, episode) {
        if (webViewRef != null) {
            loadServer(currentApi, manualOverride)
        }
    }

    BackHandler(onBack = onBackClick)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { pokeControls(); false }
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                try {
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        // Never consume the touch (the embedded player still needs it) - just
                        // bring the overlay back for a few seconds.
                        setOnTouchListener { _, ev ->
                            if (ev.actionMasked == MotionEvent.ACTION_DOWN || ev.actionMasked == MotionEvent.ACTION_UP) {
                                pokeControls()
                            }
                            false
                        }
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.setSupportMultipleWindows(false) // Block popup windows
                        settings.javaScriptCanOpenWindowsAutomatically = false

                        webChromeClient = object : WebChromeClient() {
                            override fun onCreateWindow(
                                view: WebView?,
                                isDialog: Boolean,
                                isUserGesture: Boolean,
                                resultMsg: android.os.Message?
                            ): Boolean {
                                // Block popups completely
                                return false
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                                view?.evaluateJavascript(AdBlockJs, null)
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val reqUrl = request?.url?.toString() ?: return false
                                // Allow Viduki domain and inner video embeds
                                if (reqUrl.contains("viduki.net", ignoreCase = true) ||
                                    reqUrl.contains("vidsrc", ignoreCase = true) ||
                                    reqUrl.contains("m3u8", ignoreCase = true) ||
                                    reqUrl.startsWith("data:")
                                ) {
                                    return false
                                }
                                // Block external popups and ad redirects
                                Log.d("AdBlocker", "Blocked pop/redirect URL: $reqUrl")
                                return true
                            }

                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                val url = request?.url?.toString()
                                if (url != null) {
                                    // 1. Sniff raw video source if exposed
                                    if (StreamUrlPattern.containsMatchIn(url) &&
                                        !AdDomainRegex.containsMatchIn(url)
                                    ) {
                                        val apiAtRequestTime = currentApi
                                        mainHandler.post {
                                            if (extractedForApi != apiAtRequestTime) {
                                                extractedForApi = apiAtRequestTime
                                                extractedSource = VideoStreamSource(
                                                    quality = "Viduki ${apiAtRequestTime.displayName} (Direct)",
                                                    url = url,
                                                    isHls = url.contains(".m3u8", ignoreCase = true),
                                                    headers = mapOf(
                                                        "User-Agent" to (view?.settings?.userAgentString ?: ""),
                                                        "Referer" to "https://viduki.net/"
                                                    )
                                                )
                                            }
                                        }
                                    }

                                    // 2. Block ad & tracker network requests
                                    if (AdDomainRegex.containsMatchIn(url) && !StreamUrlPattern.containsMatchIn(url)) {
                                        Log.d("AdBlocker", "Blocked ad request: $url")
                                        return WebResourceResponse(
                                            "text/plain",
                                            "utf-8",
                                            ByteArrayInputStream(ByteArray(0))
                                        )
                                    }
                                }
                                return super.shouldInterceptRequest(view, request)
                            }
                        }

                        addJavascriptInterface(
                            object {
                                @JavascriptInterface
                                fun onAllServersFailed(dataJson: String) {
                                    Log.d("Viduki", "all-servers-failed from ${currentApi.displayName}: $dataJson")
                                    tryFallback(currentApi)
                                }
                            },
                            "AndroidBridge"
                        )

                        val initialUrl = currentUrl(currentApi)
                        val vidukiOrigin = VidukiProvider.ORIGIN
                        val failureEventType = VidukiProvider.FAILURE_EVENT_TYPE
                        val wrapperHtml = """
                            <!DOCTYPE html>
                            <html>
                            <head>
                              <style>
                                * { margin:0; padding:0; background-color:#000; }
                                html, body { width:100%; height:100%; overflow:hidden; }
                                iframe { width:100%; height:100%; border:none; display:block; }
                              </style>
                            </head>
                            <body>
                              <iframe id="videoFrame" src="$initialUrl"
                                allowfullscreen
                                allow="autoplay; fullscreen; picture-in-picture"></iframe>
                              <script>
                                window.addEventListener("message", function(event) {
                                  if (event.origin !== "$vidukiOrigin") { return; }
                                  if (event.data && event.data.type === "$failureEventType") {
                                    if (window.AndroidBridge) {
                                      AndroidBridge.onAllServersFailed(JSON.stringify(event.data));
                                    }
                                  }
                                });
                              </script>
                            </body>
                            </html>
                        """.trimIndent()

                        loadDataWithBaseURL(
                            "https://viduki.net",
                            wrapperHtml,
                            "text/html",
                            "utf-8",
                            null
                        )
                        webViewRef = this
                    }
                } catch (e: Exception) {
                    Log.e("Viduki", "WebView init failed: ${e.message}")
                    WebView(ctx)
                }
            }
        )

        if (isLoading && !allServersFailed) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Accent)
            }
        }

        if (allServersFailed) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "All available servers are unavailable.",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = { loadServer(VidukiProvider.Api.API_1, manual = false) },
                        colors = ButtonDefaults.buttonColors(containerColor = Accent),
                        modifier = Modifier.tvFocusRing(RoundedCornerShape(50.dp))
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Retry")
                    }
                }
            }
        }

        // Top bar: back + title
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.tvAutoFocus(isTv).tvFocusRing(RoundedCornerShape(50))
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }

        // "Native HD available" pill
        AnimatedVisibility(
            visible = controlsVisible && onDirectPlayReady != null && extractedSource != null && extractedForApi == currentApi && !allServersFailed,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .tvFocusRing(RoundedCornerShape(50))
                    .clip(RoundedCornerShape(50))
                    .background(Accent)
                    .clickable {
                        extractedSource?.let { onDirectPlayReady?.invoke(it) }
                    }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Native HD — Play", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Bottom server selector (auto-hides together with the top bar)
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomStart)
        ) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(VidukiProvider.Api.fallbackOrder) { api ->
                    val active = api == currentApi
                    Box(
                        modifier = Modifier
                            .tvFocusRing(RoundedCornerShape(50))
                            .clip(RoundedCornerShape(50))
                            .background(if (active) Accent else Color.White.copy(alpha = 0.12f))
                            .clickable {
                                pokeControls()
                                if (!active) loadServer(api, manual = true)
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            api.displayName,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
