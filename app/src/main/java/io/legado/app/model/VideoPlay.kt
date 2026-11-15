package io.legado.app.model

import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.edit
import io.legado.app.R
import io.legado.app.integration.video.NgVideoBridge
import io.legado.app.integration.video.NgVideoPreload
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.constant.SourceType
import io.legado.app.data.appDb
import io.legado.app.data.entities.BaseSource
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.RssReadRecord
import io.legado.app.data.entities.RssSource
import io.legado.app.data.entities.RssStar
import io.legado.app.exception.ContentEmptyException
import io.legado.app.help.CacheManager
import io.legado.app.help.book.update
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.video.VideoPlaybackBridge
import io.legado.app.help.video.VideoPlaybackTarget
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.model.rss.Rss
import io.legado.app.utils.FileUtils
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.externalCache
import io.legado.app.utils.postEvent
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.currentCoroutineContext
import splitties.init.appCtx
import java.io.File

object VideoPlay : CoroutineScope by MainScope(){
    private const val VIDEO_POS_NAME = "video_pos_" //单链接播放进度
    private const val VIDEO_POS_SAVE_TIME = 60 * 60 * 24 * 20 //20天
    private var needClearTemp = true //需要清理缓存
    private const val VIDEO_TEMP_PATH = "video_temp"
    private val videoTempFile by lazy { File(FileUtils.getCachePath(), VIDEO_TEMP_PATH) }

    const val VIDEO_PREF_NAME = "video_config"

    private val videoPrefs: SharedPreferences by lazy { appCtx.getSharedPreferences(VIDEO_PREF_NAME, MODE_PRIVATE) }
    var bufferPolicy: Int
        get() = videoPrefs.getInt("bufferPolicy", 0)
        set(value) { videoPrefs.edit { putInt("bufferPolicy", value.coerceIn(0, 2)) } }
    var decoderFallback: Boolean
        get() = videoPrefs.getBoolean("decoderFallback", true)
        set(value) { videoPrefs.edit { putBoolean("decoderFallback", value) } }
    /**  是否自动播放  **/
    var autoPlay
        get() = videoPrefs.getBoolean("autoPlay", true)
        set(value) {
            videoPrefs.edit { putBoolean("autoPlay", value) }
        }
    /**  直接全屏，需先启用自动播放  **/
    var startFull
        get() = videoPrefs.getBoolean("startFull", false)
        set(value) {
            videoPrefs.edit { putBoolean("startFull", value) }
        }
    /**  长按倍速  **/
    var longPressSpeed
        get() = videoPrefs.getInt("longPressSpeed", 30)
        set(value) {
            videoPrefs.edit { putInt("longPressSpeed", value) }
        }
    /**  全屏底部进度条  **/
    var fullBottomProgressBar
        get() = videoPrefs.getBoolean("fullBottomProgressBar", true)
        set(value) {
            videoPrefs.edit { putBoolean("fullBottomProgressBar", value) }
        }
    /**  弹幕滚动速度  **/
    var danmakuSpeed = 1.2f
    /**  锁屏  **/
    var lockCurScreen = false
    /**  竖屏视频  **/
    var isPortraitVideo = false

    private var isLoading = false
    private val loadScope = CoroutineScope(SupervisorJob() + IO)
    var videoUrl: String? = null //播放链接
    var singleUrl = false
    var videoTitle: String? = null
    var source: BaseSource? = null
    var book: Book? = null
    var toc: List<BookChapter>? =  null
    var chapter: BookChapter? = null
    var volumes = arrayListOf<BookChapter>()
    var episodes: List<BookChapter>? =  null
    /**  在当前episodes中的位置  **/
    var chapterInVolumeIndex = 0
    /**  卷章节 -> 线路或者季数  **/
    var durVolumeIndex = 0
    /**  当前卷  **/
    var durVolume: BookChapter? = null
    /**  本集的进度  **/
    var durChapterPos = 0
    var inBookshelf = true
    /**  订阅收藏  **/
    var rssStar: RssStar? = null
    /**  订阅历史记录,收藏优先  **/
    var rssRecord: RssReadRecord? = null
    /**  弹幕相关  **/
    var danmakuFile: File? = null
    var danmakuStr: String? = null
    var danmakuShow = true

