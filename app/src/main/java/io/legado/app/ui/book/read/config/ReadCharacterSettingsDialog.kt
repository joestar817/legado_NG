package io.legado.app.ui.book.read.config

import android.content.DialogInterface
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.base.BaseComposeDialogFragment
import io.legado.app.help.config.ReadAppearanceMode
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ReadCharacterMarkMode
import io.legado.app.help.config.ReadCharacterMarkSettings
import io.legado.app.help.config.ReadHighlightRule
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.book.read.ReadDrawerContentTheme
import io.legado.app.ui.book.read.ReadDrawerStyle
import io.legado.app.ui.book.read.readFloatingGlassStyle
import io.legado.app.ui.design.components.compose.NgDismissibleDrawer
import io.legado.app.ui.design.components.compose.NgFormGroupDivider
import io.legado.app.ui.design.components.compose.NgFormPanel
import io.legado.app.ui.design.components.compose.NgFormSelectMenuVariant
import io.legado.app.ui.design.components.compose.NgFormSelectOption
import io.legado.app.ui.design.components.compose.NgFormSelectRow
import io.legado.app.ui.design.components.compose.NgGlassSurface
import io.legado.app.ui.design.theme.NgTheme

class ReadCharacterSettingsDialog : BaseComposeDialogFragment() {
    private var registered = false
    private val reader get() = activity as? ReadBookActivity

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

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        if (!registered) reader?.let {
            it.bottomDialog++
            registered = true
        }
        val context = requireContext()
        val snapshot = ReadDrawerStyle.themeSnapshot(context)
        (view as ComposeView).apply {
            setBackgroundColor(AndroidColor.TRANSPARENT)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                var settings by remember { mutableStateOf(ReadCharacterMarkSettings.load(context)) }
                ReadDrawerContentTheme(snapshot = snapshot) {
                    NgDismissibleDrawer(onDismiss = { dismissAllowingStateLoss() }) {
                        ReadCharacterSettingsScreen(
                            settings = settings,
                            onChange = { newSettings ->
                                if (settings != newSettings) {
                                    val previous = settings
                                    settings = newSettings
                                    newSettings.save(context)
                                    reader?.onCharacterMarkSettingsChanged(previous, newSettings)
                                }
                            },
                            onOpenList = {
                                dismissAllowingStateLoss()
                                reader?.openReadingCharacterList()
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        reader?.flushCharacterStyleRefresh()
        super.onDismiss(dialog)
        if (registered) {
            reader?.let { it.bottomDialog = (it.bottomDialog - 1).coerceAtLeast(0) }
            registered = false
        }
    }
}

@Composable
internal fun ReadCharacterSettingsScreen(
    settings: ReadCharacterMarkSettings,
    onChange: (ReadCharacterMarkSettings) -> Unit,
    onOpenList: () -> Unit,
) {
    var editNight by rememberSaveable { mutableStateOf(false) }
    var colorPage by rememberSaveable { mutableStateOf<Int?>(null) }
    BackHandler(enabled = colorPage != null) { colorPage = null }
    val appearance = settings.appearance
    val updateAppearance: (ReadHighlightRule) -> Unit = { onChange(settings.copy(appearance = it)) }
    val contentColor = Color(NgTheme.colors.onSurface)
    val underlined = settings.mode == ReadCharacterMarkMode.UNDERLINE
    val colorTarget = colorPage
    if (colorTarget != null) {
        val themeLabel = stringResource(if (editNight) R.string.read_style_mode_night else R.string.read_style_mode_day)
        val followsDay = editNight && appearance.followsDayAtNight()
        val updateColor: (Int?) -> Unit = { color ->
            val palette = if (followsDay) appearance.withNightFollowDay(false) else appearance
            updateAppearance(palette.withColor(colorTarget, color, editNight))
        }
        NgGlassSurface(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            style = readFloatingGlassStyle(),
        ) {
            val initialColor = appearance.colorValue(colorTarget, editNight)
                ?: characterColorDefault(colorTarget, editNight)
            AdvancedColorPage(
                title = "$themeLabel · ${stringResource(characterColorTitle(colorTarget))}",
                initialColor = initialColor,
                onBack = { colorPage = null },
                onColorChanged = { updateColor(it) },
                onReset = { updateColor(null) },
                topContent = if (editNight) {
                    {
                        NgFormPanel {
                            SwitchSettingRow(
                                title = stringResource(R.string.highlight_rule_night_follow_day),
                                checked = followsDay,
                                onCheckedChange = { updateAppearance(appearance.withNightFollowDay(it)) },
                                modifier = Modifier.padding(horizontal = 14.dp),
                            )
                        }
                    }
                } else null,
            )
        }
        return
    }

    val arrow = painterResource(R.drawable.ic_expand_more)
    val modes = listOf(
        NgFormSelectOption(stringResource(R.string.read_character_mark_underline), ReadCharacterMarkMode.UNDERLINE.key),
        NgFormSelectOption(stringResource(R.string.read_character_mark_text), ReadCharacterMarkMode.BOLD.key),
        NgFormSelectOption(stringResource(R.string.highlight_rule_underline_none), ReadCharacterMarkMode.OFF.key),
    )
    ReadSettingsDrawer(
        title = stringResource(R.string.book_characters),
        maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.68f,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            NgFormPanel {
                Column(Modifier.padding(horizontal = 14.dp)) {
                    EditorNavigationRow(
                        title = stringResource(R.string.read_book_character_list),
                        color = contentColor,
                        leadingIconRes = R.drawable.ic_bookshelf_action_user,
                        rowHeight = 48.dp,
                        onClick = onOpenList,
                    )
                }
            }
            NgFormPanel {
                NgFormSelectRow(
                    title = stringResource(R.string.read_character_mark_mode),
                    selectedValue = settings.mode.key,
                    options = modes,
                    onValueChange = { key ->
                        onChange(settings.copy(mode = ReadCharacterMarkMode.entries.first { it.key == key }))
                    },
                    arrowIcon = arrow,
                    menuVariant = NgFormSelectMenuVariant.END_ANCHORED_CONTENT,
                )
                if (underlined) {
                    NgFormGroupDivider()
                    NgFormSelectRow(
                        title = stringResource(R.string.read_character_mark_line_style),
                        selectedValue = appearance.underlineMode.toString(),
                        options = listOf(
                            NgFormSelectOption(stringResource(R.string.read_style_underline_solid), "1"),
                            NgFormSelectOption(stringResource(R.string.read_style_underline_dashed), "2"),
                            NgFormSelectOption(stringResource(R.string.highlight_rule_underline_wavy), "3"),
                            NgFormSelectOption(stringResource(R.string.highlight_rule_underline_bar), "4"),
                        ),
                        onValueChange = { updateAppearance(appearance.copy(underlineMode = it.toInt())) },
                        arrowIcon = arrow,
                        menuVariant = NgFormSelectMenuVariant.END_ANCHORED_CONTENT,
                    )
                }
                NgFormGroupDivider()
                Column(Modifier.padding(horizontal = 14.dp)) {
                    SwitchSettingRow(
                        title = stringResource(R.string.read_character_mark_open_portrait),
                        checked = settings.opensPortrait,
                        enabled = settings.mode != ReadCharacterMarkMode.OFF,
                        onCheckedChange = { onChange(settings.copy(tapToOpenPortrait = it)) },
                    )
                }
            }
            if (settings.mode != ReadCharacterMarkMode.OFF) {
                CharacterColorSettings(
                    appearance = appearance,
                    underlined = underlined,
                    contentColor = contentColor,
                    onOpenColor = { key, night ->
                        editNight = night
                        colorPage = key
                    },
                )
                NgFormPanel {
                    if (underlined) {
                        HighlightUnderlineGeometryRows(
                            width = appearance.underlineWidth,
                            offset = appearance.underlineOffset,
                            contentColor = contentColor,
                            onWidthChanged = { updateAppearance(appearance.copy(underlineWidth = it)) },
                            onOffsetChanged = { updateAppearance(appearance.copy(underlineOffset = it)) },
                            inline = true,
                        )
                    } else {
                        HighlightFontWeightRow(
                            weight = appearance.fontWeight,
                            contentColor = contentColor,
                            onWeightChanged = { updateAppearance(appearance.copy(fontWeight = it)) },
                            inline = true,
                        )
                        NgFormGroupDivider()
                        SwitchSettingRow(
                            title = stringResource(R.string.highlight_rule_italic),
                            checked = appearance.isItalic,
                            onCheckedChange = { updateAppearance(appearance.copy(isItalic = it)) },
                            modifier = Modifier.padding(horizontal = 14.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CharacterColorSettings(
    appearance: ReadHighlightRule,
    underlined: Boolean,
    contentColor: Color,
    onOpenColor: (Int, Boolean) -> Unit,
) {
    val colorKeys = if (underlined) listOf(ReadHighlightRule.COLOR_UNDERLINE)
    else listOf(ReadHighlightRule.COLOR_TEXT, ReadHighlightRule.COLOR_BACKGROUND)
    NgFormPanel {
        colorKeys.forEachIndexed { index, key ->
            if (index > 0) NgFormGroupDivider()
            val title = stringResource(characterColorTitle(key))
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    color = contentColor,
                    fontSize = ReadSettingTextSize.switchLabel,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                for (night in listOf(false, true)) {
                    val color = appearance.colorValue(key, night)
                    val preview = color ?: if (key == ReadHighlightRule.COLOR_BACKGROUND) null
                        else characterColorDefault(key, night)
                    val valueDescription = when {
                        night && appearance.followsDayAtNight() -> stringResource(R.string.highlight_rule_night_follow_day)
                        preview == null -> stringResource(R.string.read_character_color_no_background)
                        color == null -> stringResource(R.string.text_default)
                        else -> null
                    }
                    ReadFormColorChoice(
                        label = stringResource(if (night) R.string.read_style_mode_night else R.string.read_style_mode_day),
                        title = title,
                        color = preview,
                        contentColor = contentColor,
                        valueDescription = valueDescription,
                        onClick = { onOpenColor(key, night) },
                    )
                }
            }
        }
    }
}

private fun characterColorTitle(key: Int) = when (key) {
    ReadHighlightRule.COLOR_UNDERLINE -> R.string.read_style_full_underline_color
    ReadHighlightRule.COLOR_BACKGROUND -> R.string.bg_color
    else -> R.string.text_color
}

private fun characterColorDefault(key: Int, night: Boolean): Int {
    val mode = if (night) ReadAppearanceMode.NIGHT else ReadAppearanceMode.DAY
    val config = ReadBookConfig.durConfig
    return when (key) {
        ReadHighlightRule.COLOR_UNDERLINE -> config.textAccentColorForMode(mode)
        ReadHighlightRule.COLOR_BACKGROUND -> (config.textAccentColorForMode(mode) and 0x00FFFFFF) or 0x33000000
        else -> config.textColorForMode(mode)
    }
}
