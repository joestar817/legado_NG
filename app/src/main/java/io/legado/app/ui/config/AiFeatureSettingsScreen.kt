package io.legado.app.ui.config

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.design.components.NgSettingsTrailing
import io.legado.app.ui.design.components.compose.NgFormControlGroup
import io.legado.app.ui.design.components.compose.NgFormGroupDivider
import io.legado.app.ui.design.components.compose.NgFormNumberSettingRow
import io.legado.app.ui.design.components.compose.NgFormPanelSectionTitle
import io.legado.app.ui.design.components.compose.NgFormSwitchSettingRow
import io.legado.app.ui.design.components.compose.NgSettingsGroup
import io.legado.app.ui.design.components.compose.NgSettingsIcon
import io.legado.app.ui.design.components.compose.NgSettingsItem
import io.legado.app.ui.design.components.compose.NgSettingsSectionLabel

@Immutable
internal data class AiPurifyModelSettingsScreenState(
    val modelSummary: String = "",
    val reasoningSummary: String = "",
    val settingsSummary: String = "",
    val reasoningAvailable: Boolean = false,
)

internal sealed interface AiPurifyModelSettingsAction {
    data object SelectModel : AiPurifyModelSettingsAction
    data object SelectReasoning : AiPurifyModelSettingsAction
    data object OpenSettings : AiPurifyModelSettingsAction
}

@Composable
internal fun AiPurifyModelSettingsScreen(
    state: AiPurifyModelSettingsScreenState,
    onAction: (AiPurifyModelSettingsAction) -> Unit,
) {
    AiSingleGroupSettingsScreen(sectionTitle = stringResource(R.string.ai_purify)) {
        AiSettingsEntry(
            title = stringResource(R.string.ai_purify_model_setting),
            summary = state.modelSummary,
            iconRes = R.drawable.ic_ai_purify,
            summaryMaxLines = 2,
            onClick = { onAction(AiPurifyModelSettingsAction.SelectModel) },
        )
        AiSettingsEntry(
            title = stringResource(R.string.ai_purify_reasoning_level),
            summary = state.reasoningSummary,
            iconRes = R.drawable.ic_ai_capability_reasoning,
            summaryMaxLines = 2,
            modifier = Modifier.alpha(if (state.reasoningAvailable) 1f else 0.55f),
            onClick = { onAction(AiPurifyModelSettingsAction.SelectReasoning) },
        )
        AiSettingsEntry(
            title = stringResource(R.string.ai_purify_settings),
            summary = state.settingsSummary,
            iconRes = R.drawable.ic_ai_purify,
            onClick = { onAction(AiPurifyModelSettingsAction.OpenSettings) },
        )
    }
}

@Immutable
internal data class AiReadAloudModelSettingsScreenState(
    val modelSummary: String = "",
    val reasoningSummary: String = "",
    val preloadSummary: String = "",
    val reasoningAvailable: Boolean = false,
)

internal sealed interface AiReadAloudModelSettingsAction {
    data object SelectModel : AiReadAloudModelSettingsAction
    data object SelectReasoning : AiReadAloudModelSettingsAction
    data object SelectPreloadCount : AiReadAloudModelSettingsAction
}

@Composable
internal fun AiReadAloudModelSettingsScreen(
    state: AiReadAloudModelSettingsScreenState,
    onAction: (AiReadAloudModelSettingsAction) -> Unit,
) {
    AiSingleGroupSettingsScreen(sectionTitle = stringResource(R.string.ai_read_aloud)) {
        AiSettingsEntry(
            title = stringResource(R.string.ai_read_aloud_storyboard_model_setting),
            summary = state.modelSummary,
            iconRes = R.drawable.ic_ai,
            summaryMaxLines = 2,
            onClick = { onAction(AiReadAloudModelSettingsAction.SelectModel) },
        )
        AiSettingsEntry(
            title = stringResource(R.string.ai_read_aloud_reasoning_level),
            summary = state.reasoningSummary,
            iconRes = R.drawable.ic_ai_capability_reasoning,
            summaryMaxLines = 2,
            modifier = Modifier.alpha(if (state.reasoningAvailable) 1f else 0.55f),
            onClick = { onAction(AiReadAloudModelSettingsAction.SelectReasoning) },
        )
        AiSettingsEntry(
            title = stringResource(R.string.ai_read_aloud_storyboard_preload_count),
            summary = state.preloadSummary,
            iconRes = R.drawable.ic_ai,
            summaryMaxLines = 2,
            onClick = { onAction(AiReadAloudModelSettingsAction.SelectPreloadCount) },
        )
    }
}