    /**
     * 开始播放
     */
    fun startPlay(target: VideoPlaybackTarget) {
        val source = source ?: return
        danmakuStr = null
        danmakuFile = null
        stopLoading()
        if (NgVideoPreload.tryStart(target)) return
        val player = NgVideoBridge.begin(target)
        player.seekOnStart = 0
        player.loading()
        if (singleUrl) {
            val mUrl = videoUrl ?: return
            Coroutine.async(loadScope, IO) {
                NgVideoBridge.analyzingRules {
                    CacheManager.getLong(VIDEO_POS_NAME + mUrl)?.let {
                        player.seekOnStart = it
                    }
                    inBookshelf = true
                    val analyzeUrl = AnalyzeUrl(
                        NgVideoBridge.initialInput(mUrl),
                        source = source,
                        ruleData = book,
                        chapter = null,
                        coroutineContext = currentCoroutineContext()
                    )
                    NgVideoBridge.prepare(player, analyzeUrl, videoTitle, rawInput = mUrl)
                }
            }.onError {
                player.loadError("加载视频链接失败")
                AppLog.put("加载视频链接失败", it, true)
            }
            return
        }
        durChapterPos.takeIf { it > 0 }?.toLong()?.let { player.seekOnStart = it }
        (source as? RssSource)?.let { s ->
            val rssArticle = rssStar?.toRssArticle() ?: rssRecord?.toRssArticle()
            if (rssArticle == null) {
                player.loadError("未找到订阅")
                appCtx.toastOnUi("未找到订阅")
                return
            }
            val ruleContent = s.ruleContent
            if (ruleContent.isNullOrBlank()) {
                Coroutine.async(loadScope, IO) {
                    NgVideoBridge.analyzingRules {
                        val mUrl = rssArticle.link
                        videoUrl = mUrl
                        val analyzeUrl = AnalyzeUrl(
                            NgVideoBridge.initialInput(mUrl),
                            source = source,
                            ruleData = rssArticle,
                            coroutineContext = currentCoroutineContext()
                        )
                        NgVideoBridge.prepare(player, analyzeUrl, rssArticle.title, rssArticle.link, rawInput = mUrl)
                    }
                }.onError {
                    player.loadError("加载订阅源视频链接失败")
                    AppLog.put("加载订阅源视频链接失败", it, true)
                }
            } else {
                Coroutine.async(loadScope, IO) {
                    NgVideoBridge.analyzingRules {
                        val content = NgVideoBridge.ruleContent {
                            Rss.getContentAwait(rssArticle, ruleContent, s)
                        }.trim()
                        val mUrl = if (content.isEmpty()) {
                            throw ContentEmptyException("正文为空")
                        } else if (NgVideoBridge.isDash(content)) {
                            val name = MD5Utils.md5Encode(content) + ".mpd"
                            val file = FileUtils.createFileIfNotExist(videoTempFile,name)
                            file.writeText(content)
                            Uri.fromFile(file).toString()
                        } else if (content.startsWith("<")) {
                            content // HTML 交给视频解析器，不能当作 MPD 文件。
                        } else {
                            NetworkUtils.getAbsoluteURL(rssArticle.link, content)
                        }
                        videoUrl = mUrl
                        val analyzeUrl = AnalyzeUrl(
                            if (mUrl.startsWith("<")) rssArticle.link else NgVideoBridge.initialInput(mUrl),
                            source = source,
                            ruleData = rssArticle,
                            coroutineContext = currentCoroutineContext()
                        )
                        NgVideoBridge.prepare(player, analyzeUrl, rssArticle.title, rssArticle.link,
                            mUrl.takeIf { it.startsWith("<") }, mUrl.takeUnless { it.startsWith("<") } ?: analyzeUrl.url)
                    }
                }.onError {
                    player.loadError("加载订阅源为链接的正文失败")
                    AppLog.put("加载订阅源为链接的正文失败", it, true)
                }
            }
            return
        }
        val book = book
        if (book == null) {
            player.loadError("未找到书籍")
            appCtx.toastOnUi("未找到书籍")
            return
        }
        chapter = if (episodes.isNullOrEmpty()) {
            //没有卷目录，那么卷就是播放的章节（适合电影类，没有剧集，全是线路卷章节，如果全是章节没有卷的写法，播放完后会继续下一个线路重复播放）
            val durVolume = durVolume
            when {
                durVolume == null -> null
                durVolume.url.startsWith(durVolume.title) -> null //卷章节没获取到链接（链接以标题开头）则返回null
                else -> durVolume
            }
        } else {
            // 优先获取当前索引的剧集，如果不存在则尝试获取第一个剧集
            episodes?.getOrNull(chapterInVolumeIndex) ?: run {
                chapterInVolumeIndex = 0
                episodes?.getOrNull(chapterInVolumeIndex)
            }
        }
        val chapter = chapter
        if (chapter == null) {
            player.loadError("未找到章节")
            appCtx.toastOnUi("未找到章节")
            return
        }
        Coroutine.async(loadScope, IO) {
            NgVideoBridge.prepareEpisode(player, chapter, source as BookSource, book, videoTempFile)
        }.onError {
            player.loadError("获取资源链接出错")
            AppLog.put("获取资源链接出错\n$it", it, true)
        }
        isLoading = false
    }

