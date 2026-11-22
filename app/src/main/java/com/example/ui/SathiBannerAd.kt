package com.example.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.model.AppConfig
import com.example.model.SathiLanguage
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds

internal fun sanitizeBannerHttpsUrl(rawUrl: String): String? {
    val trimmed = rawUrl.trim()
    if (trimmed.isEmpty()) return null
    val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase() ?: return null
    val host = uri.host?.trim().orEmpty()
    if (scheme != "https" || host.isEmpty()) return null
    val isPrivate172 = Regex("^172\\.(1[6-9]|2\\d|3[01])\\..*").matches(host)
    if (host.equals("localhost", ignoreCase = true) ||
        host.startsWith("127.") ||
        host.startsWith("10.") ||
        host.startsWith("169.254.") ||
        host.startsWith("192.168.") ||
        isPrivate172 ||
        host == "0.0.0.0" ||
        host == "[::1]"
    ) {
        return null
    }
    return uri.toString()
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SathiAdBannerBar(
    config: AppConfig,
    language: SathiLanguage,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isInspection = LocalInspectionMode.current
    val isRobolectric = remember {
        "robolectric".equals(Build.FINGERPRINT, ignoreCase = true)
    }

    val safeWebBannerUrl = remember(config.bannerAdUrl) {
        sanitizeBannerHttpsUrl(config.bannerAdUrl)
    }

    val unitId = remember(config.bannerAdUnitId) {
        config.bannerAdUnitId.trim().ifBlank {
            try {
                context.getString(R.string.admob_banner_unit_id)
            } catch (_: Exception) {
                "ca-app-pub-3940256099942544/6300978111"
            }
        }
    }

    if (isInspection || isRobolectric) {
        BannerFallbackBar(
            language = language,
            modifier = modifier
        )
        return
    }

    // Primary WebView banner ad container when an HTTPS banner URL is configured
    if (safeWebBannerUrl != null) {
        var hasLoadError by remember(safeWebBannerUrl) { mutableStateOf(false) }

        if (hasLoadError) {
            BannerFallbackBar(
                language = language,
                modifier = modifier
            )
            return
        }

        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.42f),
            modifier = modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("bottom_banner_ad")
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            setBackgroundColor(AndroidColor.TRANSPARENT)
                            isVerticalScrollBarEnabled = false
                            isHorizontalScrollBarEnabled = false
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                loadsImagesAutomatically = true
                                allowFileAccess = false
                                allowContentAccess = false
                                @Suppress("DEPRECATION")
                                allowFileAccessFromFileURLs = false
                                @Suppress("DEPRECATION")
                                allowUniversalAccessFromFileURLs = false
                                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                setSupportMultipleWindows(false)
                                javaScriptCanOpenWindowsAutomatically = false
                                useWideViewPort = true
                                loadWithOverviewMode = true
                            }
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val targetUri = request?.url ?: return true
                                    val safeTarget = sanitizeBannerHttpsUrl(targetUri.toString())
                                        ?: return true
                                    // Open user-tapped ad links in external browser while allowing automatic HTTPS redirects inside banner
                                    if (request.hasGesture()) {
                                        runCatching {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(safeTarget)).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            ctx.startActivity(intent)
                                        }
                                        return true
                                    }
                                    return false
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    if (request?.isForMainFrame == true) {
                                        hasLoadError = true
                                    }
                                }

                                override fun onReceivedSslError(
                                    view: WebView?,
                                    handler: SslErrorHandler?,
                                    error: SslError?
                                ) {
                                    handler?.cancel()
                                    hasLoadError = true
                                }
                            }
                            tag = safeWebBannerUrl
                            loadUrl(safeWebBannerUrl)
                        }
                    },
                    update = { webView ->
                        if (webView.tag != safeWebBannerUrl) {
                            hasLoadError = false
                            webView.tag = safeWebBannerUrl
                            webView.loadUrl(safeWebBannerUrl)
                        }
                    },
                    onRelease = { webView ->
                        webView.stopLoading()
                        webView.destroy()
                    }
                )
            }
        }
        return
    }

    // Optional AdMob fallback if Admin clears bannerAdUrl
    LaunchedEffect(Unit) {
        try {
            MobileAds.initialize(context) {}
        } catch (_: Throwable) {
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("bottom_banner_ad")
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    AdView(ctx).apply {
                        setAdSize(AdSize.BANNER)
                        adUnitId = unitId
                        try {
                            loadAd(AdRequest.Builder().build())
                        } catch (_: Throwable) {
                        }
                    }
                },
                update = { adView ->
                    if (adView.adUnitId != unitId) {
                        adView.adUnitId = unitId
                        try {
                            adView.loadAd(AdRequest.Builder().build())
                        } catch (_: Throwable) {
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun BannerFallbackBar(
    language: SathiLanguage,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
            .testTag("bottom_banner_ad")
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "Ad",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (language == SathiLanguage.BN) "সাথী স্পন্সর্ড ব্যানার" else "Sathi Sponsored Banner",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
