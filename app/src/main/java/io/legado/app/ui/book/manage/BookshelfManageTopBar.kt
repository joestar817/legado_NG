package io.legado.app.ui.book.manage

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.design.components.compose.NgFloatingToolbarBackButton
import io.legado.app.ui.design.components.compose.NgGlassDefaults
import io.legado.app.ui.design.components.compose.NgGlassSurface
import io.legado.app.ui.design.components.compose.NgMaterialRole
import io.legado.app.ui.design.components.compose.NgSearchBar
import io.legado.app.ui.design.components.compose.NgSearchBarVariant
import io.legado.app.ui.design.theme.NgTheme

/**
 * 书架管理页顶栏:只有返回与搜索。
 *
 * 分组与缓存的筛选统一收在列表上方的筛选轨里(见 [BookshelfCacheFilterRail] 与
 * [BookshelfGroupFilterRail]),这里不再挂悬浮菜单。
 */
@Composable
internal fun BookshelfManageTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
) {
    val headerShape = RoundedCornerShape(NgTheme.shapes.mediumDp.dp)
    val headerStyle = NgGlassDefaults.bookDetailStyle(
        containerColor = colorResource(R.color.ng_bookshelf_manage_header_surface)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        NgGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            role = NgMaterialRole.CONTROL,
            shape = headerShape,
            style = headerStyle
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NgFloatingToolbarBackButton(onClick = onBack)
                    NgSearchBar(
                        query = query,
                        onQueryChange = onQueryChange,
                        hint = stringResource(R.string.search_book_key),
                        modifier = Modifier.weight(1f),
                        variant = NgSearchBarVariant.TOOLBAR,
                        containerColor = Color.Transparent,
                        hideHintOnFocus = true
                    )
                }
            }
        }
    }
}
