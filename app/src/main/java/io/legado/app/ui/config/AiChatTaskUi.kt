package io.legado.app.ui.config

import android.text.format.DateFormat
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.NgDialogTextActionButton
import io.legado.app.ui.design.components.compose.NgMaterialRole
import io.legado.app.ui.design.components.compose.NgVisualSurface
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.widget.dialog.applyNgWindow
import java.util.Date

internal val AiChatTaskRowMinHeight = 52.dp

@Composable
internal fun AiChatTaskRow(
    task: AiChatTask,
    onClick: () -> Unit,
    showStartTime: Boolean = false,
) {
    val context = LocalContext.current
    val startTime = remember(showStartTime, task.createdAt, context) {
        task.createdAt?.takeIf { showStartTime }?.let { DateFormat.getTimeFormat(context).format(Date(it)) }
    }
    val title = if (task.workKey != null) stringResource(R.string.ai_chat_task_character_card, task.title) else task.title
    val state = task.replyPreview ?: task.actionTitle ?: stringResource(task.state.label)
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .heightIn(min = AiChatTaskRowMinHeight).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (task.state.isTerminal) {
            val completed = task.state == AiChatTaskState.COMPLETED
            Icon(
                imageVector = if (completed) Icons.Rounded.CheckCircle else Icons.Rounded.Error,
                contentDescription = stringResource(task.state.label),
                tint = if (completed) colorResource(R.color.success) else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(17.dp),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = listOfNotNull(state, startTime).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = if (task.state.needsAction) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
    }
}

@Composable
internal fun AiChatTaskList(
    tasks: List<AiChatTask>,
    modifier: Modifier = Modifier,
    glassBackdrop: View? = null,
    onOpenTask: (String) -> Unit,
) {
    val repeatedTitles = remember(tasks) {
        tasks.groupingBy { it.title }.eachCount().filterValues { it > 1 }.keys
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (glassBackdrop == null) 0.dp else 6.dp),
    ) {
        items(tasks, key = { it.sessionId }) { task ->
            val open = { onOpenTask(task.sessionId) }
            val showTime = task.title in repeatedTitles
            if (glassBackdrop == null) {
                AiChatTaskRow(task, onClick = open, showStartTime = showTime)
            } else {
                NgVisualSurface(
                    role = NgMaterialRole.OVERLAY,
                    cornerRadius = 18.dp,
                    viewBackdropSource = glassBackdrop,
                ) {
                    AiChatTaskRow(task, onClick = open, showStartTime = showTime)
                }
            }
        }
    }
}

internal fun ComponentActivity.showAiChatTaskPicker(
    tasks: List<AiChatTask>,
    onOpenTask: (String) -> Unit,
    onNewChat: (() -> Unit)? = null,
) {
    val offeredIds = tasks.map { it.sessionId }
    val dialog = ComponentDialog(this)
    val observer = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_DESTROY) dialog.dismiss()
    }
    lifecycle.addObserver(observer)
    dialog.setOnDismissListener { lifecycle.removeObserver(observer) }
    dialog.setContentView(ComposeView(this).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent {
            NgAppTheme(updateSystemBars = false) {
                val current by aiChatSessions.tasks.collectAsStateWithLifecycle()
                val offered = remember(current) {
                    val byId = current.associateBy { it.sessionId }
                    offeredIds.mapNotNull(byId::get)
                }
                LaunchedEffect(offered.isEmpty()) { if (offered.isEmpty()) dialog.dismiss() }
                NgDialog(
                    title = stringResource(R.string.ai_chat_tasks),
                    titleFontWeight = FontWeight.Normal,
                    actions = {
                        NgDialogTextActionButton(stringResource(R.string.cancel), onClick = dialog::dismiss)
                        if (onNewChat != null) {
                            NgDialogTextActionButton(stringResource(R.string.ai_chat_task_new_chat), onClick = {
                                dialog.dismiss()
                                onNewChat()
                            })
                        }
                    },
                ) {
                    AiChatTaskList(offered, Modifier.heightIn(max = 300.dp)) { id ->
                        dialog.dismiss()
                        onOpenTask(id)
                    }
                }
            }
        }
    })
    dialog.show()
    dialog.applyNgWindow()
}
