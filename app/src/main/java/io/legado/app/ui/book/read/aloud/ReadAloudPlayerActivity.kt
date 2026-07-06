package io.legado.app.ui.book.read.aloud

import android.graphics.drawable.Animatable
import android.content.res.ColorStateList
import android.os.Bundle
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.constant.EventBus
import io.legado.app.constant.Status
import io.legado.app.databinding.ActivityReadAloudPlayerBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.help.tts.TtsEngineStore
import io.legado.app.lib.theme.accentColor
import io.legado.app.model.BookCover
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.config.ConfigActivity
import io.legado.app.ui.config.ConfigTag
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.observeEvent
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding

class ReadAloudPlayerActivity : BaseActivity<ActivityReadAloudPlayerBinding>(
    imageBg = true,
    showOpenMenuIcon = false
) {

    override val binding by viewBinding(ActivityReadAloudPlayerBinding::inflate)
    private var lastProgress = -1
    private var cachedChapterIndex = -1
    private var cachedParagraphs = emptyList<ParagraphSummary>()
    private var lastParagraphIndex = -1
    private var lastPercent = -1
    private var switchingChapter = false
    private var switchingVoice = false
    private var playButtonLoading = false

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        bindView()
        bindActions()
        setupActionItems()
        refreshStaticState()
        refreshPlayState()
        refreshProgress(ReadBook.durChapterPos)
    }

    override fun onResume() {
        super.onResume()
        ReadAloudMiniPlayer.detach(this)
        refreshStaticState()
        refreshPlayState()
    }

    private fun bindView() = binding.run {
        progressChapter.progressTintList = ColorStateList.valueOf(accentColor)
        progressChapter.progressBackgroundTintList =
            ColorStateList.valueOf(ColorUtils.withAlpha(accentColor, 0.2f))
        val book = ReadBook.book
        BookCover.load(
            context = this@ReadAloudPlayerActivity,
            path = book?.getDisplayCover(),
            sourceOrigin = ReadBook.bookSource?.bookSourceUrl
        ).into(ivCover)
    }

    private fun bindActions() = binding.run {
        btnClose.setOnClickListener { finish() }
        cardSubtitle.setOnClickListener { openOriginal() }
        actionMore.root.setOnClickListener { showMoreSheet() }
        actionTimer.root.setOnClickListener {
            ReadAloudTimerSheet().show(supportFragmentManager, "readAloudTimer")
        }
        actionSpeed.root.setOnClickListener {
            ReadAloudSpeedSheet().show(supportFragmentManager, "readAloudSpeed")
        }
        actionOriginal.root.setOnClickListener { openOriginal() }
        cardEngine.setOnClickListener {
            ReadAloudVoiceSheet(this@ReadAloudPlayerActivity).show()
        }
        actionCatalog.root.setOnClickListener { openChapterList() }
        actionBookmark.root.setOnClickListener { openBookInfo() }
        btnPlay.setOnClickListener { togglePlay() }
        btnPrevParagraph.setOnClickListener { ReadAloud.prevParagraph(this@ReadAloudPlayerActivity) }
        btnNextParagraph.setOnClickListener { ReadAloud.nextParagraph(this@ReadAloudPlayerActivity) }
        btnPrev.setOnClickListener { ReadAloud.prevChapter(this@ReadAloudPlayerActivity) }
        btnNext.setOnClickListener { ReadAloud.nextChapter(this@ReadAloudPlayerActivity) }
    }

    private fun setupActionItems() = binding.run {
        actionTimer.ivIcon.setImageResource(R.drawable.ic_timer_black_24dp)
        actionTimer.tvLabel.text = "定时"
        actionSpeed.ivIcon.setImageResource(R.drawable.ic_read_aloud_speed)
        actionSpeed.tvLabel.text = speedLabel()
        actionOriginal.ivIcon.setImageResource(R.drawable.ic_read_aloud_original)
        actionOriginal.tvLabel.text = "原文"
        actionMore.ivIcon.setImageResource(R.drawable.ic_more_horiz)
        actionMore.tvLabel.text = getString(R.string.more)
        actionBookmark.ivIcon.setImageResource(R.drawable.ic_read_aloud_detail)
        actionBookmark.tvLabel.text = "详情"
        actionCatalog.ivIcon.setImageResource(R.drawable.ic_toc)
        actionCatalog.tvLabel.text = getString(R.string.chapter_list)
    }

    private fun refreshStaticState() = binding.run {
        val book = ReadBook.book
        val chapter = ReadBook.curTextChapter
        tvChapter.text = chapter?.title ?: "正在准备朗读"
        tvBook.text = listOfNotNull(book?.name, book?.author)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
            .ifBlank { "阅读NG" }
        actionSpeed.tvLabel.text = speedLabel()
        val engine = runCatching { TtsEngineStore.activeEngine() }.getOrNull()
        val voice = runCatching { engine?.activeVoice()?.name }.getOrNull()
        tvEngine.text = when {
            engine == null || !engine.enabled -> "选择朗读音色"
            voice.isNullOrBlank() -> "朗读音色 · ${engine.name}"
            else -> "朗读音色 · $voice"
        }
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
        if (progress == lastProgress) return
        lastProgress = progress
        val chapter = ReadBook.curTextChapter
        if (chapter?.isCompleted == true) {
            val total = chapter.getContent().length.coerceAtLeast(1)
            val percent = ((progress.coerceAtLeast(0) * 100f) / total).toInt().coerceIn(0, 100)
            if (percent != lastPercent) {
                lastPercent = percent
                binding.progressChapter.progress = percent
            }
            refreshSubtitle(progress)
        }
    }

    private fun refreshSubtitle(progress: Int) {
        if (!ensureParagraphCache()) return
        val index = cachedParagraphs.indexOfFirst { progress in it.range }.let { exact ->
            if (exact >= 0) exact else cachedParagraphs.indexOfLast { progress >= it.range.first }
        }.coerceAtLeast(0)
        if (index == lastParagraphIndex) return
        lastParagraphIndex = index
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
        return cachedParagraphs.isNotEmpty()
    }

    private fun togglePlay() {
        when {
            !BaseReadAloudService.isRun -> {
                if (!TtsEngineStore.hasEnabledEngine()) {
                    toastOnUi("未启用朗读引擎")
                    return
                }
                setPlayButtonLoading(true)
                ReadBook.readAloud(play = true, startPos = currentPageStartPos())
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

    fun openChapterFromCatalog(chapterIndex: Int) {
        if (chapterIndex == ReadBook.durChapterIndex) return
        val wasPlaying = BaseReadAloudService.isRun && !BaseReadAloudService.pause
        switchingChapter = true
        if (BaseReadAloudService.isRun) {
            ReadAloud.stop(this)
        }
        cachedChapterIndex = -1
        cachedParagraphs = emptyList()
        lastParagraphIndex = -1
        lastProgress = -1
        lastPercent = -1
        binding.progressChapter.progress = 0
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
                putExtra("name", it.name)
                putExtra("author", it.author)
                putExtra("bookUrl", it.bookUrl)
            }
        }
    }

    fun openEngineConfig() {
        startActivity<ConfigActivity> {
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
                Status.PLAY -> if (!playButtonLoading) refreshPlayState()
            }
            refreshStaticState()
            if (it == Status.STOP && !switchingChapter && !switchingVoice) {
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

    private fun speedLabel(): String {
        return "${(AppConfig.ttsSpeechRate + 5) / 10f}x"
    }

    private data class ParagraphSummary(
        val range: IntRange,
        val text: String
    )
}
