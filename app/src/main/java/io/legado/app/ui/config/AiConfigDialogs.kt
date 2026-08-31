package io.legado.app.ui.config

import android.content.res.ColorStateList
import android.widget.NumberPicker
import android.widget.RadioButton
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.R
import io.legado.app.lib.theme.accentColor
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.NgDialogTextActionButton
import io.legado.app.ui.design.components.compose.NgFormActionButton
import io.legado.app.ui.design.components.compose.NgFormActionButtonAppearance
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.utils.hideSoftInput

@Immutable
internal data class AiOperationPermissionOptionUiModel(
    val title: String,
    val summary: String,
    val selected: Boolean,
)

@Composable
internal fun AiClassicDialogContent(
    title: String,
    message: String,
    cancelText: String?,
    confirmText: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    NgDialog(
        title = title,
        modifier = Modifier.heightIn(min = 156.dp),
        variant = NgDialogVariant.CLASSIC_CONFIRMATION,
        titleFontWeight = FontWeight.Normal,
        actions = {
            cancelText?.let {
                NgDialogTextActionButton(
                    text = it,
                    onClick = onCancel,
                )
            }
            NgDialogTextActionButton(
                text = confirmText,
                onClick = onConfirm,
            )
        },
    ) {
        Text(
            text = message,
            color = androidx.compose.ui.graphics.Color(NgTheme.colors.onSurface),
            fontSize = 16.sp,
            lineHeight = 24.sp,
        )
    }
}

@Composable
internal fun AiNumberPickerDialogContent(
    title: String,
    minValue: Int,
    maxValue: Int,
    initialValue: Int,
    cancelText: String,
    confirmText: String,
    onCancel: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var selectedValue by remember {
        mutableIntStateOf(initialValue.coerceIn(minValue, maxValue))
    }
    val numberPickerRef = remember { arrayOfNulls<NumberPicker>(1) }

    NgDialog(
        title = title,
        variant = NgDialogVariant.CLASSIC_CONFIRMATION,
        titleFontWeight = FontWeight.Normal,
        actions = {
            NgDialogTextActionButton(
                text = cancelText,
                onClick = onCancel,
            )
            NgDialogTextActionButton(
                text = confirmText,
                onClick = {
                    numberPickerRef[0]?.let { numberPicker ->
                        numberPicker.clearFocus()
                        numberPicker.hideSoftInput()
                        onConfirm(numberPicker.value)
                    }
                },
            )
        },
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { context ->
                    NumberPicker(context).apply {
                        isVerticalScrollBarEnabled = false
                        isHorizontalScrollBarEnabled = false
                        this.minValue = minValue
                        this.maxValue = maxValue
                        value = selectedValue
                        setOnValueChangedListener { _, _, newValue ->
                            selectedValue = newValue
                        }
                        numberPickerRef[0] = this
                    }
                },
                update = { numberPicker ->
                    numberPicker.minValue = minValue
                    numberPicker.maxValue = maxValue
                    if (numberPicker.value != selectedValue) {
                        numberPicker.value = selectedValue
                    }
                },
            )
        }
    }
}

@Composable
internal fun AiOperationPermissionDialogContent(
    title: String,
    options: List<AiOperationPermissionOptionUiModel>,
    onSelect: (Int) -> Unit,
) {
    val accentColor = LocalContext.current.accentColor
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(colorResource(R.color.ng_surface)),
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 10.dp),
            color = colorResource(R.color.ng_on_surface),
            fontSize = 24.sp,
            lineHeight = 29.sp,
            letterSpacing = 0.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        )
        Column(
            modifier = Modifier.padding(start = 20.dp, end = 24.dp, bottom = 22.dp),
        ) {
            options.forEachIndexed { index, option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(index) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AndroidView(
                        factory = { context ->
                            RadioButton(context).apply {
                                isClickable = false
                            }
                        },
                        modifier = Modifier.size(48.dp),
                        update = { radioButton ->
                            radioButton.isChecked = option.selected
                            radioButton.buttonTintList = ColorStateList.valueOf(accentColor)
                        },
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = option.title,
                            color = colorResource(R.color.ng_on_surface),
                            fontSize = 17.sp,
                            lineHeight = 21.sp,
                            letterSpacing = 0.sp,
                        )
                        Text(
                            text = option.summary,
                            modifier = Modifier.padding(top = 4.dp),
                            color = colorResource(R.color.ng_on_surface_variant),
                            fontSize = 13.sp,
                            lineHeight = 16.sp,
                            letterSpacing = 0.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun AiMemoryDialogContent(
    title: String,
    summary: String,
    countLabel: String,
    countValue: String,
    sizeLabel: String,
    sizeValue: String,
    clearEnabled: Boolean,
    cancelText: String,
    clearText: String,
    onCancel: () -> Unit,
    onClear: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(colorResource(R.color.ng_surface))
            .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 18.dp),
    ) {
        Text(
            text = title,
            color = colorResource(R.color.ng_on_surface),
            fontSize = 24.sp,
            lineHeight = 29.sp,
            letterSpacing = 0.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        )
        Text(
            text = summary,
            modifier = Modifier.padding(top = 8.dp, bottom = 18.dp),
            color = colorResource(R.color.ng_on_surface_variant),
            fontSize = 14.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.sp,
        )
        AiMemoryStatRow(countLabel, countValue)
        AiMemoryStatRow(sizeLabel, sizeValue)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 22.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NgFormActionButton(
                text = cancelText,
                onClick = onCancel,
                modifier = Modifier.width(76.dp),
                variant = NgButtonVariant.OUTLINE,
                appearance = NgFormActionButtonAppearance.SURFACE_CARD,
            )
            Spacer(Modifier.width(8.dp))
            NgFormActionButton(
                text = clearText,
                onClick = onClear,
                modifier = Modifier.width(90.dp),
                enabled = clearEnabled,
                variant = NgButtonVariant.PRIMARY,
            )
        }
    }
}

@Composable
private fun AiMemoryStatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = colorResource(R.color.ng_on_surface_variant),
            fontSize = 15.sp,
            lineHeight = 19.sp,
            letterSpacing = 0.sp,
        )
        Text(
            text = value,
            color = colorResource(R.color.ng_on_surface),
            fontSize = 15.sp,
            lineHeight = 19.sp,
            letterSpacing = 0.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        )
    }
}
