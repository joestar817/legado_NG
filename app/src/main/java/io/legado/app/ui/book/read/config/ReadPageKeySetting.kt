package io.legado.app.ui.book.read.config

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.help.config.PageKeyBindings
import io.legado.app.help.config.PageKeyDirection
import io.legado.app.help.config.isValidPageKey
import io.legado.app.ui.design.theme.NgTheme
import kotlinx.coroutines.delay

/** The dialog receives physical keys before they can reach the reader or change system volume. */
internal class PageKeyCaptureController(
    private val onCaptured: (PageKeyDirection, Int) -> Unit,
) {
    var waitingFor by mutableStateOf<PageKeyDirection?>(null)
        private set
    private var pendingKeyUp = KeyEvent.KEYCODE_UNKNOWN

    fun start(direction: PageKeyDirection) {
        pendingKeyUp = KeyEvent.KEYCODE_UNKNOWN
        waitingFor = direction
    }

    fun cancel() {
        waitingFor = null
    }

    fun onKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        if (pendingKeyUp != KeyEvent.KEYCODE_UNKNOWN && keyCode == pendingKeyUp) {
            if (event.action == KeyEvent.ACTION_UP) pendingKeyUp = KeyEvent.KEYCODE_UNKNOWN
            return true
        }
        val direction = waitingFor ?: return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            when {
                keyCode == KeyEvent.KEYCODE_BACK -> {
                    pendingKeyUp = keyCode
                    cancel()
                }
                keyCode <= KeyEvent.KEYCODE_UNKNOWN -> Unit
                else -> {
                    pendingKeyUp = keyCode
                    waitingFor = null
                    onCaptured(direction, keyCode)
                }
            }
        }
        return true
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ReadPageKeyExpandableSetting(
    bindings: PageKeyBindings,
    capture: PageKeyCaptureController,
    onBindingsChanged: (PageKeyBindings) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val bringIntoView = remember { BringIntoViewRequester() }
    Column(Modifier.fillMaxWidth().bringIntoViewRequester(bringIntoView)) {
        Box(Modifier.padding(horizontal = 14.dp)) {
            EditorNavigationRow(
                title = stringResource(R.string.custom_page_key),
                color = Color(NgTheme.colors.onSurface),
                expanded = expanded,
                rowHeight = 48.dp,
                arrowVariant = EditorNavigationArrowVariant.RIGHT_TO_DOWN,
                onClick = {
                    if (expanded) capture.cancel()
                    expanded = !expanded
                },
            )
        }
        AnimatedVisibility(visible = expanded) {
            PageKeyBindingControls(
                bindings = bindings,
                capture = capture,
                onBindingsChanged = onBindingsChanged,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
    }
    LaunchedEffect(expanded) {
        if (expanded) {
            delay(250)
            bringIntoView.bringIntoView()
        }
    }
}

@Composable
internal fun PageKeyBindingControls(
    bindings: PageKeyBindings,
    capture: PageKeyCaptureController,
    onBindingsChanged: (PageKeyBindings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val previousTitle = stringResource(R.string.prev_page)
    val nextTitle = stringResource(R.string.next_page)
    val previousDescription = stringResource(R.string.prev_page_key)
    val nextDescription = stringResource(R.string.next_page_key)
    val bindLabel = stringResource(R.string.page_key_bind)
    val waitingLabel = stringResource(R.string.page_key_waiting)
    val previousWaiting = capture.waitingFor == PageKeyDirection.PREVIOUS
    val nextWaiting = capture.waitingFor == PageKeyDirection.NEXT
    val previousValue = when {
        previousWaiting -> waitingLabel
        bindings.previous.isEmpty() -> bindLabel
        else -> pageKeyLabel(bindings.previous)
    }
    val nextValue = when {
        nextWaiting -> waitingLabel
        bindings.next.isEmpty() -> bindLabel
        else -> pageKeyLabel(bindings.next)
    }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val textStyle = LocalTextStyle.current
    fun measureValue(value: String, waiting: Boolean) = with(density) {
        val valueStyle = if (waiting) {
            textStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium)
        } else textStyle.copy(fontSize = 13.sp)
        measurer.measure(value, valueStyle, maxLines = 1).size.width.toDp()
    }
    val previousValueWidth = measureValue(previousValue, previousWaiting)
    val nextValueWidth = measureValue(nextValue, nextWaiting)
    Column(modifier.fillMaxWidth()) {
        PageKeyBindingRow(
            title = previousTitle,
            accessibilityTitle = previousDescription,
            value = bindings.previous,
            displayValue = previousValue,
            displayValueWidth = previousValueWidth,
            waiting = previousWaiting,
            onBind = { capture.start(PageKeyDirection.PREVIOUS) },
            onCancel = capture::cancel,
            onClear = {
                capture.cancel()
                onBindingsChanged(bindings.clear(PageKeyDirection.PREVIOUS))
            },
        )
        PageKeyBindingRow(
            title = nextTitle,
            accessibilityTitle = nextDescription,
            value = bindings.next,
            displayValue = nextValue,
            displayValueWidth = nextValueWidth,
            waiting = nextWaiting,
            onBind = { capture.start(PageKeyDirection.NEXT) },
            onCancel = capture::cancel,
            onClear = {
                capture.cancel()
                onBindingsChanged(bindings.clear(PageKeyDirection.NEXT))
            },
        )
    }
}

@Composable
private fun PageKeyBindingRow(
    title: String,
    accessibilityTitle: String,
    value: String,
    displayValue: String,
    displayValueWidth: Dp,
    waiting: Boolean,
    onBind: () -> Unit,
    onCancel: () -> Unit,
    onClear: () -> Unit,
) {
    val primary = Color(NgTheme.colors.primary)
    val tint = primary.copy(alpha = 0.12f)
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp).padding(start = 28.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
            color = Color(NgTheme.colors.onSurface),
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        when {
            waiting -> {
                Text(
                    text = displayValue,
                    modifier = Modifier.clip(shape).background(tint).border(0.8.dp, primary, shape)
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                    color = primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
                Box(
                    modifier = Modifier.size(48.dp)
                        .clickable(role = Role.Button, onClick = onCancel),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.cancel), color = Color(NgTheme.colors.onSurfaceVariant), fontSize = 13.sp)
                }
            }
            value.isEmpty() -> {
                Box(
                    modifier = Modifier.height(48.dp).widthIn(min = 44.dp).clickable(role = Role.Button, onClick = onBind)
                        .semantics { contentDescription = accessibilityTitle },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = displayValue,
                        modifier = Modifier.clip(shape).background(tint)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        color = primary,
                        fontSize = 13.sp,
                        maxLines = 1,
                    )
                }
            }
            else -> {
                val clearDescription = stringResource(R.string.page_key_clear_description, accessibilityTitle)
                Box {
                    Box(
                        Modifier.matchParentSize()
                            .padding(
                                start = (44.dp - displayValueWidth - 8.dp).coerceAtLeast(0.dp),
                                end = 22.dp,
                                top = 10.dp,
                                bottom = 10.dp,
                            )
                            .clip(shape).background(tint),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.height(48.dp).widthIn(min = 44.dp)
                                .clickable(role = Role.Button, onClick = onBind)
                                .semantics { contentDescription = accessibilityTitle }
                                .padding(start = 8.dp),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            Text(
                                text = displayValue,
                                color = primary,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Box(
                            modifier = Modifier.size(48.dp)
                                .clickable(role = Role.Button, onClick = onClear),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = clearDescription,
                                modifier = Modifier.padding(start = 4.dp).size(16.dp),
                                tint = primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun pageKeyLabel(value: String): String {
    if (!isValidPageKey(value)) return stringResource(R.string.page_key_rebind)
    val code = value.toInt()
    val label = when (code) {
        KeyEvent.KEYCODE_DPAD_LEFT -> R.string.page_key_direction_left
        KeyEvent.KEYCODE_DPAD_RIGHT -> R.string.page_key_direction_right
        KeyEvent.KEYCODE_DPAD_UP -> R.string.page_key_direction_up
        KeyEvent.KEYCODE_DPAD_DOWN -> R.string.page_key_direction_down
        KeyEvent.KEYCODE_DPAD_CENTER -> R.string.page_key_direction_center
        KeyEvent.KEYCODE_VOLUME_UP -> R.string.page_key_volume_up
        KeyEvent.KEYCODE_VOLUME_DOWN -> R.string.page_key_volume_down
        KeyEvent.KEYCODE_PAGE_UP -> R.string.page_key_page_up
        KeyEvent.KEYCODE_PAGE_DOWN -> R.string.page_key_page_down
        KeyEvent.KEYCODE_ENTER -> R.string.page_key_enter
        KeyEvent.KEYCODE_SPACE -> R.string.page_key_space
        else -> null
    }
    return label?.let { stringResource(it) } ?: stringResource(R.string.page_key_code, code)
}
