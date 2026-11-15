package io.legado.app.integration.video

import android.app.ActivityManager
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Looper
import io.legado.app.constant.AppLog
import io.legado.app.data.entities.BookSource
import io.legado.app.help.video.Media3VideoEngine
import io.legado.app.help.video.VideoPlaybackBridge
import io.legado.app.help.video.VideoPlaybackTarget
import io.legado.app.model.VideoPlay
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.features.api.video.PlaybackSelection
import io.legado.features.preload.NextVideoPreloader
import kotlinx.coroutines.*
import splitties.init.appCtx
import java.io.File

internal object NgVideoPreload {
    private data class Key(
        val source: String, val sourceVersion: String, val book: String, val volume: Int,
        val chapter: Int, val url: String, val variables: String?, val selection: PlaybackSelection,
        val buffer: Int, val fallback: Boolean, val enhancement: Int,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs get() = appCtx.getSharedPreferences("feature_modules", Context.MODE_PRIVATE)
    var enabled: Boolean
        get() {
            val television = (appCtx.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager)
                .currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
            val lowRam = (appCtx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).isLowRamDevice
            return prefs.getBoolean("video:preopen", !television && !lowRam)
        }
        set(value) {
            prefs.edit().putBoolean("video:preopen", value).apply()
            if (!value) clear()
        }

    private val preloader = NextVideoPreloader<Key, NgResolvedEpisode, Media3VideoEngine>(
        scope = scope,
        expiresAt = { it.expiresAt },
        usable = { it.reusable },
        release = { it.releaseMediaPlayer() },
        onFailure = { AppLog.put("视频预加载：准备失败，切集时将正常解析 (${it.javaClass.simpleName})") },
    )

    private fun key(index: Int): Key? {
        if (!enabled || VideoPlay.singleUrl) return null
        val source = VideoPlay.source as? BookSource ?: return null
        val book = VideoPlay.book ?: return null
        val chapter = VideoPlay.episodes?.getOrNull(index)?.takeUnless { it.isVolume } ?: return null
        return Key(source.getKey(), GSON.toJson(source), book.bookUrl, VideoPlay.durVolumeIndex,
            chapter.index, chapter.url, chapter.variable,
            NgVideoBridge.selection().copy(episodeId = "", position = 0),
            VideoPlay.bufferPolicy, VideoPlay.decoderFallback, NgVideoBridge.enhancement.id)
    }

    fun update(playing: Boolean, waiting: Boolean, duration: Long, position: Long, buffered: Long, live: Boolean) {
        val nextIndex = VideoPlay.chapterInVolumeIndex + 1
        val candidate = key(nextIndex)
        val source = VideoPlay.source as? BookSource
        val book = VideoPlay.book
        val chapter = VideoPlay.episodes?.getOrNull(nextIndex)
        if (candidate == null || source == null || book == null || chapter == null || live) {
            clear()
            return
        }
        preloader.update(candidate,
            preloader.policy.eligible(playing, waiting, duration, position, buffered, live),
            resolve = {
                val bookCopy = book.copy(variable = GSON.toJson(book.variableMap),
                    durChapterIndex = chapter.index, chapterInVolumeIndex = nextIndex,
                    durVolumeIndex = candidate.volume, durChapterTitle = chapter.title, durChapterPos = 0).also {
                    it.infoHtml = book.infoHtml
                    it.tocHtml = book.tocHtml
                }
                val chapterCopy = chapter.copy(variable = GSON.toJson(chapter.variableMap))
                val sourceCopy = GSON.fromJson(candidate.sourceVersion, BookSource::class.java)
                withContext(Dispatchers.IO) {
                    NgVideoEpisodeResolver.resolve(sourceCopy, bookCopy, chapterCopy, candidate.selection,
                        File(FileUtils.getCachePath(), "video_temp"), save = false)
                }
            },
            prepare = { plan ->
                Media3VideoEngine().also { player ->
                    try {
                        player.prepare(plan.request.url, plan.request.headers, false, 1f, true, null,
                            0, false, request = plan.request, warmup = true)
                    } catch (error: Exception) {
                        player.releaseMediaPlayer()
                        throw error
                    }
                }
            })
    }

    fun tryStart(target: VideoPlaybackTarget): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            clear()
            return false
        }
        val candidate = key(VideoPlay.chapterInVolumeIndex)
        if (target !== VideoPlaybackBridge || candidate == null || VideoPlay.durChapterPos != 0) {
            clear()
            return false
        }
        val warmed = preloader.take(candidate) ?: return false
        return try {
            VideoPlay.chapter = VideoPlay.episodes?.getOrNull(VideoPlay.chapterInVolumeIndex)
            NgVideoBridge.adoptEpisode(warmed.resource)
            VideoPlaybackBridge.adoptPrepared(warmed.player, warmed.resource.request.url, warmed.resource.title)
            AppLog.put("视频预加载：已接管下一集播放器")
            true
        } catch (error: Exception) {
            warmed.player.releaseMediaPlayer()
            AppLog.put("视频预加载：接管失败，回退正常播放", error)
            false
        }
    }

    fun clear() {
        if (Looper.myLooper() == Looper.getMainLooper()) preloader.clear()
        else scope.launch { preloader.clear() }
    }
}
