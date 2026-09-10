package io.legado.app.ui.book.read.config

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.book.read.page.provider.ReadHighlightImageRenderer
import io.legado.app.ui.design.components.compose.NgSlider
import io.legado.app.ui.design.components.compose.NgSliderVariant
import io.legado.app.ui.design.theme.NgTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

internal fun nineSliceImageBounds(width: Int, height: Int, areaWidth: Float, areaHeight: Float): Rect {
    if (width <= 0 || height <= 0 || areaWidth <= 0 || areaHeight <= 0) return Rect.Zero
    val scale = minOf(areaWidth / width, areaHeight / height)
    val w = width * scale
    val h = height * scale
    return Rect((areaWidth - w) / 2, (areaHeight - h) / 2, (areaWidth + w) / 2, (areaHeight + h) / 2)
}

internal fun stepNineSliceCut(value: Float, delta: Int): Float =
    ((value * 100).roundToInt() + delta).coerceIn(0, 50) / 100f

/** 宽条圆角素材的起始建议，只有用户点击时才写入草稿。 */
internal fun estimateNineSliceCuts(width: Int, height: Int): List<Float> {
    if (width <= 0 || height <= 0) return listOf(0.1f, 0.1f, 0.1f, 0.1f)
    val corner = minOf(width, height) / 4f
    val horizontal = kotlin.math.ceil(corner / width * 100f).toFloat().coerceIn(1f, 49f) / 100f
    val vertical = kotlin.math.ceil(corner / height * 100f).toFloat().coerceIn(1f, 49f) / 100f
    return listOf(horizontal, horizontal, vertical, vertical)
}

