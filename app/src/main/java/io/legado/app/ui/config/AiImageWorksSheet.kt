package io.legado.app.ui.config

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.legado.app.R
import io.legado.app.help.ai.AiImageAssets
import io.legado.app.help.ai.AiImageWorksSnapshot
import io.legado.app.help.ai.saveAiImageWorksToAlbum
import io.legado.app.ui.design.components.NgButtonShapeVariant
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.compose.NgButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AiImageWorksSheet(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var works by remember { mutableStateOf<AiImageWorksSnapshot?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var managing by rememberSaveable { mutableStateOf(false) }
    var removedTab by rememberSaveable { mutableStateOf(false) }
    var selectedIds by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var albumPermissionPending by rememberSaveable { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    val busy by rememberUpdatedState(working || albumPermissionPending)
    val ids = if (removedTab) works?.removed.orEmpty() else works?.visible.orEmpty()
    val availableIds = remember(ids) { ids.toHashSet() }
    val selected = remember(availableIds, selectedIds) {
        selectedIds.filterTo(linkedSetOf(), availableIds::contains)
    }
    val allSelected = ids.isNotEmpty() && selected.size == ids.size

    LaunchedEffect(Unit) {
        try {
            works = withContext(Dispatchers.IO) { AiImageAssets.works.read() }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            loadFailed = true
        }
    }

    fun runBatch(action: suspend () -> Unit) {
        if (working || albumPermissionPending) return
        working = true
        scope.launch {
            try {
                action()
            } finally {
                working = false
            }
        }
    }

    fun saveSelection(selection: Set<String>) {
        if (selection.isEmpty()) return
        runBatch {
            val failed = saveAiImageWorksToAlbum(context.applicationContext, selection)
            val saved = selection.size - failed.size
            val message = if (failed.isEmpty()) {
                context.getString(R.string.ai_image_album_saved, saved)
            } else {
                selectedIds = ArrayList(failed)
                context.getString(R.string.ai_image_album_save_partial, saved, failed.size)
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    val albumPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (albumPermissionPending) {
            albumPermissionPending = false
            if (granted) {
                saveSelection(selectedIds.toSet())
            } else {
                Toast.makeText(context, R.string.ai_image_album_permission, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun saveToAlbum() {
        if (working || albumPermissionPending || selected.isEmpty()) return
        val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        ) {
            selectedIds = ArrayList(selected)
            albumPermissionPending = true
            try {
                albumPermission.launch(permission)
            } catch (_: Exception) {
                albumPermissionPending = false
                Toast.makeText(context, R.string.ai_image_album_permission, Toast.LENGTH_SHORT).show()
            }
        } else {
            saveSelection(selected)
        }
    }

    fun changeRemovedState() {
        val remove = !removedTab
        if (selected.isEmpty()) return
        runBatch {
            try {
                works = withContext(Dispatchers.IO) { AiImageAssets.works.setRemoved(selected, remove) }
                selectedIds = arrayListOf()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                Toast.makeText(context, R.string.ai_image_works_update_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || !busy },
    )
    val managementHeight = maxOf(380.dp, LocalConfiguration.current.screenHeightDp.dp * 0.57f)
    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.fillMaxWidth().then(if (managing) Modifier.height(managementHeight) else Modifier),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(if (managing) R.string.ai_image_works_manage else R.string.ai_image_works),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    enabled = !busy,
                    onClick = {
                        managing = !managing
                        removedTab = false
                        selectedIds = arrayListOf()
                    },
                ) {
                    Text(stringResource(if (managing) R.string.complete else R.string.manage))
                }
            }
            if (managing) {
                WorksTabs(removedTab, !busy) { removed ->
                    if (removed != removedTab) {
                        removedTab = removed
                        selectedIds = arrayListOf()
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.ai_image_works_selected, selected.size),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(
                        enabled = ids.isNotEmpty() && !busy,
                        onClick = { selectedIds = if (allSelected) arrayListOf() else ArrayList(ids) },
                    ) {
                        Text(stringResource(if (allSelected) R.string.unselect_all else R.string.select_all))
                    }
                }
            }
            val gridHeight = if (managing) Modifier.weight(1f) else Modifier.heightIn(max = 420.dp)
            key(removedTab) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(88.dp),
                    modifier = gridHeight.fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (ids.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            val message = when {
                                loadFailed -> R.string.ai_image_works_load_failed
                                works == null -> null
                                removedTab -> R.string.ai_image_works_no_removed
                                else -> R.string.ai_image_no_works
                            }
                            if (message == null) {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(Modifier.size(24.dp))
                                }
                            } else {
                                Text(stringResource(message), Modifier.padding(4.dp, 12.dp))
                            }
                        }
                    }
                    items(ids, key = { it }) { id ->
                        val file = remember(id) { AiImageAssets.file(id) }
                        if (file != null) {
                            if (managing) {
                                WorkSelectionThumbnail(file, id in selected, !busy) {
                                    selectedIds = ArrayList(selectedIds).apply { if (!remove(id)) add(id) }
                                }
                            } else {
                                ImageThumbnail(file, Modifier.fillMaxWidth().aspectRatio(1f)) { onPick(id) }
                            }
                        }
                    }
                }
            }
            if (managing) {
                if (working) LinearProgressIndicator(Modifier.fillMaxWidth()) else HorizontalDivider()
                WorksActions(
                    removed = removedTab,
                    enabled = selected.isNotEmpty() && !busy,
                    onSave = ::saveToAlbum,
                    onMove = ::changeRemovedState,
                )
            }
        }
    }
}

@Composable
private fun WorksTabs(removed: Boolean, enabled: Boolean, onSelect: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(CircleShape)
            .background(colors.surfaceVariant).selectableGroup(),
    ) {
        for (removedTab in listOf(false, true)) {
            val selected = removed == removedTab
            Box(
                modifier = Modifier.weight(1f).heightIn(min = 44.dp).clip(CircleShape)
                    .background(if (selected) colors.secondaryContainer else Color.Transparent)
                    .selectable(selected, enabled = enabled, role = Role.Tab) { onSelect(removedTab) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(if (removedTab) R.string.ai_image_works_removed else R.string.ai_image_works),
                    color = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun WorkSelectionThumbnail(file: File, selected: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(shape)
            .toggleable(selected, enabled = enabled, role = Role.Checkbox) { onToggle() }
            .then(if (selected) Modifier.border(2.dp, colors.primary, shape) else Modifier),
    ) {
        ImageThumbnail(file, Modifier.matchParentSize())
        Box(
            modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).size(23.dp)
                .background(if (selected) colors.primary else colors.surface.copy(alpha = 0.94f), CircleShape)
                .border(1.dp, if (selected) colors.onPrimary else colors.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Rounded.Check, null, Modifier.size(15.dp), tint = colors.onPrimary)
        }
    }
}

@Composable
private fun WorksActions(removed: Boolean, enabled: Boolean, onSave: () -> Unit, onMove: () -> Unit) {
    val padding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!removed) {
            NgButton(
                onClick = onSave,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                enabled = enabled,
                shapeVariant = NgButtonShapeVariant.COMPACT_ROUNDED,
                contentPadding = padding,
            ) {
                Text(stringResource(R.string.ai_image_album_save), textAlign = TextAlign.Center)
            }
        }
        NgButton(
            onClick = onMove,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            enabled = enabled,
            variant = if (removed) NgButtonVariant.PRIMARY else NgButtonVariant.OUTLINE,
            shapeVariant = NgButtonShapeVariant.COMPACT_ROUNDED,
            contentPadding = padding,
        ) {
            Text(
                stringResource(if (removed) R.string.restore else R.string.ai_image_works_remove),
                textAlign = TextAlign.Center,
            )
        }
    }
}
