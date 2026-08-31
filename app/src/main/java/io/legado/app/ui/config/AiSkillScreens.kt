package io.legado.app.ui.config

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import io.legado.app.R
import io.legado.app.help.ai.AiSkillDefinition
import io.legado.app.ui.design.components.NgManagementListCardVariant
import io.legado.app.ui.design.components.NgStatusTagSpec
import io.legado.app.ui.design.components.compose.NgManagementLeadingText
import io.legado.app.ui.design.components.compose.NgManagementListCard
import io.legado.app.ui.design.components.compose.NgExpandableActionMenu
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuItem
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuVariant
import io.legado.app.ui.design.components.compose.NgFloatingTitleToolbar
import io.legado.app.ui.design.components.compose.NgFloatingToolbarActionButton
import io.legado.app.ui.design.components.compose.NgPopupToggleState
import io.legado.app.ui.design.components.compose.NgSwipeToDelete
import io.legado.app.ui.design.theme.NgTheme

@Immutable
internal data class AiSkillListItemUiModel(
    val skill: AiSkillDefinition,
    val name: String,
    val summary: String,
    val iconText: String,
    val headerTags: List<NgStatusTagSpec>,
)

internal sealed interface AiSkillListAction {
    data class OpenSkill(val skill: AiSkillDefinition) : AiSkillListAction
    data class DeleteSkill(val skill: AiSkillDefinition) : AiSkillListAction
}

