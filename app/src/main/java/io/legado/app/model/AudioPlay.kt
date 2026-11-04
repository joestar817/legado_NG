package io.legado.app.model

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.constant.IntentAction
import io.legado.app.constant.Status
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.book.getBookSource
import io.legado.app.help.book.readSimulating
import io.legado.app.help.book.simulatedTotalChapterNum
import io.legado.app.help.book.update
import io.legado.app.help.config.AppConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.exoplayer.AudioDownloadCache
import io.legado.app.help.exoplayer.CachedAudioChapter
import io.legado.app.help.globalExecutor
import io.legado.app.model.webBook.WebBook
import io.legado.app.service.AudioPlayService
import io.legado.app.model.SourceCallBack
import io.legado.app.utils.postEvent
import io.legado.app.utils.startService
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import kotlin.text.trim

@SuppressLint("StaticFieldLeak")
@Suppress("unused")
object AudioPlay : CoroutineScope by MainScope() {
    /**
     * 播放模式枚举
     */
    enum class PlayMode(val iconRes: Int) {
        LIST_END_STOP(R.drawable.ic_play_mode_list_end_stop),
        SINGLE_LOOP(R.drawable.ic_play_mode_single_loop),
        RANDOM(R.drawable.ic_play_mode_random),
        LIST_LOOP(R.drawable.ic_play_mode_list_loop);

        fun next(): PlayMode {
            return when (this) {
                LIST_END_STOP -> SINGLE_LOOP
                SINGLE_LOOP -> RANDOM
                RANDOM -> LIST_LOOP
                LIST_LOOP -> LIST_END_STOP
            }
        }
    }

    var playMode = PlayMode.LIST_END_STOP
    var status = Status.STOP
    private val loadingState = MutableStateFlow(false)
    internal val loading = loadingState.asStateFlow()
    private var activityContext: Context? = null
    private var serviceContext: Context? = null
    private val context: Context get() = activityContext ?: serviceContext ?: appCtx
    var callback: CallBack? = null
    var book: Book? = null
    var chapterSize = 0
    var simulatedChapterSize = 0
    var durChapterIndex = 0
    var durChapterPos = 0
    var durChapter: BookChapter? = null
    var durPlayUrl = ""
    var durAudioCacheKeys: List<String> = emptyList()
        private set
    var durLyric: String? = null
    var durAudioSize = 0
    var inBookshelf = false
    var bookSource: BookSource? = null
    val loadingChapters = arrayListOf<Int>()
    private class LoadingChapterRequest(val owner: Any, val bookUrl: String?)
    private val loadingChapterOwners = mutableMapOf<Int, LoadingChapterRequest>()
    private var playbackRequestOwner: Any? = null
    private var historyRequestOwner: Any? = null
    @Volatile
    private var tocOrderPausedBookUrl: String? = null
    private var tocOrderPausedChapterUrl: String? = null
    private var resumeUrlLoadAfterTocReorder = false
    private var advanceAfterTocReorder = false
    private val metadataWrites = mutableMapOf<CompletableDeferred<Unit>, String>()
    val executor = globalExecutor

    /** 只暂停地址解析和旧索引写入，正在播放的媒体保持不动；从外部 Main scope 调用。 */
    internal suspend fun pauseForTocReorder(bookUrl: String): Boolean {
        val (jobs, writes) = synchronized(this) {
            if (book?.bookUrl != bookUrl) return false
            tocOrderPausedBookUrl = bookUrl
            tocOrderPausedChapterUrl = durChapter?.url
                ?: appDb.bookChapterDao.getChapter(bookUrl, durChapterIndex)?.url
            resumeUrlLoadAfterTocReorder = loadingState.value && durPlayUrl.isEmpty()
            advanceAfterTocReorder = false
            playbackRequestOwner = null
            historyRequestOwner = null
            loadingChapters.clear()
            loadingChapterOwners.clear()
            upLoading(false)
            val pending = coroutineContext[Job]?.children?.toList().orEmpty()
            pending.forEach { it.cancel() }
            pending to metadataWrites.filterValues { it == bookUrl }.keys.toList()
        }
        jobs.joinAll()
        writes.forEach { it.await() }
        return true
    }

