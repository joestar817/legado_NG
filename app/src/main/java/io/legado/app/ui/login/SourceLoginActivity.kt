package io.legado.app.ui.login

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.viewModels
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.ComposeActivityBinding
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.AppConst
import io.legado.app.data.entities.BaseSource
import io.legado.app.help.http.BookSourceCookieStore
import io.legado.app.help.http.CookieStore
import io.legado.app.help.webView.PooledWebView
import io.legado.app.help.webView.WebViewPool
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.openUrl
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.launch

class SourceLoginActivity :
    VMBaseActivity<ComposeActivityBinding, SourceLoginViewModel>(imageBg = false) {

    override val binding by viewBinding(ComposeActivityBinding::inflate)
    override val viewModel by viewModels<SourceLoginViewModel>()

    private var pooledWebView: PooledWebView? = null
    private var currentWebView by mutableStateOf<WebView?>(null)
    private var title by mutableStateOf("")
    private var webProgress by mutableIntStateOf(100)
    private var checking = false
    private val snackbarHostState = SnackbarHostState()

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.composeView.setContent {
            NgAppTheme {
                currentWebView?.let { webView ->
                    SourceWebViewLoginScreen(
                        title = title,
                        webView = webView,
                        progress = webProgress,
                        snackbarHostState = snackbarHostState,
                        onBack = ::finish,
                        onConfirm = ::confirmLogin,
                    )
                }
            }
        }
        viewModel.initData(
            intent = intent,
            success = ::initView,
            error = ::finish,
        )
    }

    private fun initView(source: BaseSource) {
        if (source.loginUi.isNullOrEmpty()) {
            title = getString(R.string.login_source, source.getTag())
            initWebView(source)
        } else {
            showDialogFragment<SourceLoginDialog>()
        }
    }

    private fun confirmLogin() {
        if (checking) return
        checking = true
        lifecycleScope.launch {
            snackbarHostState.showSnackbar(
                message = getString(R.string.check_host_cookie),
                duration = SnackbarDuration.Short,
            )
        }
        viewModel.source?.let(::loadUrl)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView(source: BaseSource) {
        val webView = WebViewPool.acquire(this).let {
            pooledWebView = it
            it.realWebView
        }
        webView.onResume()
        webView.settings.apply {
            useWideViewPort = true
            loadWithOverviewMode = true
            viewModel.headerMap[AppConst.UA_NAME]?.let {
                userAgentString = it
            }
        }
        val cookieManager = CookieManager.getInstance()
        val bookSourceCookieStore = BookSourceCookieStore.forBookSource(source)
        fun captureCookie(url: String?) {
            url ?: return
            if (bookSourceCookieStore != null) {
                bookSourceCookieStore.captureFromWebView(
                    pageUrl = url,
                    storageUrl = source.getKey(),
                )
            } else {
                CookieStore.setCookie(source.getKey(), cookieManager.getCookie(url))
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                captureCookie(url)
                super.onPageStarted(view, url, favicon)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                captureCookie(url)
                if (checking) finish()
                super.onPageFinished(view, url)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean = shouldOverrideUrlLoading(request.url)

            @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION", "KotlinRedundantDiagnosticSuppress")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                return shouldOverrideUrlLoading(url.toUri())
            }

            private fun shouldOverrideUrlLoading(url: Uri): Boolean {
                if (url.scheme == "http" || url.scheme == "https") return false
                lifecycleScope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = getString(R.string.jump_to_another_app),
                        actionLabel = getString(R.string.confirm),
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        openUrl(url)
                    }
                }
                return true
            }

            @SuppressLint("WebViewClientOnReceivedSslError")
            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?,
            ) {
                handler?.proceed()
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                webProgress = newProgress
            }
        }
        currentWebView = webView
        loadUrl(source)
    }

    private fun loadUrl(source: BaseSource) {
        val loginUrl = source.loginUrl ?: return
        val absoluteUrl = NetworkUtils.getAbsoluteURL(source.getKey(), loginUrl)
        currentWebView?.let { webView ->
            BookSourceCookieStore.forBookSource(source)?.applyToWebView(
                cookieUrl = source.getKey(),
                targetUrl = absoluteUrl,
            )
            webView.loadUrl(absoluteUrl, viewModel.headerMap)
        }
    }

    override fun onDestroy() {
        currentWebView?.let { webView ->
            (webView.parent as? ViewGroup)?.removeView(webView)
        }
        pooledWebView?.let(WebViewPool::release)
        pooledWebView = null
        currentWebView = null
        super.onDestroy()
    }
}
