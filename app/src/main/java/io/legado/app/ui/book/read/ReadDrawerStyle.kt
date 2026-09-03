package io.legado.app.ui.book.read

import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.View
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.NgColorConfigStore
import io.legado.app.help.config.NgThemeModeStore
import io.legado.app.help.config.NgThemePresentationMode
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ReadFloatingColorStyle
import io.legado.app.help.config.ThemeConfig
import io.legado.app.ui.design.components.compose.NgGlassDefaults
import io.legado.app.ui.design.components.compose.NgGlassSurface
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.design.theme.NgThemeResolver
import io.legado.app.ui.design.theme.NgThemeSnapshot
import io.legado.app.utils.dpToPx
import io.legado.app.utils.windowSize
import splitties.systemservices.windowManager
import java.util.WeakHashMap
import kotlin.math.roundToInt

object ReadDrawerStyle {
    private const val DRAWER_AVOIDANCE_ANIMATION_DURATION = 180L

    private val topRadius: Float
        get() = 18.dpToPx().toFloat()

    private val dialogAvoidanceStates = WeakHashMap<View, DialogAvoidanceState>()
    private val drawerAvoidanceStates = WeakHashMap<View, DrawerAvoidanceState>()

    private data class DialogAvoidanceState(
        val originalWindowY: Int,
    )

    private data class DrawerAvoidanceState(
        val originalTranslationY: Float,
        var ownerDecorView: View? = null,
    )

