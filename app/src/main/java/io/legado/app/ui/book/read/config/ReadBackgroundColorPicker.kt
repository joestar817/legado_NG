package io.legado.app.ui.book.read.config

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import io.legado.app.R
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.ui.book.read.ReadDrawerStyle
import io.legado.app.ui.book.read.readFloatingGlassStyle
import io.legado.app.ui.design.components.compose.NgGlassStyle
import io.legado.app.ui.design.components.compose.NgGlassSurface
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgColorMath
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.utils.getMeanColor
import kotlin.math.roundToInt

internal data class ReadBackgroundColorResult(
    val color: Int,
)

internal object ReadBackgroundColorSampler {

    fun samplePatch(
        width: Int,
        height: Int,
        centerX: Int,
        centerY: Int,
        radius: Int = 4,
        pixelAt: (x: Int, y: Int) -> Int,
    ): Int {
        if (width <= 0 || height <= 0) return 0xFF000000.toInt()
        val left = (centerX - radius).coerceIn(0, width - 1)
        val right = (centerX + radius).coerceIn(0, width - 1)
        val top = (centerY - radius).coerceIn(0, height - 1)
        val bottom = (centerY + radius).coerceIn(0, height - 1)
        var red = 0L
        var green = 0L
        var blue = 0L
        var count = 0
        for (y in top..bottom) {
            for (x in left..right) {
                val color = pixelAt(x, y)
                red += (color ushr 16) and 0xFF
                green += (color ushr 8) and 0xFF
                blue += color and 0xFF
                count++
            }
        }
        return 0xFF000000.toInt() or
            ((red / count).toInt() shl 16) or
            ((green / count).toInt() shl 8) or
            (blue / count).toInt()
    }
}

internal fun renderCurrentReadBackground(width: Int, height: Int): Bitmap {
    val safeWidth = width.coerceAtLeast(1)
    val safeHeight = height.coerceAtLeast(1)
    val drawable = ReadBookConfig.durConfig.curBgDrawable(safeWidth, safeHeight)
    val meanColor = when (drawable) {
        is BitmapDrawable -> drawable.bitmap?.getMeanColor() ?: ReadBookConfig.bgMeanColor
        is ColorDrawable -> drawable.color
        else -> ReadBookConfig.bgMeanColor
    }
    return Bitmap.createBitmap(safeWidth, safeHeight, Bitmap.Config.ARGB_8888).also { target ->
        val canvas = Canvas(target)
        canvas.drawColor(meanColor)
        drawable.alpha = (ReadBookConfig.bgAlpha.coerceIn(0, 100) / 100f * 255).roundToInt()
        drawable.setBounds(0, 0, safeWidth, safeHeight)
        drawable.draw(canvas)
        if (drawable is BitmapDrawable && drawable.bitmap !== target) {
            drawable.bitmap?.let { source ->
                if (!source.isRecycled) source.recycle()
            }
        }
    }
}

internal fun showReadBackgroundColorPicker(
    context: Context,
    background: Bitmap,
    onPicked: (ReadBackgroundColorResult) -> Unit,
): ComponentDialog {
    val dialog = ComponentDialog(context)
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
    dialog.setCanceledOnTouchOutside(false)
    val contentView = ComposeView(context).apply {
        setBackgroundColor(AndroidColor.TRANSPARENT)
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent {
            NgAppTheme(
                snapshot = ReadDrawerStyle.themeSnapshot(context),
                updateSystemBars = false,
            ) {
                ReadBackgroundColorPickerScreen(
                    background = background,
                    onCancel = dialog::dismiss,
                    onPicked = {
                        onPicked(it)
                        dialog.dismiss()
                    },
                )
            }
        }
    }
    dialog.setContentView(contentView)
    dialog.setOnDismissListener {
        Handler(Looper.getMainLooper()).post {
            if (!background.isRecycled) background.recycle()
        }
    }
    dialog.show()
    dialog.window?.let { window ->
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        window.statusBarColor = AndroidColor.TRANSPARENT
        window.navigationBarColor = AndroidColor.TRANSPARENT
        val topColor = ReadBackgroundColorSampler.samplePatch(
            width = background.width,
            height = background.height,
            centerX = background.width / 2,
            centerY = (background.height * 0.04f).roundToInt(),
            pixelAt = background::getPixel,
        )
        val bottomColor = ReadBackgroundColorSampler.samplePatch(
            width = background.width,
            height = background.height,
            centerX = background.width / 2,
            centerY = (background.height * 0.96f).roundToInt(),
            pixelAt = background::getPixel,
        )
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = ColorUtils.calculateLuminance(topColor) > 0.5
            isAppearanceLightNavigationBars = ColorUtils.calculateLuminance(bottomColor) > 0.5
        }
    }
    return dialog
}

