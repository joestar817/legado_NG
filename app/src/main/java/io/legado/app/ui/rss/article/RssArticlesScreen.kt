package io.legado.app.ui.rss.article

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.data.entities.RssArticle
import io.legado.app.ui.design.components.compose.NgPullRefreshBox
import io.legado.app.ui.design.components.compose.NgSearchBar
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.rss.RssEmptyState
import io.legado.app.ui.rss.RssPageScaffold
import io.legado.app.ui.rss.RssRemoteImage
import io.legado.app.ui.rss.RssToolbarAction

@Composable
internal fun RssArticlesScreen(
    title: String,
    sorts: List<Pair<String, String>>,
    selectedSort: Int,
    articles: List<RssArticle>,
    articleStyle: Int,
    refreshing: Boolean,
    loadingMore: Boolean,
    hasMore: Boolean,
    loadError: String?,
    searchVisible: Boolean,
    searchQuery: String,
    searchEnabled: Boolean,
    loginVisible: Boolean,
    onBack: () -> Unit,
    onSortSelected: (Int) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onOpenArticle: (RssArticle) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onAction: (Int) -> Unit
) {
    val actions = buildList {
        if (searchEnabled) add(
            RssToolbarAction(R.id.menu_search, R.string.search, R.drawable.ic_search)
        )
        if (loginVisible) add(
            RssToolbarAction(R.id.menu_login, R.string.login, R.drawable.ic_lock_outline)
        )
        add(
            RssToolbarAction(
                R.id.menu_refresh_sort,
                R.string.refresh_sort,
                R.drawable.ic_refresh_black_24dp
            )
        )
        add(
            RssToolbarAction(
                R.id.menu_set_source_variable,
                R.string.set_source_variable,
                R.drawable.ic_code
            )
        )
        add(
            RssToolbarAction(
                R.id.menu_edit_source,
                R.string.edit_source,
                R.drawable.ic_edit
            )
        )
        add(
            RssToolbarAction(
                R.id.menu_switch_layout,
                R.string.switchLayout,
                R.drawable.ic_grid_menu
            )
        )
        add(
            RssToolbarAction(
                R.id.menu_read_record,
                R.string.read_record,
                R.drawable.ic_history
            )
        )
        add(
            RssToolbarAction(
                R.id.menu_clear,
                R.string.clear,
                R.drawable.ic_baseline_close,
                dividerBefore = true
            )
        )
    }
    RssPageScaffold(
        title = title,
        onBack = onBack,
        actions = actions,
        onAction = onAction
    ) {
        Column(Modifier.fillMaxSize()) {
            if (searchVisible) {
                NgSearchBar(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    hint = stringResource(R.string.search),
                    onSearch = onSearch,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            if (sorts.size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sorts.indices.toList(), key = { index ->
                        sorts[index].first + '\u0000' + sorts[index].second
                    }) { index ->
                        val selected = index == selectedSort
                        Surface(
                            onClick = { onSortSelected(index) },
                            shape = RoundedCornerShape(16.dp),
                            color = Color(
                                if (selected) NgTheme.colors.selectedContainer
                                else NgTheme.colors.surfaceContainerLow
                            )
                        ) {
                            Text(
                                text = sorts[index].first.ifBlank { stringResource(R.string.all) },
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                color = Color(NgTheme.colors.onSurface),
                                fontSize = 14.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
            NgPullRefreshBox(
                isRefreshing = refreshing,
                onRefresh = onRefresh,
                modifier = Modifier.weight(1f)
            ) {
                if (articles.isEmpty() && !refreshing) {
                    when {
                        loadingMore -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) { RssLoadingFooter() }
                        loadError != null -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) { RssRetryFooter(enabled = true, onRetry = onRetry) }
                        else -> RssEmptyState(stringResource(R.string.empty))
                    }
                } else {
                    RssArticleCollection(
                        articles = articles,
                        style = articleStyle,
                        hasMore = hasMore,
                        loading = loadingMore,
                        refreshing = refreshing,
                        loadError = loadError,
                        onLoadMore = onLoadMore,
                        onRetry = onRetry,
                        onOpenArticle = onOpenArticle
                    )
                }
            }
        }
    }
}

@Composable
private fun RssArticleCollection(
    articles: List<RssArticle>,
    style: Int,
    hasMore: Boolean,
    loading: Boolean,
    refreshing: Boolean,
    loadError: String?,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onOpenArticle: (RssArticle) -> Unit
) {
    if (style == 0 || style == 1) {
        val state = rememberLazyListState()
        RssListLoadMoreEffect(
            state, articles.size, hasMore && loadError == null, loading || refreshing, onLoadMore
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = state,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(articles, key = { it.origin + '\u0000' + it.sort + '\u0000' + it.link }) {
                RssArticleCard(it, style, onOpenArticle)
            }
            if (articles.isNotEmpty()) {
                if (loading) item { RssLoadingFooter() }
                else if (loadError != null) item {
                    RssRetryFooter(enabled = !refreshing, onRetry = onRetry)
                }
            }
        }
    } else {
        val state = rememberLazyGridState()
        RssGridLoadMoreEffect(
            state, articles.size, hasMore && loadError == null, loading || refreshing, onLoadMore
        )
        val columns = when (style) {
            4 -> 3
            3 -> if (LocalConfiguration.current.orientation ==
                Configuration.ORIENTATION_LANDSCAPE) 3 else 2
            else -> 2
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            state = state,
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(articles, key = { it.origin + '\u0000' + it.sort + '\u0000' + it.link }) {
                RssArticleCard(it, style, onOpenArticle)
            }
            if (articles.isNotEmpty()) {
                if (loading) item(span = { GridItemSpan(maxLineSpan) }) { RssLoadingFooter() }
                else if (loadError != null) item(span = { GridItemSpan(maxLineSpan) }) {
                    RssRetryFooter(enabled = !refreshing, onRetry = onRetry)
                }
            }
        }
    }
}

@Composable
private fun RssArticleCard(
    article: RssArticle,
    style: Int,
    onOpenArticle: (RssArticle) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenArticle(article) },
        shape = RoundedCornerShape(14.dp),
        color = Color(NgTheme.colors.surface).copy(alpha = 0.84f)
    ) {
        if (style == 0) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArticleText(article, Modifier.weight(1f))
                if (!article.image.isNullOrBlank()) {
                    Spacer(Modifier.width(10.dp))
                    RssRemoteImage(
                        imageUrl = article.image,
                        sourceOrigin = article.origin,
                        placeholder = R.drawable.image_rss_article,
                        contentDescription = article.title,
                        modifier = Modifier
                            .size(width = 110.dp, height = 72.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                }
            }
        } else {
            Column {
                if (!article.image.isNullOrBlank() || style in 2..4) {
                    RssRemoteImage(
                        imageUrl = article.image,
                        sourceOrigin = article.origin,
                        placeholder = if (style == 2) {
                            R.drawable.transparent_placeholder
                        } else {
                            R.drawable.image_rss_article
                        },
                        contentDescription = article.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(
                                when (style) {
                                    4 -> 1.15f
                                    3 -> 0.92f
                                    else -> 1.45f
                                }
                            )
                    )
                }
                ArticleText(article, Modifier.padding(10.dp))
            }
        }
    }
}

@Composable
private fun ArticleText(article: RssArticle, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = article.title,
            color = Color(
                if (article.read) NgTheme.colors.onSurfaceVariant else NgTheme.colors.onSurface
            ),
            fontSize = 15.sp,
            lineHeight = 20.sp,
            fontWeight = if (article.read) FontWeight.Normal else FontWeight.Medium,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
        if (!article.pubDate.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = article.pubDate.orEmpty(),
                color = Color(NgTheme.colors.onSurfaceVariant),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RssRetryFooter(enabled: Boolean, onRetry: () -> Unit) {
    TextButton(
        onClick = onRetry,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = stringResource(R.string.load_error_retry),
            color = Color(NgTheme.colors.primary),
            fontSize = 14.sp
        )
    }
}

@Composable
private fun RssLoadingFooter() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            color = Color(NgTheme.colors.primary),
            strokeWidth = 2.dp
        )
    }
}

@Composable
private fun RssListLoadMoreEffect(
    state: LazyListState,
    count: Int,
    hasMore: Boolean,
    loading: Boolean,
    onLoadMore: () -> Unit
) {
    val nearEnd by remember(state, count) {
        derivedStateOf {
            state.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= count - 4 } == true
        }
    }
    LaunchedEffect(nearEnd, count, hasMore, loading) {
        if (nearEnd && count > 0 && hasMore && !loading) onLoadMore()
    }
}

@Composable
private fun RssGridLoadMoreEffect(
    state: LazyGridState,
    count: Int,
    hasMore: Boolean,
    loading: Boolean,
    onLoadMore: () -> Unit
) {
    val nearEnd by remember(state, count) {
        derivedStateOf {
            state.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= count - 6 } == true
        }
    }
    LaunchedEffect(nearEnd, count, hasMore, loading) {
        if (nearEnd && count > 0 && hasMore && !loading) onLoadMore()
    }
}
