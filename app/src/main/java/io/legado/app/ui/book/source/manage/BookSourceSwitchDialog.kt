package io.legado.app.ui.book.source.manage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.legado.app.R
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.NgDialogDivider
import io.legado.app.ui.design.components.compose.NgFormActionButton
import io.legado.app.ui.design.components.compose.NgFormActionButtonAppearance
import io.legado.app.ui.design.components.compose.NgSwitchControl
import io.legado.app.ui.design.components.compose.NgSwitchControlVariant
import io.legado.app.ui.design.theme.NgTheme

@Composable
internal fun BookSourceSwitchDialog(
    source: BookSourcePart,
    onDismiss: () -> Unit,
    onConfirm: (searchEnabled: Boolean, exploreEnabled: Boolean) -> Unit,
) {
    var searchEnabled by remember(source.bookSourceUrl) { mutableStateOf(source.enabled) }
    var exploreEnabled by remember(source.bookSourceUrl) {
        mutableStateOf(source.enabledExplore)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        NgDialog(
            title = source.bookSourceName,
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
                    text = stringResource(R.string.ok),
                    onClick = {
                        onConfirm(
                            searchEnabled,
                            if (source.hasExploreUrl) exploreEnabled else source.enabledExplore,
                        )
                    },
                    variant = NgButtonVariant.PRIMARY,
                    appearance = NgFormActionButtonAppearance.DIALOG,
                )
            },
        ) {
            BookSourceSwitchRow(
                tag = stringResource(R.string.book_source_capability_search_short),
                tagContainerColor = colorResource(R.color.ng_success_container),
                tagContentColor = colorResource(R.color.ng_success),
                title = stringResource(R.string.book_source_capability_search),
                checked = searchEnabled,
                onCheckedChange = { searchEnabled = it },
            )
            NgDialogDivider()
            BookSourceSwitchRow(
                tag = stringResource(R.string.book_source_capability_explore_short),
                tagContainerColor = colorResource(R.color.ng_info_container),
                tagContentColor = colorResource(R.color.ng_info),
                title = stringResource(R.string.book_source_capability_explore),
                checked = if (source.hasExploreUrl) exploreEnabled else false,
                enabled = source.hasExploreUrl,
                onCheckedChange = { exploreEnabled = it },
            )
        }
    }
}

@Composable
internal fun BookSourceSelectionCapabilityDialog(
    sources: List<BookSourcePart>,
    onDismiss: () -> Unit,
    onConfirm: (searchEnabled: Boolean, exploreEnabled: Boolean) -> Unit,
) {
    val exploreSources = remember(sources) { sources.filter(BookSourcePart::hasExploreUrl) }
    var searchEnabled by remember(sources) {
        mutableStateOf(sources.isNotEmpty() && sources.all(BookSourcePart::enabled))
    }
    var exploreEnabled by remember(exploreSources) {
        mutableStateOf(
            exploreSources.isNotEmpty() && exploreSources.all(BookSourcePart::enabledExplore)
        )
    }
    val hasExploreSources = exploreSources.isNotEmpty()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        NgDialog(
            title = stringResource(R.string.book_source_capability_manage),
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
                    text = stringResource(R.string.ok),
                    onClick = { onConfirm(searchEnabled, exploreEnabled) },
                    variant = NgButtonVariant.PRIMARY,
                    appearance = NgFormActionButtonAppearance.DIALOG,
                )
            },
        ) {
            BookSourceSwitchRow(
                tag = stringResource(R.string.book_source_capability_search_short),
                tagContainerColor = colorResource(R.color.ng_success_container),
                tagContentColor = colorResource(R.color.ng_success),
                title = stringResource(R.string.book_source_capability_search),
                checked = searchEnabled,
                onCheckedChange = { searchEnabled = it },
            )
            NgDialogDivider()
            BookSourceSwitchRow(
                tag = stringResource(R.string.book_source_capability_explore_short),
                tagContainerColor = colorResource(R.color.ng_info_container),
                tagContentColor = colorResource(R.color.ng_info),
                title = stringResource(R.string.book_source_capability_explore),
                checked = exploreEnabled,
                enabled = hasExploreSources,
                onCheckedChange = { exploreEnabled = it },
            )
        }
    }
}

@Composable
private fun BookSourceSwitchRow(
    tag: String,
    tagContainerColor: Color,
    tagContentColor: Color,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(
                enabled = enabled,
                role = Role.Switch,
                onClick = { onCheckedChange(!checked) },
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(5.dp),
            color = tagContainerColor,
        ) {
            Text(
                text = tag,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                color = tagContentColor.copy(alpha = if (enabled) 1f else 0.45f),
                fontSize = 9.sp,
                lineHeight = 11.sp,
                maxLines = 1,
            )
        }
        Text(
            text = title,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
            color = Color(NgTheme.colors.onSurface).copy(alpha = if (enabled) 1f else 0.45f),
            fontSize = 15.sp,
            lineHeight = 19.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        NgSwitchControl(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            variant = NgSwitchControlVariant.COMPACT,
        )
    }
}
