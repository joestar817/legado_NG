package io.legado.app.ui.book.read.aloud

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookChapter
import io.legado.app.databinding.DialogReadAloudMoreSheetBinding
import io.legado.app.databinding.DialogReadAloudSpeedSheetBinding
import io.legado.app.databinding.DialogReadAloudTimerSheetBinding
import io.legado.app.databinding.ItemTtsVoiceBinding
import io.legado.app.help.IntentHelp
import io.legado.app.help.config.AppConfig
import io.legado.app.help.tts.TtsEngineSetting
import io.legado.app.help.tts.TtsEngineStore
import io.legado.app.help.tts.TtsEngineType
import io.legado.app.help.tts.TtsVoice
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.view.ThemeSwitch
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.config.ConfigActivity
import io.legado.app.ui.config.ConfigTag
import io.legado.app.ui.config.TtsVoiceCardBinder
import io.legado.app.ui.config.TtsVoicePreviewController
import io.legado.app.ui.widget.dialog.NgLongListBottomSheet
import io.legado.app.ui.widget.seekbar.SeekBarChangeListener
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.postEvent
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.startActivity
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

abstract class ReadAloudBottomSheet(layoutId: Int) : BaseDialogFragment(layoutId) {
    override fun onStart() {
        super.onStart()
        view?.setBackgroundResource(R.drawable.ng_bg_read_aloud_sheet)
        dialog?.window?.run {
            setBackgroundDrawableResource(R.color.transparent)
            decorView.setPadding(0, 0, 0, 0)
            val attr = attributes
            attr.dimAmount = 0.18f
            attr.gravity = Gravity.BOTTOM
            attributes = attr
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }
}

class ReadAloudTimerSheet : ReadAloudBottomSheet(R.layout.dialog_read_aloud_timer_sheet) {
    private val binding by viewBinding(DialogReadAloudTimerSheetBinding::bind)

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) = binding.run {
        seekTimer.applyReadAloudSliderStyle()
        val activeMinute = BaseReadAloudService.timeMinute
        val initialMinute = if (activeMinute > 0) {
            activeMinute
        } else {
            AppConfig.ttsTimer
        }.coerceIn(0, seekTimer.max)
        seekTimer.progress = initialMinute
        upStateText(initialMinute, applied = activeMinute > 0)
        seekTimer.setOnSeekBarChangeListener(object : SeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                upStateText(progress, applied = false)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                val safeContext = this@ReadAloudTimerSheet.context ?: return
                val minute = seekBar.progress.coerceIn(0, seekBar.max)
                AppConfig.ttsTimer = minute
                ReadAloud.setTimer(safeContext, minute)
                upStateText(minute, applied = true)
            }
        })
    }

    private fun upStateText(minute: Int, applied: Boolean) {
        binding.tvTimerState.text = when {
            minute <= 0 -> "未开启定时"
            applied -> "当前定时 $minute 分钟"
            else -> "拖动设置 $minute 分钟"
        }
    }
}

class ReadAloudSpeedSheet : ReadAloudBottomSheet(R.layout.dialog_read_aloud_speed_sheet) {
    private val binding by viewBinding(DialogReadAloudSpeedSheetBinding::bind)

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) = binding.run {
        seekSpeed.applyReadAloudSliderStyle()
        seekSpeed.progress = AppConfig.ttsSpeechRate.coerceIn(0, seekSpeed.max)
        upTitle(seekSpeed.progress)
        seekSpeed.setOnSeekBarChangeListener(object : SeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                upTitle(progress)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                val safeContext = this@ReadAloudSpeedSheet.context ?: return
                AppConfig.ttsFlowSys = false
                AppConfig.ttsSpeechRate = seekBar.progress
                ReadAloud.upTtsSpeechRate(safeContext)
                if (BaseReadAloudService.isPlay()) {
                    ReadAloud.pause(safeContext)
                    ReadAloud.resume(safeContext)
                }
            }
        })
    }

    private fun upTitle(progress: Int) {
        binding.tvTitle.text = "语速 ${(progress + 5) / 10f}x"
    }
}

