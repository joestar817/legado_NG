package io.legado.app.ui.book.read

import android.app.Application
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color.TRANSPARENT
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookChapter
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.book.isLocal
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.config.NgThemeRuntimeAssets
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.LayoutStateChangeEvent
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula
import io.legado.app.model.ReadBook
import io.legado.app.model.webBook.WebBook
import io.legado.app.ui.book.read.config.ReadConfigDialogSurface
import io.legado.app.ui.book.read.config.ReadConfigDialogTitle
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.compose.NgFormActionButton
import io.legado.app.ui.design.components.compose.NgFormField
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.utils.sendToClip
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 内容编辑
 */
class ContentEditDialog : DialogFragment() {

    private val viewModel by viewModels<ContentEditViewModel>()
    private val chapterIndex by lazy {
        arguments?.getInt(ARG_CHAPTER_INDEX) ?: ReadBook.durChapterIndex
    }

    private var chapterTitle by mutableStateOf("")
    private var isLoading by mutableStateOf(false)
    private var editorDocument by mutableStateOf(EditorDocument())
    private var titleEditorState by mutableStateOf<TitleEditorState?>(null)
    private var showOverflowMenu by mutableStateOf(false)

    private var editorRevision = 0
    private var applyingEditorDocument = false
    private var editorView: ContentEditorView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.AppTheme_ContentEditor)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        setBackgroundColor(TRANSPARENT)
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        chapterTitle = arguments?.getString(ARG_CHAPTER_TITLE)
            ?: ReadBook.curTextChapter?.title.orEmpty()
        viewModel.loadStateLiveData.observe(viewLifecycleOwner) {
            isLoading = it
        }
        viewModel.initContent(chapterIndex) { content ->
            val offset = arguments?.getInt(ARG_PARAGRAPH_INDEX)?.let {
                contentEditSelectionOffset(
                    content, it,
                    arguments?.getInt(ARG_PARAGRAPH_OFFSET) ?: 0,
                    arguments?.getString(ARG_SELECTED_TEXT).orEmpty(),
                )
            } ?: ReadBook.durChapterPos
            showEditorContent(content, offset)
        }

        ReadFloatingAppearanceState.refreshFromConfig()
        val snapshot = ReadDrawerStyle.themeSnapshot(requireContext())
        (view as ComposeView).setContent {
            NgAppTheme(snapshot = snapshot, updateSystemBars = false) {
                ContentEditorScreen(
                    chapterTitle = chapterTitle,
                    document = editorDocument,
                    isLoading = isLoading,
                    overflowExpanded = showOverflowMenu,
                    onTitleClick = ::openTitleEditor,
                    onBack = { dialog?.cancel() },
                    onSave = {
                        save()
                        dismiss()
                    },
                    onOverflowClick = { showOverflowMenu = true },
                    onOverflowDismiss = { showOverflowMenu = false },
                    onReset = ::resetContent,
                    onCopyAll = ::copyAll,
                    onEditorCreated = { editorView = it },
                    onEditorTextChanged = { content ->
                        if (!applyingEditorDocument) {
                            viewModel.draftContent = content
                        }
                    },
                    onApplyingDocument = { applying ->
                        applyingEditorDocument = applying
                    },
                )
                titleEditorState?.let { state ->
                    TitleEditorDialog(
                        state = state,
                        onValueChange = { title ->
                            titleEditorState = state.copy(title = title)
                        },
                        onDismiss = { titleEditorState = null },
                        onConfirm = ::updateTitle,
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawable(ColorDrawable(android.graphics.Color.WHITE))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            decorView.setPadding(0, 0, 0, 0)
            WindowCompat.setDecorFitsSystemWindows(this, false)
            WindowInsetsControllerCompat(this, decorView).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = false
            }
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        save()
    }

    override fun onDestroyView() {
        editorView?.takeUnless { it.isReleased }?.text?.toString()?.let {
            viewModel.draftContent = it
        }
        editorView = null
        super.onDestroyView()
    }

    override fun show(manager: FragmentManager, tag: String?) {
        kotlin.runCatching {
            manager.beginTransaction().remove(this).commit()
            super.show(manager, tag)
        }.onFailure {
            AppLog.put("显示对话框失败 tag:$tag", it)
        }
    }

    private fun openTitleEditor() {
        lifecycleScope.launch {
            val book = ReadBook.book ?: return@launch
            val chapter = withContext(IO) {
                appDb.bookChapterDao.getChapter(book.bookUrl, chapterIndex)
            } ?: return@launch
            titleEditorState = TitleEditorState(chapter, chapter.title)
        }
    }

    private fun updateTitle(state: TitleEditorState) {
        titleEditorState = null
        state.chapter.title = state.title
        lifecycleScope.launch {
            withContext(IO) {
                state.chapter.update()
            }
            chapterTitle = state.chapter.getDisplayTitle()
            ReadBook.loadContent(chapterIndex, resetPageOffset = false)
        }
    }

    private fun resetContent() {
        showOverflowMenu = false
        viewModel.initContent(chapterIndex, reset = true) { content ->
            showEditorContent(content)
            ReadBook.loadContent(chapterIndex, resetPageOffset = false)
        }
    }

    private fun copyAll() {
        showOverflowMenu = false
        requireContext().sendToClip("$chapterTitle\n${currentEditorContent()}")
    }

    private fun showEditorContent(content: String, scrollOffset: Int? = null) {
        viewModel.draftContent = content
        editorRevision += 1
        editorDocument = EditorDocument(
            content = content,
            revision = editorRevision,
            scrollOffset = scrollOffset,
        )
    }

    private fun currentEditorContent(): String {
        return editorView?.text?.toString() ?: viewModel.draftContent.orEmpty()
    }

    private fun save() {
        val content = currentEditorContent()
        Coroutine.async {
            val book = ReadBook.book ?: return@async
            val chapter = appDb.bookChapterDao
                .getChapter(book.bookUrl, chapterIndex)
                ?: return@async
            BookHelp.saveText(book, chapter, content)
            ReadBook.loadContent(chapterIndex, resetPageOffset = false)
        }
    }

    class ContentEditViewModel(application: Application) : BaseViewModel(application) {
        val loadStateLiveData = MutableLiveData<Boolean>()
        var content: String? = null
        var draftContent: String? = null

        fun initContent(chapterIndex: Int, reset: Boolean = false, success: (String) -> Unit) {
            execute {
                val book = ReadBook.book ?: return@execute null
                val chapter = appDb.bookChapterDao
                    .getChapter(book.bookUrl, chapterIndex)
                    ?: return@execute null
                if (reset) {
                    content = null
                    draftContent = null
                    BookHelp.delContent(book, chapter)
                    if (!book.isLocal) ReadBook.bookSource?.let { bookSource ->
                        WebBook.getContentAwait(bookSource, book, chapter)
                    }
                }
                return@execute draftContent ?: content ?: let {
                    val contentProcessor = ContentProcessor.get(book.name, book.origin)
                    val content = BookHelp.getContent(book, chapter) ?: return@let null
                    contentProcessor.getContent(book, chapter, content, includeTitle = false)
                        .toString()
                }
            }.onStart {
                loadStateLiveData.postValue(true)
            }.onSuccess {
                content = it
                draftContent = it
                success.invoke(it ?: "")
            }.onFinally {
                loadStateLiveData.postValue(false)
            }
        }
    }
    companion object {
        private const val ARG_CHAPTER_INDEX = "chapterIndex"
        private const val ARG_CHAPTER_TITLE = "chapterTitle"
        private const val ARG_PARAGRAPH_INDEX = "paragraphIndex"
        private const val ARG_PARAGRAPH_OFFSET = "paragraphOffset"
        private const val ARG_SELECTED_TEXT = "selectedTextPrefix"

        fun atSelection(
            chapterIndex: Int,
            chapterTitle: String,
            paragraphIndex: Int,
            paragraphOffset: Int,
            selectedTextPrefix: String,
        ) =
            ContentEditDialog().apply {
                arguments = Bundle().apply {
                    putInt(ARG_CHAPTER_INDEX, chapterIndex)
                    putString(ARG_CHAPTER_TITLE, chapterTitle)
                    putInt(ARG_PARAGRAPH_INDEX, paragraphIndex)
                    putInt(ARG_PARAGRAPH_OFFSET, paragraphOffset)
                    putString(ARG_SELECTED_TEXT, selectedTextPrefix)
                }
            }
    }
}

private data class EditorDocument(
    val content: String = "",
    val revision: Int = 0,
    val scrollOffset: Int? = null,
)

private data class TitleEditorState(
    val chapter: BookChapter,
    val title: String,
)

@Composable
private fun ContentEditorScreen(
    chapterTitle: String,
    document: EditorDocument,
    isLoading: Boolean,
    overflowExpanded: Boolean,
    onTitleClick: () -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onOverflowClick: () -> Unit,
    onOverflowDismiss: () -> Unit,
    onReset: () -> Unit,
    onCopyAll: () -> Unit,
    onEditorCreated: (ContentEditorView) -> Unit,
    onEditorTextChanged: (String) -> Unit,
    onApplyingDocument: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .imePadding(),
    ) {
        ContentEditorTopBar(
            chapterTitle = chapterTitle,
            overflowExpanded = overflowExpanded,
            onTitleClick = onTitleClick,
            onBack = onBack,
            onSave = onSave,
            onOverflowClick = onOverflowClick,
            onOverflowDismiss = onOverflowDismiss,
            onReset = onReset,
            onCopyAll = onCopyAll,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF1E1E1E))
                .navigationBarsPadding(),
        ) {
            ContentEditorTextArea(
                document = document,
                onEditorCreated = onEditorCreated,
                onEditorTextChanged = onEditorTextChanged,
                onApplyingDocument = onApplyingDocument,
            )
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(46.dp),
                    color = Color(NgTheme.colors.primary),
                    trackColor = Color(NgTheme.colors.surfaceContainerHigh),
                    strokeWidth = 4.dp,
                )
            }
        }
    }
}

