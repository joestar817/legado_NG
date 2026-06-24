package io.legado.app.ui.association

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.viewModels
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.databinding.DialogVerificationCodeViewBinding
import io.legado.app.help.glide.ImageLoader
import io.legado.app.help.glide.OkHttpModelLoader
import io.legado.app.help.source.SourceVerificationHelp
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.accentColor
import io.legado.app.model.ImageProvider
import io.legado.app.ui.widget.dialog.PhotoDialog
import io.legado.app.ui.widget.dialog.applyNgDialogWindow
import io.legado.app.utils.applyTint
import io.legado.app.utils.dpToPx
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 图片验证码对话框
 * 结果保存在内存中
 * val key = "${sourceOrigin ?: ""}_verificationResult"
 * CacheManager.get(key)
 */
class VerificationCodeDialog() : BaseDialogFragment(R.layout.dialog_verification_code_view),
    Toolbar.OnMenuItemClickListener {

    constructor(
        imageUrl: String,
        sourceOrigin: String? = null,
        sourceName: String? = null,
        sourceType: Int
    ) : this() {
        arguments = Bundle().apply {
            putString("imageUrl", imageUrl)
            putString("sourceOrigin", sourceOrigin)
            putString("sourceName", sourceName)
            putInt("sourceType", sourceType)
        }
    }

    val binding by viewBinding(DialogVerificationCodeViewBinding::bind)
    val viewModel by viewModels<VerificationCodeViewModel>()

    override fun onStart() {
        super.onStart()
        applyNgDialogWindow()
    }

    private var sourceOrigin: String? = null
    private var imageUrl: String? = null
    private var sourceActionPopup: SourceActionPopup? = null

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?): Unit = binding.run {
        initMenu()
        val arguments = arguments ?: return@run
        viewModel.initData(arguments)
        view.setBackgroundResource(R.drawable.ng_bg_dialog)
        toolBar.subtitle = arguments.getString("sourceName")
        sourceOrigin = arguments.getString("sourceOrigin")
        imageUrl = arguments.getString("imageUrl")
        loadImage(imageUrl ?: return@run, sourceOrigin)
        verificationCode.setOnFocusChangeListener { _, hasFocus ->
            verificationCode.hint =
                if (hasFocus) null else getString(R.string.verification_code)
        }
        verificationCode.setOnEditorActionListener { _, actionId, event ->
            if (
                actionId == EditorInfo.IME_ACTION_DONE ||
                event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP
            ) {
                submitVerificationCode()
                true
            } else {
                false
            }
        }
        verificationCodeImageView.setOnClickListener {
            imageUrl?.let {
                showDialogFragment(PhotoDialog(it, sourceOrigin))
            }
        }
    }

    private fun initMenu() {
        binding.toolBar.setOnMenuItemClickListener(this)
        binding.toolBar.inflateMenu(R.menu.verification_code)
        binding.toolBar.menu.applyTint(requireContext())
    }

    @SuppressLint("CheckResult")
    private fun loadImage(url: String, sourceUrl: String?) {
        ImageProvider.remove(url)
        ImageLoader.loadBitmap(requireContext(), url).apply {
            sourceUrl?.let {
                apply(RequestOptions().set(OkHttpModelLoader.sourceOriginOption, it))
            }
        }.error(R.drawable.image_loading_error)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .skipMemoryCache(true)
            .override(Target.SIZE_ORIGINAL)
            .listener(object : RequestListener<Bitmap> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Bitmap?>,
                    isFirstResource: Boolean
                ): Boolean {
                    return false
                }

                override fun onResourceReady(
                    resource: Bitmap,
                    model: Any,
                    target: Target<Bitmap?>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    updateImageSize(resource)
                    val bitmap = resource.copy(resource.config ?: Bitmap.Config.ARGB_8888, true)
                    ImageProvider.put(url, bitmap) // 传给 PhotoDialog
                    return false
                }
            })
            .into(binding.verificationCodeImageView)
    }

    private fun updateImageSize(bitmap: Bitmap) {
        val bitmapWidth = bitmap.width.takeIf { it > 0 } ?: return
        val bitmapHeight = bitmap.height.takeIf { it > 0 } ?: return
        val maxWidth = (resources.displayMetrics.widthPixels - 64.dpToPx()).coerceAtLeast(1)
        val minHeight = 44.dpToPx()
        val maxHeight = 72.dpToPx()
        val targetHeight = bitmapHeight.coerceIn(minHeight, maxHeight)
        val scale = min(maxWidth.toFloat() / bitmapWidth, targetHeight.toFloat() / bitmapHeight)
        binding.verificationCodeImageView.layoutParams =
            binding.verificationCodeImageView.layoutParams.apply {
                width = (bitmapWidth * scale).roundToInt()
                height = (bitmapHeight * scale).roundToInt()
            }
    }

    @SuppressLint("InflateParams")
    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_ok -> submitVerificationCode()

            R.id.menu_refresh -> {
                binding.verificationCode.text = null
                imageUrl?.let {
                    loadImage(it, sourceOrigin)
                }
            }

            R.id.menu_source_actions -> showSourceActionPopup()

            R.id.menu_disable_source -> disableSource()

            R.id.menu_delete_source -> confirmDeleteSource()
        }
        return true
    }

    private fun showSourceActionPopup() {
        val popup = sourceActionPopup ?: SourceActionPopup(requireContext()) { itemId ->
            onSourceActionClick(itemId)
        }.also {
            sourceActionPopup = it
        }
        popup.show(binding.toolBar)
    }

    private fun onSourceActionClick(itemId: Int) {
        when (itemId) {
            R.id.menu_disable_source -> disableSource()
            R.id.menu_delete_source -> confirmDeleteSource()
        }
    }

    private fun disableSource() {
        viewModel.disableSource {
            dismiss()
        }
    }

    private fun confirmDeleteSource() {
        alert(R.string.draw) {
            setMessage(getString(R.string.sure_del) + "\n" + viewModel.sourceName)
            noButton()
            yesButton {
                viewModel.deleteSource {
                    dismiss()
                }
            }
        }
    }

    private fun submitVerificationCode() {
        val sourceKey = sourceOrigin ?: return
        val verificationCode = binding.verificationCode.text.toString()
        SourceVerificationHelp.setResult(sourceKey, verificationCode)
        dismiss()
    }

    override fun onDestroy() {
        sourceActionPopup?.dismiss()
        SourceVerificationHelp.checkResult(sourceOrigin!!)
        super.onDestroy()
        activity?.finish()
    }

    private class SourceActionPopup(
        context: Context,
        private val onItemClick: (Int) -> Unit
    ) : PopupWindow(128.dpToPx(), ViewGroup.LayoutParams.WRAP_CONTENT) {

        init {
            contentView = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 4.dpToPx(), 0, 4.dpToPx())
                setBackgroundResource(R.drawable.bg_popup_menu)
                addView(createMenuItem(context, R.string.disable_source, R.id.menu_disable_source))
                addView(createMenuItem(context, R.string.delete_source, R.id.menu_delete_source))
            }
            isTouchable = true
            isOutsideTouchable = true
            isFocusable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = 4.dpToPx().toFloat()
        }

        fun show(anchor: View) {
            if (isShowing) {
                dismiss()
            }
            showAsDropDown(anchor, anchor.width - width - 8.dpToPx(), 0)
        }

        private fun createMenuItem(context: Context, titleRes: Int, itemId: Int): TextView {
            val outValue = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            return TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    44.dpToPx()
                )
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER_VERTICAL
                maxLines = 1
                setBackgroundResource(outValue.resourceId)
                setPadding(16.dpToPx(), 0, 12.dpToPx(), 0)
                setText(titleRes)
                setTextColor(context.accentColor)
                textSize = 16f
                setOnClickListener {
                    dismiss()
                    onItemClick(itemId)
                }
            }
        }

    }

}
