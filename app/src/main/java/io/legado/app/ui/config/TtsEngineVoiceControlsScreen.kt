package io.legado.app.ui.config

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.legado.app.R
import io.legado.app.ui.design.components.compose.NgFloatingSearchToolbar
import io.legado.app.ui.design.components.compose.NgFloatingTabBar
import io.legado.app.ui.design.components.compose.NgFloatingTabSpec
import io.legado.app.ui.design.components.compose.NgFloatingToolbarActionButton
import io.legado.app.ui.design.components.compose.NgSlider
import io.legado.app.ui.design.components.compose.NgSliderVariant
import io.legado.app.ui.design.theme.NgTheme
import kotlin.math.roundToInt

internal enum class TtsEngineVoiceControlsMode {
    SCRIPT,
    SYSTEM,
}

@Immutable
internal data class TtsEngineVoiceControlsState(
    val mode: TtsEngineVoiceControlsMode = TtsEngineVoiceControlsMode.SCRIPT,
    val query: String = "",
    val message: String? = null,
    val canToggleAll: Boolean = false,
    val allEnabled: Boolean = true,
    val speed: Int = 50,
    val pitch: Int = 50,
)

@Immutable
internal data class TtsVoiceParamPanelState(
    val speed: Int = 50,
    val volume: Int = 50,
    val pitch: Int = 50,
    val languages: List<String> = emptyList(),
    val selectedLanguages: Set<String> = emptySet(),
    val selectedGenders: Set<String> = emptySet(),
    val showFilters: Boolean = true,
    val showGenderFilters: Boolean = false,
)

@Composable
internal fun TtsEngineVoiceControlsScreen(
    state: TtsEngineVoiceControlsState,
    paramPanelState: TtsVoiceParamPanelState,
    paramPanelExpanded: Boolean,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onOpenParams: () -> Unit,
    onDismissParams: () -> Unit,
    onParamSpeedChange: (Int) -> Unit,
    onParamVolumeChange: (Int) -> Unit,
    onParamPitchChange: (Int) -> Unit,
    onParamValueChangeFinished: () -> Unit,
    onToggleLanguage: (String) -> Unit,
    onToggleGender: (String) -> Unit,
    onToggleAll: () -> Unit,
    onSystemSpeedChange: (Int) -> Unit,
    onSystemPitchChange: (Int) -> Unit,
    onSystemValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (state.mode == TtsEngineVoiceControlsMode.SYSTEM) {
            TtsSystemVoiceParamsPanel(
                speed = state.speed,
                pitch = state.pitch,
                onSpeedChange = onSystemSpeedChange,
                onPitchChange = onSystemPitchChange,
                onValueChangeFinished = onSystemValueChangeFinished,
                modifier = Modifier.padding(top = 4.dp),
            )
            return@Column
        }

        TtsVoiceFloatingTopBar(
            query = state.query,
            paramPanelState = paramPanelState,
            paramPanelExpanded = paramPanelExpanded,
            canToggleAll = state.canToggleAll,
            allEnabled = state.allEnabled,
            onBack = onBack,
            onQueryChange = onQueryChange,
            onOpenParams = onOpenParams,
            onDismissParams = onDismissParams,
            onParamSpeedChange = onParamSpeedChange,
            onParamVolumeChange = onParamVolumeChange,
            onParamPitchChange = onParamPitchChange,
            onParamValueChangeFinished = onParamValueChangeFinished,
            onToggleLanguage = onToggleLanguage,
            onToggleGender = onToggleGender,
            onToggleAll = onToggleAll,
        )

        state.message?.takeIf(String::isNotBlank)?.let { message ->
            Text(
                text = message,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                color = Color(NgTheme.colors.onSurfaceVariant),
                fontSize = 14.sp,
                lineHeight = 18.sp,
            )
        }

    }
}

