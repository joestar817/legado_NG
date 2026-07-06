package io.legado.app.ui.book.read.aloud

import android.animation.ObjectAnimator
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import io.legado.app.R
import io.legado.app.lib.theme.accentColor
import io.legado.app.model.BookCover
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.service.BaseReadAloudService
import io.legado.app.utils.dpToPx
import java.util.WeakHashMap
import kotlin.math.abs

object ReadAloudMiniPlayer {

    private const val TAG_ID = R.id.read_aloud_mini_player
    private const val ROTATION_DURATION = 16000L
    private val coverAnimators = WeakHashMap<ImageView, ObjectAnimator>()
    private var savedX: Float? = null
    private var savedY: Float? = null

    fun attach(activity: Activity) {
        if (activity is ReadAloudPlayerActivity) {
            detach(activity)
            return
        }
        val content = activity.findViewById<FrameLayout>(android.R.id.content) ?: return
        val view = content.findViewById<View>(TAG_ID) ?: createView(activity).also {
            content.addView(it)
        }
        refresh(activity)
        restorePosition(view)
        view.bringToFront()
    }

    fun refresh(activity: Activity) {
        val content = activity.findViewById<FrameLayout>(android.R.id.content) ?: return
        val view = content.findViewById<View>(TAG_ID) ?: return
        view.isVisible = BaseReadAloudService.isRun && activity !is ReadAloudPlayerActivity
        if (!view.isVisible) return
        val cover = view.findViewById<ImageView>(R.id.iv_read_aloud_mini_cover)
        val play = view.findViewById<ImageButton>(R.id.btn_read_aloud_mini_play)
        BookCover.load(
            context = activity,
            path = ReadBook.book?.getDisplayCover(),
            sourceOrigin = ReadBook.bookSource?.bookSourceUrl
        ).into(cover)
        upCoverRotation(cover)
        play.setImageResource(
            if (BaseReadAloudService.isPlay()) R.drawable.ic_pause_24dp else R.drawable.ic_play_24dp
        )
    }

    fun detach(activity: Activity) {
        val content = activity.findViewById<FrameLayout>(android.R.id.content) ?: return
        content.findViewById<View>(TAG_ID)?.let {
            it.findViewById<ImageView>(R.id.iv_read_aloud_mini_cover)?.let(::stopCoverRotation)
            content.removeView(it)
        }
    }

