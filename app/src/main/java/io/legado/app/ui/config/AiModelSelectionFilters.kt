package io.legado.app.ui.config

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import com.google.android.flexbox.FlexboxLayout
import io.legado.app.R
import io.legado.app.databinding.LayoutNgModelFiltersBinding
import io.legado.app.help.ai.AiProviderSetting
import io.legado.app.lib.theme.accentColor
import io.legado.app.ui.design.theme.NgThemeResolver
import io.legado.app.ui.widget.dialog.NgLongListBottomSheet
import io.legado.app.ui.design.components.compose.NgDrawerDefaults
import io.legado.app.utils.dpToPx

/**
 * AI 模型选择抽屉的折叠搜索与厂商筛选控制器。
 * 厂商较多时使用 Flexbox 自动换行；只有一个厂商时只显示搜索。
 */
internal class AiModelSelectionFilters(
    private val context: Context,
    private val sheet: NgLongListBottomSheet,
    providers: List<AiProviderSetting>
) {

    private val providers = providers.distinctBy(AiProviderSetting::id)
    private val selectedProviderIds = linkedSetOf<String>()
    private val binding = LayoutNgModelFiltersBinding.inflate(LayoutInflater.from(context))
    private val filterAction: ImageButton

    init {
        (sheet.searchBar.parent as? ViewGroup)?.removeView(sheet.searchBar)
        binding.layoutSearchContainer.addView(
            sheet.searchBar,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        sheet.searchBar.apply {
            isVisible = true
            setBackgroundResource(R.drawable.ng_bg_tts_filter_search)
        }
        val searchHint = sheet.searchEdit.hint
        sheet.searchEdit.setOnFocusChangeListener { _, hasFocus ->
            sheet.searchEdit.hint = if (hasFocus) null else searchHint
        }

        binding.root.isVisible = false
        binding.layoutProviderFilterSection.isVisible = this.providers.size > 1
        bindProviderFilters()
        sheet.setTopContent(binding.root)

        filterAction = sheet.addCompactTitleIcon(
            iconRes = R.drawable.ic_tts_params_grid,
            contentDescription = context.getString(R.string.ai_filter_models)
        ) { button ->
            val showFilters = !binding.root.isVisible
            binding.root.isVisible = showFilters
            if (showFilters) sheet.scrollContentToTop()
            updateActionTint(button)
        }
        sheet.searchEdit.doOnTextChanged { _, _, _, _ ->
            updateActionTint(filterAction)
        }
        updateActionTint(filterAction)
    }

    fun accepts(providerId: String): Boolean {
        return selectedProviderIds.isEmpty() || providerId in selectedProviderIds
    }

    private fun bindProviderFilters() {
        binding.layoutProviderFilters.removeAllViews()
        providers.forEach { provider ->
            binding.layoutProviderFilters.addView(createProviderChip(provider))
        }
    }

    private fun createProviderChip(provider: AiProviderSetting): FrameLayout {
        val selected = provider.id in selectedProviderIds
        val colors = NgThemeResolver.resolve(context).colors
        val iconSize = 16.dpToPx()
        val icon = ContextCompat.getDrawable(context, provider.iconRes())?.mutate()?.apply {
            setBounds(0, 0, iconSize, iconSize)
        }
        val chip = TextView(context).apply {
            text = provider.name
            gravity = android.view.Gravity.CENTER
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(if (selected) colors.primary else colors.onSurfaceVariant)
            setCompoundDrawablesRelative(icon, null, null, null)
            compoundDrawablePadding = 5.dpToPx()
            setPadding(8.dpToPx(), 0, 8.dpToPx(), 0)
            isClickable = true
            isFocusable = true
            contentDescription = provider.name
            background = GradientDrawable().apply {
                cornerRadius = 16.dpToPx().toFloat()
                setColor(
                    if (selected) colors.selectedContainer
                    else NgDrawerDefaults.adaptiveContentCardColor(context)
                )
            }
            setOnClickListener {
                if (!selectedProviderIds.add(provider.id)) {
                    selectedProviderIds.remove(provider.id)
                }
                bindProviderFilters()
                updateActionTint(filterAction)
                sheet.refreshContent()
                sheet.scrollContentToTop()
            }
        }
        return FrameLayout(context).apply {
            addView(
                chip,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    32.dpToPx()
                ).apply {
                    marginStart = 3.dpToPx()
                    marginEnd = 3.dpToPx()
                }
            )
            layoutParams = FlexboxLayout.LayoutParams(
                0,
                38.dpToPx()
            ).apply {
                flexBasisPercent = 1f / 3f
            }
        }
    }

    private fun updateActionTint(button: ImageButton) {
        val active = sheet.searchEdit.text?.isNotBlank() == true || selectedProviderIds.isNotEmpty()
        button.setColorFilter(
            if (active) context.accentColor
            else ContextCompat.getColor(context, R.color.ng_on_surface_variant)
        )
    }
}
