package io.legado.app.ui.config

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.help.tts.TtsEngineSetting
import io.legado.app.help.tts.TtsEngineType
import io.legado.app.help.tts.TtsPlayerFactory
import io.legado.app.help.tts.TtsScriptEngineClient
import io.legado.app.help.tts.TtsSpeedPolicy
import io.legado.app.help.tts.TtsVoice
import io.legado.app.help.tts.packages.MicrosoftOfflineClient
import io.legado.app.help.tts.previewText
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class TtsVoicePreviewState {
    IDLE,
    LOADING,
    PLAYING
}

data class TtsVoicePreviewStatus(
    val key: String?,
    val state: TtsVoicePreviewState
)

internal class TtsVoicePreviewDebouncer(
    private val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS
) {
    private var lastAcceptedAt = Long.MIN_VALUE
    private var lastAcceptedKey: String? = null

    fun tryAcquire(key: String, nowMillis: Long): Boolean {
        if (key == lastAcceptedKey && lastAcceptedAt != Long.MIN_VALUE &&
            nowMillis - lastAcceptedAt < intervalMillis
        ) {
            return false
        }
        lastAcceptedKey = key
        lastAcceptedAt = nowMillis
        return true
    }

    companion object {
        const val DEFAULT_INTERVAL_MILLIS = 600L
    }
}

internal object TtsVoicePreviewCompletionGuard {
    private const val POSITION_TOLERANCE_MILLIS = 50L

    fun isAtTimelineEnd(positionMillis: Long, durationMillis: Long): Boolean {
        return durationMillis != C.TIME_UNSET &&
            durationMillis > 0L &&
            positionMillis >= (durationMillis - POSITION_TOLERANCE_MILLIS).coerceAtLeast(0L)
    }
}

