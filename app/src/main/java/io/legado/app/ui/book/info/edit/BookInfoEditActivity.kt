package io.legado.app.ui.book.info.edit

import android.net.Uri
import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import io.legado.app.base.ComposeActivityBinding
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.BookType
import io.legado.app.constant.Theme
import io.legado.app.data.entities.Book
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.isAudio
import io.legado.app.help.book.isImage
import io.legado.app.help.book.isVideo
import io.legado.app.help.book.setBookTypeFromEditor
import io.legado.app.ui.book.changecover.ChangeCoverDialog
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.utils.FileUtils
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.SelectImageContract
import io.legado.app.utils.externalFiles
import io.legado.app.utils.inputStream
import io.legado.app.utils.readUri
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import splitties.init.appCtx
import java.io.FileOutputStream

class BookInfoEditActivity :
    VMBaseActivity<ComposeActivityBinding, BookInfoEditViewModel>(toolBarTheme = Theme.Dark),
    ChangeCoverDialog.CallBack {

    private val selectCover = registerForActivityResult(SelectImageContract()) {
        it.uri?.let(::coverChangeTo)
    }

    override val binding by viewBinding(ComposeActivityBinding::inflate)
    override val viewModel by viewModels<BookInfoEditViewModel>()
    override val bindNgToolbarMenu: Boolean = false
    private var uiState by mutableStateOf(BookInfoEditUiState())

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        initContent()
        viewModel.bookData.observe(this, ::upView)
        if (viewModel.bookData.value == null) {
            intent.getStringExtra("bookUrl")?.let(viewModel::loadBook)
        }
    }

    private fun initContent() {
        binding.composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
        )
        binding.composeView.setContent {
            NgAppTheme {
                BookInfoEditScreen(
                    state = uiState,
                    onEvent = ::handleUiEvent,
                )
            }
        }
    }

    private fun handleUiEvent(event: BookInfoEditUiEvent) {
        when (event) {
            BookInfoEditUiEvent.Back -> onBackPressedDispatcher.onBackPressed()
            BookInfoEditUiEvent.Save -> saveData()
            is BookInfoEditUiEvent.NameChange -> uiState = uiState.copy(name = event.value)
            is BookInfoEditUiEvent.AuthorChange -> uiState = uiState.copy(author = event.value)
            is BookInfoEditUiEvent.TypeChange -> uiState = uiState.copy(
                typeIndex = event.index,
                typeSelected = true,
            )
            is BookInfoEditUiEvent.CoverUrlChange -> uiState = uiState.copy(coverUrl = event.value)
            is BookInfoEditUiEvent.IntroChange -> uiState = uiState.copy(intro = event.value)
            BookInfoEditUiEvent.SelectLocalCover -> selectCover.launch(null)
            BookInfoEditUiEvent.ChangeCover -> showChangeCoverDialog()
            BookInfoEditUiEvent.RefreshCover -> refreshCover()
        }
    }

    private fun showChangeCoverDialog() {
        viewModel.book?.let {
            val selectedType = if (uiState.typeSelected) {
                when (uiState.typeIndex) {
                    BOOK_TYPE_VIDEO -> BookType.video
                    BOOK_TYPE_IMAGE -> BookType.image
                    BOOK_TYPE_AUDIO -> BookType.audio
                    else -> BookType.text
                }
            } else null
            showDialogFragment(ChangeCoverDialog(it, selectedType))
        }
    }

    private fun upView(book: Book) {
        uiState = BookInfoEditUiState(
            book = book.copy(),
            name = book.name,
            author = book.author,
            typeIndex = when {
                book.isVideo -> BOOK_TYPE_VIDEO
                book.isImage -> BOOK_TYPE_IMAGE
                book.isAudio -> BOOK_TYPE_AUDIO
                else -> BOOK_TYPE_TEXT
            },
            coverUrl = book.getDisplayCover().orEmpty(),
            intro = book.getDisplayIntro().orEmpty(),
            coverRevision = uiState.coverRevision + 1,
        )
    }

    private fun refreshCover() {
        coverChangeTo(uiState.coverUrl)
    }

    private fun saveData() {
        val book = viewModel.book ?: return
        val oldBook = book.copy()
        book.name = uiState.name
        book.author = uiState.author
        val bookType = when (uiState.typeIndex) {
            BOOK_TYPE_VIDEO -> BookType.video
            BOOK_TYPE_IMAGE -> BookType.image
            BOOK_TYPE_AUDIO -> BookType.audio
            else -> BookType.text
        }
        book.setBookTypeFromEditor(bookType, uiState.typeSelected)
        book.customCoverUrl = if (uiState.coverUrl == book.coverUrl) null else uiState.coverUrl
        book.customIntro = if (uiState.intro == book.intro) null else uiState.intro
        BookHelp.updateCacheFolder(oldBook, book)
        viewModel.saveBook(book) {
            setResult(RESULT_OK)
            finish()
        }
    }

    override fun coverChangeTo(coverUrl: String) {
        val book = viewModel.book ?: return
        book.customCoverUrl = coverUrl
        uiState = uiState.copy(
            book = book.copy(),
            coverUrl = coverUrl,
            coverRevision = uiState.coverRevision + 1,
        )
    }

    private fun coverChangeTo(uri: Uri) {
        if (uri.scheme?.lowercase() in listOf("http", "https")) {
            coverChangeTo(uri.toString())
            return
        }
        readUri(uri) { fileDoc, inputStream ->
            runCatching {
                inputStream.use {
                    val suffix = if (fileDoc.name.contains(".9.png", true)) {
                        ".9.png"
                    } else {
                        "." + fileDoc.name.substringAfterLast(".")
                    }
                    val fileName = uri.inputStream(this).getOrThrow().use {
                        MD5Utils.md5Encode(it) + suffix
                    }
                    val file = FileUtils.createFileIfNotExist(
                        externalFiles,
                        "covers",
                        fileName,
                    )
                    FileOutputStream(file).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                    coverChangeTo(file.absolutePath)
                }
            }.onFailure {
                appCtx.toastOnUi(it.localizedMessage)
            }
        }
    }
}