@Immutable
internal data class AiAssistantModelSettingsScreenState(
    val modelSummary: String = "",
    val reasoningSummary: String = "",
    val compactionModelSummary: String = "",
    val contextWindowSummary: String = "",
    val compactionThresholdSummary: String = "",
    val internalMcpEnabled: Boolean = false,
    val internalMcpSummary: String = "",
    val memoryEnabled: Boolean = false,
    val memorySummary: String = "",
    val operationPermissionSummary: String = "",
    val reasoningAvailable: Boolean = false,
)

internal sealed interface AiAssistantModelSettingsAction {
    data object SelectModel : AiAssistantModelSettingsAction
    data object SelectReasoning : AiAssistantModelSettingsAction
    data object SelectCompactionModel : AiAssistantModelSettingsAction
    data object SelectContextWindow : AiAssistantModelSettingsAction
    data object SelectCompactionThreshold : AiAssistantModelSettingsAction
    data class InternalMcpChanged(val enabled: Boolean) : AiAssistantModelSettingsAction
    data class MemoryChanged(val enabled: Boolean) : AiAssistantModelSettingsAction
    data object OpenMemory : AiAssistantModelSettingsAction
    data object OpenOperationPermission : AiAssistantModelSettingsAction
}

@Composable
internal fun AiAssistantModelSettingsScreen(
    state: AiAssistantModelSettingsScreenState,
    onAction: (AiAssistantModelSettingsAction) -> Unit,
) {
    AiSingleGroupSettingsScreen(sectionTitle = stringResource(R.string.ai_assistant)) {
        AiSettingsEntry(
            title = stringResource(R.string.ai_assistant_model_setting),
            summary = state.modelSummary,
            iconRes = R.drawable.ic_ai,
            summaryMaxLines = 2,
            onClick = { onAction(AiAssistantModelSettingsAction.SelectModel) },
        )
        AiSettingsEntry(
            title = stringResource(R.string.ai_assistant_reasoning_level),
            summary = state.reasoningSummary,
            iconRes = R.drawable.ic_ai_capability_reasoning,
            summaryMaxLines = 2,
            modifier = Modifier.alpha(if (state.reasoningAvailable) 1f else 0.55f),
            onClick = { onAction(AiAssistantModelSettingsAction.SelectReasoning) },
        )
        AiSettingsEntry(
            title = stringResource(R.string.ai_context_compaction_model),
            summary = state.compactionModelSummary,
            iconRes = R.drawable.ic_ai,
            summaryMaxLines = 2,
            onClick = { onAction(AiAssistantModelSettingsAction.SelectCompactionModel) },
        )
        AiSettingsEntry(
            title = stringResource(R.string.ai_assistant_context_window),
            summary = state.contextWindowSummary,
            iconRes = R.drawable.ic_ai_context_menu,
            summaryMaxLines = 2,
            onClick = { onAction(AiAssistantModelSettingsAction.SelectContextWindow) },
        )
        AiSettingsEntry(
            title = stringResource(R.string.ai_context_compaction_threshold),
            summary = state.compactionThresholdSummary,
            iconRes = R.drawable.ic_read_aloud_speed,
            summaryMaxLines = 2,
            onClick = { onAction(AiAssistantModelSettingsAction.SelectCompactionThreshold) },
        )
        AiSettingsSwitchEntry(
            title = stringResource(R.string.ai_internal_mcp),
            summary = state.internalMcpSummary,
            iconRes = R.drawable.ic_ai_capability_tool,
            checked = state.internalMcpEnabled,
            onCheckedChange = {
                onAction(AiAssistantModelSettingsAction.InternalMcpChanged(it))
            },
        )
        AiSettingsSwitchEntry(
            title = stringResource(R.string.ai_memory),
            summary = state.memorySummary,
            iconRes = R.drawable.ic_ai_context_menu,
            checked = state.memoryEnabled,
            rowClickToggles = false,
            showClickIndication = true,
            onClick = { onAction(AiAssistantModelSettingsAction.OpenMemory) },
            onCheckedChange = { onAction(AiAssistantModelSettingsAction.MemoryChanged(it)) },
        )
        AiSettingsEntry(
            title = stringResource(R.string.ai_operation_permission),
            summary = state.operationPermissionSummary,
            iconRes = R.drawable.ic_lock_outline,
            summaryMaxLines = 2,
            onClick = { onAction(AiAssistantModelSettingsAction.OpenOperationPermission) },
        )
    }
}

internal enum class AiPurifyNumberField {
    PARAGRAPH_LIMIT,
    CHAPTER_CONCURRENCY,
    CHAPTER_RETRY_COUNT,
    CHAPTER_SEGMENT_LIMIT,
    CHAPTER_SAMPLE_LIMIT,
}

internal enum class AiPurifyRuleType {
    TYPO,
    NOISE,
    AD,
}

