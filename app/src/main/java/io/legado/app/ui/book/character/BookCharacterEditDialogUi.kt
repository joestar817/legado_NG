package io.legado.app.ui.book.character

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.design.components.NgButtonShapeVariant
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.compose.NgButton
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.theme.NgTheme

@Composable
internal fun BookCharacterEditDialogContent(
    title: String,
    confirmText: String,
    initialValue: BookCharacterFormValue,
    saving: Boolean,
    onCancel: () -> Unit,
    onConfirm: (BookCharacterFormValue) -> Unit,
) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf(initialValue.name) }
    var aliases by rememberSaveable {
        mutableStateOf(initialValue.aliases.joinToString(", "))
    }
    var gender by rememberSaveable { mutableStateOf(initialValue.gender) }
    var roleTag by rememberSaveable { mutableStateOf(initialValue.roleTag) }
    var intro by rememberSaveable { mutableStateOf(initialValue.intro.orEmpty()) }
    val genderOptions = remember(context) {
        BookCharacterLabels.genderValues.map { value ->
            CharacterFormSelectOption(value, BookCharacterLabels.genderLabel(context, value))
        }
    }
    val roleOptions = remember(context) {
        BookCharacterLabels.roleValues.map { value ->
            CharacterFormSelectOption(value, BookCharacterLabels.roleLabel(context, value))
        }
    }

    NgDialog(
        title = title,
        modifier = Modifier.fillMaxHeight(),
        variant = NgDialogVariant.FORM_EDITOR,
        actions = {
            NgButton(
                onClick = onCancel,
                modifier = Modifier
                    .width(96.dp)
                    .height(48.dp),
                variant = NgButtonVariant.OUTLINE,
                shapeVariant = NgButtonShapeVariant.ROUNDED,
                contentPadding = PaddingValues(horizontal = 18.dp),
            ) {
                Text(
                    text = stringResource(R.string.cancel),
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    maxLines = 1,
                )
            }
            NgButton(
                onClick = {
                    onConfirm(
                        BookCharacterFormValue(
                            name = name.trim(),
                            aliases = parseCharacterAliases(aliases),
                            gender = gender,
                            roleTag = roleTag,
                            intro = intro.trim().ifBlank { null },
                        )
                    )
                },
                modifier = Modifier
                    .width(96.dp)
                    .height(48.dp),
                enabled = !saving,
                variant = NgButtonVariant.PRIMARY_LIGHT_CONTENT,
                shapeVariant = NgButtonShapeVariant.ROUNDED,
                contentPadding = PaddingValues(horizontal = 18.dp),
            ) {
                Text(
                    text = confirmText,
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    maxLines = 1,
                )
            }
        },
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            CharacterFormTextField(
                label = stringResource(R.string.character_name),
                value = name,
                onValueChange = { name = it },
                placeholder = stringResource(R.string.character_name),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            CharacterFormTextField(
                label = stringResource(R.string.character_aliases),
                value = aliases,
                onValueChange = { aliases = it },
                placeholder = stringResource(R.string.character_aliases_hint),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.padding(top = 16.dp),
            )
            CharacterFormSelectField(
                label = stringResource(R.string.character_gender),
                selectedValue = gender,
                options = genderOptions,
                onValueChange = { gender = it },
                modifier = Modifier.padding(top = 16.dp),
            )
            CharacterFormSelectField(
                label = stringResource(R.string.character_role),
                selectedValue = roleTag,
                options = roleOptions,
                onValueChange = { roleTag = it },
                modifier = Modifier.padding(top = 16.dp),
            )
            CharacterFormTextField(
                label = stringResource(R.string.character_intro),
                value = intro,
                onValueChange = { intro = it },
                placeholder = stringResource(R.string.character_intro_hint),
                fieldHeight = 116.dp,
                singleLine = false,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun CharacterFormTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    fieldHeight: Dp = 56.dp,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val colors = NgTheme.colors
    val shape = RoundedCornerShape(NgTheme.shapes.mediumDp.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val outlineColor = if (focused) Color(colors.primary) else Color(colors.outline)
    val borderWidth = if (focused) 1.5.dp else 1.dp

    Column(modifier = modifier.fillMaxWidth()) {
        CharacterFormLabel(label)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(fieldHeight),
            interactionSource = interactionSource,
            singleLine = singleLine,
            maxLines = if (singleLine) 1 else 5,
            keyboardOptions = keyboardOptions,
            textStyle = TextStyle(
                color = Color(colors.onSurface),
                fontSize = 16.sp,
                lineHeight = 22.sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
            cursorBrush = SolidColor(Color(colors.primary)),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shape)
                        .background(colorResource(R.color.ng_surface_card))
                        .border(borderWidth, outlineColor, shape)
                        .padding(
                            start = 16.dp,
                            top = if (singleLine) 0.dp else 14.dp,
                            end = 16.dp,
                            bottom = if (singleLine) 0.dp else 14.dp,
                        ),
                    contentAlignment = if (singleLine) {
                        Alignment.CenterStart
                    } else {
                        Alignment.TopStart
                    },
                ) {
                    if (value.isEmpty() && !focused) {
                        Text(
                            text = placeholder,
                            color = Color(colors.onSurfaceVariant),
                            fontSize = 16.sp,
                            lineHeight = 22.sp,
                            maxLines = if (singleLine) 1 else 5,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

@Composable
private fun CharacterFormSelectField(
    label: String,
    selectedValue: String,
    options: List<CharacterFormSelectOption>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NgTheme.colors
    val density = LocalDensity.current
    val shape = RoundedCornerShape(NgTheme.shapes.mediumDp.dp)
    var expanded by remember { mutableStateOf(false) }
    var fieldWidthPx by remember { mutableIntStateOf(0) }
    val selectedLabel = options.firstOrNull { it.value == selectedValue }?.label.orEmpty()
    val fieldWidth = with(density) { fieldWidthPx.toDp() }

    Column(modifier = modifier.fillMaxWidth()) {
        CharacterFormLabel(label)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { fieldWidthPx = it.size.width },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(shape)
                    .background(colorResource(R.color.ng_surface_card))
                    .border(1.dp, Color(colors.outline), shape)
                    .clickable { expanded = true }
                    .semantics {
                        role = Role.Button
                        contentDescription = label
                        stateDescription = selectedLabel
                    }
                    .padding(start = 16.dp, end = 10.dp),
            ) {
                Text(
                    text = selectedLabel,
                    modifier = Modifier.align(Alignment.CenterStart),
                    color = Color(colors.onSurface),
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    painter = painterResource(R.drawable.ic_ng_spinner_arrow_down),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(20.dp),
                    tint = Color(colors.onSurfaceVariant),
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.width(fieldWidth),
                shape = shape,
                containerColor = colorResource(R.color.ng_surface_card),
                tonalElevation = 0.dp,
                shadowElevation = 4.dp,
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = option.label,
                                color = Color(colors.onSurface),
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        onClick = {
                            expanded = false
                            onValueChange(option.value)
                        },
                        modifier = Modifier.height(44.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CharacterFormLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, bottom = 6.dp),
        color = Color(NgTheme.colors.onSurfaceVariant),
        fontSize = 13.sp,
        lineHeight = 17.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private data class CharacterFormSelectOption(
    val value: String,
    val label: String,
)
