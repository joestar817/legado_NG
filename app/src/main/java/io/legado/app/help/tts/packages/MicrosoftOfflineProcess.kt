package io.legado.app.help.tts.packages

import android.app.Application
import android.os.Build
import java.io.FileInputStream

internal object MicrosoftOfflineProcess {
    fun isCurrentProcess(): Boolean {
        val name = if (Build.VERSION.SDK_INT >= 28) Application.getProcessName() else runCatching {
            FileInputStream("/proc/self/cmdline").use { input ->
                val bytes = ByteArray(256)
                val size = input.read(bytes)
                if (size <= 0) null else {
                    val end = bytes.indexOf(0).takeIf { it in 0 until size } ?: size
                    String(bytes, 0, end, Charsets.UTF_8)
                }
            }
        }.getOrNull()
        return name?.endsWith(":tts_microsoft") == true
    }
}