@Immutable
internal data class AiPurifySettingsScreenState(
    val paragraphAutoApply: Boolean = false,
    val paragraphAutoApplySummary: String = "",
    val paragraphIntercept: Boolean = false,
    val paragraphLimit: String = "",
    val chapterAutoApply: Boolean = false,
    val chapterAutoApplySummary: String = "",
    val chapterIntercept: Boolean = false,
    val chapterRuleTypo: Boolean = true,
    val chapterRuleNoise: Boolean = true,
    val chapterRuleAd: Boolean = true,
    val chapterConcurrency: String = "",
    val chapterRetryCount: String = "",
    val chapterSegmentLimit: String = "",
    val chapterSampleLimit: String = "",
)

internal sealed interface AiPurifySettingsAction {
    data class ParagraphAutoApplyChanged(val enabled: Boolean) : AiPurifySettingsAction
    data class ParagraphInterceptChanged(val enabled: Boolean) : AiPurifySettingsAction
    data class ChapterAutoApplyChanged(val enabled: Boolean) : AiPurifySettingsAction
    data class ChapterInterceptChanged(val enabled: Boolean) : AiPurifySettingsAction
    data class ChapterRuleTypeChanged(
        val type: AiPurifyRuleType,
        val enabled: Boolean,
    ) : AiPurifySettingsAction
    data class NumberChanged(
        val field: AiPurifyNumberField,
        val value: String,
    ) : AiPurifySettingsAction
    data class NumberFocusLost(val field: AiPurifyNumberField) : AiPurifySettingsAction
}

@Composable
internal fun AiPurifySettingsScreen(
    state: AiPurifySettingsScreenState,
    onAction: (AiPurifySettingsAction) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val numberKeyboardActions = KeyboardActions(
        onDone = { focusManager.clearFocus() },
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 20.dp),
    ) {
        NgSettingsSectionLabel(stringResource(R.string.ai_purify_paragraph_section))
        NgFormControlGroup(
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            NgFormSwitchSettingRow(
                title = stringResource(R.string.ai_purify_auto_apply),
                summary = state.paragraphAutoApplySummary,
                checked = state.paragraphAutoApply,
                onCheckedChange = {
                    onAction(AiPurifySettingsAction.ParagraphAutoApplyChanged(it))
                },
            )
            NgFormGroupDivider(horizontalPadding = 14.dp)
            NgFormSwitchSettingRow(
                title = stringResource(R.string.ai_purify_exception_intercept),
                summary = stringResource(R.string.ai_purify_exception_intercept_summary),
                checked = state.paragraphIntercept,
                onCheckedChange = {
                    onAction(AiPurifySettingsAction.ParagraphInterceptChanged(it))
                },
            )
            NgFormGroupDivider(horizontalPadding = 14.dp)
            AiPurifyNumberFormRow(
                title = stringResource(R.string.ai_purify_paragraph_limit),
                summary = stringResource(R.string.ai_purify_paragraph_limit_summary),
                field = AiPurifyNumberField.PARAGRAPH_LIMIT,
                value = state.paragraphLimit,
                keyboardActions = numberKeyboardActions,
                onAction = onAction,
            )
        }

        Spacer(Modifier.height(18.dp))
        NgSettingsSectionLabel(stringResource(R.string.ai_purify_chapter_section))
        NgFormControlGroup(
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            NgFormSwitchSettingRow(
                title = stringResource(R.string.ai_purify_auto_apply),
                summary = state.chapterAutoApplySummary,
                checked = state.chapterAutoApply,
                onCheckedChange = {
                    onAction(AiPurifySettingsAction.ChapterAutoApplyChanged(it))
                },
            )
            NgFormGroupDivider(horizontalPadding = 14.dp)
            NgFormSwitchSettingRow(
                title = stringResource(R.string.ai_purify_exception_intercept),
                summary = stringResource(R.string.ai_purify_chapter_exception_intercept_summary),
                checked = state.chapterIntercept,
                onCheckedChange = {
                    onAction(AiPurifySettingsAction.ChapterInterceptChanged(it))
                },
            )
            NgFormGroupDivider(horizontalPadding = 14.dp)
            NgFormPanelSectionTitle(
                title = stringResource(R.string.ai_purify_chapter_rule_types),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            NgFormGroupDivider(horizontalPadding = 14.dp)
            NgFormSwitchSettingRow(
                title = stringResource(R.string.ai_purify_rule_type_typo_full),
                checked = state.chapterRuleTypo,
                onCheckedChange = {
                    onAction(
                        AiPurifySettingsAction.ChapterRuleTypeChanged(
                            AiPurifyRuleType.TYPO,
                            it,
                        )
                    )
                },
            )
            NgFormGroupDivider(horizontalPadding = 14.dp)
            NgFormSwitchSettingRow(
                title = stringResource(R.string.ai_purify_rule_type_noise_full),
                checked = state.chapterRuleNoise,
                onCheckedChange = {
                    onAction(
                        AiPurifySettingsAction.ChapterRuleTypeChanged(
                            AiPurifyRuleType.NOISE,
                            it,
                        )
                    )
                },
            )
            NgFormGroupDivider(horizontalPadding = 14.dp)
            NgFormSwitchSettingRow(
                title = stringResource(R.string.ai_purify_rule_type_ad_full),
                checked = state.chapterRuleAd,
                onCheckedChange = {
                    onAction(
                        AiPurifySettingsAction.ChapterRuleTypeChanged(
                            AiPurifyRuleType.AD,
                            it,
                        )
                    )
                },
            )
            NgFormGroupDivider(horizontalPadding = 14.dp)
            AiPurifyNumberFormRow(
                title = stringResource(R.string.ai_purify_chapter_concurrency_limit),
                summary = stringResource(R.string.ai_purify_chapter_concurrency_limit_summary),
                field = AiPurifyNumberField.CHAPTER_CONCURRENCY,
                value = state.chapterConcurrency,
                keyboardActions = numberKeyboardActions,
                onAction = onAction,
            )
            NgFormGroupDivider(horizontalPadding = 14.dp)
            AiPurifyNumberFormRow(
                title = stringResource(R.string.ai_purify_chapter_retry_count),
                summary = stringResource(R.string.ai_purify_chapter_retry_count_summary),
                field = AiPurifyNumberField.CHAPTER_RETRY_COUNT,
                value = state.chapterRetryCount,
                keyboardActions = numberKeyboardActions,
                onAction = onAction,
            )
            NgFormGroupDivider(horizontalPadding = 14.dp)
            AiPurifyNumberFormRow(
                title = stringResource(R.string.ai_purify_chapter_segment_limit),
                summary = stringResource(R.string.ai_purify_chapter_segment_limit_summary),
                field = AiPurifyNumberField.CHAPTER_SEGMENT_LIMIT,
                value = state.chapterSegmentLimit,
                keyboardActions = numberKeyboardActions,
                onAction = onAction,
            )
            NgFormGroupDivider(horizontalPadding = 14.dp)
            AiPurifyNumberFormRow(
                title = stringResource(R.string.ai_purify_chapter_sample_limit),
                summary = stringResource(R.string.ai_purify_chapter_sample_limit_summary),
                field = AiPurifyNumberField.CHAPTER_SAMPLE_LIMIT,
                value = state.chapterSampleLimit,
                keyboardActions = numberKeyboardActions,
                onAction = onAction,
            )
        }
    }
}

