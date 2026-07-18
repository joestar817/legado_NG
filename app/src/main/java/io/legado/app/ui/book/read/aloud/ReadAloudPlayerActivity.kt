package io.legado.app.ui.book.read.aloud

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.drawable.Animatable
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Choreographer
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import android.view.animation.LinearInterpolator
import android.widget.SeekBar
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.constant.EventBus
import io.legado.app.constant.Status
import io.legado.app.databinding.ActivityReadAloudPlayerBinding
import io.legado.app.databinding.ItemBookStoryboardSceneBinding
import io.legado.app.databinding.ItemBookStoryboardSegmentBinding
import io.legado.app.help.ai.AiTtsStoryboardHelper
import io.legado.app.help.book.BookHelp
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.help.tts.ReadAloudTtsRouter
import io.legado.app.help.tts.TtsEngineType
import io.legado.app.help.tts.TtsEngineStore
import io.legado.app.help.tts.TtsScriptEngineClient
import io.legado.app.lib.theme.accentColor
import io.legado.app.model.BookCover
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.service.BaseReadAloudService
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.ui.book.character.ChapterStoryboard
import io.legado.app.ui.book.character.BookCharacterActivity
import io.legado.app.ui.book.character.BookStoryboardActivity
import io.legado.app.ui.book.character.BookCharacterTtsActivity
import io.legado.app.ui.book.character.StoryboardScene
import io.legado.app.ui.book.character.StoryboardSegment
import io.legado.app.ui.book.character.StoryboardSegmentType
import io.legado.app.ui.config.ConfigActivity
import io.legado.app.ui.config.ConfigTag
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.observeEvent
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ReadAloudPlayerActivity : BaseActivity<ActivityReadAloudPlayerBinding>(
    imageBg = true,
    showOpenMenuIcon = false
) {

    override val binding by viewBinding(ActivityReadAloudPlayerBinding::inflate)
    private var lastProgress = -1
    private var cachedChapterIndex = -1
    private var cachedParagraphs = emptyList<ParagraphSummary>()
    private var lastParagraphIndex = -1
    private var paragraphSeeking = false
    private var pendingSeekParagraphIndex = -1
    private var switchingChapter = false
    private var switchingVoice = false
    private var switchingParagraph = false
    private var playButtonLoading = false
    private var playerPage = PlayerPage.PLAYER
    private var storyboardLoadedChapterIndex = -1
    private var storyboardLoadingChapterIndex = -1
    private var storyboardLoadJob: Job? = null
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var storyboardPreviewJob: Job? = null
    private var storyboardPreviewPlayer: ExoPlayer? = null
    private var storyboardLoadingAnimator: ObjectAnimator? = null
    private val storyboardAdapter by lazy { StoryboardAdapter() }
    private val lyricsAdapter by lazy { LyricsAdapter() }

    override fun upBackgroundImage() {
        window.decorView.setBackgroundColor(ThemeConfig.getReadingNgImageSurfaceColor())
        super.upBackgroundImage()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        bindView()
        bindActions()
        setupActionItems()
        refreshStaticState()
        refreshPlayState()
        refreshProgress(ReadBook.durChapterPos)
        consumeAutoStart(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        refreshStaticState()
        refreshPlayState()
        refreshProgress(ReadBook.durChapterPos)
        consumeAutoStart(intent)
    }

    private fun consumeAutoStart(intent: Intent) {
        if (intent.getBooleanExtra(ReadAloudLauncher.EXTRA_AUTO_START, false)) {
            intent.removeExtra(ReadAloudLauncher.EXTRA_AUTO_START)
            if (isCurrentBookAlreadyReading(intent)) {
                if (BaseReadAloudService.pause) {
                    ReadAloud.resume(this)
                }
                setPlayButtonLoading(false)
                refreshStaticState()
                refreshPlayState()
                refreshProgress(ReadBook.durChapterPos)
                return
            }
            startReadAloudAfterContentReady()
        }
    }

    private fun isCurrentBookAlreadyReading(intent: Intent): Boolean {
        if (!BaseReadAloudService.isRun) {
            return false
        }
        val targetBookUrl = intent.getStringExtra(ReadAloudLauncher.EXTRA_BOOK_URL)
            ?: ReadBook.book?.bookUrl
            ?: return false
        return BaseReadAloudService.activeBookUrl == targetBookUrl
    }

    override fun onResume() {
        super.onResume()
        ReadAloudMiniPlayer.detach(this)
        refreshStaticState()
        refreshPlayState()
    }

    private fun bindView() = binding.run {
        progressChapter.progressTintList = ColorStateList.valueOf(accentColor)
        val trackBackgroundTint = ColorStateList.valueOf(ColorUtils.adjustAlpha(accentColor, 0.18f))
        progressChapter.progressBackgroundTintList = trackBackgroundTint
        progressChapter.secondaryProgressTintList = trackBackgroundTint
        progressChapter.thumb = readAloudSeekThumb()
        progressChapter.thumbTintList = null
        progressChapter.thumbOffset = 11.dp
        ivCover.setImageDrawable(null)
        runAfterFirstFrame {
            val book = ReadBook.book
            BookCover.load(
                context = this@ReadAloudPlayerActivity,
                path = book?.getDisplayCover(),
                sourceOrigin = ReadBook.bookSource?.bookSourceUrl
            ).placeholder(ColorDrawable(Color.TRANSPARENT)).into(ivCover)
        }
        recyclerStoryboard.layoutManager = LinearLayoutManager(this@ReadAloudPlayerActivity)
        recyclerStoryboard.adapter = lyricsAdapter
        ivStoryboardLoading.imageTintList = ColorStateList.valueOf(accentColor)
    }

    private fun runAfterFirstFrame(block: () -> Unit) {
        Choreographer.getInstance().postFrameCallback {
            binding.root.post(block)
        }
    }

    private fun bindActions() = binding.run {
        btnClose.setOnClickListener { finish() }
        cardCover.setOnClickListener { openBookInfo() }
        cardSubtitle.setOnClickListener { openOriginal() }
        actionMore.root.setOnClickListener { showMoreSheet() }
        actionTimer.root.setOnClickListener {
            ReadAloudTimerSheet().show(supportFragmentManager, "readAloudTimer")
        }
        actionSpeed.root.setOnClickListener {
            ReadAloudSpeedSheet().show(supportFragmentManager, "readAloudSpeed")
        }
        actionRefresh.root.setOnClickListener { refreshCurrentChapter() }
        actionOriginal.root.setOnClickListener { openOriginal() }
        cardEngine.setOnClickListener { openVoiceOrRoleBindings() }
        actionCatalog.root.setOnClickListener { openChapterList() }
        actionCharacter.root.setOnClickListener {
            ReadAloudModeSheet(this@ReadAloudPlayerActivity).show(
                supportFragmentManager,
                "readAloudMode"
            )
        }
        btnPlay.setOnClickListener { togglePlay() }
        btnPrev.setOnClickListener { ReadAloud.prevChapter(this@ReadAloudPlayerActivity) }
        btnNext.setOnClickListener { ReadAloud.nextChapter(this@ReadAloudPlayerActivity) }
        progressChapter.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar) {
                paragraphSeeking = ensureParagraphCache()
                pendingSeekParagraphIndex = currentParagraphIndex(ReadBook.durChapterPos)
            }

            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser && paragraphSeeking) {
                    previewSeekParagraph(progress)
                }
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                if (!paragraphSeeking) return
                val targetIndex = pendingSeekParagraphIndex
                paragraphSeeking = false
                pendingSeekParagraphIndex = -1
                if (targetIndex == currentParagraphIndex(ReadBook.durChapterPos)) {
                    refreshProgress(ReadBook.durChapterPos)
                } else {
                    seekToParagraph(targetIndex)
                }
            }
        })
    }

    private fun setupActionItems() = binding.run {
        actionTimer.ivIcon.setImageResource(R.drawable.ic_timer_black_24dp)
        actionTimer.tvLabel.text = "定时"
        actionSpeed.ivIcon.setImageResource(R.drawable.ic_read_aloud_speed)
        actionSpeed.tvLabel.text = speedLabel()
        actionRefresh.ivIcon.setImageResource(R.drawable.ic_refresh_black_24dp)
        actionRefresh.tvLabel.text = getString(R.string.refresh)
        actionOriginal.ivIcon.setImageResource(R.drawable.ic_read_aloud_original)
        actionOriginal.tvLabel.text = "原文"
        actionMore.ivIcon.setImageResource(R.drawable.ic_more_horiz)
        actionMore.tvLabel.text = getString(R.string.more)
        actionCharacter.ivIcon.setImageResource(R.drawable.ic_read_aloud_mode_settings)
        actionCharacter.tvLabel.text = "模式"
        actionCatalog.ivIcon.setImageResource(R.drawable.ic_toc)
        actionCatalog.tvLabel.text = getString(R.string.chapter_list)
    }

    private fun refreshStaticState() = binding.run {
        val book = ReadBook.book
        val chapter = ReadBook.curTextChapter
        tvChapter.text = chapter?.title ?: "正在准备朗读"
        tvBook.text = book?.name?.takeIf { it.isNotBlank() } ?: "阅读NG"
        val multiRole = AppConfig.readAloudMultiRole
        actionCharacter.ivIcon.setImageResource(R.drawable.ic_read_aloud_mode_settings)
        actionCharacter.tvLabel.text = "模式"
        actionSpeed.tvLabel.text = speedLabel()
        val engine = runCatching { TtsEngineStore.activeEngine() }.getOrNull()
        val voice = runCatching { engine?.activeVoice()?.name }.getOrNull()
        tvEngine.text = when {
            multiRole -> "多人朗读 · 角色音色"
            engine == null || !engine.enabled -> "选择朗读音色"
            voice.isNullOrBlank() -> "朗读音色 · ${engine.name}"
            else -> "朗读音色 · $voice"
        }
        refreshPlayerPage()
        actionTimer.tvLabel.text = if (BaseReadAloudService.timeMinute > 0) {
            "${BaseReadAloudService.timeMinute}分"
        } else {
            "定时"
        }
    }

    private fun refreshPlayState() = binding.run {
        if (playButtonLoading) return@run
        if (BaseReadAloudService.isPlay()) {
            btnPlay.setImageResource(R.drawable.ic_pause_24dp)
            btnPlay.contentDescription = getString(R.string.pause)
        } else {
            btnPlay.setImageResource(R.drawable.ic_play_24dp)
            btnPlay.contentDescription = getString(R.string.audio_play)
        }
    }

    private fun refreshProgress(progress: Int) {
        if (paragraphSeeking || progress == lastProgress) return
        lastProgress = progress
        val chapter = ReadBook.curTextChapter
        if (chapter?.isCompleted == true) {
            refreshSubtitle(progress)
        }
    }

    private fun refreshSubtitle(progress: Int) {
        if (!ensureParagraphCache()) return
        val index = currentParagraphIndex(progress)
        if (index == lastParagraphIndex) return
        lastParagraphIndex = index
        syncParagraphSeekBar(index)
        syncLyrics(index)
        binding.tvSubtitle.text = cachedParagraphs.getOrNull(index)?.text ?: "正在准备朗读..."
    }

    private fun ensureParagraphCache(): Boolean {
        val chapterIndex = ReadBook.durChapterIndex
        if (chapterIndex == cachedChapterIndex && cachedParagraphs.isNotEmpty()) return true
        val chapter = ReadBook.curTextChapter?.takeIf { it.isCompleted } ?: return false
        cachedChapterIndex = chapterIndex
        lastParagraphIndex = -1
        cachedParagraphs = chapter.getParagraphs(false).mapNotNull { paragraph ->
            val text = paragraph.text
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(160)
            if (text.isBlank()) {
                null
            } else {
                ParagraphSummary(paragraph.chapterIndices, text)
            }
        }
        lyricsAdapter.submitItems(cachedParagraphs)
        updateLyricsHeader()
        syncParagraphSeekBar(0)
        return cachedParagraphs.isNotEmpty()
    }

    private fun currentParagraphIndex(progress: Int): Int {
        if (cachedParagraphs.isEmpty() && !ensureParagraphCache()) return 0
        return cachedParagraphs.indexOfFirst { progress in it.range }.let { exact ->
            if (exact >= 0) exact else cachedParagraphs.indexOfLast { progress >= it.range.first }
        }.coerceIn(0, cachedParagraphs.lastIndex.coerceAtLeast(0))
    }

    private fun syncParagraphSeekBar(index: Int) = binding.progressChapter.run {
        val maxIndex = (cachedParagraphs.size - 1).coerceAtLeast(0)
        if (max != maxIndex) {
            max = maxIndex
            isEnabled = maxIndex > 0
        }
        val safeIndex = index.coerceIn(0, maxIndex)
        if (progress != safeIndex) {
            progress = safeIndex
        }
    }

    private fun updateLyricsPage() {
        val chapter = ReadBook.curTextChapter
        binding.layoutStoryboardLoading.isVisible = false
        binding.recyclerStoryboard.isVisible = true
        binding.tvStoryboardSummary.isVisible = true
        binding.tvStoryboardTitle.text = chapter?.title ?: "原文"
        if (!ensureParagraphCache()) {
            lyricsAdapter.submitItems(emptyList())
            binding.tvStoryboardSummary.text = "暂无当前章节正文"
            return
        }
        val index = currentParagraphIndex(ReadBook.durChapterPos)
        syncLyrics(index)
    }

    private fun updateLyricsHeader() = binding.run {
        tvStoryboardTitle.text = ReadBook.curTextChapter?.title ?: "原文"
        tvStoryboardSummary.text = "当前章节 · ${cachedParagraphs.size} 个段落"
    }

    private fun syncLyrics(index: Int) {
        if (cachedParagraphs.isEmpty()) return
        val safeIndex = index.coerceIn(0, cachedParagraphs.lastIndex)
        lyricsAdapter.setActiveIndex(safeIndex)
        if (playerPage == PlayerPage.STORYBOARD) {
            binding.recyclerStoryboard.post {
                (binding.recyclerStoryboard.layoutManager as? LinearLayoutManager)
                    ?.scrollToPositionWithOffset(safeIndex, 150.dp)
            }
        }
    }

    private fun previewSeekParagraph(index: Int) {
        if (cachedParagraphs.isEmpty()) return
        val safeIndex = index.coerceIn(0, cachedParagraphs.lastIndex)
        pendingSeekParagraphIndex = safeIndex
        lastParagraphIndex = safeIndex
        syncLyrics(safeIndex)
        binding.tvSubtitle.text = cachedParagraphs[safeIndex].text
    }

    private fun seekToParagraph(index: Int) {
        if (!ensureParagraphCache()) return
        val target = cachedParagraphs.getOrNull(index.coerceIn(0, cachedParagraphs.lastIndex)) ?: return
        val chapter = ReadBook.curTextChapter?.takeIf { it.isCompleted } ?: return
        val targetPos = target.range.first.coerceAtLeast(0)
        val targetPage = chapter.getPageIndexByCharIndex(targetPos).coerceAtLeast(0)
        val pageStart = chapter.getReadLength(targetPage)
        val pageStartPos = (targetPos - pageStart).coerceAtLeast(0)
        val wasRun = BaseReadAloudService.isRun
        val wasPlaying = BaseReadAloudService.isRun && !BaseReadAloudService.pause

        ReadBook.durChapterPos = targetPos
        lastProgress = -1
        refreshProgress(targetPos)
        if (!wasRun) return

        switchingParagraph = true
        if (wasPlaying) {
            setPlayButtonLoading(true)
        }
        ReadAloud.stop(this)
        binding.root.postDelayed({
            ReadAloud.play(this, play = wasPlaying, pageIndex = targetPage, startPos = pageStartPos)
            binding.root.postDelayed({
                switchingParagraph = false
                if (!wasPlaying) {
                    setPlayButtonLoading(false)
                }
            }, 1200L)
        }, 180L)
    }

    private fun togglePlay() {
        when {
            !BaseReadAloudService.isRun -> {
                if (!TtsEngineStore.hasEnabledEngine()) {
                    toastOnUi("未启用朗读引擎")
                    return
                }
                startReadAloudAfterContentReady()
            }
            BaseReadAloudService.pause -> {
                setPlayButtonLoading(true)
                ReadAloud.resume(this)
            }
            else -> ReadAloud.pause(this)
        }
    }

    private fun openChapterList() {
        ReadAloudCatalogSheet(this).show()
    }

    private fun refreshCurrentChapter() {
        val book = ReadBook.book ?: run {
            toastOnUi("当前书籍为空")
            return
        }
        val chapterIndex = ReadBook.durChapterIndex
        val chapterPos = ReadBook.durChapterPos
        val wasRun = BaseReadAloudService.isRun
        val wasPlaying = BaseReadAloudService.isRun && !BaseReadAloudService.pause
        switchingChapter = true
        stopStoryboardPreview()
        if (wasRun) {
            setPlayButtonLoading(true)
            ReadAloud.stop(this)
        }
        binding.tvChapter.text = "正在准备朗读"
        cachedChapterIndex = -1
        cachedParagraphs = emptyList()
        storyboardLoadJob?.cancel()
        storyboardLoadingChapterIndex = -1
        storyboardLoadedChapterIndex = -1
        lastParagraphIndex = -1
        lastProgress = -1
        binding.progressChapter.progress = 0
        binding.progressChapter.max = 1
        toastOnUi("正在刷新当前章节")
        lifecycleScope.launch {
            val result = runCatching {
                withContext(IO) {
                    if (ReadBook.bookSource != null) {
                        appDb.bookChapterDao.getChapter(book.bookUrl, chapterIndex)?.let {
                            BookHelp.delContent(book, it)
                        }
                    }
                }
                ReadBook.msg = null
                ReadBook.clearTextChapter()
                ReadBook.removeLoading(chapterIndex)
                ReadBook.durChapterIndex = chapterIndex
                ReadBook.durChapterPos = chapterPos.coerceAtLeast(0)
                ReadBook.saveRead()
                ReadBook.loadContentAwait(chapterIndex, upContent = false, resetPageOffset = false)
            }
            if (!result.isSuccess) {
                toastOnUi("刷新失败：${result.exceptionOrNull()?.localizedMessage ?: "未知错误"}")
                switchingChapter = false
                setPlayButtonLoading(false)
                return@launch
            }
            val chapter = ReadBook.curTextChapter?.takeIf { it.isCompleted }
            if (chapter == null) {
                toastOnUi("刷新失败：章节内容未加载")
                switchingChapter = false
                setPlayButtonLoading(false)
                refreshStaticState()
                return@launch
            }
            val contentLength = chapter.getContent().length
            ReadBook.durChapterPos = ReadBook.durChapterPos.coerceIn(0, contentLength)
            cachedChapterIndex = -1
            cachedParagraphs = emptyList()
            lastParagraphIndex = -1
            lastProgress = -1
            refreshStaticState()
            refreshProgress(ReadBook.durChapterPos)
            if (wasRun) {
                resumeReadAloudAfterReload(wasPlaying)
            } else {
                setPlayButtonLoading(false)
            }
            binding.root.postDelayed({ switchingChapter = false }, 1200L)
        }
    }

    private fun startReadAloudAfterContentReady() {
        if (!TtsEngineStore.hasEnabledEngine()) {
            toastOnUi("未启用朗读引擎")
            return
        }
        setPlayButtonLoading(true)
        lifecycleScope.launch {
            val prepared = ReadAloudLauncher.loadCurrentChapter(this@ReadAloudPlayerActivity)
            if (!prepared) {
                setPlayButtonLoading(false)
                refreshStaticState()
                toastOnUi(ReadBook.msg ?: "加载正文失败")
                return@launch
            }
            cachedChapterIndex = -1
            cachedParagraphs = emptyList()
            lastParagraphIndex = -1
            lastProgress = -1
            refreshStaticState()
            refreshProgress(ReadBook.durChapterPos)
            switchingChapter = true
            ReadAloud.upReadAloudClass()
            ReadBook.readAloud(play = true, startPos = currentPageStartPos())
            binding.root.postDelayed({
                switchingChapter = false
            }, 1200L)
        }
    }

    private fun resumeReadAloudAfterReload(wasPlaying: Boolean) {
        val chapter = ReadBook.curTextChapter?.takeIf { it.isCompleted }
        val targetPage = chapter
            ?.getPageIndexByCharIndex(ReadBook.durChapterPos)
            ?.coerceAtLeast(0)
            ?: ReadBook.durPageIndex
        val pageStart = chapter?.getReadLength(targetPage) ?: 0
        val startPos = (ReadBook.durChapterPos - pageStart).coerceAtLeast(0)
        ReadAloud.play(this, play = wasPlaying, pageIndex = targetPage, startPos = startPos)
        if (!wasPlaying) {
            binding.root.postDelayed({ setPlayButtonLoading(false) }, 1200L)
        }
    }

    fun setMultiRoleEnabled(enabled: Boolean) {
        if (AppConfig.readAloudMultiRole == enabled) return
        val targetMode = if (enabled) 1 else 0
        val wasRun = BaseReadAloudService.isRun
        val wasPlaying = BaseReadAloudService.isRun && !BaseReadAloudService.pause
        val startPos = currentPageStartPos()
        stopStoryboardPreview()
        storyboardLoadJob?.cancel()
        storyboardLoadingChapterIndex = -1
        storyboardLoadedChapterIndex = -1
        AppConfig.readAloudScenarioMode = targetMode
        refreshStaticState()
        if (wasRun) {
            switchingVoice = true
            setPlayButtonLoading(true)
            ReadAloud.stop(this)
            binding.root.postDelayed({
                ReadBook.readAloud(play = wasPlaying, startPos = startPos)
                binding.root.postDelayed({
                    switchingVoice = false
                    if (!wasPlaying) {
                        setPlayButtonLoading(false)
                    }
                }, 1200L)
            }, 180L)
        }
        toastOnUi(
            if (AppConfig.readAloudMultiRole) {
                "已开启多人模式"
            } else {
                "已切换单人模式"
            }
        )
    }

    private fun openVoiceOrRoleBindings() {
        if (AppConfig.readAloudMultiRole) {
            openCharacterTtsBindings()
        } else {
            ReadAloudVoiceSheet(this@ReadAloudPlayerActivity).show()
        }
    }

    private fun refreshPlayerPage() = binding.run {
        pagePlayer.isVisible = playerPage == PlayerPage.PLAYER
        pageStoryboard.isVisible = playerPage == PlayerPage.STORYBOARD
        layoutPageDots.isVisible = true
        dotPlayer.setPageIndicatorSelected(playerPage == PlayerPage.PLAYER)
        dotStoryboard.setPageIndicatorSelected(playerPage == PlayerPage.STORYBOARD)
        if (playerPage == PlayerPage.STORYBOARD) {
            updateLyricsPage()
        }
    }

    private fun android.view.View.setPageIndicatorSelected(selected: Boolean) {
        layoutParams = layoutParams.apply {
            width = if (selected) 18.dp else 5.dp
            height = if (selected) 4.dp else 5.dp
        }
        setBackgroundResource(
            if (selected) {
                R.drawable.ng_bg_read_aloud_page_line
            } else {
                R.drawable.ng_bg_read_aloud_page_dot
            }
        )
        alpha = if (selected) 1f else 0.35f
    }

    private fun accentCircleBackground(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ColorUtils.withAlpha(accentColor, 0.12f))
        }
    }

    private fun readAloudSeekThumb(): LayerDrawable {
        val outer = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.WHITE)
            setStroke(1.dp, ColorUtils.withAlpha(accentColor, 0.18f))
            setSize(22.dp, 22.dp)
        }
        val inner = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(accentColor)
            setSize(14.dp, 14.dp)
        }
        return LayerDrawable(arrayOf(outer, inner)).apply {
            setLayerInset(1, 4.dp, 4.dp, 4.dp, 4.dp)
        }
    }

    private fun showPage(page: PlayerPage) {
        if (playerPage == page) return
        playerPage = page
        refreshPlayerPage()
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density + 0.5f).toInt()

    private fun loadStoryboard() {
        val chapter = ReadBook.curTextChapter
        val chapterIndex = ReadBook.durChapterIndex
        if (chapter == null) {
            renderEmptyStoryboard()
            return
        }
        if (storyboardLoadedChapterIndex == chapterIndex && storyboardAdapter.itemCount > 0) {
            return
        }
        if (storyboardLoadingChapterIndex == chapterIndex && storyboardLoadJob?.isActive == true) {
            return
        }
        val content = AiTtsStoryboardHelper.readAloudContentFromChapter(chapter)
        if (content.isBlank()) {
            renderEmptyStoryboard()
            return
        }
        storyboardLoadedChapterIndex = chapterIndex
        storyboardLoadingChapterIndex = chapterIndex
        binding.tvStoryboardTitle.text = chapter.title
        setStoryboardLoading(true, "正在生成 AI 分镜…")
        storyboardAdapter.submitRows(emptyList())
        storyboardLoadJob?.cancel()
        storyboardLoadJob = lifecycleScope.launch {
            val result = withContext(IO) {
                val book = ReadBook.book
                    ?: return@withContext Result.failure<ChapterStoryboard>(
                        IllegalStateException("当前书籍为空")
                    )
                val workKey = BookCharacterProfile.workKey(book.name, book.author)
                val characters = if (workKey.isBlank()) {
                    emptyList()
                } else {
                    appDb.bookCharacterDao.getCharacters(workKey)
                }
                runCatching {
                    AiTtsStoryboardHelper.getOrGenerate(
                        book = book,
                        chapterIndex = chapterIndex,
                        chapterTitle = chapter.title,
                        content = content,
                        characters = characters
                    )
                }
            }
            result
                .onSuccess { renderStoryboard(it) }
                .onFailure {
                    setStoryboardLoading(false)
                    binding.tvStoryboardSummary.text = "AI 分镜生成失败：${it.localizedMessage ?: "未知错误"}"
                    storyboardAdapter.submitRows(emptyList())
                }
            if (storyboardLoadingChapterIndex == chapterIndex) {
                storyboardLoadingChapterIndex = -1
            }
        }
    }

    private fun renderEmptyStoryboard() = binding.run {
        setStoryboardLoading(false)
        tvStoryboardTitle.text = "分镜"
        tvStoryboardSummary.text = "暂无可分析的当前章节"
        storyboardAdapter.submitRows(emptyList())
    }

    private fun renderStoryboard(storyboard: ChapterStoryboard) = binding.run {
        setStoryboardLoading(false)
        val rows = storyboard.scenes.flatMap { scene ->
            val segments = scene.segments.filterNot { it.isChapterTitleSegment(storyboard.chapterTitle) }
            listOf(StoryboardRow.Scene(scene)) + segments.map { StoryboardRow.Segment(it) }
        }
        val visibleSegments = rows.count { it is StoryboardRow.Segment }
        val visibleDialogueCount = rows.count {
            it is StoryboardRow.Segment && it.segment.type == StoryboardSegmentType.DIALOGUE
        }
        val visibleThoughtCount = rows.count {
            it is StoryboardRow.Segment && it.segment.type == StoryboardSegmentType.THOUGHT
        }
        tvStoryboardTitle.text = storyboard.chapterTitle
        tvStoryboardSummary.text = "${storyboard.scenes.size} 个分镜 · $visibleSegments 个片段 · 对白 $visibleDialogueCount · 心声 $visibleThoughtCount"
        storyboardAdapter.submitRows(rows)
    }

    private fun setStoryboardLoading(loading: Boolean, message: String = "") = binding.run {
        layoutStoryboardLoading.isVisible = loading
        recyclerStoryboard.isVisible = !loading
        tvStoryboardSummary.isVisible = !loading
        if (loading) {
            tvStoryboardLoading.text = message
            startStoryboardLoadingAnimation()
        } else {
            stopStoryboardLoadingAnimation()
        }
    }

    private fun startStoryboardLoadingAnimation() {
        val icon = binding.ivStoryboardLoading
        if (storyboardLoadingAnimator?.isStarted == true) return
        icon.rotation = 0f
        storyboardLoadingAnimator = ObjectAnimator.ofFloat(icon, "rotation", 0f, 360f).apply {
            duration = 750L
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            start()
        }
    }

    private fun stopStoryboardLoadingAnimation() = binding.run {
        storyboardLoadingAnimator?.cancel()
        storyboardLoadingAnimator = null
        ivStoryboardLoading.rotation = 0f
    }

    private fun StoryboardSegment.isChapterTitleSegment(chapterTitle: String): Boolean {
        if (type != StoryboardSegmentType.NARRATION || paragraphIndex != 0) {
            return false
        }
        val normalizedTitle = chapterTitle.normalizedStoryboardTitle()
        return normalizedTitle.isNotBlank() && text.normalizedStoryboardTitle() == normalizedTitle
    }

    private fun String.normalizedStoryboardTitle(): String {
        return filterNot { it.isWhitespace() || it == '\u3000' }
    }

    private fun previewStoryboardSegment(segment: StoryboardSegment) {
        stopStoryboardPreview()
        val text = segment.text.trim()
        if (text.isBlank()) {
            toastOnUi("片段内容为空")
            return
        }
        val baseEngine = (ReadAloud.httpTtsEngineV2 ?: runCatching { TtsEngineStore.activeEngine() }.getOrNull())
            ?.takeIf { it.enabled && it.type == TtsEngineType.SCRIPT }
        if (baseEngine == null) {
            toastOnUi("当前朗读引擎不支持片段试听")
            return
        }
        toastOnUi("正在合成片段试听...")
        storyboardPreviewJob?.cancel()
        storyboardPreviewJob = lifecycleScope.launch {
            val result = runCatching {
                withContext(IO) {
                    val router = ReadBook.book?.let { ReadAloudTtsRouter.create(it) }
                    val route = router?.route(segment, baseEngine)
                    val engine = (route?.engine ?: baseEngine)
                        .takeIf { it.enabled && it.type == TtsEngineType.SCRIPT }
                        ?: error("角色绑定的朗读引擎不可用")
                    val response = TtsScriptEngineClient.getSynthesisResponse(
                        engine = engine,
                        text = text,
                        voiceId = route?.voiceId ?: engine.activeVoiceId,
                        styleId = route?.styleId
                    )
                    File(cacheDir, "storyboard_preview_${System.currentTimeMillis()}.audio").apply {
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
                storyboardPreviewPlayer?.release()
                storyboardPreviewPlayer = ExoPlayer.Builder(this@ReadAloudPlayerActivity).build().apply {
                    setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                    prepare()
                    play()
                }
            }.onFailure {
                if (it !is CancellationException) {
                    toastOnUi("片段试听失败：${it.localizedMessage ?: it.javaClass.simpleName}")
                }
            }
        }
    }

    fun stopStoryboardPreview() {
        storyboardPreviewJob?.cancel()
        storyboardPreviewJob = null
        storyboardPreviewPlayer?.release()
        storyboardPreviewPlayer = null
    }

    fun openChapterFromCatalog(chapterIndex: Int) {
        if (chapterIndex == ReadBook.durChapterIndex) return
        val wasPlaying = BaseReadAloudService.isRun && !BaseReadAloudService.pause
        switchingChapter = true
        if (BaseReadAloudService.isRun) {
            ReadAloud.stop(this)
        }
        cachedChapterIndex = -1
        cachedParagraphs = emptyList()
        storyboardLoadJob?.cancel()
        storyboardLoadingChapterIndex = -1
        storyboardLoadedChapterIndex = -1
        lastParagraphIndex = -1
        lastProgress = -1
        binding.progressChapter.progress = 0
        binding.progressChapter.max = 1
        ReadBook.openChapter(chapterIndex, durChapterPos = 0, upContent = false) {
            refreshStaticState()
            refreshProgress(0)
            ReadBook.readAloud(wasPlaying, startPos = 0)
            binding.root.postDelayed({ switchingChapter = false }, 800L)
        }
    }

    fun runVoiceSwitch(block: () -> Unit) {
        switchingVoice = true
        setPlayButtonLoading(true)
        try {
            block()
        } finally {
            binding.root.postDelayed({ switchingVoice = false }, 1200L)
        }
    }

    fun currentPageStartPos(): Int {
        val pageStart = ReadBook.curTextChapter
            ?.takeIf { it.isCompleted }
            ?.getReadLength(ReadBook.durPageIndex)
            ?: 0
        return (ReadBook.durChapterPos - pageStart).coerceAtLeast(0)
    }

    private fun setPlayButtonLoading(loading: Boolean) = binding.run {
        if (playButtonLoading == loading) {
            if (!loading) {
                refreshPlayState()
            }
            return@run
        }
        playButtonLoading = loading
        if (loading) {
            btnPlay.setImageResource(R.drawable.avd_read_aloud_loading_bars)
            btnPlay.contentDescription = "正在准备朗读"
            (btnPlay.drawable as? Animatable)?.start()
        } else {
            (btnPlay.drawable as? Animatable)?.stop()
            refreshPlayState()
        }
    }

    fun openOriginal() {
        ReadBook.book?.let {
            startActivity<io.legado.app.ui.book.read.ReadBookActivity> {
                putExtra("bookUrl", it.bookUrl)
            }
        }
        finish()
    }

    private fun openBookInfo() {
        ReadBook.book?.let {
            startActivity<io.legado.app.ui.book.info.BookInfoActivity> {
                ReadAloudLauncher.markPlayerDerived(this)
                putExtra("name", it.name)
                putExtra("author", it.author)
                putExtra("bookUrl", it.bookUrl)
            }
        }
    }

    private fun openCharacterTtsBindings() {
        ReadBook.book?.let {
            startActivity<BookCharacterTtsActivity> {
                ReadAloudLauncher.markPlayerDerived(this)
                putExtra(BookCharacterActivity.EXTRA_WORK_KEY, BookCharacterProfile.workKey(it.name, it.author))
                putExtra(BookCharacterActivity.EXTRA_BOOK_NAME, it.name)
                putExtra(BookCharacterActivity.EXTRA_BOOK_AUTHOR, it.author)
                putExtra(BookCharacterActivity.EXTRA_BOOK_URL, it.bookUrl)
            }
        }
    }

    fun openStoryboardResult() {
        ReadBook.book?.let {
            startActivity<BookStoryboardActivity> {
                ReadAloudLauncher.markPlayerDerived(this)
                putExtra(BookCharacterActivity.EXTRA_WORK_KEY, BookCharacterProfile.workKey(it.name, it.author))
                putExtra(BookCharacterActivity.EXTRA_BOOK_NAME, it.name)
                putExtra(BookCharacterActivity.EXTRA_BOOK_AUTHOR, it.author)
                putExtra(BookCharacterActivity.EXTRA_BOOK_URL, it.bookUrl)
            }
        } ?: toastOnUi("当前书籍为空")
    }

    fun openEngineConfig() {
        startActivity<ConfigActivity> {
            ReadAloudLauncher.markPlayerDerived(this)
            putExtra("configTag", ConfigTag.TTS_ENGINE_CONFIG)
        }
    }

    private fun showMoreSheet() {
        ReadAloudMoreSheet().show(supportFragmentManager, "readAloudMore")
    }

    override fun observeLiveBus() {
        observeEvent<Int>(EventBus.ALOUD_STATE) {
            when (it) {
                Status.LOADING -> setPlayButtonLoading(true)
                Status.PAUSE, Status.STOP -> setPlayButtonLoading(false)
                Status.PLAY -> setPlayButtonLoading(false)
            }
            refreshStaticState()
            if (it == Status.STOP && !switchingChapter && !switchingVoice && !switchingParagraph) {
                finish()
            }
        }
        observeEvent<Int>(EventBus.TTS_PROGRESS) {
            setPlayButtonLoading(false)
            refreshProgress(it)
        }
        observeEvent<Int>(EventBus.READ_ALOUD_DS) {
            refreshStaticState()
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (!paragraphSeeking) {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchDownX = ev.x
                    touchDownY = ev.y
                }
                MotionEvent.ACTION_UP -> {
                    val dx = ev.x - touchDownX
                    val dy = ev.y - touchDownY
                    if (kotlin.math.abs(dx) > 120.dp && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.4f) {
                        if (dx < 0) {
                            showPage(PlayerPage.STORYBOARD)
                        } else {
                            showPage(PlayerPage.PLAYER)
                        }
                        return true
                    }
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun speedLabel(): String {
        return "${(AppConfig.ttsSpeechRate + 5) / 10f}x"
    }

    private data class ParagraphSummary(
        val range: IntRange,
        val text: String
    )

    private enum class PlayerPage {
        PLAYER,
        STORYBOARD
    }

    private sealed interface StoryboardRow {
        data class Scene(val scene: StoryboardScene) : StoryboardRow
        data class Segment(val segment: StoryboardSegment) : StoryboardRow
    }

    private inner class LyricsAdapter : RecyclerView.Adapter<LyricsAdapter.LyricHolder>() {

        private val items = mutableListOf<ParagraphSummary>()
        private var activeIndex = -1

        fun submitItems(newItems: List<ParagraphSummary>) {
            items.clear()
            items.addAll(newItems)
            activeIndex = activeIndex.coerceIn(-1, items.lastIndex)
            notifyDataSetChanged()
        }

        fun setActiveIndex(index: Int) {
            if (activeIndex == index) return
            val oldIndex = activeIndex
            activeIndex = index
            if (oldIndex in items.indices) {
                notifyItemChanged(oldIndex)
            }
            if (index in items.indices) {
                notifyItemChanged(index)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LyricHolder {
            return LyricHolder(TextView(parent.context))
        }

        override fun onBindViewHolder(holder: LyricHolder, position: Int) {
            holder.bind(items[position], active = position == activeIndex)
        }

        override fun getItemCount(): Int = items.size

        inner class LyricHolder(
            private val textView: TextView
        ) : RecyclerView.ViewHolder(textView) {

            fun bind(item: ParagraphSummary, active: Boolean) {
                textView.text = item.text
                textView.gravity = Gravity.CENTER
                textView.includeFontPadding = false
                textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (active) 23f else 22f)
                textView.typeface = if (active) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                textView.setTextColor(
                    if (active) {
                        ContextCompat.getColor(this@ReadAloudPlayerActivity, R.color.ng_on_surface)
                    } else {
                        ColorUtils.withAlpha(
                            ContextCompat.getColor(
                                this@ReadAloudPlayerActivity,
                                R.color.ng_on_surface
                            ),
                            0.38f
                        )
                    }
                )
                textView.layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 14.dp
                    bottomMargin = 14.dp
                }
            }
        }
    }

    private inner class StoryboardAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val rows = mutableListOf<StoryboardRow>()

        fun submitRows(newRows: List<StoryboardRow>) {
            rows.clear()
            rows.addAll(newRows)
            notifyDataSetChanged()
        }

        override fun getItemViewType(position: Int): Int {
            return when (rows[position]) {
                is StoryboardRow.Scene -> 0
                is StoryboardRow.Segment -> 1
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == 0) {
                SceneHolder(ItemBookStoryboardSceneBinding.inflate(inflater, parent, false))
            } else {
                SegmentHolder(ItemBookStoryboardSegmentBinding.inflate(inflater, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val row = rows[position]) {
                is StoryboardRow.Scene -> (holder as SceneHolder).bind(row.scene)
                is StoryboardRow.Segment -> (holder as SegmentHolder).bind(row.segment)
            }
        }

        override fun getItemCount(): Int = rows.size
    }

    private inner class SceneHolder(
        private val binding: ItemBookStoryboardSceneBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(scene: StoryboardScene) = binding.run {
            ivVisual.background = accentCircleBackground()
            ivVisual.imageTintList = ColorStateList.valueOf(accentColor)
            tvTitle.text = scene.title.toSceneTitle()
            tvSummary.text = scene.characters.joinToString("、").ifBlank { scene.summary }
            tvMeta.text = buildList {
                add("旁白 ${scene.narrationCount}")
                add("对白 ${scene.dialogueCount}")
                if (scene.thoughtCount > 0) {
                    add("心声 ${scene.thoughtCount}")
                }
            }.joinToString(" · ")
        }

        private fun String.toSceneTitle(): String {
            val number = Regex("""分镜\s*(\d+)""").find(this)?.groupValues?.getOrNull(1)
            return number?.let { "分镜 $it" } ?: this.substringBefore("·").trim()
        }
    }

    private inner class SegmentHolder(
        private val binding: ItemBookStoryboardSegmentBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(segment: StoryboardSegment) = binding.run {
            val identity = when (segment.type) {
                StoryboardSegmentType.NARRATION -> "旁白"
                StoryboardSegmentType.DIALOGUE -> segment.speakerName ?: segment.virtualSpeakerName()
                StoryboardSegmentType.THOUGHT -> segment.speakerName ?: segment.virtualSpeakerName()
            }
            val evidence = segment.evidence.trim()
            tvType.text = identity
            tvType.setTextColor(accentColor)
            tvSpeaker.text = "· 第 ${segment.paragraphIndex + 1} 段"
            tvText.text = segment.text
            tvEvidence.text = evidence
            tvEvidence.isVisible = evidence.isNotBlank() && evidence != identity
            btnPreview.background = accentCircleBackground()
            btnPreview.imageTintList = ColorStateList.valueOf(accentColor)
            btnPreview.setOnClickListener { previewStoryboardSegment(segment) }
        }
    }

    private fun StoryboardSegment.virtualSpeakerName(): String {
        return when (speakerGender) {
            StoryboardSegment.SpeakerGender.MALE -> "对白男"
            StoryboardSegment.SpeakerGender.FEMALE -> "对白女"
            else -> if (type == StoryboardSegmentType.THOUGHT) "心声" else "待确认说话人"
        }
    }

    override fun onDestroy() {
        stopStoryboardPreview()
        stopStoryboardLoadingAnimation()
        super.onDestroy()
    }
}
