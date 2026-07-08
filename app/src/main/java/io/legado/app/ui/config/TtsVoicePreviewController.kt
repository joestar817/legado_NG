package io.legado.app.ui.config

import android.content.Context
import android.net.Uri
import android.speech.tts.TextToSpeech
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import io.legado.app.R
import io.legado.app.help.tts.TtsEngineSetting
import io.legado.app.help.tts.TtsEngineType
import io.legado.app.help.tts.TtsScriptEngineClient
import io.legado.app.help.tts.TtsVoice
import io.legado.app.help.tts.previewText
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class TtsVoicePreviewController(
    private val context: Context,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val beforePreview: () -> Unit = {}
) {

    private var previewJob: Job? = null
    private var previewPlayer: ExoPlayer? = null
    private var systemPreviewTts: TextToSpeech? = null
    private var systemPreviewToken = 0

    fun preview(engine: TtsEngineSetting, voice: TtsVoice, systemDefault: Boolean) {
        beforePreview()
        if (engine.type == TtsEngineType.SYSTEM) {
            previewSystemVoice(engine, voice)
        } else {
            previewScriptVoice(engine, voice, systemDefault)
        }
    }

    private fun previewScriptVoice(
        engine: TtsEngineSetting,
        voice: TtsVoice,
        systemDefault: Boolean
    ) {
        val scriptEngine = engine.takeIf { it.enabled && it.type == TtsEngineType.SCRIPT }
            ?: run {
                context.toastOnUi("当前发音人不支持试听")
                return
            }
        context.toastOnUi("正在合成试听...")
        stopSystemPreview()
        previewJob?.cancel()
        previewJob = lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val response = TtsScriptEngineClient.getSynthesisResponse(
                        engine = scriptEngine,
                        text = TtsScriptEngineClient.sampleText(scriptEngine, voice),
                        voiceId = voice.id.takeUnless { systemDefault },
                        styleId = null
                    )
                    File(
                        context.cacheDir,
                        "voice_preview_${scriptEngine.id}_${System.currentTimeMillis()}.audio"
                    ).apply {
                        response.use {
                            outputStream().use { out ->
                                it.body.byteStream().use { input ->
                                    input.copyTo(out)
                                }
                            }
                        }
                    }
                }
            }
            result.onSuccess { file ->
                previewPlayer?.release()
                previewPlayer = ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                    prepare()
                    play()
                }
            }.onFailure {
                context.toastOnUi("试听失败：${it.localizedMessage ?: it.javaClass.simpleName}")
            }
        }
    }

    private fun previewSystemVoice(engine: TtsEngineSetting, voice: TtsVoice) {
        previewJob?.cancel()
        previewPlayer?.release()
        previewPlayer = null
        stopSystemPreview()
        val previewToken = ++systemPreviewToken
        val appContext = context.applicationContext
        val previewText = voice.previewText()
        val ttsHolder = arrayOfNulls<TextToSpeech>(1)
        val listener = TextToSpeech.OnInitListener { status ->
            val tts = ttsHolder[0] ?: return@OnInitListener
            if (previewToken != systemPreviewToken) {
                tts.shutdown()
                return@OnInitListener
            }
            if (status == TextToSpeech.SUCCESS) {
                systemPreviewTts = tts
                tts.setSpeechRate((engine.effectiveSpeed() / 50f).coerceIn(0.1f, 5f))
                tts.setPitch((engine.effectivePitch() / 50f).coerceIn(0.1f, 2f))
                val result = tts.speak(
                    previewText,
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "voice_preview_${System.currentTimeMillis()}"
                )
                if (result == TextToSpeech.ERROR) {
                    context.toastOnUi("试听失败")
                }
            } else {
                tts.shutdown()
                context.toastOnUi(R.string.tts_init_failed)
            }
        }
        ttsHolder[0] = if (engine.enginePackage.isNullOrBlank()) {
            TextToSpeech(appContext, listener)
        } else {
            TextToSpeech(appContext, listener, engine.enginePackage)
        }
        systemPreviewTts = ttsHolder[0]
    }

    fun release() {
        previewJob?.cancel()
        previewJob = null
        previewPlayer?.release()
        previewPlayer = null
        stopSystemPreview()
    }

    private fun stopSystemPreview() {
        systemPreviewToken++
        systemPreviewTts?.runCatching {
            stop()
            shutdown()
        }
        systemPreviewTts = null
    }
}
