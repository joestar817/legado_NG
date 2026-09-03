package io.legado.app.ui.widget.dialog

import android.app.Dialog
import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.textclassifier.TextClassifier
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.help.IntentData
import io.legado.app.ui.design.components.compose.NgBottomDrawerSurface
import io.legado.app.ui.design.components.compose.NgDrawerContentCardStyle
import io.legado.app.ui.design.components.compose.ngDrawerContentCardColor
import io.legado.app.ui.design.components.compose.NgButton
import io.legado.app.ui.design.components.compose.NgLazyListFastScrollerVariant
import io.legado.app.ui.design.components.compose.NgLongDrawerHeader
import io.legado.app.ui.design.components.compose.NgScrollFastScroller
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.widget.text.ScrollTextView
import io.legado.app.utils.getCompatColor
import io.legado.app.utils.setMarkdown
import io.legado.app.utils.showDialogFragment
import io.noties.markwon.Markwon
import io.noties.markwon.core.spans.EmphasisSpan
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.glide.GlideImagesPlugin
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** NG 只读 Markdown 文本抽屉，不提供源码或编辑入口。 */
class MarkdownTextDrawer() : BottomSheetDialogFragment() {

    constructor(title: String, content: String) : this() {
        arguments = Bundle().apply {
            putString(ARG_TITLE, title)
            putString(ARG_CONTENT, IntentData.put(content))
        }
    }

    private var renderedMarkdown by mutableStateOf<RenderedMarkdown?>(null)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        BottomSheetDialog(requireContext(), theme)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setBackgroundColor(AndroidColor.TRANSPARENT)
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val title = requireArguments().getString(ARG_TITLE).orEmpty()
        val content = IntentData.get<String>(
            requireArguments().getString(ARG_CONTENT),
        ).orEmpty()
        (view as ComposeView).setContent {
            NgAppTheme(updateSystemBars = false) {
                MarkdownTextDrawerContent(
                    title = title,
                    renderedMarkdown = renderedMarkdown,
                    onImageLongClick = { source ->
                        showDialogFragment(PhotoDialog(source))
                    },
                )
            }
        }
        renderMarkdown(content)
    }

    override fun onStart() {
        super.onStart()
        configureMarkdownTextDrawerSheet()
    }

    override fun show(manager: FragmentManager, tag: String?) {
        runCatching { super.show(manager, tag) }
            .onFailure { AppLog.put("显示只读文本抽屉失败 tag:$tag", it) }
    }

    private fun renderMarkdown(content: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val rendered = renderMarkdownContent(requireContext(), content)
            if (isActive) renderedMarkdown = rendered
        }
    }

    companion object {
        private const val ARG_TITLE = "title"
        private const val ARG_CONTENT = "content"
    }
}

internal data class RenderedMarkdown(
    val markwon: Markwon,
    val content: Spanned,
)

@Composable
internal fun MarkdownTextDrawerContent(
    title: String,
    renderedMarkdown: RenderedMarkdown?,
    onImageLongClick: (String) -> Unit,
    bottomActionText: String? = null,
    onBottomAction: (() -> Unit)? = null,
) {
    NgBottomDrawerSurface(
        modifier = Modifier.fillMaxSize(),
        contentCardStyle = NgDrawerContentCardStyle.ADAPTIVE,
    ) {
        MarkdownTextContentLayout(
            title = title,
            renderedMarkdown = renderedMarkdown,
            onImageLongClick = onImageLongClick,
            bottomActionText = bottomActionText,
            onBottomAction = onBottomAction,
            showDrawerHandle = true,
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 12.dp),
        )
    }
}

@Composable
internal fun MarkdownTextDialogContent(
    title: String,
    renderedMarkdown: RenderedMarkdown?,
    onImageLongClick: (String) -> Unit,
    bottomActionText: String,
    onBottomAction: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = colorResource(R.color.ng_surface),
        contentColor = colorResource(R.color.ng_on_surface),
        shape = RoundedCornerShape(NgTheme.shapes.dialogDp.dp),
    ) {
        MarkdownTextContentLayout(
            title = title,
            renderedMarkdown = renderedMarkdown,
            onImageLongClick = onImageLongClick,
            bottomActionText = bottomActionText,
            onBottomAction = onBottomAction,
            showDrawerHandle = false,
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 12.dp),
        )
    }
}

