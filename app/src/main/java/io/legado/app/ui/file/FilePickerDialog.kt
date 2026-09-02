package io.legado.app.ui.file

import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import io.legado.app.R
import io.legado.app.base.BaseComposeDialogFragment
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.file.BuiltInFilePickerActivity.Companion.FILE
import io.legado.app.ui.widget.dialog.applyNgDialogWindow
import io.legado.app.ui.widget.dialog.ngDialogMaxHeight
import io.legado.app.utils.FileUtils
import io.legado.app.utils.toastOnUi
import java.io.File

class FilePickerDialog : BaseComposeDialogFragment() {

    companion object {
        const val tag = "FileChooserDialog"

        fun show(
            manager: FragmentManager,
            mode: Int = FILE,
            title: String? = null,
            initPath: String? = null,
            isShowHideDir: Boolean = false,
            allowExtensions: Array<String>? = null,
        ) {
            FilePickerDialog().apply {
                arguments = Bundle().apply {
                    putInt("mode", mode)
                    putString("title", title)
                    putBoolean("isShowHideDir", isShowHideDir)
                    putString("initPath", initPath)
                    putStringArray("allowExtensions", allowExtensions)
                }
            }.show(manager, tag)
        }
    }

    private val viewModel by viewModels<FilePickerViewModel>()
    private var entries by mutableStateOf<List<FilePickerEntry>>(emptyList())
    private var breadcrumbs by mutableStateOf<List<File>>(emptyList())
    private var selectedFile by mutableStateOf<File?>(null)
    private var createFolderDialogVisible by mutableStateOf(false)
    private var folderNameDraft by mutableStateOf("")

    override fun onStart() {
        super.onStart()
        applyNgDialogWindow(height = ngDialogMaxHeight(0.8f))
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        viewModel.filesLiveData.observe(viewLifecycleOwner) { newEntries ->
            selectedFile = null
            entries = newEntries
        }
        viewModel.initData(arguments)
        breadcrumbs = viewModel.subDocs.toList()

        (view as ComposeView).apply {
            setBackgroundColor(AndroidColor.TRANSPARENT)
            layoutParams = layoutParams.apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                height = ViewGroup.LayoutParams.MATCH_PARENT
            }
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
            )
            setContent {
                NgAppTheme(updateSystemBars = false) {
                    FilePickerScreen(
                        title = arguments?.getString("title") ?: getString(
                            if (viewModel.isSelectDir) {
                                R.string.folder_chooser
                            } else {
                                R.string.file_chooser
                            },
                        ),
                        breadcrumbs = breadcrumbs,
                        entries = entries,
                        currentDirectory = viewModel.lastDir,
                        directoryMode = viewModel.isSelectDir,
                        selectedFile = selectedFile,
                        isFileAllowed = ::isFileAllowed,
                        onRootClick = ::openRoot,
                        onBreadcrumbClick = ::openBreadcrumb,
                        onEntryClick = ::openEntry,
                        onCreateFolderClick = ::showCreateFolderDialog,
                        onConfirmClick = ::confirmSelection,
                    )
                    if (createFolderDialogVisible) {
                        FilePickerCreateFolderDialog(
                            value = folderNameDraft,
                            onValueChange = { folderNameDraft = it },
                            onDismissRequest = ::dismissCreateFolderDialog,
                            onConfirm = ::createFolder,
                        )
                    }
                }
            }
        }
    }

    private fun openRoot() {
        viewModel.subDocs.clear()
        breadcrumbs = emptyList()
        selectedFile = null
        viewModel.upFiles(viewModel.rootDoc)
    }

    private fun openBreadcrumb(index: Int) {
        viewModel.subDocs = viewModel.subDocs
            .take(index + 1)
            .toMutableList()
        breadcrumbs = viewModel.subDocs.toList()
        selectedFile = null
        viewModel.upFiles(viewModel.subDocs.lastOrNull() ?: viewModel.rootDoc)
    }

    private fun openEntry(entry: FilePickerEntry) {
        val file = entry.file
        when {
            breadcrumbs.isNotEmpty() && file == viewModel.lastDir -> {
                viewModel.subDocs.removeLastOrNull()
                breadcrumbs = viewModel.subDocs.toList()
                selectedFile = null
                viewModel.upFiles(viewModel.subDocs.lastOrNull() ?: viewModel.rootDoc)
            }
            entry.isDirectory -> {
                viewModel.subDocs.add(file)
                breadcrumbs = viewModel.subDocs.toList()
                selectedFile = null
                viewModel.upFiles(file)
            }
            viewModel.isSelectFile && isFileAllowed(entry) -> {
                selectedFile = file
            }
        }
    }

    private fun isFileAllowed(entry: FilePickerEntry): Boolean {
        val extensions = viewModel.allowExtensions
        return extensions.isNullOrEmpty() || entry.extension in extensions
    }

    private fun confirmSelection() {
        if (viewModel.isSelectDir) {
            viewModel.lastDir?.let { directory ->
                setResultData(directory.path)
                dismissAllowingStateLoss()
            }
            return
        }
        val file = selectedFile
        if (file == null) {
            toastOnUi("请选择文件")
        } else {
            setResultData(file.path)
            dismissAllowingStateLoss()
        }
    }

    private fun showCreateFolderDialog() {
        folderNameDraft = ""
        createFolderDialogVisible = true
    }

    private fun dismissCreateFolderDialog() {
        createFolderDialogVisible = false
        folderNameDraft = ""
    }

    private fun createFolder() {
        val name = folderNameDraft
        dismissCreateFolderDialog()
        if (name.isBlank()) {
            toastOnUi("文件夹名不能为空")
        } else {
            viewModel.createFolder(name.trim())
        }
    }

    private fun setResultData(path: String) {
        val data = Intent().setData(Uri.fromFile(File(path)))
        (parentFragment as? CallBack)?.onResult(data)
        (activity as? CallBack)?.onResult(data)
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        activity?.finish()
    }

    interface CallBack {
        fun onResult(data: Intent)
    }
}
