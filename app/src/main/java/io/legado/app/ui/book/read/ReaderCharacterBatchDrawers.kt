package io.legado.app.ui.book.read

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.book.character.BookCharacterDrawer
import io.legado.app.ui.book.character.BookCharacterDrawerVariant
import io.legado.app.ui.book.character.BookCharacterLabels
import io.legado.app.ui.book.character.BookCharacterPortraitImage
import io.legado.app.ui.book.character.BookCharacterSilhouetteStyle
import io.legado.app.ui.book.character.forRole
import io.legado.app.ui.book.character.readerCharacterHeaderAccentColor
import io.legado.app.ui.book.character.readerCharacterHeaderActionContainerColor
import io.legado.app.ui.book.character.rememberBookCharacterSilhouetteStyle
import io.legado.app.ui.config.NgThemeSheetActionButton
import io.legado.app.ui.design.components.NgStatusTagStyle
import io.legado.app.ui.design.components.NgStatusTagVariant
import io.legado.app.ui.design.components.compose.NgCircularSelectionIndicator
import io.legado.app.ui.design.components.compose.NgFormActionButton
import io.legado.app.ui.design.components.compose.NgFormActionButtonAppearance
import io.legado.app.ui.design.components.compose.NgFormGroupDivider
import io.legado.app.ui.design.components.compose.NgFormPanel
import io.legado.app.ui.design.components.compose.NgStatusTag
import io.legado.app.ui.design.theme.NgTheme