@Composable
private fun TtsVoiceFloatingTopBar(
    query: String,
    paramPanelState: TtsVoiceParamPanelState,
    paramPanelExpanded: Boolean,
    canToggleAll: Boolean,
    allEnabled: Boolean,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onOpenParams: () -> Unit,
    onDismissParams: () -> Unit,
    onParamSpeedChange: (Int) -> Unit,
    onParamVolumeChange: (Int) -> Unit,
    onParamPitchChange: (Int) -> Unit,
    onParamValueChangeFinished: () -> Unit,
    onToggleLanguage: (String) -> Unit,
    onToggleGender: (String) -> Unit,
    onToggleAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var anchorWidthPx by remember { mutableIntStateOf(0) }
    var anchorHeightPx by remember { mutableIntStateOf(0) }
    val actionColor = colorResource(R.color.ng_search_icon)
    val paramsActive = paramPanelExpanded ||
        paramPanelState.selectedLanguages.isNotEmpty() ||
        paramPanelState.selectedGenders.isNotEmpty()
    val toggleAllTitle = stringResource(
        if (allEnabled) R.string.tts_disable_all_voices else R.string.tts_enable_all_voices
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged {
                anchorWidthPx = it.width
                anchorHeightPx = it.height
            },
    ) {
        NgFloatingSearchToolbar(
            query = query,
            onQueryChange = onQueryChange,
            hint = stringResource(R.string.tts_search_voice),
            onBack = onBack,
        ) {
                NgFloatingToolbarActionButton(
                    iconRes = R.drawable.ic_tts_params_grid,
                    contentDescription = stringResource(R.string.tts_voice_params),
                    tint = if (paramsActive) Color(NgTheme.colors.primary) else actionColor,
                    onClick = onOpenParams,
                )
                if (canToggleAll) {
                    Spacer(Modifier.width(2.dp))
                    NgFloatingToolbarActionButton(
                        iconRes = if (allEnabled) {
                            R.drawable.ic_block_outline
                        } else {
                            R.drawable.ic_check_circle_outline
                        },
                        contentDescription = toggleAllTitle,
                        tint = actionColor,
                        onClick = onToggleAll,
                    )
                }
        }
        if (paramPanelExpanded && anchorWidthPx > 0 && anchorHeightPx > 0) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(
                    x = 0,
                    y = anchorHeightPx + with(density) { 4.dp.roundToPx() },
                ),
                onDismissRequest = onDismissParams,
                properties = PopupProperties(focusable = true),
            ) {
                TtsVoiceParamPopupContent(
                    state = paramPanelState,
                    onSpeedChange = onParamSpeedChange,
                    onVolumeChange = onParamVolumeChange,
                    onPitchChange = onParamPitchChange,
                    onValueChangeFinished = onParamValueChangeFinished,
                    onToggleLanguage = onToggleLanguage,
                    onToggleGender = onToggleGender,
                    modifier = Modifier
                        .width(with(density) { anchorWidthPx.toDp() })
                        .shadow(6.dp, RoundedCornerShape(30.dp)),
                )
            }
        }
    }
}

@Composable
private fun TtsSystemVoiceParamsPanel(
    speed: Int,
    pitch: Int,
    onSpeedChange: (Int) -> Unit,
    onPitchChange: (Int) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(colorResource(R.color.ng_surface))
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        TtsSystemVoiceParamRow(
            label = stringResource(R.string.tts_speed),
            value = speed,
            onValueChange = onSpeedChange,
            onValueChangeFinished = onValueChangeFinished,
        )
        TtsSystemVoiceParamRow(
            label = stringResource(R.string.tts_pitch),
            value = pitch,
            onValueChange = onPitchChange,
            onValueChangeFinished = onValueChangeFinished,
        )
    }
}

