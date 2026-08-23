package io.legado.app.ui.book.read

import android.app.Application
import android.content.DialogInterface
import android.graphics.Color.TRANSPARENT
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
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
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.platform.LocalDensity
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
import androidx.core.widget.addTextChangedListener
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
import io.legado.app.lib.theme.view.ThemeEditText
import io.legado.app.model.ReadBook
import io.legado.app.model.webBook.WebBook
import io.legado.app.ui.book.read.config.ReadConfigDialogSurface
import io.legado.app.ui.book.read.config.ReadConfigDialogTitle
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.compose.NgFormActionButton
import io.legado.app.ui.design.components.compose.NgFormField
import io.legado.app.ui.design.components.compose.NgGlassDefaults
import io.legado.app.ui.design.components.compose.NgGlassSurface
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.utils.applyTint
import io.legado.app.utils.sendToClip
import io.legado.app.utils.setLayout
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 内容编辑
 */
class ContentEditDialog : DialogFragment() {

    private val viewModel by viewModels<ContentEditViewModel>()

    private var chapterTitle by mutableStateOf("")
    private var isLoading by mutableStateOf(false)
    private var editorDocument by mutableStateOf(EditorDocument())
    private var titleEditorState by mutableStateOf<TitleEditorState?>(null)
    private var showOverflowMenu by mutableStateOf(false)

    private var editorRevision = 0
    private var applyingEditorDocument = false
    private var editorView: ThemeEditText? = null

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
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        setBackgroundColor(TRANSPARENT)
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        chapterTitle = ReadBook.curTextChapter?.title.orEmpty()
        viewModel.loadStateLiveData.observe(viewLifecycleOwner) {
            isLoading = it
        }
        viewModel.initContent { content ->
            showEditorContent(content, ReadBook.durChapterPos)
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
            setBackgroundDrawable(ColorDrawable(TRANSPARENT))
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        setLayout(1f, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        save()
    }

    override fun onDestroyView() {
        editorView?.text?.toString()?.let { viewModel.draftContent = it }
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
                appDb.bookChapterDao.getChapter(book.bookUrl, ReadBook.durChapterIndex)
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
            ReadBook.loadContent(ReadBook.durChapterIndex, resetPageOffset = false)
        }
    }

    private fun resetContent() {
        showOverflowMenu = false
        viewModel.initContent(true) { content ->
            showEditorContent(content)
            ReadBook.loadContent(ReadBook.durChapterIndex, resetPageOffset = false)
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
                .getChapter(book.bookUrl, ReadBook.durChapterIndex)
                ?: return@async
            BookHelp.saveText(book, chapter, content)
            ReadBook.loadContent(ReadBook.durChapterIndex, resetPageOffset = false)
        }
    }

    class ContentEditViewModel(application: Application) : BaseViewModel(application) {
        val loadStateLiveData = MutableLiveData<Boolean>()
        var content: String? = null
        var draftContent: String? = null

        fun initContent(reset: Boolean = false, success: (String) -> Unit) {
            execute {
                val book = ReadBook.book ?: return@execute null
                val chapter = appDb.bookChapterDao
                    .getChapter(book.bookUrl, ReadBook.durChapterIndex)
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
    onSave: () -> Unit,
    onOverflowClick: () -> Unit,
    onOverflowDismiss: () -> Unit,
    onReset: () -> Unit,
    onCopyAll: () -> Unit,
    onEditorCreated: (ThemeEditText) -> Unit,
    onEditorTextChanged: (String) -> Unit,
    onApplyingDocument: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(NgTheme.colors.background))
            .padding(8.dp),
    ) {
        ContentEditorTopBar(
            chapterTitle = chapterTitle,
            overflowExpanded = overflowExpanded,
            onTitleClick = onTitleClick,
            onSave = onSave,
            onOverflowClick = onOverflowClick,
            onOverflowDismiss = onOverflowDismiss,
            onReset = onReset,
            onCopyAll = onCopyAll,
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            NgGlassSurface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(18.dp),
                style = readFloatingGlassStyle().copy(shadowElevation = 0.dp),
            ) {
                ContentEditorTextArea(
                    document = document,
                    onEditorCreated = onEditorCreated,
                    onEditorTextChanged = onEditorTextChanged,
                    onApplyingDocument = onApplyingDocument,
                )
            }
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
    onSave: () -> Unit,
    onOverflowClick: () -> Unit,
    onOverflowDismiss: () -> Unit,
    onReset: () -> Unit,
    onCopyAll: () -> Unit,
) {
    NgGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),
        shape = RoundedCornerShape(18.dp),
        style = NgGlassDefaults.style(containerAlpha = NgTheme.effects.dialogAlpha),
        contentPadding = PaddingValues(start = 16.dp, end = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
                    color = Color(NgTheme.colors.onSurface),
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
                    tint = Color(NgTheme.colors.onSurface),
                )
            }
            Box {
                IconButton(onClick = onOverflowClick) {
                    Icon(
                        painter = painterResource(R.drawable.ic_more_vert),
                        contentDescription = stringResource(R.string.more),
                        modifier = Modifier.size(24.dp),
                        tint = Color(NgTheme.colors.onSurface),
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
    onEditorCreated: (ThemeEditText) -> Unit,
    onEditorTextChanged: (String) -> Unit,
    onApplyingDocument: (Boolean) -> Unit,
) {
    val paddingPx = with(LocalDensity.current) { 12.dp.roundToPx() }
    val colors = NgTheme.colors
    val isDark = NgTheme.snapshot.isDark
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            ThemeEditText(context).apply {
                gravity = Gravity.TOP or Gravity.START
                inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                setHorizontallyScrolling(false)
                isVerticalScrollBarEnabled = true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
                }
                applyTint(colors.primary, isDark)
                background = null
                setTextColor(colors.onSurface)
                setHintTextColor(colors.onSurfaceVariant)
                setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
                addTextChangedListener(
                    afterTextChanged = { onEditorTextChanged(it?.toString().orEmpty()) }
                )
                onEditorCreated(this)
            }
        },
        update = { editText ->
            editText.applyTint(colors.primary, isDark)
            editText.setTextColor(colors.onSurface)
            editText.setHintTextColor(colors.onSurfaceVariant)
            if (editText.tag != document.revision) {
                onApplyingDocument(true)
                if (editText.text?.toString() != document.content) {
                    editText.setText(document.content)
                }
                editText.tag = document.revision
                onApplyingDocument(false)
                document.scrollOffset?.let { requestedOffset ->
                    editText.post {
                        val textLayout = editText.layout ?: return@post
                        val offset = requestedOffset.coerceIn(0, editText.text?.length ?: 0)
                        val lineIndex = textLayout.getLineForOffset(offset)
                        editText.scrollTo(0, textLayout.getLineTop(lineIndex))
                    }
                }
            }
        },
    )
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
