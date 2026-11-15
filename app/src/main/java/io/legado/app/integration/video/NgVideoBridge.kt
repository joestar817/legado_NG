package io.legado.app.integration.video

import android.content.Context
import io.legado.app.data.entities.BookChapter
import io.legado.app.help.video.VideoPlaybackTarget
import io.legado.app.help.video.VideoPlaybackBridge
import io.legado.app.help.http.okHttpClient
import io.legado.app.model.VideoPlay
import io.legado.app.utils.GSON
import io.legado.app.utils.externalCache
import io.legado.features.adapter.VideoFeatures
import io.legado.features.adapter.Media3Features
import io.legado.features.adapter.WebVideoFeatures
import io.legado.features.adapter.VideoRuleFeatures
import kotlinx.coroutines.CancellationException
import io.legado.app.utils.NetworkUtils
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.help.config.AppConfig
import io.legado.app.constant.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.legado.features.api.source.VideoResource
import io.legado.features.api.video.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import splitties.init.appCtx
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource

object NgVideoBridge {
    private val features = VideoFeatures()
    private val variants = MutableStateFlow<List<VideoResource>>(emptyList())
    val resources = variants.asStateFlow()
    @Volatile private var media: PlaybackRequest? = null
    private val prefs get() = appCtx.getSharedPreferences("feature_modules", Context.MODE_PRIVATE)
    var enhancement: VideoEnhancement
        get() = VideoEnhancement.fromId(prefs.getInt("video:enhancement", 0))
        set(value) { prefs.edit().putInt("video:enhancement", value.id).apply() }
    @Synchronized fun begin(target: VideoPlaybackTarget): VideoPlaybackTarget {
        val episode = listOf(VideoPlay.book?.bookUrl, VideoPlay.source?.getKey(), VideoPlay.durVolumeIndex, VideoPlay.chapterInVolumeIndex, if (VideoPlay.singleUrl) VideoPlay.videoUrl else "").joinToString(":")
        variants.value = emptyList(); media = null
        return Guarded(target, features.begin(episode), episode)
    }
    private class Guarded(val target: VideoPlaybackTarget, val revision: Long, val episode: String) : VideoPlaybackTarget {
        var request: PlaybackRequest? = null
        override var seekOnStart: Long
            get() = target.seekOnStart
            set(value) { synchronized(NgVideoBridge) { if (features.current(revision)) target.seekOnStart = value } }
        override var mapHeadData: Map<String, String>?
            get() = target.mapHeadData
            set(value) { synchronized(NgVideoBridge) { if (features.current(revision)) target.mapHeadData = features.headers(value.orEmpty(), request?.headers.orEmpty()) } }
        override fun setUp(url: String?, cache: Boolean, cachePath: File?, title: String?): Boolean = synchronized(NgVideoBridge) {
            if (!features.current(revision)) return@synchronized false
            media = request?.copy(url = url.orEmpty(), headers = target.mapHeadData.orEmpty())
            target.setUp(url, cache, cachePath, title)
        }
        override fun startPlayLogic() { synchronized(NgVideoBridge) { if (features.current(revision)) target.startPlayLogic() } }
        override fun loading() { synchronized(NgVideoBridge) { if (features.current(revision)) target.loading() } }
        override fun loadError(message: String) { synchronized(NgVideoBridge) { if (features.current(revision)) target.loadError(message) } }
    }
    suspend fun resolve(target: VideoPlaybackTarget, chapter: BookChapter, source: io.legado.app.data.entities.BookSource, book: io.legado.app.data.entities.Book): PlaybackRequest? {
        val guarded = target as? Guarded ?: return null
        if (!features.current(guarded.revision)) throw kotlinx.coroutines.CancellationException("视频解析会话已失效")
        val json = chapter.variableMap["legadoFeatures.videos"] ?: return null
        var resources = GSON.fromJson(json, Array<VideoResource>::class.java).toList()
        val request = features.resolveRefreshing(guarded.revision, guarded.episode, resources) {
            io.legado.app.integration.source.NgExtensionSourceBridge.content(source, book, chapter, save = false)
            GSON.fromJson(chapter.variableMap["legadoFeatures.videos"], Array<VideoResource>::class.java).toList().also { resources = it }
        } ?: throw kotlinx.coroutines.CancellationException("视频解析会话已失效")
        return synchronized(this) {
            if (!features.current(guarded.revision)) throw kotlinx.coroutines.CancellationException("视频解析会话已失效")
            guarded.request = request
            variants.value = resources
            request
        }
    }
    /** Every NG video entry resolves web pages before they reach the native player. */
    suspend fun <T> analyzingRules(block: suspend () -> T): T = VideoRuleFeatures.analyze(block)

