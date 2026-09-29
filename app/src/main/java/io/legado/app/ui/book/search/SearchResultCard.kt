package io.legado.app.ui.book.search

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.TextPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowOverflow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import io.legado.app.R
import io.legado.app.constant.AppPattern
import io.legado.app.data.entities.SearchBook
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.model.BookCover
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.utils.textHeight
import io.legado.app.utils.toStringArray

private val SearchCardTextStyle: TextStyle
    @Composable
    get() = TextStyle(
        fontFamily = NgTheme.fontFamily,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        textAlign = TextAlign.Start
    )

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SearchResultCard(
    book: SearchBook,
    inBookshelf: Boolean,
    originCount: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    outerHorizontalPadding: Dp = 12.dp,
    outerVerticalPadding: Dp = 4.dp,
    cardCornerRadius: Dp = 18.dp,
    minCardHeight: Dp = 120.dp,
    cardContentPadding: Dp = 10.dp,
    coverWidth: Dp = 78.dp,
    coverHeight: Dp = 116.5.dp,
    cardBackgroundColorRes: Int = R.color.ng_surface_card,
    cardStrokeColorRes: Int = R.color.ng_settings_item_stroke,
    cardBorderWidth: Dp = 0.8.dp
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val shape = RoundedCornerShape(cardCornerRadius)
    val cardColor = colorResource(cardBackgroundColorRes)
    val pressedOverlay = colorResource(R.color.ng_surface_pressed)
    val strokeColor = colorResource(
        if (isPressed) R.color.ng_card_stroke else cardStrokeColorRes
    )
    val primaryText = colorResource(R.color.primaryText)
    val secondaryText = colorResource(R.color.secondaryText)
    val accent = Color(NgTheme.colors.primary)
    val onAccent = Color.White
    val textStyle = SearchCardTextStyle

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = outerHorizontalPadding, vertical = outerVerticalPadding)
            .heightIn(min = minCardHeight)
            .clip(shape)
            .background(cardColor)
            .then(if (isPressed) Modifier.background(pressedOverlay) else Modifier)
            .then(
                if (cardBorderWidth > 0.dp) Modifier.border(cardBorderWidth, strokeColor, shape)
                else Modifier
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(cardContentPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SearchBookCover(
            book = book,
            modifier = Modifier.size(width = coverWidth, height = coverHeight),
            coverWidth = coverWidth,
            coverHeight = coverHeight
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = book.name,
                    modifier = Modifier.weight(1f),
                    color = primaryText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 19.sp,
                    style = textStyle
                )
                if (inBookshelf) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(colorResource(R.color.md_green_600), CircleShape)
                    )
                }
                if (originCount > 0) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = originCount.toString(),
                        modifier = Modifier
                            .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
                            .background(accent, RoundedCornerShape(8.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                        color = onAccent,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        maxLines = 1,
                        style = textStyle
                    )
                }
            }

            SearchBookMetadata(
                icon = R.drawable.ic_author,
                text = book.author,
                description = context.getString(R.string.author_show, book.author),
                color = secondaryText,
            )
            SearchBookMetadata(
                icon = R.drawable.ic_web_outline,
                text = book.originName,
                description = context.getString(R.string.origin_show, book.originName),
                color = secondaryText,
            )
            val kinds = book.getKindList()
            if (kinds.isNotEmpty()) {
                SearchKindLabels(kinds, accent, onAccent)
            }
            book.latestChapterTitle?.takeIf { it.isNotEmpty() }?.let { latest ->
                SearchBookMetadata(
                    icon = R.drawable.ic_book_last,
                    text = latest,
                    description = context.getString(R.string.lasted_show, latest),
                    color = secondaryText,
                )
            }
            Text(
                text = book.trimIntro(context),
                color = secondaryText,
                fontSize = 12.sp,
                minLines = 1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp,
                style = textStyle
            )
        }
    }
}

@Composable
private fun SearchBookMetadata(
    icon: Int,
    text: String,
    description: String,
    color: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            color = color,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = SearchCardTextStyle
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchKindLabels(
    labels: List<String>,
    accent: Color,
    onAccent: Color
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        maxLines = 1,
        overflow = FlowRowOverflow.Clip
    ) {
        labels.forEach { label ->
            Box(
                modifier = Modifier
                    .heightIn(min = 18.dp)
                    .defaultMinSize(minWidth = 28.dp)
                    .background(accent, RoundedCornerShape(2.dp))
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = onAccent,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = SearchCardTextStyle
                )
            }
        }
    }
}