class TtsVoicePreviewController(
    private val context: Context,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val beforePreview: () -> Unit = {},
    private val onStatusChanged: (TtsVoicePreviewStatus) -> Unit = {}
) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val debouncer = TtsVoicePreviewDebouncer()
    private var previewJob: Job? = null
    private var previewPlayer: ExoPlayer? = null
    private var previewFile: File? = null
    private var systemPreviewTts: TextToSpeech? = null
    private var systemPreviewToken = 0
    private var requestToken = 0
    private var activeKey: String? = null
    private var previewState = TtsVoicePreviewState.IDLE
    private var previewCompletionCheck: Runnable? = null

    fun preview(
        engine: TtsEngineSetting,
        voice: TtsVoice,
        systemDefault: Boolean,
        styleId: String? = null
    ): Boolean {
        val key = keyOf(engine, voice, systemDefault)
        if (!debouncer.tryAcquire(key, SystemClock.elapsedRealtime())) return false
        if (activeKey == key && previewState != TtsVoicePreviewState.IDLE) {
            stopActivePreview()
            return true
        }
        beforePreview()
        stopActivePreview(notify = false)
        activeKey = key
        updateState(TtsVoicePreviewState.LOADING)
        if (engine.type == TtsEngineType.SYSTEM) {
            previewSystemVoice(engine, voice, key)
        } else {
            previewScriptVoice(engine, voice, systemDefault, styleId, key)
        }
        return true
    }

    fun refreshPlaybackParams(engine: TtsEngineSetting, voice: TtsVoice) {
        if (activeKey != keyOf(engine, voice, systemDefault = false)) return
        previewPlayer?.let { player ->
            TtsPlayerFactory.applyPlaybackAdjustments(
                player = player,
                baseRate = TtsSpeedPolicy.playbackRate(AppConfig.speechRatePlay),
                voiceParams = engine.voicePlaybackParams(voice.id),
            )
        }
    }

    private fun previewScriptVoice(
        engine: TtsEngineSetting,
        voice: TtsVoice,
        systemDefault: Boolean,
        styleId: String?,
        key: String
    ) {
        val scriptEngine = engine.takeIf { it.usesGeneratedAudio }
            ?: run {
                finishPreview(key)
                context.toastOnUi("当前发音人不支持试听")
                return
            }
        if (!scriptEngine.enabled) {
            finishPreview(key)
            context.toastOnUi(context.getString(R.string.tts_preview_engine_disabled))
            return
        }
        val token = ++requestToken
        previewJob = lifecycleScope.launch {
            var pendingFile: File? = null
            try {
                val file = withContext(Dispatchers.IO) {
                    val target = File(
                        context.cacheDir,
                        "voice_preview_${scriptEngine.id}_${System.currentTimeMillis()}.audio"
                    )
                    pendingFile = target
                    try {
                        if (scriptEngine.type == TtsEngineType.OFFLINE) {
                            check(Build.VERSION.SDK_INT >= 26) { "微软离线语音需要 Android 8.0 或更高版本" }
                            MicrosoftOfflineClient.synthesize(
                                context, scriptEngine.offlineRevision.orEmpty(), voice.id,
                                TtsScriptEngineClient.sampleText(scriptEngine, voice)
                            ).use { input -> target.outputStream().use { input.copyTo(it) } }
                        } else {
                            val response = TtsScriptEngineClient.getSynthesisResponse(
                                engine = scriptEngine,
                                text = TtsScriptEngineClient.sampleText(scriptEngine, voice),
                                voiceId = voice.id.takeUnless { systemDefault },
                                styleId = styleId
                            )
                            response.use {
                                target.outputStream().use { out ->
                                    it.body.byteStream().use { input -> input.copyTo(out) }
                                }
                            }
                        }
                        target
                    } catch (error: Throwable) {
                        target.delete()
                        throw error
                    }
                }
                if (token != requestToken || activeKey != key) {
                    return@launch
                }
                previewFile = file
                pendingFile = null
                startPlayer(
                    file = file,
                    key = key,
                    token = token,
                    engine = scriptEngine,
                    voiceId = voice.id,
                )
            } catch (_: CancellationException) {
                // 新的试听或页面销毁会主动取消旧请求。
            } catch (error: Throwable) {
                if (token == requestToken && activeKey == key) {
                    finishPreview(key)
                    context.toastOnUi(
                        "试听失败：${error.localizedMessage ?: error.javaClass.simpleName}"
                    )
                }
            } finally {
                pendingFile?.delete()
            }
        }
    }

    private fun startPlayer(
        file: File,
        key: String,
        token: Int,
        engine: TtsEngineSetting,
        voiceId: String,
    ) {
        previewPlayer?.release()
        previewPlayer = TtsPlayerFactory.create(context).apply {
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying && token == requestToken && activeKey == key) {
                        updateState(TtsVoicePreviewState.PLAYING)
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        finishPreview(key)
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (token == requestToken && activeKey == key) {
                        finishPreview(key)
                        context.toastOnUi("试听失败：${error.localizedMessage}")
                    }
                }
            })
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            TtsPlayerFactory.applyPlaybackAdjustments(
                player = this,
                baseRate = TtsSpeedPolicy.playbackRate(AppConfig.speechRatePlay),
                voiceParams = engine.voicePlaybackParams(voiceId),
            )
            prepare()
            play()
        }
        schedulePreviewCompletionCheck(checkNotNull(previewPlayer), key, token)
    }

    private fun schedulePreviewCompletionCheck(player: ExoPlayer, key: String, token: Int) {
        cancelPreviewCompletionCheck()
        val check = object : Runnable {
            private var timelineEndReachedAt = Long.MIN_VALUE

            override fun run() {
                if (
                    token != requestToken ||
                    activeKey != key ||
                    previewPlayer !== player
                ) {
                    return
                }
                if (player.playbackState == Player.STATE_ENDED) {
                    finishPreview(key)
                    return
                }
                val atTimelineEnd = TtsVoicePreviewCompletionGuard.isAtTimelineEnd(
                    positionMillis = player.currentPosition,
                    durationMillis = player.duration
                )
                if (atTimelineEnd) {
                    if (timelineEndReachedAt == Long.MIN_VALUE) {
                        timelineEndReachedAt = SystemClock.elapsedRealtime()
                    } else if (
                        SystemClock.elapsedRealtime() - timelineEndReachedAt >=
                        TIMELINE_END_GRACE_MILLIS
                    ) {
                        finishPreview(key)
                        return
                    }
                } else {
                    timelineEndReachedAt = Long.MIN_VALUE
                }
                mainHandler.postDelayed(this, COMPLETION_CHECK_INTERVAL_MILLIS)
            }
        }
        previewCompletionCheck = check
        mainHandler.post(check)
    }

    private fun cancelPreviewCompletionCheck() {
        previewCompletionCheck?.let(mainHandler::removeCallbacks)
        previewCompletionCheck = null
    }

    private fun previewSystemVoice(engine: TtsEngineSetting, voice: TtsVoice, key: String) {
        val previewToken = ++systemPreviewToken
        val appContext = context.applicationContext
        val previewText = voice.previewText()
        val ttsHolder = arrayOfNulls<TextToSpeech>(1)
        val listener = TextToSpeech.OnInitListener { status ->
            val tts = ttsHolder[0] ?: return@OnInitListener
            if (previewToken != systemPreviewToken || activeKey != key) {
                tts.shutdown()
                return@OnInitListener
            }
            if (status != TextToSpeech.SUCCESS) {
                tts.shutdown()
                finishPreview(key)
                context.toastOnUi(R.string.tts_init_failed)
                return@OnInitListener
            }
            systemPreviewTts = tts
            tts.setSpeechRate((engine.effectiveSpeed() / 50f).coerceIn(0.1f, 5f))
            tts.setPitch((engine.effectivePitch() / 50f).coerceIn(0.1f, 2f))
            val utteranceId = "voice_preview_${System.currentTimeMillis()}"
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    postIfActive(key, previewToken) {
                        updateState(TtsVoicePreviewState.PLAYING)
                    }
                }

                override fun onDone(utteranceId: String?) {
                    postIfActive(key, previewToken) { finishPreview(key) }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    postIfActive(key, previewToken) {
                        finishPreview(key)
                        context.toastOnUi("试听失败")
                    }
                }
            })
            if (tts.speak(previewText, TextToSpeech.QUEUE_FLUSH, null, utteranceId) ==
                TextToSpeech.ERROR
            ) {
                finishPreview(key)
                context.toastOnUi("试听失败")
            }
        }
        ttsHolder[0] = if (engine.enginePackage.isNullOrBlank()) {
            TextToSpeech(appContext, listener)
        } else {
            TextToSpeech(appContext, listener, engine.enginePackage)
        }
        systemPreviewTts = ttsHolder[0]
    }

    private fun postIfActive(key: String, previewToken: Int, action: () -> Unit) {
        mainHandler.post {
            if (previewToken == systemPreviewToken && activeKey == key) action()
        }
    }

    private fun finishPreview(key: String) {
        if (activeKey != key) return
        stopActivePreview()
    }

    private fun stopActivePreview(notify: Boolean = true) {
        val stoppedKey = activeKey
        requestToken++
        previewJob?.cancel()
        previewJob = null
        cancelPreviewCompletionCheck()
        previewPlayer?.release()
        previewPlayer = null
        previewFile?.delete()
        previewFile = null
        stopSystemPreview()
        if (notify && stoppedKey != null) {
            previewState = TtsVoicePreviewState.IDLE
            onStatusChanged(TtsVoicePreviewStatus(stoppedKey, previewState))
        } else {
            previewState = TtsVoicePreviewState.IDLE
        }
        activeKey = null
    }

    private fun updateState(state: TtsVoicePreviewState) {
        if (previewState == state) return
        previewState = state
        onStatusChanged(TtsVoicePreviewStatus(activeKey, state))
    }

    fun release() {
        stopActivePreview()
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun stopSystemPreview() {
        systemPreviewToken++
        systemPreviewTts?.runCatching {
            stop()
            shutdown()
        }
        systemPreviewTts = null
    }

    companion object {
        private const val COMPLETION_CHECK_INTERVAL_MILLIS = 100L
        private const val TIMELINE_END_GRACE_MILLIS = 300L

        fun keyOf(
            engine: TtsEngineSetting,
            voice: TtsVoice,
            systemDefault: Boolean
        ): String {
            return "${engine.id}|${if (systemDefault) "system" else "voice"}|${voice.id}"
        }
    }
}