@Composable
private fun MarkdownTextContentLayout(
    title: String,
    renderedMarkdown: RenderedMarkdown?,
    onImageLongClick: (String) -> Unit,
    bottomActionText: String?,
    onBottomAction: (() -> Unit)?,
    showDrawerHandle: Boolean,
    modifier: Modifier,
) {
    val scrollViewRef = remember { arrayOfNulls<ScrollTextView>(1) }
    var scrollFraction by remember { mutableFloatStateOf(0f) }
    var canScroll by remember { mutableStateOf(false) }
    var scrollInProgress by remember { mutableStateOf(false) }
    val scrollIdleRunnable = remember { Runnable { scrollInProgress = false } }
    val updateScrollMetrics: (ScrollTextView, Boolean) -> Unit = { textView, scrolling ->
        val maxScrollY = textView.maxScrollOffset()
        canScroll = maxScrollY > 0
        scrollFraction = if (maxScrollY > 0) {
            (textView.scrollY.toFloat() / maxScrollY).coerceIn(0f, 1f)
        } else {
            0f
        }
        if (scrolling && canScroll) {
            scrollInProgress = true
            textView.removeCallbacks(scrollIdleRunnable)
            textView.postDelayed(scrollIdleRunnable, 120L)
        }
    }
    Column(modifier = modifier) {
        if (showDrawerHandle) {
            NgLongDrawerHeader(title = title, centerTitle = true)
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = title,
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(NgTheme.colors.onSurface),
                    fontSize = 17.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            color = ngDrawerContentCardColor(),
            contentColor = colorResource(R.color.ng_on_surface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(
                width = if (NgTheme.snapshot.isEInk) 1.dp else 0.6.dp,
                color = Color(NgTheme.colors.outlineVariant).copy(alpha = 0.42f),
            ),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { context ->
                        ScrollTextView(context, null).apply {
                            val density = resources.displayMetrics.density
                            val contentPadding = (12 * density).toInt()
                            val endPadding = (36 * density).toInt()
                            setPadding(
                                contentPadding,
                                contentPadding,
                                endPadding,
                                contentPadding,
                            )
                            setTextColor(context.getCompatColor(R.color.ng_on_surface))
                            setTextIsSelectable(true)
                            setBackgroundColor(AndroidColor.TRANSPARENT)
                            isVerticalScrollBarEnabled = false
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                setTextClassifier(TextClassifier.NO_OP)
                            }
                            setOnScrollChangeListener { _, _, _, _, _ ->
                                updateScrollMetrics(this, true)
                            }
                            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                                updateScrollMetrics(this, false)
                            }
                            scrollViewRef[0] = this
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { textView ->
                        renderedMarkdown?.let { rendered ->
                            if (textView.tag !== rendered.content) {
                                textView.setMarkdown(
                                    markwon = rendered.markwon,
                                    spanned = rendered.content,
                                    imgOnLongClickListener = onImageLongClick,
                                )
                                textView.tag = rendered.content
                                textView.post { updateScrollMetrics(textView, false) }
                            }
                        }
                    },
                )
                NgScrollFastScroller(
                    scrollFraction = scrollFraction,
                    canScroll = canScroll,
                    isScrollInProgress = scrollInProgress,
                    onScrollFractionChange = { fraction ->
                        scrollViewRef[0]?.let { textView ->
                            textView.scrollTo(
                                0,
                                (textView.maxScrollOffset() * fraction).roundToInt(),
                            )
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 4.dp),
                    variant = NgLazyListFastScrollerVariant.FLOATING_HANDLE,
                )
                if (renderedMarkdown == null) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .height(28.dp),
                        color = Color(NgTheme.colors.primary),
                        strokeWidth = 2.5.dp,
                    )
                }
            }
        }
        if (bottomActionText != null && onBottomAction != null) {
            Spacer(Modifier.height(12.dp))
            NgButton(
                onClick = onBottomAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text(
                    text = bottomActionText,
                    fontSize = 15.sp,
                )
            }
        }
    }
}

internal fun BottomSheetDialogFragment.configureMarkdownTextDrawerSheet() {
    dialog?.window?.apply {
        setBackgroundDrawableResource(R.color.transparent)
        addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        attributes = attributes.apply { dimAmount = 0.22f }
        decorView.setPadding(0, 0, 0, 0)
    }
    val sheet = dialog?.findViewById<View>(
        com.google.android.material.R.id.design_bottom_sheet,
    ) ?: return
    sheet.setBackgroundColor(AndroidColor.TRANSPARENT)
    sheet.layoutParams = sheet.layoutParams.apply {
        height = (resources.displayMetrics.heightPixels * MARKDOWN_DRAWER_HEIGHT_RATIO).toInt()
    }
    BottomSheetBehavior.from(sheet).apply {
        skipCollapsed = true
        isFitToContents = true
        isDraggable = true
        isDraggableOnNestedScroll = true
        state = BottomSheetBehavior.STATE_EXPANDED
    }
}

internal suspend fun renderMarkdownContent(
    context: Context,
    content: String,
): RenderedMarkdown = withContext(IO) {
    val markwon = Markwon.builder(context)
        .usePlugin(GlideImagesPlugin.create(Glide.with(context)))
        .usePlugin(HtmlPlugin.create())
        .usePlugin(TablePlugin.create(context))
        .build()
    RenderedMarkdown(
        markwon = markwon,
        content = markwon.toMarkdown(content).withoutItalic(),
    )
}

private fun ScrollTextView.maxScrollOffset(): Int =
    ((layout?.height ?: 0) + totalPaddingTop + totalPaddingBottom - height).coerceAtLeast(0)

private fun Spanned.withoutItalic(): Spanned {
    val spannable = SpannableString(this)
    getSpans(0, length, EmphasisSpan::class.java).forEach(spannable::removeSpan)
    getSpans(0, length, StyleSpan::class.java).forEach { span ->
        when (span.style) {
            Typeface.ITALIC -> spannable.removeSpan(span)
            Typeface.BOLD_ITALIC -> {
                val start = getSpanStart(span)
                val end = getSpanEnd(span)
                val flags = getSpanFlags(span)
                spannable.removeSpan(span)
                if (start >= 0 && end >= 0) {
                    spannable.setSpan(StyleSpan(Typeface.BOLD), start, end, flags)
                }
            }
        }
    }
    return spannable
}

private const val MARKDOWN_DRAWER_HEIGHT_RATIO = 0.82f
