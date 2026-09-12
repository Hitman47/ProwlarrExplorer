package dev.mkdev.prowlarrexplorer.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri

/** Page à ouvrir dans le navigateur intégré ; `cookie` = session fournie par Prowlarr, injectée avant chargement. */
data class WebTarget(val url: String, val title: String, val cookie: String? = null)

/**
 * Navigateur intégré à cookies persistants : une connexion au tracker suffit, elle est conservée
 * d'une ouverture à l'autre. Les liens magnet / .torrent cliqués partent vers qBittorrent.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebScreen(target: WebTarget, state: UiState, vm: SearchViewModel, onClose: () -> Unit, onShowDownloads: () -> Unit) {
    val ctx = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var title by remember { mutableStateOf(target.title) }
    var progress by remember { mutableIntStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        val m = state.message ?: return@LaunchedEffect
        val r = snackbar.showSnackbar(m.text, actionLabel = if (m.goDownloads) "Voir" else null)
        if (r == SnackbarResult.ActionPerformed) { onClose(); onShowDownloads() }
        vm.consumeMessage()
    }

    BackHandler { if (canGoBack) webView?.goBack() else onClose() }

    DisposableEffect(Unit) {
        onDispose { CookieManager.getInstance().flush(); webView?.destroy() }
    }

    fun external(url: String) = runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Fermer") } },
                actions = {
                    IconButton(onClick = { webView?.reload() }) { Icon(Icons.Default.Refresh, contentDescription = "Recharger") }
                    IconButton(onClick = { external(webView?.url ?: target.url) }) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = "Ouvrir dans le navigateur")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (progress in 1..99) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { c ->
                    WebView(c).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        // Sans le marqueur « wv » : certains trackers / Cloudflare refusent les WebView.
                        settings.userAgentString = settings.userAgentString.replace("; wv", "")

                        val cookies = CookieManager.getInstance()
                        cookies.setAcceptCookie(true)
                        cookies.setAcceptThirdPartyCookies(this, true)
                        target.cookie?.let { injectCookies(target.url, it) }

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val u = request.url.toString()
                                if (u.startsWith("magnet:")) { vm.addFromWeb(u, null); return true }
                                if (!u.startsWith("http")) { external(u); return true }
                                return false
                            }
                            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) { canGoBack = view.canGoBack() }
                            override fun onPageFinished(view: WebView, url: String?) {
                                canGoBack = view.canGoBack()
                                CookieManager.getInstance().flush()
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView, newProgress: Int) { progress = newProgress }
                            override fun onReceivedTitle(view: WebView, t: String?) { t?.takeIf { it.isNotBlank() }?.let { title = it } }
                        }
                        // Fichier .torrent : URL + cookies de session transmis à qBittorrent, qui télécharge lui-même.
                        setDownloadListener { url, _, _, mime, _ ->
                            if (url.endsWith(".torrent") || mime == "application/x-bittorrent" || url.contains("download")) {
                                vm.addFromWeb(url, CookieManager.getInstance().getCookie(url))
                            } else external(url)
                        }
                        loadUrl(target.url)
                    }.also { webView = it }
                },
            )
        }
    }
}

/** « a=1; b=2 » → cookies posés sur le domaine de `url`. */
private fun injectCookies(url: String, cookie: String) {
    val host = runCatching { url.toUri().host }.getOrNull() ?: return
    val cm = CookieManager.getInstance()
    cookie.split(';').map { it.trim() }.filter { it.contains('=') }.forEach { pair ->
        cm.setCookie(url, "$pair; Domain=$host; Path=/")
    }
    cm.flush()
}
