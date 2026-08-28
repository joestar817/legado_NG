package io.legado.app.ui.main.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.constant.AppPattern
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.ui.design.components.compose.NgVisualOverlayDialog
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.utils.splitNotBlank

internal data class ExploreSourceFolder(
    val title: String,
    val sources: List<BookSourcePart>
)

internal fun buildExploreSourceFolders(
    sources: List<BookSourcePart>,
    visibleGroups: List<String>,
    noGroupTitle: String
): List<ExploreSourceFolder> {
    val visibleGroupSet = visibleGroups.toSet()
    val groupedSources = linkedMapOf<String, MutableList<BookSourcePart>>()
    sources.forEach { source ->
        val sourceGroups = source.bookSourceGroup
            ?.splitNotBlank(AppPattern.splitGroupRegex)
            .orEmpty()
            .filter { it in visibleGroupSet }
            .distinct()
        if (sourceGroups.isEmpty()) {
            groupedSources.getOrPut(noGroupTitle, ::mutableListOf).add(source)
        } else {
            sourceGroups.forEach { group ->
                groupedSources.getOrPut(group, ::mutableListOf).add(source)
            }
        }
    }
    return buildList {
        groupedSources[noGroupTitle]?.let { ungrouped ->
            add(ExploreSourceFolder(noGroupTitle, ungrouped.distinctBy { it.bookSourceUrl }))
        }
        visibleGroups.asSequence()
            .filter { it != noGroupTitle }
            .forEach { group ->
                groupedSources[group]?.let { groupSources ->
                    add(
                        ExploreSourceFolder(
                            title = group,
                            sources = groupSources.distinctBy { it.bookSourceUrl }
                        )
                    )
                }
            }
    }
}

@Composable
internal fun ExploreGroupedSourceGrid(
    sources: List<BookSourcePart>,
    groups: List<String>,
    loadingSourceUrls: Set<String>,
    bottomInset: Dp,
    scrollToTopToken: Long,
    onOpenSource: (BookSourcePart) -> Unit,
    onSourceAction: (BookSourcePart, Int) -> Unit
) {
    val noGroupTitle = stringResource(R.string.no_group)
    val folders = remember(sources, groups, noGroupTitle) {
        buildExploreSourceFolders(sources, groups, noGroupTitle)
    }
    var openFolderTitle by remember { mutableStateOf<String?>(null) }
    val openFolder = folders.firstOrNull { it.title == openFolderTitle }
    val gridState = rememberLazyGridState()

    LaunchedEffect(scrollToTopToken) {
        if (scrollToTopToken > 0L) gridState.animateScrollToItem(0)
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        state = gridState,
        contentPadding = PaddingValues(bottom = bottomInset + 12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(folders, key = { it.title }) { folder ->
            ExploreSourceFolderItem(
                folder = folder,
                onClick = { openFolderTitle = folder.title }
            )
        }
    }

    if (openFolder != null) {
        ExploreSourceFolderDialog(
            folder = openFolder,
            loadingSourceUrls = loadingSourceUrls,
            onDismiss = { openFolderTitle = null },
            onOpenSource = { source ->
                openFolderTitle = null
                onOpenSource(source)
            },
            onSourceAction = { source, actionId ->
                openFolderTitle = null
                onSourceAction(source, actionId)
            }
        )
    }
}

@Composable
private fun ExploreSourceFolderItem(
    folder: ExploreSourceFolder,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ExploreSourceFolderPreview(folder.sources)
        Spacer(Modifier.height(10.dp))
        Text(
            text = folder.title,
            color = Color(NgTheme.colors.onSurfaceVariant),
            fontSize = 13.sp,
            lineHeight = 17.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.heightIn(min = 34.dp)
        )
    }
}

@Composable
private fun ExploreSourceFolderPreview(sources: List<BookSourcePart>) {
    val folderShape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .size(58.dp)
            .clip(folderShape)
            .background(Color(NgTheme.colors.cardContainer).copy(alpha = 0.82f))
            .border(
                width = 1.dp,
                color = Color(NgTheme.colors.outlineVariant).copy(alpha = 0.28f),
                shape = folderShape
            )
            .padding(7.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(2) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(2) { column ->
                    ExploreSourceFolderPreviewCell(sources.getOrNull(row * 2 + column))
                }
            }
        }
    }
}

@Composable
private fun ExploreSourceFolderPreviewCell(source: BookSourcePart?) {
    val shape = RoundedCornerShape(5.dp)
    if (source == null) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(shape)
                .background(Color(NgTheme.colors.onSurfaceVariant).copy(alpha = 0.08f))
        )
        return
    }
    val tileColor = remember(source.bookSourceUrl) { sourceTileColor(source.bookSourceUrl) }
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(shape)
            .background(Color(tileColor)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = source.bookSourceName.trim().firstOrNull()
                ?.toString()?.uppercase() ?: "源",
            color = sourceTileContentColor(tileColor),
            fontSize = 9.sp,
            lineHeight = 9.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun ExploreSourceFolderDialog(
    folder: ExploreSourceFolder,
    loadingSourceUrls: Set<String>,
    onDismiss: () -> Unit,
    onOpenSource: (BookSourcePart) -> Unit,
    onSourceAction: (BookSourcePart, Int) -> Unit
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp
    val rowCount = (folder.sources.size + 3) / 4
    val dialogHeight = ((76 + rowCount * 128).coerceAtLeast(204))
        .coerceAtMost((screenHeight * 0.74f).toInt())
        .dp

    NgVisualOverlayDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .height(dialogHeight),
    ) {
        Text(
            text = folder.title,
            color = Color(NgTheme.colors.onSurface),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 4.dp)
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(bottom = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            items(folder.sources, key = { it.bookSourceUrl }) { source ->
                ExploreGridSourceItem(
                    source = source,
                    loading = source.bookSourceUrl in loadingSourceUrls,
                    onClick = { onOpenSource(source) },
                    onAction = { actionId -> onSourceAction(source, actionId) }
                )
            }
        }
    }
}
