package io.legado.app.ui.book.character

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.compose.NgFormActionButton
import io.legado.app.ui.design.components.compose.NgFormExpandableRow
import io.legado.app.ui.design.components.compose.NgFormGroupDivider
import io.legado.app.ui.design.components.compose.NgFormInlineTextRow
import io.legado.app.ui.design.components.compose.NgFormMultilineField
import io.legado.app.ui.design.components.compose.NgFormMultilineFieldVariant
import io.legado.app.ui.design.components.compose.NgFormPanel
import io.legado.app.ui.design.components.compose.NgFormSelectMenuVariant
import io.legado.app.ui.design.components.compose.NgFormSelectOption
import io.legado.app.ui.design.components.compose.NgFormSelectRow
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.utils.sendToClip

@Composable
internal fun BookCharacterEditDialogContent(
    title: String,
    confirmText: String,
    initialValue: BookCharacterFormValue,
    saving: Boolean,
    showPortraitOptions: Boolean,
    automaticSilhouetteStyle: BookCharacterSilhouetteStyle,
    onConfirm: (BookCharacterFormValue, BookCharacterSilhouetteStyle?) -> Unit,
) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf(initialValue.name) }
    var aliases by rememberSaveable {
        mutableStateOf(initialValue.aliases.joinToString(", "))
    }
    var gender by rememberSaveable { mutableStateOf(initialValue.gender) }
    var roleTag by rememberSaveable { mutableStateOf(initialValue.roleTag) }
    var intro by rememberSaveable { mutableStateOf(initialValue.intro.orEmpty()) }
    var imagePrompt by rememberSaveable { mutableStateOf(initialValue.imagePrompt.orEmpty()) }
    var portraitUri by rememberSaveable { mutableStateOf(initialValue.portraitUri) }
    val genderOptions = remember(context) {
        BookCharacterLabels.genderValues.map { value ->
            NgFormSelectOption(label = BookCharacterLabels.genderLabel(context, value), value = value)
        }
    }
    val roleOptions = remember(context) {
        BookCharacterLabels.roleValues.map { value ->
            NgFormSelectOption(label = BookCharacterLabels.roleLabel(context, value), value = value)
        }
    }

    val styleDraft = if (showPortraitOptions) {
        rememberSaveable { mutableStateOf<String?>(null) }
    } else null

    fun submit() {
        onConfirm(
            BookCharacterFormValue(
                name = name.trim(),
                aliases = parseCharacterAliases(aliases),
                gender = gender,
                roleTag = roleTag,
                intro = intro.trim().ifBlank { null },
                imagePrompt = imagePrompt.trim().ifBlank { null },
                portraitUri = portraitUri,
            ),
            styleDraft?.value?.let(BookCharacterSilhouetteStyle::fromKey),
        )
    }

    BookCharacterDrawer(
        title = title,
        confirmText = confirmText,
        saveEnabled = !saving,
        onSave = ::submit,
    ) {
        NgFormPanel {
            NgFormInlineTextRow(
                title = stringResource(R.string.character_name),
                value = name,
                onValueChange = { name = it },
                placeholder = stringResource(R.string.character_name_hint),
                enabled = !saving,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            NgFormGroupDivider(horizontalPadding = 14.dp)
            NgFormInlineTextRow(
                title = stringResource(R.string.character_aliases_label),
                value = aliases,
                onValueChange = { aliases = it },
                placeholder = stringResource(R.string.character_aliases_hint),
                enabled = !saving,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            NgFormGroupDivider(horizontalPadding = 14.dp)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val arrow = painterResource(R.drawable.ic_ng_spinner_arrow_down)
                val genderTitle = stringResource(R.string.character_gender)
                val roleTitle = stringResource(R.string.character_role)
                val font = NgTheme.fontFamily
                val density = LocalDensity.current
                val measurer = rememberTextMeasurer()
                val minChoiceWidth = remember(measurer, density, font, genderTitle, roleTitle, genderOptions, roleOptions) {
                    fun width(title: String, options: List<NgFormSelectOption>): Dp = with(density) {
                        val titleWidth = measurer.measure(title, TextStyle(fontFamily = font, fontSize = 14.sp)).size.width
                        val valueWidth = options.maxOf {
                            measurer.measure(it.label, TextStyle(fontFamily = font, fontSize = 13.sp)).size.width
                        }
                        (titleWidth + valueWidth).toDp() + 48.dp
                    }
                    maxOf(width(genderTitle, genderOptions), width(roleTitle, roleOptions))
                }
                val genderField: @Composable (Modifier) -> Unit = { fieldModifier ->
                    NgFormSelectRow(
                        title = genderTitle,
                        selectedValue = gender,
                        options = genderOptions,
                        onValueChange = { gender = it },
                        arrowIcon = arrow,
                        modifier = fieldModifier,
                        enabled = !saving,
                        menuVariant = NgFormSelectMenuVariant.END_ANCHORED_CONTENT,
                        useVisibleWindowBounds = true,
                    )
                }
                val roleField: @Composable (Modifier) -> Unit = { fieldModifier ->
                    NgFormSelectRow(
                        title = roleTitle,
                        selectedValue = roleTag,
                        options = roleOptions,
                        onValueChange = { roleTag = it },
                        arrowIcon = arrow,
                        modifier = fieldModifier,
                        enabled = !saving,
                        menuVariant = NgFormSelectMenuVariant.END_ANCHORED_CONTENT,
                        useVisibleWindowBounds = true,
                    )
                }
                if (maxWidth >= minChoiceWidth * 2 + 1.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        genderField(Modifier.weight(1f))
                        VerticalDivider(
                            modifier = Modifier.height(22.dp),
                            thickness = 0.6.dp,
                            color = Color(NgTheme.colors.outlineVariant).copy(alpha = 0.22f),
                        )
                        roleField(Modifier.weight(1f))
                    }
                } else {
                    Column {
                        genderField(Modifier)
                        NgFormGroupDivider(horizontalPadding = 14.dp)
                        roleField(Modifier)
                    }
                }
            }
            NgFormGroupDivider(horizontalPadding = 14.dp)
            NgFormMultilineField(
                value = intro,
                onValueChange = { intro = it },
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                label = stringResource(R.string.character_intro),
                placeholder = stringResource(R.string.character_intro_hint),
                enabled = !saving,
                minHeight = 63.dp,
                maxHeight = 106.dp,
                minLines = 3,
                maxLines = 5,
                variant = NgFormMultilineFieldVariant.PANEL,
            )
        }
        if (styleDraft != null) {
            val storedStyle = rememberBookCharacterSilhouetteStyle()
            val selectedStyle = styleDraft.value?.let(BookCharacterSilhouetteStyle::fromKey) ?: storedStyle
            var styleExpanded by rememberSaveable { mutableStateOf(false) }
            val focusManager = LocalFocusManager.current
            val selectPortrait = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {
                if (it != null) portraitUri = it.toString()
            }
            CharacterPortraitPanel(
                uri = portraitUri,
                silhouette = selectedStyle.resolve(automaticSilhouetteStyle).forRole(
                    roleTag, hasPortrait = !portraitUri.isNullOrBlank(),
                ),
                imagePrompt = imagePrompt,
                enabled = !saving,
                onPromptChange = { imagePrompt = it },
                onSelect = {
                    focusManager.clearFocus()
                    selectPortrait.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onClear = { portraitUri = null },
            )
            NgFormPanel {
                NgFormExpandableRow(
                    title = stringResource(R.string.character_silhouette_style),
                    value = stringResource(selectedStyle.labelRes),
                    expanded = styleExpanded,
                    onExpandedChange = {
                        focusManager.clearFocus()
                        styleExpanded = it
                    },
                    enabled = !saving,
                ) {
                    NgFormGroupDivider(
                        modifier = Modifier.padding(bottom = 6.dp),
                        horizontalPadding = 0.dp,
                    )
                    BookCharacterSilhouetteOptions(
                        selected = selectedStyle,
                        enabled = !saving,
                        onSelect = { styleDraft.value = it.takeUnless { it == storedStyle }?.key },
                    )
                }
            }
        } else {
            NgFormPanel {
                CharacterImagePromptRow(
                    imagePrompt = imagePrompt,
                    enabled = !saving,
                    onPromptChange = { imagePrompt = it },
                    titleRes = R.string.character_image_prompt,
                )
            }
        }
    }
}

