package io.legado.app.ui.design.components.compose

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import io.legado.app.R
import io.legado.app.help.config.NgThemeDrawerProfile
import io.legado.app.help.config.NgVisualSystem
import io.legado.app.ui.design.theme.NgTheme

/** A centered alert that can opt into the same wallpaper material as NG drawers. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun NgDrawerGlassAlertDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(),
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit,
) {
    val profile = rememberNgDrawerThemeProfile()
    val backdrop = if (
        profile?.source == NgThemeDrawerProfile.LIQUID_GLASS &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        !NgTheme.snapshot.isEInk
    ) {
        LocalView.current.rootView.findNgDrawerBackdropSource()
    } else null
    if (backdrop == null) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            properties = properties,
            containerColor = colorResource(R.color.ng_surface_card),
            tonalElevation = 0.dp,
            title = title,
            text = text,
            confirmButton = confirmButton,
            dismissButton = dismissButton,
        )
        return
    }

    BasicAlertDialog(onDismissRequest = onDismissRequest, modifier = modifier, properties = properties) {
        val titleColor = AlertDialogDefaults.titleContentColor
        NgVisualSurface(
            role = NgMaterialRole.OVERLAY,
            cornerRadius = NgTheme.shapes.extraLargeDp.dp,
            shape = AlertDialogDefaults.shape,
            style = NgDrawerDefaults.style(NgDrawerDefaults.rememberAppearance()).copy(contentColor = titleColor),
            contentPadding = PaddingValues(24.dp),
            liquidBackdrop = null,
            viewBackdropSource = backdrop,
            visualSystemOverride = NgVisualSystem.LIQUID_GLASS,
        ) {
            Box(Modifier.padding(bottom = 16.dp)) {
                ProvideTextStyle(MaterialTheme.typography.headlineSmall, content = title)
            }
            Box(Modifier.weight(1f, fill = false).padding(bottom = 24.dp)) {
                CompositionLocalProvider(LocalContentColor provides AlertDialogDefaults.textContentColor) {
                    ProvideTextStyle(MaterialTheme.typography.bodyMedium, content = text)
                }
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                dismissButton()
                confirmButton()
            }
        }
    }
}