@Composable
private fun ContentEditorTopBar(
    chapterTitle: String,
    overflowExpanded: Boolean,
    onTitleClick: () -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onOverflowClick: () -> Unit,
    onOverflowDismiss: () -> Unit,
    onReset: () -> Unit,
    onCopyAll: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(end = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.back),
                    modifier = Modifier.size(24.dp),
                    tint = Color.Black,
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(
                        role = Role.Button,
                        onClickLabel = stringResource(R.string.edit),
                        onClick = onTitleClick,
                    ),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = chapterTitle,
                    color = Color.Black,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onSave) {
                Icon(
                    painter = painterResource(R.drawable.ic_save),
                    contentDescription = stringResource(R.string.action_save),
                    modifier = Modifier.size(24.dp),
                    tint = Color.Black,
                )
            }
            Box {
                IconButton(onClick = onOverflowClick) {
                    Icon(
                        painter = painterResource(R.drawable.ic_more_vert),
                        contentDescription = stringResource(R.string.more),
                        modifier = Modifier.size(24.dp),
                        tint = Color.Black,
                    )
                }
                ContentEditorOverflowMenu(
                    expanded = overflowExpanded,
                    onDismiss = onOverflowDismiss,
                    onReset = onReset,
                    onCopyAll = onCopyAll,
                )
            }
        }
    }
}

