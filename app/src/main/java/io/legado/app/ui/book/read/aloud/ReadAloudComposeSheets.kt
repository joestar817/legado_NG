package io.legado.app.ui.book.read.aloud

import android.app.Dialog
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ListeningMotionConfig
import io.legado.app.help.config.ListeningMotionSettings
import io.legado.app.help.tts.TtsSpeedPolicy
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.design.components.compose.NgBottomDrawerSurface
import io.legado.app.ui.design.components.compose.NgLongDrawerHeader
import io.legado.app.ui.design.components.compose.NgSlider
import io.legado.app.ui.design.components.compose.NgSliderVariant
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.design.theme.NgThemeSnapshot
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 听书 Compose 抽屉的宿主边界。
 *
 * 这里只处理 BottomSheet 窗口和拖拽行为；颜色、材质、边距和圆角全部交给
 * [NgBottomDrawerSurface]，避免播放器重新维护一套抽屉外观。
 */
internal abstract class ReadAloudComposeBottomSheet : BottomSheetDialogFragment() {

    private var listeningThemeSnapshot by mutableStateOf<NgThemeSnapshot?>(null)
    private var listeningThemeJob: Job? = null

    protected open fun listeningBook() = ReadBook.book

    protected open fun listeningSourceOrigin(): String? = ReadBook.bookSource?.bookSourceUrl

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        BottomSheetDialog(requireContext(), theme)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setBackgroundColor(AndroidColor.TRANSPARENT)
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        refreshListeningSheetTheme()
    }

    protected fun refreshListeningSheetTheme(
        settings: ListeningMotionSettings = ListeningMotionConfig.current(),
    ) {
        val safeContext = context ?: return
        val book = listeningBook()
        val sourceOrigin = listeningSourceOrigin()
        listeningThemeJob?.cancel()
        listeningThemeSnapshot = ReadAloudPlayerTheme.initialDrawerSnapshot(
            context = safeContext,
            book = book,
            sourceOrigin = sourceOrigin,
            settings = settings,
        )
        listeningThemeJob = viewLifecycleOwner.lifecycleScope.launch {
            listeningThemeSnapshot = ReadAloudPlayerTheme.resolveDrawerSnapshot(
                context = safeContext,
                book = book,
                sourceOrigin = sourceOrigin,
                settings = settings,
            )
        }
    }

    @Composable
    protected fun ListeningSheetTheme(content: @Composable () -> Unit) {
        NgAppTheme(
            snapshot = listeningThemeSnapshot,
            updateSystemBars = false,
            content = content,
        )
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawableResource(R.color.transparent)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = 0.22f }
            decorView.setPadding(0, 0, 0, 0)
        }
        val sheet = dialog?.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return
        sheet.setBackgroundColor(AndroidColor.TRANSPARENT)
        sheet.layoutParams = sheet.layoutParams.apply {
            height = ViewGroup.LayoutParams.WRAP_CONTENT
        }
        BottomSheetBehavior.from(sheet).apply {
            skipCollapsed = true
            isFitToContents = true
            isDraggable = true
            isDraggableOnNestedScroll = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }
}

internal class ReadAloudTimerDialog : ReadAloudComposeBottomSheet() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val initialMinute = BaseReadAloudService.timeMinute.takeIf { it > 0 }
            ?: AppConfig.ttsTimer
        (view as ComposeView).setContent {
            ListeningSheetTheme {
                ReadAloudTimerSheet(
                    initialMinute = initialMinute.coerceIn(0, TIMER_MAX_MINUTES),
                    onValueCommitted = ::commitMinute,
                )
            }
        }
    }

    private fun commitMinute(minute: Int) {
        val safeContext = context ?: return
        val normalized = minute.coerceIn(0, TIMER_MAX_MINUTES)
        AppConfig.ttsTimer = normalized
        ReadAloud.setTimer(safeContext, normalized)
    }
}

internal class ReadAloudSpeedDialog : ReadAloudComposeBottomSheet() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (view as ComposeView).setContent {
            ListeningSheetTheme {
                ReadAloudSpeedSheet(
                    initialProgress = AppConfig.ttsSpeechRate.coerceIn(0, SPEED_MAX_PROGRESS),
                    onValueCommitted = ::commitSpeed,
                )
            }
        }
    }

    private fun commitSpeed(progress: Int) {
        val safeContext = context ?: return
        AppConfig.ttsFlowSys = false
        AppConfig.ttsSpeechRate = progress.coerceIn(0, SPEED_MAX_PROGRESS)
        (activity as? ReadAloudPlayerActivity)?.refreshPlaybackSpeedLabel()
        ReadAloud.upTtsSpeechRate(safeContext)
        if (BaseReadAloudService.isPlay() && ReadAloud.httpTtsEngineV2 == null) {
            ReadAloud.pause(safeContext)
            ReadAloud.resume(safeContext)
        }
    }
}

@Composable
private fun ReadAloudTimerSheet(
    initialMinute: Int,
    onValueCommitted: (Int) -> Unit,
) {
    var minute by remember(initialMinute) { mutableStateOf(initialMinute) }
    val title = if (minute <= 0) {
        stringResource(R.string.read_aloud_timer_close_title)
    } else {
        stringResource(R.string.read_aloud_timer_close_minutes, minute)
    }
    ReadAloudSliderSheet(
        title = title,
        value = minute,
        max = TIMER_MAX_MINUTES,
        steps = TIMER_MAX_MINUTES - 1,
        labels = listOf(
            stringResource(R.string.close),
            stringResource(R.string.timer_m, 60),
            stringResource(R.string.timer_m, 120),
            stringResource(R.string.timer_m, 180),
        ),
        onValueChange = { minute = it },
        onValueCommitted = { onValueCommitted(minute) },
    )
}

@Composable
private fun ReadAloudSpeedSheet(
    initialProgress: Int,
    onValueCommitted: (Int) -> Unit,
) {
    var progress by remember(initialProgress) { mutableStateOf(initialProgress) }
    ReadAloudSliderSheet(
        title = stringResource(
            R.string.read_aloud_playback_speed_title,
            TtsSpeedPolicy.playbackLabel(progress),
        ),
        value = progress,
        max = SPEED_MAX_PROGRESS,
        steps = SPEED_MAX_PROGRESS - 1,
        labels = listOf(
            TtsSpeedPolicy.playbackLabel(0),
            TtsSpeedPolicy.playbackLabel(SPEED_MAX_PROGRESS),
        ),
        onValueChange = { progress = it },
        onValueCommitted = { onValueCommitted(progress) },
    )
}

@Composable
internal fun ReadAloudSliderSheet(
    title: String,
    value: Int,
    max: Int,
    steps: Int,
    labels: List<String>,
    onValueChange: (Int) -> Unit,
    onValueCommitted: () -> Unit,
) {
    NgBottomDrawerSurface(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 18.dp),
        ) {
            NgLongDrawerHeader(
                title = title,
                centerTitle = true,
            )
            NgSlider(
                value = value.toFloat(),
                onValueChange = {
                    onValueChange(it.roundToInt().coerceIn(0, max))
                },
                valueRange = 0f..max.toFloat(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp),
                steps = steps,
                variant = NgSliderVariant.CONTINUOUS,
                onValueChangeFinished = onValueCommitted,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                labels.forEach { label ->
                    Text(
                        text = label,
                        color = Color(NgTheme.colors.onSurfaceVariant),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                }
            }
        }
    }
}

private const val TIMER_MAX_MINUTES = 180
private const val SPEED_MAX_PROGRESS = 45