@Composable
private fun CharacterPortraitPanel(
    uri: String?,
    silhouette: BookCharacterSilhouette,
    imagePrompt: String,
    enabled: Boolean,
    onPromptChange: (String) -> Unit,
    onSelect: () -> Unit,
    onClear: () -> Unit,
) {
    val hasPortrait = !uri.isNullOrBlank()
    val portrait = remember(uri) { BookCharacterPortraits.localUri(uri) }
    NgFormPanel {
        val label = stringResource(R.string.character_portrait)
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 62.dp)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BookCharacterPortraitImage(
                portrait = portrait,
                silhouette = silhouette,
                contentDescription = null,
                modifier = Modifier.size(width = 32.dp, height = 42.dp)
                    .clip(RoundedCornerShape(NgTheme.shapes.smallDp.dp)),
            )
            Column(Modifier.weight(1f)) {
                Text(text = label, color = Color(NgTheme.colors.onSurface), fontSize = 14.sp)
                if (portrait == null) {
                    Text(
                        text = stringResource(R.string.character_uses_silhouette),
                        color = Color(NgTheme.colors.onSurfaceVariant),
                        fontSize = 11.sp,
                    )
                }
            }
            NgFormActionButton(
                text = stringResource(if (hasPortrait) R.string.character_portrait_replace else R.string.import_str),
                onClick = onSelect,
                enabled = enabled,
                variant = NgButtonVariant.TONAL,
                minimumWidth = 52.dp,
            )
            if (hasPortrait) {
                NgFormActionButton(
                    text = stringResource(R.string.character_portrait_clear),
                    onClick = onClear,
                    enabled = enabled,
                    variant = NgButtonVariant.OUTLINE,
                    minimumWidth = 52.dp,
                )
            }
        }
        NgFormGroupDivider(horizontalPadding = 14.dp)
        CharacterImagePromptRow(
            imagePrompt = imagePrompt,
            enabled = enabled,
            onPromptChange = onPromptChange,
        )
    }
}

