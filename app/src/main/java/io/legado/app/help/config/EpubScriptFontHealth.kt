package io.legado.app.help.config

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 调试期之后仍需保留：EPUB WebView 报告各脚本字体 face 是否加载失败（如 OTS 拒绝）。
 * 只有可见 surface 上报；Language fonts 页据此画删除线，Done 时把失败项恢复为跟随预设。
 */
object EpubScriptFontHealth {
    private val _failedScopes = MutableStateFlow<Set<String>>(emptySet())
    val failedScopes: StateFlow<Set<String>> = _failedScopes.asStateFlow()

    fun report(scope: String, failed: Boolean) {
        val next = if (failed) _failedScopes.value + scope else _failedScopes.value - scope
        if (next != _failedScopes.value) _failedScopes.value = next
    }

    fun isFailed(scope: String): Boolean = scope in _failedScopes.value

    fun clear() {
        _failedScopes.value = emptySet()
    }
}
