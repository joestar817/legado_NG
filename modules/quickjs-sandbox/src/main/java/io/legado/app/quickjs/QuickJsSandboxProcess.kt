package io.legado.app.quickjs

import android.app.Application
import android.os.Build
import java.io.FileInputStream

object QuickJsSandboxProcess {

    @JvmStatic
    fun isCurrentProcess(): Boolean =
        isSandboxProcessName(currentProcessName())

    internal fun isSandboxProcessName(processName: String?): Boolean {
        if (processName == null) return false
        val marker = QuickJsSandboxProtocol.PROCESS_SUFFIX
        val markerIndex = processName.indexOf(marker)
        if (markerIndex < 0) return false
        val markerEnd = markerIndex + marker.length
        return markerEnd == processName.length || processName.getOrNull(markerEnd) == ':'
    }

    private fun currentProcessName(): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Application.getProcessName()
        }
        return runCatching {
            FileInputStream("/proc/self/cmdline").use { input ->
                val bytes = ByteArray(256)
                val length = input.read(bytes)
                if (length <= 0) return@use null
                val end = bytes.indexOf(0).let { if (it in 0 until length) it else length }
                String(bytes, 0, end, Charsets.UTF_8)
            }
        }.getOrNull()
    }
}
