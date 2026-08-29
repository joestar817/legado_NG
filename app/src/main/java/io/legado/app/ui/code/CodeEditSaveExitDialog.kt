package io.legado.app.ui.code

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.View
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.base.BaseComposeDialogFragment
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.NgDialogTextActionButton
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.widget.dialog.applyNgDialogWindow

class CodeEditSaveExitDialog : BaseComposeDialogFragment() {

    private val callback get() = activity as? Callback

    override fun onStart() {
        super.onStart()
        applyNgDialogWindow()
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        (view as ComposeView).apply {
            setBackgroundColor(AndroidColor.TRANSPARENT)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                NgAppTheme(updateSystemBars = false) {
                    NgDialog(
                        title = stringResource(R.string.exit),
                        modifier = Modifier.heightIn(min = 156.dp),
                        variant = NgDialogVariant.CLASSIC_CONFIRMATION,
                        titleFontWeight = FontWeight.Normal,
                        actions = {
                            NgDialogTextActionButton(
                                text = stringResource(R.string.cancel),
                                onClick = ::dismissAllowingStateLoss,
                            )
                            NgDialogTextActionButton(
                                text = stringResource(R.string.dont_save),
                                danger = true,
                                onClick = ::discardAndExit,
                            )
                            NgDialogTextActionButton(
                                text = stringResource(R.string.save),
                                onClick = ::saveAndExit,
                            )
                        },
                    ) {
                        Text(
                            text = stringResource(R.string.save_before_exit),
                            color = Color(NgTheme.colors.onSurface),
                            fontSize = 15.sp,
                            lineHeight = 21.sp,
                        )
                    }
                }
            }
        }
    }

    private fun saveAndExit() {
        dismissAllowingStateLoss()
        callback?.onCodeEditSaveAndExit()
    }

    private fun discardAndExit() {
        dismissAllowingStateLoss()
        callback?.onCodeEditDiscardAndExit()
    }

    interface Callback {
        fun onCodeEditSaveAndExit()
        fun onCodeEditDiscardAndExit()
    }
}
