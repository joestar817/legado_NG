package io.legado.app.ui.about

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseComposeDialogFragment
import io.legado.app.help.update.AppUpdate
import io.legado.app.model.Download
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.widget.dialog.MarkdownTextDialogContent
import io.legado.app.ui.widget.dialog.PhotoDialog
import io.legado.app.ui.widget.dialog.RenderedMarkdown
import io.legado.app.ui.widget.dialog.applyNgDialogWindow
import io.legado.app.ui.widget.dialog.ngDialogMaxHeight
import io.legado.app.ui.widget.dialog.renderMarkdownContent
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 更新详情使用居中 NG 弹窗，正文与只读 Markdown 抽屉共享同一布局。 */
class UpdateDialog() : BaseComposeDialogFragment() {

    constructor(updateInfo: AppUpdate.UpdateInfo) : this() {
        arguments = Bundle().apply {
            putString(ARG_NEW_VERSION, updateInfo.tagName)
            putString(ARG_UPDATE_BODY, updateInfo.updateLog)
            putStringArrayList(ARG_URLS, ArrayList(updateInfo.downloadUrls))
            putString(ARG_NAME, updateInfo.fileName)
            putLong(ARG_FILE_SIZE, updateInfo.fileSize)
            putString(ARG_SHA256, updateInfo.sha256)
        }
    }

    private var renderedMarkdown by mutableStateOf<RenderedMarkdown?>(null)

    override fun onStart() {
        super.onStart()
        applyNgDialogWindow(height = ngDialogMaxHeight(UPDATE_DIALOG_HEIGHT_RATIO))
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        val updateBody = arguments?.getString(ARG_UPDATE_BODY)
        if (updateBody == null) {
            toastOnUi("没有数据")
            dismiss()
            return
        }
        (view as ComposeView).apply {
            setBackgroundColor(AndroidColor.TRANSPARENT)
            layoutParams = layoutParams.apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                height = ViewGroup.LayoutParams.MATCH_PARENT
            }
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                NgAppTheme(updateSystemBars = false) {
                    MarkdownTextDialogContent(
                        title = arguments?.getString(ARG_NEW_VERSION).orEmpty(),
                        renderedMarkdown = renderedMarkdown,
                        onImageLongClick = { source ->
                            showDialogFragment(PhotoDialog(source))
                        },
                        bottomActionText = stringResource(R.string.action_download),
                        onBottomAction = ::download,
                    )
                }
            }
        }
        renderMarkdown(updateBody)
    }

    private fun renderMarkdown(content: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val rendered = renderMarkdownContent(requireContext(), content)
            if (isActive) renderedMarkdown = rendered
        }
    }

    private fun download() {
        val urls = arguments?.getStringArrayList(ARG_URLS)
        val name = arguments?.getString(ARG_NAME)
        val fileSize = arguments?.getLong(ARG_FILE_SIZE) ?: 0L
        val sha256 = arguments?.getString(ARG_SHA256)
        if (!urls.isNullOrEmpty() && name != null && sha256 != null) {
            Download.start(requireContext(), urls, name, fileSize, sha256)
            toastOnUi(R.string.download_start)
        }
    }

    private companion object {
        const val ARG_NEW_VERSION = "newVersion"
        const val ARG_UPDATE_BODY = "updateBody"
        const val ARG_URLS = "urls"
        const val ARG_NAME = "name"
        const val ARG_FILE_SIZE = "fileSize"
        const val ARG_SHA256 = "sha256"
        const val UPDATE_DIALOG_HEIGHT_RATIO = 0.82f
    }
}
