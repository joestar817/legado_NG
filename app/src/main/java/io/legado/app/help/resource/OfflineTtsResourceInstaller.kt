package io.legado.app.help.resource

import android.net.Uri
import android.os.Build
import io.legado.app.help.tts.TtsEngineStore
import io.legado.app.help.tts.packages.MicrosoftOfflineSupport
import io.legado.app.help.tts.packages.MicrosoftTtsBundleManager
import io.legado.app.help.tts.packages.MicrosoftTtsPackageStore
import java.io.File

/** The resource center and local import commit through the same complete-bundle installer. */
internal object OfflineTtsResourceInstaller {
    fun fingerprint(): String? = if (Build.VERSION.SDK_INT < 26) null else
        MicrosoftTtsBundleManager.installedFingerprint()

    fun refreshEngine() {
        // A committed receipt is valid even if the app later runs under another ABI.
        if (MicrosoftOfflineSupport.currentUnavailableReason() != null) return
        TtsEngineStore.registerMicrosoftOffline(MicrosoftTtsBundleManager.catalog())
    }

    suspend fun prepare(
        entry: ResourceEntry,
        payload: File,
        old: ResourceInstallation?,
        mode: ResourceInstallMode,
        confirmed: TtsResourceConflict?,
    ): PreparedResource {
        require(entry.isOfflineTts && entry.item.payload.engineId == TtsEngineStore.MICROSOFT_OFFLINE_ID) {
            "离线语音资源身份不一致"
        }
        require(mode != ResourceInstallMode.COPY) { "离线语音仅支持覆盖安装" }
        check(Build.VERSION.SDK_INT >= 26) { "离线语音需要 Android 8.0 或更高版本" }
        MicrosoftOfflineSupport.currentUnavailableReason()?.let { error(it) }
        require(payload.length() == entry.item.payload.size && ResourceFiles.hash(payload) == entry.item.payload.sha256) {
            "离线语音包内容已变化，请重试"
        }
        val candidate = MicrosoftTtsBundleManager.inspect(Uri.fromFile(payload))
        try {
            require(candidate.packageId == "${MicrosoftTtsPackageStore.ENGINE_ID}.bundle") { "离线语音包身份不一致" }
            require(candidate.processCompatible) { "离线语音包不适配当前运行环境" }
            require(candidate.summary.hasBundledLicense || MicrosoftTtsBundleManager.hasLicense()) { "离线语音包缺少运行所需文件" }
            val current = candidate.previousFingerprint
            val incoming = candidate.fingerprint
            val replaceLocal = current != null && current != incoming &&
                (old == null || old.baseline != current || old.target != TtsEngineStore.MICROSOFT_OFFLINE_ID)
            if (replaceLocal && (mode != ResourceInstallMode.REPLACE ||
                    confirmed?.engineId != TtsEngineStore.MICROSOFT_OFFLINE_ID ||
                    confirmed.localScriptHash != current || confirmed.incomingScriptHash != incoming)) {
                throw ResourceConflict("将覆盖本地离线语音包，仍有效的声音绑定和个人参数会保留。",
                    ttsConflict = TtsResourceConflict(TtsEngineStore.MICROSOFT_OFFLINE_ID, current, incoming))
            }
            return PreparedResource(TtsEngineStore.MICROSOFT_OFFLINE_ID, incoming,
                commit = { candidate.commit() },
                discard = candidate::close,
                afterCommit = { refreshEngine(); "离线语音已安装" },
            )
        } catch (error: Throwable) {
            candidate.close()
            throw error
        }
    }
}