private fun SeekBar.applyReadAloudSliderStyle() {
    val accent = context.accentColor
    val trackBackgroundTint = ColorStateList.valueOf(ColorUtils.adjustAlpha(accent, 0.18f))
    progressDrawable = ContextCompat.getDrawable(context, R.drawable.ng_read_aloud_progress)?.mutate()
    progressTintList = ColorStateList.valueOf(accent)
    progressBackgroundTintList = trackBackgroundTint
    secondaryProgressTintList = trackBackgroundTint
    thumb = readAloudSheetSeekThumb(context, accent)
    thumbTintList = null
    thumbOffset = 11.dpToPx()
    splitTrack = false
}

private fun readAloudSheetSeekThumb(context: Context, accent: Int): LayerDrawable {
    val outerSize = 22.dpToPx()
    val innerInset = 4.dpToPx()
    val outer = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(Color.WHITE)
        setStroke(1.dpToPx(), ColorUtils.withAlpha(accent, 0.18f))
        setSize(outerSize, outerSize)
    }
    val inner = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(accent)
        setSize(14.dpToPx(), 14.dpToPx())
    }
    return LayerDrawable(arrayOf(outer, inner)).apply {
        setLayerInset(1, innerInset, innerInset, innerInset, innerInset)
    }
}

class ReadAloudMoreSheet : ReadAloudBottomSheet(R.layout.dialog_read_aloud_more_sheet) {
    private val binding by viewBinding(DialogReadAloudMoreSheetBinding::bind)

    override fun onStart() {
        super.onStart()
        val height = (resources.displayMetrics.heightPixels * 0.86f).toInt()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, height)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) = binding.run {
        tvEngineSummary.text = TtsEngineStore.activeEngine().name
        bindSwitch(itemIgnoreAudioFocus, switchIgnoreAudioFocus, PreferKey.ignoreAudioFocus) {
            syncPauseOnCallState()
        }
        bindSwitch(itemPauseOnCall, switchPauseOnCall, PreferKey.pauseReadAloudWhilePhoneCalls)
        bindSwitch(itemWakeLock, switchWakeLock, PreferKey.readAloudWakeLock)
        bindSwitch(itemMediaButtonPerNext, switchMediaButtonPerNext, "mediaButtonPerNext")
        bindSwitch(itemReadByPage, switchReadByPage, PreferKey.readAloudByPage) {
            notifyReadAloudRuntimeChanged()
        }
        bindSwitch(itemSkipChapterTitle, switchSkipChapterTitle, PreferKey.skipReadAloudChapterTitle) {
            notifyReadAloudRuntimeChanged()
        }
        bindSwitch(itemStreamAudio, switchStreamAudio, PreferKey.streamReadAloudAudio) {
            notifyReadAloudRuntimeChanged()
        }
        syncPauseOnCallState()
        itemEngine.setOnClickListener {
            val safeContext = context ?: return@setOnClickListener
            safeContext.startActivity<ConfigActivity> {
                putExtra("configTag", ConfigTag.TTS_ENGINE_CONFIG)
            }
            dismissAllowingStateLoss()
        }
        itemSystemTts.setOnClickListener { IntentHelp.openTTSSetting() }
        itemStop.setOnClickListener {
            val safeContext = context ?: return@setOnClickListener
            ReadAloud.stop(safeContext)
            dismissAllowingStateLoss()
            activity?.finish()
        }
    }

    private fun bindSwitch(
        row: View,
        switch: ThemeSwitch,
        key: String,
        defaultValue: Boolean = false,
        afterChanged: () -> Unit = {}
    ) {
        val safeContext = row.context
        switch.isChecked = safeContext.getPrefBoolean(key, defaultValue)
        switch.setOnCheckedChangeListener { _, isChecked ->
            safeContext.putPrefBoolean(key, isChecked)
            afterChanged()
        }
        row.setOnClickListener {
            if (row.isEnabled && switch.isEnabled) {
                switch.isChecked = !switch.isChecked
            }
        }
    }

    private fun syncPauseOnCallState() = binding.run {
        val enabled = itemPauseOnCall.context.getPrefBoolean(PreferKey.ignoreAudioFocus, false)
        itemPauseOnCall.isEnabled = enabled
        switchPauseOnCall.isEnabled = enabled
        itemPauseOnCall.alpha = if (enabled) 1f else 0.42f
    }

    private fun notifyReadAloudRuntimeChanged() {
        if (BaseReadAloudService.isRun) {
            postEvent(EventBus.MEDIA_BUTTON, false)
        }
    }
}