@Composable
internal fun AiSkillListScreen(
    items: List<AiSkillListItemUiModel>,
    onBack: () -> Unit,
    onAddSkill: () -> Unit,
    onImportLocal: () -> Unit,
    onImportUrl: () -> Unit,
    onAction: (AiSkillListAction) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        AiSkillListTopBar(
            onBack = onBack,
            onAddSkill = onAddSkill,
            onImportLocal = onImportLocal,
            onImportUrl = onImportUrl,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items, key = { it.skill.id }) { item ->
                NgSwipeToDelete(
                    deletable = !item.skill.builtIn,
                    reordering = false,
                    onDeleteRequested = {
                        onAction(AiSkillListAction.DeleteSkill(item.skill))
                    },
                ) {
                    NgManagementListCard(
                        title = item.name,
                        summary = item.summary,
                        headerTags = item.headerTags,
                        variant = NgManagementListCardVariant.MULTILINE_SUMMARY,
                        onClick = { onAction(AiSkillListAction.OpenSkill(item.skill)) },
                        leading = {
                            NgManagementLeadingText(
                                text = item.iconText,
                                contentDescription = item.name,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AiSkillListTopBar(
    onBack: () -> Unit,
    onAddSkill: () -> Unit,
    onImportLocal: () -> Unit,
    onImportUrl: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val menuState = remember { NgPopupToggleState() }
    val menuItems = remember {
        listOf(
            NgExpandableActionMenuItem(
                itemId = SKILL_ACTION_ADD,
                titleRes = R.string.ai_skill_add,
                iconRes = R.drawable.ic_add,
            ),
            NgExpandableActionMenuItem(
                itemId = SKILL_ACTION_IMPORT_LOCAL,
                titleRes = R.string.ai_skill_import_file,
                iconRes = R.drawable.ic_import,
            ),
            NgExpandableActionMenuItem(
                itemId = SKILL_ACTION_IMPORT_URL,
                titleRes = R.string.ai_skill_import_github,
                iconRes = R.drawable.ic_add_online,
            ),
        )
    }
    NgFloatingTitleToolbar(
        title = stringResource(R.string.ai_prompt_menu),
        onBack = onBack,
        modifier = modifier,
    ) {
        Box {
            NgFloatingToolbarActionButton(
                iconRes = R.drawable.ic_grid_menu,
                contentDescription = stringResource(R.string.menu),
                onClick = menuState::onAnchorClick,
            )
            NgExpandableActionMenu(
                expanded = menuState.expanded,
                onDismissRequest = menuState::onDismissRequest,
                items = menuItems,
                variant = NgExpandableActionMenuVariant.SIDE_SLIDE,
                menuContainerColor = colorResource(R.color.ng_surface_card),
                properties = PopupProperties(focusable = true, clippingEnabled = false),
                onItemClick = { item ->
                    menuState.close()
                    when (item.itemId) {
                        SKILL_ACTION_ADD -> onAddSkill()
                        SKILL_ACTION_IMPORT_LOCAL -> onImportLocal()
                        SKILL_ACTION_IMPORT_URL -> onImportUrl()
                    }
                },
            )
        }
    }
}

private const val SKILL_ACTION_ADD = 0x4E470111
private const val SKILL_ACTION_IMPORT_LOCAL = 0x4E470112
private const val SKILL_ACTION_IMPORT_URL = 0x4E470113

internal sealed interface AiSkillFileRowUiModel {
    val path: String
    val name: String
    val depth: Int

    @Immutable
    data class Directory(
        override val path: String,
        override val name: String,
        override val depth: Int,
        val expanded: Boolean,
    ) : AiSkillFileRowUiModel

    @Immutable
    data class File(
        override val path: String,
        override val name: String,
        override val depth: Int,
        val sizeText: String,
        val editable: Boolean,
    ) : AiSkillFileRowUiModel
}

internal sealed interface AiSkillDetailAction {
    data class ToggleDirectory(val path: String) : AiSkillDetailAction
    data class OpenFile(val path: String) : AiSkillDetailAction
    data class EditFile(val path: String) : AiSkillDetailAction
}

@Composable
internal fun AiSkillDetailScreen(
    rows: List<AiSkillFileRowUiModel>,
    onAction: (AiSkillDetailAction) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(R.color.ng_surface_card)),
    ) {
        HorizontalDivider(
            thickness = 1.dp,
            color = Color(NgTheme.colors.outlineVariant).copy(alpha = 0.52f),
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            itemsIndexed(rows, key = { _, item -> item.path }) { index, item ->
                when (item) {
                    is AiSkillFileRowUiModel.Directory -> AiSkillDirectoryRow(
                        item = item,
                        onClick = { onAction(AiSkillDetailAction.ToggleDirectory(item.path)) },
                    )
                    is AiSkillFileRowUiModel.File -> AiSkillFileRow(
                        item = item,
                        onClick = { onAction(AiSkillDetailAction.OpenFile(item.path)) },
                        onEdit = { onAction(AiSkillDetailAction.EditFile(item.path)) },
                    )
                }
                if (index < rows.lastIndex) {
                    AiSkillRowDivider(item)
                }
            }
        }
    }
}

@Composable
private fun AiSkillRowDivider(item: AiSkillFileRowUiModel) {
    val startPadding = when (item) {
        is AiSkillFileRowUiModel.Directory -> 70 + item.depth * 20
        is AiSkillFileRowUiModel.File -> 52 + item.depth * 20
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = startPadding.dp, end = 16.dp),
        thickness = 0.5.dp,
        color = Color(NgTheme.colors.outlineVariant).copy(alpha = 0.42f),
    )
}

@Composable
private fun AiSkillDirectoryRow(
    item: AiSkillFileRowUiModel.Directory,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(start = (16 + item.depth * 20).dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right_20),
            contentDescription = null,
            modifier = Modifier
                .size(18.dp)
                .padding(2.dp)
                .rotate(if (item.expanded) 90f else 0f),
            tint = colorResource(R.color.ng_on_surface_variant),
        )
        Icon(
            painter = painterResource(
                if (item.expanded) R.drawable.ic_folder_open else R.drawable.ic_folder_outline
            ),
            contentDescription = null,
            modifier = Modifier
                .padding(start = 6.dp)
                .size(20.dp),
            tint = Color(NgTheme.colors.primary),
        )
        AiSkillPathText(
            text = item.name,
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun AiSkillFileRow(
    item: AiSkillFileRowUiModel.File,
    onClick: () -> Unit,
    onEdit: () -> Unit,
) {
    val rowInteractionSource = remember { MutableInteractionSource() }
    val editInteractionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(
                interactionSource = rowInteractionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(start = (16 + item.depth * 20).dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_code),
            contentDescription = null,
            modifier = Modifier
                .padding(start = 6.dp)
                .size(20.dp),
            tint = Color(NgTheme.colors.primary),
        )
        AiSkillPathText(
            text = item.name,
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
        )
        Text(
            text = item.sizeText,
            modifier = Modifier.padding(start = 8.dp),
            color = colorResource(R.color.ng_on_surface_variant),
            fontSize = 13.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
        )
        if (item.editable) {
            Box(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(36.dp)
                    .clickable(
                        interactionSource = editInteractionSource,
                        indication = null,
                        onClick = onEdit,
                    )
                    .padding(9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_edit),
                    contentDescription = stringResource(R.string.edit),
                    modifier = Modifier.fillMaxSize(),
                    tint = colorResource(R.color.ng_on_surface),
                )
            }
        }
    }
}

@Composable
private fun AiSkillPathText(
    text: String,
    modifier: Modifier,
    fontWeight: FontWeight = FontWeight.Normal,
) {
    val style = TextStyle(
        color = colorResource(R.color.ng_on_surface),
        fontSize = 15.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = fontWeight,
    )
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier) {
        val availableWidth = maxWidth
        val displayedText = remember(text, availableWidth, style, density) {
            middleEllipsize(
                text = text,
                maxWidthPx = with(density) { availableWidth.roundToPx() },
                measureWidth = { candidate ->
                    textMeasurer.measure(
                        text = candidate,
                        style = style,
                        maxLines = 1,
                        softWrap = false,
                    ).size.width
                },
            )
        }
        Text(
            text = displayedText,
            modifier = Modifier.fillMaxWidth(),
            style = style,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

private fun middleEllipsize(
    text: String,
    maxWidthPx: Int,
    measureWidth: (String) -> Int,
): String {
    if (maxWidthPx <= 0 || text.isEmpty() || measureWidth(text) <= maxWidthPx) return text
    val ellipsis = "…"
    if (measureWidth(ellipsis) > maxWidthPx) return ""
    val codePointCount = text.codePointCount(0, text.length)
    var low = 0
    var high = codePointCount
    var best = ellipsis
    while (low <= high) {
        val kept = (low + high) ushr 1
        val prefixLength = (kept + 1) / 2
        val suffixLength = kept / 2
        val prefixEnd = text.offsetByCodePoints(0, prefixLength)
        val suffixStart = text.offsetByCodePoints(text.length, -suffixLength)
        val candidate = buildString(prefixEnd + 1 + text.length - suffixStart) {
            append(text, 0, prefixEnd)
            append(ellipsis)
            append(text, suffixStart, text.length)
        }
        if (measureWidth(candidate) <= maxWidthPx) {
            best = candidate
            low = kept + 1
        } else {
            high = kept - 1
        }
    }
    return best
}
