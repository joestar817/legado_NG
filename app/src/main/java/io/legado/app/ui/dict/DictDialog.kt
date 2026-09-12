package io.legado.app.ui.dict

import android.os.Build
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import android.view.ViewGroup
import android.view.textclassifier.TextClassifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import io.legado.app.R
import io.legado.app.base.BaseComposeDialogFragment
import io.legado.app.data.entities.DictRule
import io.legado.app.help.GlideImageGetter
import io.legado.app.help.TextViewTagHandler
import io.legado.app.ui.book.read.ReadDrawerStyle
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgInterfaceFontMarkwonPlugin
import io.legado.app.ui.widget.dialog.PhotoDialog
import io.legado.app.ui.widget.dialog.applyNgDialogWindow
import io.legado.app.ui.widget.text.ScrollTextView
import io.legado.app.utils.setHtml
import io.legado.app.utils.setMarkdown
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.glide.GlideImagesPlugin
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 词典。窗口与标签使用 Compose，富文本渲染继续复用现有 ScrollTextView 能力。 */
class DictDialog() : BaseComposeDialogFragment() {

    constructor(word: String) : this() {
        arguments = Bundle().apply {
            putString("word", word)
        }
    }

    private val viewModel by viewModels<DictViewModel>()
    private var word = ""
    private var rules by mutableStateOf(emptyList<DictRule>())
    private var selectedIndex by mutableIntStateOf(-1)
    private var loading by mutableStateOf(false)
    private var dictTextView: ScrollTextView? = null
    private var pendingContent: Pair<DictRule, String>? = null
    private var glideImageGetter: GlideImageGetter? = null

    override fun onStart() {
        super.onStart()
        applyNgDialogWindow(marginDp = 24, dimAmount = 0.16f)
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        word = arguments?.getString("word").orEmpty()
        if (word.isBlank()) {
            toastOnUi(R.string.cannot_empty)
            dismiss()
            return
        }
        (view as ComposeView).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                NgAppTheme(
                    snapshot = ReadDrawerStyle.themeSnapshot(requireContext()),
                    updateSystemBars = false,
                ) {
                    DictDialogContent(
                        word = word,
                        rules = rules,
                        selectedIndex = selectedIndex,
                        loading = loading,
                        onRuleSelected = ::selectRule,
                        onTextViewReady = ::attachTextView,
                    )
                }
            }
        }
        viewModel.initData { enabledRules ->
            rules = enabledRules
            if (enabledRules.isNotEmpty()) selectRule(0)
        }
    }

    private fun attachTextView(textView: ScrollTextView) {
        if (dictTextView !== textView) {
            glideImageGetter?.clear()
            glideImageGetter = null
            dictTextView = textView.apply {
                movementMethod = LinkMovementMethod()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setTextClassifier(TextClassifier.NO_OP)
                }
            }
        }
        pendingContent?.let { (rule, content) ->
            pendingContent = null
            renderContent(rule, content)
        }
    }

    private fun selectRule(index: Int) {
        val rule = rules.getOrNull(index) ?: return
        if (selectedIndex == index && dictTextView?.text?.isNotEmpty() == true) return
        selectedIndex = index
        loading = true
        viewModel.dict(rule, word) { content ->
            loading = false
            if (dictTextView == null) {
                pendingContent = rule to content
            } else {
                renderContent(rule, content)
            }
        }
    }

    private fun renderContent(dictRule: DictRule, content: String) {
        val textView = dictTextView ?: return
        val contentTrimmed = content.trimStart()
        if (contentTrimmed.startsWith("<md>")) {
            val lastIndex = contentTrimmed.lastIndexOf("<")
            if (lastIndex < 4) {
                textView.text = contentTrimmed
                return
            }
            val markdownSource = contentTrimmed.substring(4, lastIndex)
            viewLifecycleOwner.lifecycleScope.launch {
                val availableWidth = textView.width
                    .takeIf { it > 0 }
                    ?.minus(textView.paddingLeft + textView.paddingRight)
                    ?: (resources.displayMetrics.widthPixels - 64 * resources.displayMetrics.density)
                        .toInt()
                lateinit var markwon: Markwon
                val markdown = withContext(IO) {
                    markwon = Markwon.builder(requireContext())
                        .usePlugin(NgInterfaceFontMarkwonPlugin(requireContext()))
                        .usePlugin(
                            GlideImagesPlugin.create(
                                Glide.with(requireContext())
                                    .applyDefaultRequestOptions(
                                        RequestOptions().override(availableWidth).encodeQuality(88)
                                    )
                            )
                        )
                        .usePlugin(HtmlPlugin.create())
                        .usePlugin(TablePlugin.create(requireContext()))
                        .build()
                    markwon.toMarkdown(markdownSource)
                }
                textView.setMarkdown(
                    markwon,
                    markdown,
                    imgOnLongClickListener = { source ->
                        showDialogFragment(PhotoDialog(source))
                    },
                )
            }
            return
        }
        val imageGetter = glideImageGetter ?: GlideImageGetter(
            requireContext(),
            textView,
            viewLifecycleOwner.lifecycle,
            (resources.displayMetrics.widthPixels - 64 * resources.displayMetrics.density).toInt(),
        ).also { glideImageGetter = it }
        val tagHandler = TextViewTagHandler(object : TextViewTagHandler.OnButtonClickListener {
            override fun onButtonClick(name: String, click: String) {
                viewModel.onButtonClick(dictRule, "button $name", click)
            }
        })
        textView.setHtml(
            content,
            imageGetter,
            tagHandler,
            imgOnLongClickListener = { source ->
                showDialogFragment(PhotoDialog(source))
            },
            imgOnClickListener = { click ->
                viewModel.onButtonClick(dictRule, "image", click)
            },
        )
    }

    override fun onDestroyView() {
        glideImageGetter?.clear()
        glideImageGetter = null
        dictTextView = null
        super.onDestroyView()
    }
}
