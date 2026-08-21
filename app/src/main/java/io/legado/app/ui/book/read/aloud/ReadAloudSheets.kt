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
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.flexbox.FlexboxLayout
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.databinding.DialogReadAloudModeSheetBinding
import io.legado.app.databinding.DialogReadAloudMoreSheetBinding
import io.legado.app.help.IntentHelp
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.help.tts.BookTtsAutomationConfig
import io.legado.app.help.tts.BookTtsCastingCoordinator
import io.legado.app.help.tts.TtsEngineCapability
import io.legado.app.help.tts.TtsEngineSetting
import io.legado.app.help.tts.TtsEngineStore
import io.legado.app.help.tts.TtsEngineType
import io.legado.app.lib.theme.view.ThemeSwitch
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.book.read.ReadDrawerStyle
import io.legado.app.ui.config.ConfigActivity
import io.legado.app.ui.config.ConfigTag
import io.legado.app.ui.config.TtsEngineSelectionSheet
import io.legado.app.ui.config.TtsVoiceOption
import io.legado.app.ui.config.TtsVoiceSelectionSheet
import io.legado.app.ui.widget.seekbar.SeekBarChangeListener
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.postEvent
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefString
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
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

    protected fun applyThemeSheetBackground() {
        val background = runCatching {
            ThemeConfig.getBgImage(requireContext(), resources.displayMetrics)
        }.getOrNull() ?: return
        view?.background = ReadDrawerStyle.wrapTopRounded(background)
    }
}