@Composable
private fun ReadBackgroundColorPickerScreen(
    background: Bitmap,
    onCancel: () -> Unit,
    onPicked: (ReadBackgroundColorResult) -> Unit,
) {
    BackHandler(onBack = onCancel)
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var position by remember { mutableStateOf(Offset.Unspecified) }
    var initialized by remember { mutableStateOf(false) }
    var sampledColor by remember {
        mutableIntStateOf(
            ReadBackgroundColorSampler.samplePatch(
                width = background.width,
                height = background.height,
                centerX = background.width / 2,
                centerY = background.height / 2,
                pixelAt = background::getPixel,
            )
        )
    }
    val image = remember(background) { background.asImageBitmap() }
    val currentPosition by rememberUpdatedState(position)

    fun updateSelection(target: Offset) {
        if (viewport.width <= 0 || viewport.height <= 0) return
        position = Offset(
            x = target.x.coerceIn(0f, viewport.width.toFloat()),
            y = target.y.coerceIn(0f, viewport.height.toFloat()),
        )
        val bitmapX = (
            position.x / viewport.width * (background.width - 1)
            ).roundToInt().coerceIn(0, background.width - 1)
        val bitmapY = (
            position.y / viewport.height * (background.height - 1)
            ).roundToInt().coerceIn(0, background.height - 1)
        sampledColor = ReadBackgroundColorSampler.samplePatch(
            width = background.width,
            height = background.height,
            centerX = bitmapX,
            centerY = bitmapY,
            pixelAt = background::getPixel,
        )
    }

    LaunchedEffect(viewport) {
        if (!initialized && viewport.width > 0 && viewport.height > 0) {
            initialized = true
            updateSelection(Offset(viewport.width / 2f, viewport.height / 2f))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { viewport = it }
            .pointerInput(viewport) {
                detectTapGestures(onTap = ::updateSelection)
            },
    ) {
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize(),
        )

        PickerHint(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 18.dp),
        )

        if (position != Offset.Unspecified) {
            ColorSamplePuck(
                color = Color(sampledColor),
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (position.x - 26.dp.toPx()).roundToInt(),
                            y = (position.y - 26.dp.toPx()).roundToInt(),
                        )
                    }
                    .pointerInput(viewport) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            updateSelection(currentPosition + dragAmount)
                        }
                    },
            )
        }

        PickerActionBar(
            onCancel = onCancel,
            onComplete = {
                onPicked(ReadBackgroundColorResult(color = sampledColor))
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 32.dp, vertical = 20.dp),
        )
    }
}

@Composable
private fun PickerHint(modifier: Modifier = Modifier) {
    val style = pickerHintStyle()
    NgGlassSurface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        style = style,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 9.dp),
    ) {
        Text(
            text = stringResource(R.string.read_style_floating_picker_hint),
            color = style.contentColor.copy(alpha = 0.78f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ColorSamplePuck(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(52.dp)
            .shadow(2.dp, CircleShape, clip = false)
            .background(Color.White.copy(alpha = 0.96f), CircleShape)
            .border(1.dp, Color(0xFF62605D).copy(alpha = 0.58f), CircleShape)
            .padding(4.dp)
            .background(color, CircleShape),
    )
}

@Composable
private fun PickerActionBar(
    onCancel: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = Color(NgTheme.colors.primary)
    val secondaryContainer = if (NgTheme.snapshot.isDark) {
        Color(0xFF2C2A28)
    } else {
        Color.White
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier
                .weight(1f)
                .fillMaxSize(),
            shape = CircleShape,
            border = BorderStroke(1.dp, accent),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = secondaryContainer,
                contentColor = accent,
            ),
            contentPadding = PaddingValues(0.dp),
        ) {
            Text(
                text = stringResource(R.string.cancel),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Button(
            onClick = onComplete,
            modifier = Modifier
                .weight(1f)
                .fillMaxSize(),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = accent,
                contentColor = Color.White,
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 2.dp,
                pressedElevation = 1.dp,
            ),
            contentPadding = PaddingValues(0.dp),
        ) {
            Text(
                text = stringResource(R.string.complete),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun pickerHintStyle(): NgGlassStyle {
    val snapshot = NgTheme.snapshot
    val isDark = snapshot.isDark
    val neutral = if (isDark) Color(0xFF242321) else Color.White
    return readFloatingGlassStyle(
        transparencyPercent = 12,
        primaryStrengthPercent = 0,
    ).copy(
        containerTop = neutral.copy(alpha = if (isDark) 0.92f else 0.90f),
        containerBottom = neutral.copy(alpha = if (isDark) 0.86f else 0.82f),
        accentGlow = Color.Transparent,
        borderColor = Color.White.copy(alpha = if (isDark) 0.22f else 0.92f),
        edgeHighlight = Color.White.copy(alpha = if (isDark) 0.30f else 0.96f),
        surfaceGloss = Color.White.copy(alpha = if (isDark) 0.04f else 0.05f),
        depthEdge = Color.Black.copy(alpha = if (isDark) 0.12f else 0.05f),
        shadowElevation = 3.dp,
        borderWidth = 0.6.dp,
        highlightWidth = 0.8.dp,
    )
}
