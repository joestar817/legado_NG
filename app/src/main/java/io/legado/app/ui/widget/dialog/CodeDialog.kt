package io.legado.app.ui.widget.dialog

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.databinding.DialogCodeViewBinding
import io.legado.app.help.IntentData
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.widget.code.addDebugLogPattern
import io.legado.app.ui.widget.code.addJsPattern
import io.legado.app.ui.widget.code.addJsonPattern
import io.legado.app.ui.widget.code.addLegadoPattern
import io.legado.app.utils.applyTint
import io.legado.app.utils.disableEdit
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding

class CodeDialog() : BaseDialogFragment(R.layout.dialog_code_view) {

    constructor(
        code: String,
        disableEdit: Boolean = true,
        requestId: String? = null,
        title: String? = null,
        highlightMode: HighlightMode = HighlightMode.Default
    ) : this() {
        arguments = Bundle().apply {
            putBoolean("disableEdit", disableEdit)
            putString("code", IntentData.put(code))
            putString("requestId", requestId)
            putString("title", title)
            putString("highlightMode", highlightMode.name)
        }
    }

    val binding by viewBinding(DialogCodeViewBinding::bind)

    override fun onStart() {
        super.onStart()
        setLayout(1f, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolBar.setBackgroundColor(primaryColor)
        arguments?.getString("title")?.let {
            binding.toolBar.title = it
        }
        if (arguments?.getBoolean("disableEdit") == true) {
            if (arguments?.getString("title").isNullOrBlank()) {
                binding.toolBar.title = "code view"
            }
            binding.codeView.setMaxHighlightLength(64 * 1024)
            binding.codeView.disableEdit()
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
            binding.codeView.setTextHighlighted(IntentData.get(it))
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


    interface Callback {

        fun onCodeSave(code: String, requestId: String?)

    }

    enum class HighlightMode {
        Default,
        DebugLog
    }

}