    fun releaseAllVideos() {
        NgVideoBridge.invalidate()
        VideoPlaybackBridge.releasePlayer()
        if (!isLoading) {
            //还原所有状态
            videoUrl = null
            singleUrl = false
            videoTitle = null
            source = null
            book = null
            toc = null
            chapter = null
            volumes.clear()
            episodes = null
            chapterInVolumeIndex = 0
            durVolumeIndex = 0
            durVolume = null
            durChapterPos = 0
            inBookshelf = true
            rssStar = null
            rssRecord = null
            danmakuStr = null
            danmakuFile = null
            lockCurScreen = false
            isPortraitVideo = false
            if (needClearTemp) {
                needClearTemp = false
                FileUtils.delete(videoTempFile)
            }
        }
    }
    fun onPause() = VideoPlaybackBridge.onVideoPause()
    fun onResume() = VideoPlaybackBridge.onVideoResume()
    fun onResume(seek: Boolean) = VideoPlaybackBridge.onVideoResume(seek)

    fun stopLoading() {
        isLoading = false
        loadScope.coroutineContext.cancelChildren()
    }

    fun initSource(sourceKey: String?, sourceType: Int?, bookUrl: String?, record:String?): Boolean {
        stopLoading()
        NgVideoPreload.clear()
        // A new source is a new selection; an Activity/Service handoff does not call initSource.
        toc = null
        chapter = null
        volumes.clear()
        episodes = null
        durVolume = null
        chapterInVolumeIndex = 0
        durVolumeIndex = 0
        durChapterPos = 0
        rssStar = null
        rssRecord = null
        isLoading = true
        source = sourceKey?.let {
            when (sourceType) {
                SourceType.book -> appDb.bookSourceDao.getBookSource(it)
                SourceType.rss -> appDb.rssSourceDao.getByKey(it)
                else -> null
            }
        }
        book = bookUrl?.let {
            toc = appDb.bookChapterDao.getChapterList(it)
            volumes.clear()
            toc?.forEach { t ->
                if (t.isVolume) {
                    volumes.add(t)
                }
            }
            appDb.bookDao.getBook(it) ?: appDb.searchBookDao.getSearchBook(it)?.toBook()
        }?.also { b ->
            chapterInVolumeIndex = b.chapterInVolumeIndex
            durVolumeIndex = b.durVolumeIndex
            durChapterPos = b.durChapterPos
            source = appDb.bookSourceDao.getBookSource(b.origin)
            SourceCallBack.callBackBook(SourceCallBack.START_READ, source as BookSource?, b, chapter)
        }
        upEpisodes()
        if (source == null) {
            appCtx.toastOnUi("未找到源")
            return false
        }
        record?.let{ //订阅源
            val sourceKey = sourceKey ?: return@let
            rssStar =appDb.rssStarDao.get(sourceKey, it)?.also{ r ->
                durChapterPos = r.durPos
            }
            if (rssStar == null) {
                rssRecord = appDb.rssReadRecordDao.getRecord(it,sourceKey)?.also{ r ->
                    durChapterPos = r.durPos
                }
            }
        }
        return true
    }

