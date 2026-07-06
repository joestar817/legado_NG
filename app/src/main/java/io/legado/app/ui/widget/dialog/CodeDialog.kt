package io.legado.app.ui.widget.dialog

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.databinding.DialogCodeViewBinding
import io.legado.app.help.IntentData
import io.legado.app.lib.theme.accentColor
import io.legado.app.ui.widget.code.addDebugLogPattern
import io.legado.app.ui.widget.code.addJsPattern
import io.legado.app.ui.widget.code.addJsonPattern
import io.legado.app.ui.widget.code.addLegadoPattern
import io.legado.app.utils.applyTint
import io.legado.app.utils.disableEdit
import io.legado.app.utils.exportTextContent
import io.legado.app.utils.tintTitle
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CodeDialog() : BaseDialogFragment(R.layout.dialog_code_view) {

    constructor(
        code: String,
        disableEdit: Boolean = true,
        requestId: String? = null,
        title: String? = null,
        highlightMode: HighlightMode = HighlightMode.Default,
        exportCode: String? = null,
        exportFilePrefix: String = "legado-code"
    ) : this() {
        arguments = Bundle().apply {
            putBoolean("disableEdit", disableEdit)
            putString("code", IntentData.put(code))
            exportCode?.let { putString("exportCode", IntentData.put(it)) }
            putString("exportFilePrefix", exportFilePrefix)
            putString("requestId", requestId)
            putString("title", title)
            putString("highlightMode", highlightMode.name)
        }
    }

    val binding by viewBinding(DialogCodeViewBinding::bind)
    private var originalContent: String = ""
    private var exportOverrideContent: String? = null

    override fun onStart() {
        super.onStart()
        applyNgDialogWindow(height = ngDialogMaxHeight())
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        view.setBackgroundResource(R.drawable.ng_bg_dialog)
        arguments?.getString("title")?.let {
            binding.toolBar.title = it
        }
        if (arguments?.getBoolean("disableEdit") == true) {
            if (arguments?.getString("title").isNullOrBlank()) {
                binding.toolBar.title = "code view"
            }
            binding.codeView.setMaxHighlightLength(64 * 1024)
            binding.codeView.disableEdit()
            initCopyMenu()
        } else {
            initMenu()
        }
        when (arguments?.getString("highlightMode")) {
            HighlightMode.DebugLog.name -> binding.codeView.addDebugLogPattern()
            else -> {
                binding.codeView.addLegadoPattern()
                binding.codeView.addJsonPattern()
                binding.codeView.addJsPattern()
            }
        }
        arguments?.getString("code")?.let {
            originalContent = IntentData.get<String>(it).orEmpty()
            binding.codeView.setTextHighlighted(displayContent(originalContent))
        }
    }

    private fun initMenu() {
        binding.toolBar.inflateMenu(R.menu.code_edit)
        binding.toolBar.menu.applyTint(requireContext())
        binding.toolBar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.menu_save -> {
                    binding.codeView.text?.toString()?.let { code ->
                        val requestId = arguments?.getString("requestId")
                        (parentFragment as? Callback)?.onCodeSave(code, requestId)
                            ?: (activity as? Callback)?.onCodeSave(code, requestId)
                    }
                    dismiss()
                }
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun initCopyMenu() {
        binding.toolBar.inflateMenu(R.menu.code_view_log)
        binding.toolBar.menu.tintTitle(R.id.menu_copy_content, requireContext().accentColor)
        binding.toolBar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.menu_copy_content -> {
                    lifecycleScope.launch {
                        val content = withContext(Dispatchers.Default) {
                            exportContent()
                        }
                        if (isAdded) {
                            requireContext().exportTextContent(
                                content,
                                filePrefix = arguments?.getString("exportFilePrefix")
                                    ?: "legado-code"
                            )
                        }
                    }
                }
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun displayContent(content: String): String {
        val disableEdit = arguments?.getBoolean("disableEdit") == true
        if (!disableEdit || content.length <= READ_ONLY_PREVIEW_MAX_LENGTH) {
            return content
        }
        return content.take(READ_ONLY_PREVIEW_MAX_LENGTH) +
                getString(
                    R.string.large_text_preview_suffix,
                    READ_ONLY_PREVIEW_MAX_LENGTH,
                    content.length
                )
    }

    private fun exportContent(): String {
        val requestId = arguments?.getString("requestId")
        (parentFragment as? ExportCallback)?.onCodeExport(requestId)?.let {
            return it
        }
        (activity as? ExportCallback)?.onCodeExport(requestId)?.let {
            return it
        }
        exportOverrideContent?.let {
            return it
        }
        arguments?.getString("exportCode")?.let {
            return IntentData.get<String>(it).orEmpty().also { content ->
                exportOverrideContent = content
            }
        }
        originalContent.takeIf { it.isNotEmpty() }?.let { return it }
        return binding.codeView.text?.toString().orEmpty()
    }


    interface Callback {

        fun onCodeSave(code: String, requestId: String?)

    }

    interface ExportCallback {

        fun onCodeExport(requestId: String?): String?

    }

    enum class HighlightMode {
        Default,
        DebugLog
    }

    private companion object {
        const val READ_ONLY_PREVIEW_MAX_LENGTH = 48 * 1024
    }

}
