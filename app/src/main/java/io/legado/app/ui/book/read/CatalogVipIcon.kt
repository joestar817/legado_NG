package io.legado.app.ui.book.read

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import io.legado.app.R

@Composable
internal fun CatalogVipIcon(isPaid: Boolean, tint: Color, modifier: Modifier = Modifier) {
    Icon(
        imageVector = if (isPaid) Icons.Outlined.LockOpen else Icons.Outlined.Lock,
        contentDescription = stringResource(
            if (isPaid) R.string.read_catalog_vip_purchased else R.string.read_catalog_vip_unpaid,
        ),
        modifier = modifier,
        tint = tint.copy(alpha = tint.alpha * 0.55f),
    )
}
