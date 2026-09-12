package io.legado.app.ui.rss.source.debug

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.design.components.compose.NgSearchBar
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.rss.RssEmptyState
import io.legado.app.ui.rss.RssPageScaffold
import io.legado.app.ui.rss.RssToolbarAction

@Composable
internal fun RssSourceDebugScreen(
    query: String,
    logs: List<String>,
    sortKinds: List<Pair<String, String>>,
    loading: Boolean,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onShowListSource: () -> Unit,
    onShowContentSource: () -> Unit
) {
    RssPageScaffold(
        title = stringResource(R.string.debug_source),
        onBack = onBack,
        actions = listOf(
            RssToolbarAction(
                R.id.menu_list_src,
                R.string.list_src,
                R.drawable.ic_chapter_list
            ),
            RssToolbarAction(
                R.id.menu_content_src,
                R.string.content_src,
                R.drawable.ic_code
            )
        ),
        onAction = {
            when (it) {
                R.id.menu_list_src -> onShowListSource()
                R.id.menu_content_src -> onShowContentSource()
            }
        }
    ) {
        Column(Modifier.fillMaxSize()) {
            NgSearchBar(
                query = query,
                onQueryChange = onQueryChange,
                hint = stringResource(R.string.search),
                onSearch = onSearch,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { DebugSuggestion("我的") { onSearch("我的") } }
                item { DebugSuggestion("系统") { onSearch("系统") } }
                items(sortKinds, key = { it.first + it.second }) { sort ->
                    DebugSuggestion(sort.first.ifBlank { sort.second }) {
                        onSearch("${sort.first}::${sort.second}")
                    }
                }
            }
            if (loading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(24.dp),
                        color = Color(NgTheme.colors.primary),
                        strokeWidth = 2.dp
                    )
                }
            }
            if (logs.isEmpty() && !loading) {
                RssEmptyState(stringResource(R.string.empty), Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(logs) { log ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(NgTheme.colors.surface).copy(alpha = 0.82f)
                        ) {
                            Text(
                                text = log,
                                modifier = Modifier.padding(12.dp),
                                color = Color(NgTheme.colors.onSurface),
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                fontFamily = NgTheme.fontFamily
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DebugSuggestion(text: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = Color(NgTheme.colors.surfaceContainerLow)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            color = Color(NgTheme.colors.onSurface),
            fontSize = 13.sp,
            maxLines = 1
        )
    }
}

@Composable
internal fun RssDebugSourceDialog(
    title: String,
    content: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                item {
                    Text(
                        text = content,
                        color = Color(NgTheme.colors.onSurface),
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        fontFamily = NgTheme.fontFamily
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        }
    )
}