    fun upEpisodes() {
        val volumes = volumes
        if (volumes.isEmpty()) {
            durVolume = null
            episodes = toc
            return
        }
        val toc = toc ?: return
        durVolume = volumes.getOrNull(durVolumeIndex)
        if (durVolume == null) {
            durVolumeIndex = 0
            durVolume = volumes.getOrNull(durVolumeIndex)
        }
        val startInt = durVolume?.index ?: 0
        val endInt = volumes.getOrNull(durVolumeIndex + 1)?.index ?: toc.size
        episodes = toc.subList(startInt + 1, endInt)
    }

    fun upDurIndex(offset: Int, player: VideoPlaybackTarget): Boolean {
        val episodes = episodes ?: return false
        val index = chapterInVolumeIndex + offset
        if (index < 0) {
            appCtx.toastOnUi("已到开头")
            return false
        }
        if (index >= episodes.size) {
            appCtx.toastOnUi("已播放完")
            return false
        }
        chapterInVolumeIndex = index
        saveRead(0)
        startPlay(player)
        postEvent(EventBus.UP_VIDEO_INFO, arrayListOf(1)) //更新选集视图
        return true
    }

    fun saveRead(durPos: Int? = null) {
        val book = book
        val rssStar = rssStar
        val rssRecord = rssRecord
        val durPos = durPos ?: VideoPlaybackBridge.state.value.position.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
        durChapterPos = durPos
        if (book == null && rssStar == null && rssRecord == null) {
            videoUrl?.let { videoUrl ->
                CacheManager.put(VIDEO_POS_NAME + videoUrl, durPos, VIDEO_POS_SAVE_TIME)
            }
            return
        }
        val durVolumeIndex = durVolumeIndex
        val chapterInVolumeIndex = chapterInVolumeIndex
        val source = source
        val volumes = volumes.toList()
        val durVolume = durVolume
        val toc = toc
        Coroutine.async {
            book?.let { book ->
                book.lastCheckCount = 0
                val durTime = System.currentTimeMillis()
                book.durChapterTime = durTime
                book.durVolumeIndex = durVolumeIndex
                book.chapterInVolumeIndex = chapterInVolumeIndex
                val durChapterIndex = if (volumes.isEmpty()) chapterInVolumeIndex else
                    (durVolume?.index ?: 0) + chapterInVolumeIndex + 1
                book.durChapterIndex = durChapterIndex
                book.durChapterPos = durPos
                val chapter = toc?.getOrNull(durChapterIndex)
                videoTitle = chapter?.title
                book.durChapterTitle = chapter?.title
                SourceCallBack.callBackBook(SourceCallBack.SAVE_READ, source as BookSource?, book, chapter, durTime.toString())
                book.update()
            }
            rssStar?.let {
                it.durPos = durPos
                videoTitle = it.title
                appDb.rssStarDao.update(it)
            }
            rssRecord?.let {
                it.durPos = durPos
                videoTitle = it.title
                appDb.rssReadRecordDao.update(it)
            }
            postEvent(EventBus.VIDEO_SUB_TITLE, videoTitle ?: appCtx.getString(R.string.data_loading))
        }
    }

    fun getDisplayCover(): String? {
        return book?.getDisplayCover() ?: rssStar?.image ?: rssRecord?.image
    }
}
