package io.legado.app.help.tts.packages

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.Process
import androidx.annotation.RequiresApi
import dalvik.system.DexClassLoader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files

/** A separate process contains native crashes and permits replacing a loaded SDK version. */
class MicrosoftOfflineService : Service() {
    private var loadedDigest: String? = null
    private var loader: DexClassLoader? = null
    private var verifiedGraph: Map<String, String>? = null
    private var licenseDigest: String? = null
    private val voices = mutableMapOf<String, EnginePlugin>()

    private val binder = object : IMicrosoftOfflineService.Stub() {
        @Synchronized
        override fun synthesize(revision: String, voiceId: String, text: String, output: ParcelFileDescriptor): String {
            if (Build.VERSION.SDK_INT < 26) {
                runCatching { output.close() }
                return "微软离线语音需要 Android 8.0 或更高版本"
            }
            if (text.isBlank() || text.length > 100_000 || voiceId.isBlank()) {
                runCatching { output.close() }
                return "离线朗读内容无效或过长"
            }
            val trace = MicrosoftOfflineTrace()
            trace.begin(MicrosoftOfflineTrace.Stage.GRAPH)
            return try {
                ParcelFileDescriptor.AutoCloseOutputStream(output).use { wav ->
                    synthesizeChecked(revision, voiceId, text, wav, trace)
                    trace.begin(MicrosoftOfflineTrace.Stage.OUTPUT)
                }
                trace.success()
                ""
            } catch (error: RestartNeeded) {
                trace.failure(error)
                RESTART_REQUIRED
            } catch (error: Exception) {
                trace.failure(error)
                "离线语音合成失败，请检查语音包、处理器架构和许可"
            } catch (error: LinkageError) {
                trace.failure(error)
                "离线语音合成失败，请检查语音包、处理器架构和许可"
            }
        }

        override fun cancel() {
            if (MicrosoftOfflineProcess.isCurrentProcess()) Process.killProcess(Process.myPid())
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    @RequiresApi(26)
    private fun synthesizeChecked(
        revision: String,
        voiceId: String,
        text: String,
        wav: java.io.OutputStream,
        trace: MicrosoftOfflineTrace,
    ) {
        val root = File(noBackupFilesDir, "tts-offline-microsoft")
        require(root.isDirectory) { "离线语音包未安装" }
        // Published objects are immutable and retained for pinned requests. A preview/installation
        // must not hold up synthesis, and a reader must never clean the installer's staging files.
        MicrosoftTtsPackageStore.readOnly(root).let { store ->
            val device = MicrosoftTtsBundleManager.processDevice()
            val graph = decodeMicrosoftOfflineGraph(revision)
            val engine = store.readInstalled(graph.getValue(MicrosoftTtsPackageStore.ENGINE))
            if (loadedDigest != null && loadedDigest != engine.digest) throw RestartNeeded()
            if (verifiedGraph != graph) {
                store.checkGraph(graph, device)
                verifiedGraph = graph.toMap()
            }
            val matches = graph.entries.map { it.key to store.readInstalled(it.value) }
                .filter { (_, item) -> item.manifest.contentRole == "voices" &&
                    item.manifest.voices.any { it.voiceId == voiceId } }
            require(matches.size == 1) { "发音人不存在或重复" }
            val (voicePackage, _) = matches.single()
            val license = store.credential(graph, voicePackage, "model_license")?.let(::File)
                ?: MicrosoftTtsBundleManager.licenseFile(this)
            require(license.isFile && license.length() in 1..65_536L) { "未设置离线语音许可" }
            val currentLicenseDigest = MicrosoftTtsPackageStore.sha256(license)
            if (licenseDigest != currentLicenseDigest) {
                closeVoices()
                licenseDigest = currentLicenseDigest
            }
            val voice = store.voice(graph, voicePackage, voiceId)
            trace.success()
            val key = "$voicePackage:$voiceId:${graph.getValue(voicePackage)}"
            val plugin = voices[key] ?: run {
                trace.begin(MicrosoftOfflineTrace.Stage.ENGINE_LOAD)
                val candidate = newPlugin(engine)
                trace.success()
                try {
                    trace.begin(MicrosoftOfflineTrace.Stage.VOICE_PREPARE)
                    val resources = store.resources(graph, voicePackage, voiceId).toMutableMap()
                    resources["license_file"] = license.canonicalPath
                    candidate.prepare(resources)
                    trace.success()
                    voices[key] = candidate
                } catch (error: Throwable) {
                    runCatching { candidate.close() }
                    throw error
                }
                candidate
            }
            trace.begin(MicrosoftOfflineTrace.Stage.SYNTHESIS)
            plugin.synthesize(text, voice.parameters.getValue("speaker_id"), 1f, wav)
            trace.success()
        }
    }

    @RequiresApi(26)
    private fun newPlugin(engine: MicrosoftTtsPackageStore.Installed): EnginePlugin {
        val activeLoader = loader ?: run {
            val source = MicrosoftTtsPackageStore.safe(engine.directory, engine.manifest.implementation.dexPath)
            val directory = MicrosoftTtsPackageStore.safe(codeCacheDir, "tts-microsoft/${engine.digest}").apply {
                check(isDirectory || mkdirs()) { "无法准备离线引擎" }
            }
            val code = File(directory, "adapter.jar")
            if (Files.isSymbolicLink(code.toPath()) || code.exists() &&
                (code.canWrite() || MicrosoftTtsPackageStore.sha256(source) != MicrosoftTtsPackageStore.sha256(code))
            ) {
                check(code.delete()) { "无法清理失效的离线引擎缓存" }
            }
            if (!code.isFile) {
                val partial = File(directory, "adapter.jar.partial")
                if (partial.exists() || Files.isSymbolicLink(partial.toPath())) {
                    check(partial.delete()) { "无法清理临时引擎" }
                }
                FileInputStream(source).use { input ->
                    FileOutputStream(partial).use { output ->
                        check(partial.setReadOnly()) { "无法保护离线引擎" }
                        input.copyTo(output)
                        output.fd.sync()
                    }
                }
                check(MicrosoftTtsPackageStore.sha256(source) == MicrosoftTtsPackageStore.sha256(partial) &&
                    partial.renameTo(code)) { "离线引擎代码不完整" }
            }
            check(!code.canWrite() && MicrosoftTtsPackageStore.sha256(source) ==
                MicrosoftTtsPackageStore.sha256(code)) { "离线引擎代码已损坏" }
            val native = MicrosoftTtsPackageStore.safe(engine.directory, engine.manifest.implementation.nativeDir)
            DexClassLoader(code.path, directory.path, native.path, EnginePlugin::class.java.classLoader).also {
                loader = it
                loadedDigest = engine.digest
            }
        }
        val implementation = activeLoader.loadClass(engine.manifest.implementation.entryClass)
        check(implementation.classLoader === activeLoader) { "引擎未从插件加载" }
        return implementation.getDeclaredConstructor().newInstance() as EnginePlugin
    }

    private fun closeVoices() {
        voices.values.forEach { runCatching { it.close() } }
        voices.clear()
    }

    override fun onDestroy() {
        closeVoices()
        super.onDestroy()
        if (MicrosoftOfflineProcess.isCurrentProcess()) Process.killProcess(Process.myPid())
    }

    private class RestartNeeded : Exception()

    internal companion object {
        const val RESTART_REQUIRED = "__offline_worker_restart__"
    }
}