@Composable
internal fun ReaderCharacterCandidateDrawer(
    chapterNumber: Int,
    candidates: List<ReaderCharacterCandidate>,
    selection: Set<String>,
    onToggle: (String) -> Unit,
    onGenerate: () -> Unit,
    onClose: () -> Unit,
) {
    BookCharacterDrawer(
        title = stringResource(R.string.ai_character_card_candidates),
        onClose = onClose,
        variant = BookCharacterDrawerVariant.READER_AI,
        headerTrailingContent = {
            CharacterBatchHeaderButton(
                text = if (selection.isEmpty()) stringResource(R.string.ai_character_card_generate_short)
                    else pluralStringResource(R.plurals.ai_character_card_generate_count, selection.size, selection.size),
                enabled = selection.isNotEmpty(),
                onClick = onGenerate,
            )
        },
    ) {
        val colors = NgTheme.colors
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 34.dp).padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(R.drawable.ic_ai_locate_selected),
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                tint = Color(colors.primary),
            )
            Text(
                stringResource(R.string.ai_character_card_candidates_scope, chapterNumber),
                color = Color(colors.onSurfaceVariant),
                fontSize = 13.sp,
            )
        }
        NgFormPanel {
            if (candidates.isEmpty()) {
                Text(
                    stringResource(R.string.ai_character_card_candidates_empty),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 19.dp),
                    color = Color(colors.onSurfaceVariant),
                    fontSize = 14.sp,
                )
            }
            candidates.forEachIndexed { index, candidate ->
                key(candidate.name) {
                    if (index > 0) NgFormGroupDivider(horizontalPadding = 14.dp)
                    val checked = candidate.name in selection
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)
                            .toggleable(checked, role = Role.Checkbox) { onToggle(candidate.name) }
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NgCircularSelectionIndicator(
                            if (checked) ToggleableState.On else ToggleableState.Off,
                            Modifier.size(20.dp),
                        )
                        CharacterBatchName(candidate.name)
                        if (candidate.existing != null) {
                            CharacterBatchExistingTag(R.string.ai_character_card_candidate_existing)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ReaderCharacterReviewDrawer(
    candidates: List<ReaderCharacterCandidate>,
    drafts: Map<String, ReaderCharacterBatchDraft>,
    failures: Map<String, String>,
    saveErrors: Map<String, String>,
    selection: Set<String>,
    saving: Boolean,
    automaticStyle: BookCharacterSilhouetteStyle,
    onToggle: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDetails: () -> Unit,
    onRetry: () -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    val storedStyle = rememberBookCharacterSilhouetteStyle()
    BookCharacterDrawer(
        title = stringResource(R.string.ai_character_card_review),
        onClose = onClose,
        saving = saving,
        variant = BookCharacterDrawerVariant.READER_AI_REVIEW,
        headerTrailingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NgThemeSheetActionButton(
                    onClick = onDetails,
                    contentDescription = stringResource(R.string.ai_character_card_details),
                    enabled = !saving,
                    containerColor = readerCharacterHeaderActionContainerColor(),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_ai_character_card_clock_thin),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = readerCharacterHeaderAccentColor().copy(alpha = if (saving) 0.45f else 1f),
                    )
                }
                CharacterBatchHeaderButton(
                    text = when {
                        saving -> stringResource(R.string.ai_character_card_saving)
                        selection.isEmpty() -> stringResource(R.string.save)
                        else -> pluralStringResource(R.plurals.ai_character_card_save_count, selection.size, selection.size)
                    },
                    enabled = !saving && selection.isNotEmpty(),
                    onClick = onSave,
                    containerHeight = 36.dp,
                )
            }
        },
    ) {
        val colors = NgTheme.colors
        NgFormPanel(modifier = Modifier.padding(top = 12.dp)) {
            candidates.forEachIndexed { index, candidate ->
                key(candidate.name) {
                    if (index > 0) NgFormGroupDivider(horizontalPadding = 14.dp)
                    val draft = drafts[candidate.name]
                    if (draft == null) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            CharacterBatchName(candidate.name)
                            CharacterBatchSubtitle(
                                failures[candidate.name] ?: stringResource(R.string.ai_character_card_no_preview),
                                error = true,
                            )
                        }
                    } else {
                        val form = draft.value.form
                        val selected = candidate.name in selection
                        val checkLabel = stringResource(R.string.ai_character_card_select_draft, form.name)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            NgCircularSelectionIndicator(
                                state = if (selected) ToggleableState.On else ToggleableState.Off,
                                modifier = Modifier.size(44.dp)
                                    .semantics { contentDescription = checkLabel }
                                    .toggleable(selected, enabled = !saving, role = Role.Checkbox) {
                                        onToggle(candidate.name)
                                    },
                            )
                            Row(
                                modifier = Modifier.weight(1f).heightIn(min = 88.dp)
                                    .clickable(enabled = !saving, role = Role.Button) { onEdit(candidate.name) }
                                    .padding(vertical = 9.dp),
                                horizontalArrangement = Arrangement.spacedBy(9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val portrait = form.portraitUri?.let(Uri::parse)
                                val style = (draft.style ?: storedStyle).resolve(automaticStyle)
                                BookCharacterPortraitImage(
                                    portrait = portrait,
                                    silhouette = style.forRole(form.roleTag, hasPortrait = portrait != null),
                                    contentDescription = null,
                                    modifier = Modifier.size(width = 34.dp, height = 46.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                )
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            form.name,
                                            modifier = Modifier.weight(1f, fill = false),
                                            color = Color(colors.onSurface),
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        if (draft.value.existing != null) {
                                            CharacterBatchExistingTag(R.string.ai_character_card_batch_update_existing)
                                        } else {
                                            Text(
                                                BookCharacterLabels.roleLabel(LocalContext.current, form.roleTag),
                                                color = Color(colors.onSurfaceVariant),
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                    val saveError = saveErrors[candidate.name]
                                    CharacterBatchSubtitle(
                                        saveError ?: form.intro
                                            ?: stringResource(R.string.ai_character_card_pending_intro),
                                        error = saveError != null,
                                    )
                                }
                                Icon(
                                    painterResource(R.drawable.ic_chevron_right_20),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = Color(colors.onSurfaceVariant),
                                )
                            }
                        }
                    }
                }
            }
            if (failures.isNotEmpty()) {
                NgFormGroupDivider(horizontalPadding = 14.dp)
                NgFormActionButton(
                    text = stringResource(R.string.ai_character_card_retry_failed),
                    onClick = onRetry,
                    modifier = Modifier.padding(horizontal = 10.dp),
                    enabled = !saving,
                    appearance = NgFormActionButtonAppearance.TEXT,
                    buttonHeight = 44.dp,
                )
            }
        }
    }
}

@Composable
private fun CharacterBatchHeaderButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    containerHeight: Dp = 32.dp,
) {
    val container = readerCharacterHeaderActionContainerColor()
    Box(
        modifier = Modifier.height(44.dp).clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.heightIn(min = containerHeight)
                .background(container.copy(alpha = container.alpha * if (enabled) 1f else 0.45f), CircleShape)
                .padding(horizontal = 9.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text,
                color = if (enabled) readerCharacterHeaderAccentColor() else Color(NgTheme.colors.onSurfaceVariant),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CharacterBatchName(name: String) {
    Text(
        name,
        color = Color(NgTheme.colors.onSurface),
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun CharacterBatchSubtitle(text: String, error: Boolean = false) {
    val colors = NgTheme.colors
    Text(
        text,
        color = Color(if (error) colors.error else colors.onSurfaceVariant),
        fontSize = 13.sp,
        lineHeight = 18.sp,
        maxLines = if (error) 3 else 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun CharacterBatchExistingTag(label: Int) {
    NgStatusTag(
        text = stringResource(label),
        variant = NgStatusTagVariant.PRIMARY,
        style = NgStatusTagStyle.INLINE,
    )
}