    /** 同一章节 URL 只改变序号/目录元数据，保留媒体地址、缓存键和实时播放位置。 */
    @Synchronized
    internal fun resumeAfterTocReorder(updatedBook: Book) {
        if (tocOrderPausedBookUrl != updatedBook.bookUrl) return
        val chapterUrl = tocOrderPausedChapterUrl
        val reloadUrl = resumeUrlLoadAfterTocReorder
        val advance = advanceAfterTocReorder
        tocOrderPausedBookUrl = null
        tocOrderPausedChapterUrl = null
        resumeUrlLoadAfterTocReorder = false
        advanceAfterTocReorder = false
        if (book?.bookUrl != updatedBook.bookUrl) return
        val chapter = appDb.bookChapterDao.getChapter(updatedBook.bookUrl, updatedBook.durChapterIndex)
            ?.takeIf { chapterUrl == null || it.url == chapterUrl }
            ?: chapterUrl?.let { url ->
                appDb.bookChapterDao.getChapterList(updatedBook.bookUrl).firstOrNull { it.url == url }
            }
        if (chapter == null) {
            AppLog.put("重排后未找到当前音频章节，保留正在播放的媒体")
            return
        }
        updatedBook.durChapterIndex = chapter.index
        updatedBook.durChapterPos = durChapterPos
        book = updatedBook
        chapterSize = updatedBook.totalChapterNum
        simulatedChapterSize = updatedBook.simulatedTotalChapterNum()
        durChapterIndex = chapter.index
        durChapter = chapter
        postEvent(EventBus.AUDIO_SUB_TITLE, chapter.title)
        postEvent(EventBus.AUDIO_SIZE, durAudioSize)
        postEvent(EventBus.AUDIO_PROGRESS, durChapterPos)
        when {
            advance -> next()
            reloadUrl -> loadPlayUrl()
        }
    }

    private fun writeMetadata(bookUrl: String, action: suspend () -> Unit) {
        synchronized(this) {
            if (tocOrderPausedBookUrl == bookUrl) return
            val completed = CompletableDeferred<Unit>()
            metadataWrites[completed] = bookUrl
            Coroutine.async {
                if (tocOrderPausedBookUrl != bookUrl) action()
            }.invokeOnCompletion {
                synchronized(this) { metadataWrites.remove(completed) }
                completed.complete(Unit)
            }
        }
    }

    fun changePlayMode() {
        playMode = playMode.next()
        book?.setPlayMode(playMode.ordinal)
        postEvent(EventBus.PLAY_MODE_CHANGED, playMode)
    }

    fun upData(book: Book) {
        invalidateHistoryRequest()
        ReadingRecordTracker.bindBook(ReadingRecordKind.AUDIO, book.bookUrl, book.name)
        AudioPlay.book = book
        chapterSize = appDb.bookChapterDao.getChapterCount(book.bookUrl)
        simulatedChapterSize = if (book.readSimulating()) {
            book.simulatedTotalChapterNum()
        } else {
            chapterSize
        }
        if (durChapterIndex != book.durChapterIndex) {
            stopPlay()
            durChapterIndex = book.durChapterIndex
            durChapterPos = book.durChapterPos
            durPlayUrl = ""
            durAudioCacheKeys = emptyList()
            durLyric = null
            durAudioSize = 0
        }
        upDurChapter()
    }

