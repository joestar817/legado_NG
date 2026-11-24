package io.legado.app.ui.config

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.help.ai.AiModel
import io.legado.app.help.ai.AiModelAbility
import io.legado.app.help.ai.AiModelCapabilityField
import io.legado.app.help.ai.AiModelGenerationIssue
import io.legado.app.help.ai.AiModelGenerationTestProtocol
import io.legado.app.help.ai.AiModelInterfacePreference
import io.legado.app.help.ai.AiModelModality
import io.legado.app.help.ai.AiModelType
import io.legado.app.help.ai.modelEffortChoices
import io.legado.app.help.ai.AiModelInfo
import io.legado.app.help.ai.AiModelSettings
import io.legado.app.help.ai.AiProviderSetting
import io.legado.app.help.ai.AiProviderType
import io.legado.app.help.ai.effectiveModelCapabilities
import io.legado.app.help.ai.AiWebTool
import io.legado.app.help.ai.deepSeekWebRoute
import io.legado.app.help.ai.isOpenAiAccountProvider
import io.legado.app.help.ai.usesAccountLogin
import io.legado.app.help.ai.knownModalities
import io.legado.app.help.ai.AiModelValue
import io.legado.app.help.ai.modelGenerationTestProtocols
import io.legado.app.help.ai.modelReasoning
import io.legado.app.help.ai.modelSettingsFor
import io.legado.app.help.ai.nativeModelReasoning
import io.legado.app.help.ai.reasoningCatalogInfo
import io.legado.app.help.ai.resolveModelGenerationRoute
import io.legado.app.help.ai.selectableEffortLevels
import io.legado.app.help.ai.sharedModelEfforts
import io.legado.app.ui.design.components.NgStatusTagStyle
import io.legado.app.ui.design.components.NgStatusTagVariant
import io.legado.app.ui.design.components.compose.NgFormActionButton
import io.legado.app.ui.design.components.compose.NgFormActionButtonAppearance
import io.legado.app.ui.design.components.compose.NgFormField
import io.legado.app.ui.design.components.compose.NgFormFieldVariant
import io.legado.app.ui.design.components.compose.NgFormGroupDivider
import io.legado.app.ui.design.components.compose.NgFormInlineTextRow
import io.legado.app.ui.design.components.compose.NgFormInlineTextRowVariant
import io.legado.app.ui.design.components.compose.NgFormMultiSelectRow
import io.legado.app.ui.design.components.compose.NgFormPanel
import io.legado.app.ui.design.components.compose.NgFormPanelSectionTitle
import io.legado.app.ui.design.components.compose.NgFormSelectMenuVariant
import io.legado.app.ui.design.components.compose.NgFormSelectOption
import io.legado.app.ui.design.components.compose.NgFormSelectRow
import io.legado.app.ui.design.components.compose.NgLongDrawerHeader
import io.legado.app.ui.design.components.compose.NgStatusTag
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.utils.openUrl
import java.text.NumberFormat

private val editableModalities = listOf(AiModelModality.TEXT, AiModelModality.IMAGE, AiModelModality.VIDEO)
private val editableAbilities = listOf(AiModelAbility.TOOL, AiModelAbility.REASONING)
private val capabilityColumnWeights = listOf(1.2f, 1f, 0.8f)

