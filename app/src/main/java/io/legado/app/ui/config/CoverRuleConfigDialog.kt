package io.legado.app.ui.config

import android.app.Dialog
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.model.BookCover
import io.legado.app.ui.design.components.compose.NgBottomDrawerSurface
import io.legado.app.ui.design.components.compose.NgDialogTextActionButton
import io.legado.app.ui.design.components.compose.NgFormMultilineField
import io.legado.app.ui.design.components.compose.NgFormMultilineFieldVariant
import io.legado.app.ui.design.components.compose.NgLongDrawerHeader
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.utils.GSON
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 封面规则编辑抽屉；规则读取、校验、保存和恢复默认仍沿用原业务语义。 */
class CoverRuleConfigDialog : BottomSheetDialogFragment() {

    private var enabled by mutableStateOf(true)
    private var searchUrl by mutableStateOf("")
    private var coverRule by mutableStateOf("")
    private var loaded by mutableStateOf(false)
    private var restoredEditingState = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState?.containsKey(KEY_SEARCH_URL) == true) {
            enabled = savedInstanceState.getBoolean(KEY_ENABLED)
            searchUrl = savedInstanceState.getString(KEY_SEARCH_URL).orEmpty()
            coverRule = savedInstanceState.getString(KEY_COVER_RULE).orEmpty()
            loaded = true
            restoredEditingState = true
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        BottomSheetDialog(requireContext(), theme)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setBackgroundColor(AndroidColor.TRANSPARENT)
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (view as ComposeView).setContent {
            NgAppTheme(updateSystemBars = false) {
                CoverRuleDrawerContent(
                    loaded = loaded,
                    enabled = enabled,
                    searchUrl = searchUrl,
                    coverRule = coverRule,
                    onEnabledChange = { enabled = it },
                    onSearchUrlChange = { searchUrl = it },
                    onCoverRuleChange = { coverRule = it },
                    onReset = ::resetToDefault,
                    onCancel = { dismissAllowingStateLoss() },
                    onConfirm = ::saveRule,
                )
            }
        }
        if (!restoredEditingState) loadRule()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawableResource(R.color.transparent)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = 0.22f }
            decorView.setPadding(0, 0, 0, 0)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        val sheet = dialog?.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet,
        ) ?: return
        sheet.setBackgroundColor(AndroidColor.TRANSPARENT)
        sheet.layoutParams = sheet.layoutParams.apply {
            height = (resources.displayMetrics.heightPixels * SHEET_HEIGHT_RATIO).toInt()
        }
        BottomSheetBehavior.from(sheet).apply {
            skipCollapsed = true
            isFitToContents = true
            isDraggable = true
            isDraggableOnNestedScroll = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_ENABLED, enabled)
        outState.putString(KEY_SEARCH_URL, searchUrl)
        outState.putString(KEY_COVER_RULE, coverRule)
        super.onSaveInstanceState(outState)
    }

    override fun show(manager: FragmentManager, tag: String?) {
        runCatching { super.show(manager, tag) }
            .onFailure { AppLog.put("显示封面规则抽屉失败 tag:$tag", it) }
    }

    private fun loadRule() {
        viewLifecycleOwner.lifecycleScope.launch {
            val rule = withContext(IO) { BookCover.getCoverRule() }
            Log.e("coverRule", GSON.toJson(rule))
            enabled = rule.enable
            searchUrl = rule.searchUrl
            coverRule = rule.coverRule
            loaded = true
        }
    }

    private fun resetToDefault() {
        BookCover.delCoverRule()
        dismissAllowingStateLoss()
    }

    private fun saveRule() {
        if (searchUrl.isBlank() || coverRule.isBlank()) {
            requireContext().toastOnUi("搜索url和cover规则不能为空")
            return
        }
        BookCover.saveCoverRule(BookCover.CoverRule(enabled, searchUrl, coverRule))
        dismissAllowingStateLoss()
    }

    private companion object {
        const val SHEET_HEIGHT_RATIO = 0.90f
        const val KEY_ENABLED = "coverRule.enabled"
        const val KEY_SEARCH_URL = "coverRule.searchUrl"
        const val KEY_COVER_RULE = "coverRule.coverRule"
    }
}

@Composable
private fun CoverRuleDrawerContent(
    loaded: Boolean,
    enabled: Boolean,
    searchUrl: String,
    coverRule: String,
    onEnabledChange: (Boolean) -> Unit,
    onSearchUrlChange: (String) -> Unit,
    onCoverRuleChange: (String) -> Unit,
    onReset: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    NgBottomDrawerSurface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding()
                .padding(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 10.dp),
        ) {
            NgLongDrawerHeader(title = stringResource(R.string.cover_config))
            Spacer(Modifier.height(4.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                color = colorResource(R.color.ng_surface_card),
                contentColor = colorResource(R.color.ng_on_surface),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(0.8.dp, colorResource(R.color.ng_card_stroke)),
            ) {
                if (!loaded) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = Color(NgTheme.colors.primary),
                            strokeWidth = 2.5.dp,
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CoverRuleEnabledRow(
                            enabled = enabled,
                            onEnabledChange = onEnabledChange,
                        )
                        NgFormMultilineField(
                            value = searchUrl,
                            onValueChange = onSearchUrlChange,
                            label = stringResource(R.string.r_search_url),
                            minHeight = 82.dp,
                            maxHeight = 116.dp,
                            minLines = 3,
                            maxLines = 5,
                            variant = NgFormMultilineFieldVariant.DIALOG_UNDERLINE,
                        )
                        NgFormMultilineField(
                            value = coverRule,
                            onValueChange = onCoverRuleChange,
                            label = stringResource(R.string.rule_cover_url),
                            minHeight = 300.dp,
                            maxHeight = 420.dp,
                            minLines = 12,
                            maxLines = 18,
                            variant = NgFormMultilineFieldVariant.DIALOG_UNDERLINE,
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NgDialogTextActionButton(
                    text = stringResource(R.string.btn_default_s),
                    onClick = onReset,
                    enabled = loaded,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NgDialogTextActionButton(
                        text = stringResource(R.string.cancel),
                        onClick = onCancel,
                        secondary = true,
                    )
                    NgDialogTextActionButton(
                        text = stringResource(R.string.ok),
                        onClick = onConfirm,
                        enabled = loaded,
                    )
                }
            }
        }
    }
}

@Composable
private fun CoverRuleEnabledRow(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .toggleable(
                value = enabled,
                role = Role.Checkbox,
                onValueChange = onEnabledChange,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = enabled,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(
                checkedColor = Color(NgTheme.colors.primary),
                checkmarkColor = Color.White,
                uncheckedColor = Color(NgTheme.colors.onSurfaceVariant).copy(alpha = 0.68f),
            ),
        )
        Text(
            text = stringResource(R.string.enable),
            modifier = Modifier.padding(start = 4.dp),
            color = Color(NgTheme.colors.onSurface),
            fontSize = 16.sp,
            lineHeight = 20.sp,
        )
    }
}