    fun resetData(book: Book) {
        invalidateHistoryRequest()
        if (AudioPlayService.isRun) stop()
        ReadingRecordTracker.bindBook(ReadingRecordKind.AUDIO, book.bookUrl, book.name)
        AudioPlay.book = book
        chapterSize = appDb.bookChapterDao.getChapterCount(book.bookUrl)
        simulatedChapterSize = if (book.readSimulating()) {
            book.simulatedTotalChapterNum()
        } else {
            chapterSize
        }
        bookSource = book.getBookSource()
        durChapterIndex = book.durChapterIndex
        durChapterPos = book.durChapterPos
        PlayMode.entries.getOrNull(book.getPlayMode())?.let{
            playMode = it
            postEvent(EventBus.PLAY_MODE_CHANGED, it)
        }
        val playSpeed = book.getPlaySpeed()
        AudioPlayService.playSpeed = playSpeed
        postEvent(EventBus.AUDIO_SPEED, playSpeed)
        durPlayUrl = ""
        durAudioCacheKeys = emptyList()
        durLyric = null
        durAudioSize = 0
        upDurChapter()
        SourceCallBack.callBackBook(SourceCallBack.START_READ, bookSource, book, durChapter)
        postEvent(EventBus.AUDIO_BUFFER_PROGRESS, 0)
    }

    fun upReadTime() {
        ReadingRecordTracker.checkpoint(ReadingRecordKind.AUDIO)
    }

    private fun addLoading(index: Int, owner: Any, bookUrl: String?): Boolean {
        synchronized(this) {
            if (bookUrl != null && tocOrderPausedBookUrl == bookUrl) return false
            if (loadingChapters.contains(index)) {
                val existing = loadingChapterOwners[index]
                if (existing != null && existing.bookUrl == bookUrl) {
                    // Returning to a chapter reuses its in-flight request, including its owner.
                    ownPlaybackRequest(existing.owner)
                    upLoading(true)
                    return false
                }
                loadingChapters.remove(index)
            }
            loadingChapters.add(index)
            loadingChapterOwners[index] = LoadingChapterRequest(owner, bookUrl)
            ownPlaybackRequest(owner)
            return true
        }
    }

    private fun removeLoading(index: Int, owner: Any) {
        synchronized(this) {
            if (loadingChapterOwners[index]?.owner !== owner) return
            loadingChapterOwners.remove(index)
            loadingChapters.remove(index)
        }
    }

    private fun ownPlaybackRequest(owner: Any, history: Boolean = false) {
        synchronized(this) {
            playbackRequestOwner = owner
            historyRequestOwner = owner.takeIf { history }
        }
    }

    private fun ownsPlaybackRequest(owner: Any): Boolean = synchronized(this) {
        playbackRequestOwner === owner
    }

    private fun clearLoadingForRequest(owner: Any) {
        synchronized(this) {
            if (playbackRequestOwner === owner) upLoading(false)
        }
    }

    private fun invalidateHistoryRequest(expectedOwner: Any? = null) {
        synchronized(this) {
            val owner = historyRequestOwner ?: return
            if (expectedOwner != null && owner !== expectedOwner) return
            historyRequestOwner = null
            if (playbackRequestOwner === owner) {
                playbackRequestOwner = null
                upLoading(false)
            }
        }
    }

    fun loadOrUpPlayUrl() {
        if (tocOrderPausedBookUrl == book?.bookUrl) return
        invalidateHistoryRequest()
        if (durPlayUrl.isEmpty()) {
            loadPlayUrl()
        } else {
            upPlayUrl()
        }
    }

    /**
     * 从已保存进度重新开始播放。
     *
     * Service 已退出时，内存中的播放地址可能已经失效；只清理地址并重新解析，
     * 保留 [durChapterIndex] 与 [durChapterPos]，避免通过重新选章才能恢复播放。
     */
    fun playFromSavedProgress(): Boolean {
        if (tocOrderPausedBookUrl == book?.bookUrl) return false
        invalidateHistoryRequest()
        val canReuseActivePlayback = AudioPlayService.isRun &&
                durPlayUrl.isNotEmpty() &&
                AudioPlayService.url == durPlayUrl
        when {
            canReuseActivePlayback && status == Status.PLAY -> return true
            canReuseActivePlayback && status == Status.PAUSE -> {
                resume(context)
                return true
            }
            !canReuseActivePlayback -> {
                durPlayUrl = ""
                durAudioCacheKeys = emptyList()
            }
        }
        loadOrUpPlayUrl()
        return false
    }