@Composable
private fun ContentEditorOverflowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onReset: () -> Unit,
    onCopyAll: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.width(160.dp),
        shape = RoundedCornerShape(18.dp),
        containerColor = Color(NgTheme.colors.surfaceContainerHigh),
        tonalElevation = 0.dp,
        shadowElevation = NgTheme.effects.overlayElevationDp.dp,
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.reset), fontSize = 14.sp) },
            onClick = onReset,
            modifier = Modifier.height(48.dp),
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_restore),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.copy_all), fontSize = 14.sp) },
            onClick = onCopyAll,
            modifier = Modifier.height(48.dp),
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_copy),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            },
        )
    }
}

@Composable
private fun ContentEditorTextArea(
    document: EditorDocument,
    onEditorCreated: (ContentEditorView) -> Unit,
    onEditorTextChanged: (String) -> Unit,
    onApplyingDocument: (Boolean) -> Unit,
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            ContentEditorView(context).apply {
                subscribeEvent(ContentChangeEvent::class.java) { _, _ ->
                    onEditorTextChanged(text.toString())
                }
                onEditorCreated(this)
            }
        },
        onRelease = { editor ->
            onEditorTextChanged(editor.text.toString())
            editor.release()
        },
        update = { editText ->
            if (editText.tag != document.revision) {
                onApplyingDocument(true)
                editText.showDocument(document)
                onApplyingDocument(false)
            }
        },
    )
}

