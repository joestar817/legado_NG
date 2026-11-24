package io.legado.app.ui.main.chatentry

import android.graphics.Rect
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.isVisible
import io.legado.app.R
import io.legado.app.ui.config.AiChatTask
import io.legado.app.ui.config.AiChatTaskList
import io.legado.app.ui.config.AiChatTaskRowMinHeight
import io.legado.app.ui.design.components.compose.NgMaterialRole
import io.legado.app.ui.design.components.compose.NgVisualSurface
import io.legado.app.ui.design.theme.NgAppTheme
import kotlin.math.roundToInt

private val PetToolbarTouchSize = 48.dp
private val PetToolbarGlassHeight = 36.dp
private val PetToolbarIconSize = 22.dp

/** The toolbar stays at the pet; the independently measured message list opens outward. */
internal class AiChatPetTaskWindow(
    private val toolbar: ComposeView,
    private val messages: ComposeView,
    backdrop: View,
    private val onMenuChanged: (Boolean) -> Unit,
    onOpenTask: (String) -> Unit,
    onOpenAssistant: () -> Unit,
) {
    private val density = toolbar.resources.displayMetrics.density
    private val gap = (8 * density).roundToInt()
    private val taskRowHeight = (AiChatTaskRowMinHeight.value * density).roundToInt()
    private val petBounds = Rect()
    private val parkedAnchor = Rect()
    private val menuAnchor = Rect()
    private val viewport = Rect()
    private var tasks by mutableStateOf<List<AiChatTask>>(emptyList())
    private val runningTasks by derivedStateOf { tasks.filter { it.state.isRunning } }
    private var messagesWidth by mutableIntStateOf(0)
    private var messagesHeightLimit by mutableIntStateOf(0)
    private var messagesExpanded by mutableStateOf(false)
    private var petParked by mutableStateOf(false)
    var isOpen by mutableStateOf(false)
        private set
    private val showAutomaticMessages get() = !isOpen && petParked && runningTasks.isNotEmpty()

    init {
        toolbar.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        toolbar.setContent {
            NgAppTheme(updateSystemBars = false) {
                val iconTint = MaterialTheme.colorScheme.primary
                Box {
                    // Center the glass inside the original touch bounds.
                    NgVisualSurface(
                        modifier = Modifier.matchParentSize()
                            .padding(vertical = (PetToolbarTouchSize - PetToolbarGlassHeight) / 2),
                        role = NgMaterialRole.ACTION,
                        cornerRadius = PetToolbarGlassHeight / 2,
                        viewBackdropSource = backdrop,
                    ) {}
                    Row(Modifier.padding(horizontal = 4.dp)) {
                        IconButton(
                            modifier = Modifier.size(PetToolbarTouchSize),
                            onClick = onOpenAssistant,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_ai_chat_suggestion),
                                contentDescription = stringResource(R.string.ai_pet_open_assistant),
                                modifier = Modifier.size(PetToolbarIconSize),
                                tint = iconTint,
                            )
                        }
                        IconButton(
                            modifier = Modifier.size(PetToolbarTouchSize),
                            onClick = {
                                messagesExpanded = !messagesExpanded
                                place()
                            },
                        ) {
                            val pendingCount = tasks.count { it.state.showsBadge }
                            BadgedBox(badge = {
                                if (!messagesExpanded && pendingCount > 0) {
                                    Badge { Text(if (pendingCount > 99) "99+" else pendingCount.toString()) }
                                }
                            }) {
                                Icon(
                                    painter = if (messagesExpanded) {
                                        rememberVectorPainter(Icons.Rounded.KeyboardArrowUp)
                                    } else {
                                        painterResource(R.drawable.ic_ai_pet_message_bell)
                                    },
                                    contentDescription = stringResource(
                                        if (messagesExpanded) R.string.ai_chat_task_collapse else R.string.ai_chat_task_expand
                                    ),
                                    modifier = Modifier.size(PetToolbarIconSize),
                                    tint = iconTint,
                                )
                            }
                        }
                    }
                }
            }
        }
        messages.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        messages.setContent {
            val automatic = showAutomaticMessages
            if ((!automatic && !messagesExpanded) || messagesWidth <= 0 || messagesHeightLimit <= 0) return@setContent
            val size = Modifier.width((messagesWidth / density).dp)
                .heightIn(max = (messagesHeightLimit / density).dp)
            NgAppTheme(updateSystemBars = false) {
                if (automatic || tasks.isEmpty()) {
                    NgVisualSurface(
                        role = NgMaterialRole.OVERLAY,
                        cornerRadius = 18.dp,
                        viewBackdropSource = backdrop,
                        modifier = size,
                    ) {
                        if (automatic) {
                            AiChatTaskList(runningTasks, onOpenTask = onOpenTask)
                        } else {
                            Text(
                                stringResource(R.string.ai_pet_no_messages),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                } else {
                    AiChatTaskList(tasks, size, backdrop, onOpenTask)
                }
            }
        }
        toolbar.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> place() }
        messages.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> place() }
    }

    fun showTasks(items: List<AiChatTask>) {
        tasks = items
        place()
    }

    fun moveTo(bounds: Rect, safeBounds: Rect, isParked: Boolean) {
        val viewportChanged = viewport != safeBounds
        val wasParked = petParked
        petBounds.set(bounds)
        viewport.set(safeBounds)
        petParked = isParked && !bounds.isEmpty
        if (petParked && (!wasParked || viewportChanged)) {
            // Keep the window still through breathing, blinking and other in-place animation.
            parkedAnchor.set(bounds)
        }
        if ((bounds.isEmpty || viewportChanged) && dismiss()) return
        if (!isOpen && (wasParked != petParked || viewportChanged)) place()
    }

    fun toggle() {
        if (dismiss() || petBounds.isEmpty || viewport.isEmpty) return
        menuAnchor.set(petBounds)
        isOpen = true
        toolbar.isVisible = true
        onMenuChanged(true)
        place()
    }

    fun dismiss(): Boolean {
        if (!isOpen) return false
        isOpen = false
        messagesExpanded = false
        toolbar.isVisible = false
        onMenuChanged(false)
        place()
        return true
    }

    private fun place() {
        val automatic = showAutomaticMessages
        val anchor = if (isOpen) menuAnchor else parkedAnchor
        if (anchor.isEmpty || viewport.isEmpty ||
            (!isOpen && !automatic) || (isOpen && (toolbar.width == 0 || toolbar.height == 0))) {
            messages.isVisible = false
            return
        }
        val left = viewport.left + gap
        val right = viewport.right - gap
        val top = viewport.top + gap
        val bottom = viewport.bottom - gap
        val aboveSpace = anchor.top - top
        val above = aboveSpace >= bottom - anchor.bottom || (automatic && aboveSpace >= taskRowHeight + gap)
        val centerX = anchor.centerX()
        val listEdge = if (isOpen) {
            val menuTop = (if (above) anchor.top - gap - toolbar.height else anchor.bottom + gap)
                .coerceIn(top, (bottom - toolbar.height).coerceAtLeast(top))
            toolbar.x = (centerX - toolbar.width / 2).coerceIn(left, (right - toolbar.width).coerceAtLeast(left)).toFloat()
            toolbar.y = menuTop.toFloat()
            if (above) menuTop - gap else menuTop + toolbar.height + gap
        } else if (above) anchor.top - gap else anchor.bottom + gap

        messagesWidth = minOf((254 * density).roundToInt(), right - left).coerceAtLeast(0)
        messagesHeightLimit = minOf((300 * density).roundToInt(), if (above) listEdge - top else bottom - listEdge)
            .coerceAtLeast(0)
        messages.isVisible = (automatic || messagesExpanded) && messagesWidth > 0 && messagesHeightLimit > 0
        if (messages.isVisible) {
            messages.x = (centerX - messagesWidth / 2).coerceIn(left, right - messagesWidth).toFloat()
            messages.y = (if (above) (listEdge - messages.height).coerceAtLeast(top) else listEdge).toFloat()
        }
    }
}
