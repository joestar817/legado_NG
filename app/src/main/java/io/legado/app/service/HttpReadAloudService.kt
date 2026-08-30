package io.legado.app.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import com.script.ScriptException
import io.legado.app.constant.EventBus
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.constant.Status
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.ai.AiConfig
import io.legado.app.help.ai.AiTtsStoryboardHelper
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.config.AppConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.tts.BookTtsAutomationConfig
import io.legado.app.help.tts.BookTtsCastingCoordinator
import io.legado.app.help.tts.ReadAloudTtsRouter
import io.legado.app.help.tts.ReadAloudCacheManager
import io.legado.app.help.tts.ReadAloudAudioTask
import io.legado.app.help.tts.ReadAloudMediaItemIdentity
import io.legado.app.help.tts.ReadAloudPlaylistAppendAction
import io.legado.app.help.tts.ReadAloudPreparedItemRange
import io.legado.app.help.tts.ReadAloudPreparedPlaybackTarget
import io.legado.app.help.tts.canReusePreparedReadAloudPlaylist
import io.legado.app.help.tts.preparedReadAloudChapterPosition
import io.legado.app.help.tts.preparedReadAloudPlaybackTarget
import io.legado.app.help.tts.previousReadAloudChapterMediaCount
import io.legado.app.help.tts.expectedReadAloudSeamlessMediaItemCount
import io.legado.app.help.tts.isReadAloudSeamlessPrefixReady
import io.legado.app.help.tts.readAloudSeekPositionMs
import io.legado.app.help.tts.ReadAloudPlaylistProductionState
import io.legado.app.help.tts.shouldSyncReadAloudMediaItemTransition
import io.legado.app.help.tts.TtsEngineSetting
import io.legado.app.help.tts.TtsPlayerFactory
import io.legado.app.help.tts.TtsSynthesisContext
import io.legado.app.help.tts.TtsSpeedPolicy
import io.legado.app.help.tts.TtsScriptEngineClient
import io.legado.app.help.tts.isReadAloudSynthesisTextSilent
import io.legado.app.help.tts.prepareReadAloudAudioTasks
import io.legado.app.help.tts.readAloudPlaylistAppendAction
import io.legado.app.help.tts.readAloudWholeChapterPageEndIndex
import io.legado.app.help.tts.normalizeStoryboardSynthesisText
import io.legado.app.help.tts.parseReadAloudMediaItemIdentity
import io.legado.app.help.tts.toTtsSynthesisContext
import io.legado.app.help.tts.forEngineCapabilities
import io.legado.app.help.tts.hasReadAloudPlayablePrefix
import io.legado.app.help.tts.hasReadAloudProductionGap
import io.legado.app.help.tts.shouldHandoffReadAloudChapter
import io.legado.app.help.tts.writeReadAloudAudioAtomically
import io.legado.app.help.tts.writeReadAloudAudioWithWavRetry
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.model.ListeningPlaybackCoordinator
import io.legado.app.model.CacheBook
import io.legado.app.ui.book.character.ChapterStoryboard
import io.legado.app.ui.book.character.StoryboardScene
import io.legado.app.ui.book.character.StoryboardSegment
import io.legado.app.ui.book.character.StoryboardSegmentType
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import io.legado.app.ui.book.read.page.entities.TextChapter
import io.legado.app.utils.FileUtils
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.postEvent
import io.legado.app.utils.servicePendingIntent
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.htmlunit.corejs.javascript.WrappedException
import java.io.File
import java.io.InputStream
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private const val SILENT_SOUND_FILE_SIZE = 2160L

/**
 * 在线朗读
 */
