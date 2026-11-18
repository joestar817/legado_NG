package io.legado.app.ui.book.read.config

import android.content.DialogInterface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.PageAnim
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.book.read.ReadDrawerStyle
import io.legado.app.ui.book.read.readFloatingGlassStyle
import io.legado.app.ui.design.components.compose.NgGlassSurface
import io.legado.app.ui.design.components.compose.NgDismissibleDrawer
import io.legado.app.ui.design.components.compose.NgSlider
import io.legado.app.ui.design.components.compose.NgSliderVariant
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgColorMath
import io.legado.app.ui.design.theme.NgTheme
import kotlin.math.roundToInt

private val AUTO_READ_SPEED_LEVELS = intArrayOf(120, 90, 60, 40, 30, 20, 10, 5, 1)

class AutoReadDialog : DialogFragment() {

    private val callBack: CallBack? get() = activity as? CallBack
    private var bottomDialogRegistered = false
    private var speed by mutableIntStateOf(
        ReadBookConfig.autoReadSpeed.coerceIn(MIN_AUTO_READ_SPEED, MAX_AUTO_READ_SPEED)
    )
    private var pageMode by mutableIntStateOf(ReadBookConfig.autoReadPageMode)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            setStyle(STYLE_NO_TITLE, 0)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.run {
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setBackgroundDrawableResource(R.color.transparent)
            decorView.setPadding(0, 0, 0, 0)
            attributes = attributes.apply {
                dimAmount = 0.0f
                gravity = Gravity.BOTTOM
            }
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        if (bottomDialogRegistered) {
            (activity as? ReadBookActivity)?.let {
                it.bottomDialog = (it.bottomDialog - 1).coerceAtLeast(0)
            }
            bottomDialogRegistered = false
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val readActivity = activity as? ReadBookActivity ?: run {
            dismissAllowingStateLoss()
            return
        }
        val bottomDialog = if (!bottomDialogRegistered) {
            readActivity.bottomDialog.also {
                readActivity.bottomDialog = it + 1
                bottomDialogRegistered = true
            }
        } else {
            0
        }
        if (bottomDialog > 0) {
            dismissAllowingStateLoss()
            return
        }
        val snapshot = ReadDrawerStyle.themeSnapshot(requireContext())
        (view as ComposeView).apply {
            setContent {
                NgAppTheme(snapshot = snapshot, updateSystemBars = false) {
                    NgDismissibleDrawer(onDismiss = { dismissAllowingStateLoss() }) {
                        AutoReadPanel(
                            speed = speed,
                            pageMode = pageMode,
                            onSpeedChanged = { speed = it },
                            onSpeedChangeFinished = {
                                ReadBookConfig.autoReadSpeed = speed
                            },
                            onPageModeChanged = ::applyPageModeSelection,
                            onStop = {
                                callBack?.autoPageStop()
                                post { dismissAllowingStateLoss() }
                            },
                        )
                    }
                }
            }
        }
    }

    override fun show(manager: FragmentManager, tag: String?) {
        kotlin.runCatching {
            manager.beginTransaction().remove(this).commit()
            super.show(manager, tag)
        }.onFailure {
            AppLog.put("显示对话框失败 tag:$tag", it)
        }
    }

    private fun applyPageModeSelection(@PageAnim.Anim mode: Int) {
        if (pageMode == mode) return
        pageMode = mode
        ReadBookConfig.autoReadPageMode = mode
        (activity as? ReadBookActivity)?.applyAutoPageMode(mode)
    }

    interface CallBack {
        fun autoPageStop()
    }

    private companion object {
        const val MIN_AUTO_READ_SPEED = 1
        const val MAX_AUTO_READ_SPEED = 120
    }
}

@Composable
private fun AutoReadPanel(
    speed: Int,
    @PageAnim.Anim pageMode: Int,
    onSpeedChanged: (Int) -> Unit,
    onSpeedChangeFinished: () -> Unit,
    onPageModeChanged: (Int) -> Unit,
    onStop: () -> Unit,
) {
    val contentColor = Color(NgTheme.colors.onSurface)
    val accentColor = Color(NgTheme.colors.primary)
    val actionTextColor = Color(NgTheme.colors.secondary)
    val selectedContentColor = Color(NgTheme.colors.onPrimary)
    val surfaceColor = Color(NgTheme.colors.surface)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        NgGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            style = readFloatingGlassStyle().copy(shadowElevation = 0.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.auto_next_page),
                        color = contentColor,
                        fontSize = 17.sp,
                    )
                    Spacer(Modifier.weight(1f))
                    AutoReadStopButton(
                        accentColor = actionTextColor,
                        surfaceColor = surfaceColor,
                        onClick = onStop,
                    )
                }

                AutoReadDivider(contentColor)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.auto_page_speed),
                        color = contentColor,
                        fontSize = 14.sp,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.auto_page_slow),
                        color = actionTextColor,
                        fontSize = 12.sp,
                    )
                    NgSlider(
                        value = autoReadSpeedPosition(speed),
                        onValueChange = { position ->
                            val index = position.roundToInt()
                                .coerceIn(AUTO_READ_SPEED_LEVELS.indices)
                            onSpeedChanged(AUTO_READ_SPEED_LEVELS[index])
                        },
                        valueRange = 0f..AUTO_READ_SPEED_LEVELS.lastIndex.toFloat(),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp),
                        steps = AUTO_READ_SPEED_LEVELS.size - 2,
                        variant = NgSliderVariant.DISCRETE,
                        onValueChangeFinished = onSpeedChangeFinished,
                    )
                    Text(
                        text = stringResource(R.string.auto_page_fast),
                        color = actionTextColor,
                        fontSize = 12.sp,
                    )
                }

                AutoReadModeDock(
                    selectedIndex = if (pageMode == PageAnim.coverPageAnim) 1 else 0,
                    labels = listOf(
                        stringResource(R.string.page_anim_scroll),
                        stringResource(R.string.page_anim_cover),
                    ),
                    contentColor = contentColor,
                    accentColor = accentColor,
                    selectedContentColor = selectedContentColor,
                    onSelected = { index ->
                        onPageModeChanged(
                            if (index == 0) PageAnim.scrollPageAnim else PageAnim.coverPageAnim
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun AutoReadStopButton(
    accentColor: Color,
    surfaceColor: Color,
    onClick: () -> Unit,
) {
    val label = stringResource(R.string.stop)
    val shape = RoundedCornerShape(15.dp)
    Row(
        modifier = Modifier
            .height(30.dp)
            .clip(shape)
            .background(surfaceColor.copy(alpha = 0.76f))
            .border(0.8.dp, accentColor.copy(alpha = 0.72f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Stop,
            contentDescription = label,
            modifier = Modifier.size(13.dp),
            tint = accentColor,
        )
        Text(
            text = label,
            modifier = Modifier.padding(start = 5.dp),
            color = accentColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun AutoReadModeDock(
    labels: List<String>,
    selectedIndex: Int,
    contentColor: Color,
    accentColor: Color,
    selectedContentColor: Color,
    onSelected: (Int) -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val dockSurfaceColor = ReadDrawerStyle.dockSurfaceColor()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(shape)
            .background(dockSurfaceColor)
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex.coerceIn(labels.indices)
            val itemShape = RoundedCornerShape(10.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(itemShape)
                    .then(
                        if (selected) {
                            Modifier.background(accentColor)
                        } else {
                            Modifier
                        }
                    )
                    .clickable(role = Role.Tab) { onSelected(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (selected) selectedContentColor else contentColor,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun AutoReadDivider(contentColor: Color) {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .height(0.8.dp)
            .background(contentColor.copy(alpha = 0.12f)),
    )
}

private fun autoReadSpeedPosition(speed: Int): Float {
    val clampedSpeed = speed.coerceIn(
        AUTO_READ_SPEED_LEVELS.last(),
        AUTO_READ_SPEED_LEVELS.first(),
    )
    for (index in 0 until AUTO_READ_SPEED_LEVELS.lastIndex) {
        val slowerSpeed = AUTO_READ_SPEED_LEVELS[index]
        val fasterSpeed = AUTO_READ_SPEED_LEVELS[index + 1]
        if (clampedSpeed in fasterSpeed..slowerSpeed) {
            val intervalFraction =
                (slowerSpeed - clampedSpeed).toFloat() / (slowerSpeed - fasterSpeed)
            return index + intervalFraction
        }
    }
    return AUTO_READ_SPEED_LEVELS.lastIndex.toFloat()
}
