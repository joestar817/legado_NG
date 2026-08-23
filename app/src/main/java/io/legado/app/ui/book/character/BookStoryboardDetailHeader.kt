package io.legado.app.ui.book.character

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.annotation.DrawableRes
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.design.theme.NgTheme

@Immutable
internal data class StoryboardDetailSummaryUi(
    val sceneCount: Int,
    val segmentCount: Int,
    val dialogueCount: Int,
    val personCount: Int,
)

@Composable
internal fun BookStoryboardDetailHeader(
    chapterTitle: String,
    summary: StoryboardDetailSummaryUi?,
    refreshEnabled: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                color = colorResource(R.color.ng_surface_card),
                shape = RoundedCornerShape(NgTheme.shapes.smallDp.dp),
                tonalElevation = 0.dp,
                shadowElevation = 1.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .width(34.dp)
                            .height(36.dp)
                            .clickable(role = Role.Button, onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_chevron_left_search),
                            contentDescription = stringResource(R.string.back),
                            tint = Color(NgTheme.colors.onTopBar),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Text(
                        text = chapterTitle,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .weight(1f),
                        color = Color(NgTheme.colors.onTopBar),
                        fontSize = 17.sp,
                        lineHeight = 21.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(
                                enabled = refreshEnabled,
                                role = Role.Button,
                                onClick = onRefresh,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_refresh_black_24dp),
                            contentDescription = stringResource(R.string.book_storyboard_regenerate),
                            tint = Color(NgTheme.colors.onSurface).copy(
                                alpha = if (refreshEnabled) 1f else 0.38f,
                            ),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }

        summary?.let { detail ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = 14.dp),
                color = colorResource(R.color.ng_surface_card),
                shape = RoundedCornerShape(NgTheme.shapes.smallDp.dp),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StoryboardSummaryMetric(
                        modifier = Modifier,
                        iconRes = R.drawable.ic_storyboard_summary_movie,
                        text = "${detail.sceneCount} 个场景 · ${detail.segmentCount} 个片段",
                        fontSize = 13.sp,
                    )
                    Spacer(
                        modifier = Modifier
                            .width(6.dp)
                            .weight(1f),
                    )
                    StoryboardSummaryMetric(
                        modifier = Modifier,
                        iconRes = R.drawable.ic_ai_chat_suggestion,
                        text = "${detail.dialogueCount} 段对白 · ${detail.personCount} 个角色",
                        fontSize = 13.sp,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun StoryboardSummaryMetric(
    modifier: Modifier,
    @DrawableRes iconRes: Int,
    text: String,
    fontSize: TextUnit,
) {
    val primary = Color(NgTheme.colors.onSurface)
    val secondary = Color(NgTheme.colors.onSurfaceVariant)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = secondary,
            modifier = Modifier.size(15.dp),
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            text = buildAnnotatedString {
                var cursor = 0
                METRIC_VALUE_REGEX.findAll(text).forEach { match ->
                    if (match.range.first > cursor) {
                        withStyle(SpanStyle(color = secondary)) {
                            append(text.substring(cursor, match.range.first))
                        }
                    }
                    withStyle(
                        SpanStyle(
                            color = primary,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    ) {
                        append(match.value)
                    }
                    cursor = match.range.last + 1
                }
                if (cursor < text.length) {
                    withStyle(SpanStyle(color = secondary)) {
                        append(text.substring(cursor))
                    }
                }
            },
            fontSize = fontSize,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val METRIC_VALUE_REGEX = Regex("""\d+|—""")