/** 参考 MD3 HighlightRuleEditSheet.NinePatchEditorDialog，保留独立草稿与单 Canvas 坐标。 */
@Composable
internal fun ReadHighlightNineSlicePage(
    state: ReadStyleUiState,
    contentColor: Color,
    accentColor: Color,
    actions: ReadStyleActions,
) {
    val draft = state.highlightDraft ?: return
    val imagePath = draft.bgImage.orEmpty()
    var cuts by remember(draft.id, imagePath) {
        mutableStateOf(listOf(draft.npLeft, draft.npRight, draft.npTop, draft.npBottom))
    }
    val bitmapState by produceState<Pair<Boolean, Bitmap?>>(false to null, imagePath) {
        value = false to null
        value = true to withContext(Dispatchers.IO) { ReadHighlightImageRenderer.loadBitmap(imagePath) }
    }
    val image = remember(bitmapState) { bitmapState.second?.asImageBitmap() }
    val lineColor = Color(NgTheme.colors.error)
    val labels = listOf(
        stringResource(R.string.highlight_nine_slice_left),
        stringResource(R.string.highlight_nine_slice_right),
        stringResource(R.string.highlight_nine_slice_top),
        stringResource(R.string.highlight_nine_slice_bottom),
    )
    fun update(index: Int, value: Float) {
        cuts = cuts.toMutableList().apply { this[index] = value.coerceIn(0f, 0.5f) }
    }
    Column(Modifier.fillMaxWidth().height(LocalConfiguration.current.screenHeightDp.dp * 0.85f)) {
        AdvancedEditorHeader(
            title = stringResource(R.string.highlight_nine_slice_editor),
            contentColor = contentColor,
            onBack = actions.onBack,
            actionLabel = stringResource(R.string.save),
            actionColor = accentColor,
            onAction = {
                actions.onHighlightDraftChanged(
                    draft.copy(npLeft = cuts[0], npRight = cuts[1], npTop = cuts[2], npBottom = cuts[3])
                )
                actions.onBack()
            },
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
                    Text(
                        text = stringResource(R.string.highlight_nine_slice_estimate),
                        color = accentColor,
                        modifier = Modifier.clickable(enabled = image != null) {
                            image?.let { cuts = estimateNineSliceCuts(it.width, it.height) }
                        }.padding(horizontal = 12.dp, vertical = 12.dp),
                    )
                }
            }
            item {
                Box(
                    Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(12.dp))
                        .background(contentColor.copy(alpha = 0.04f)).padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (image != null) {
                        Canvas(Modifier.fillMaxSize()) {
                            val bounds = nineSliceImageBounds(image.width, image.height, size.width, size.height)
                            drawImage(
                                image,
                                dstOffset = IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()),
                                dstSize = IntSize(bounds.width.roundToInt().coerceAtLeast(1), bounds.height.roundToInt().coerceAtLeast(1)),
                            )
                            val stroke = 2.dp.toPx()
                            val left = bounds.left + bounds.width * cuts[0]
                            val right = bounds.right - bounds.width * cuts[1]
                            val top = bounds.top + bounds.height * cuts[2]
                            val bottom = bounds.bottom - bounds.height * cuts[3]
                            drawLine(lineColor, Offset(left, bounds.top), Offset(left, bounds.bottom), stroke)
                            drawLine(lineColor, Offset(right, bounds.top), Offset(right, bounds.bottom), stroke)
                            drawLine(lineColor, Offset(bounds.left, top), Offset(bounds.right, top), stroke)
                            drawLine(lineColor, Offset(bounds.left, bottom), Offset(bounds.right, bottom), stroke)
                        }
                    } else {
                        Text(
                            stringResource(if (bitmapState.first) R.string.highlight_nine_slice_image_unavailable else R.string.loading),
                            color = contentColor, fontSize = 13.sp,
                        )
                    }
                }
            }
            item {
                val panelShape = RoundedCornerShape(16.dp)
                Column(
                    Modifier.fillMaxWidth().padding(top = 16.dp)
                        .clip(panelShape)
                        .background(Color(NgTheme.colors.surface).copy(alpha = 0.42f))
                        .border(0.7.dp, contentColor.copy(alpha = 0.08f), panelShape)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                ) {
                    repeat(4) { index ->
                        Row(
                            Modifier.fillMaxWidth().height(56.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(labels[index], Modifier.width(56.dp), color = contentColor, fontSize = 13.sp)
                            NineSliceStepButton(
                                increase = false,
                                enabled = cuts[index] > 0f,
                                contentColor = contentColor,
                                onClick = { update(index, stepNineSliceCut(cuts[index], -1)) },
                            )
                            NgSlider(
                                value = cuts[index] * 100,
                                onValueChange = { update(index, it.roundToInt() / 100f) },
                                valueRange = 0f..50f,
                                steps = 49,
                                variant = NgSliderVariant.INLINE,
                                modifier = Modifier.weight(1f),
                            )
                            NineSliceStepButton(
                                increase = true,
                                enabled = cuts[index] < 0.5f,
                                contentColor = contentColor,
                                onClick = { update(index, stepNineSliceCut(cuts[index], 1)) },
                            )
                            Text(
                                "${(cuts[index] * 100).roundToInt()}%", Modifier.width(32.dp),
                                color = contentColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End,
                            )
                        }
                        if (index < 3) {
                            HorizontalDivider(color = contentColor.copy(alpha = 0.08f), thickness = 0.7.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NineSliceStepButton(
    increase: Boolean,
    enabled: Boolean,
    contentColor: Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(36.dp).clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(24.dp).clip(CircleShape)
                .background(Color(NgTheme.colors.surface).copy(alpha = 0.30f))
                .border(0.7.dp, contentColor.copy(alpha = if (enabled) 0.18f else 0.08f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (increase) Icons.Rounded.Add else Icons.Rounded.Remove,
                stringResource(if (increase) R.string.highlight_nine_slice_increase else R.string.highlight_nine_slice_decrease),
                Modifier.size(16.dp), tint = contentColor.copy(alpha = if (enabled) 0.85f else 0.38f),
            )
        }
    }
}
