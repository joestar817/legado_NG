package io.legado.app.ui.book.read.epub

import android.content.Context

/** Phase 3A PoC 开关与测试字体路径。不接入任何持久化模型/Config/Room/resolver。 */
object EpubScriptFontPoc {
    const val ENABLED = false
    const val LATIN_ASSET = "poc-fonts/latin.ttf"   // 仅 Latin 字形、特征明显的测试字体
    const val CJK_ASSET = "poc-fonts/cjk.ttf"       // CJK 子集化测试字体

    /** 读取测试字体字节并复用 EpubFontData.forWebView 修 vmtx（WebView OTS 校验必需）。 */
    fun bytes(context: Context, asset: String): ByteArray? = runCatching {
        context.assets.open(asset).use { it.readBytes() }
    }.getOrNull()?.let { EpubFontData.forWebView(it) }
}
