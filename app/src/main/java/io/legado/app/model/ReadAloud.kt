package io.legado.app.model

import android.content.Context
import android.content.Intent
import android.os.Bundle
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.constant.IntentAction
import io.legado.app.help.config.AppConfig
import io.legado.app.help.tts.ReadAloudTtsRouter
import io.legado.app.help.tts.TtsEngineSetting
import io.legado.app.help.tts.TtsEngineStore
import io.legado.app.help.tts.TtsEngineType
import io.legado.app.service.BaseReadAloudService
import io.legado.app.service.HttpReadAloudService
import io.legado.app.service.TTSReadAloudService
import io.legado.app.utils.LogUtils
import io.legado.app.utils.postEvent
import io.legado.app.utils.startForegroundServiceCompat
import io.legado.app.utils.toastOnUi
import splitties.init.appCtx

object ReadAloud {
    // 服务初始化复用最近一次路由解析结果，避免在主线程重复查询系统 TTS 与音色目录。
    // 对外的 ttsEngineV2 仍以 TtsEngineStore 为实时数据源，配置语义不依赖该快照。
    @Volatile
    private var preparedActiveEngine: TtsEngineSetting? = null

    @Volatile
    var httpTtsEngineV2: TtsEngineSetting? = null

    @Volatile
    private var aloudClass: Class<*> = getReadAloudClass()

    val ttsEngine get() = ReadBook.book?.getTtsEngine() ?: AppConfig.ttsEngine
    val ttsEngineV2: TtsEngineSetting get() = TtsEngineStore.activeEngine()
    val preparedTtsEngineV2: TtsEngineSetting
        get() = checkNotNull(preparedActiveEngine) { "朗读引擎尚未初始化" }

    private fun getReadAloudClass(): Class<*> {
        val activeEngine = TtsEngineStore.activeEngine()
        preparedActiveEngine = activeEngine
        val engineV2 = if (AppConfig.readAloudMultiRole) {
            ReadAloudTtsRouter.globalScriptNarratorEngine() ?: activeEngine
        } else {
            activeEngine
        }
        if (engineV2.enabled) {
            when (engineV2.type) {
                TtsEngineType.SYSTEM -> {
                    httpTtsEngineV2 = null
                    return TTSReadAloudService::class.java
                }
                TtsEngineType.SCRIPT -> {
                    httpTtsEngineV2 = engineV2
                    return HttpReadAloudService::class.java
                }
            }
        }
        httpTtsEngineV2 = null
        return TTSReadAloudService::class.java
    }

    fun updatePreparedTtsEngine(engine: TtsEngineSetting) {
        if (preparedActiveEngine?.id != engine.id) return
        preparedActiveEngine = engine
        if (httpTtsEngineV2?.id == engine.id) {
            httpTtsEngineV2 = engine
        }
    }

    @Synchronized
    fun upReadAloudClass() {
        stop(appCtx)
        aloudClass = getReadAloudClass()
    }

    @Synchronized
    fun refreshReadAloudClass() {
        aloudClass = getReadAloudClass()
    }

    /**
     * @param engineVerified 仅供“服务已运行”或调用方刚在后台完成引擎校验的热路径使用。
     */
    fun play(
        context: Context,
        play: Boolean = true,
        pageIndex: Int = ReadBook.durPageIndex,
        startPos: Int = 0,
        forceRebuild: Boolean = false,
        engineVerified: Boolean = false,
    ) {
        if (!engineVerified && !TtsEngineStore.hasEnabledEngine()) {
            context.toastOnUi("未启用朗读引擎")
            return
        }
        val intent = Intent(context, aloudClass)
        intent.action = IntentAction.play
        intent.putExtra("play", play)
        intent.putExtra("pageIndex", pageIndex)
        intent.putExtra("startPos", startPos)
        intent.putExtra("forceRebuild", forceRebuild)
        LogUtils.d("ReadAloud", intent.toString())
        try {
            context.startForegroundServiceCompat(intent)
        } catch (e: Exception) {
            val msg = "启动朗读服务出错\n${e.localizedMessage}"
            AppLog.put(msg, e)
            context.toastOnUi(msg)
        }
    }

    fun playByEventBus(
        play: Boolean = true,
        pageIndex: Int = ReadBook.durPageIndex,
        startPos: Int = 0,
        forceRebuild: Boolean = false
    ) {
        val bundle = Bundle().apply {
            putBoolean("play", play)
            putInt("pageIndex", pageIndex)
            putInt("startPos", startPos)
            putBoolean("forceRebuild", forceRebuild)
        }
        postEvent(EventBus.READ_ALOUD_PLAY, bundle)
    }

    fun pause(context: Context) {
        if (BaseReadAloudService.isRun) {
            val intent = Intent(context, aloudClass)
            intent.action = IntentAction.pause
            context.startForegroundServiceCompat(intent)
        }
    }

    fun resume(context: Context) {
        if (BaseReadAloudService.isRun) {
            val intent = Intent(context, aloudClass)
            intent.action = IntentAction.resume
            context.startForegroundServiceCompat(intent)
        }
    }

    fun stop(context: Context) {
        if (BaseReadAloudService.isRun) {
            val intent = Intent(context, aloudClass)
            intent.action = IntentAction.stop
            context.startForegroundServiceCompat(intent)
        }
    }

    fun prevParagraph(context: Context) {
        if (BaseReadAloudService.isRun) {
            val intent = Intent(context, aloudClass)
            intent.action = IntentAction.prevParagraph
            context.startForegroundServiceCompat(intent)
        }
    }

    fun nextParagraph(context: Context) {
        if (BaseReadAloudService.isRun) {
            val intent = Intent(context, aloudClass)
            intent.action = IntentAction.nextParagraph
            context.startForegroundServiceCompat(intent)
        }
    }

    fun prevChapter(context: Context) {
        if (BaseReadAloudService.isRun) {
            val intent = Intent(context, aloudClass)
            intent.action = IntentAction.prev
            context.startForegroundServiceCompat(intent)
        }
    }

    fun nextChapter(context: Context) {
        if (BaseReadAloudService.isRun) {
            val intent = Intent(context, aloudClass)
            intent.action = IntentAction.next
            context.startForegroundServiceCompat(intent)
        }
    }

    fun upTtsSpeechRate(context: Context) {
        if (BaseReadAloudService.isRun) {
            val intent = Intent(context, aloudClass)
            intent.action = IntentAction.upTtsSpeechRate
            context.startForegroundServiceCompat(intent)
        }
    }

    fun refreshTtsRoute(context: Context) {
        if (BaseReadAloudService.isRun) {
            val intent = Intent(context, aloudClass)
            intent.action = IntentAction.refreshTtsRoute
            context.startForegroundServiceCompat(intent)
        }
    }

    fun prepareTtsCasting(context: Context) {
        if (BaseReadAloudService.isRun) {
            val intent = Intent(context, aloudClass)
            intent.action = IntentAction.prepareTtsCasting
            context.startForegroundServiceCompat(intent)
        }
    }

    fun setTimer(context: Context, minute: Int) {
        if (BaseReadAloudService.isRun) {
            val intent = Intent(context, aloudClass)
            intent.action = IntentAction.setTimer
            intent.putExtra("minute", minute)
            context.startForegroundServiceCompat(intent)
        }
    }

}
