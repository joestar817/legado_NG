package io.legado.app.ui.book.source.manage

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import io.legado.app.ui.design.components.compose.NgFileSelectionCheckbox
import io.legado.app.ui.design.components.compose.NgFileSelectionCheckboxVariant
import io.legado.app.ui.design.components.compose.NgFormActionButton
import io.legado.app.ui.design.components.compose.NgFormActionButtonAppearance
import io.legado.app.ui.design.theme.NgTheme

private data class AutoGroupRuleItem(
    val type: BookSourceAutoGroupRuleType,
    @param:StringRes val titleRes: Int,
    @param:StringRes val summaryRes: Int,
)

@Composable
internal fun BookSourceAutoGroupDialog(
    onDismiss: () -> Unit,
    onConfirm: (Set<BookSourceAutoGroupRuleType>) -> Unit,
) {
    val ruleItems = remember {
        listOf(
            AutoGroupRuleItem(
                type = BookSourceAutoGroupRuleType.SOURCE_CATEGORY,
                titleRes = R.string.auto_group_source_category,
                summaryRes = R.string.auto_group_source_category_summary,
            ),
            AutoGroupRuleItem(
                type = BookSourceAutoGroupRuleType.DEBUG_FEATURES,
                titleRes = R.string.auto_group_debug_features,
                summaryRes = R.string.auto_group_debug_features_summary,
            ),
            AutoGroupRuleItem(
                type = BookSourceAutoGroupRuleType.URL_CATEGORY,
                titleRes = R.string.auto_group_url_category,
                summaryRes = R.string.auto_group_url_category_summary,
            ),
        )
    }
    var selectedRuleTypes by remember {
        mutableStateOf<Set<BookSourceAutoGroupRuleType>>(
            setOf(BookSourceAutoGroupRuleType.SOURCE_CATEGORY)
        )
    }

    fun toggle(type: BookSourceAutoGroupRuleType) {
        selectedRuleTypes = selectedRuleTypes.toMutableSet().apply {
            if (!add(type)) remove(type)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        NgDialog(
            title = stringResource(R.string.auto_group),
            modifier = Modifier
                .padding(horizontal = 18.dp)
                .widthIn(max = 520.dp),
            variant = NgDialogVariant.LONG_CONTENT,
            actions = {
                NgFormActionButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                    appearance = NgFormActionButtonAppearance.DIALOG,
                )
                NgFormActionButton(
                    text = stringResource(R.string.ok),
                    onClick = { onConfirm(selectedRuleTypes) },
                    enabled = selectedRuleTypes.isNotEmpty(),
                    variant = NgButtonVariant.PRIMARY,
                    appearance = NgFormActionButtonAppearance.DIALOG,
                )
            },
        ) {
            ruleItems.forEachIndexed { index, item ->
                if (index > 0) NgDialogDivider()
                AutoGroupRuleRow(
                    item = item,
                    selected = item.type in selectedRuleTypes,
                    onToggle = { toggle(item.type) },
                )
            }
        }
    }
}

@Composable
private fun AutoGroupRuleRow(
    item: AutoGroupRuleItem,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clickable(role = Role.Checkbox, onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NgFileSelectionCheckbox(
            checked = selected,
            onCheckedChange = { onToggle() },
            variant = NgFileSelectionCheckboxVariant.COMPACT,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp, end = 4.dp),
        ) {
            Text(
                text = stringResource(item.titleRes),
                color = Color(NgTheme.colors.onSurface),
                fontSize = 14.sp,
                lineHeight = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(item.summaryRes),
                color = Color(NgTheme.colors.onSurfaceVariant),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
