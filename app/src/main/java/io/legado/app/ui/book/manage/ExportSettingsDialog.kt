package io.legado.app.ui.book.manage

import android.app.Dialog
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.legado.app.R
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.help.book.BookExportFileNameRules
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.compose.NgBottomDrawerSurface
import io.legado.app.ui.design.components.compose.NgDrawerContentCardStyle
import io.legado.app.ui.design.components.compose.NgChoiceCard
import io.legado.app.ui.design.components.compose.NgChoiceCardVariant
import io.legado.app.ui.design.components.compose.NgExpandableSettingsItem
import io.legado.app.ui.design.components.compose.NgExpandableSettingsItemVariant
import io.legado.app.ui.design.components.compose.NgFormActionButton
import io.legado.app.ui.design.components.compose.NgFormGroup
import io.legado.app.ui.design.components.compose.NgFormGroupDivider
import io.legado.app.ui.design.components.compose.NgFormSelectOption
import io.legado.app.ui.design.components.compose.NgFormSelectRow
import io.legado.app.ui.design.components.compose.NgFormSwitchSettingRow
import io.legado.app.ui.design.components.compose.NgLongDrawerHeader
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgTheme

/** 书架管理导出设置抽屉。目录由确认后的系统选择器决定。 */
class ExportSettingsDialog : BottomSheetDialogFragment() {

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
                ExportSettingsSheetContent(
                    selectedCount = requireArguments().getInt(ARG_SELECTED_COUNT),
                    onConfirm = ::confirmExport,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawableResource(R.color.transparent)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            attributes = attributes.apply { dimAmount = 0.22f }
            decorView.setPadding(0, 0, 0, 0)
        }
        val sheet = dialog?.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return
        sheet.setBackgroundColor(AndroidColor.TRANSPARENT)
        sheet.layoutParams = sheet.layoutParams.apply {
            height = (resources.displayMetrics.heightPixels * SHEET_HEIGHT_RATIO).toInt()
        }
        BottomSheetBehavior.from(sheet).apply {
            skipCollapsed = true
            isDraggable = true
            isDraggableOnNestedScroll = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun show(manager: FragmentManager, tag: String?) {
        runCatching { super.show(manager, tag) }
            .onFailure { AppLog.put("显示导出设置抽屉失败 tag:$tag", it) }
    }

    private fun confirmExport(draft: ExportSettingsDraft) {
        AppConfig.exportType = draft.exportType
        AppConfig.exportCharset = draft.charset
        AppConfig.exportPlainText = draft.plainText
        AppConfig.exportFilterInteractiveImages = draft.filterInteractiveImages
        AppConfig.exportPictureFile = draft.exportPictures && !draft.plainText
        AppConfig.bookExportFileName = draft.fileNameRule
        callback?.onExportSettingsConfirmed(
            ExportSettingsResult(
                exportType = draft.exportType,
                plainText = draft.plainText,
                filterInteractiveImages = draft.filterInteractiveImages,
                exportPictures = draft.exportPictures && !draft.plainText,
            )
        )
        dismissAllowingStateLoss()
    }

    private val callback: Callback?
        get() = (parentFragment as? Callback) ?: activity as? Callback

    interface Callback {
        fun onExportSettingsConfirmed(result: ExportSettingsResult)
    }

    companion object {
        private const val ARG_SELECTED_COUNT = "selectedCount"
        private const val SHEET_HEIGHT_RATIO = 0.72f

        fun show(manager: FragmentManager, selectedCount: Int) {
            ExportSettingsDialog().apply {
                arguments = Bundle().apply { putInt(ARG_SELECTED_COUNT, selectedCount) }
            }.show(manager, ExportSettingsDialog::class.java.simpleName)
        }
    }
}

data class ExportSettingsResult(
    val exportType: Int = 0,
    val plainText: Boolean = true,
    val filterInteractiveImages: Boolean = true,
    val exportPictures: Boolean = false,
)

private data class ExportSettingsDraft(
    val exportType: Int,
    val charset: String,
    val plainText: Boolean,
    val filterInteractiveImages: Boolean,
    val exportPictures: Boolean,
    val fileNameRule: String,
)

@Composable
private fun ExportSettingsSheetContent(
    selectedCount: Int,
    onConfirm: (ExportSettingsDraft) -> Unit,
) {
    var exportType by remember { mutableStateOf(AppConfig.exportType.coerceIn(0, 1)) }
    var advancedExpanded by remember { mutableStateOf(false) }
    var charset by remember { mutableStateOf(AppConfig.exportCharset) }
    var plainText by remember { mutableStateOf(AppConfig.exportPlainText) }
    var filterInteractiveImages by remember {
        mutableStateOf(AppConfig.exportFilterInteractiveImages)
    }
    var exportPictures by remember { mutableStateOf(AppConfig.exportPictureFile) }
    var fileNameRule by remember {
        mutableStateOf(BookExportFileNameRules.presetOrDefault(AppConfig.bookExportFileName))
    }
    val advancedSummary = if (exportType == 0) {
        "$charset · ${stringResource(
            if (plainText) R.string.export_plain_text
            else R.string.export_keep_images
        )}"
    } else {
        stringResource(R.string.export_standard_epub_summary)
    }
    val fileNameOptions = listOf(
        NgFormSelectOption(
            stringResource(R.string.export_file_name_book_only),
            BookExportFileNameRules.NAME_ONLY,
        ),
        NgFormSelectOption(
            stringResource(R.string.export_file_name_book_author),
            BookExportFileNameRules.NAME_AUTHOR,
        ),
        NgFormSelectOption(
            stringResource(R.string.export_file_name_book_author_chapters),
            BookExportFileNameRules.NAME_AUTHOR_CHAPTER_COUNT,
        ),
    )

    NgBottomDrawerSurface(
        modifier = Modifier.fillMaxSize(),
        contentCardStyle = NgDrawerContentCardStyle.ADAPTIVE,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 12.dp),
        ) {
            NgLongDrawerHeader(
                title = stringResource(R.string.export_settings_title),
                statusText = stringResource(R.string.export_selected_count, selectedCount),
                statusIconRes = R.drawable.ic_check_circle_outline,
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "formats") {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NgChoiceCard(
                            title = "TXT",
                            summary = stringResource(R.string.export_format_txt_summary),
                            iconRes = R.drawable.ic_bookshelf_action_detail,
                            selected = exportType == 0,
                            onClick = { exportType = 0 },
                            modifier = Modifier.weight(1f),
                            variant = NgChoiceCardVariant.FORMAT,
                        )
                        NgChoiceCard(
                            title = "EPUB",
                            summary = stringResource(R.string.export_format_epub_summary),
                            iconRes = R.drawable.ic_book_info_read,
                            selected = exportType == 1,
                            onClick = { exportType = 1 },
                            modifier = Modifier.weight(1f),
                            variant = NgChoiceCardVariant.FORMAT,
                        )
                    }
                }
                item(key = "advanced") {
                    NgExpandableSettingsItem(
                        title = stringResource(R.string.export_settings_advanced),
                        summary = advancedSummary,
                        expanded = advancedExpanded,
                        onExpandedChange = { advancedExpanded = it },
                        variant = NgExpandableSettingsItemVariant.COMPACT_LEADING,
                        leadingIconRes = R.drawable.ic_settings,
                    ) {
                        if (exportType == 0) {
                            ExportTxtAdvancedContent(
                                charset = charset,
                                fileNameRule = fileNameRule,
                                fileNameOptions = fileNameOptions,
                                plainText = plainText,
                                filterInteractiveImages = filterInteractiveImages,
                                exportPictures = exportPictures,
                                onCharsetChange = { charset = it },
                                onFileNameRuleChange = { fileNameRule = it },
                                onPlainTextChange = { plainText = it },
                                onFilterInteractiveImagesChange = {
                                    filterInteractiveImages = it
                                },
                                onExportPicturesChange = { exportPictures = it },
                            )
                        } else {
                            ExportEpubAdvancedContent(
                                fileNameRule = fileNameRule,
                                fileNameOptions = fileNameOptions,
                                filterInteractiveImages = filterInteractiveImages,
                                onFileNameRuleChange = { fileNameRule = it },
                                onFilterInteractiveImagesChange = {
                                    filterInteractiveImages = it
                                },
                            )
                        }
                    }
                }
            }
            NgFormActionButton(
                text = stringResource(R.string.export_select_folder_and_export),
                onClick = {
                    onConfirm(
                        ExportSettingsDraft(
                            exportType = exportType,
                            charset = charset,
                            plainText = plainText,
                            filterInteractiveImages = filterInteractiveImages,
                            exportPictures = exportPictures,
                            fileNameRule = fileNameRule,
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                variant = NgButtonVariant.PRIMARY,
            )
        }
    }
}

