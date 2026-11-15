package io.legado.app.ui.resource

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Subject
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.WebAsset
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.legado.app.ui.design.components.compose.NgDialogDivider
import io.legado.app.ui.design.components.compose.NgDialogSurface
import io.legado.app.ui.design.components.compose.NgDialogTextActionButton
import io.legado.app.ui.design.theme.NgTheme

internal enum class ResourceFontDestination(val label: String) {
    INTERFACE("界面字体"),
    BODY("正文字体"),
    TITLE("标题字体"),
}

/** Identifies a resource and a book; the installed path is resolved again in the reader. */
internal data class ResourceReaderFontRequest(
    val resourceId: String,
    val destination: ResourceFontDestination,
    val bookUrl: String,
) {
    fun writeTo(intent: Intent) {
        intent.putExtra(EXTRA_RESOURCE, resourceId)
        intent.putExtra(EXTRA_DESTINATION, destination.name)
        intent.putExtra(EXTRA_BOOK, bookUrl)
    }

    companion object {
        private const val EXTRA_RESOURCE = "io.legado.app.resource.FONT_ID"
        private const val EXTRA_DESTINATION = "io.legado.app.resource.FONT_DESTINATION"
        private const val EXTRA_BOOK = "io.legado.app.resource.FONT_BOOK"

        fun readFrom(intent: Intent): ResourceReaderFontRequest? {
            val id = intent.getStringExtra(EXTRA_RESOURCE)?.takeIf { it.isNotBlank() } ?: return null
            val book = intent.getStringExtra(EXTRA_BOOK)?.takeIf { it.isNotBlank() } ?: return null
            val destination = when (intent.getStringExtra(EXTRA_DESTINATION)) {
                ResourceFontDestination.BODY.name -> ResourceFontDestination.BODY
                ResourceFontDestination.TITLE.name -> ResourceFontDestination.TITLE
                else -> return null
            }
            return ResourceReaderFontRequest(id, destination, book)
        }

        fun clear(intent: Intent) {
            intent.removeExtra(EXTRA_RESOURCE)
            intent.removeExtra(EXTRA_DESTINATION)
            intent.removeExtra(EXTRA_BOOK)
        }
    }
}

@Composable
internal fun ResourceFontUseDialog(
    onDismiss: () -> Unit,
    onSelect: (ResourceFontDestination) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        NgDialogSurface {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text(
                    text = "字体用途",
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 24.dp),
                    color = Color(NgTheme.colors.onSurface),
                    fontSize = 18.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    ResourceFontDestination.entries.forEachIndexed { index, destination ->
                        if (index > 0) {
                            VerticalDivider(
                                modifier = Modifier.height(64.dp),
                                thickness = 0.6.dp,
                                color = Color(NgTheme.colors.outlineVariant).copy(alpha = 0.26f),
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f)
                                .clickable(role = Role.Button) { onSelect(destination) }
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                imageVector = when (destination) {
                                    ResourceFontDestination.INTERFACE -> Icons.Outlined.WebAsset
                                    ResourceFontDestination.BODY -> Icons.AutoMirrored.Outlined.Subject
                                    ResourceFontDestination.TITLE -> Icons.Outlined.TextFields
                                },
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = Color(NgTheme.colors.primary),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = destination.label,
                                color = Color(NgTheme.colors.onSurface),
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                NgDialogDivider()
                NgDialogTextActionButton(
                    text = "取消",
                    onClick = onDismiss,
                    secondary = true,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                )
            }
        }
    }
}