    suspend fun ruleContent(block: suspend () -> String): String {
        val content = try { block() } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (VideoRuleFeatures.captured() == null) throw error
                AppLog.put("视频规则已提供播放网页，继续后台解析：${error.localizedMessage}")
                ""
            }
        return content.takeIf { it.isNotBlank() }?.let { WebVideoFeatures.filterContent(it) { message -> AppLog.put(message) } }
            ?: VideoRuleFeatures.captured()?.url.orEmpty()
    }

    suspend fun prepare(target: VideoPlaybackTarget, analyze: AnalyzeUrl, title: String?, baseUrl: String = analyze.url,
        inlineHtml: String? = null, rawInput: String = analyze.url) {
        val guarded = target as? Guarded ?: return
        if (!features.current(guarded.revision)) throw kotlinx.coroutines.CancellationException("视频解析会话已失效")
        val resolved = resolveMedia(analyze, baseUrl, inlineHtml, rawInput, guarded.request)
        withContext(Dispatchers.Main) {
            synchronized(this@NgVideoBridge) {
                if (!features.current(guarded.revision)) throw kotlinx.coroutines.CancellationException("视频解析会话已失效")
                guarded.request = guarded.request?.copy(url = resolved.url, headers = resolved.headers)
                target.mapHeadData = resolved.headers
                target.setUp(resolved.url, true, File(appCtx.externalCache, "exoplayer"), title)
                AppLog.put("视频播放：已解析媒体地址，交由应用播放器播放")
                if (VideoPlay.autoPlay) target.startPlayLogic()
            }
        }
    }

    internal suspend fun resolveMedia(analyze: AnalyzeUrl, baseUrl: String, inlineHtml: String?,
        rawInput: String, request: PlaybackRequest?): VideoResource {
        val source = analyze.getSource()
        val headers = features.headers(mapOf("User-Agent" to AppConfig.userAgent),
            features.headers(analyze.getRequestHeaders(), request?.headers.orEmpty()))
        val captured = VideoRuleFeatures.captured()
        val documentBase = listOf(baseUrl, source?.getKey().orEmpty()).firstOrNull(WebVideoFeatures::isNetwork)
            ?: baseUrl
        val capturedUrl = captured?.url?.let { NetworkUtils.getAbsoluteURL(documentBase, it) }
        val pageUrl = capturedUrl?.takeIf(String::isNotBlank) ?: rawInput
        val inlineDocument = captured?.html?.takeIf { pageUrl == capturedUrl } ?: inlineHtml
        return NgLegadoVideoResolver.resolve(analyze, pageUrl, documentBase, inlineDocument, headers)
    }

    internal suspend fun prepareEpisode(target: VideoPlaybackTarget, chapter: BookChapter,
        source: io.legado.app.data.entities.BookSource, book: io.legado.app.data.entities.Book, directory: File) {
        val guarded = target as? Guarded ?: return
        if (!features.current(guarded.revision)) throw CancellationException("视频解析会话已失效")
        val resolved = NgVideoEpisodeResolver.resolve(source, book, chapter, features.selected(), directory, save = true)
        withContext(Dispatchers.Main) {
            synchronized(this@NgVideoBridge) {
                publish(guarded, resolved)
                target.mapHeadData = resolved.request.headers
                target.setUp(resolved.request.url, true, File(appCtx.externalCache, "exoplayer"), resolved.title)
                AppLog.put("视频播放：已解析媒体地址，交由应用播放器播放")
                if (VideoPlay.autoPlay) target.startPlayLogic()
            }
        }
    }

    private fun publish(guarded: Guarded, resolved: NgResolvedEpisode) {
        if (!features.current(guarded.revision)) throw CancellationException("视频解析会话已失效")
        guarded.request = resolved.request
        media = resolved.request
        variants.value = resolved.resources
        features.select(resolved.selection.lineId, resolved.selection.qualityTitle, 0)
        features.tracks(resolved.selection.subtitle, resolved.selection.audio, 0)
        VideoPlay.videoUrl = resolved.input
        VideoPlay.danmakuStr = resolved.danmaku as? String
        VideoPlay.danmakuFile = resolved.danmaku as? File
    }

    @Synchronized internal fun adoptEpisode(resolved: NgResolvedEpisode) {
        val guarded = begin(VideoPlaybackBridge) as Guarded
        publish(guarded, resolved)
        guarded.mapHeadData = resolved.request.headers
    }
    fun isDash(text: String) = WebVideoFeatures.isDash(text)
    fun initialInput(input: String): String = WebVideoFeatures.pluginSource(input)?.url ?: WebVideoFeatures.legacySource(
        WebVideoFeatures.candidates(input).firstOrNull().orEmpty()).url
    fun mediaItem(url: String): androidx.media3.common.MediaItem = androidx.media3.common.MediaItem.Builder()
        .setUri(url).apply { WebVideoFeatures.mimeType(url)?.let(::setMimeType) }.build()
    fun selectQuality(title: String) {
        val position = VideoPlaybackBridge.state.value.position
        features.select(features.selected().lineId, title, position)
        VideoPlay.durChapterPos = position.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
        VideoPlay.startPlay(VideoPlaybackBridge)
    }
    fun selectedQuality() = features.selected().qualityTitle
    fun selection() = features.selected()
    fun selectLine(line: String) {
        features.select(line, null, VideoPlaybackBridge.state.value.position)
        restart()
    }
    fun selectTracks(subtitle: Int?, audio: Int?) {
        features.tracks(subtitle, audio, VideoPlaybackBridge.state.value.position)
        restart()
    }
    private fun restart() {
        VideoPlay.durChapterPos = features.selected().position.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
        VideoPlay.startPlay(VideoPlaybackBridge)
    }
    fun currentRequest() = media
    fun selectAudio(player: ExoPlayer, request: PlaybackRequest?) = Media3Features.selectAudio(player, request)
    fun source(context: Context, base: MediaSource, request: PlaybackRequest?) = Media3Features.source(context, base, request, okHttpClient)
    fun enhance(player: ExoPlayer): Boolean {
        Media3Features.attachCaptions(player)
        return Media3Features.enhance(player, enhancement)
    }
    @Synchronized fun invalidate() { NgVideoPreload.clear(); features.invalidate(); variants.value = emptyList(); media = null; Media3Features.clearCaptions() }
}