@SuppressLint("UnsafeOptInUsageError")
class HttpReadAloudService : BaseReadAloudService(),
    Player.Listener {
    private val exoPlayer: ExoPlayer by lazy {
        TtsPlayerFactory.create(
            context = this,
            allowFormatChanges = AppConfig.readAloudMultiRole
        )
    }
    private val ttsFolderPath: String by lazy {
        val directory = ReadBook.book?.let { book ->
            ReadAloudCacheManager.ttsCacheDirectory(this, book)
        } ?: ReadAloudCacheManager.ttsCacheRootDirectory(this)
        directory.absolutePath + File.separator
    }
    private var downloadTask: Coroutine<*>? = null
    private var playIndexJob: Job? = null
    private var backgroundStoryboardPreloadJob: Job? = null
    private var nextStoryboardPreloadJob: Deferred<ChapterStoryboard?>? = null
    private var nextAudioPreloadJob: Job? = null
    private var seamlessChapterQueueJob: Job? = null
    private val downloadErrorNo = AtomicInteger()
    private var playErrorNo = 0
    private var ttsRouter: ReadAloudTtsRouter? = null
    private var speakItems: List<SpeakItem> = emptyList()
    private var speakItemIndex = 0
    private var playlistChapterIndex = -1
    private var pendingPlaylistSeek: PendingPlaylistSeek? = null
    private var preparedSeekInProgress = false
    private var progressGeneration = 0L
    private val seekWindow = Timeline.Window()
    @Volatile
    private var nextChapterPlaybackPlan: NextChapterPlaybackPlan? = null
    @Volatile
    private var seamlessChapterPlan: SeamlessChapterPlan? = null
    private val playlistProductionState = ReadAloudPlaylistProductionState()

    override fun onCreate() {
        super.onCreate()
        exoPlayer.addListener(this)
        applyPlaybackRate()
    }

    override fun onDestroy() {
        super.onDestroy()
        downloadTask?.cancel()
        backgroundStoryboardPreloadJob?.cancel()
        nextStoryboardPreloadJob?.cancel()
        nextAudioPreloadJob?.cancel()
        seamlessChapterQueueJob?.cancel()
        exoPlayer.release()
        Coroutine.async {
            removeCacheFile()
        }
    }

    override fun play() {
        pageChanged = false
        exoPlayer.stop()
        if (!requestFocus()) return
        if (contentList.isEmpty()) {
            AppLog.putDebug("朗读列表为空")
            ReadBook.readAloud(engineVerified = true)
        } else {
            while (nowSpeak in contentList.indices && isReadAloudTextSilent()) {
                if (!skipCurrentReadAloudTextIfNeeded()) {
                    return
                }
            }
            val prefetchedPlan = takeNextChapterPlaybackPlan()
            val seamlessHandoff = prefetchedPlan?.hasPlayablePrefix() == true
            val preparationStage = when {
                seamlessHandoff -> BaseReadAloudService.PREPARATION_NONE
                AppConfig.readAloudMultiRole -> BaseReadAloudService.PREPARATION_STORYBOARD
                else -> BaseReadAloudService.PREPARATION_NONE
            }
            updatePreparationStage(preparationStage)
            super.play()
            if (preparationStage != BaseReadAloudService.PREPARATION_NONE) {
                postEvent(EventBus.ALOUD_STATE, Status.LOADING)
            }
            downloadAndPlayAudios(prefetchedPlan)
        }
    }

    override fun playStop() {
        updatePreparationStage(BaseReadAloudService.PREPARATION_NONE)
        exoPlayer.stop()
        playIndexJob?.cancel()
        speakItems = emptyList()
        speakItemIndex = 0
        playlistChapterIndex = -1
        pendingPlaylistSeek = null
        preparedSeekInProgress = false
        clearSeamlessChapterQueue()
    }

    override fun onNewReadAloudRequest() {
        progressGeneration++
        playIndexJob?.cancel()
        // 当前请求可能直接复用 prepared playlist；跨章 staged 计划必须和 timeline 同寿命。
        // 只有 downloadAndPlayAudios() 真正清空并重建 timeline 时才清理该计划。
    }

    private fun clearSeamlessChapterQueue() {
        seamlessChapterQueueJob?.cancel()
        seamlessChapterQueueJob = null
        seamlessChapterPlan = null
    }

    override fun tryReusePreparedPlayback(play: Boolean, forceRebuild: Boolean): Boolean {
        if (!canReusePreparedReadAloudPlaylist(
                forceRebuild = forceRebuild,
                playlistChapterIndex = playlistChapterIndex,
                currentChapterIndex = ReadBook.durChapterIndex,
                hasSpeakItems = speakItems.isNotEmpty()
            )
        ) {
            return false
        }
        val target = preparedReadAloudPlaybackTarget(
            ranges = speakItems.map { item ->
                ReadAloudPreparedItemRange(item.paragraphIndex, item.start, item.end)
            },
            targetParagraphIndex = nowSpeak,
            targetParagraphOffset = paragraphStartPos,
            mediaItemCount = exoPlayer.mediaItemCount
        ) ?: return false

        pageChanged = false
        playIndexJob?.cancel()
        speakItemIndex = target.itemIndex
        seekToPreparedPlayback(target)
        updatePreparationStage(BaseReadAloudService.PREPARATION_NONE)
        if (play) {
            if (pause) {
                super.resumeReadAloud()
            } else {
                postEvent(EventBus.ALOUD_STATE, Status.PLAY)
            }
            exoPlayer.play()
            if (pendingPlaylistSeek == null) {
                upPlayPos()
            }
        } else {
            exoPlayer.pause()
            postEvent(EventBus.ALOUD_STATE, Status.PAUSE)
        }
        return true
    }

    private fun seekToPreparedPlayback(target: ReadAloudPreparedPlaybackTarget) {
        val durationMs = preparedItemDurationMs(target.itemIndex)
        val item = speakItems[target.itemIndex]
        val positionMs = readAloudSeekPositionMs(
            durationMs = durationMs,
            itemLength = item.text.length,
            itemOffset = target.itemOffset
        )
        val changesMediaItem = target.itemIndex != exoPlayer.currentMediaItemIndex
        val durationKnown = durationMs > 0L
        pendingPlaylistSeek = if (changesMediaItem || !durationKnown) {
            PendingPlaylistSeek(target, durationKnown)
        } else {
            null
        }
        preparedSeekInProgress = true
        exoPlayer.seekTo(target.itemIndex, positionMs)
        updatePreparedSeekProgress(target)
        if (pendingPlaylistSeek == null && exoPlayer.playbackState == Player.STATE_READY) {
            preparedSeekInProgress = false
        }
    }

    private fun preparedItemDurationMs(itemIndex: Int): Long {
        if (itemIndex == exoPlayer.currentMediaItemIndex && exoPlayer.duration > 0L) {
            return exoPlayer.duration
        }
        val timeline = exoPlayer.currentTimeline
        if (timeline.isEmpty || itemIndex !in 0 until timeline.windowCount) return 0L
        return runCatching { timeline.getWindow(itemIndex, seekWindow).durationMs }
            .getOrDefault(0L)
            .coerceAtLeast(0L)
    }

    private fun applyPendingPlaylistSeek(): Boolean {
        val pending = pendingPlaylistSeek ?: return false
        if (exoPlayer.currentMediaItemIndex != pending.target.itemIndex) return false
        if (!pending.positionApplied) {
            val durationMs = exoPlayer.duration.takeIf { it > 0L } ?: return false
            val item = speakItems.getOrNull(pending.target.itemIndex) ?: return false
            exoPlayer.seekTo(
                readAloudSeekPositionMs(
                    durationMs = durationMs,
                    itemLength = item.text.length,
                    itemOffset = pending.target.itemOffset
                )
            )
        }
        speakItemIndex = pending.target.itemIndex
        pendingPlaylistSeek = null
        updatePreparedSeekProgress(pending.target)
        return true
    }

    private fun updatePreparedSeekProgress(target: ReadAloudPreparedPlaybackTarget) {
        val item = speakItems.getOrNull(target.itemIndex) ?: return
        val progress = if (item.paragraphIndex == nowSpeak) {
            currentParagraphBaseNumber() + item.start + target.itemOffset + 1
        } else {
            readAloudNumber + 1
        }
        upTtsProgress(progress)
    }

    private fun updateNextPos() {
        if (speakItems.isNotEmpty()) {
            updateNextPosBySpeakItem()
        } else {
            advanceReadAloudPosition()
        }
    }

    private fun updateNextPosBySpeakItem() {
        val currentItem = speakItems.getOrNull(speakItemIndex)
        if (currentItem == null) {
            advanceReadAloudPosition()
            return
        }
        if (speakItemIndex < speakItems.lastIndex) {
            val nextItem = speakItems[speakItemIndex + 1]
            speakItemIndex++
            if (nextItem.paragraphIndex == currentItem.paragraphIndex) {
                upTtsProgress(currentParagraphBaseNumber() + nextItem.start + 1)
                return
            }
            advanceToParagraph(nextItem.paragraphIndex)
        } else {
            advanceToParagraph(currentItem.paragraphIndex + 1)
            speakItems = emptyList()
            speakItemIndex = 0
        }
    }

    private fun advanceToParagraph(paragraphIndex: Int) {
        while (nowSpeak < paragraphIndex && nowSpeak in contentList.indices) {
            if (!advanceReadAloudPosition()) {
                return
            }
        }
    }

    private fun currentParagraphBaseNumber(): Int {
        return readAloudNumber - paragraphStartPos
    }

    private fun buildSpeakMediaItem(
        file: File,
        generation: Long,
        itemIndex: Int,
        item: SpeakItem,
        chapterIndex: Int = playlistChapterIndex
    ): MediaItem = MediaItem.Builder()
        .setUri(Uri.fromFile(file))
        .setMediaId(
            ReadAloudMediaItemIdentity(
                generation = generation,
                chapterIndex = chapterIndex,
                itemIndex = itemIndex,
                paragraphIndex = item.paragraphIndex,
                start = item.start,
                end = item.end
            ).toMediaId()
        )
        .build()

    private fun currentSpeakItemIndex(mediaItem: MediaItem?): Int? {
        val identity = mediaItem?.mediaId
            ?.let(::parseReadAloudMediaItemIdentity)
            ?: return null
        if (!playlistProductionState.isCurrent(identity.generation) ||
            identity.chapterIndex != playlistChapterIndex
        ) return null
        val item = speakItems.getOrNull(identity.itemIndex) ?: return null
        return identity.itemIndex.takeIf {
            identity.paragraphIndex == item.paragraphIndex &&
                    identity.start == item.start &&
                    identity.end == item.end
        }
    }

    private fun syncSpeakItemPosition(itemIndex: Int): Boolean {
        val item = speakItems.getOrNull(itemIndex) ?: return false
        if (item.paragraphIndex < nowSpeak) return false
        speakItemIndex = itemIndex
        if (item.paragraphIndex > nowSpeak) {
            advanceToParagraph(item.paragraphIndex)
        }
        return nowSpeak == item.paragraphIndex
    }

    private fun takeNextChapterPlaybackPlan(): NextChapterPlaybackPlan? {
        val plan = nextChapterPlaybackPlan?.takeIf { plan ->
            plan.chapterIndex == ReadBook.durChapterIndex &&
                    nowSpeak == 0 && paragraphStartPos == 0
        }
        nextChapterPlaybackPlan = null
        return plan
    }

    private fun NextChapterPlaybackPlan.hasPlayablePrefix(): Boolean {
        val preparedItemCount = preparedFiles
            .takeWhile { it.isFile && it.length() > 0L }
            .size
        return hasReadAloudPlayablePrefix(preparedItemCount, items.size)
    }

    private fun downloadAndPlayAudios(
        prefetchedPlan: NextChapterPlaybackPlan? = takeNextChapterPlaybackPlan()
    ) {
        clearSeamlessChapterQueue()
        val seamlessHandoff = prefetchedPlan?.hasPlayablePrefix() == true
        pendingPlaylistSeek = null
        preparedSeekInProgress = false
        if (!pause && !seamlessHandoff && AppConfig.readAloudMultiRole) {
            updatePreparationStage(BaseReadAloudService.PREPARATION_STORYBOARD)
            postEvent(EventBus.ALOUD_STATE, Status.LOADING)
        }
        exoPlayer.clearMediaItems()
        downloadTask?.cancel()
        backgroundStoryboardPreloadJob?.cancel()
        nextStoryboardPreloadJob?.cancel()
        nextAudioPreloadJob?.cancel()
        val productionToken = playlistProductionState.begin()
        val routeWarningTracker = RouteWarningTracker(productionToken)
        downloadTask = execute {
            ensureActive()
            val engineV2 = ReadAloud.httpTtsEngineV2
            if (engineV2 == null) {
                throw NoStackTraceException("tts is null")
            }
                val storyboard = try {
                    prefetchedPlan?.storyboard ?: loadCurrentAiStoryboard()
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    playlistProductionState.cancel(productionToken)
                    AppLog.put(
                        "AI听书分镜生成失败，朗读已暂停\n${error.localizedMessage}",
                        error,
                        true
                    )
                    pauseReadAloud()
                    return@execute
                }
                try {
                    ensurePlaybackVoiceBindings(showPreparation = !seamlessHandoff)
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    playlistProductionState.cancel(productionToken)
                    AppLog.put(
                        "发音人分配失败，朗读已暂停\n${error.localizedMessage}",
                        error,
                        true
                    )
                    pauseReadAloud()
                    return@execute
                }
                // 缓存分镜只在首声前补缺失绑定；证据复评仍留在后台，不在章中途换声。
                ttsRouter = prefetchedPlan?.router ?: ReadAloudTtsRouter.createForCurrentBook()
                val currentRouter = ttsRouter
                speakItems = prefetchedPlan?.items ?: buildSpeakItems(storyboard)
                speakItemIndex = 0
                playlistChapterIndex = ReadBook.durChapterIndex
                if (speakItems.isEmpty()) {
                    playlistProductionState.cancel(productionToken)
                    nextChapter()
                    return@execute
                }
                val nextChapterIndex = ReadBook.durChapterIndex + 1
                val nextStoryboardTask = startNextStoryboardPreload()
                scheduleAdditionalStoryboardPreloads(nextStoryboardTask)
                val currentSpeakItems = speakItems
                val paragraphStarts = textChapter?.getParagraphs(readAloudByPage)
                    .orEmpty()
                    .map { it.chapterPosition }
                val prefetchedPrefix = buildList {
                    for ((index, file) in prefetchedPlan?.preparedFiles.orEmpty().withIndex()) {
                        val item = currentSpeakItems.getOrNull(index)
                        if (item == null || !file.isFile || file.length() <= 0L) break
                        add(CachedSpeakItem(item, file))
                    }
                }
                val cachedPrefix = prefetchedPrefix.takeIf { it.isNotEmpty() }
                    ?: findCachedSpeakPrefix(
                        engineV2 = engineV2,
                        items = currentSpeakItems,
                        router = currentRouter
                    )
                if (!pause) {
                    if (cachedPrefix.isEmpty()) {
                        updatePreparationStage(BaseReadAloudService.PREPARATION_AUDIO)
                        postEvent(EventBus.ALOUD_STATE, Status.LOADING)
                    } else {
                        updatePreparationStage(BaseReadAloudService.PREPARATION_NONE)
                    }
                }
                if (cachedPrefix.isNotEmpty()) {
                    withContext(Main) {
                        if (!playlistProductionState.isCurrent(productionToken)) {
                            return@withContext
                        }
                        cachedPrefix.lastOrNull()?.item?.let { item ->
                            preparedReadAloudChapterPosition(
                                paragraphStarts = paragraphStarts,
                                paragraphIndex = item.paragraphIndex,
                                preparedEnd = item.end
                            )?.let(::upTtsBufferProgress)
                        }
                        exoPlayer.addMediaItems(
                            cachedPrefix.mapIndexed { index, cached ->
                                buildSpeakMediaItem(
                                    file = cached.file,
                                    generation = productionToken,
                                    itemIndex = index,
                                    item = cached.item
                                )
                            }
                        )
                        playlistProductionState.onItemAppended(productionToken)
                        exoPlayer.seekTo(0, 0L)
                        exoPlayer.prepare()
                    }
                }
                var currentNextAudioPreloadJob: Job? = null
                try {
                    prepareSpeakFilesConcurrently(
                        engineV2 = engineV2,
                        items = currentSpeakItems.drop(cachedPrefix.size),
                        router = currentRouter,
                        routeWarningTracker = routeWarningTracker
                    ) { file ->
                        withContext(Main) {
                            if (!playlistProductionState.isCurrent(productionToken)) {
                                return@withContext
                            }
                            val nextIndex = exoPlayer.mediaItemCount
                            val wasPlaylistEmpty = nextIndex == 0
                            val preparedItem = currentSpeakItems.getOrNull(nextIndex)
                                ?: return@withContext
                            preparedReadAloudChapterPosition(
                                paragraphStarts = paragraphStarts,
                                paragraphIndex = preparedItem.paragraphIndex,
                                preparedEnd = preparedItem.end
                            )?.let(::upTtsBufferProgress)
                            exoPlayer.addMediaItem(
                                buildSpeakMediaItem(
                                    file = file,
                                    generation = productionToken,
                                    itemIndex = nextIndex,
                                    item = preparedItem
                                )
                            )
                            when (readAloudPlaylistAppendAction(
                                resumeProductionGap = playlistProductionState.onItemAppended(
                                    productionToken
                                ),
                                wasPlaylistEmpty = wasPlaylistEmpty,
                                playbackIdle = exoPlayer.playbackState == Player.STATE_IDLE
                            )) {
                                ReadAloudPlaylistAppendAction.RESUME -> {
                                    exoPlayer.seekTo(nextIndex, 0L)
                                    exoPlayer.prepare()
                                }
                                ReadAloudPlaylistAppendAction.START -> {
                                    exoPlayer.seekTo(0, 0L)
                                    exoPlayer.prepare()
                                }
                                ReadAloudPlaylistAppendAction.NONE -> Unit
                            }
                        }
                    }
                    currentNextAudioPreloadJob = nextStoryboardTask?.let { preloadTask ->
                        lifecycleScope.launch(IO) {
                            try {
                                preloadTask.await()?.let { nextStoryboard ->
                                    preDownloadAudios(
                                        engineV2 = engineV2,
                                        chapterIndex = nextChapterIndex,
                                        storyboard = nextStoryboard,
                                        ownerToken = productionToken,
                                        routeWarningTracker = routeWarningTracker
                                    )
                                }
                            } catch (error: Throwable) {
                                if (error is CancellationException) throw error
                                AppLog.put(
                                    "下一章朗读音频预下载失败，已保留当前章播放" +
                                            "\n${error.localizedMessage}",
                                    error
                                )
                            }
                        }
                    }
                    nextAudioPreloadJob = currentNextAudioPreloadJob
                    withContext(Main) {
                        if (playlistProductionState.isCurrent(productionToken) &&
                            routeWarningTracker.roleEngineSucceeded.get() &&
                            routeWarningTracker.failureKeys.isEmpty()
                        ) {
                            clearTtsRouteWarning(ReadBook.book?.bookUrl)
                        }
                        if (!scheduleNextChapterProduction(productionToken) &&
                            playlistProductionState.finish(productionToken)
                        ) {
                            finishPlaybackBatch()
                        }
                    }
                } catch (e: Throwable) {
                    playlistProductionState.cancel(productionToken)
                    currentNextAudioPreloadJob?.cancel()
                    if (e !is CancellationException) {
                        AppLog.put("朗读音频合成失败\n${e.localizedMessage}", e, true)
                        pauseReadAloud()
                    }
                    return@execute
                }
        }.onError {
            playlistProductionState.cancel(productionToken)
            AppLog.put("朗读下载出错\n${it.localizedMessage}", it, true)
        }
    }

    private suspend fun preDownloadAudios(
        engineV2: TtsEngineSetting,
        chapterIndex: Int,
        storyboard: ChapterStoryboard?,
        ownerToken: Long,
        routeWarningTracker: RouteWarningTracker
    ) {
        val textChapter = loadStoryboardTextChapter(chapterIndex) ?: return
        ensurePlaybackVoiceBindings(showPreparation = false)
        // 下一章使用独立路由快照，不能在当前章仍合成时替换全局路由。
        val preloadRouter = ReadAloudTtsRouter.createForCurrentBook()
        val pageEndIndex = readAloudWholeChapterPageEndIndex(textChapter.pageSize) ?: return
        // items 必须覆盖整章；只有实际音频预下载仍限制为前 10 条。
        val contentList = textChapter.getNeedReadAloud(0, readAloudByPage, 0, pageEndIndex)
            .splitToSequence("\n")
            .filter { it.isNotEmpty() }
            .toList()
        val allItems = buildSpeakItemsForContent(
            paragraphs = contentList,
            storyboard = storyboard,
            startParagraphIndex = 0,
            maxItems = Int.MAX_VALUE,
            sourceChapter = textChapter
        )
        val preDownloadItems = allItems.take(10)
        val preparedFiles = arrayListOf<File>()
        prepareSpeakFilesConcurrently(
            engineV2 = engineV2,
            items = preDownloadItems,
            cacheChapter = textChapter,
            router = preloadRouter,
            routeWarningTracker = routeWarningTracker
        ) { file ->
            if (!playlistProductionState.isCurrent(ownerToken)) return@prepareSpeakFilesConcurrently
            preparedFiles += file
            nextChapterPlaybackPlan = NextChapterPlaybackPlan(
                chapterIndex = chapterIndex,
                storyboard = storyboard,
                router = preloadRouter,
                items = allItems,
                preparedFiles = preparedFiles.toList()
            )
        }
    }

    /**
     * 当前章生产完成后，使用同一 worker 上限生产下一章。合成可以并发完成，
     * prepareSpeakFilesConcurrently 会按原 item 顺序回调，因此这里只按连续前缀追加。
     */
    private fun scheduleNextChapterProduction(generation: Long): Boolean {
        if (AppConfig.readAloudMultiRole ||
            seamlessChapterPlan != null ||
            seamlessChapterQueueJob?.isActive == true
        ) return false
        val sourceChapterIndex = playlistChapterIndex
        val sourceMediaItemCount = speakItems.size
        val targetChapterIndex = sourceChapterIndex + 1
        if (targetChapterIndex !in 0 until ReadBook.chapterSize ||
            !playlistProductionState.continueProduction(generation)
        ) return false

        seamlessChapterQueueJob = lifecycleScope.launch(IO) {
            var plan: SeamlessChapterPlan? = null
            try {
                val engineV2 = ReadAloud.httpTtsEngineV2
                    ?: throw NoStackTraceException("tts is null")
                val nextTextChapter = loadStoryboardTextChapter(targetChapterIndex)
                    ?: throw NoStackTraceException("下一章正文不可用")
                val pageEndIndex = readAloudWholeChapterPageEndIndex(nextTextChapter.pageSize)
                    ?: throw NoStackTraceException("下一章没有可朗读页面")
                val nextContentList = nextTextChapter.getNeedReadAloud(
                    0,
                    readAloudByPage,
                    0,
                    pageEndIndex
                )
                    .splitToSequence("\n")
                    .filter { it.isNotEmpty() }
                    .toList()
                val nextRouter = ReadAloudTtsRouter.createForCurrentBook()
                val nextItems = buildSpeakItemsForContent(
                    paragraphs = nextContentList,
                    storyboard = null,
                    startParagraphIndex = 0,
                    maxItems = Int.MAX_VALUE,
                    sourceChapter = nextTextChapter
                )
                if (nextItems.isEmpty()) {
                    throw NoStackTraceException("下一章没有可合成文本")
                }
                val cachedItems = findCachedSpeakPrefix(
                    engineV2 = engineV2,
                    items = nextItems,
                    router = nextRouter,
                    cacheChapter = nextTextChapter
                )
                val nextPlan = SeamlessChapterPlan(
                    generation = generation,
                    sourceChapterIndex = sourceChapterIndex,
                    sourceMediaItemCount = sourceMediaItemCount,
                    chapterIndex = targetChapterIndex,
                    textChapter = nextTextChapter,
                    contentList = nextContentList,
                    paragraphStarts = nextTextChapter.getParagraphs(readAloudByPage)
                        .map { it.chapterPosition },
                    router = nextRouter,
                    items = nextItems
                )
                plan = nextPlan
                val attached = withContext(Main) {
                    attachSeamlessChapterPlan(nextPlan, cachedItems)
                }
                if (!attached) {
                    withContext(Main) {
                        seamlessChapterQueueJob = null
                        if (playlistChapterIndex == sourceChapterIndex &&
                            playlistProductionState.finish(generation)
                        ) {
                            finishPlaybackBatch()
                        }
                    }
                    return@launch
                }

                val routeWarningTracker = RouteWarningTracker(generation)
                prepareSpeakFilesConcurrently(
                    engineV2 = engineV2,
                    items = nextItems.drop(cachedItems.size),
                    cacheChapter = nextTextChapter,
                    router = nextRouter,
                    routeWarningTracker = routeWarningTracker
                ) { file ->
                    val appended = withContext(Main) {
                        appendSeamlessChapterItem(nextPlan, file)
                    }
                    if (!appended) {
                        throw NoStackTraceException("流式队列状态已变化")
                    }
                }
                withContext(Main) {
                    completeSeamlessChapterProduction(nextPlan)
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                withContext(Main) {
                    failSeamlessChapterProduction(
                        generation = generation,
                        sourceChapterIndex = sourceChapterIndex,
                        plan = plan,
                        error = error
                    )
                }
            }
        }
        return true
    }

    private fun attachSeamlessChapterPlan(
        plan: SeamlessChapterPlan,
        cachedItems: List<CachedSpeakItem>
    ): Boolean {
        if (!ownsPlaybackState() ||
            !playlistProductionState.isCurrent(plan.generation) ||
            playlistChapterIndex != plan.sourceChapterIndex ||
            ReadBook.durChapterIndex != plan.sourceChapterIndex ||
            seamlessChapterPlan != null ||
            exoPlayer.mediaItemCount != plan.sourceMediaItemCount
        ) return false

        seamlessChapterPlan = plan
        AppLog.putDebug(
            "听书流式队列预生产 ${plan.sourceChapterIndex}->${plan.chapterIndex}" +
                    " items=${plan.items.size} cached=${cachedItems.size}" +
                    " workers=${AppConfig.readAloudWorkerCount}"
        )
        if (cachedItems.isEmpty()) return true
        val firstMediaItemIndex = exoPlayer.mediaItemCount
        exoPlayer.addMediaItems(
            cachedItems.mapIndexed { index, cached ->
                buildSpeakMediaItem(
                    file = cached.file,
                    generation = plan.generation,
                    itemIndex = index,
                    item = cached.item,
                    chapterIndex = plan.chapterIndex
                )
            }
        )
        plan.preparedFiles += cachedItems.map { it.file }
        resumeSeamlessPlaybackIfNeeded(plan.generation, firstMediaItemIndex)
        return true
    }

    private fun appendSeamlessChapterItem(plan: SeamlessChapterPlan, file: File): Boolean {
        if (!isActiveSeamlessChapterPlan(plan)) return false
        val itemIndex = plan.preparedFiles.size
        val item = plan.items.getOrNull(itemIndex) ?: return false
        val firstMediaItemIndex = exoPlayer.mediaItemCount
        exoPlayer.addMediaItem(
            buildSpeakMediaItem(
                file = file,
                generation = plan.generation,
                itemIndex = itemIndex,
                item = item,
                chapterIndex = plan.chapterIndex
            )
        )
        plan.preparedFiles += file
        if (plan.handedOff) updateSeamlessChapterBufferProgress(plan)
        resumeSeamlessPlaybackIfNeeded(plan.generation, firstMediaItemIndex)
        return true
    }

    private fun isActiveSeamlessChapterPlan(plan: SeamlessChapterPlan): Boolean {
        if (!ownsPlaybackState() ||
            !playlistProductionState.isCurrent(plan.generation) ||
            seamlessChapterPlan !== plan
        ) return false
        val activeChapterIndex = if (plan.handedOff) plan.chapterIndex else plan.sourceChapterIndex
        val expectedMediaItemCount = expectedReadAloudSeamlessMediaItemCount(
            sourceMediaItemCount = plan.sourceMediaItemCount,
            preparedItemCount = plan.preparedFiles.size,
            handedOff = plan.handedOff
        )
        return playlistChapterIndex == activeChapterIndex &&
                ReadBook.durChapterIndex == activeChapterIndex &&
                exoPlayer.mediaItemCount == expectedMediaItemCount
    }

    private fun resumeSeamlessPlaybackIfNeeded(
        generation: Long,
        firstMediaItemIndex: Int
    ) {
        when (readAloudPlaylistAppendAction(
            resumeProductionGap = playlistProductionState.onItemAppended(generation),
            wasPlaylistEmpty = firstMediaItemIndex == 0,
            playbackIdle = exoPlayer.playbackState == Player.STATE_IDLE
        )) {
            ReadAloudPlaylistAppendAction.RESUME -> {
                exoPlayer.seekTo(firstMediaItemIndex, 0L)
                exoPlayer.prepare()
            }

            ReadAloudPlaylistAppendAction.START -> {
                exoPlayer.seekTo(0, 0L)
                exoPlayer.prepare()
            }

            ReadAloudPlaylistAppendAction.NONE -> Unit
        }
    }

    private fun updateSeamlessChapterBufferProgress(plan: SeamlessChapterPlan) {
        val lastPreparedItem = plan.items.getOrNull(plan.preparedFiles.lastIndex) ?: return
        preparedReadAloudChapterPosition(
            paragraphStarts = plan.paragraphStarts,
            paragraphIndex = lastPreparedItem.paragraphIndex,
            preparedEnd = lastPreparedItem.end
        )?.let(::upTtsBufferProgress)
    }

    private fun completeSeamlessChapterProduction(plan: SeamlessChapterPlan) {
        if (!isActiveSeamlessChapterPlan(plan)) {
            failSeamlessChapterProduction(
                generation = plan.generation,
                sourceChapterIndex = plan.sourceChapterIndex,
                plan = plan,
                error = NoStackTraceException("流式队列完成时状态不一致")
            )
            return
        }
        plan.productionComplete = true
        seamlessChapterQueueJob = null
        AppLog.putDebug(
            "听书流式队列生产完成 chapter=${plan.chapterIndex}" +
                    " items=${plan.preparedFiles.size} handedOff=${plan.handedOff}"
        )
        if (plan.handedOff) {
            seamlessChapterPlan = null
            if (!scheduleNextChapterProduction(plan.generation) &&
                playlistProductionState.finish(plan.generation)
            ) {
                finishPlaybackBatch()
            }
        } else if (playlistProductionState.finish(plan.generation)) {
            finishPlaybackBatch()
        }
    }

    private fun failSeamlessChapterProduction(
        generation: Long,
        sourceChapterIndex: Int,
        plan: SeamlessChapterPlan?,
        error: Throwable
    ) {
        if (!playlistProductionState.isCurrent(generation)) return
        seamlessChapterQueueJob = null
        if (plan?.handedOff == true) {
            AppLog.put("下一章流式合成失败\n${error.localizedMessage}", error, true)
            playlistProductionState.cancel(generation)
            pauseReadAloud()
            return
        }
        if (plan != null && seamlessChapterPlan === plan) {
            if (playlistChapterIndex == sourceChapterIndex &&
                exoPlayer.mediaItemCount > plan.sourceMediaItemCount
            ) {
                exoPlayer.removeMediaItems(
                    plan.sourceMediaItemCount,
                    exoPlayer.mediaItemCount
                )
            }
            seamlessChapterPlan = null
        }
        AppLog.put("下一章朗读预合成失败，保留当前章播放\n${error.localizedMessage}", error)
        if (playlistProductionState.finish(generation)) {
            finishPlaybackBatch()
        }
    }

    private suspend fun ensurePlaybackVoiceBindings(showPreparation: Boolean) {
        if (!AppConfig.readAloudMultiRole) return
        val book = ReadBook.book ?: return
        val workKey = BookCharacterProfile.workKey(book.name, book.author)
        if (!BookTtsAutomationConfig.get(workKey).autoAssignVoices) return
        BookTtsCastingCoordinator.assignMissingRolesForPlayback(workKey) {
            if (showPreparation && !pause) {
                updatePreparationStage(BaseReadAloudService.PREPARATION_CASTING)
                postEvent(EventBus.ALOUD_STATE, Status.LOADING)
            }
        }
    }

    private suspend fun prepareSpeakFilesConcurrently(
        engineV2: TtsEngineSetting,
        items: List<SpeakItem>,
        cacheChapter: TextChapter? = null,
        router: ReadAloudTtsRouter? = ttsRouter,
        globalConcurrency: Int = AppConfig.readAloudWorkerCount,
        routeWarningTracker: RouteWarningTracker,
        onPrepared: suspend (File) -> Unit = {}
    ) {
        val tasks = items.map { item ->
            val route = routeFor(router, engineV2, item.segment, item.scene)
            val routedEngine = route?.engine ?: engineV2
            val synthesisText = item.synthesisText()
            val synthesisContext = item.synthesisContext?.forEngineCapabilities(routedEngine)
            val fileName = if (cacheChapter == null) {
                md5SpeakFileName(synthesisText, route, synthesisContext = synthesisContext)
            } else {
                md5SpeakFileName(synthesisText, route, cacheChapter, synthesisContext)
            }
            ReadAloudAudioTask(
                cacheKey = fileName,
                engineKey = routedEngine.id,
                maxConcurrency = routedEngine.effectiveMaxConcurrency(globalConcurrency),
                prepare = {
                    prepareSpeakFileWithFallback(
                        engineV2 = engineV2,
                        item = item,
                        primaryRoute = route,
                        router = router,
                        cacheChapter = cacheChapter,
                        synthesisText = synthesisText,
                        routeWarningTracker = routeWarningTracker
                    )
                }
            )
        }
        prepareReadAloudAudioTasks(tasks, globalConcurrency, onPrepared)
    }

    private suspend fun prepareSpeakFileWithFallback(
        engineV2: TtsEngineSetting,
        item: SpeakItem,
        primaryRoute: ReadAloudTtsRouter.Route?,
        router: ReadAloudTtsRouter?,
        cacheChapter: TextChapter?,
        synthesisText: String,
        routeWarningTracker: RouteWarningTracker
    ): File {
        if (primaryRoute?.bindingUnavailable == true) {
            notifyUnavailableBinding(primaryRoute, item, routeWarningTracker)
        }
        val routes = routeCandidates(engineV2, item, primaryRoute, router)
        var lastError: Throwable? = null
        var roleRouteFailure: Pair<ReadAloudTtsRouter.Route, Throwable>? = null
        routes.forEachIndexed { index, route ->
            val routedEngine = route?.engine ?: engineV2
            val synthesisContext = item.synthesisContext?.forEngineCapabilities(routedEngine)
            val fileName = md5SpeakFileName(
                content = synthesisText,
                route = route,
                textChapter = cacheChapter ?: textChapter,
                synthesisContext = synthesisContext
            )
            try {
                val file = prepareSpeakFile(
                    engineV2 = engineV2,
                    item = item,
                    route = route,
                    fileName = fileName,
                    synthesisContext = synthesisContext,
                    synthesisText = synthesisText
                )
                if (index > 0 && route != null) {
                    roleRouteFailure?.let { (failedRoute, failure) ->
                        notifyRoleRouteFallback(
                            failedRoute,
                            route,
                            failure,
                            routeWarningTracker
                        )
                    }
                } else if (route?.warnOnFailure == true &&
                    playlistProductionState.isCurrent(routeWarningTracker.productionToken)
                ) {
                    routeWarningTracker.roleEngineSucceeded.set(true)
                }
                return file
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                lastError = error
                val fallback = routes.getOrNull(index + 1)
                if (fallback != null) {
                    if (index == 0 && route?.warnOnFailure == true) {
                        roleRouteFailure = route to error
                    }
                    AppLog.put(
                        "TTS片段合成失败，改用${fallback.kind.displayName()}继续朗读" +
                            "\n片段：${item.text.take(80)}\n${error.localizedMessage}",
                        error
                    )
                }
            }
        }
        throw lastError ?: NoStackTraceException("TTS片段无可用合成路径")
    }

    private fun notifyRoleRouteFallback(
        failedRoute: ReadAloudTtsRouter.Route,
        fallbackRoute: ReadAloudTtsRouter.Route,
        error: Throwable,
        routeWarningTracker: RouteWarningTracker
    ) {
        if (!playlistProductionState.isCurrent(routeWarningTracker.productionToken)) return
        val reason = error.localizedMessage
            ?.lineSequence()
            ?.firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "未知错误"
        val noticeKey = "${failedRoute.engine.id}:$reason"
        if (!routeWarningTracker.failureKeys.add(noticeKey)) return
        updateTtsRouteWarning(
            TtsRouteWarning(
                bookUrl = ReadBook.book?.bookUrl,
                engineId = failedRoute.engine.id,
                engineName = failedRoute.engine.name,
                reason = reason,
                fallbackName = fallbackRoute.kind.displayName()
            )
        )
        AppLog.put(
            "角色引擎“${failedRoute.engine.name}”不可用，已临时改用${fallbackRoute.kind.displayName()}" +
                "\n$reason",
            error
        )
    }

    private fun notifyUnavailableBinding(
        route: ReadAloudTtsRouter.Route,
        item: SpeakItem,
        routeWarningTracker: RouteWarningTracker
    ) {
        if (!playlistProductionState.isCurrent(routeWarningTracker.productionToken)) return
        val roleName = item.segment?.speakerName?.takeIf { it.isNotBlank() } ?: "当前角色"
        val reason = "“$roleName”的绑定发音人已不可用"
        val noticeKey = "binding:${route.engine.id}:$roleName"
        if (!routeWarningTracker.failureKeys.add(noticeKey)) return
        updateTtsRouteWarning(
            TtsRouteWarning(
                bookUrl = ReadBook.book?.bookUrl,
                engineId = route.engine.id,
                engineName = route.engine.name,
                reason = reason,
                fallbackName = route.kind.displayName()
            )
        )
        AppLog.put("$reason，已临时改用${route.kind.displayName()}")
    }

    private fun findCachedSpeakPrefix(
        engineV2: TtsEngineSetting?,
        items: List<SpeakItem>,
        router: ReadAloudTtsRouter?,
        cacheChapter: TextChapter? = textChapter
    ): List<CachedSpeakItem> {
        val cachedItems = arrayListOf<CachedSpeakItem>()
        for (item in items) {
            val primaryRoute = routeFor(router, engineV2, item.segment, item.scene)
            val synthesisText = item.synthesisText()
            val routedEngine = primaryRoute?.engine ?: engineV2
            val synthesisContext = item.synthesisContext
                ?.forEngineCapabilities(routedEngine)
            val fileName = md5SpeakFileName(
                content = synthesisText,
                route = primaryRoute,
                textChapter = cacheChapter,
                synthesisContext = synthesisContext
            )
            val cachedFile = getSpeakFileAsMd5(fileName).takeIf { file ->
                file.isFile && file.length() > 0L
            }
            if (cachedFile == null) break
            cachedItems += CachedSpeakItem(item, cachedFile)
        }
        return cachedItems
    }

    private fun routeCandidates(
        engineV2: TtsEngineSetting?,
        item: SpeakItem,
        primaryRoute: ReadAloudTtsRouter.Route?,
        router: ReadAloudTtsRouter?
    ): List<ReadAloudTtsRouter.Route?> = buildList {
        add(primaryRoute)
        if (engineV2 != null) {
            addAll(router?.fallbackRoutes(item.segment, engineV2, primaryRoute).orEmpty())
        }
    }.distinctBy { route ->
        listOf(route?.engine?.id, route?.voiceId, route?.styleId)
    }

    private suspend fun prepareSpeakFile(
        engineV2: TtsEngineSetting,
        item: SpeakItem,
        route: ReadAloudTtsRouter.Route?,
        fileName: String,
        synthesisContext: TtsSynthesisContext?,
        synthesisText: String
    ): File {
        currentCoroutineContext().ensureActive()
        val speakText = synthesisText.replace(AppPattern.notReadAloudRegex, "")
        if (speakText.isEmpty()) {
            AppLog.put("阅读片段内容为空，使用无声音频代替。\n朗读文本：${item.sourceText}")
            if (!hasSpeakFile(fileName)) {
                createSilentSound(fileName)
            }
        } else {
            removeSilentSpeakFile(fileName)
            val target = getSpeakFileAsMd5(fileName)
            writeReadAloudAudioWithWavRetry(
                target = target,
                text = speakText,
                onRejected = { issue, nextAttempt ->
                    AppLog.put(
                        "TTS音频疑似在句中截断，正在第${nextAttempt}次合成" +
                                "\n实际时长：${issue.durationMillis}ms，文本长度：${issue.speechUnits}"
                    )
                }
            ) {
                getSpeakStream(engineV2, speakText, route, synthesisContext)
            }
        }
        return getSpeakFileAsMd5(fileName)
    }

    private fun SpeakItem.synthesisText(): String {
        return normalizeStoryboardSynthesisText(text, segment?.type)
    }

    private suspend fun getSpeakStream(
        engineV2: TtsEngineSetting,
        speakText: String,
        route: ReadAloudTtsRouter.Route?,
        synthesisContext: TtsSynthesisContext?
    ): InputStream {
        while (true) {
            try {
                val routedEngine = route?.engine ?: engineV2
                val stream = TtsScriptEngineClient.getSynthesisStream(
                    engine = routedEngine,
                    text = speakText,
                    voiceId = route?.voiceId ?: routedEngine.activeVoiceId,
                    styleId = route?.styleId,
                    speed = TtsSpeedPolicy.synthesisSpeed(routedEngine),
                    synthesisContext = synthesisContext,
                    coroutineContext = currentCoroutineContext()
                )
                currentCoroutineContext().ensureActive()
                downloadErrorNo.set(0)
                return stream
            } catch (e: Exception) {
                when (e) {
                    is CancellationException -> throw e
                    is ScriptException, is WrappedException -> {
                        AppLog.put("js错误\n${e.localizedMessage}", e, true)
                        e.printOnDebug()
                        throw e
                    }

                    is SocketTimeoutException, is ConnectException -> {
                        if (downloadErrorNo.incrementAndGet() > 5) {
                            val msg = "tts超时或连接错误超过5次\n${e.localizedMessage}"
                            AppLog.put(msg, e, true)
                            throw e
                        }
                    }

                    else -> {
                        val errorCount = downloadErrorNo.incrementAndGet()
                        val msg = "tts下载错误\n${e.localizedMessage}"
                        AppLog.put(msg, e)
                        e.printOnDebug()
                        if (errorCount > 5) {
                            val msg1 = "TTS服务器连续5次错误，已暂停阅读。"
                            AppLog.put(msg1, e, true)
                        }
                        throw e
                    }
                }
            }
        }
    }

    private suspend fun loadCurrentAiStoryboard(): ChapterStoryboard? {
        if (!AppConfig.readAloudMultiRole) {
            return null
        }
        val book = ReadBook.book ?: return null
        val chapter = textChapter ?: return null
        val content = AiTtsStoryboardHelper.readAloudContentFromChapter(chapter, readAloudByPage)
            .takeIf { it.isNotBlank() } ?: return null
        val workKey = BookCharacterProfile.workKey(book.name, book.author)
        val characters = appDb.bookCharacterDao.getCharacters(workKey)
        return AiTtsStoryboardHelper.getOrGenerate(
            book = book,
            chapterIndex = ReadBook.durChapterIndex,
            chapterTitle = chapter.title,
            content = content,
            characters = characters
        )
    }

    private fun startNextStoryboardPreload(): Deferred<ChapterStoryboard?>? {
        if (!AppConfig.readAloudMultiRole) {
            return null
        }
        val preloadCount = AiConfig.readAloudStoryboardPreloadCount
        if (preloadCount <= 0) {
            return null
        }
        val chapterIndex = ReadBook.durChapterIndex + 1
        if (chapterIndex !in 0 until ReadBook.chapterSize) {
            return null
        }
        return lifecycleScope.async(IO) {
            preGenerateAiStoryboard(chapterIndex)
        }.also { nextStoryboardPreloadJob = it }
    }

    private fun scheduleAdditionalStoryboardPreloads(
        nextStoryboardTask: Deferred<ChapterStoryboard?>?
    ) {
        if (!AppConfig.readAloudMultiRole || backgroundStoryboardPreloadJob?.isActive == true) {
            return
        }
        val preloadCount = AiConfig.readAloudStoryboardPreloadCount
        if (preloadCount <= 1) {
            return
        }
        val firstChapterIndex = ReadBook.durChapterIndex + 2
        val maxChapterIndex = minOf(
            ReadBook.durChapterIndex + preloadCount,
            ReadBook.chapterSize - 1
        )
        if (firstChapterIndex > maxChapterIndex) {
            return
        }
        backgroundStoryboardPreloadJob = lifecycleScope.launch(IO) {
            if (nextStoryboardTask?.await() == null) return@launch
            for (chapterIndex in firstChapterIndex..maxChapterIndex) {
                currentCoroutineContext().ensureActive()
                preGenerateAiStoryboard(chapterIndex)
            }
        }
    }

    private suspend fun preGenerateAiStoryboard(chapterIndex: Int): ChapterStoryboard? {
        val book = ReadBook.book ?: return null
        val chapter = loadStoryboardTextChapter(chapterIndex) ?: return null
        val content = AiTtsStoryboardHelper.readAloudContentFromChapter(chapter, readAloudByPage)
            .takeIf { it.isNotBlank() } ?: return null
        val workKey = BookCharacterProfile.workKey(book.name, book.author)
        val characters = appDb.bookCharacterDao.getCharacters(workKey)
        return runCatching {
            AiTtsStoryboardHelper.getOrGenerate(
                book = book,
                chapterIndex = chapterIndex,
                chapterTitle = chapter.title,
                content = content,
                characters = characters
            )
        }.onFailure {
            if (it !is CancellationException) {
                AppLog.put("AI听书分镜预处理失败，章节 $chapterIndex\n${it.localizedMessage}", it)
            }
        }.getOrNull()
    }

    private suspend fun loadStoryboardTextChapter(chapterIndex: Int): TextChapter? {
        textChapter?.takeIf { chapterIndex == ReadBook.durChapterIndex }?.let {
            return it
        }
        ReadBook.nextTextChapter
            ?.takeIf {
                chapterIndex == ReadBook.durChapterIndex + 1 && it.isCompleted
            }
            ?.let { return it }
        if (chapterIndex !in 0 until ReadBook.chapterSize) {
            return null
        }
        val book = ReadBook.book ?: return null
        val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, chapterIndex) ?: return null
        val rawContent = BookHelp.getContent(book, chapter) ?: run {
            val bookSource = ReadBook.bookSource ?: return null
            CacheBook.getOrCreate(bookSource, book).downloadAwait(chapter)
        }
        val contentProcessor = ContentProcessor.get(book.name, book.origin)
        val displayTitle = chapter.getDisplayTitle(
            contentProcessor.getTitleReplaceRules(),
            book.getUseReplaceRule(),
            replaceBook = book.toReplaceBook()
        )
        val contents = contentProcessor.getContent(
            book,
            chapter,
            rawContent,
            includeTitle = false
        )
        return ChapterProvider.getTextChapterAsync(
            lifecycleScope,
            book,
            chapter,
            displayTitle,
            contents,
            ReadBook.simulatedChapterSize
        ).also { generated ->
            generated.layoutChannel.receiveAsFlow().collect()
        }
    }

    private fun buildSpeakItems(storyboard: ChapterStoryboard?): List<SpeakItem> {
        return buildSpeakItemsForContent(
            paragraphs = contentList,
            storyboard = storyboard,
            startParagraphIndex = nowSpeak,
            maxItems = Int.MAX_VALUE,
            sourceChapter = textChapter,
            startParagraphOffset = paragraphStartPos
        )
    }

    private fun buildSpeakItemsForContent(
        paragraphs: List<String>,
        storyboard: ChapterStoryboard?,
        startParagraphIndex: Int,
        maxItems: Int,
        sourceChapter: TextChapter? = null,
        startParagraphOffset: Int = 0
    ): List<SpeakItem> {
        val items = arrayListOf<SpeakItem>()
        val sceneByParagraph = storyboard?.scenes
            .orEmpty()
            .flatMap { scene ->
                scene.segments.map { segment -> segment.paragraphIndex to scene }
            }
            .toMap()
        paragraphs.forEachIndexed { index, originalText ->
            if (index < startParagraphIndex || items.size >= maxItems) return@forEachIndexed
            val readableText = sourceChapter?.let { chapter ->
                getReadAloudText(
                    chapter = chapter,
                    index = index,
                    originalText = originalText,
                    startOffset = startParagraphOffset.takeIf {
                        index == startParagraphIndex
                    } ?: 0
                )
            } ?: originalText
            val readableStart = readableStartOffset(originalText, readableText)
            if (readableStart >= originalText.length) return@forEachIndexed
            val paragraphSegments = AiTtsStoryboardHelper.segmentsForParagraph(
                storyboard = storyboard,
                paragraphIndex = index,
                fallbackText = originalText
            )
            val paragraphItems = paragraphSegments.mapNotNull { segment ->
                segment.toSpeakItem(
                    index,
                    originalText,
                    readableStart,
                    sceneByParagraph[index]
                )
            }
            if (paragraphItems.isNotEmpty()) {
                items += paragraphItems.take(maxItems - items.size)
            } else {
                val readable = readableText
                if (!isReadAloudSynthesisTextSilent(readable)) {
                    items += SpeakItem(
                        paragraphIndex = index,
                        text = readable,
                        start = readableStart,
                        end = originalText.length,
                        sourceText = originalText,
                        synthesisContext = null,
                        scene = null,
                        segment = StoryboardSegment(
                            type = StoryboardSegmentType.NARRATION,
                            paragraphIndex = index,
                            text = readable,
                            speakerName = null,
                            evidence = "旁白",
                            start = readableStart,
                            end = originalText.length
                        )
                    )
                }
            }
        }
        return items
    }

    private fun StoryboardSegment.toSpeakItem(
        paragraphIndex: Int,
        originalText: String,
        readableStart: Int,
        scene: StoryboardScene?
    ): SpeakItem? {
        val safeStart = maxOf(start, readableStart).coerceIn(0, originalText.length)
        val safeEnd = end.coerceIn(0, originalText.length)
        if (safeEnd <= safeStart) return null
        val speakText = originalText.substring(safeStart, safeEnd)
        // 只过滤音频项，正文段落与 paragraphIndex 保持不变，由播放推进逻辑跨过该段。
        if (isReadAloudSynthesisTextSilent(speakText)) return null
        return SpeakItem(
            paragraphIndex = paragraphIndex,
            text = speakText,
            start = safeStart,
            end = safeEnd,
            sourceText = originalText,
            synthesisContext = toTtsSynthesisContext(scene),
            scene = scene,
            segment = copy(
                paragraphIndex = paragraphIndex,
                text = speakText,
                start = safeStart,
                end = safeEnd
            )
        )
    }

    private fun readableStartOffset(originalText: String, readableText: String): Int {
        if (readableText.isBlank()) {
            return originalText.length
        }
        if (readableText == originalText) {
            return 0
        }
        if (originalText.endsWith(readableText)) {
            return originalText.length - readableText.length
        }
        return originalText.indexOf(readableText).takeIf { it >= 0 } ?: 0
    }

    private fun routeFor(
        router: ReadAloudTtsRouter?,
        engineV2: TtsEngineSetting?,
        segment: StoryboardSegment?,
        scene: StoryboardScene?
    ): ReadAloudTtsRouter.Route? {
        val baseEngine = engineV2 ?: return null
        return router?.route(segment, baseEngine, scene)
    }

    private fun ReadAloudTtsRouter.RouteKind.displayName(): String = when (this) {
        ReadAloudTtsRouter.RouteKind.DIALOGUE_FALLBACK -> "对白兜底"
        ReadAloudTtsRouter.RouteKind.NARRATOR -> "旁白"
        ReadAloudTtsRouter.RouteKind.ENGINE_DEFAULT -> "默认声音"
        ReadAloudTtsRouter.RouteKind.CHARACTER,
        ReadAloudTtsRouter.RouteKind.CAST_ROLE -> "角色声音"
    }

    private fun md5SpeakFileName(
        content: String,
        route: ReadAloudTtsRouter.Route?,
        textChapter: TextChapter? = this.textChapter,
        synthesisContext: TtsSynthesisContext? = null
    ): String {
        val scenarioMode = if (AppConfig.readAloudMultiRole) "multi" else "single"
        val engine = route?.engine ?: ReadAloud.httpTtsEngineV2
            ?: throw IllegalStateException("未选择脚本朗读引擎")
        val effectiveSpeed = TtsSpeedPolicy.synthesisSpeed(engine)
        return MD5Utils.md5Encode16(textChapter?.title ?: "") + "_" +
                MD5Utils.md5Encode16(
                    listOf(
                        scenarioMode,
                        TtsScriptEngineClient.audioCacheKey(
                            engine = engine,
                            text = content,
                            voiceId = route?.voiceId ?: engine.activeVoiceId,
                            styleId = route?.styleId,
                            speed = effectiveSpeed,
                            synthesisContext = synthesisContext
                        )
                    ).joinToString("-|-")
                )
    }

    private fun applyPlaybackRate() {
        exoPlayer.setPlaybackSpeed(TtsSpeedPolicy.playbackRate(AppConfig.speechRatePlay))
    }

    private suspend fun createSilentSound(fileName: String) {
        writeReadAloudAudioAtomically(
            getSpeakFileAsMd5(fileName),
            resources.openRawResource(R.raw.silent_sound)
        )
    }

    private fun hasSpeakFile(name: String): Boolean {
        return FileUtils.exist("${ttsFolderPath}$name.mp3")
    }

    private fun removeSilentSpeakFile(name: String) {
        getSpeakFileAsMd5(name)
            .takeIf { it.isFile && it.length() == SILENT_SOUND_FILE_SIZE }
            ?.delete()
    }

    private fun getSpeakFileAsMd5(name: String): File {
        return File("${ttsFolderPath}$name.mp3")
    }

    /**
     * 移除缓存文件
     */
    private fun removeCacheFile() {
        val titleMd5 = MD5Utils.md5Encode16(textChapter?.title ?: "")
        FileUtils.listDirsAndFiles(ttsFolderPath)?.forEach {
            val isSilentSound = it.length() == SILENT_SOUND_FILE_SIZE
            if ((!it.name.startsWith(titleMd5)
                        && System.currentTimeMillis() - it.lastModified() > 600000)
                || isSilentSound
            ) {
                FileUtils.delete(it.absolutePath)
            }
        }
    }


    override fun pauseReadAloud(abandonFocus: Boolean) {
        runOnPlayerThread {
            playIndexJob?.cancel()
            kotlin.runCatching { exoPlayer.pause() }
                .onSuccess { super.pauseReadAloud(abandonFocus) }
                .onFailure { AppLog.put("暂停在线朗读失败", it) }
        }
    }

    override fun resumeReadAloud() {
        ListeningPlaybackCoordinator.beforeReadAloud()
        runOnPlayerThread(::resumeReadAloudOnPlayerThread)
    }

    private fun resumeReadAloudOnPlayerThread() {
        if (pageChanged || exoPlayer.mediaItemCount == 0 ||
            exoPlayer.playbackState == Player.STATE_IDLE ||
            exoPlayer.playbackState == Player.STATE_ENDED
        ) {
            play()
            return
        }
        kotlin.runCatching { exoPlayer.play() }
            .onSuccess {
                super.resumeReadAloud()
                // play() 可能在进入 onSuccess 前同步触发 onIsPlayingChanged(true)，
                // 当时 Base 的 pause 仍为 true，真实回调会被拒绝。清除 pause 后补齐确认，
                // 已经在播放时立即收敛；仍在缓冲时继续等待后续 listener 回调。
                syncActualPlaybackState(exoPlayer.isPlaying)
                upPlayPos()
            }
            .onFailure { AppLog.put("继续在线朗读失败", it) }
    }

    private fun runOnPlayerThread(action: () -> Unit) {
        if (Looper.myLooper() == exoPlayer.applicationLooper) {
            action()
        } else {
            Handler(exoPlayer.applicationLooper).post(action)
        }
    }

    private fun upPlayPos() {
        playIndexJob?.cancel()
        val textChapter = textChapter ?: return
        val activeGeneration = progressGeneration
        playIndexJob = lifecycleScope.launch {
            if (activeGeneration != progressGeneration) return@launch
            val activeItem = speakItems.getOrNull(speakItemIndex)
            val progressBase = activeItem
                ?.takeIf { it.paragraphIndex == nowSpeak }
                ?.let { currentParagraphBaseNumber() + it.start }
                ?: readAloudNumber
            val speakTextLength = activeItem?.text?.length ?: contentList[nowSpeak].length
            if (speakTextLength <= 0) {
                return@launch
            }
            val durationMs = exoPlayer.duration
            val start = if (durationMs > 0L) {
                speakTextLength * exoPlayer.currentPosition / durationMs
            } else {
                0L
            }
            upTtsProgress(progressBase + start.toInt() + 1)
            if (durationMs <= 0L) return@launch
            val playbackRate = exoPlayer.playbackParameters.speed.coerceAtLeast(0.1f)
            val sleep = maxOf(1L, (durationMs / speakTextLength / playbackRate).toLong())
            for (i in start..speakTextLength.toLong()) {
                if (activeGeneration != progressGeneration) return@launch
                if (pageIndex + 1 < textChapter.pageSize
                    && progressBase + i > textChapter.getReadLength(pageIndex + 1)
                ) {
                    pageIndex++
                    ReadBook.moveToNextPage()
                    upTtsProgress(progressBase + i.toInt())
                }
                delay(sleep)
            }
        }
    }

    /**
     * 更新朗读速度
     */
    override fun upSpeechRate(reset: Boolean) {
        applyPlaybackRate()
        if (!pause) {
            upPlayPos()
        }
    }

    override fun refreshTtsRoute() {
        nextChapterPlaybackPlan = null
        clearSeamlessChapterQueue()
        playIndexJob?.cancel()
        downloadTask?.cancel()
        exoPlayer.stop()
        if (!pause) {
            postEvent(EventBus.ALOUD_STATE, Status.LOADING)
        }
        downloadAndPlayAudios()
    }

    override fun prepareTtsCasting() {
        nextChapterPlaybackPlan = null
        clearSeamlessChapterQueue()
        playIndexJob?.cancel()
        downloadTask?.cancel()
        backgroundStoryboardPreloadJob?.cancel()
        nextStoryboardPreloadJob?.cancel()
        nextAudioPreloadJob?.cancel()
        // 新代次在分配完成前保持 producing，吞掉旧队列可能迟到的 ENDED。
        playlistProductionState.begin()
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        ttsRouter = null
        speakItems = emptyList()
        speakItemIndex = 0
        playlistChapterIndex = -1
        pendingPlaylistSeek = null
        preparedSeekInProgress = false
        updatePreparationStage(BaseReadAloudService.PREPARATION_CASTING)
        postEvent(EventBus.ALOUD_STATE, Status.LOADING)
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        super.onPlaybackStateChanged(playbackState)
        if (!ownsPlaybackState()) return
        when (playbackState) {
            Player.STATE_IDLE -> {
                // 空闲
            }

            Player.STATE_BUFFERING -> {
                if (!pause && !preparedSeekInProgress) {
                    postEvent(EventBus.ALOUD_STATE, Status.LOADING)
                }
            }

            Player.STATE_READY -> {
                // 准备好
                applyPendingPlaylistSeek()
                preparedSeekInProgress = false
                updatePreparationStage(BaseReadAloudService.PREPARATION_NONE)
                if (pause) return
                exoPlayer.play()
                upPlayPos()
                postEvent(EventBus.ALOUD_STATE, Status.PLAY)
            }

            Player.STATE_ENDED -> {
                if (playlistProductionState.onPlaybackEnded()) {
                    finishPlaybackBatch()
                } else if (!pause && (
                            seamlessChapterQueueJob?.isActive == true ||
                                    hasReadAloudProductionGap(
                                        enqueuedItemCount = exoPlayer.mediaItemCount,
                                        totalItemCount = speakItems.size
                                    )
                            )
                ) {
                    updatePreparationStage(BaseReadAloudService.PREPARATION_AUDIO)
                    postEvent(EventBus.ALOUD_STATE, Status.LOADING)
                }
            }
        }
    }

    private fun finishPlaybackBatch() {
        playErrorNo = 0
        updateNextPos()
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        super.onIsPlayingChanged(isPlaying)
        syncActualPlaybackState(isPlaying)
    }

    private fun handoffSeamlessChapter(identity: ReadAloudMediaItemIdentity): Boolean {
        val plan = seamlessChapterPlan ?: return false
        if (!shouldHandoffReadAloudChapter(
                currentChapterIndex = playlistChapterIndex,
                mediaChapterIndex = identity.chapterIndex,
                stagedChapterIndex = plan.chapterIndex
            ) ||
            identity.generation != plan.generation ||
            !playlistProductionState.isCurrent(identity.generation) ||
            plan.sourceChapterIndex != playlistChapterIndex ||
            !isReadAloudSeamlessPrefixReady(
                itemIndex = identity.itemIndex,
                preparedItemCount = plan.preparedFiles.size
            )
        ) return false
        val item = plan.items.getOrNull(identity.itemIndex) ?: return false
        if (identity.paragraphIndex != item.paragraphIndex ||
            identity.start != item.start ||
            identity.end != item.end
        ) return false
        val oldChapterMediaCount = previousReadAloudChapterMediaCount(
            exoPlayer.currentMediaItemIndex
        )
        if (oldChapterMediaCount <= 0) return false

        ReadBook.upReadTime()
        ReadBook.nextTextChapter = plan.textChapter
        if (!ReadBook.moveToNextChapter(
                upContent = true,
                restartReadAloud = false
            )
        ) return false

        progressGeneration++
        playIndexJob?.cancel()
        textChapter = ReadBook.curTextChapter ?: plan.textChapter
        contentList = plan.contentList
        speakItems = plan.items
        speakItemIndex = identity.itemIndex
        playlistChapterIndex = plan.chapterIndex
        pendingPlaylistSeek = null
        preparedSeekInProgress = false
        ttsRouter = plan.router
        nowSpeak = item.paragraphIndex
        paragraphStartPos = 0
        readAloudNumber = plan.paragraphStarts.getOrNull(nowSpeak) ?: 0
        pageIndex = textChapter?.getPageIndexByCharIndex(readAloudNumber) ?: 0
        exoPlayer.removeMediaItems(0, oldChapterMediaCount)
        plan.handedOff = true
        AppLog.putDebug(
            "听书流式队列交接 ${plan.sourceChapterIndex}->${plan.chapterIndex}" +
                    " prepared=${plan.preparedFiles.size}/${plan.items.size}"
        )
        updatePreparationStage(BaseReadAloudService.PREPARATION_NONE)
        notifySeamlessChapterChanged()
        upTtsProgress(readAloudNumber + item.start + 1)
        updateSeamlessChapterBufferProgress(plan)
        if (plan.productionComplete) {
            seamlessChapterPlan = null
            scheduleNextChapterProduction(identity.generation)
        }
        return true
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (!ownsPlaybackState()) return
        val identity = mediaItem?.mediaId
            ?.let(::parseReadAloudMediaItemIdentity)
            ?: return
        if (identity.chapterIndex != playlistChapterIndex &&
            !handoffSeamlessChapter(identity)
        ) return
        val itemIndex = currentSpeakItemIndex(mediaItem) ?: return
        val pendingSeek = pendingPlaylistSeek
        if (pendingSeek != null) {
            if (itemIndex != pendingSeek.target.itemIndex) return
            applyPendingPlaylistSeek()
            if (pendingPlaylistSeek == null && exoPlayer.playbackState == Player.STATE_READY) {
                preparedSeekInProgress = false
                if (!pause) upPlayPos()
            }
            return
        }
        val previousIndex = speakItemIndex
        if (!shouldSyncReadAloudMediaItemTransition(
                playlistChanged = reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED,
                previousItemIndex = previousIndex,
                currentItemIndex = itemIndex
            )
        ) return
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
            playErrorNo = 0
        }
        if (!syncSpeakItemPosition(itemIndex)) return
        upPlayPos()
    }

    override fun onPlayerError(error: PlaybackException) {
        super.onPlayerError(error)
        if (!ownsPlaybackState()) return
        AppLog.put("朗读错误\n${contentList[nowSpeak]}", error)
        deleteCurrentSpeakFile()
        playErrorNo++
        if (playErrorNo >= 5) {
            toastOnUi("朗读连续5次错误, 最后一次错误代码(${error.localizedMessage})")
            AppLog.put("朗读连续5次错误, 最后一次错误代码(${error.localizedMessage})", error)
            pauseReadAloud()
        } else {
            if (exoPlayer.hasNextMediaItem()) {
                exoPlayer.seekToNextMediaItem()
                exoPlayer.prepare()
            } else {
                exoPlayer.clearMediaItems()
                updateNextPos()
            }
        }
    }

    private fun deleteCurrentSpeakFile() {
        val mediaItem = exoPlayer.currentMediaItem ?: return
        val filePath = mediaItem.localConfiguration!!.uri.path!!
        File(filePath).delete()
    }

    override fun aloudServicePendingIntent(actionStr: String): PendingIntent? {
        return servicePendingIntent<HttpReadAloudService>(actionStr)
    }

    private data class SpeakItem(
        val paragraphIndex: Int,
        val text: String,
        val start: Int,
        val end: Int,
        val sourceText: String,
        val synthesisContext: TtsSynthesisContext?,
        val scene: StoryboardScene?,
        val segment: StoryboardSegment?
    )

    private data class CachedSpeakItem(
        val item: SpeakItem,
        val file: File
    )

    private data class PendingPlaylistSeek(
        val target: ReadAloudPreparedPlaybackTarget,
        val positionApplied: Boolean
    )

    private data class RouteWarningTracker(
        val productionToken: Long,
        val failureKeys: MutableSet<String> = ConcurrentHashMap.newKeySet(),
        val roleEngineSucceeded: AtomicBoolean = AtomicBoolean()
    )

    private data class NextChapterPlaybackPlan(
        val chapterIndex: Int,
        val storyboard: ChapterStoryboard?,
        val router: ReadAloudTtsRouter?,
        val items: List<SpeakItem>,
        val preparedFiles: List<File>
    )

    private data class SeamlessChapterPlan(
        val generation: Long,
        val sourceChapterIndex: Int,
        val sourceMediaItemCount: Int,
        val chapterIndex: Int,
        val textChapter: TextChapter,
        val contentList: List<String>,
        val paragraphStarts: List<Int>,
        val router: ReadAloudTtsRouter?,
        val items: List<SpeakItem>,
        val preparedFiles: MutableList<File> = arrayListOf(),
        var handedOff: Boolean = false,
        var productionComplete: Boolean = false
    )

}