    private fun createView(activity: Activity): View {
        val capsule = LinearLayout(activity).apply {
            id = TAG_ID
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = capsuleBackground(activity.accentColor)
            elevation = 6.dpToPx().toFloat()
            setPadding(5.dpToPx(), 5.dpToPx(), 7.dpToPx(), 5.dpToPx())
            isClickable = true
            isFocusable = true
        }
        installDragTouch(activity, capsule, capsule) {
            activity.startActivity(Intent(activity, ReadAloudPlayerActivity::class.java))
        }
        val cover = ImageView(activity).apply {
            id = R.id.iv_read_aloud_mini_cover
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
        }
        installDragTouch(activity, capsule, cover) {
            activity.startActivity(Intent(activity, ReadAloudPlayerActivity::class.java))
        }
        capsule.addView(cover, LinearLayout.LayoutParams(42.dpToPx(), 42.dpToPx()))
        val play = ImageButton(activity).apply {
            id = R.id.btn_read_aloud_mini_play
            background = circleBackground(ColorUtils.setAlphaComponent(Color.WHITE, 42), Color.WHITE)
            setColorFilter(Color.WHITE)
            setPadding(11.dpToPx(), 11.dpToPx(), 11.dpToPx(), 11.dpToPx())
        }
        installDragTouch(activity, capsule, play) {
            if (BaseReadAloudService.pause) {
                ReadAloud.resume(activity)
            } else {
                ReadAloud.pause(activity)
            }
        }
        capsule.addView(
            play,
            LinearLayout.LayoutParams(42.dpToPx(), 42.dpToPx()).apply {
                marginStart = 7.dpToPx()
            }
        )
        val close = ImageButton(activity).apply {
            id = R.id.btn_read_aloud_mini_close
            setImageResource(R.drawable.ic_baseline_close)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.WHITE)
            setPadding(10.dpToPx(), 10.dpToPx(), 10.dpToPx(), 10.dpToPx())
        }
        installDragTouch(activity, capsule, close) {
            ReadAloud.stop(activity)
            detach(activity)
        }
        capsule.addView(close, LinearLayout.LayoutParams(38.dpToPx(), 38.dpToPx()))
        capsule.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.START or Gravity.BOTTOM
        ).apply {
            marginStart = 24.dpToPx()
            bottomMargin = 108.dpToPx()
        }
        return capsule
    }

    private fun installDragTouch(
        activity: Activity,
        capsule: View,
        touchView: View,
        clickAction: () -> Unit
    ) {
        val touchSlop = ViewConfiguration.get(activity).scaledTouchSlop
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0f
        var startY = 0f
        var dragging = false
        touchView.setOnTouchListener { _, event ->
            val parentView = capsule.parent as? View ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = capsule.x
                    startY = capsule.y
                    dragging = false
                    parentView.parent?.requestDisallowInterceptTouchEvent(true)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!dragging && abs(dx) + abs(dy) > touchSlop) {
                        dragging = true
                    }
                    if (dragging) {
                        val margin = 8.dpToPx().toFloat()
                        val maxX = (parentView.width - capsule.width - margin).coerceAtLeast(margin)
                        val maxY = (parentView.height - capsule.height - margin).coerceAtLeast(margin)
                        capsule.x = (startX + dx).coerceIn(margin, maxX)
                        capsule.y = (startY + dy).coerceIn(margin, maxY)
                    }
                    true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    parentView.parent?.requestDisallowInterceptTouchEvent(false)
                    if (dragging) {
                        savedX = capsule.x
                        savedY = capsule.y
                    } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                        clickAction()
                    }
                    dragging = false
                    true
                }

                else -> false
            }
        }
    }

    private fun restorePosition(view: View) {
        val x = savedX
        val y = savedY
        if (x == null || y == null) return
        view.post {
            val parentView = view.parent as? View ?: return@post
            val margin = 8.dpToPx().toFloat()
            val maxX = (parentView.width - view.width - margin).coerceAtLeast(margin)
            val maxY = (parentView.height - view.height - margin).coerceAtLeast(margin)
            view.x = x.coerceIn(margin, maxX)
            view.y = y.coerceIn(margin, maxY)
        }
    }

    private fun upCoverRotation(cover: ImageView) {
        val animator = coverAnimators.getOrPut(cover) {
            ObjectAnimator.ofFloat(cover, View.ROTATION, 0f, 360f).apply {
                duration = ROTATION_DURATION
                interpolator = LinearInterpolator()
                repeatCount = ObjectAnimator.INFINITE
            }
        }
        if (BaseReadAloudService.isPlay()) {
            if (!animator.isStarted) {
                animator.start()
            } else if (animator.isPaused) {
                animator.resume()
            }
        } else if (animator.isStarted && !animator.isPaused) {
            animator.pause()
        }
    }

    private fun stopCoverRotation(cover: ImageView) {
        coverAnimators.remove(cover)?.cancel()
        cover.rotation = 0f
    }

    private fun capsuleBackground(accent: Int): GradientDrawable {
        val fill = ColorUtils.setAlphaComponent(accent, (255 * 0.82f).toInt())
        val stroke = ColorUtils.setAlphaComponent(Color.WHITE, (255 * 0.28f).toInt())
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 28.dpToPx().toFloat()
            setColor(fill)
            setStroke(1.dpToPx(), stroke)
        }
    }

    private fun circleBackground(fill: Int, stroke: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fill)
            setStroke(2.dpToPx(), ColorUtils.setAlphaComponent(stroke, (255 * 0.48f).toInt()))
        }
    }
}