@Composable
private fun ExportTxtAdvancedContent(
    charset: String,
    fileNameRule: String,
    fileNameOptions: List<NgFormSelectOption>,
    plainText: Boolean,
    filterInteractiveImages: Boolean,
    exportPictures: Boolean,
    onCharsetChange: (String) -> Unit,
    onFileNameRuleChange: (String) -> Unit,
    onPlainTextChange: (Boolean) -> Unit,
    onFilterInteractiveImagesChange: (Boolean) -> Unit,
    onExportPicturesChange: (Boolean) -> Unit,
) {
    NgFormGroup(title = stringResource(R.string.export_text_format)) {
        NgFormSelectRow(
            title = stringResource(R.string.export_charset),
            selectedValue = charset,
            options = AppConst.charsets.map { NgFormSelectOption(it, it) },
            onValueChange = onCharsetChange,
            arrowIcon = painterResource(R.drawable.ic_ng_spinner_arrow_down),
        )
        NgFormGroupDivider()
        NgFormSelectRow(
            title = stringResource(R.string.export_file_name),
            selectedValue = fileNameRule,
            options = fileNameOptions,
            onValueChange = onFileNameRuleChange,
            arrowIcon = painterResource(R.drawable.ic_ng_spinner_arrow_down),
        )
    }
    NgFormGroup(title = stringResource(R.string.export_image_content)) {
        NgFormSwitchSettingRow(
            title = stringResource(R.string.export_plain_text),
            summary = stringResource(R.string.export_plain_text_summary),
            checked = plainText,
            onCheckedChange = onPlainTextChange,
        )
        NgFormGroupDivider()
        NgFormSwitchSettingRow(
            title = stringResource(R.string.export_filter_interactive_images),
            summary = stringResource(R.string.export_filter_interactive_images_summary),
            checked = filterInteractiveImages,
            onCheckedChange = onFilterInteractiveImagesChange,
            enabled = !plainText,
        )
        NgFormGroupDivider()
        NgFormSwitchSettingRow(
            title = stringResource(R.string.export_save_images_separately),
            summary = stringResource(R.string.export_save_images_separately_summary),
            checked = exportPictures,
            onCheckedChange = onExportPicturesChange,
            enabled = !plainText,
        )
    }
}

@Composable
private fun ExportEpubAdvancedContent(
    fileNameRule: String,
    fileNameOptions: List<NgFormSelectOption>,
    filterInteractiveImages: Boolean,
    onFileNameRuleChange: (String) -> Unit,
    onFilterInteractiveImagesChange: (Boolean) -> Unit,
) {
    NgFormGroup(title = stringResource(R.string.export_file_settings)) {
        NgFormSelectRow(
            title = stringResource(R.string.export_file_name),
            selectedValue = fileNameRule,
            options = fileNameOptions,
            onValueChange = onFileNameRuleChange,
            arrowIcon = painterResource(R.drawable.ic_ng_spinner_arrow_down),
        )
    }
    NgFormGroup(title = stringResource(R.string.export_image_content)) {
        NgFormSwitchSettingRow(
            title = stringResource(R.string.export_filter_interactive_images),
            summary = stringResource(R.string.export_filter_interactive_images_summary),
            checked = filterInteractiveImages,
            onCheckedChange = onFilterInteractiveImagesChange,
        )
    }
}
