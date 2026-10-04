package io.legado.app.help.config

import android.util.Log

/**
 * 调试期脚本字体追踪日志。
 * 会话结束后删除本文件，并删除所有 `ScriptFontDebug.d(` 调用点（grep SCRIPT_FONT_DBG）。
 */
object ScriptFontDebug {
    const val TAG = "ScriptFontDbg"

    fun d(message: String) {
        Log.d(TAG, message)
    }

    fun short(value: String?): String = value?.substringAfterLast('/')?.take(48) ?: "null"
}
