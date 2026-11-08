package io.legado.app.help.http

import java.net.URI

/** 更新与资源中心共用的公开 GitHub 镜像顺序；各调用方仍限制自身的仓库/路径。 */
internal object GitHubRelay {
    const val METADATA_ATTEMPT_TIMEOUT_MS = 4_500L
    private const val PREFIX = "https://gh-proxy.com/"
    private val allowedHosts = setOf("github.com", "api.github.com", "raw.githubusercontent.com")

    fun proxyFirst(url: String): List<String> {
        val uri = runCatching { URI(url) }.getOrNull()
        require(uri != null && uri.scheme == "https" && uri.host in allowedHosts &&
            uri.port == -1 && uri.userInfo == null && uri.rawFragment == null) {
            "不支持的 GitHub 地址"
        }
        return listOf(PREFIX + url, url)
    }
}