@Composable
internal fun SearchBookCover(
    book: SearchBook,
    modifier: Modifier = Modifier,
    coverWidth: Dp = 78.dp,
    coverHeight: Dp = 104.dp
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val widthPx = with(density) { coverWidth.roundToPx() }
    val heightPx = with(density) { coverHeight.roundToPx() }
    val coverRadius = with(density) { 12.toDp() }
    val cleanName = remember(book.name) {
        book.name.replace(AppPattern.bdRegex, "").trim()
    }
    val cleanAuthor = remember(book.author) {
        book.author.replace(AppPattern.bdRegex, "").trim()
    }
    val defaultSeed = remember(cleanName, cleanAuthor) {
        listOf(cleanName, cleanAuthor).filter { it.isNotEmpty() }.joinToString("\u0000")
            .ifEmpty { book.coverUrl }
    }
    val defaultBitmap = remember(defaultSeed, widthPx, heightPx, AppConfig.isNightTheme) {
        BookCover.getDefaultDrawable(defaultSeed).toBitmap(widthPx, heightPx).asImageBitmap()
    }
    var cover by remember(book.coverUrl, defaultSeed) { mutableStateOf(defaultBitmap) }
    var showNameOverlay by remember(book.coverUrl, defaultSeed) {
        mutableStateOf(BookCover.drawBookName)
    }

    DisposableEffect(
        context,
        book.coverUrl,
        book.origin,
        defaultSeed,
        widthPx,
        heightPx,
        AppConfig.loadCoverOnlyWifi,
        AppConfig.useDefaultCover
    ) {
        cover = defaultBitmap
        showNameOverlay = BookCover.drawBookName
        if (AppConfig.useDefaultCover) {
            onDispose { }
        } else {
            val target = object : CustomTarget<Drawable>(widthPx, heightPx) {
                override fun onResourceReady(
                    resource: Drawable,
                    transition: Transition<in Drawable>?
                ) {
                    cover = resource.toBitmap(widthPx, heightPx).asImageBitmap()
                    showNameOverlay = false
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    cover = defaultBitmap
                    showNameOverlay = BookCover.drawBookName
                }

                override fun onLoadCleared(placeholder: Drawable?) = Unit
            }
            BookCover.load(
                context = context,
                path = book.coverUrl,
                loadOnlyWifi = AppConfig.loadCoverOnlyWifi,
                sourceOrigin = book.origin
            ).override(widthPx, heightPx).into(target)
            onDispose { Glide.with(context).clear(target) }
        }
    }

    Box(modifier.clip(RoundedCornerShape(coverRadius))) {
        Image(
            bitmap = cover,
            contentDescription = context.getString(R.string.img_cover),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        if (showNameOverlay && cleanName.isNotEmpty()) {
            CoverNameOverlay(cleanName, cleanAuthor)
        }
    }
}

@Composable
private fun CoverNameOverlay(name: String, author: String) {
    val context = LocalContext.current
    val background = remember(context) { context.backgroundColor }
    val accent = remember(context) { context.accentColor }
    val fontFamily = NgTheme.fontFamily
    val fontFamilyResolver = LocalFontFamilyResolver.current
    val nameTypeface by fontFamilyResolver.resolve(fontFamily, FontWeight.Bold)
    val authorTypeface by fontFamilyResolver.resolve(fontFamily, FontWeight.Normal)
    Canvas(Modifier.fillMaxSize()) {
        drawIntoCanvas { canvas ->
            val nativeCanvas = canvas.nativeCanvas
            var startX = size.width * 0.2f
            var startY = size.height * 0.2f
            val namePaint = TextPaint().apply {
                typeface = nameTypeface as Typeface
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                textSize = size.width / 7f
                strokeWidth = textSize / 6f
            }
            name.toStringArray().let { chars ->
                var line = 0
                chars.forEachIndexed { index, char ->
                    namePaint.color = background
                    namePaint.style = Paint.Style.STROKE
                    nativeCanvas.drawText(char, startX, startY, namePaint)
                    namePaint.color = accent
                    namePaint.style = Paint.Style.FILL
                    nativeCanvas.drawText(char, startX, startY, namePaint)
                    startY += namePaint.textHeight
                    if (startY > size.height * 0.9f) {
                        if (chars.size - index - 1 == 1) {
                            startY -= namePaint.textHeight / 5f
                            namePaint.textSize = size.width / 9f
                        } else {
                            startX += namePaint.textSize
                            line++
                            namePaint.textSize = size.width / 10f
                            startY = size.height * 0.2f + namePaint.textHeight * line
                        }
                    } else if (startY > size.height * 0.8f && chars.size - index - 1 > 2) {
                        startX += namePaint.textSize
                        line++
                        namePaint.textSize = size.width / 10f
                        startY = size.height * 0.2f + namePaint.textHeight * line
                    }
                }
            }
            if (BookCover.drawBookAuthor && author.isNotEmpty()) {
                val authorPaint = TextPaint(namePaint).apply {
                    typeface = authorTypeface as Typeface
                    textSize = size.width / 10f
                    strokeWidth = textSize / 5f
                }
                val chars = author.toStringArray()
                startX = size.width * 0.8f
                startY = (size.height * 0.95f - chars.size * authorPaint.textHeight)
                    .coerceAtLeast(size.height * 0.3f)
                chars.forEach { char ->
                    if (startY > size.height * 0.95f) return@forEach
                    authorPaint.color = background
                    authorPaint.style = Paint.Style.STROKE
                    nativeCanvas.drawText(char, startX, startY, authorPaint)
                    authorPaint.color = accent
                    authorPaint.style = Paint.Style.FILL
                    nativeCanvas.drawText(char, startX, startY, authorPaint)
                    startY += authorPaint.textHeight
                }
            }
        }
    }
}
