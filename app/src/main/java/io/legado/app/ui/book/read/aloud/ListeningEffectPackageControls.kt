package io.legado.app.ui.book.read.aloud

import android.app.Activity
import android.content.Intent
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.EffectThemeRuntime
import io.legado.app.help.config.NgEffectPackageKind
import io.legado.app.help.config.NgListeningEffectLibraryStore
import io.legado.app.help.config.NgManagedTheme
import io.legado.app.help.config.NgPreparedThemeImport
import io.legado.app.help.config.NgThemeImportAction
import io.legado.app.help.config.NgThemePackageManager
import io.legado.app.help.resource.LISTENING_EFFECT_RESOURCE_TYPE
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.NgDialogTextActionButton
import io.legado.app.ui.design.components.compose.NgPaneDialog
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.resource.ResourceCenterActivity
import io.legado.app.ui.resource.ResourcePurpose
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

internal const val LISTENING_EFFECT_REQUEST = "listeningEffectRequest"
internal const val RETURN_LISTENING_EFFECT = "returnListeningEffect"

@Composable
internal fun ListeningEffectPackageControls(
    packs: List<NgManagedTheme>,
    selectedId: String?,
    requestedId: String?,
    onRequestConsumed: (String) -> Unit,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    onLibraryChanged: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val kind = NgEffectPackageKind.LISTENING_EFFECT
    var managing by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var candidate by remember { mutableStateOf<NgPreparedThemeImport?>(null) }
    var deleting by remember { mutableStateOf<NgManagedTheme?>(null) }
    var focusedId by rememberSaveable { mutableStateOf<String?>(null) }
    val gridState = rememberLazyGridState()

    fun focusPackage(id: String) {
        focusedId = id
        val index = packs.indexOfFirst { it.id == id }
        if (index >= 0) gridState.requestScrollToItem(index)
    }

    LaunchedEffect(requestedId) {
        if (requestedId != null) {
            focusPackage(requestedId)
            onRequestConsumed(requestedId)
        }
    }
    LaunchedEffect(packs, focusedId) {
        focusedId?.let(::focusPackage)
    }
    DisposableEffect(Unit) {
        onDispose { candidate?.discard() }
    }
    val resourceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringExtra(LISTENING_EFFECT_REQUEST)?.let(::focusPackage)
        }
        onLibraryChanged()
    }
    val openResources = {
        resourceLauncher.launch(Intent(context, ResourceCenterActivity::class.java)
            .putExtra("resourcePurpose", ResourcePurpose.LISTENING.name)
            .putExtra("resourceType", LISTENING_EFFECT_RESOURCE_TYPE)
            .putExtra(RETURN_LISTENING_EFFECT, true))
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !busy) scope.launch {
            busy = true
            var inspected: NgPreparedThemeImport? = null
            try {
                withContext(Dispatchers.IO) {
                    inspected = NgThemePackageManager.inspectTheme(context, uri, kind).getOrThrow()
                }
                val result = requireNotNull(inspected)
                if (result.action == NgThemeImportAction.NO_OP) {
                    focusPackage(result.theme.id)
                    context.toastOnUi(context.getString(R.string.listening_effect_installed))
                } else {
                    candidate = result
                    inspected = null
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                context.toastOnUi(error.message ?: context.getString(R.string.listening_effect_import_failed))
            } finally {
                withContext(NonCancellable + Dispatchers.IO) { inspected?.discard() }
                busy = false
            }
        }
    }
    val primary = Color(NgTheme.colors.primary)
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.listening_effect_installed_header),
            color = Color(NgTheme.colors.onSurfaceVariant),
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            enabled = !busy,
            onClick = {
                importLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
            },
        ) {
            Icon(painterResource(R.drawable.ic_import), stringResource(R.string.listening_effect_import), Modifier.size(22.dp))
        }
        if (managing) {
            TextButton(onClick = { managing = false }, enabled = !busy) {
                Text(stringResource(R.string.complete), color = primary, fontSize = 13.sp)
            }
        } else {
            IconButton(onClick = { managing = true }, enabled = !busy && packs.isNotEmpty()) {
                Icon(
                    painterResource(R.drawable.ic_settings),
                    stringResource(R.string.listening_effect_manage),
                    Modifier.size(24.dp),
                )
            }
        }
        IconButton(onClick = openResources, enabled = !busy) {
            Icon(
                painterResource(R.drawable.ic_about_cloud_download),
                stringResource(R.string.listening_effect_get),
                Modifier.size(22.dp),
            )
        }
    }
    if (packs.isEmpty()) {
        Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.listening_effect_empty),
                color = Color(NgTheme.colors.onSurfaceVariant),
                fontSize = 13.sp,
            )
        }
    } else {
        val fontScale = LocalDensity.current.fontScale
        val maxRows = if (LocalConfiguration.current.screenHeightDp < 540) 1 else 2
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = when {
                maxWidth >= (92.dp * fontScale) * 3 + 12.dp -> 3
                maxWidth >= (92.dp * fontScale) * 2 + 6.dp -> 2
                else -> 1
            }
            val rowHeight = 84.dp + 38.dp * fontScale
            val rows = ((packs.size + columns - 1) / columns).coerceAtMost(maxRows)
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = gridState,
                modifier = Modifier.fillMaxWidth().height(rowHeight * rows + 6.dp * (rows - 1)).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(packs, key = { it.id }) { pack ->
                    ListeningEffectPackageCard(
                        pack, selectedId == pack.id, focusedId == pack.id, managing,
                        enabled && !busy && !AppConfig.isEInkMode, rowHeight,
                        onDelete = { deleting = pack },
                        onSelect = {
                            scope.launch {
                                busy = true
                                try {
                                    val status = withContext(Dispatchers.IO) {
                                        EffectThemeRuntime.status(context, pack, kind.target)
                                    }
                                    if (status.canRun) {
                                        onSelect(pack.id)
                                        focusedId = null
                                    } else context.toastOnUi(status.label)
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    context.toastOnUi(error.message ?: context.getString(R.string.listening_effect_use_failed))
                                } finally { busy = false }
                            }
                        },
                    )
                }
            }
        }
    }

    candidate?.let { pending ->
        ListeningEffectImportDialog(
            pending,
            onDismiss = { candidate = null; pending.discard() },
            onConfirm = {
                scope.launch {
                    candidate = null
                    busy = true
                    try {
                        val installed = withContext(Dispatchers.IO) {
                            pending.commit(
                                context, allowReplace = true,
                                allowDowngrade = pending.action == NgThemeImportAction.DOWNGRADE,
                                keepCustomizations = pending.incompatibleParameters.isEmpty(),
                            )
                        }
                        focusPackage(installed.id)
                        managing = false
                        onLibraryChanged()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        context.toastOnUi(error.message ?: context.getString(R.string.listening_effect_import_failed))
                    } finally {
                        withContext(NonCancellable + Dispatchers.IO) { pending.discard() }
                        busy = false
                    }
                }
            },
        )
    }
    deleting?.let { pack ->
        NgPaneDialog(onDismissRequest = { deleting = null }) {
            NgDialog(
                title = stringResource(R.string.listening_effect_delete),
                variant = NgDialogVariant.COMPACT_CONFIRMATION,
                actions = {
                    NgDialogTextActionButton(stringResource(R.string.cancel), { deleting = null }, secondary = true)
                    NgDialogTextActionButton(stringResource(R.string.delete), danger = true, onClick = {
                        deleting = null
                        scope.launch {
                            busy = true
                            try {
                                NgListeningEffectLibraryStore.removeAsync(context, pack)
                                onLibraryChanged()
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                context.toastOnUi(error.message ?: context.getString(R.string.listening_effect_delete_failed))
                            } finally { busy = false }
                        }
                    })
                },
            ) {
                Text(stringResource(R.string.listening_effect_delete_confirm, pack.name), fontSize = 14.sp)
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun ListeningEffectPackageCard(
    pack: NgManagedTheme,
    selected: Boolean,
    focused: Boolean,
    managing: Boolean,
    enabled: Boolean,
    height: Dp,
    onDelete: () -> Unit,
    onSelect: () -> Unit,
) {
    val colors = NgTheme.colors
    val shape = RoundedCornerShape(12.dp)
    val highlight = selected || focused
    Column(
        modifier = Modifier.height(height).clip(shape)
            .background(Color(if (selected) colors.selectedContainer else colors.surface))
            .border(if (highlight) 1.dp else 0.6.dp,
                Color(if (highlight) colors.primary else colors.outlineVariant).copy(alpha = if (highlight) 0.85f else 0.4f),
                shape)
            .selectable(selected, enabled = enabled && !managing, role = Role.RadioButton, onClick = onSelect)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val preview = remember(pack.packageRootPath, pack.effect?.preview) {
            pack.packageRootPath?.let { root -> pack.effect?.preview?.let { File(root, it) } }
        }
        Box(Modifier.fillMaxWidth().height(77.dp).clip(RoundedCornerShape(8.dp))
            .background(Color(colors.surfaceVariant))) {
            if (preview != null) {
                GlideImage(preview, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            }
            if (managing) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.align(Alignment.TopEnd).size(40.dp),
                ) {
                    Box(Modifier.size(22.dp).background(Color(colors.surface), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_book_info_delete),
                            stringResource(R.string.listening_effect_delete_confirm, pack.name),
                            Modifier.size(14.dp), tint = Color(colors.error))
                    }
                }
            } else if (selected) {
                Box(Modifier.align(Alignment.TopEnd).padding(5.dp).size(20.dp)
                    .background(Color(colors.surface), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_check), null, Modifier.size(14.dp), tint = Color(colors.primary))
                }
            }
        }
        Text(pack.name, modifier = Modifier.padding(top = 4.dp), fontSize = 13.sp, lineHeight = 17.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color(colors.onSurface))
        if (selected) {
            Text(stringResource(R.string.listening_effect_using), fontSize = 10.sp, lineHeight = 13.sp, color = Color(colors.primary))
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun ListeningEffectImportDialog(pack: NgPreparedThemeImport, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val preview by produceState<ByteArray?>(null, pack) {
        value = withContext(Dispatchers.IO) { runCatching { pack.readPreview() }.getOrNull() }
    }
    val title = stringResource(when (pack.action) {
        NgThemeImportAction.UPDATE -> R.string.listening_effect_update
        NgThemeImportAction.DOWNGRADE -> R.string.listening_effect_downgrade
        NgThemeImportAction.REPLACE -> R.string.listening_effect_replace
        else -> R.string.listening_effect_import
    })
    NgPaneDialog(onDismissRequest = onDismiss) {
        NgDialog(
            title = title,
            actions = {
                NgDialogTextActionButton(stringResource(R.string.cancel), onDismiss, secondary = true)
                NgDialogTextActionButton(stringResource(R.string.ok), onConfirm)
            },
        ) {
            val colors = NgTheme.colors
            val theme = pack.theme
            val version = theme.packageVersionLabel()
            val previousVersion = pack.previous?.packageVersionLabel()
            val versionDetail = if (previousVersion != null && previousVersion != version) {
                "$previousVersion → $version"
            } else version
            val author = theme.publication?.author?.takeIf(String::isNotBlank)
            val versionText = stringResource(R.string.version) + " " + versionDetail
            val metadata = listOfNotNull(versionText, author).joinToString(" · ")
            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                preview?.let {
                    GlideImage(
                        model = it,
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            theme.name,
                            modifier = Modifier.weight(1f),
                            color = Color(colors.onSurface),
                            fontSize = 16.sp,
                            lineHeight = 22.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            Formatter.formatFileSize(LocalContext.current, pack.sizeBytes),
                            color = Color(colors.onSurfaceVariant),
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            maxLines = 1,
                        )
                    }
                    Text(
                        metadata,
                        color = Color(colors.onSurfaceVariant),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (pack.hasCustomizations && pack.incompatibleParameters.isNotEmpty()) {
                    Text(
                        stringResource(R.string.listening_effect_reset_parameters),
                        color = Color(colors.onSurfaceVariant),
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

private fun NgManagedTheme.packageVersionLabel(): String =
    publication?.versionName?.takeIf(String::isNotBlank) ?: publication?.versionCode?.toString() ?: "—"
