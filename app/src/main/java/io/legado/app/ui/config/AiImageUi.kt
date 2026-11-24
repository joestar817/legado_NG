@file:OptIn(com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi::class)

package io.legado.app.ui.config

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.drawable.ColorDrawable
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowInsetsControllerCompat
import com.bumptech.glide.integration.compose.GlideImage
import io.legado.app.R
import io.legado.app.help.ai.AI_IMAGE_ATTACHMENT_LIMIT
import io.legado.app.help.ai.AiChatGptException
import io.legado.app.help.ai.AiImageAssets
import io.legado.app.help.ai.imageFileExtension
import io.legado.app.help.ai.imageMimeType
import io.legado.app.ui.design.components.compose.NgPopupToggleState
import io.legado.app.ui.widget.compose.NgFunctionMenu
import io.legado.app.ui.widget.compose.NgFunctionMenuAction
import io.legado.app.ui.widget.compose.NgFunctionMenuVariant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.sin

internal data class AiChatImageWait(val startedAt: Long, val editing: Boolean)

@Composable
internal fun rememberAiImageAttachmentActions(
    targetId: String,
    attachmentCount: Int,
    onDismiss: () -> Unit,
    onAdd: (List<String>) -> Unit,
    onDirect: (() -> Unit)? = null,
    importingChanged: (Boolean) -> Unit,
): List<NgFunctionMenuAction> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val target by rememberUpdatedState(targetId)
    val add by rememberUpdatedState(onAdd)
    val count by rememberUpdatedState(attachmentCount)
    val reportImporting by rememberUpdatedState(importingChanged)
    var launchedFor by rememberSaveable { mutableStateOf<String?>(null) }
    var cameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    var worksFor by rememberSaveable { mutableStateOf<String?>(null) }
    val room = AI_IMAGE_ATTACHMENT_LIMIT - attachmentCount
    fun reportLimit() {
        Toast.makeText(context, context.getString(R.string.ai_image_attachment_limit,
            AI_IMAGE_ATTACHMENT_LIMIT), Toast.LENGTH_SHORT).show()
    }

    fun importPickedImages(uris: List<Uri>, camera: File? = null) {
        val origin = launchedFor
        launchedFor = null
        if (uris.isEmpty() || origin == null || target != origin) {
            camera?.delete()
            return
        }
        scope.launch {
            val imported = mutableListOf<String>()
            reportImporting(true)
            try {
                val available = (AI_IMAGE_ATTACHMENT_LIMIT - count).coerceAtLeast(0)
                if (uris.size > available) reportLimit()
                uris.take(available).forEach { uri ->
                    imported += AiImageAssets.importImage(context, uri)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val message = (error as? AiChatGptException)?.messageRes ?: R.string.ai_image_attachment_unavailable
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            } finally {
                try {
                    if (target == origin && imported.isNotEmpty() &&
                        kotlinx.coroutines.currentCoroutineContext().isActive
                    ) {
                        val accepted = imported.take((AI_IMAGE_ATTACHMENT_LIMIT - count).coerceAtLeast(0))
                        add(accepted)
                        imported.removeAll(accepted.toSet())
                    }
                    withContext(Dispatchers.IO + NonCancellable) {
                        imported.forEach(AiImageAssets::discardUnsent)
                        camera?.delete()
                    }
                } finally {
                    reportImporting(false)
                }
            }
        }
    }
    val photos = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(AI_IMAGE_ATTACHMENT_LIMIT)
    ) { importPickedImages(it) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = cameraPath?.let(::File)
        cameraPath = null
        val uri = file?.takeIf { success && it.isFile }?.let { Uri.fromFile(it) }
        importPickedImages(listOfNotNull(uri), file)
    }
    if (worksFor == targetId) {
        AiImageWorksSheet(
            onDismiss = { worksFor = null },
            onPick = { id ->
                if (room > 0) {
                    add(listOf(id))
                    worksFor = null
                } else {
                    reportLimit()
                }
            },
        )
    }
    LaunchedEffect(targetId) {
        if (worksFor != targetId) worksFor = null
    }

    return buildList {
        add(NgFunctionMenuAction(Icons.Rounded.PhotoLibrary, stringResource(R.string.ai_image_photos)) {
            if (room <= 0) reportLimit() else {
                launchedFor = targetId
                onDismiss()
                photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        })
        add(NgFunctionMenuAction(Icons.Rounded.CameraAlt, stringResource(R.string.ai_image_camera)) {
            if (room <= 0) reportLimit() else {
                onDismiss()
                val file = try {
                    File.createTempFile("ai-camera-", ".jpg", context.cacheDir).also { output ->
                        cameraPath = output.path
                        launchedFor = targetId
                    }
                } catch (_: Exception) {
                    Toast.makeText(context, R.string.ai_image_camera_unavailable, Toast.LENGTH_SHORT).show()
                    return@NgFunctionMenuAction
                }
                try {
                    camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileProvider", file))
                } catch (_: Exception) {
                    launchedFor = null
                    cameraPath = null
                    file.delete()
                    Toast.makeText(context, R.string.ai_image_camera_unavailable, Toast.LENGTH_SHORT).show()
                }
            }
        })
        add(NgFunctionMenuAction(Icons.Rounded.Collections, stringResource(R.string.ai_image_works)) {
            onDismiss()
            worksFor = targetId
        })
        if (onDirect != null) {
            add(NgFunctionMenuAction(Icons.Rounded.AutoAwesome, stringResource(R.string.ai_image_generate)) {
                onDismiss()
                onDirect()
            })
        }
    }
}

