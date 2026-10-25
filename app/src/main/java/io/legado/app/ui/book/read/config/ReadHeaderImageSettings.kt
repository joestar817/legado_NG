package io.legado.app.ui.book.read.config

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ReadHeaderImageStore
import io.legado.app.model.ReadBook
import io.legado.app.ui.design.components.compose.NgFormActionButton
import io.legado.app.ui.design.components.compose.NgFormActionButtonAppearance
import io.legado.app.ui.design.components.compose.NgSurface
import io.legado.app.ui.design.components.compose.NgSwitchControl
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.utils.SelectFileContract
import io.legado.app.utils.postEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

internal enum class ReadImagePosition { HEADER, FOOTER }

private data class ReadImageConfig(
    val path: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val enabled: Boolean = false,
)

private fun ReadBookConfig.Config.imageConfig(position: ReadImagePosition): ReadImageConfig =
    when (position) {
        ReadImagePosition.HEADER -> ReadImageConfig(
            headerImage, headerImageWidth, headerImageHeight, headerImageEnabled,
        )
        ReadImagePosition.FOOTER -> ReadImageConfig(
            footerImage, footerImageWidth, footerImageHeight, footerImageEnabled,
        )
    }

private fun ReadBookConfig.Config.setImageConfig(position: ReadImagePosition, image: ReadImageConfig) {
    when (position) {
        ReadImagePosition.HEADER -> {
            headerImage = image.path
            headerImageWidth = image.width
            headerImageHeight = image.height
            headerImageEnabled = image.enabled
        }
        ReadImagePosition.FOOTER -> {
            footerImage = image.path
            footerImageWidth = image.width
            footerImageHeight = image.height
            footerImageEnabled = image.enabled
        }
    }
}

internal class ReadImageSettingsState(
    val position: ReadImagePosition,
    val path: String,
    val width: Int,
    val height: Int,
    val enabled: Boolean,
    val importing: Boolean,
    val error: String?,
    val onChoose: () -> Unit,
    val onEnabledChanged: (Boolean) -> Unit,
    val onRemove: () -> Unit,
)

private fun imageScope(position: ReadImagePosition): String {
    val config = ReadBookConfig.config
    val target = when {
        ReadBookConfig.onlyThisBook -> "book"
        ReadBookConfig.shareLayout -> "shared"
        else -> "preset:${ReadBookConfig.configList.indexOfFirst { it === config }}"
    }
    return listOf(ReadBook.book?.bookUrl.orEmpty(), target, config.name, position.name)
        .joinToString("\u0000")
}

@Composable
internal fun rememberReadImageSettingsState(
    position: ReadImagePosition,
    revision: Int,
    onChanged: () -> Unit,
): ReadImageSettingsState {
    revision
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectingFor by rememberSaveable(position) { mutableStateOf<String?>(null) }
    var importing by remember(position) { mutableStateOf(false) }
    var error by remember(position) { mutableStateOf<String?>(null) }
    val config = ReadBookConfig.config
    val imageConfig = config.imageConfig(position)

    fun applyChange(image: ReadImageConfig) {
        config.setImageConfig(position, image)
        ReadBookConfig.save()
        postEvent(EventBus.UP_CONFIG, arrayListOf(2, 5))
        onChanged()
    }

    val picker = rememberLauncherForActivityResult(SelectFileContract()) { uri ->
        val requestedScope = selectingFor
        selectingFor = null
        if (uri != null && requestedScope != null) {
            if (requestedScope != imageScope(position)) {
                error = context.getString(R.string.read_header_image_scope_changed)
                return@rememberLauncherForActivityResult
            }
            val target = ReadBookConfig.config
            scope.launch {
                importing = true
                error = null
                try {
                    val image = withContext(Dispatchers.IO) {
                        ReadHeaderImageStore.install(context.applicationContext, uri)
                    }
                    if (target === ReadBookConfig.config && requestedScope == imageScope(position)) {
                        target.setImageConfig(position, ReadImageConfig(
                            path = image.path,
                            width = image.width,
                            height = image.height,
                            enabled = true,
                        ))
                        ReadBookConfig.save()
                        postEvent(EventBus.UP_CONFIG, arrayListOf(2, 5))
                        onChanged()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    error = failure.localizedMessage ?: context.getString(
                        if (position == ReadImagePosition.HEADER) R.string.read_header_image_failed
                        else R.string.read_footer_image_failed
                    )
                } finally {
                    importing = false
                }
            }
        }
    }

    fun choose() {
        error = null
        selectingFor = imageScope(position)
        picker.launch(arrayOf("image/png", "image/webp", "image/jpeg"))
    }

    return ReadImageSettingsState(
        position = position,
        path = imageConfig.path,
        width = imageConfig.width,
        height = imageConfig.height,
        enabled = imageConfig.enabled,
        importing = importing,
        error = error,
        onChoose = ::choose,
        onEnabledChanged = { enabled ->
            if (!importing) {
                if (enabled && imageConfig.path.isBlank()) choose()
                else applyChange(config.imageConfig(position).copy(enabled = enabled))
            }
        },
        onRemove = {
            error = null
            applyChange(ReadImageConfig())
        },
    )
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
internal fun ReadImageSettings(state: ReadImageSettingsState) {
    val hasImage = state.path.isNotBlank()
    val imageLabel = stringResource(
        if (state.position == ReadImagePosition.HEADER) R.string.read_header_image
        else R.string.read_footer_image
    )
    NgSurface(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clickable(enabled = !state.importing) { state.onEnabledChanged(!state.enabled) },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = imageLabel,
                color = Color(NgTheme.colors.onSurface),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
            )
            NgSwitchControl(
                checked = state.enabled,
                onCheckedChange = state.onEnabledChanged,
                enabled = !state.importing,
                modifier = Modifier.size(width = 44.dp, height = 28.dp),
            )
        }
        if (hasImage) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val aspectRatio = state.width.coerceAtLeast(1).toFloat() /
                    state.height.coerceAtLeast(1)
                val previewHeight = (maxWidth / aspectRatio).coerceIn(40.dp, 144.dp)
                GlideImage(
                    model = File(state.path),
                    contentDescription = imageLabel,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(previewHeight)
                        .clip(RoundedCornerShape(8.dp)),
                )
            }
            Spacer(Modifier.height(6.dp))
        } else {
            Spacer(Modifier.height(4.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NgFormActionButton(
                text = stringResource(
                    when {
                        state.importing -> R.string.read_header_image_importing
                        hasImage -> R.string.read_header_image_change
                        else -> R.string.read_header_image_choose
                    }
                ),
                onClick = state.onChoose,
                enabled = !state.importing,
                modifier = Modifier.weight(1f),
                appearance = NgFormActionButtonAppearance.SURFACE_CARD,
                buttonHeight = 40.dp,
                textSize = 13.sp,
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_image),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
            if (hasImage) {
                NgFormActionButton(
                    text = stringResource(R.string.read_header_image_remove),
                    onClick = state.onRemove,
                    enabled = !state.importing,
                    modifier = Modifier.weight(1f),
                    appearance = NgFormActionButtonAppearance.SURFACE_CARD,
                    buttonHeight = 40.dp,
                    textSize = 13.sp,
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_bookshelf_manage_delete),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
            }
        }
        state.error?.let {
            Text(
                text = it,
                color = Color(NgTheme.colors.error),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
    Spacer(Modifier.height(6.dp))
}
