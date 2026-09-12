package io.legado.app.ui.widget.dialog

import android.os.Build
import android.os.Bundle
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.View
import android.view.textclassifier.TextClassifier
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.databinding.DialogTextViewBinding
import io.legado.app.help.CacheManager
import io.legado.app.help.IntentData
import io.legado.app.help.config.NgThemeRuntimeAssets
import io.legado.app.lib.theme.accentColor
import io.legado.app.ui.code.CodeEditActivity
import io.legado.app.ui.design.theme.NgInterfaceFontMarkwonPlugin
import io.legado.app.utils.applyTint
import io.legado.app.utils.setHtml
import io.legado.app.utils.setMarkdown
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.tintTitle
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.core.spans.EmphasisSpan
import io.noties.markwon.image.glide.GlideImagesPlugin
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class TextDialog() : BaseDialogFragment(R.layout.dialog_text_view) {

    enum class Mode {
        MD, HTML, TEXT
    }

    constructor(
        title: String,
        content: String?,
        mode: Mode = Mode.TEXT,
        time: Long = 0,
        autoClose: Boolean = false
    ) : this() {
        arguments = Bundle().apply {
            putString("title", title)
            putString("content", IntentData.put(content))
            putString("mode", mode.name)
            putLong("time", time)
        }
        isCancelable = false
        this.autoClose = autoClose
    }

    private val binding by viewBinding(DialogTextViewBinding::bind)
    private var time = 0L
    private var autoClose: Boolean = false

    override fun onStart() {
        super.onStart()
        applyNgDialogWindow(height = ngDialogMaxHeight())
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        view.setBackgroundResource(R.drawable.ng_bg_dialog)
        binding.toolBar.inflateMenu(R.menu.dialog_text)
        binding.toolBar.menu.applyTint(requireContext())
        binding.toolBar.menu.tintTitle(R.id.menu_close, requireContext().accentColor)
        arguments?.let {
            val title = it.getString("title")
            binding.toolBar.title = title
            NgThemeRuntimeAssets.applyToolbarTypeface(binding.toolBar)
            val content = IntentData.get(it.getString("content")) ?: ""
            val mode = it.getString("mode")
            when (mode) {
                Mode.MD.name -> viewLifecycleOwner.lifecycleScope.launch {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        binding.textView.setTextClassifier(TextClassifier.NO_OP)
                    }
                    val markwon: Markwon
                    val markdown = withContext(IO) {
                        markwon = Markwon.builder(requireContext())
                            .usePlugin(NgInterfaceFontMarkwonPlugin(requireContext()))
                            .usePlugin(GlideImagesPlugin.create(Glide.with(requireContext())))
                            .usePlugin(HtmlPlugin.create())
                            .usePlugin(TablePlugin.create(requireContext()))
                            .build()
                        markwon.toMarkdown(content).withoutItalic()
                    }
                    binding.textView.setMarkdown(
                        markwon,
                        markdown,
                        imgOnLongClickListener = { source  ->
                            showDialogFragment(PhotoDialog(source))
                        }
                    )
                }

                Mode.HTML.name -> binding.textView.setHtml(content)
                else -> {
                    if (content.length >= 32 * 1024) {
                        val truncatedContent =
                            content.take(32 * 1024) + "\n\n数据太大，无法全部显示…"
                        binding.textView.text = truncatedContent
                    } else {
                        binding.textView.text = content
                    }
                }
            }
            binding.toolBar.setOnMenuItemClickListener { menu ->
                when (menu.itemId) {
                    R.id.menu_close -> dismissAllowingStateLoss()
                    R.id.menu_fullscreen_edit -> {
                        val cacheKey = "code_text_${System.currentTimeMillis()}"
                        CacheManager.putMemory(cacheKey, content)
                        startActivity<CodeEditActivity> {
                            putExtra("cacheKey", cacheKey)
                            putExtra("title", title)
                            putExtra("languageName", if (mode == Mode.MD.name) "text.html.markdown" else "text.html.basic")
                        }
                    }
                }
                true
            }
            time = it.getLong("time", 0L)
        }
        if (time > 0) {
            binding.badgeView.setBadgeCount((time / 1000).toInt())
            lifecycleScope.launch {
                while (time > 0) {
                    delay(1000)
                    time -= 1000
                    binding.badgeView.setBadgeCount((time / 1000).toInt())
                    if (time <= 0) {
                        view.post {
                            dialog?.setCancelable(true)
                            if (autoClose) dialog?.cancel()
                        }
                    }
                }
            }
        } else {
            view.post {
                dialog?.setCancelable(true)
            }
        }
    }

    private fun Spanned.withoutItalic(): Spanned {
        val spannable = SpannableString(this)
        getSpans(0, length, EmphasisSpan::class.java).forEach { span ->
            spannable.removeSpan(span)
        }
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

}
