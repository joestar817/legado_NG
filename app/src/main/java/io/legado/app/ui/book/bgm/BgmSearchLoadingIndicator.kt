package io.legado.app.ui.book.bgm

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.legado.app.ui.design.components.compose.NgDotsLoadingIndicator

/** 保留 BGM 原调用入口、动画几何与墨水屏行为。 */
@Composable
internal fun BgmSearchLoadingIndicator(modifier: Modifier = Modifier) {
    NgDotsLoadingIndicator(modifier)
}
