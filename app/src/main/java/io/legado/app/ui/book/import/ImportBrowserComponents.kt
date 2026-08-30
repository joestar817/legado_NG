package io.legado.app.ui.book.import

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.design.components.NgButtonShapeVariant
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.compose.NgButton
import io.legado.app.ui.design.components.compose.NgFlatActionRail
import io.legado.app.ui.design.components.compose.NgFlatActionRailItem
import io.legado.app.ui.design.components.compose.NgFlatActionRailVariant
import io.legado.app.ui.design.theme.NgTheme

@Composable
internal fun ImportBrowserPathRow(
    pathText: String,
    showGoUp: Boolean,
    onGoUp: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = pathText,
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            color = Color(NgTheme.colors.onSurfaceVariant),
            fontSize = 13.sp,
            lineHeight = 17.sp,
            maxLines = 1,
        )
        if (showGoUp) {
            TextButton(
                onClick = onGoUp,
                modifier = Modifier.height(36.dp),
                contentPadding = PaddingValues(horizontal = 10.dp),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color(NgTheme.colors.primary),
                ),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_drop_up),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(3.dp))
                Text(stringResource(R.string.go_back), fontSize = 13.sp)
            }
        }
    }
}

@Composable
internal fun ImportSelectionDock(
    selectedCount: Int,
    itemCount: Int,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onAddSelected: () -> Unit,
    @StringRes actionLabelRes: Int = R.string.nb_file_add_shelf,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(start = 12.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.bookshelf_manage_selected_count, selectedCount),
            modifier = Modifier.weight(1f),
            color = Color(NgTheme.colors.onSurface),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
        Box(modifier = Modifier.width(146.dp)) {
            NgFlatActionRail(
                items = listOf(
                    NgFlatActionRailItem(
                        iconRes = R.drawable.ic_select_all,
                        label = stringResource(R.string.select_all),
                        enabled = itemCount > 0,
                    ),
                    NgFlatActionRailItem(
                        iconRes = R.drawable.ic_refresh_black_24dp,
                        label = stringResource(R.string.revert_selection),
                        enabled = itemCount > 0,
                    ),
                ),
                onItemClick = { index ->
                    if (index == 0) onSelectAll() else onInvertSelection()
                },
                variant = NgFlatActionRailVariant.INLINE_DIVIDED,
            )
        }
        Spacer(Modifier.width(6.dp))
        NgButton(
            onClick = onAddSelected,
            enabled = selectedCount > 0,
            variant = NgButtonVariant.PRIMARY_LIGHT_CONTENT,
            modifier = Modifier
                .widthIn(min = 92.dp)
                .height(38.dp),
            shapeVariant = NgButtonShapeVariant.ROUNDED,
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) {
            Text(
                text = stringResource(actionLabelRes),
                fontSize = 13.sp,
                maxLines = 1,
            )
        }
    }
}
