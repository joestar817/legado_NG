package io.legado.app.ui.widget.dialog

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.dpToPx
import io.legado.app.utils.windowSize
import splitties.systemservices.windowManager

object NgDialog {
    const val DEFAULT_MARGIN_DP = 16
    const val COMPACT_MARGIN_DP = 12
    const val DEFAULT_DIM_AMOUNT = 0.55f
    const val DEFAULT_MAX_HEIGHT_RATIO = 0.92f

    fun applyWindow(
        dialog: Dialog?,
        marginDp: Int = DEFAULT_MARGIN_DP,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        dimAmount: Float = DEFAULT_DIM_AMOUNT
    ) {
        val window = dialog?.window ?: return
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        if (AppConfig.isEInkMode) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        } else {
            val attributes = window.attributes
            attributes.dimAmount = dimAmount
            window.attributes = attributes
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        window.setLayout(resolveWidth(window, marginDp), height)
    }

    fun maxHeight(dialog: Dialog?, ratio: Float = DEFAULT_MAX_HEIGHT_RATIO): Int {
        val window = dialog?.window ?: return ViewGroup.LayoutParams.WRAP_CONTENT
        val dm = window.context.windowManager.windowSize
        return (dm.heightPixels * ratio).toInt()
    }

    private fun resolveWidth(window: Window, marginDp: Int): Int {
        val dm = window.context.windowManager.windowSize
        return dm.widthPixels - marginDp.dpToPx() * 2
    }
}

fun DialogFragment.applyNgDialogWindow(
    marginDp: Int = NgDialog.DEFAULT_MARGIN_DP,
    height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
    dimAmount: Float = NgDialog.DEFAULT_DIM_AMOUNT
) {
    NgDialog.applyWindow(dialog, marginDp, height, dimAmount)
}

fun Dialog.applyNgWindow(
    marginDp: Int = NgDialog.DEFAULT_MARGIN_DP,
    height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
    dimAmount: Float = NgDialog.DEFAULT_DIM_AMOUNT
) {
    NgDialog.applyWindow(this, marginDp, height, dimAmount)
}

fun DialogFragment.ngDialogMaxHeight(ratio: Float = NgDialog.DEFAULT_MAX_HEIGHT_RATIO): Int {
    return NgDialog.maxHeight(dialog, ratio)
}
