package io.legado.app.ui.book.read.config

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.data.entities.Book
import io.legado.app.help.config.EpubFormattingProfileContract
import io.legado.app.help.config.EpubLayoutPreferences
import io.legado.app.help.config.EpubPublisherProfile
import io.legado.app.ui.design.components.compose.NgBottomDrawerSurface
import io.legado.app.ui.design.components.compose.NgDrawerDefaults
import io.legado.app.ui.design.theme.NgTheme

/**
 * EPUB 三态用户策略 + 八项独立 precedence rules（publisher 为第 8 条规则）。
 * 存储不变：仍写 EpubLayoutPreferences 的 8 个布尔；三态由契约 resolver 推导。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EpubLayoutSheet(
    book: Book,
    onStyleChanged: () -> Unit,
    onDismiss: () -> Unit,
) {
    val maxHeight = minOf(READ_MORE_CONFIG_WINDOW_HEIGHT_DP.dp, LocalConfiguration.current.screenHeightDp.dp)
    val appearance = NgDrawerDefaults.currentAppearance().copy(horizontalMarginDp = 0, cornerRadiusDp = 20)
    var choices by remember(book.bookUrl) { mutableStateOf(EpubLayoutPreferences.read(book.bookUrl)) }
    val ruleKeys = listOf(EpubLayoutPreferences.PUBLISHER) + EpubLayoutPreferences.features.keys

    fun flagsOf(choices: Map<String, Boolean>): List<Boolean> = ruleKeys.map { choices.getValue(it) }

    fun profileOf(choices: Map<String, Boolean>): EpubPublisherProfile =
        EpubFormattingProfileContract.resolve(flagsOf(choices)).profile

    fun applyProfile(profile: EpubPublisherProfile) {
        // 机械消费契约 writer：CUSTOM 恒等返回当前旗标 → 零写入（展开 Custom 不改渲染）。
        val target = EpubFormattingProfileContract.respectFlagsFor(profile, flagsOf(choices))
        ruleKeys.zip(target).forEach { (key, value) ->
            if (choices.getValue(key) != value) EpubLayoutPreferences.set(book.bookUrl, key, value)
        }
        choices = EpubLayoutPreferences.read(book.bookUrl)
        onStyleChanged()
    }

    fun changeFeature(key: String, value: Boolean) {
        EpubLayoutPreferences.set(book.bookUrl, key, value)
        choices = EpubLayoutPreferences.read(book.bookUrl)
        onStyleChanged()
    }

    val profile = profileOf(choices)
    val contentColor = Color(NgTheme.colors.onSurface)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetMaxWidth = Dp.Unspecified,
        containerColor = Color.Transparent, shape = RectangleShape, dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        NgBottomDrawerSurface(
            modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight).padding(top = 8.dp),
            appearance = appearance,
        ) {
            Column(
                Modifier.navigationBarsPadding().verticalScroll(rememberScrollState())
                    .padding(top = 10.dp, bottom = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.epub_layout_title),
                    modifier = Modifier.height(42.dp).padding(horizontal = 16.dp),
                    color = contentColor,
                    fontSize = 20.sp,
                    lineHeight = 42.sp,
                    fontWeight = FontWeight.Medium,
                )
                Column(Modifier.padding(horizontal = 16.dp)) {
                    ProfileRow(
                        title = stringResource(R.string.epub_formatting_respect),
                        selected = profile == EpubPublisherProfile.RESPECT,
                        contentColor = contentColor,
                        onClick = { applyProfile(EpubPublisherProfile.RESPECT) },
                    )
                    ReadMoreDivider(contentColor)
                    ProfileRow(
                        title = stringResource(R.string.epub_formatting_override),
                        selected = profile == EpubPublisherProfile.OVERRIDE,
                        contentColor = contentColor,
                        onClick = { applyProfile(EpubPublisherProfile.OVERRIDE) },
                    )
                    ReadMoreDivider(contentColor)
                    ProfileRow(
                        title = stringResource(R.string.epub_formatting_custom),
                        selected = profile == EpubPublisherProfile.CUSTOM,
                        contentColor = contentColor,
                        onClick = { applyProfile(EpubPublisherProfile.CUSTOM) },
                    )
                    if (profile == EpubPublisherProfile.CUSTOM) {
                        ReadMoreDivider(contentColor)
                        SwitchSettingRow(
                            title = stringResource(R.string.epub_publisher_style),
                            checked = choices.getValue(EpubLayoutPreferences.PUBLISHER),
                            onCheckedChange = { changeFeature(EpubLayoutPreferences.PUBLISHER, it) },
                        )
                        ReadMoreDivider(contentColor)
                        EpubLayoutPreferences.features.entries.forEach { (key, title) ->
                            SwitchSettingRow(
                                title = title,
                                checked = choices.getValue(key),
                                onCheckedChange = { changeFeature(key, it) },
                            )
                            ReadMoreDivider(contentColor)
                        }
                    }
                    ActionSettingRow(
                        title = stringResource(R.string.restore_default),
                        onClick = {
                            EpubLayoutPreferences.reset(book.bookUrl)
                            choices = EpubLayoutPreferences.read(book.bookUrl)
                            onStyleChanged()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileRow(
    title: String,
    selected: Boolean,
    contentColor: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = contentColor,
            fontSize = 16.sp,
        )
        if (selected) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = Color(NgTheme.colors.primary),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
