package io.legado.app.ui.book.source.manage

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.legado.app.R
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.NgDialogDivider
import io.legado.app.ui.design.components.compose.NgDialogTextActionButton
import io.legado.app.ui.design.components.compose.NgFormActionButton
import io.legado.app.ui.design.components.compose.NgFormActionButtonAppearance
import io.legado.app.ui.design.theme.NgTheme

internal enum class BookSourceSectionDeleteMode {
    GROUP_ONLY,
    GROUP_AND_SOURCES,
}

@Composable
internal fun BookSourceDeleteConfirmDialog(
    itemName: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val message = stringResource(R.string.sure_del)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        NgDialog(
            title = stringResource(R.string.draw),
            modifier = Modifier
                .padding(horizontal = 10.dp)
                .widthIn(max = 520.dp)
                .heightIn(min = 156.dp),
            variant = NgDialogVariant.CLASSIC_CONFIRMATION,
            titleFontWeight = FontWeight.Normal,
            actions = {
                NgDialogTextActionButton(
                    text = stringResource(R.string.no),
                    onClick = onDismiss,
                )
                NgDialogTextActionButton(
                    text = stringResource(R.string.yes),
                    onClick = onConfirm,
                )
            },
        ) {
            Text(
                text = buildString {
                    append(message)
                    itemName?.let {
                        append('\n')
                        append(it)
                    }
                },
                color = Color(NgTheme.colors.onSurface),
                fontSize = 18.sp,
                lineHeight = 24.sp,
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
internal fun BookSourceSectionDeleteDialog(
    title: String,
    groupName: String?,
    sourceCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (BookSourceSectionDeleteMode) -> Unit,
) {
    var mode by remember(groupName) {
        mutableStateOf(
            if (groupName == null) {
                BookSourceSectionDeleteMode.GROUP_AND_SOURCES
            } else {
                BookSourceSectionDeleteMode.GROUP_ONLY
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        NgDialog(
            title = stringResource(R.string.book_source_delete_section_title, title),
            modifier = Modifier
                .padding(horizontal = 18.dp)
                .widthIn(max = 520.dp),
            variant = NgDialogVariant.STANDARD,
            titleFontSize = 18.sp,
            titleFontWeight = FontWeight.Medium,
            actions = {
                NgFormActionButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                    appearance = NgFormActionButtonAppearance.DIALOG,
                )
                NgFormActionButton(
                    text = stringResource(R.string.delete),
                    onClick = { onConfirm(mode) },
                    variant = NgButtonVariant.DANGER,
                    appearance = NgFormActionButtonAppearance.DIALOG,
                )
            },
        ) {
            if (groupName != null) {
                BookSourceSectionDeleteChoice(
                    title = stringResource(R.string.book_source_delete_group_only),
                    summary = stringResource(R.string.book_source_delete_group_only_summary),
                    selected = mode == BookSourceSectionDeleteMode.GROUP_ONLY,
                    onClick = { mode = BookSourceSectionDeleteMode.GROUP_ONLY },
                )
                NgDialogDivider()
            }
            BookSourceSectionDeleteChoice(
                title = stringResource(
                    if (groupName == null) {
                        R.string.book_source_delete_ungrouped_sources
                    } else {
                        R.string.book_source_delete_group_and_sources
                    }
                ),
                summary = stringResource(
                    R.string.book_source_delete_group_and_sources_summary,
                    sourceCount,
                ),
                selected = mode == BookSourceSectionDeleteMode.GROUP_AND_SOURCES,
                onClick = { mode = BookSourceSectionDeleteMode.GROUP_AND_SOURCES },
            )
        }
    }
}

@Composable
private fun BookSourceSectionDeleteChoice(
    title: String,
    summary: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                role = Role.RadioButton
                this.selected = selected
            }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .border(
                    width = 1.5.dp,
                    color = Color(
                        if (selected) NgTheme.colors.primary else NgTheme.colors.outline
                    ),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(NgTheme.colors.primary)),
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                text = title,
                color = Color(
                    if (selected) NgTheme.colors.primary else NgTheme.colors.onSurface
                ),
                fontSize = 15.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = summary,
                modifier = Modifier.padding(top = 2.dp),
                color = Color(NgTheme.colors.onSurfaceVariant),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