    /** Restore an already initialized history bookmark without opening the player Activity. */
    internal suspend fun playFromSavedProgress(restoreGuard: () -> Boolean): Boolean = withContext(Main) {
        if (!restoreGuard()) return@withContext false
        val targetBook = book ?: return@withContext false
        if (tocOrderPausedBookUrl == targetBook.bookUrl) return@withContext false
        val targetSource = bookSource ?: return@withContext false
        val targetChapter = durChapter ?: return@withContext false
        val targetIndex = durChapterIndex
        if (targetChapter.bookUrl != targetBook.bookUrl || targetChapter.index != targetIndex ||
            targetChapter.isVolume
        ) return@withContext false
        val owner = Any()
        ownPlaybackRequest(owner, history = true)
        upLoading(true)
        var dispatched = false
        try {
            val playbackSource = withContext(IO) {
                fetchPlaybackSource(targetSource, targetBook, targetChapter)
            }
            ensureActive()
            if (!ownsPlaybackRequest(owner) || book !== targetBook || bookSource !== targetSource ||
                durChapterIndex != targetIndex || !restoreGuard()
            ) return@withContext false
            val content = playbackSource.content.trim()
            if (content.isEmpty()) {
                appCtx.toastOnUi("未获取到资源链接")
                return@withContext false
            }
            contentLoadFinish(targetChapter, playbackSource.copy(content = content))
            dispatched = true
            true
        } finally {
            if (!dispatched) invalidateHistoryRequest(owner)
        }
    }