@Composable
internal fun AiImageAttachmentMenuButton(
    actions: List<NgFunctionMenuAction>,
    menu: NgPopupToggleState,
    enabled: Boolean,
) {
    Box {
        IconButton(
            modifier = Modifier.size(48.dp),
            onClick = menu::onAnchorClick,
            enabled = enabled,
        ) {
            Icon(Icons.Rounded.Add, stringResource(R.string.ai_image_attach))
        }
        NgFunctionMenu(
            expanded = menu.expanded,
            onDismissRequest = menu::onDismissRequest,
            actions = actions,
            variant = NgFunctionMenuVariant.ATTACHMENT,
        )
    }
}

@Composable
internal fun AiImageThumbnailRow(
    ids: List<String>,
    onPreview: (File) -> Unit,
    onRemove: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (ids.isEmpty()) return
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ids.forEach { id ->
            AiImageAssets.file(id)?.let { file ->
                Box(Modifier.size(64.dp)) {
                    ImageThumbnail(file, Modifier.size(58.dp).align(Alignment.BottomStart), onClick = { onPreview(file) })
                    if (onRemove != null) {
                        val label = stringResource(R.string.ai_image_remove)
                        Box(
                            modifier = Modifier.align(Alignment.TopEnd).size(28.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.inverseSurface)
                                .clickable(onClickLabel = label, role = Role.Button) { onRemove(id) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.Close, label, modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.inverseOnSurface)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ImageThumbnail(file: File, modifier: Modifier, onClick: (() -> Unit)? = null) {
    val imageModifier = modifier.clip(RoundedCornerShape(10.dp))
    GlideImage(
        model = file,
        contentDescription = stringResource(R.string.ai_image_reference),
        modifier = if (onClick == null) imageModifier else imageModifier.clickable(role = Role.Button, onClick = onClick),
        contentScale = ContentScale.Crop,
    ) { it.error(R.drawable.image_loading_error) }
}

@Composable
internal fun AiImageWaitCard(wait: AiChatImageWait) {
    val elapsed by produceState(0L, wait.startedAt) {
        while (true) {
            value = (System.currentTimeMillis() - wait.startedAt).coerceAtLeast(0L) / 1000L
            delay(1000)
        }
    }
    val transition = rememberInfiniteTransition(label = "image wait")
    val phase by transition.animateFloat(0f, (2 * Math.PI).toFloat(),
        infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "image dots")
    val primary = MaterialTheme.colorScheme.primary
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(if (wait.editing) R.string.ai_image_editing else R.string.ai_image_generating),
                style = MaterialTheme.typography.titleSmall)
            Canvas(Modifier.fillMaxWidth().height(180.dp)) {
                val columns = 21
                val rows = 13
                val stepX = size.width / (columns + 1)
                val stepY = size.height / (rows + 1)
                repeat(rows) { row ->
                    repeat(columns) { column ->
                        val light = (sin(phase - column * .42f + row * .28f) + 1f) * .5f
                        drawCircle(primary.copy(alpha = .16f + light * .54f),
                            radius = 1.dp.toPx() + light * 1.2.dp.toPx(),
                            center = Offset((column + 1) * stepX, (row + 1) * stepY))
                    }
                }
            }
            Text(stringResource(R.string.ai_image_elapsed, elapsed),
                modifier = Modifier.align(Alignment.End), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
internal fun AiChatImageResult(
    file: File,
    onPreview: () -> Unit,
    onEdit: (() -> Unit)?,
) {
    val ratio by produceState(chatImageAspectRatios.get(file) ?: 1f, file) {
        value = chatImageAspectRatios.get(file) ?: withContext(Dispatchers.IO) {
            file.readChatImageAspectRatio()?.also { chatImageAspectRatios.put(file, it) } ?: 1f
        }
    }
    Box(
        Modifier.fillMaxWidth().padding(top = 4.dp).aspectRatio(ratio)
            .clip(RoundedCornerShape(12.dp)),
    ) {
        GlideImage(
            model = file,
            contentDescription = stringResource(R.string.ai_codex_generated_image),
            modifier = Modifier.matchParentSize().clickable(role = Role.Button, onClick = onPreview),
            contentScale = ContentScale.Fit,
        ) { it.error(R.drawable.image_loading_error) }

        val menu = remember { NgPopupToggleState() }
        val download = rememberAiImageDownload(file)
        val share = rememberAiImageShare(file)
        Box(Modifier.align(Alignment.BottomEnd).padding(6.dp)) {
            IconButton(onClick = menu::onAnchorClick) {
                Box(
                    Modifier.size(34.dp).background(Color.Black.copy(alpha = 0.52f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.MoreHoriz,
                        contentDescription = stringResource(R.string.more),
                        modifier = Modifier.size(20.dp),
                        tint = Color.White,
                    )
                }
            }
            if (menu.expanded) {
                NgFunctionMenu(
                    expanded = true,
                    onDismissRequest = menu::onDismissRequest,
                    actions = buildList {
                        add(NgFunctionMenuAction(Icons.Rounded.FileDownload, stringResource(R.string.action_download)) {
                            menu.close()
                            download()
                        })
                        if (onEdit != null) {
                            add(NgFunctionMenuAction(Icons.Rounded.Edit, stringResource(R.string.ai_image_continue)) {
                                menu.close()
                                onEdit()
                            })
                        }
                        add(NgFunctionMenuAction(Icons.Rounded.Share, stringResource(R.string.share)) {
                            menu.close()
                            share()
                        })
                    },
                    width = 120.dp,
                    openAboveAnchor = true,
                )
            }
        }
    }
}

private val chatImageAspectRatios = LruCache<File, Float>(128)

private fun File.readChatImageAspectRatio(): Float? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val orientation = runCatching {
        ExifInterface(absolutePath).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val width = bounds.outWidth.toFloat()
    val height = bounds.outHeight.toFloat()
    val ratio = if (orientation in ExifInterface.ORIENTATION_TRANSPOSE..ExifInterface.ORIENTATION_ROTATE_270) {
        height / width
    } else {
        width / height
    }
    // Keep extreme dimensions within practical layout constraints. Fit preserves the entire source.
    return ratio.coerceIn(0.25f, 4f)
}

@Suppress("DEPRECATION")
@Composable
internal fun AiImagePreview(file: File, onDismiss: () -> Unit, onEdit: (() -> Unit)? = null) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val view = LocalView.current
        val window = (view.parent as DialogWindowProvider).window
        DisposableEffect(window, view) {
            val black = android.graphics.Color.BLACK
            window.setBackgroundDrawable(ColorDrawable(black))
            window.attributes = window.attributes.apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                height = ViewGroup.LayoutParams.MATCH_PARENT
                flags = flags or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                        setFitInsetsTypes(0)
                        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    }
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> {
                        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                }
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                window.statusBarColor = black
                window.navigationBarColor = black
            }
            WindowInsetsControllerCompat(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
            onDispose { }
        }
        val download = rememberAiImageDownload(file)
        val share = rememberAiImageShare(file)
        Column(
            Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AiImagePreviewButton(Icons.Rounded.Close, stringResource(R.string.ai_image_close), onDismiss)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onEdit != null) {
                        AiImagePreviewButton(Icons.Rounded.Edit, stringResource(R.string.ai_image_continue), onEdit)
                    }
                    AiImagePreviewButton(Icons.Rounded.FileDownload, stringResource(R.string.action_download), download)
                    AiImagePreviewButton(Icons.Rounded.Share, stringResource(R.string.share), share)
                }
            }
            GlideImage(
                model = file,
                contentDescription = stringResource(R.string.ai_image_preview),
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentScale = ContentScale.Fit,
            ) { it.error(R.drawable.image_loading_error) }
        }
    }
}

@Composable
private fun AiImagePreviewButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp).clip(CircleShape).background(Color(0xFF2C2C2C)),
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp), tint = Color.White)
    }
}