@Composable
private fun AiSingleGroupSettingsScreen(
    sectionTitle: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 20.dp),
    ) {
        NgSettingsSectionLabel(sectionTitle)
        NgSettingsGroup { content() }
    }
}

@Composable
private fun AiSettingsEntry(
    title: String,
    summary: String,
    @DrawableRes iconRes: Int,
    summaryMaxLines: Int = 1,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    NgSettingsItem(
        title = title,
        summary = summary,
        summaryMaxLines = summaryMaxLines,
        modifier = modifier,
        trailingSpacing = 0.dp,
        onClick = onClick,
        leading = {
            NgSettingsIcon(
                painter = painterResource(iconRes),
                contentDescription = null,
            )
        },
    )
}

@Composable
private fun AiSettingsSwitchEntry(
    title: String,
    summary: String,
    @DrawableRes iconRes: Int,
    checked: Boolean,
    rowClickToggles: Boolean = true,
    showClickIndication: Boolean = false,
    onClick: (() -> Unit)? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    NgSettingsItem(
        title = title,
        summary = summary,
        summaryMaxLines = 2,
        trailing = NgSettingsTrailing.SWITCH,
        checked = checked,
        onCheckedChange = onCheckedChange,
        trailingSpacing = 0.dp,
        showClickIndication = showClickIndication,
        onClick = onClick ?: if (rowClickToggles) {
            { onCheckedChange(!checked) }
        } else {
            null
        },
        leading = {
            NgSettingsIcon(
                painter = painterResource(iconRes),
                contentDescription = null,
            )
        },
    )
}

@Composable
private fun AiPurifyNumberFormRow(
    title: String,
    summary: String,
    field: AiPurifyNumberField,
    value: String,
    keyboardActions: KeyboardActions,
    onAction: (AiPurifySettingsAction) -> Unit,
) {
    NgFormNumberSettingRow(
        title = title,
        summary = summary,
        value = value,
        onValueChange = {
            onAction(AiPurifySettingsAction.NumberChanged(field, it))
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = keyboardActions,
        onFocusLost = {
            onAction(AiPurifySettingsAction.NumberFocusLost(field))
        },
    )
}
