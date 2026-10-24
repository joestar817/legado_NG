package io.legado.app.ui.main.explore

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

@Composable
internal fun exploreDropdownMenuBounds(): Modifier {
    val configuration = LocalConfiguration.current
    return Modifier
        .heightIn(max = (configuration.screenHeightDp.dp / 2).coerceAtMost(320.dp))
        .widthIn(max = (configuration.screenWidthDp.dp - 16.dp).coerceAtMost(240.dp))
}
