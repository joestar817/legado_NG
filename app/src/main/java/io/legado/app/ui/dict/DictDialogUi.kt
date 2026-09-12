package io.legado.app.ui.dict

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.data.entities.DictRule
import io.legado.app.help.config.NgThemeRuntimeAssets
import io.legado.app.ui.book.read.config.ReadConfigDialogSurface
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.widget.text.ScrollTextView

@Composable
internal fun DictDialogContent(
    word: String,
    rules: List<DictRule>,
    selectedIndex: Int,
    loading: Boolean,
    onRuleSelected: (Int) -> Unit,
    onTextViewReady: (ScrollTextView) -> Unit,
) {
    val colors = NgTheme.colors
    val maxContentHeight = (LocalConfiguration.current.screenHeightDp * 0.46f).dp
    ReadConfigDialogSurface(
        contentPadding = PaddingValues(
            start = 18.dp,
            top = 18.dp,
            end = 18.dp,
            bottom = 14.dp,
        ),
    ) {
        Text(
            text = word,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            color = Color(colors.onSurface),
            style = TextStyle(
                fontFamily = NgTheme.fontFamily,
                fontSize = 22.sp,
                lineHeight = 27.sp,
                fontWeight = FontWeight.Bold,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 10.dp)
                .selectableGroup(),
            contentPadding = PaddingValues(horizontal = 2.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(
                items = rules,
                key = { index, rule -> "$index:${rule.name}" },
            ) { index, rule ->
                val selected = index == selectedIndex
                Text(
                    text = rule.name,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .then(
                            if (selected) {
                                Modifier.background(Color(colors.selectedContainer))
                            } else {
                                Modifier
                            }
                        )
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            onClick = { onRuleSelected(index) },
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    color = Color(
                        if (selected) colors.primary else colors.onSurface
                    ),
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 84.dp, max = maxContentHeight)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(colors.surfaceContainerLow).copy(alpha = 0.46f))
                .border(
                    width = 0.75.dp,
                    color = Color(colors.outline).copy(alpha = 0.42f),
                    shape = RoundedCornerShape(14.dp),
                ),
        ) {
            AndroidView(
                factory = { context ->
                    ScrollTextView(context, null).apply {
                        NgThemeRuntimeAssets.applyAppTypeface(context, this)
                        val density = resources.displayMetrics.density
                        setPadding(
                            (14 * density).toInt(),
                            (12 * density).toInt(),
                            (14 * density).toInt(),
                            (12 * density).toInt(),
                        )
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        includeFontPadding = false
                        setLineSpacing(0f, 1.08f)
                        setTextColor(colors.onSurface)
                        textSize = 15f
                        onTextViewReady(this)
                    }
                },
                update = {
                    it.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    it.setTextColor(colors.onSurface)
                    onTextViewReady(it)
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(32.dp),
                    color = Color(colors.primary),
                    trackColor = Color(colors.outline).copy(alpha = 0.4f),
                )
            }
        }
    }
}