@Composable
internal fun AiModelEditSheet(
    provider: AiProviderSetting,
    model: AiModel,
    info: AiModelInfo,
    modelForDraft: (AiModelEditDraft) -> AiModel,
    onSave: (AiModelEditDraft?) -> Unit,
) {
    val initial = remember(provider, model, info) {
        val effective = provider.effectiveModelCapabilities(model)
        AiModelEditDraft(
            displayName = model.safeDisplayName().ifBlank { info.name?.value ?: model.displayName() },
            type = effective.safeType(),
            inputModalities = effective.safeInputModalities().toSet(),
            outputModalities = effective.safeOutputModalities().toSet(),
            abilities = effective.safeAbilities().toSet(),
            modelSettings = model.modelSettingsFor(info.endpoint) ?: AiModelSettings(info.endpoint),
        )
    }
    var draft by remember(initial) { mutableStateOf(initial) }
    var showRestore by remember { mutableStateOf(false) }
    var showGenerationTest by remember { mutableStateOf(false) }
    var contextInput by remember(initial) { mutableStateOf<String?>(null) }
    var outputInput by remember(initial) { mutableStateOf<String?>(null) }
    val settings = requireNotNull(draft.modelSettings)
    val subscription = provider.isOpenAiAccountProvider()
    val catalogContext = info.contextWindow
    val catalogOutput = info.maxOutputTokens
    val enteredContext = contextInput.positiveTokenCount()
    val enteredOutput = outputInput.positiveTokenCount()
    val invalidContext = contextInput?.isNotBlank() == true && enteredContext == null
    val invalidOutput = outputInput?.isNotBlank() == true && enteredOutput == null
    val invalidSpecifications = invalidContext || invalidOutput
    val referenceSettings = settings.copy(
        referenceContextWindowTokens = if (contextInput != null && !invalidContext) {
            enteredContext?.takeUnless { it == catalogContext?.value }
        } else {
            settings.referenceContextWindowTokens
        },
        referenceMaxOutputTokens = if (outputInput != null && !invalidOutput) {
            enteredOutput?.takeUnless { it == catalogOutput?.value }
        } else {
            settings.referenceMaxOutputTokens
        },
    )
    val registry = remember(model) { model.registryCapabilityDefaults() }
    val draftModel = remember(draft) { modelForDraft(draft) }
    val nativeWeb = remember(provider, draftModel) { provider.deepSeekWebRoute(draftModel) }
    val efforts = remember(provider, draftModel) { provider.modelReasoning(draftModel).efforts }
    val detected = remember(provider, info, registry, model) {
        val native = provider.nativeModelReasoning(model.id, provider.reasoningCatalogInfo(model))
        val detectedEfforts = native?.efforts ?: info.effortLevels?.let { info.selectableEffortLevels() }
            ?: sharedModelEfforts(model.id)
        val capabilities = detectedModelCapabilities(info, registry, detectedEfforts)
        if (native != null && info.reasoning == null) capabilities.copy(reasoning = true) else capabilities
    }
    val reasoningExplicitlyUnsupported = info.reasoning?.value == false
    val showChatSettings = draft.type == AiModelType.CHAT
    val interfacePreference = settings.interfacePreference ?: AiModelInterfacePreference.PROVIDER_DEFAULT
    val usesProviderInterface = interfacePreference == AiModelInterfacePreference.PROVIDER_DEFAULT
    val providerProtocols = remember(provider) { provider.modelGenerationTestProtocols() }
    val showInterfaceType = showChatSettings && !provider.usesAccountLogin() && provider.type == AiProviderType.OPENAI &&
        (providerProtocols.size > 1 || !usesProviderInterface)
    val unsupportedInterface = showInterfaceType && remember(provider, draftModel) {
        provider.resolveModelGenerationRoute(draftModel).issue == AiModelGenerationIssue.UNSUPPORTED_PROTOCOL
    }
    val invalidInterfaceOverride = unsupportedInterface && !usesProviderInterface
    val canRestore = detected.hasValues || detected.effortLevelsKnown || catalogContext != null ||
        catalogOutput != null || !usesProviderInterface ||
        settings.nativeWebSearch != null || settings.nativeReadLinks != null

    fun editCapability(edited: AiModelEditDraft, field: AiModelCapabilityField) {
        val current = requireNotNull(edited.modelSettings)
        draft = edited.copy(modelSettings = current.withCapabilityOverrides(current.overriddenCapabilities() + field))
    }

    Column(Modifier.fillMaxSize().padding(start = 16.dp, top = 6.dp, end = 16.dp)) {
        NgLongDrawerHeader(
            title = stringResource(R.string.ai_edit_model),
            navigationIconRes = R.drawable.ic_restore.takeIf { canRestore },
            navigationContentDescription = stringResource(R.string.ai_model_restore_detected),
            navigationActive = true,
            onNavigationClick = if (canRestore) ({ showRestore = true }) else null,
            actionIconRes = R.drawable.ic_save,
            actionContentDescription = stringResource(R.string.save),
            actionActive = !showChatSettings || (!invalidSpecifications && !invalidInterfaceOverride),
            onActionClick = save@{
                if (showChatSettings && (invalidSpecifications || invalidInterfaceOverride)) return@save
                val updatedDraft = draft.copy(modelSettings = referenceSettings)
                onSave(updatedDraft.takeUnless { it == initial })
            },
            centerTitle = true,
        )
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                .padding(top = 2.dp, bottom = 8.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            AiModelIntro(draftModel, info, provider.iconRes(), AiProviderHelpDocuments.modelPage(provider))

            NgFormPanel {
                NgFormPanelSectionTitle(stringResource(R.string.ai_model_name_and_type))
                NgFormInlineTextRow(
                    title = stringResource(R.string.ai_model_display_name),
                    value = draft.displayName,
                    onValueChange = { name ->
                        draft = draft.copy(displayName = name.replace('\r', ' ').replace('\n', ' '))
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    variant = NgFormInlineTextRowVariant.WRAPPING_VALUE,
                )
                NgFormGroupDivider()
                NgFormSelectRow(
                    title = stringResource(R.string.ai_model_type),
                    selectedValue = draft.type.name,
                    options = AiModelType.entries.map { type ->
                        NgFormSelectOption(label = modelTypeLabel(type), value = type.name)
                    },
                    onValueChange = {
                        val type = AiModelType.valueOf(it)
                        if (type != draft.type) editCapability(draft.copy(type = type), AiModelCapabilityField.TYPE)
                    },
                    arrowIcon = painterResource(R.drawable.ic_ng_spinner_arrow_down),
                    menuVariant = NgFormSelectMenuVariant.END_ANCHORED_CONTENT,
                )
                if (showInterfaceType) {
                    NgFormGroupDivider()
                    NgFormSelectRow(
                        title = stringResource(R.string.ai_model_test_protocol),
                        selectedValue = interfacePreference.name,
                        options = listOf(
                            NgFormSelectOption(
                                stringResource(R.string.ai_model_interface_provider_default),
                                AiModelInterfacePreference.PROVIDER_DEFAULT.name,
                            ),
                            NgFormSelectOption(
                                AiModelGenerationTestProtocol.CHAT_COMPLETIONS.displayName(),
                                AiModelInterfacePreference.CHAT_COMPLETIONS.name,
                                AiModelGenerationTestProtocol.CHAT_COMPLETIONS in providerProtocols,
                            ),
                            NgFormSelectOption(
                                AiModelGenerationTestProtocol.RESPONSES.displayName(),
                                AiModelInterfacePreference.RESPONSES.name,
                                AiModelGenerationTestProtocol.RESPONSES in providerProtocols,
                            ),
                        ),
                        onValueChange = { value ->
                            val preference = AiModelInterfacePreference.valueOf(value)
                            if (preference != interfacePreference) {
                                draft = draft.copy(modelSettings = settings.copy(
                                    interfacePreference = preference.takeUnless {
                                        it == AiModelInterfacePreference.PROVIDER_DEFAULT
                                    },
                                ))
                            }
                        },
                        arrowIcon = painterResource(R.drawable.ic_ng_spinner_arrow_down),
                        menuVariant = NgFormSelectMenuVariant.END_ANCHORED_CONTENT,
                    )
                    if (unsupportedInterface) {
                        Text(
                            text = stringResource(R.string.ai_model_interface_unsupported),
                            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 8.dp),
                            color = Color(NgTheme.colors.error),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                        )
                    }
                }
            }

            if (showChatSettings) {
                NgFormPanel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NgFormPanelSectionTitle(
                            title = stringResource(R.string.ai_model_abilities),
                            modifier = Modifier.weight(1f),
                        )
                        NgFormActionButton(
                            text = stringResource(R.string.ai_model_test_generation),
                            onClick = { showGenerationTest = true },
                            modifier = Modifier.padding(end = 6.dp),
                            appearance = NgFormActionButtonAppearance.TEXT,
                            buttonHeight = 34.dp,
                            minimumWidth = 0.dp,
                            textSize = 13.sp,
                        )
                    }
                    ModelModalityRow(
                        R.string.ai_model_input_modalities, draft.inputModalities, detected.input.orEmpty(),
                    ) { value ->
                        editCapability(draft.copy(inputModalities = draft.inputModalities.toggle(value)),
                            AiModelCapabilityField.INPUT_MODALITIES)
                    }
                    NgFormGroupDivider()
                    ModelModalityRow(
                        R.string.ai_model_output_modalities, draft.outputModalities, detected.output.orEmpty(),
                    ) { value ->
                        editCapability(draft.copy(outputModalities = draft.outputModalities.toggle(value)),
                            AiModelCapabilityField.OUTPUT_MODALITIES)
                    }
                    NgFormGroupDivider()
                    ModelCapabilityRow(
                        title = R.string.ai_model_supported_abilities,
                        items = editableAbilities,
                        selected = if (reasoningExplicitlyUnsupported) draft.abilities - AiModelAbility.REASONING
                            else draft.abilities,
                        label = { abilityLabel(it) },
                        isEnabled = { !reasoningExplicitlyUnsupported || it != AiModelAbility.REASONING },
                        onToggle = { ability ->
                            val field = if (ability == AiModelAbility.TOOL) AiModelCapabilityField.TOOL_CALLING
                                else AiModelCapabilityField.REASONING
                            editCapability(draft.copy(abilities = draft.abilities.toggle(ability)), field)
                        },
                    )
                    NgFormGroupDivider()
                    if (nativeWeb != null) {
                        ModelCapabilityRow(
                            title = R.string.ai_model_builtin_web_tools,
                            items = nativeWeb.supported.toList(),
                            selected = nativeWeb.enabled,
                            label = { tool ->
                                stringResource(when (tool) {
                                    AiWebTool.SEARCH -> R.string.ai_model_web_search
                                    AiWebTool.READ_LINK -> R.string.ai_model_web_read_links
                                })
                            },
                            onToggle = { tool ->
                                // No explicit override is needed for the default checked state.
                                val overrideValue = false.takeIf { tool in nativeWeb.enabled }
                                draft = draft.copy(modelSettings = when (tool) {
                                    AiWebTool.SEARCH -> settings.copy(nativeWebSearch = overrideValue)
                                    AiWebTool.READ_LINK -> settings.copy(nativeReadLinks = overrideValue)
                                })
                            },
                        )
                        NgFormGroupDivider()
                    }
                    val checked = efforts.checked.toSet()
                    NgFormMultiSelectRow(
                        title = stringResource(R.string.ai_catalog_effort_levels),
                        selectedLabel = when {
                            efforts.candidates.isEmpty() -> stringResource(R.string.ai_model_effort_selection_unavailable)
                            checked.isEmpty() -> stringResource(R.string.ai_model_effort_selection_none)
                            else -> stringResource(R.string.ai_model_effort_selection_count, checked.size)
                        },
                        options = (modelEffortChoices + efforts.editable).distinct().map {
                            NgFormSelectOption(label = it, value = it, enabled = it in efforts.editable)
                        },
                        selectedValues = checked,
                        onToggle = { effort ->
                            val updated = checked.toggle(effort)
                            draft = draft.copy(modelSettings = settings.copy(
                                enabledReasoningEfforts = efforts.editable.filter(updated::contains),
                            ))
                        },
                        arrowIcon = painterResource(R.drawable.ic_ng_spinner_arrow_down),
                    )
                }

                NgFormPanel {
                    NgFormPanelSectionTitle(stringResource(R.string.ai_model_specifications))
                    ModelTokenSpecificationField(
                        label = R.string.ai_model_context_window,
                        input = contextInput,
                        reference = referenceSettings.referenceContextWindow(info),
                        catalog = catalogContext,
                        invalid = invalidContext,
                        onValueChange = { contextInput = it },
                    )
                    if (!subscription) {
                        NgFormGroupDivider()
                        ModelTokenSpecificationField(
                            label = R.string.ai_model_max_output,
                            input = outputInput,
                            reference = referenceSettings.referenceMaxOutput(info),
                            catalog = catalogOutput,
                            invalid = invalidOutput,
                            onValueChange = { outputInput = it },
                        )
                    }
                }
            } else {
                NgFormPanel {
                    Text(
                        text = stringResource(R.string.ai_model_type_unsupported),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
                        color = Color(NgTheme.colors.onSurfaceVariant),
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    if (showGenerationTest) {
        AiModelGenerationTestDialog(
            provider = provider,
            model = draftModel,
            onDismissRequest = { showGenerationTest = false },
        )
    }

    if (showRestore) {
        ConfigConfirmationDialog(
            title = stringResource(R.string.ai_model_restore_detected),
            message = stringResource(R.string.ai_model_restore_detected_message),
            cancelText = stringResource(R.string.cancel),
            confirmText = stringResource(R.string.confirm),
            onDismissRequest = { showRestore = false },
            onConfirm = {
                if (catalogContext != null) contextInput = null
                if (catalogOutput != null) outputInput = null
                draft = restoreModelDraft(draft, info, detected)
                showRestore = false
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AiModelIntro(model: AiModel, info: AiModelInfo, providerIcon: Int, officialPage: String?) {
    val context = LocalContext.current
    val capabilities = model.capabilityTags()
    NgFormPanel {
        Box(Modifier.fillMaxWidth().padding(start = 14.dp, top = 11.dp, end = 10.dp, bottom = 11.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(model.iconRes(providerIcon)),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = model.safeDisplayName().ifBlank { info.name?.value ?: model.displayName() },
                        modifier = Modifier.fillMaxWidth().padding(end = if (officialPage != null) 24.dp else 0.dp),
                        color = Color(NgTheme.colors.onSurface),
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        NgStatusTag(
                            text = model.safeId(),
                            variant = NgStatusTagVariant.INFO,
                            modifier = Modifier.align(Alignment.CenterVertically),
                            style = NgStatusTagStyle.COMPACT,
                            wrapText = true,
                        )
                        if (capabilities.isNotEmpty()) {
                            // Measure the badges as a group so they move to the next line together.
                            FlowRow(
                                modifier = Modifier.align(Alignment.CenterVertically),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                capabilities.forEach { AiModelCapabilityIcon(it) }
                            }
                        }
                    }
                }
            }
            if (officialPage != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(44.dp)
                        .clickable(role = Role.Button) { context.openUrl(officialPage) }
                        .padding(top = 3.dp, end = 4.dp),
                    contentAlignment = Alignment.TopEnd,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = stringResource(R.string.ai_model_official_page),
                        tint = Color(NgTheme.colors.primary),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelModalityRow(
    @StringRes title: Int,
    selected: Set<AiModelModality>,
    detected: Set<AiModelModality>,
    onToggle: (AiModelModality) -> Unit,
) {
    val choices = editableModalities + listOf(AiModelModality.AUDIO, AiModelModality.FILE)
        .filter { it in selected || it in detected }
    ModelCapabilityRow(
        title = title,
        items = choices,
        selected = selected,
        label = { modalityLabel(it) },
        onToggle = onToggle,
    )
}

@Composable
private fun <T : Any> ModelCapabilityRow(
    @StringRes title: Int,
    items: List<T>,
    selected: Set<T>,
    label: @Composable (T) -> String,
    isEnabled: (T) -> Boolean = { true },
    onToggle: (T) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
        Box(
            modifier = Modifier.weight(1.15f).heightIn(min = 44.dp).padding(end = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = stringResource(title),
                color = Color(NgTheme.colors.onSurface),
                fontSize = 14.sp,
                lineHeight = 18.sp,
            )
        }
        Column(Modifier.weight(3f)) {
            items.chunked(capabilityColumnWeights.size).forEach { rowItems ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    capabilityColumnWeights.forEachIndexed { index, weight ->
                        val item = rowItems.getOrNull(index)
                        if (item == null) {
                            Spacer(Modifier.weight(weight))
                        } else {
                            ModelCapabilityCheckbox(
                                label = label(item),
                                checked = item in selected,
                                enabled = isEnabled(item),
                                onToggle = { onToggle(item) },
                                modifier = Modifier.weight(weight),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelCapabilityCheckbox(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = Color(if (checked) NgTheme.colors.primary else NgTheme.colors.onSurfaceVariant)
        .copy(alpha = if (enabled) 1f else 0.45f)
    Row(
        modifier = modifier
            .heightIn(min = 44.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() }),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(14.dp).border(1.dp, color, RoundedCornerShape(3.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(12.dp), tint = color)
            }
        }
        Text(
            text = label,
            color = color,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = if (checked) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

@Composable
private fun ModelTokenSpecificationField(
    @StringRes label: Int,
    input: String?,
    reference: Int?,
    catalog: AiModelValue<Int>?,
    invalid: Boolean,
    onValueChange: (String) -> Unit,
) {
    val supportingText = when {
        invalid -> stringResource(R.string.ai_model_positive_tokens)
        catalog != null && (catalog.fromPreviousCatalog || reference != catalog.value) -> {
            val source = if (catalog.fromPreviousCatalog) R.string.ai_model_previous_limit
            else R.string.ai_model_known_limit
            stringResource(source, tokenCount(catalog.value))
        }
        else -> null
    }
    NgFormField(
        label = stringResource(label),
        value = input ?: reference?.toString().orEmpty(),
        onValueChange = onValueChange,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        placeholder = catalog?.value?.toString() ?: stringResource(R.string.ai_model_specification_unknown),
        isError = invalid,
        supportingText = supportingText,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        trailingContent = {
            Text(
                text = "tokens",
                modifier = Modifier.padding(start = 6.dp),
                color = Color(NgTheme.colors.onSurfaceVariant),
                fontSize = 13.sp,
            )
        },
        variant = NgFormFieldVariant.INLINE_END_UNDERLINE,
    )
}

private fun String?.positiveTokenCount(): Int? =
    this?.trim()?.replace(",", "")?.replace("，", "")?.toIntOrNull()?.takeIf { it > 0 }

@Composable
private fun tokenCount(value: Int): String = stringResource(
    R.string.ai_catalog_token_count,
    NumberFormat.getIntegerInstance().format(value),
)

internal data class DetectedModelCapabilities(
    val type: AiModelType?,
    val input: Set<AiModelModality>?,
    val output: Set<AiModelModality>?,
    val abilities: Set<AiModelAbility>?,
    val reasoning: Boolean?,
    val toolCalling: Boolean?,
    val effortLevelsKnown: Boolean,
) {
    val hasValues: Boolean
        get() = type != null || input != null || output != null || abilities != null || reasoning != null || toolCalling != null
}

internal fun detectedModelCapabilities(
    info: AiModelInfo,
    registry: AiModelCapabilityDefaults?,
    detectedEfforts: List<String>? = info.effortLevels?.let { info.selectableEffortLevels() },
): DetectedModelCapabilities = DetectedModelCapabilities(
    type = registry?.type?.takeIf { it == AiModelType.OCR }
        ?: when (info.outputModalities?.knownModalities()?.singleOrNull()) {
            AiModelModality.IMAGE -> AiModelType.IMAGE
            AiModelModality.VIDEO -> AiModelType.VIDEO
            else -> registry?.type ?: AiModelType.CHAT.takeIf {
                AiModelModality.TEXT in info.outputModalities?.knownModalities().orEmpty() ||
                    !detectedEfforts.isNullOrEmpty()
            }
        },
    input = info.inputModalities?.knownModalities() ?: registry?.inputModalities,
    output = info.outputModalities?.knownModalities() ?: registry?.outputModalities,
    abilities = registry?.abilities,
    reasoning = info.reasoning?.value ?: detectedEfforts?.isNotEmpty(),
    toolCalling = info.toolCalling?.value,
    effortLevelsKnown = detectedEfforts != null,
)

internal fun restoreModelDraft(
    draft: AiModelEditDraft,
    info: AiModelInfo,
    detected: DetectedModelCapabilities,
): AiModelEditDraft {
    val settings = requireNotNull(draft.modelSettings)
    val abilities = (detected.abilities ?: draft.abilities).toMutableSet()
    detected.toolCalling?.let { if (it) abilities.add(AiModelAbility.TOOL) else abilities.remove(AiModelAbility.TOOL) }
    detected.reasoning?.let { if (it) abilities.add(AiModelAbility.REASONING) else abilities.remove(AiModelAbility.REASONING) }
    val restored = buildSet {
        if (detected.type != null) add(AiModelCapabilityField.TYPE)
        if (detected.input != null) add(AiModelCapabilityField.INPUT_MODALITIES)
        if (detected.output != null) add(AiModelCapabilityField.OUTPUT_MODALITIES)
        if (detected.abilities != null || detected.toolCalling != null) add(AiModelCapabilityField.TOOL_CALLING)
        if (detected.abilities != null || detected.reasoning != null) add(AiModelCapabilityField.REASONING)
    }
    return draft.copy(
        type = detected.type ?: draft.type,
        inputModalities = detected.input ?: draft.inputModalities,
        outputModalities = detected.output ?: draft.outputModalities,
        abilities = abilities,
        modelSettings = settings.withCapabilityOverrides(settings.overriddenCapabilities() - restored).copy(
            interfacePreference = null,
            nativeWebSearch = null,
            nativeReadLinks = null,
            referenceContextWindowTokens = settings.referenceContextWindowTokens.takeIf { info.contextWindow == null },
            referenceMaxOutputTokens = settings.referenceMaxOutputTokens.takeIf { info.maxOutputTokens == null },
            enabledReasoningEfforts = settings.enabledReasoningEfforts.takeIf {
                !detected.effortLevelsKnown && detected.reasoning != false
            },
        ),
    )
}
