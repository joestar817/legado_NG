package io.legado.app.ui.config

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.help.ai.AiConfig
import io.legado.app.help.ai.AiReasoningLevel
import io.legado.app.ui.design.components.compose.NgBottomDrawerSurface
import io.legado.app.ui.design.components.compose.NgDrawerContentCardStyle
import io.legado.app.ui.design.theme.NgTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AiChatReasoningSheet(onChanged: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val levels = AiReasoningLevel.entries
    val labels = levels.map { it.displayName(context) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = null,
        containerColor = Color.Transparent,
        contentColor = Color(NgTheme.colors.onSurface),
        shape = RectangleShape,
    ) {
        NgBottomDrawerSurface(
            modifier = Modifier.fillMaxWidth(),
            contentCardStyle = NgDrawerContentCardStyle.ADAPTIVE,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AiChatSheetHandle()
                AiDiscreteScaleDialogContent(
                    title = context.getString(R.string.ai_assistant_reasoning_title),
                    description = null,
                    iconRes = R.drawable.ic_ai_capability_reasoning,
                    labels = labels,
                    currentLabels = labels,
                    initialSelectedIndex = levels.indexOf(AiConfig.assistantReasoningLevel)
                        .coerceAtLeast(0),
                    tintIcon = { index -> levels[index] != AiReasoningLevel.OFF },
                    onSelectedIndexChanged = { index ->
                        AiConfig.assistantReasoningLevel = levels[index]
                        onChanged()
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AiChatInternalMcpSheet(onChanged: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(AiConfig.internalMcpEnabled) }
    fun setEnabled(value: Boolean) {
        if (value == enabled) return
        enabled = value
        AiConfig.internalMcpEnabled = value
        onChanged()
    }
    val summary = context.getString(
        if (enabled) R.string.ai_internal_mcp_summary_on else R.string.ai_internal_mcp_summary_off
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = null,
        containerColor = Color.Transparent,
        contentColor = Color(NgTheme.colors.onSurface),
        shape = RectangleShape,
    ) {
        NgBottomDrawerSurface(
            modifier = Modifier.fillMaxWidth(),
            contentCardStyle = NgDrawerContentCardStyle.ADAPTIVE,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 24.dp, top = 10.dp, end = 24.dp, bottom = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AiChatSheetHandle()
                Text(
                    text = context.getString(R.string.ai_internal_mcp),
                    color = Color(NgTheme.colors.onSurface),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = summary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 22.dp),
                    color = Color(NgTheme.colors.onSurfaceVariant),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(NgTheme.colors.cardContainer))
                        .clickable { setEnabled(!enabled) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(NgTheme.colors.primary).copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_ai_capability_tool),
                            contentDescription = null,
                            tint = Color(NgTheme.colors.primary),
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = context.getString(R.string.ai_internal_mcp),
                            color = Color(NgTheme.colors.onSurface),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = summary,
                            color = Color(NgTheme.colors.onSurfaceVariant),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(checked = enabled, onCheckedChange = ::setEnabled)
                }
            }
        }
    }
}

@Composable
private fun AiChatSheetHandle() {
    Box(
        modifier = Modifier
            .padding(bottom = 26.dp)
            .size(width = 40.dp, height = 4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Color(NgTheme.colors.onSurfaceVariant).copy(alpha = 0.65f)),
    )
}
