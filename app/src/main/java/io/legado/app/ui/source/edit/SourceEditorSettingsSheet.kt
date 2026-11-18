package io.legado.app.ui.source.edit

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import io.legado.app.R
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.compose.NgBottomDrawerSurface
import io.legado.app.ui.design.components.compose.NgDrawerContentCardStyle
import io.legado.app.ui.design.components.compose.NgFormActionButton
import io.legado.app.ui.design.components.compose.NgFormGroup
import io.legado.app.ui.design.components.compose.NgFormGroupDivider
import io.legado.app.ui.design.components.compose.NgFormNavigationRow
import io.legado.app.ui.design.components.compose.NgFormSelectMenuVariant
import io.legado.app.ui.design.components.compose.NgFormSelectOption
import io.legado.app.ui.design.components.compose.NgFormSelectRow
import io.legado.app.ui.design.components.compose.NgFormSwitchSettingRow
import io.legado.app.ui.design.components.compose.NgLongDrawerHeader
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.utils.SelectFileContract
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalGlideComposeApi::class)
@Composable
internal fun SourceEditorSettingsSheet(
    autoComplete: Boolean,
    onAutoCompleteChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val appearance = LocalSourceEditorAppearance.current
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun changeImage(uri: Uri) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                SourceEditorAppearanceStore.setImage(context, uri)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure.localizedMessage ?: context.getString(R.string.source_editor_image_failed)
            } finally {
                busy = false
            }
        }
    }
    val picker = rememberLauncherForActivityResult(SelectFileContract()) { uri ->
        if (uri != null) changeImage(uri)
    }
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.85f
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        scrimColor = if (appearance.visibleImage != null) Color.Transparent else BottomSheetDefaults.ScrimColor,
        containerColor = Color.Transparent, shape = RectangleShape, dragHandle = null,
    ) {
        NgBottomDrawerSurface(
            modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight),
            contentCardStyle = NgDrawerContentCardStyle.ADAPTIVE,
            themeProfile = null,
        ) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                NgLongDrawerHeader(title = stringResource(R.string.source_editor_settings), centerTitle = true)
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    NgFormGroup(title = stringResource(R.string.source_editor_editing)) {
                        NgFormSwitchSettingRow(
                            title = stringResource(R.string.auto_complete), checked = autoComplete,
                            onCheckedChange = onAutoCompleteChange,
                        )
                    }
                    NgFormGroup(title = stringResource(R.string.source_editor_appearance)) {
                        NgFormSelectRow(
                            title = stringResource(R.string.source_editor_theme), selectedValue = appearance.theme.key,
                            options = SourceEditorTheme.entries.map { NgFormSelectOption(stringResource(it.label), it.key) },
                            onValueChange = { key ->
                                SourceEditorAppearanceStore.setTheme(context, SourceEditorTheme.entries.first { it.key == key })
                            },
                            arrowIcon = painterResource(R.drawable.ic_expand_more),
                            menuVariant = NgFormSelectMenuVariant.END_ANCHORED_CONTENT,
                        )
                        NgFormGroupDivider()
                        val image = appearance.image
                        NgFormNavigationRow(
                            title = stringResource(R.string.background_image),
                            value = stringResource(if (busy) R.string.loading else if (image == null) R.string.select_image else R.string.replace),
                            onClick = { picker.launch(arrayOf("image/*")) }, enabled = !busy,
                            arrowIcon = painterResource(R.drawable.ic_chevron_right_20),
                            valueContent = if (image == null) null else { {
                                GlideImage(image, contentDescription = null, contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(42.dp, 28.dp).clip(RoundedCornerShape(5.dp)))
                            } },
                        )
                        if (image != null) {
                            NgFormGroupDivider()
                            NgFormSwitchSettingRow(
                                title = stringResource(R.string.source_editor_show_image), checked = appearance.showImage,
                                onCheckedChange = { SourceEditorAppearanceStore.setImageVisible(context, it) },
                                enabled = !busy,
                            )
                        }
                    }
                    error?.let { Text(it, color = Color(NgTheme.colors.error), fontSize = 13.sp) }
                }
                NgFormActionButton(
                    text = stringResource(R.string.complete), onClick = onDismiss, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    variant = NgButtonVariant.PRIMARY,
                )
            }
        }
    }
}
