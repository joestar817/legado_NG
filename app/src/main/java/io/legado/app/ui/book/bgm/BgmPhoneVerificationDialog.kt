package io.legado.app.ui.book.bgm

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.ConsoleMessage
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import io.legado.app.help.bgm.qq.QqPhoneVerification
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.NgDialogTextActionButton
import io.legado.app.ui.design.components.compose.NgPaneDialog
import io.legado.app.ui.design.theme.NgTheme
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Dedicated verification content: no browser intents, downloads, file picker or native JS bridge. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun BgmPhoneVerificationDialog(
    verification: QqPhoneVerification,
    onDismiss: () -> Unit,
    onVerified: () -> Unit,
) {
    var message by remember(verification.id) { mutableStateOf("") }
    var progress by remember(verification.id) { mutableStateOf(0) }
    var currentView by remember(verification.id) { mutableStateOf<WebView?>(null) }
    val alive = remember(verification.id) { AtomicBoolean(true) }
    val currentPage = remember(verification.id) { AtomicReference<String?>(null) }
    val verified by rememberUpdatedState(onVerified)

    NgPaneDialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp).imePadding()) {
            val bodyHeight = (maxHeight - 172.dp).coerceIn(0.dp, 440.dp)
            NgDialog(title = "安全验证", actions = {
                NgDialogTextActionButton("取消", onDismiss, secondary = true)
                if (message.isNotBlank()) NgDialogTextActionButton("重试", {
                    message = ""
                    currentPage.set(null)
                    currentView?.loadUrl(verification.startUrl)
                })
            }) {
                LinearProgressIndicator(progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth().height(1.dp),
                    color = if (progress < 100) Color(NgTheme.colors.primary) else Color.Transparent,
                    trackColor = Color.Transparent)
                AndroidView(
                    modifier = Modifier.fillMaxWidth().height(bodyHeight),
                    factory = { context ->
                        WebView(context).apply {
                            currentView = this
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                cacheMode = WebSettings.LOAD_NO_CACHE
                                allowFileAccess = false
                                allowContentAccess = false
                                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                javaScriptCanOpenWindowsAutomatically = false
                                setSupportMultipleWindows(false)
                                setGeolocationEnabled(false)
                                useWideViewPort = true
                                loadWithOverviewMode = true
                            }
                            fun report(text: String) { post { if (alive.get()) message = text } }
                            fun finish(url: String, mainFrame: Boolean) {
                                post {
                                    if (alive.get() && verification.acceptCompletion(url, currentPage.get(), mainFrame)) {
                                        verified()
                                    }
                                }
                            }
                            setDownloadListener { _, _, _, _, _ ->
                                report("已拦截下载，请重试验证")
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean = true

                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    if (alive.get()) progress = newProgress
                                }
                            }
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    val url = request.url.toString()
                                    if (verification.isCallback(url)) {
                                        finish(url, request.isForMainFrame)
                                        return true
                                    }
                                    return navigate(view, url, request.isForMainFrame)
                                }

                                @Deprecated("Needed on Android 5 and 6")
                                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                                    // The request interceptor supplies isForMainFrame on API 21+.
                                    if (verification.isCallback(url)) return false
                                    if (verification.allowsResource(url, false)) return false
                                    return navigate(view, url, true)
                                }

                                private fun navigate(view: WebView, url: String, mainFrame: Boolean): Boolean {
                                    if (!mainFrame) return !verification.allowsResource(url, false)
                                    val destination = verification.pageUrl(url)
                                    if (destination == null) {
                                        report("已拦截外部跳转，请重试验证")
                                        return true
                                    }
                                    if (destination != url) {
                                        view.loadUrl(destination)
                                        return true
                                    }
                                    return false
                                }

                                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                                    if (verification.isCallback(url)) return
                                    val destination = verification.pageUrl(url)
                                    if (destination == null) {
                                        currentPage.set(null)
                                        view.stopLoading()
                                        report("已拦截外部跳转，请重试验证")
                                    } else if (destination != url) {
                                        view.stopLoading()
                                        view.loadUrl(destination)
                                    } else currentPage.set(url)
                                }

                                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                                    val url = request.url.toString()
                                    if (verification.isCallback(url)) {
                                        finish(url, request.isForMainFrame)
                                        return emptyResponse()
                                    }
                                    return if (verification.allowsResource(url, request.isForMainFrame)) null else emptyResponse()
                                }

                                override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                                    handler.cancel()
                                    report("验证页面连接失败，请重试")
                                }

                                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                                    if (request.isForMainFrame && !verification.isCallback(request.url.toString())) {
                                        report("验证页面加载失败，请重试")
                                    }
                                }

                                @Deprecated("Needed on Android 5")
                                override fun onReceivedError(view: WebView, errorCode: Int, description: String?, failingUrl: String?) {
                                    if (failingUrl != null && verification.pageUrl(failingUrl) != null) {
                                        report("验证页面加载失败，请重试")
                                    }
                                }
                            }
                            loadUrl(verification.startUrl)
                        }
                    },
                    onRelease = { view ->
                        alive.set(false)
                        currentView = null
                        view.stopLoading()
                        view.webChromeClient = null
                        view.webViewClient = WebViewClient()
                        view.clearHistory()
                        view.removeAllViews()
                        view.destroy()
                    },
                    update = {},
                )
                if (message.isNotBlank()) {
                    Text(message, Modifier.fillMaxWidth().padding(top = 8.dp),
                        color = Color(NgTheme.colors.onSurfaceVariant), fontSize = 13.sp, lineHeight = 18.sp)
                }
            }
        }
    }
}

private fun emptyResponse() = WebResourceResponse("text/plain", "UTF-8", 204, "No Content",
    emptyMap(), ByteArrayInputStream(ByteArray(0)))