class ReadAloudModeSheet(
    private val activity: ReadAloudPlayerActivity
) : ReadAloudBottomSheet(R.layout.dialog_read_aloud_mode_sheet) {

    private val binding by viewBinding(DialogReadAloudModeSheetBinding::bind)

    override fun onStart() {
        super.onStart()
        applyThemeSheetBackground()
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) = binding.run {
        renderState()
        cardSingleRole.setOnClickListener {
            selectMultiRole(false)
        }
        cardMultiRole.setOnClickListener {
            selectMultiRole(true)
        }
        itemMultiRoleEngine.setOnClickListener(::showMultiRoleEngineSheet)
        itemAutoCreateRoles.setOnClickListener {
            switchAutoCreateRoles.toggle()
        }
        itemAutoAssignVoices.setOnClickListener {
            switchAutoAssignVoices.toggle()
        }
        itemAutoSwitchSceneVoices.setOnClickListener {
            switchAutoSwitchSceneVoices.toggle()
        }
        itemStoryboardResult.setOnClickListener {
            activity.openStoryboardResult()
            dismissAllowingStateLoss()
        }
    }

    private fun renderState() = binding.run {
        val multiRole = AppConfig.readAloudMultiRole
        cardSingleRole.isSelected = !multiRole
        cardMultiRole.isSelected = multiRole
        applyReadAloudModeCardStyles()
        layoutMultiRoleDetails.isVisible = multiRole
        itemAutoCreateRoles.isVisible = multiRole
        itemAutoAssignVoices.isVisible = multiRole
        if (multiRole) {
            val engine = TtsEngineStore.engine(AppConfig.multiRoleTtsEngineId)
            itemAutoSwitchSceneVoices.isVisible = engine?.supportsCapability(
                TtsEngineCapability.CASTING_METADATA
            ) == true
            textMultiRoleEngine.text = engine?.name ?: "未选择多人 TTS 引擎"
            bindStoryboardCapabilities(engine)
            val automation = workKey()?.let(BookTtsAutomationConfig::get)
                ?: BookTtsAutomationConfig.Settings()
            switchAutoCreateRoles.setOnCheckedChangeListener(null)
            switchAutoAssignVoices.setOnCheckedChangeListener(null)
            switchAutoSwitchSceneVoices.setOnCheckedChangeListener(null)
            switchAutoCreateRoles.isChecked = automation.autoCreateTemporaryRoles
            switchAutoAssignVoices.isChecked = automation.autoAssignVoices
            switchAutoSwitchSceneVoices.isChecked = automation.autoSwitchSceneVoices
            bindAutomationListeners()
        } else {
            itemAutoSwitchSceneVoices.isVisible = false
        }
        val storyboardAlpha = if (multiRole) 1f else 0.42f
        itemStoryboardResult.isEnabled = multiRole
        itemStoryboardResult.alpha = storyboardAlpha
    }

    private fun applyReadAloudModeCardStyles() = binding.run {
        val safeContext = root.context
        val activeIndicatorColor = ReadDrawerStyle.indicatorColor(safeContext)
        val activeTextColor = ReadDrawerStyle.accentColor(safeContext)
        val innerSurfaceColor = ContextCompat.getColor(safeContext, R.color.ng_surface)
        val textColor = ContextCompat.getColor(safeContext, R.color.ng_on_surface)
        val inactiveIconColor = ContextCompat.getColor(safeContext, R.color.ng_on_surface_variant)
        layoutReadAloudMode.setBackgroundResource(R.drawable.ng_bg_settings_item)
        layoutReadAloudMode.elevation = 0f
        layoutMultiRoleDetails.background = GradientDrawable().apply {
            cornerRadius = 14.dpToPx().toFloat()
            setColor(innerSurfaceColor)
        }
        itemAutoCreateRoles.background = GradientDrawable().apply {
            cornerRadius = 18.dpToPx().toFloat()
            setColor(innerSurfaceColor)
        }
        itemAutoAssignVoices.background = GradientDrawable().apply {
            cornerRadius = 18.dpToPx().toFloat()
            setColor(innerSurfaceColor)
        }
        itemAutoSwitchSceneVoices.background = GradientDrawable().apply {
            cornerRadius = 18.dpToPx().toFloat()
            setColor(innerSurfaceColor)
        }
        itemStoryboardResult.background = GradientDrawable().apply {
            cornerRadius = 18.dpToPx().toFloat()
            setColor(innerSurfaceColor)
        }
        itemAutoCreateRoles.elevation = 0f
        itemAutoAssignVoices.elevation = 0f
        itemAutoSwitchSceneVoices.elevation = 0f
        itemStoryboardResult.elevation = 0f
        val cards = listOf(
            Triple(cardSingleRole, iconSingleRole, titleSingleRole),
            Triple(cardMultiRole, iconMultiRole, titleMultiRole)
        )
        cards.forEach { (card, icon, title) ->
            val selected = card.isSelected
            card.background = GradientDrawable().apply {
                cornerRadius = 14.dpToPx().toFloat()
                setColor(innerSurfaceColor)
            }
            icon.imageTintList = ColorStateList.valueOf(
                if (selected) activeIndicatorColor else inactiveIconColor
            )
            title.setTextColor(if (selected) activeTextColor else textColor)
            title.typeface = Typeface.defaultFromStyle(
                if (selected) Typeface.BOLD else Typeface.NORMAL
            )
        }
    }

    private fun selectMultiRole(enabled: Boolean) {
        activity.setMultiRoleEnabled(enabled)
        renderState()
    }

    private fun bindAutomationListeners() = binding.run {
        switchAutoCreateRoles.setOnCheckedChangeListener { _, enabled ->
            workKey()?.let { BookTtsAutomationConfig.setAutoCreateTemporaryRoles(it, enabled) }
        }
        switchAutoAssignVoices.setOnCheckedChangeListener { _, enabled ->
            val workKey = workKey() ?: return@setOnCheckedChangeListener
            val wasEnabled = BookTtsAutomationConfig.get(workKey).autoAssignVoices
            BookTtsAutomationConfig.setAutoAssignVoices(workKey, enabled)
            if (!wasEnabled && enabled) assignUnboundVoices(workKey)
        }
        switchAutoSwitchSceneVoices.setOnCheckedChangeListener { _, enabled ->
            val workKey = workKey() ?: return@setOnCheckedChangeListener
            BookTtsAutomationConfig.setAutoSwitchSceneVoices(workKey, enabled)
            if (BaseReadAloudService.isRun && AppConfig.readAloudMultiRole) {
                ReadAloud.refreshTtsRoute(activity)
            }
        }
    }

    private fun showMultiRoleEngineSheet(@Suppress("UNUSED_PARAMETER") view: View) {
        val selectedId = AppConfig.multiRoleTtsEngineId
        TtsEngineSelectionSheet(
            context = activity,
            title = getString(R.string.multi_role_tts_engine),
            searchHint = getString(R.string.multi_role_tts_engine_search),
            emptyText = getString(R.string.multi_role_tts_engine_empty),
            engines = TtsEngineStore.engines().filter {
                it.enabled && it.type == TtsEngineType.SCRIPT
            },
            selectedEngineId = selectedId,
            onSelect = { engine ->
                activity.selectMultiRoleEngine(engine.id)
                renderState()
            },
            titleAction = selectedId?.takeIf { it.isNotBlank() }?.let {
                getString(R.string.clear) to {
                    activity.selectMultiRoleEngine(null)
                    renderState()
                }
            }
        ).show()
    }

    private fun assignUnboundVoices(workKey: String) {
        if (BaseReadAloudService.isRun && AppConfig.readAloudMultiRole) {
            ReadAloud.prepareTtsCasting(activity)
        }
        activity.lifecycleScope.launch(Dispatchers.IO) {
            runCatching { BookTtsCastingCoordinator.assignUnboundRoles(workKey) }
                .onSuccess { count ->
                    withContext(Dispatchers.Main) {
                        activity.toastOnUi(
                            if (count > 0) {
                                activity.getString(R.string.character_auto_assign_done, count)
                            } else {
                                activity.getString(R.string.character_auto_assign_no_change)
                            }
                        )
                        if (BaseReadAloudService.isRun && AppConfig.readAloudMultiRole) {
                            ReadAloud.refreshTtsRoute(activity)
                        }
                    }
                }
                .onFailure {
                    withContext(Dispatchers.Main) {
                        activity.toastOnUi(R.string.character_auto_assign_failed)
                        if (BaseReadAloudService.isRun && AppConfig.readAloudMultiRole) {
                            ReadAloud.refreshTtsRoute(activity)
                        }
                    }
                }
        }
    }

    private fun workKey(): String? {
        val book = ReadBook.book ?: return null
        return BookCharacterProfile.workKey(book.name, book.author).takeIf { it.isNotBlank() }
    }

    private fun bindStoryboardCapabilities(engine: TtsEngineSetting?) = binding.run {
        layoutMultiRoleCapabilities.removeAllViews()
        storyboardCapabilityTags(engine).forEach { tag ->
            layoutMultiRoleCapabilities.addView(
                TextView(layoutMultiRoleCapabilities.context).apply {
                    text = tag.text
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    maxLines = 1
                    setTextColor(ContextCompat.getColor(context, tag.colorRes))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setBackgroundResource(tag.backgroundRes)
                    setPadding(8.dpToPx(), 0, 8.dpToPx(), 0)
                    layoutParams = FlexboxLayout.LayoutParams(
                        FlexboxLayout.LayoutParams.WRAP_CONTENT,
                        24.dpToPx()
                    ).apply {
                        marginEnd = 6.dpToPx()
                        bottomMargin = 4.dpToPx()
                    }
                }
            )
        }
    }

    private fun storyboardCapabilityTags(engine: TtsEngineSetting?): List<StoryboardCapabilityTag> {
        val activeEngine = engine
            ?.takeIf { it.enabled && it.isScriptEngine }
            ?: return listOf(
                StoryboardCapabilityTag(
                    "需先选择引擎",
                    R.drawable.ng_bg_tag_warning,
                    R.color.ng_warning
                )
            )
        return buildList {
            add(
                StoryboardCapabilityTag(
                    "角色识别",
                    R.drawable.ng_bg_tts_voice_tag_blue,
                    R.color.ng_tts_tag_blue
                )
            )
            add(
                StoryboardCapabilityTag(
                    "片段拆分",
                    R.drawable.ng_bg_tts_voice_tag_purple,
                    R.color.ng_tts_tag_purple
                )
            )
            if (activeEngine.supportsCapability(TtsEngineCapability.SCENE_CONTEXT) ||
                activeEngine.supportsCapability(TtsEngineCapability.PERFORMANCE_INSTRUCTION)
            ) {
                add(
                    StoryboardCapabilityTag(
                        "场景理解",
                        R.drawable.ng_bg_tts_voice_tag_orange,
                        R.color.ng_tts_tag_orange
                    )
                )
            }
            if (activeEngine.supportsCapability(TtsEngineCapability.PERFORMANCE_INSTRUCTION)) {
                add(
                    StoryboardCapabilityTag(
                        "演员指导",
                        R.drawable.ng_bg_tts_voice_tag_green,
                        R.color.ng_tts_tag_green
                    )
                )
            }
        }
    }

    private data class StoryboardCapabilityTag(
        val text: String,
        val backgroundRes: Int,
        val colorRes: Int
    )
}