@Composable
private fun rememberAiImageDownload(file: File): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val output = result.data?.data?.takeIf { result.resultCode == Activity.RESULT_OK }
            ?: return@rememberLauncherForActivityResult
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    requireNotNull(context.contentResolver.openOutputStream(output)).use { stream ->
                        file.inputStream().use { it.copyTo(stream) }
                    }
                }.isSuccess
            }
            Toast.makeText(context, if (saved) R.string.ai_image_saved else R.string.ai_image_save_failed,
                Toast.LENGTH_SHORT).show()
        }
    }
    return {
        scope.launch {
            val mime = withContext(Dispatchers.IO) { runCatching { file.imageMimeType() }.getOrNull() }
            if (mime == null) {
                Toast.makeText(context, R.string.ai_image_save_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val extension = imageFileExtension(mime)
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType(mime).putExtra(Intent.EXTRA_TITLE, "ng-image-${System.currentTimeMillis()}.$extension")
            try {
                picker.launch(intent)
            } catch (_: Exception) {
                Toast.makeText(context, R.string.ai_image_save_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
private fun rememberAiImageShare(file: File): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sharing by remember(file) { mutableStateOf(false) }
    return {
        if (!sharing) {
            sharing = true
            scope.launch {
                try {
                    val (shareFile, mime) = withContext(Dispatchers.IO) {
                        val mime = requireNotNull(file.imageMimeType())
                        val directory = File(context.cacheDir, "ai-image-share")
                        directory.mkdirs()
                        val target = File(directory, "ng-image-${file.nameWithoutExtension}.${imageFileExtension(mime)}")
                        if (!target.isFile || target.length() != file.length()) {
                            val temporary = File.createTempFile("ng-image-", ".tmp", directory)
                            try {
                                file.copyTo(temporary, overwrite = true)
                                check(temporary.renameTo(target))
                            } finally {
                                temporary.delete()
                            }
                        }
                        target to mime
                    }
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileProvider", shareFile)
                    ShareCompat.IntentBuilder(context).setType(mime).setStream(uri)
                        .setChooserTitle(R.string.share).startChooser()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    Toast.makeText(context, R.string.can_not_share, Toast.LENGTH_SHORT).show()
                } finally {
                    sharing = false
                }
            }
        }
    }
}