    private suspend fun fetchPlaybackSource(
        bookSource: BookSource,
        book: Book,
        chapter: BookChapter,
    ): CachedAudioChapter {
        val cached = try {
            AudioDownloadCache.getCachedChapter(bookSource, book, chapter)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        return cached ?: CachedAudioChapter(
            content = WebBook.getContentAwait(bookSource, book, chapter),
            cacheKeys = emptyList(),
        )
    }

    /**
     * 加载播放URL
     */
    private fun loadPlayUrl() {
        val index = durChapterIndex
        val owner = Any()
        if (addLoading(index, owner, book?.bookUrl)) {
            val book = book
            val bookSource = bookSource
            if (book != null && bookSource != null) {
                upDurChapter()
                val chapter = durChapter
                if (chapter == null) {
                    upLoading(false)
                    removeLoading(index, owner)
                    return
                }
                if (chapter.isVolume) {
                    skipTo(index + 1)
                    removeLoading(index, owner)
                    return
                }
                upLoading(true)
                Coroutine.async(this) {
                    fetchPlaybackSource(bookSource, book, chapter)
                }.onSuccess { playbackSource ->
                    if (!ownsPlaybackRequest(owner)) return@onSuccess
                    val content = playbackSource.content.trim()
                    if (content.isEmpty()) {
                        clearLoadingForRequest(owner)
                        appCtx.toastOnUi("未获取到资源链接")
                    } else {
                        contentLoadFinish(
                            chapter,
                            playbackSource.copy(content = content),
                        )
                    }
                }.onError {
                    if (it is CancellationException) throw it
                    if (!ownsPlaybackRequest(owner)) return@onError
                    AppLog.put("获取资源链接出错\n$it", it, true)
                    clearLoadingForRequest(owner)
                }.onCancel {
                    clearLoadingForRequest(owner)
                    removeLoading(index, owner)
                }.onFinally {
                    if (ownsPlaybackRequest(owner)) callback?.upLyric(durLyric)
                    removeLoading(index, owner)
                }
            } else {
                upLoading(false)
                removeLoading(index, owner)
                appCtx.toastOnUi("book or source is null")
            }
        }
    }

    /**
     * 加载完成
     */
    private fun contentLoadFinish(chapter: BookChapter, playbackSource: CachedAudioChapter) {
        if (chapter.bookUrl == book?.bookUrl && chapter.index == durChapterIndex) {
            durPlayUrl = playbackSource.content
            durAudioCacheKeys = playbackSource.cacheKeys
            durLyric = chapter.getVariable("lyric")
            upPlayUrl()
        }
    }

    private fun upPlayUrl() {
        if (isPlayToEnd()) {
            playNew()
        } else {
            play()
        }
    }

    /**
     * 播放当前章节
     */
    fun play() {
        context.startService<AudioPlayService> {
            action = IntentAction.play
        }
    }

    /**
     * 从头播放新章节
     */
    private fun playNew() {
        context.startService<AudioPlayService> {
            action = IntentAction.playNew
        }
    }

    /**
     * 更新当前章节
     */
    fun upDurChapter() {
        val book = book ?: return
        durChapter = appDb.bookChapterDao.getChapter(book.bookUrl, durChapterIndex)
        durAudioSize = durChapter?.end?.toInt() ?: 0
        val title = durChapter?.title ?: appCtx.getString(R.string.data_loading)
        postEvent(EventBus.AUDIO_SUB_TITLE, title)
        postEvent(EventBus.AUDIO_SIZE, durAudioSize)
        postEvent(EventBus.AUDIO_PROGRESS, durChapterPos)
    }

    fun pause(context: Context) {
        if (AudioPlayService.isRun) {
            context.startService<AudioPlayService> {
                action = IntentAction.pause
            }
        }
    }

    fun resume(context: Context) {
        invalidateHistoryRequest()
        if (AudioPlayService.isRun) {
            context.startService<AudioPlayService> {
                action = IntentAction.resume
            }
        }
    }

    fun stop() {
        io.legado.app.help.bgm.BgmPlaybackController.releaseForegroundPlayback()
        invalidateHistoryRequest()
        if (AudioPlayService.isRun) {
            context.startService<AudioPlayService> {
                action = IntentAction.stop
            }
        }
    }

    fun setSpeed(speed: Float) {
        if (AudioPlayService.isRun) {
            book?.setPlaySpeed(speed)
            val clampedSpeed = speed.coerceIn(0.5f, 3.0f)
            context.startService<AudioPlayService> {
                action = IntentAction.setSpeed
                putExtra("speed", clampedSpeed)
            }
        }
    }

     

    fun adjustProgress(position: Int) {
        durChapterPos = position
        saveRead()
        if (AudioPlayService.isRun) {
            context.startService<AudioPlayService> {
                action = IntentAction.adjustProgress
                putExtra("position", position)
            }
        }
    }

    fun skipTo(index: Int) {
        if (tocOrderPausedBookUrl == book?.bookUrl) return
        Coroutine.async {
            if (tocOrderPausedBookUrl == book?.bookUrl) return@async
            stopPlay()
            if (index in 0..<simulatedChapterSize) {
                durChapterIndex = index
                durChapterPos = 0
                durPlayUrl = ""
                durAudioCacheKeys = emptyList()
                durLyric = null
                saveRead()
                loadPlayUrl()
            }
        }
    }

    fun prev() {
        if (tocOrderPausedBookUrl == book?.bookUrl) return
        Coroutine.async {
            if (tocOrderPausedBookUrl == book?.bookUrl) return@async
            stopPlay()
            if (durChapterIndex > 0) {
                durChapterIndex -= 1
                durChapterPos = 0
                durPlayUrl = ""
                durAudioCacheKeys = emptyList()
                durLyric = null
                saveRead()
                loadPlayUrl()
            }
        }
    }

    fun next() {
        if (tocOrderPausedBookUrl == book?.bookUrl) {
            advanceAfterTocReorder = true
            return
        }
        stopPlay()
        upReadTime()
        when (playMode) {
            PlayMode.LIST_END_STOP -> {
                if (durChapterIndex + 1 < simulatedChapterSize) {
                    durChapterIndex += 1
                    durChapterPos = 0
                    durPlayUrl = ""
                    durAudioCacheKeys = emptyList()
                    durLyric = null
                    saveRead()
                    loadPlayUrl()
                }
            }

            PlayMode.SINGLE_LOOP -> {
                durChapterPos = 0
                durPlayUrl = ""
                durAudioCacheKeys = emptyList()
                durLyric = null
                saveRead()
                loadPlayUrl()
            }

            PlayMode.RANDOM -> {
                durChapterIndex = (0 until simulatedChapterSize).random()
                durChapterPos = 0
                durPlayUrl = ""
                durAudioCacheKeys = emptyList()
                durLyric = null
                saveRead()
                loadPlayUrl()
            }

            PlayMode.LIST_LOOP -> {
                durChapterIndex = (durChapterIndex + 1) % simulatedChapterSize
                durChapterPos = 0
                durPlayUrl = ""
                durAudioCacheKeys = emptyList()
                durLyric = null
                saveRead()
                loadPlayUrl()
            }
        }
    }

    fun setTimer(minute: Int) {
        if (AudioPlayService.isRun) {
            val intent = Intent(context, AudioPlayService::class.java)
            intent.action = IntentAction.setTimer
            intent.putExtra("minute", minute)
            context.startService(intent)
        } else {
            AudioPlayService.timeMinute = minute
            postEvent(EventBus.AUDIO_DS, minute)
        }
    }

    fun addTimer() {
        val intent = Intent(context, AudioPlayService::class.java)
        intent.action = IntentAction.addTimer
        context.startService(intent)
    }

    fun stopPlay() {
        invalidateHistoryRequest()
        if (AudioPlayService.isRun) {
            context.startService<AudioPlayService> {
                action = IntentAction.stopPlay
            }
        }
    }

    fun onBookCacheCleared(bookUrl: String) {
        if (book?.bookUrl != bookUrl) return
        durPlayUrl = ""
        durAudioCacheKeys = emptyList()
        if (AudioPlayService.isRun) stop()
    }

    fun saveRead(first: Boolean = false) {
        val book = book ?: return
        writeMetadata(book.bookUrl) {
            book.lastCheckCount = 0
            val durTime = System.currentTimeMillis()
            book.durChapterTime = durTime
            val chapterChanged = book.durChapterIndex != durChapterIndex
            book.durChapterIndex = durChapterIndex
            book.durChapterPos = durChapterPos
            if (first || chapterChanged) {
                appDb.bookChapterDao.getChapter(book.bookUrl, book.durChapterIndex)?.let {
                    book.durChapterTitle = it.getDisplayTitle(
                        ContentProcessor.get(book.name, book.origin).getTitleReplaceRules(),
                        book.getUseReplaceRule(),
                        replaceBook = book.toReplaceBook()
                    )
                    SourceCallBack.callBackBook(SourceCallBack.SAVE_READ, bookSource, book, it,durTime.toString())
                }
            }
            book.update()
        }
    }

    /**
     * 保存章节长度
     */
    fun saveDurChapter(audioSize: Long) {
        val chapter = durChapter ?: return
        writeMetadata(chapter.bookUrl) {
            durAudioSize = audioSize.toInt()
            chapter.end = audioSize
            chapter.update()
        }
    }

    fun playPositionChanged(position: Int) {
        durChapterPos = position
        saveRead()
    }

    fun upLoading(loading: Boolean) {
        loadingState.value = loading
        callback?.upLoading(loading)
    }

    private fun isPlayToEnd(): Boolean {
        return durChapterIndex + 1 == simulatedChapterSize
                && durChapterPos == durAudioSize
    }

    fun register(context: Context) {
        activityContext = context
        callback = context as CallBack
    }

    fun unregister(context: Context) {
        detachActivity(context)
        coroutineContext.cancelChildren()
    }

    internal fun detachActivity(context: Context) {
        if (activityContext === context) {
            activityContext = null
            callback = null
        }
    }

    fun registerService(context: Context) {
        serviceContext = context
    }

    fun unregisterService() {
        serviceContext = null
        loadingState.value = false
    }

    interface CallBack {

        fun upLoading(loading: Boolean)
        fun upLyric(lyric: String?)
        fun upLyricP(position: Int)
    }

}
