package io.legado.app.help.tts.packages

import android.os.Build

/** This package format currently accepts only the supported Android ARM64 runtime. */
internal object MicrosoftOfflineSupport {
    const val UNSUPPORTED_ARCH = "微软离线语音需要 ARM64 运行环境"
    const val UNSUPPORTED_ANDROID = "微软离线语音需要 Android 8.0 或更高版本"

    fun unavailableReason(sdk: Int, processArch: String?): String? = when {
        sdk < 26 -> UNSUPPORTED_ANDROID
        processArch?.lowercase() !in setOf("aarch64", "arm64") -> UNSUPPORTED_ARCH
        else -> null
    }

    fun currentUnavailableReason(): String? =
        unavailableReason(Build.VERSION.SDK_INT, System.getProperty("os.arch"))

    fun engineUnavailableReason(sdk: Int, processArch: String?, revision: String?): String? =
        unavailableReason(sdk, processArch)
            ?: "离线语音包尚未准备完成".takeIf { revision.isNullOrBlank() }

    fun currentEngineUnavailableReason(revision: String?): String? =
        engineUnavailableReason(Build.VERSION.SDK_INT, System.getProperty("os.arch"), revision)

    fun supportsPackageAbi(abi: String?): Boolean = abi == "arm64-v8a"

    fun runtimeCompatible(
        packageAbi: String?,
        processAbi: String?,
        sdk: Int,
        minSdk: Int,
        pageSizeKb: Int,
        supportedPageSizes: List<Int>,
    ): Boolean = supportsPackageAbi(packageAbi) && packageAbi == processAbi &&
        sdk >= 26 && sdk >= minSdk && pageSizeKb in supportedPageSizes
}