class ReadAloudCatalogSheet(
    private val activity: ReadAloudPlayerActivity
) {
    private lateinit var sheet: NgLongListBottomSheet
    private lateinit var recyclerChapters: RecyclerView
    private lateinit var textSummary: TextView
    private lateinit var textEmpty: TextView
    private val adapter by lazy {
        ReadAloudCatalogAdapter(
            context = activity,
            currentIndex = { ReadBook.durChapterIndex },
            onSelect = ::selectChapter
        )
    }

    fun show() {
        sheet = NgLongListBottomSheet(
            context = activity,
            searchHint = "搜索章节",
            title = "目录",
            showSearch = false
        )
        textSummary = TextView(activity).apply {
            setTextColor(ContextCompat.getColor(activity, R.color.ng_on_surface_variant))
            textSize = 15f
            includeFontPadding = false
            setPadding(0, 2.dpToPx(), 0, 12.dpToPx())
        }
        recyclerChapters = RecyclerView(activity).apply {
            layoutManager = LinearLayoutManager(activity)
            adapter = this@ReadAloudCatalogSheet.adapter
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setPadding(0, 0, 0, 8.dpToPx())
        }
        textEmpty = TextView(activity).apply {
            text = activity.getString(R.string.chapter_list_empty)
            setTextColor(ContextCompat.getColor(activity, R.color.ng_on_surface_variant))
            textSize = 15f
            gravity = Gravity.CENTER
            isVisible = false
        }
        val content = FrameLayout(activity).apply {
            addView(
                LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(
                        textSummary,
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    )
                    addView(
                        recyclerChapters,
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            0,
                            1f
                        )
                    )
                },
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            addView(
                textEmpty,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
        sheet.setContent(content) {}
        sheet.show()
        loadChapters()
    }

    private fun loadChapters() {
        val book = ReadBook.book ?: run {
            showChapters(emptyList())
            return
        }
        activity.lifecycleScope.launch {
            val chapters = withContext(Dispatchers.IO) {
                appDb.bookChapterDao.getChapterList(book.bookUrl)
            }
            showChapters(chapters)
        }
    }

    private fun showChapters(chapters: List<BookChapter>) {
        adapter.submitItems(chapters)
        textSummary.text = "共 ${chapters.size} 章"
        val empty = chapters.isEmpty()
        recyclerChapters.isVisible = !empty
        textSummary.isVisible = !empty
        textEmpty.isVisible = empty
        scrollToCurrent()
    }

    private fun scrollToCurrent() {
        val position = adapter.positionOf(ReadBook.durChapterIndex)
        if (position >= 0) {
            recyclerChapters.post {
                (recyclerChapters.layoutManager as? LinearLayoutManager)
                    ?.scrollToPositionWithOffset((position - 2).coerceAtLeast(0), 0)
            }
        }
    }

    private fun selectChapter(chapter: BookChapter) {
        sheet.dismiss()
        activity.openChapterFromCatalog(chapter.index)
    }
}

class ReadAloudVoiceSheet(
    private val activity: ReadAloudPlayerActivity
) {
    private lateinit var sheet: NgLongListBottomSheet
    private lateinit var recyclerVoices: RecyclerView
    private lateinit var textEmpty: TextView
    private val previewController by lazy {
        TtsVoicePreviewController(
            context = activity,
            lifecycleScope = activity.lifecycleScope,
            beforePreview = {
                activity.stopStoryboardPreview()
                if (BaseReadAloudService.isPlay()) {
                    ReadAloud.pause(activity)
                }
            }
        )
    }
    private val adapter by lazy {
        ReadAloudVoiceAdapter(
            context = activity,
            onSelect = ::selectVoice,
            onPreview = ::previewVoice
        )
    }

    fun show() {
        sheet = NgLongListBottomSheet(
            context = activity,
            searchHint = "搜索引擎或发音人"
        )
        recyclerVoices = RecyclerView(activity).apply {
            layoutManager = LinearLayoutManager(activity)
            adapter = this@ReadAloudVoiceSheet.adapter
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setPadding(0, 0, 0, 6.dpToPx())
        }
        textEmpty = TextView(activity).apply {
            text = "没有可选发音人"
            setTextColor(ContextCompat.getColor(activity, R.color.ng_on_surface_variant))
            textSize = 15f
            gravity = Gravity.CENTER
            isVisible = false
        }
        val content = FrameLayout(activity).apply {
            addView(
                recyclerVoices,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            addView(
                textEmpty,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
        sheet.setContent(content, ::renderVoiceOptions)
        sheet.dialog.setOnDismissListener {
            releasePreview()
        }
        sheet.show()
    }

    private fun renderVoiceOptions(query: String) {
        val items = buildVoiceItems(query)
        adapter.submitItems(items)
        val hasChoice = items.any { it is VoiceSheetItem.Choice }
        recyclerVoices.isVisible = hasChoice
        textEmpty.isVisible = !hasChoice
    }

    private fun buildVoiceItems(query: String): List<VoiceSheetItem> {
        val activeEngineId = runCatching { TtsEngineStore.activeEngineId() }.getOrDefault("")
        val activeEngine = runCatching { TtsEngineStore.engine(activeEngineId) }.getOrNull()
        val activeVoiceId = activeEngine?.activeVoiceId
        val normalizedQuery = query.trim()
        return buildList {
            TtsEngineStore.engines()
                .filter { it.enabled }
                .forEach { engine ->
                    val choices = voiceChoices(engine)
                        .filter { choice ->
                            normalizedQuery.isBlank() || choice.matches(normalizedQuery)
                        }
                    if (choices.isNotEmpty()) {
                        add(VoiceSheetItem.Header(engine.name))
                        choices.forEach { choice ->
                            add(
                                VoiceSheetItem.Choice(
                                    option = choice,
                                    selected = activeEngineId == engine.id &&
                                            if (choice.systemDefault) {
                                                activeVoiceId.isNullOrBlank()
                                            } else {
                                                activeVoiceId == choice.voice.id
                                            }
                                )
                            )
                        }
                    }
                }
        }
    }

    private fun voiceChoices(engine: TtsEngineSetting): List<VoiceOption> {
        if (engine.type == TtsEngineType.SYSTEM) {
            return listOf(
                VoiceOption(
                    engine = engine,
                    voice = TtsVoice(
                        id = TtsEngineStore.SYSTEM_DEFAULT_ID,
                        name = "默认发音人"
                    ),
                    systemDefault = true
                )
            )
        }
        return engine.enabledVoices().map { voice ->
            VoiceOption(engine = engine, voice = voice, systemDefault = false)
        }
    }

    private fun selectVoice(option: VoiceOption) {
        val wasRun = BaseReadAloudService.isRun
        val oldEngineType = runCatching { TtsEngineStore.activeEngine().type }.getOrNull()
        val pageIndex = ReadBook.durPageIndex
        val startPos = activity.currentPageStartPos()
        activity.runVoiceSwitch {
            if (wasRun && oldEngineType != null && oldEngineType != option.engine.type) {
                ReadAloud.stop(activity)
            }
            val selected = TtsEngineStore.selectVoice(
                engineId = option.engine.id,
                voiceId = option.voice.id.takeUnless { option.systemDefault }
            )
            if (selected != null) {
                if (wasRun) {
                    ReadAloud.play(activity, play = true, pageIndex = pageIndex, startPos = startPos)
                }
                sheet.dismiss()
            }
        }
    }

    private fun previewVoice(option: VoiceOption) {
        previewController.preview(option.engine, option.voice, option.systemDefault)
    }

    private fun releasePreview() {
        previewController.release()
    }
}

private class ReadAloudCatalogAdapter(
    private val context: Context,
    private val currentIndex: () -> Int,
    private val onSelect: (BookChapter) -> Unit
) : RecyclerView.Adapter<ReadAloudCatalogAdapter.ChapterHolder>() {

    private val items = mutableListOf<BookChapter>()

    fun submitItems(newItems: List<BookChapter>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun positionOf(chapterIndex: Int): Int {
        return items.indexOfFirst { it.index == chapterIndex }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChapterHolder {
        val itemView = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 13.dpToPx(), 0, 13.dpToPx())
        }
        return ChapterHolder(itemView)
    }

    override fun onBindViewHolder(holder: ChapterHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ChapterHolder(
        private val container: LinearLayout
    ) : RecyclerView.ViewHolder(container) {

        private val titleView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            includeFontPadding = false
        }
        private val metaView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            includeFontPadding = false
            setPadding(0, 8.dpToPx(), 0, 0)
        }
        private val divider = View(context).apply {
            setBackgroundColor(ContextCompat.getColor(context, R.color.bg_divider_line))
        }

        init {
            container.addView(
                titleView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            container.addView(
                metaView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            container.addView(
                divider,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    1
                ).apply {
                    topMargin = 14.dpToPx()
                }
            )
        }

        fun bind(chapter: BookChapter) {
            val isCurrent = chapter.index == currentIndex()
            titleView.text = chapter.title
            titleView.typeface = if (isCurrent) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            titleView.setTextColor(
                if (isCurrent) context.accentColor
                else ContextCompat.getColor(context, R.color.ng_on_surface)
            )
            val meta = chapter.tag?.takeIf { it.isNotBlank() }
                ?: "第 ${chapter.index + 1} 章"
            metaView.text = if (isCurrent) "$meta · 当前播放" else meta
            metaView.setTextColor(ContextCompat.getColor(context, R.color.ng_on_surface_variant))
            container.setOnClickListener { onSelect(chapter) }
            container.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }
}

private sealed class VoiceSheetItem {
    data class Header(val title: String) : VoiceSheetItem()
    data class Choice(val option: VoiceOption, val selected: Boolean) : VoiceSheetItem()
}

private data class VoiceOption(
    val engine: TtsEngineSetting,
    val voice: TtsVoice,
    val systemDefault: Boolean
) {
    fun matches(query: String): Boolean {
        return listOf(
            engine.name,
            voice.name,
            voice.id,
            voice.language.orEmpty(),
            voice.gender.orEmpty(),
            voice.style.orEmpty(),
            voice.tags.joinToString(" ")
        ).any { it.contains(query, ignoreCase = true) }
    }
}

private class ReadAloudVoiceAdapter(
    private val context: Context,
    private val onSelect: (VoiceOption) -> Unit,
    private val onPreview: (VoiceOption) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<VoiceSheetItem>()

    fun submitItems(newItems: List<VoiceSheetItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is VoiceSheetItem.Header -> VIEW_TYPE_HEADER
            is VoiceSheetItem.Choice -> VIEW_TYPE_CHOICE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_HEADER) {
            HeaderHolder(TextView(parent.context))
        } else {
            ChoiceHolder(
                ItemTtsVoiceBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is VoiceSheetItem.Header -> (holder as HeaderHolder).bind(item)
            is VoiceSheetItem.Choice -> (holder as ChoiceHolder).bind(item)
        }
    }

    override fun getItemCount(): Int = items.size

    private inner class HeaderHolder(
        private val textView: TextView
    ) : RecyclerView.ViewHolder(textView) {
        fun bind(item: VoiceSheetItem.Header) {
            textView.text = item.title
            textView.setTextColor(context.accentColor)
            textView.typeface = Typeface.DEFAULT_BOLD
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            textView.includeFontPadding = false
            textView.setPadding(4.dpToPx(), 12.dpToPx(), 4.dpToPx(), 8.dpToPx())
            textView.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private inner class ChoiceHolder(
        private val binding: ItemTtsVoiceBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: VoiceSheetItem.Choice) = binding.run {
            val option = item.option
            TtsVoiceCardBinder.bind(
                context = context,
                binding = this,
                item = option.voice,
                engine = option.engine,
                isSystemEngine = option.systemDefault,
                showControls = false
            )
            switchEnabled.isVisible = false
            imagePreview.isVisible = true
            imagePreview.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.ng_on_surface)
            )
            val contentArea = root.getChildAt(0)
            root.alpha = 1f
            root.isClickable = false
            root.isFocusable = false
            root.setOnClickListener(null)
            contentArea.isClickable = true
            contentArea.isFocusable = true
            contentArea.setOnClickListener { onSelect(option) }
            listOf(scrollHeader, scrollTags).forEach { scrollView ->
                scrollView.setOnTouchListener { _, event ->
                    if (event.action == MotionEvent.ACTION_UP) {
                        contentArea.performClick()
                    }
                    true
                }
            }
            imagePreview.setOnClickListener {
                it.animate().cancel()
                it.scaleX = 0.9f
                it.scaleY = 0.9f
                it.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(160L)
                    .start()
                onPreview(option)
            }
            root.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 10.dpToPx()
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_CHOICE = 1
    }
}
