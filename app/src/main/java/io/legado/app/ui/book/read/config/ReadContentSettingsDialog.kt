package io.legado.app.ui.book.read.config

import android.content.DialogInterface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.liuyueyi.quick.transfer.constants.TransType
import io.legado.app.R
import io.legado.app.base.BaseComposeDialogFragment
import io.legado.app.constant.EventBus
import io.legado.app.help.config.AppConfig
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.book.read.ReadDrawerContentTheme
import io.legado.app.ui.book.read.ReadDrawerStyle
import io.legado.app.ui.book.read.readFloatingGlassStyle
import io.legado.app.ui.design.components.compose.NgDismissibleDrawer
import io.legado.app.ui.design.components.compose.NgFormGroupDivider
import io.legado.app.ui.design.components.compose.NgFormPanel
import io.legado.app.ui.design.components.compose.NgGlassSurface
import io.legado.app.ui.design.components.compose.NgLongDrawerHeader
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.design.theme.NgThemeSnapshot
import io.legado.app.utils.ChineseUtils
import io.legado.app.utils.postEvent

internal data class ReadContentSettingsState(
    val chineseConverterType: Int,
    val bookAvailable: Boolean,
    val replaceEnabled: Boolean,
    val removeSameTitle: Boolean,
    val reSegment: Boolean,
)

internal fun applyReadChineseConverter(index: Int) {
    AppConfig.chineseConverterType = index
    ChineseUtils.unLoad(*TransType.entries.toTypedArray())
    postEvent(EventBus.UP_CONFIG, arrayListOf(5))
}

class ReadContentSettingsDialog : BaseComposeDialogFragment() {
    private var screenState by mutableStateOf<ReadContentSettingsState?>(null)
    private var themeSnapshot by mutableStateOf<NgThemeSnapshot?>(null)
    private var bottomDialogRegistered = false

    private val readActivity: ReadBookActivity?
        get() = activity as? ReadBookActivity

    override fun onStart() {
        super.onStart()
        dialog?.window?.run {
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setBackgroundDrawableResource(R.color.transparent)
            decorView.setPadding(0, 0, 0, 0)
            attributes = attributes.apply {
                dimAmount = 0f
                gravity = Gravity.BOTTOM
            }
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: android.os.Bundle?) {
        if (!bottomDialogRegistered) {
            readActivity?.let {
                it.bottomDialog++
                bottomDialogRegistered = true
            }
        }
        refreshUi()
        themeSnapshot = ReadDrawerStyle.themeSnapshot(requireContext())
        (view as ComposeView).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                themeSnapshot?.let { snapshot ->
                    ReadDrawerContentTheme(snapshot = snapshot) {
                        NgDismissibleDrawer(onDismiss = { dismissAllowingStateLoss() }) {
                            screenState?.let { state ->
                                ReadContentSettingsScreen(
                                    state = state,
                                    onChineseConverterChanged = ::changeChineseConverter,
                                    onReplaceChanged = { enabled ->
                                        readActivity?.setReadContentReplaceEnabled(enabled)
                                        refreshUi()
                                    },
                                    onOpenReplaceRules = { readActivity?.openReplaceRule() },
                                    onRemoveSameTitleChanged = { enabled ->
                                        readActivity?.setReadContentRemoveSameTitle(enabled)
                                        refreshUi()
                                    },
                                    onReSegmentChanged = { enabled ->
                                        readActivity?.setReadContentReSegment(enabled)
                                        refreshUi()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        if (bottomDialogRegistered) {
            readActivity?.let {
                it.bottomDialog = (it.bottomDialog - 1).coerceAtLeast(0)
            }
            bottomDialogRegistered = false
        }
    }

    private fun changeChineseConverter(index: Int) {
        applyReadChineseConverter(index)
        refreshUi()
    }

    private fun refreshUi() {
        val book = ReadBook.book
        screenState = ReadContentSettingsState(
            chineseConverterType = AppConfig.chineseConverterType,
            bookAvailable = book != null,
            replaceEnabled = book?.getUseReplaceRule() == true,
            removeSameTitle = book?.getRemoveSameTitle() == true,
            reSegment = book?.getReSegment() == true,
        )
    }
}

@Composable
internal fun ReadContentSettingsScreen(
    state: ReadContentSettingsState,
    onChineseConverterChanged: (Int) -> Unit,
    onReplaceChanged: (Boolean) -> Unit,
    onOpenReplaceRules: () -> Unit,
    onRemoveSameTitleChanged: (Boolean) -> Unit,
    onReSegmentChanged: (Boolean) -> Unit,
) {
    val contentColor = Color(NgTheme.colors.onSurface)
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp
    NgGlassSurface(
        modifier = Modifier.padding(top = 8.dp).fillMaxWidth().heightIn(max = maxHeight),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        style = readFloatingGlassStyle(),
    ) {
        Column(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp)) {
            NgLongDrawerHeader(
                title = stringResource(R.string.content),
                centerTitle = true,
            )
            Column(
                Modifier.fillMaxWidth().weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 8.dp),
            ) {
                Text(
                    text = stringResource(R.string.read_content_chinese_conversion),
                    modifier = Modifier.fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 6.dp),
                    color = Color(NgTheme.colors.primary),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                ReadStyleModeSelector(
                    labels = stringArrayResource(R.array.chinese_mode_short).toList(),
                    selectedIndex = state.chineseConverterType,
                    contentColor = contentColor,
                    variant = ReadStyleModeSelectorVariant.PRIMARY_SELECTION,
                    onSelected = onChineseConverterChanged,
                )
                Spacer(Modifier.height(12.dp))
                NgFormPanel {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 44.dp)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.read_content_settings_title),
                            modifier = Modifier.weight(1f),
                            color = Color(NgTheme.colors.primary),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = stringResource(R.string.read_content_only_current_book),
                            color = Color(NgTheme.colors.onSurfaceVariant),
                            fontSize = 12.sp,
                        )
                    }
                    NgFormGroupDivider()
                    Box(Modifier.padding(horizontal = 14.dp)) {
                        SwitchSettingRow(
                            title = stringResource(R.string.replace_rule_title),
                            checked = state.replaceEnabled,
                            onCheckedChange = onReplaceChanged,
                            enabled = state.bookAvailable,
                        )
                    }
                    NgFormGroupDivider()
                    Box(Modifier.padding(horizontal = 14.dp)) {
                        ActionSettingRow(
                            title = stringResource(R.string.read_content_replace_rules),
                            onClick = onOpenReplaceRules,
                        )
                    }
                    NgFormGroupDivider()
                    Box(Modifier.padding(horizontal = 14.dp)) {
                        SwitchSettingRow(
                            title = stringResource(R.string.same_title_removed),
                            checked = state.removeSameTitle,
                            onCheckedChange = onRemoveSameTitleChanged,
                            enabled = state.bookAvailable,
                        )
                    }
                    NgFormGroupDivider()
                    Box(Modifier.padding(horizontal = 14.dp)) {
                        SwitchSettingRow(
                            title = stringResource(R.string.re_segment),
                            checked = state.reSegment,
                            onCheckedChange = onReSegmentChanged,
                            enabled = state.bookAvailable,
                        )
                    }
                }
            }
        }
    }
}
