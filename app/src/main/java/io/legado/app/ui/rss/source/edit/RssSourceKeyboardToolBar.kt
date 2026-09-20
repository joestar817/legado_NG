package io.legado.app.ui.rss.source.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.legado.app.R
import io.legado.app.data.entities.KeyboardAssist
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.NgDialogTextActionButton
import io.legado.app.ui.design.theme.NgTheme

private data class RssKeyboardTool(val key: String, val label: String, val action: RssSourceEditAction?)

/** 沿用书源编辑的辅助按键数据和行数配置；仅输入时显示。 */
@Composable
internal fun RssSourceKeyboardToolBar(
    assists: List<KeyboardAssist>,
    rowCount: Int,
    imeVisible: Boolean,
    onAction: (RssSourceEditAction) -> Unit,
) {
    var showHelp by remember { mutableStateOf(false) }
    if (imeVisible) {
        val help = stringResource(R.string.help)
        val undo = stringResource(R.string.rss_editor_undo)
        val redo = stringResource(R.string.rss_editor_redo)
        val tools = remember(assists, help, undo, redo) {
            listOf(
                RssKeyboardTool("help", help, null),
                RssKeyboardTool("undo", undo, RssSourceEditAction.Undo),
                RssKeyboardTool("redo", redo, RssSourceEditAction.Redo),
            ) + assists.map {
                RssKeyboardTool("assist:${it.type}:${it.key}", it.key, RssSourceEditAction.InsertText(it.value))
            }
        }
        val rows = rowCount.coerceIn(1, 5)
        LazyHorizontalGrid(
            rows = GridCells.Fixed(rows),
            modifier = Modifier.fillMaxWidth().height((rows * 38 + 8).dp)
                .background(Color(NgTheme.colors.cardContainer)),
            contentPadding = PaddingValues(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(tools, key = RssKeyboardTool::key) { item ->
                Box(
                    modifier = Modifier.height(34.dp).widthIn(min = 40.dp)
                        .background(Color(NgTheme.colors.surfaceContainerHigh), RoundedCornerShape(4.dp))
                        .clickable(role = Role.Button) {
                            item.action?.let(onAction) ?: run { showHelp = true }
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(item.label, color = Color(NgTheme.colors.onSurface), fontSize = 14.sp, maxLines = 1)
                }
            }
        }
    }
    if (showHelp) {
        val actions = listOf(
            R.string.rss_source_insert_url_option to RssSourceEditAction.InsertUrlOption,
            R.string.rss_source_tutorial to RssSourceEditAction.Help,
            R.string.rss_source_js_tutorial to RssSourceEditAction.JsHelp,
            R.string.rss_source_regex_tutorial to RssSourceEditAction.RegexHelp,
            R.string.select_file to RssSourceEditAction.SelectFile,
            R.string.assists_key_config to RssSourceEditAction.KeyboardConfig,
        )
        Dialog(onDismissRequest = { showHelp = false }) {
            NgDialog(
                title = stringResource(R.string.help),
                variant = NgDialogVariant.LONG_CONTENT,
                actions = {
                    NgDialogTextActionButton(stringResource(R.string.cancel), { showHelp = false })
                },
            ) {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    items(actions, key = { it.first }) { (label, action) ->
                        Box(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .clickable(role = Role.Button) {
                                    showHelp = false
                                    onAction(action)
                                }.padding(horizontal = 4.dp, vertical = 12.dp),
                        ) {
                            Text(stringResource(label), fontSize = 16.sp, color = Color(NgTheme.colors.onSurface))
                        }
                    }
                }
            }
        }
    }
}
