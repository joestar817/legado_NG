package io.legado.app.ui.config

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.theme.NgTheme

@Composable
internal fun AiToolWriteConfirmationContent(
    summaries: List<WriteOperationSummary>,
    destructive: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    NgDialog(
        title = if (destructive) "确认高风险操作" else "确认写操作",
        variant = NgDialogVariant.LONG_CONTENT,
        titleFontSize = 24.sp,
        actions = {
            AiToolActionButton("取消", primary = false, onClick = onCancel)
            AiToolActionButton("执行", primary = true, onClick = onConfirm)
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.43f).dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (destructive) {
                    "AI 请求执行删除、清空或回滚操作。请确认对象和影响范围后再执行。"
                } else {
                    "AI 请求执行以下写操作，确认后会写入或修改本地数据。"
                },
                modifier = Modifier.padding(bottom = 2.dp),
                color = Color(NgTheme.colors.onSurfaceVariant),
                fontSize = 15.sp,
                lineHeight = 21.sp,
            )
            summaries.forEachIndexed { index, summary ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(NgTheme.shapes.mediumDp.dp),
                    color = Color(NgTheme.colors.surfaceContainer),
                ) {
                    Column(
                        modifier = Modifier.padding(
                            start = 10.dp,
                            top = 8.dp,
                            end = 10.dp,
                            bottom = 8.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "${index + 1}. ${summary.title}",
                            color = Color(NgTheme.colors.onSurface),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = summary.description,
                            color = Color(NgTheme.colors.onSurfaceVariant),
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AiToolActionButton(text: String, primary: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(76.dp)
            .height(36.dp),
        shape = RoundedCornerShape(NgTheme.shapes.mediumDp.dp),
        color = Color(if (primary) NgTheme.colors.primary else NgTheme.colors.surface),
        border = if (primary) null else BorderStroke(1.dp, Color(NgTheme.colors.primary)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(PaddingValues(vertical = 7.dp)),
            color = Color(if (primary) NgTheme.colors.onPrimary else NgTheme.colors.primary),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}
