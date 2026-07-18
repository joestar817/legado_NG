package io.legado.app.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.net.Uri
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
import io.legado.app.data.entities.HttpTTS
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.ai.AiConfig
import io.legado.app.help.ai.AiTtsStoryboardHelper
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.config.AppConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.tts.ReadAloudTtsRouter
import io.legado.app.help.tts.TtsEngineSetting
import io.legado.app.help.tts.TtsScriptEngineClient
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.model.CacheBook
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.ui.book.character.ChapterStoryboard
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
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Response
import org.mozilla.javascript.WrappedException
import java.io.File
import java.io.InputStream
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

/**
 * 在线朗读
 */
@SuppressLint("UnsafeOptInUsageError")
class HttpReadAloudService : BaseReadAloudService(),
    Player.Listener {
    private val exoPlayer: ExoPlayer by lazy {
        ExoPlayer.Builder(this).build()
    }
    private val ttsFolderPath: String by lazy {
        cacheDir.absolutePath + File.separator + "httpTTS" + File.separator
    }
    private var speechRate: Int = AppConfig.speechRatePlay + 5
    private var downloadTask: Coroutine<*>? = null
    private var playIndexJob: Job? = null
    private val downloadErrorNo = AtomicInteger()
    private var playErrorNo = 0
    private var ttsRouter: ReadAloudTtsRouter? = null
    private var speakItems: List<SpeakItem> = emptyList()
    private var speakItemIndex = 0
    private val downloadTaskActiveLock = Mutex()

    override fun onCreate() {
        super.onCreate()
        exoPlayer.addListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        downloadTask?.cancel()
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
            ReadBook.readAloud()
        } else {
            while (nowSpeak in contentList.indices && isReadAloudTextSilent()) {
                if (!skipCurrentReadAloudTextIfNeeded()) {
                    return
                }
            }
            super.play()
            postEvent(EventBus.ALOUD_STATE, Status.LOADING)
            downloadAndPlayAudios()
        }
    }

    override fun playStop() {
        exoPlayer.stop()
        playIndexJob?.cancel()
        speakItems = emptyList()
        speakItemIndex = 0
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

    private fun downloadAndPlayAudios() {
        exoPlayer.clearMediaItems()
        downloadTask?.cancel()
        downloadTask = execute {
            downloadTaskActiveLock.withLock {
                ensureActive()
                ttsRouter = ReadAloudTtsRouter.createForCurrentBook()
                val httpTts = ReadAloud.httpTTS
                val engineV2 = ReadAloud.httpTtsEngineV2
                if (httpTts == null && engineV2 == null) {
                    throw NoStackTraceException("tts is null")
                }
                val storyboard = loadCurrentAiStoryboard()
                speakItems = buildSpeakItems(storyboard)
                speakItemIndex = 0
                if (speakItems.isEmpty()) {
                    nextChapter()
                    return@execute
                }
                try {
                    prepareSpeakFilesConcurrently(
                        httpTts = httpTts,
                        engineV2 = engineV2,
                        items = speakItems
                    ) { file ->
                        withContext(Main) {
                            exoPlayer.addMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                        }
                    }
                } catch (e: Throwable) {
                    if (e !is CancellationException) {
                        pauseReadAloud()
                    }
                    return@execute
                }
                preDownloadAudios(httpTts, engineV2)
            }
        }.onError {
            AppLog.put("朗读下载出错\n${it.localizedMessage}", it, true)
        }
    }

    private suspend fun preDownloadAudios(httpTts: HttpTTS?, engineV2: TtsEngineSetting?) {
        val textChapter = ReadBook.nextTextChapter ?: return
        val storyboard = preGenerateAiStoryboards()
        val contentList = textChapter.getNeedReadAloud(0, readAloudByPage, 0, 1)
            .splitToSequence("\n")
            .filter { it.isNotEmpty() }
            .toList()
        val preDownloadItems = buildSpeakItemsForContent(
            paragraphs = contentList,
            storyboard = storyboard,
            startParagraphIndex = 0,
            maxItems = 10
        )
        prepareSpeakFilesConcurrently(
            httpTts = httpTts,
            engineV2 = engineV2,
            items = preDownloadItems,
            cacheChapter = textChapter
        )
    }

    private suspend fun prepareSpeakFilesConcurrently(
        httpTts: HttpTTS?,
        engineV2: TtsEngineSetting?,
        items: List<SpeakItem>,
        cacheChapter: TextChapter? = null,
        onPrepared: suspend (File) -> Unit = {}
    ) = coroutineScope {
        val semaphore = Semaphore(AppConfig.readAloudWorkerCount)
        val jobsByFileName = hashMapOf<String, Deferred<File>>()
        val orderedJobs = items.map { item ->
            val route = routeFor(engineV2, item.segment)
            val fileName = if (cacheChapter == null) {
                md5SpeakFileName(item.text, route)
            } else {
                md5SpeakFileName(item.text, route, cacheChapter)
            }
            jobsByFileName.getOrPut(fileName) {
                async {
                    semaphore.withPermit {
                        prepareSpeakFile(httpTts, engineV2, item, route, fileName)
                    }
                }
            }
        }
        orderedJobs.forEach { job ->
            onPrepared(job.await())
        }
    }

    private suspend fun prepareSpeakFile(
        httpTts: HttpTTS?,
        engineV2: TtsEngineSetting?,
        item: SpeakItem,
        route: ReadAloudTtsRouter.Route?,
        fileName: String
    ): File {
        currentCoroutineContext().ensureActive()
        val speakText = item.text.replace(AppPattern.notReadAloudRegex, "")
        if (speakText.isEmpty()) {
            AppLog.put("阅读片段内容为空，使用无声音频代替。\n朗读文本：${item.sourceText}")
            createSilentSound(fileName)
        } else if (!hasSpeakFile(fileName)) {
            val inputStream = getSpeakStream(httpTts, engineV2, speakText, route)
            if (inputStream != null) {
                createSpeakFile(fileName, inputStream)
            } else {
                createSilentSound(fileName)
            }
        }
        return getSpeakFileAsMd5(fileName)
    }

    private suspend fun getSpeakStream(
        httpTts: HttpTTS?,
        engineV2: TtsEngineSetting?,
        speakText: String,
        route: ReadAloudTtsRouter.Route?
    ): InputStream? {
        while (true) {
            try {
                val routedEngine = route?.engine ?: engineV2
                if (routedEngine != null) {
                    val response = TtsScriptEngineClient.getSynthesisResponse(
                        engine = routedEngine,
                        text = speakText,
                        voiceId = route?.voiceId ?: routedEngine.activeVoiceId,
                        styleId = route?.styleId,
                        speed = effectiveEngineSpeed(routedEngine),
                        coroutineContext = currentCoroutineContext()
                    )
                    response.headers["Content-Type"]?.let { contentType ->
                        val normalizedContentType = contentType.substringBefore(";")
                        val expected = routedEngine.contentType.orEmpty()
                        if (normalizedContentType == "application/json" ||
                            normalizedContentType.startsWith("text/")
                        ) {
                            throw NoStackTraceException(response.body.string())
                        } else if (expected.isNotBlank() &&
                            !normalizedContentType.matches(expected.toRegex())
                        ) {
                            throw NoStackTraceException(
                                "TTS服务器返回错误：" + response.body.string()
                            )
                        }
                    }
                    currentCoroutineContext().ensureActive()
                    downloadErrorNo.set(0)
                    return response.body.byteStream()
                }
                val legacyHttpTts = httpTts ?: throw NoStackTraceException("tts is null")
                val analyzeUrl = AnalyzeUrl(
                    legacyHttpTts.url,
                    speakText = speakText,
                    speakSpeed = speechRate,
                    source = legacyHttpTts,
                    readTimeout = 300 * 1000L,
                    coroutineContext = currentCoroutineContext()
                )
                val checkJs = legacyHttpTts.loginCheckJs
                val response = kotlin.runCatching {
                    analyzeUrl.getResponseAwait().let {
                        currentCoroutineContext().ensureActive()
                        if (!checkJs.isNullOrBlank()) {
                            analyzeUrl.evalJS(checkJs, it) as Response
                        } else {
                            it
                        }
                    }
                }.getOrElse { throwable ->
                    currentCoroutineContext().ensureActive()
                    if (!checkJs.isNullOrBlank()) {
                        val errResponse = analyzeUrl.getErrResponse(throwable)
                        try {
                            (analyzeUrl.evalJS(checkJs, errResponse) as Response).also {
                                if (it.code == 500) {
                                    throw throwable
                                }
                            }
                        } catch (_: Throwable) {
                            throw throwable
                        }
                    } else {
                        throw throwable
                    }
                }
                response.headers["Content-Type"]?.let { contentType ->
                    val contentType = contentType.substringBefore(";")
                    val ct = legacyHttpTts.contentType
                    if (contentType == "application/json" || contentType.startsWith("text/")) {
                        throw NoStackTraceException(response.body.string())
                    } else if (ct?.isNotBlank() == true) {
                        if (!contentType.matches(ct.toRegex())) {
                            throw NoStackTraceException(
                                "TTS服务器返回错误：" + response.body.string()
                            )
                        }
                    }
                }
                currentCoroutineContext().ensureActive()
                response.body.byteStream().let { stream ->
                    downloadErrorNo.set(0)
                    return stream
                }
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
                            throw e
                        } else {
                            AppLog.put("TTS下载音频出错，使用无声音频代替。\n朗读文本：$speakText")
                            break
                        }
                    }
                }
            }
        }
        return null
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
        return runCatching {
            AiTtsStoryboardHelper.getOrGenerate(
                book = book,
                chapterIndex = ReadBook.durChapterIndex,
                chapterTitle = chapter.title,
                content = content,
                characters = characters
            )
        }.onFailure {
            AppLog.put("AI听书分镜生成失败，已回退旁白朗读\n${it.localizedMessage}", it)
        }.getOrNull()
    }

    private suspend fun preGenerateAiStoryboards(): ChapterStoryboard? {
        if (!AppConfig.readAloudMultiRole) {
            return null
        }
        val preloadCount = AiConfig.readAloudStoryboardPreloadCount
        if (preloadCount <= 0) {
            return null
        }
        val book = ReadBook.book ?: return null
        val workKey = BookCharacterProfile.workKey(book.name, book.author)
        val characters = appDb.bookCharacterDao.getCharacters(workKey)
        var nextStoryboard: ChapterStoryboard? = null
        val maxChapterIndex = minOf(
            ReadBook.durChapterIndex + preloadCount,
            ReadBook.chapterSize - 1
        )
        for (chapterIndex in (ReadBook.durChapterIndex + 1)..maxChapterIndex) {
            currentCoroutineContext().ensureActive()
            val chapter = loadStoryboardTextChapter(chapterIndex) ?: continue
            val content = AiTtsStoryboardHelper.readAloudContentFromChapter(chapter, readAloudByPage)
                .takeIf { it.isNotBlank() } ?: continue
            val storyboard = runCatching {
                AiTtsStoryboardHelper.getOrGenerate(
                    book = book,
                    chapterIndex = chapterIndex,
                    chapterTitle = chapter.title,
                    content = content,
                    characters = characters
                )
            }.onFailure {
                AppLog.put("AI听书分镜预处理失败，章节 $chapterIndex\n${it.localizedMessage}", it)
            }.getOrNull()
            if (chapterIndex == ReadBook.durChapterIndex + 1) {
                nextStoryboard = storyboard
            }
        }
        return nextStoryboard
    }

    private suspend fun loadStoryboardTextChapter(chapterIndex: Int): TextChapter? {
        textChapter?.takeIf { chapterIndex == ReadBook.durChapterIndex }?.let {
            return it
        }
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
            maxItems = Int.MAX_VALUE
        )
    }

    private fun buildSpeakItemsForContent(
        paragraphs: List<String>,
        storyboard: ChapterStoryboard?,
        startParagraphIndex: Int,
        maxItems: Int
    ): List<SpeakItem> {
        val items = arrayListOf<SpeakItem>()
        paragraphs.forEachIndexed { index, originalText ->
            if (index < startParagraphIndex || items.size >= maxItems) return@forEachIndexed
            val readableStart = if (paragraphs === contentList) {
                readableStartOffset(index, originalText)
            } else {
                0
            }
            if (readableStart >= originalText.length) return@forEachIndexed
            val paragraphSegments = AiTtsStoryboardHelper.segmentsForParagraph(
                storyboard = storyboard,
                paragraphIndex = index,
                fallbackText = originalText
            )
            val paragraphItems = paragraphSegments.mapNotNull { segment ->
                segment.toSpeakItem(index, originalText, readableStart)
            }
            if (paragraphItems.isNotEmpty()) {
                items += paragraphItems.take(maxItems - items.size)
            } else {
                val readable = if (paragraphs === contentList) {
                    getReadAloudText(index)
                } else {
                    originalText
                }
                if (readable.isNotBlank()) {
                    items += SpeakItem(
                        paragraphIndex = index,
                        text = readable,
                        start = readableStart,
                        end = originalText.length,
                        sourceText = originalText,
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
        readableStart: Int
    ): SpeakItem? {
        val safeStart = maxOf(start, readableStart).coerceIn(0, originalText.length)
        val safeEnd = end.coerceIn(0, originalText.length)
        if (safeEnd <= safeStart) return null
        val speakText = originalText.substring(safeStart, safeEnd)
        if (speakText.isBlank()) return null
        return SpeakItem(
            paragraphIndex = paragraphIndex,
            text = speakText,
            start = safeStart,
            end = safeEnd,
            sourceText = originalText,
            segment = copy(
                paragraphIndex = paragraphIndex,
                text = speakText,
                start = safeStart,
                end = safeEnd
            )
        )
    }

    private fun readableStartOffset(index: Int, originalText: String): Int {
        val readableText = getReadAloudText(index)
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
        engineV2: TtsEngineSetting?,
        segment: StoryboardSegment?
    ): ReadAloudTtsRouter.Route? {
        val baseEngine = engineV2 ?: return null
        return ttsRouter?.route(segment, baseEngine)
    }

    private fun md5SpeakFileName(
        content: String,
        route: ReadAloudTtsRouter.Route?,
        textChapter: TextChapter? = this.textChapter
    ): String {
        val scenarioMode = if (AppConfig.readAloudMultiRole) "multi" else "single"
        (route?.engine ?: ReadAloud.httpTtsEngineV2)?.let { engine ->
            val effectiveSpeed = effectiveEngineSpeed(engine)
            return MD5Utils.md5Encode16(textChapter?.title ?: "") + "_" +
                    MD5Utils.md5Encode16(
                        listOf(
                            scenarioMode,
                            TtsScriptEngineClient.audioCacheKey(
                                engine = engine,
                                text = content,
                                voiceId = route?.voiceId ?: engine.activeVoiceId,
                                styleId = route?.styleId,
                                speed = effectiveSpeed
                            )
                        ).joinToString("-|-")
                    )
        }
        return MD5Utils.md5Encode16(textChapter?.title ?: "") + "_" +
                MD5Utils.md5Encode16("$scenarioMode-|-${ReadAloud.httpTTS?.url}-|-$speechRate-|-$content")
    }

    private fun effectiveEngineSpeed(engine: TtsEngineSetting): Int {
        return (engine.effectiveSpeed() * speechRateMultiplier())
            .roundToInt()
            .coerceIn(0, 100)
    }

    private fun speechRateMultiplier(): Float {
        return speechRate / 10f
    }

    private fun createSilentSound(fileName: String) {
        val file = createSpeakFile(fileName)
        file.writeBytes(resources.openRawResource(R.raw.silent_sound).readBytes())
    }

    private fun hasSpeakFile(name: String): Boolean {
        return FileUtils.exist("${ttsFolderPath}$name.mp3")
    }

    private fun getSpeakFileAsMd5(name: String): File {
        return File("${ttsFolderPath}$name.mp3")
    }

    private fun createSpeakFile(name: String): File {
        return FileUtils.createFileIfNotExist("${ttsFolderPath}$name.mp3")
    }

    private fun createSpeakFile(name: String, inputStream: InputStream) {
        FileUtils.createFileIfNotExist("${ttsFolderPath}$name.mp3").outputStream().use { out ->
            inputStream.use {
                it.copyTo(out)
            }
        }
    }

    /**
     * 移除缓存文件
     */
    private fun removeCacheFile() {
        val titleMd5 = MD5Utils.md5Encode16(textChapter?.title ?: "")
        FileUtils.listDirsAndFiles(ttsFolderPath)?.forEach {
            val isSilentSound = it.length() == 2160L
            if ((!it.name.startsWith(titleMd5)
                        && System.currentTimeMillis() - it.lastModified() > 600000)
                || isSilentSound
            ) {
                FileUtils.delete(it.absolutePath)
            }
        }
    }


    override fun pauseReadAloud(abandonFocus: Boolean) {
        super.pauseReadAloud(abandonFocus)
        kotlin.runCatching {
            playIndexJob?.cancel()
            exoPlayer.pause()
        }
    }

    override fun resumeReadAloud() {
        super.resumeReadAloud()
        kotlin.runCatching {
            if (pageChanged) {
                play()
            } else {
                exoPlayer.play()
                upPlayPos()
            }
        }
    }

    private fun upPlayPos() {
        playIndexJob?.cancel()
        val textChapter = textChapter ?: return
        playIndexJob = lifecycleScope.launch {
            val activeItem = speakItems.getOrNull(speakItemIndex)
            val progressBase = activeItem
                ?.takeIf { it.paragraphIndex == nowSpeak }
                ?.let { currentParagraphBaseNumber() + it.start }
                ?: readAloudNumber
            upTtsProgress(progressBase + 1)
            if (exoPlayer.duration <= 0) {
                return@launch
            }
            val speakTextLength = activeItem?.text?.length ?: contentList[nowSpeak].length
            if (speakTextLength <= 0) {
                return@launch
            }
            val sleep = maxOf(1L, exoPlayer.duration / speakTextLength)
            val start = speakTextLength * exoPlayer.currentPosition / exoPlayer.duration
            for (i in start..speakTextLength.toLong()) {
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
        downloadTask?.cancel()
        exoPlayer.stop()
        speechRate = AppConfig.speechRatePlay + 5
        downloadAndPlayAudios()
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        super.onPlaybackStateChanged(playbackState)
        when (playbackState) {
            Player.STATE_IDLE -> {
                // 空闲
            }

            Player.STATE_BUFFERING -> {
                if (!pause) {
                    postEvent(EventBus.ALOUD_STATE, Status.LOADING)
                }
            }

            Player.STATE_READY -> {
                // 准备好
                if (pause) return
                exoPlayer.play()
                upPlayPos()
                postEvent(EventBus.ALOUD_STATE, Status.PLAY)
            }

            Player.STATE_ENDED -> {
                // 结束
                playErrorNo = 0
                updateNextPos()
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
            }
        }
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        when (reason) {
            Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED -> {
                if (!timeline.isEmpty && exoPlayer.playbackState == Player.STATE_IDLE) {
                    exoPlayer.prepare()
                }
            }

            else -> {}
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) return
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
            playErrorNo = 0
        }
        updateNextPos()
        upPlayPos()
    }

    override fun onPlayerError(error: PlaybackException) {
        super.onPlayerError(error)
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
        val segment: StoryboardSegment?
    )

}