private fun SeekBar.applyReadAloudSliderStyle() {
    val accent = ReadDrawerStyle.indicatorColor(context)
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
        applyThemeSheetBackground()
        val height = (resources.displayMetrics.heightPixels * 0.86f).toInt()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, height)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) = binding.run {
        applyReadAloudMoreCardStyles()
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
        seekWorkerCount.applyReadAloudSliderStyle()
        seekWorkerCount.tickMarkTintList = ColorStateList.valueOf(
            ReadDrawerStyle.indicatorColor(view.context)
        )
        seekWorkerCount.progress = AppConfig.readAloudWorkerCount - 1
        syncWorkerCount(seekWorkerCount.progress + 1)
        seekWorkerCount.setOnSeekBarChangeListener(object : SeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                syncWorkerCount(progress + 1)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                val safeContext = context ?: return
                val count = seekBar.progress + 1
                safeContext.putPrefString(PreferKey.readAloudWorkerCount, count.toString())
                notifyReadAloudRuntimeChanged()
            }
        })
        syncPauseOnCallState()
        itemEngine.setOnClickListener {
            val safeContext = context ?: return@setOnClickListener
            safeContext.startActivity<ConfigActivity> {
                ReadAloudLauncher.markPlayerDerived(this)
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

    private fun applyReadAloudMoreCardStyles() = binding.run {
        val surfaceColor = ContextCompat.getColor(root.context, R.color.ng_surface)
        listOf(layoutPlaybackSettings, layoutEngineSettings).forEach { group ->
            group.background = null
            group.elevation = 0f
        }
        listOf(
            itemIgnoreAudioFocus,
            itemPauseOnCall,
            itemWakeLock,
            itemMediaButtonPerNext,
            itemReadByPage,
            itemSkipChapterTitle,
            itemWorkerCount,
            itemStop,
            itemEngine,
            itemSystemTts
        ).forEach { item ->
            item.background = GradientDrawable().apply {
                cornerRadius = 14.dpToPx().toFloat()
                setColor(surfaceColor)
            }
            item.elevation = 0f
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

    private fun syncWorkerCount(count: Int) = binding.run {
        val activeColor = ReadDrawerStyle.accentColor(seekWorkerCount.context)
        val inactiveColor = ContextCompat.getColor(
            seekWorkerCount.context,
            R.color.ng_on_surface_variant
        )
        listOf(
            tvWorkerCount1,
            tvWorkerCount2,
            tvWorkerCount3,
            tvWorkerCount4,
            tvWorkerCount5
        ).forEachIndexed { index, label ->
            label.setTextColor(if (index + 1 == count) activeColor else inactiveColor)
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

class ReadAloudVoiceSheet(
    private val activity: ReadAloudPlayerActivity
) {
    private lateinit var voiceSheet: TtsVoiceSelectionSheet

    fun show() {
        val activeEngineId = runCatching { TtsEngineStore.activeEngineId() }.getOrDefault("")
        val activeEngine = runCatching { TtsEngineStore.engine(activeEngineId) }.getOrNull()
        val activeVoiceId = activeEngine?.activeVoiceId
        voiceSheet = TtsVoiceSelectionSheet(
            context = activity,
            lifecycleScope = activity.lifecycleScope,
            searchHint = "搜索引擎或发音人",
            emptyText = "没有可选发音人",
            engines = { TtsEngineStore.engines().filter { it.enabled } },
            isSelected = { option ->
                activeEngineId == option.engine.id && if (option.systemDefault) {
                    activeVoiceId.isNullOrBlank()
                } else {
                    activeVoiceId == option.voice.id
                }
            },
            onSelect = ::selectVoice,
            beforePreview = {
                activity.stopStoryboardPreview()
                if (BaseReadAloudService.isPlay()) ReadAloud.pause(activity)
            },
            dismissOnSelect = false
        )
        voiceSheet.show()
    }

    private fun selectVoice(option: TtsVoiceOption) {
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
                    ReadAloud.play(
                        activity,
                        play = true,
                        pageIndex = pageIndex,
                        startPos = startPos,
                        forceRebuild = true
                    )
                }
                voiceSheet.dismiss()
            }
        }
    }
}