    /**
     * 在保留既有 View 内容结构的前提下，只替换阅读配置抽屉的承载面。
     * 配置内容较密，使用 dialogAlpha；初始阅读菜单继续使用更通透的 floatingStyle。
     */
    fun applyGlassBackground(
        view: ComposeView,
        radiusDp: Int = 12,
        disposeOnDetachedFromWindow: Boolean = false,
    ) {
        val snapshot = themeSnapshot(view.context)
        view.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        view.setViewCompositionStrategy(
            if (disposeOnDetachedFromWindow) {
                ViewCompositionStrategy.DisposeOnDetachedFromWindow
            } else {
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            }
        )
        view.setContent {
            NgAppTheme(
                snapshot = snapshot,
                updateSystemBars = false,
            ) {
                NgGlassSurface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(radiusDp.dp),
                    style = NgGlassDefaults.style(
                        containerAlpha = NgTheme.effects.dialogAlpha
                    )
                ) {}
            }
        }
    }

    /**
     * 阅读浮层使用背景取色时保持自己的日夜配色；跟随应用时采用当前应用配色。
     * 柔光渐变只参与配色，不作为阅读页背景；墨水屏继续保留全局强制黑白。
     */
    fun themeSnapshot(
        context: Context,
        primaryStrengthPercent: Int = ReadBookConfig.durConfig.curReadFloatingPrimaryStrength(),
        colorStyle: ReadFloatingColorStyle = ReadBookConfig.durConfig.curReadFloatingColorStyle(),
    ): NgThemeSnapshot = if (
        NgThemeModeStore.current(context) == NgThemePresentationMode.EINK
    ) {
        NgThemeResolver.resolve(context)
    } else {
        val seed = ReadBookConfig.durConfig.curReadFloatingSeed()
        val base = if (
            ReadBookConfig.durConfig.curReadFloatingFollowsApplication() &&
            NgThemeModeStore.current(context) == NgThemePresentationMode.SOFT_GRADIENT
        ) {
            NgThemeResolver.resolve(context)
        } else {
            NgThemeResolver.resolve(
                context = context,
                colors = NgColorConfigStore.current(context),
                isDark = ReadBookConfig.isNightTheme,
            )
        }
        val seeded = ReadFloatingPalette.applySeed(
            base = base,
            seed = seed,
        )
        ReadFloatingPalette.applySemanticRoles(
            snapshot = seeded,
            primaryStrengthPercent = primaryStrengthPercent,
            colorStyle = colorStyle,
        )
    }

    fun contentColor(context: Context): Int = themeSnapshot(context).colors.onSurface

    fun accentColor(context: Context): Int = themeSnapshot(context).colors.secondary

    fun indicatorColor(context: Context): Int = themeSnapshot(context).colors.primary

    fun surfaceColor(context: Context): Int = themeSnapshot(context).colors.surface

    /**
     * Material BottomSheet 的默认回调会在非 IME Insets 动画开始时也写入 translationY，
     * 阅读页显示系统栏时会把抽屉短暂移到屏幕顶部。保留原 IME 补偿，只过滤其它类型。
     */
    fun installImeOnlyBottomSheetInsetsAnimation(sheet: View) {
        sheet.doOnLayout {
            ViewCompat.setWindowInsetsAnimationCallback(
                sheet,
                ImeOnlyBottomSheetInsetsAnimationCallback(sheet),
            )
        }
    }

    /** 与 View 版浮动 Dock 对齐，但按阅读页自己的日夜快照取色。 */
    @Composable
    fun dockSurfaceColor(alpha: Float = 0.28f): ComposeColor {
        val baseColor = if (NgTheme.snapshot.isDark) {
            ComposeColor(0xFF1F1F1F)
        } else {
            ComposeColor.White
        }
        return baseColor.copy(alpha = alpha)
    }

    fun positionDialogAbove(dialog: Dialog?, avoidView: View, gapDp: Int = 16) {
        val window = dialog?.window ?: return
        val decorView = window.decorView
        val dialogState = dialogAvoidanceStates.getOrPut(decorView) {
            DialogAvoidanceState(window.attributes.y)
        }
        val drawerState = drawerAvoidanceStates.getOrPut(avoidView) {
            DrawerAvoidanceState(avoidView.translationY)
        }
        bindDrawerRestore(decorView, avoidView, drawerState)

        decorView.doOnLayout {
            if (!avoidView.isAttachedToWindow) return@doOnLayout
            val dialogLocation = IntArray(2)
            val avoidLocation = IntArray(2)
            decorView.getLocationOnScreen(dialogLocation)
            avoidView.getLocationOnScreen(avoidLocation)

            val gap = gapDp.dpToPx()
            val currentWindowOffset = window.attributes.y - dialogState.originalWindowY
            val naturalDialogTop = dialogLocation[1] - currentWindowOffset
            val naturalDialogBottom = naturalDialogTop + decorView.height
            val currentDrawerOffset =
                (avoidView.translationY - drawerState.originalTranslationY).roundToInt()
            val naturalDrawerTop = avoidLocation[1] - currentDrawerOffset
            val overlap = (naturalDialogBottom - (naturalDrawerTop - gap)).coerceAtLeast(0)

            val topInset = ViewCompat.getRootWindowInsets(avoidView)
                ?.getInsets(WindowInsetsCompat.Type.systemBars())
                ?.top
                ?: 0
            val minTop = topInset + gap
            val maxDialogShift = (naturalDialogTop - minTop).coerceAtLeast(0)
            val dialogShift = overlap.coerceAtMost(maxDialogShift)
            val targetWindowY = dialogState.originalWindowY - dialogShift
            if (window.attributes.y != targetWindowY) {
                window.attributes = window.attributes.apply {
                    y = targetWindowY
                }
            }

            val drawerRetreat = (overlap - dialogShift)
                .coerceIn(0, avoidView.height)
                .toFloat()
            updateDrawerTranslation(avoidView, drawerState, drawerRetreat)
        }
    }

    private class ImeOnlyBottomSheetInsetsAnimationCallback(
        private val view: View,
    ) : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_STOP) {

        private val location = IntArray(2)
        private var startY = 0
        private var startTranslationY = 0f

        override fun onPrepare(animation: WindowInsetsAnimationCompat) {
            if (!animation.isImeAnimation()) return
            view.getLocationOnScreen(location)
            startY = location[1]
        }

        override fun onStart(
            animation: WindowInsetsAnimationCompat,
            bounds: WindowInsetsAnimationCompat.BoundsCompat,
        ): WindowInsetsAnimationCompat.BoundsCompat {
            if (!animation.isImeAnimation()) return bounds
            view.getLocationOnScreen(location)
            startTranslationY = (startY - location[1]).toFloat()
            view.translationY = startTranslationY
            return bounds
        }

        override fun onProgress(
            insets: WindowInsetsCompat,
            runningAnimations: MutableList<WindowInsetsAnimationCompat>,
        ): WindowInsetsCompat {
            val imeAnimation = runningAnimations.firstOrNull { it.isImeAnimation() }
                ?: return insets
            view.translationY = startTranslationY * (1f - imeAnimation.interpolatedFraction)
            return insets
        }

        override fun onEnd(animation: WindowInsetsAnimationCompat) {
            if (!animation.isImeAnimation()) return
            view.translationY = 0f
        }

        private fun WindowInsetsAnimationCompat.isImeAnimation(): Boolean =
            typeMask and WindowInsetsCompat.Type.ime() != 0
    }

    private fun bindDrawerRestore(
        decorView: View,
        avoidView: View,
        drawerState: DrawerAvoidanceState,
    ) {
        if (drawerState.ownerDecorView === decorView) return
        drawerState.ownerDecorView = decorView
        decorView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = Unit

            override fun onViewDetachedFromWindow(view: View) {
                view.removeOnAttachStateChangeListener(this)
                dialogAvoidanceStates.remove(view)
                if (drawerState.ownerDecorView !== view) return
                drawerState.ownerDecorView = null
                restoreDrawerTranslation(avoidView, drawerState)
            }
        })
    }

    private fun updateDrawerTranslation(
        avoidView: View,
        drawerState: DrawerAvoidanceState,
        retreat: Float,
    ) {
        val target = drawerState.originalTranslationY + retreat
        if (avoidView.translationY == target) return
        avoidView.animate().cancel()
        if (!avoidView.isAttachedToWindow) {
            avoidView.translationY = target
            return
        }
        avoidView.animate()
            .translationY(target)
            .setDuration(DRAWER_AVOIDANCE_ANIMATION_DURATION)
            .start()
    }

    private fun restoreDrawerTranslation(
        avoidView: View,
        drawerState: DrawerAvoidanceState,
    ) {
        avoidView.animate().cancel()
        if (!avoidView.isAttachedToWindow) {
            avoidView.translationY = drawerState.originalTranslationY
            drawerAvoidanceStates.remove(avoidView)
            return
        }
        avoidView.animate()
            .translationY(drawerState.originalTranslationY)
            .setDuration(DRAWER_AVOIDANCE_ANIMATION_DURATION)
            .withEndAction {
                if (drawerState.ownerDecorView == null) {
                    drawerAvoidanceStates.remove(avoidView)
                }
            }
            .start()
    }

    fun applyTopRoundedBackground(
        view: View,
        fallbackColor: Int = surfaceColor(view.context),
    ) {
        view.background = createTopRoundedBackground(view.context, fallbackColor)
    }

    fun createTopRoundedBackground(
        context: Context,
        fallbackColor: Int = surfaceColor(context),
    ): Drawable {
        val source = if (!AppConfig.isEInkMode && ThemeConfig.isReadingNgBackgroundTheme(context)) {
            ThemeConfig.getBgImage(context, context.windowManager.windowSize)
                ?: ThemeConfig.getGradientBgImage(context)
        } else {
            null
        }
        return wrapTopRounded(source ?: ColorDrawable(fallbackColor))
    }

    fun wrapTopRounded(source: Drawable): Drawable {
        return TopRoundedDrawable(source, topRadius)
    }
}

private class TopRoundedDrawable(
    private val source: Drawable,
    private val radius: Float
) : Drawable() {
    private val path = Path()
    private val rect = RectF()

    override fun onBoundsChange(bounds: Rect) {
        rect.set(bounds)
        path.reset()
        path.addRoundRect(
            rect,
            floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f),
            Path.Direction.CW
        )
        source.bounds = bounds
    }

    override fun draw(canvas: Canvas) {
        val saveCount = canvas.save()
        canvas.clipPath(path)
        source.bounds = bounds
        source.draw(canvas)
        canvas.restoreToCount(saveCount)
    }

    override fun setAlpha(alpha: Int) {
        source.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        source.colorFilter = colorFilter
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }

    override fun getIntrinsicWidth(): Int {
        return -1
    }

    override fun getIntrinsicHeight(): Int {
        return -1
    }

    override fun getMinimumWidth(): Int {
        return 0
    }

    override fun getMinimumHeight(): Int {
        return 0
    }
}