@Composable
private fun CharacterImagePromptRow(
    imagePrompt: String,
    enabled: Boolean,
    onPromptChange: (String) -> Unit,
    @StringRes titleRes: Int = R.string.character_portrait_prompt,
) {
    var promptExpanded by rememberSaveable { mutableStateOf(false) }
    NgFormExpandableRow(
        title = stringResource(titleRes),
        value = stringResource(
            if (imagePrompt.isBlank()) R.string.character_portrait_prompt_empty
            else R.string.character_portrait_prompt_filled,
        ),
        expanded = promptExpanded,
        onExpandedChange = { promptExpanded = it },
        enabled = enabled,
    ) {
        NgFormMultilineField(
            value = imagePrompt,
            onValueChange = onPromptChange,
            placeholder = stringResource(R.string.character_image_prompt_hint),
            enabled = enabled,
            minHeight = 58.dp,
            maxHeight = 106.dp,
            minLines = 2,
            maxLines = 5,
            variant = NgFormMultilineFieldVariant.PANEL,
        )
        if (imagePrompt.isNotBlank()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                ImagePromptCopyAction(imagePrompt)
            }
        }
    }
}

@Composable
private fun ImagePromptCopyAction(text: String) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { context.sendToClip(text) }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_copy),
            contentDescription = stringResource(android.R.string.copy),
            modifier = Modifier.size(14.dp),
            tint = Color(NgTheme.colors.primary),
        )
        Text(
            text = stringResource(android.R.string.copy),
            color = Color(NgTheme.colors.primary),
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}