@Composable
private fun TtsSystemVoiceParamRow(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            modifier = Modifier.width(40.dp),
            color = Color(NgTheme.colors.onSurfaceVariant),
            fontSize = 14.sp,
            lineHeight = 18.sp,
        )
        NgSlider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = 0f..100f,
            modifier = Modifier.weight(1f),
            variant = NgSliderVariant.COMPACT,
            onValueChangeFinished = onValueChangeFinished,
        )
        Text(
            text = value.toString(),
            modifier = Modifier.width(36.dp),
            color = Color(NgTheme.colors.onSurfaceVariant),
            fontSize = 14.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.End,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TtsVoiceParamPopupContent(
    state: TtsVoiceParamPanelState,
    onSpeedChange: (Int) -> Unit,
    onVolumeChange: (Int) -> Unit,
    onPitchChange: (Int) -> Unit,
    onValueChangeFinished: () -> Unit,
    onToggleLanguage: (String) -> Unit,
    onToggleGender: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(colorResource(R.color.ng_surface))
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        TtsVoiceParamsSliderPanel(
            speed = state.speed,
            volume = state.volume,
            pitch = state.pitch,
            onSpeedChange = onSpeedChange,
            onVolumeChange = onVolumeChange,
            onPitchChange = onPitchChange,
            onValueChangeFinished = onValueChangeFinished,
        )
        if (state.showFilters && state.languages.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(top = 10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                TtsVoiceFilterLabel(stringResource(R.string.language))
                FlowRow(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    state.languages.forEach { language ->
                        TtsVoiceTextFilterChip(
                            text = language,
                            selected = language in state.selectedLanguages,
                            onClick = { onToggleLanguage(language) },
                        )
                    }
                }
            }
        }
        if (state.showFilters && state.showGenderFilters) {
            Row(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .height(30.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TtsVoiceFilterLabel(stringResource(R.string.character_gender))
                Row(
                    modifier = Modifier.padding(start = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    TtsVoiceGenderFilterChip(
                        label = "男",
                        iconRes = R.drawable.ic_tts_gender_male,
                        selectedColor = colorResource(R.color.ng_tts_gender_male),
                        selected = "男" in state.selectedGenders,
                        onClick = { onToggleGender("男") },
                    )
                    TtsVoiceGenderFilterChip(
                        label = "女",
                        iconRes = R.drawable.ic_tts_gender_female,
                        selectedColor = colorResource(R.color.ng_tts_gender_female),
                        selected = "女" in state.selectedGenders,
                        onClick = { onToggleGender("女") },
                    )
                }
            }
        }
    }
}

@Composable
private fun TtsVoiceFilterLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .width(40.dp)
            .height(30.dp)
            .wrapContentHeight(Alignment.CenterVertically),
        color = Color(NgTheme.colors.onSurfaceVariant),
        fontSize = 14.sp,
        lineHeight = 18.sp,
    )
}

@Composable
private fun TtsVoiceTextFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .height(24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                colorResource(
                    if (selected) R.color.ng_tts_language_container
                    else R.color.ng_neutral_container
                )
            )
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = colorResource(
                if (selected) R.color.ng_tts_language else R.color.ng_on_surface_variant
            ),
            fontSize = 12.sp,
            lineHeight = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TtsVoiceGenderFilterChip(
    label: String,
    @DrawableRes iconRes: Int,
    selectedColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(34.dp)
            .height(24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                colorResource(
                    if (selected) R.color.ng_tts_language_container
                    else R.color.ng_neutral_container
                )
            )
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label,
            tint = if (selected) selectedColor else colorResource(R.color.ng_on_surface_variant),
        )
    }
}

@Composable
internal fun TtsEngineDetailTabBar(
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    NgFloatingTabBar(
        items = listOf(
            NgFloatingTabSpec(
                iconRes = R.drawable.ic_ai_tab_config,
                contentDescription = stringResource(R.string.tts_config_tab),
            ),
            NgFloatingTabSpec(
                iconRes = R.drawable.ic_tts_tab_voice,
                contentDescription = stringResource(R.string.tts_voices),
            ),
        ),
        selectedIndex = selectedIndex,
        onTabSelected = onSelected,
        modifier = modifier,
    )
}