/** 复用书源编辑器内核，正文只按纯文本编辑，不启用代码语言或补全。 */
private class ContentEditorView(context: Context) : CodeEditor(context) {
    private var pendingOffset: Int? = null
    private var contentLayoutBusy = false

    init {
        setEditorLanguage(EmptyLanguage())
        colorScheme = SchemeDarcula().apply {
            setColor(EditorColorScheme.WHOLE_BACKGROUND, 0xFF1E1E1E.toInt())
            setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, 0xFF1E1E1E.toInt())
            setColor(EditorColorScheme.TEXT_NORMAL, android.graphics.Color.WHITE)
            setColor(EditorColorScheme.TEXT_SELECTED, android.graphics.Color.WHITE)
            setColor(EditorColorScheme.LINE_NUMBER, 0xFF888888.toInt())
            setColor(EditorColorScheme.LINE_NUMBER_CURRENT, 0xFF66E0D0.toInt())
            setColor(EditorColorScheme.SELECTION_INSERT, 0xFF66E0D0.toInt())
            setColor(EditorColorScheme.SELECTION_HANDLE, 0xFF66E0D0.toInt())
            setColor(EditorColorScheme.CURRENT_LINE, 0xFF263238.toInt())
        }
        setCursorWidth(2f * resources.displayMetrics.density)
        isLineNumberEnabled = true
        setPinLineNumber(true)
        isWordwrap = true
        setTextSize(16f)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        NgThemeRuntimeAssets.appTypeface(context)?.let {
            typefaceText = it
            typefaceLineNumber = it
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        }
        subscribeEvent(LayoutStateChangeEvent::class.java) { event, _ ->
            contentLayoutBusy = event.isLayoutBusy
            if (!contentLayoutBusy) post { restorePosition() }
        }
    }

    fun showDocument(document: EditorDocument) {
        pendingOffset = document.scrollOffset
        if (text.toString() != document.content) setText(document.content)
        tag = document.revision
        post { restorePosition() }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        post { restorePosition() }
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) post { restorePosition() }
    }

    private fun restorePosition() {
        if (isReleased || contentLayoutBusy || width == 0 || height == 0 ||
            isLayoutRequested || !hasWindowFocus()) return
        val offset = pendingOffset ?: return
        if (!requestFocus()) return
        pendingOffset = null
        val position = text.indexer.getCharPosition(offset.coerceIn(0, text.length))
        // 只在打开文档时定位；使用软换行后的实际行坐标，保留目标上下文。
        setSelection(position.line, position.column, false)
        val rowTop = layout.getCharLayoutOffset(position.line, position.column)[0] - rowHeight
        val targetY = (rowTop - height / 3f).toInt().coerceIn(0, scrollMaxY)
        scroller.startScroll(offsetX, targetY, 0, 0, 0)
        scroller.abortAnimation()
        invalidate()
    }
}

@Composable
private fun TitleEditorDialog(
    state: TitleEditorState,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (TitleEditorState) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        ReadConfigDialogSurface(
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        ) {
            ReadConfigDialogTitle(stringResource(R.string.edit))
            NgFormField(
                label = stringResource(R.string.title),
                value = state.title,
                onValueChange = onValueChange,
                modifier = Modifier.padding(top = 18.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                NgFormActionButton(
                    text = stringResource(R.string.ok),
                    onClick = { onConfirm(state) },
                    variant = NgButtonVariant.PRIMARY,
                )
            }
        }
    }
}
