package io.legado.app.ui.book.source.manage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.legado.app.R
import io.legado.app.model.CheckSource
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.NgFileSelectionCheckbox
import io.legado.app.ui.design.components.compose.NgFileSelectionCheckboxVariant
import io.legado.app.ui.design.components.compose.NgFormActionButton
import io.legado.app.ui.design.components.compose.NgFormActionButtonAppearance
import io.legado.app.ui.design.components.compose.NgFormField
import io.legado.app.ui.design.components.compose.NgFormFieldVariant
import io.legado.app.ui.design.components.compose.NgFormSwitchSettingRow
import io.legado.app.ui.design.theme.NgTheme

internal data class BookSourceCheckDialogResult(
    val keyword: String,
    val timeoutSeconds: Long,
    val writeSourceComment: Boolean,
    val checkDomain: Boolean,
    val checkSearch: Boolean,
    val checkDiscovery: Boolean,
    val checkInfo: Boolean,
    val checkCategory: Boolean,
    val checkContent: Boolean,
    val blockSourceDialogs: Boolean,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BookSourceCheckDialog(
    onDismiss: () -> Unit,
    onConfirm: (BookSourceCheckDialogResult) -> Unit,
) {
    var keyword by remember { mutableStateOf(CheckSource.keyword) }
    var timeoutText by remember { mutableStateOf((CheckSource.timeout / 1000).toString()) }
    var timeoutError by remember { mutableStateOf(false) }
    var advancedExpanded by remember { mutableStateOf(false) }
    var writeSourceComment by remember { mutableStateOf(CheckSource.wSourceComment) }
    var checkDomain by remember { mutableStateOf(CheckSource.checkDomain) }
    var checkSearch by remember { mutableStateOf(CheckSource.checkSearch) }
    var checkDiscovery by remember { mutableStateOf(CheckSource.checkDiscovery) }
    var checkInfo by remember { mutableStateOf(CheckSource.checkInfo) }
    var checkCategory by remember { mutableStateOf(CheckSource.checkCategory) }
    var checkContent by remember { mutableStateOf(CheckSource.checkContent) }
    var blockSourceDialogs by remember { mutableStateOf(CheckSource.blockSourceDialogs) }

    val infoEnabled = checkSearch || checkDiscovery
    val categoryEnabled = infoEnabled && checkInfo
    val contentEnabled = categoryEnabled && checkCategory
    val selectedItemCount = listOf(
        checkDomain,
        checkSearch,
        checkDiscovery,
        checkInfo,
        checkCategory,
        checkContent,
    ).count { it }

    fun disableInfoSection() {
        checkInfo = false
        checkCategory = false
        checkContent = false
    }

    fun toggleDomain() {
        checkDomain = !checkDomain
        if (!checkSearch && !checkDiscovery && !checkDomain) {
            checkSearch = true
        }
    }

    fun toggleSearch() {
        checkSearch = !checkSearch
        if (!checkSearch && !checkDiscovery) {
            disableInfoSection()
            if (!checkDomain) checkDiscovery = true
        }
    }

    fun toggleDiscovery() {
        checkDiscovery = !checkDiscovery
        if (!checkSearch && !checkDiscovery) {
            disableInfoSection()
            if (!checkDomain) checkSearch = true
        }
    }

    fun toggleInfo() {
        checkInfo = !checkInfo
        if (!checkInfo) {
            checkCategory = false
            checkContent = false
        }
    }

    fun toggleCategory() {
        checkCategory = !checkCategory
        if (!checkCategory) checkContent = false
    }

    fun confirm() {
        val timeoutSeconds = timeoutText.toLongOrNull()
        if (
            timeoutSeconds == null ||
            timeoutSeconds <= 0L ||
            timeoutSeconds > Long.MAX_VALUE / 1000L
        ) {
            timeoutError = true
            advancedExpanded = true
            return
        }
        onConfirm(
            BookSourceCheckDialogResult(
                keyword = keyword,
                timeoutSeconds = timeoutSeconds,
                writeSourceComment = writeSourceComment,
                checkDomain = checkDomain,
                checkSearch = checkSearch,
                checkDiscovery = checkDiscovery,
                checkInfo = checkInfo,
                checkCategory = checkCategory,
                checkContent = checkContent,
                blockSourceDialogs = blockSourceDialogs,
            )
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        NgDialog(
            title = stringResource(R.string.check_select_source),
            modifier = Modifier
                .padding(horizontal = 18.dp)
                .widthIn(max = 520.dp),
            variant = NgDialogVariant.STANDARD,
            titleFontSize = 18.sp,
            titleFontWeight = FontWeight.Medium,
            actions = {
                NgFormActionButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    appearance = NgFormActionButtonAppearance.DIALOG,
                )
                NgFormActionButton(
                    text = stringResource(R.string.book_source_check_start),
                    onClick = ::confirm,
                    modifier = Modifier.weight(1f),
                    variant = NgButtonVariant.PRIMARY,
                    appearance = NgFormActionButtonAppearance.DIALOG,
                )
            },
        ) {
            BookSourceCheckUnderlinedField(
                label = stringResource(R.string.search_book_key),
                value = keyword,
                onValueChange = { keyword = it },
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clickable { advancedExpanded = !advancedExpanded }
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.book_source_check_advanced),
                        color = Color(NgTheme.colors.onSurface),
                        fontSize = 15.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!advancedExpanded) {
                        Text(
                            text = stringResource(
                                R.string.book_source_check_advanced_summary,
                                timeoutText.toLongOrNull() ?: 0L,
                                selectedItemCount,
                            ),
                            modifier = Modifier.padding(top = 2.dp),
                            color = Color(NgTheme.colors.primary),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right_20),
                    contentDescription = null,
                    tint = Color(NgTheme.colors.onSurfaceVariant),
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(if (advancedExpanded) 90f else 0f),
                )
            }
            if (advancedExpanded) {
                Spacer(Modifier.height(4.dp))
                BookSourceCheckUnderlinedField(
                    label = stringResource(R.string.check_source_timeout),
                    value = timeoutText,
                    onValueChange = { value ->
                        timeoutText = value.filter(Char::isDigit)
                        timeoutError = false
                    },
                    isError = timeoutError,
                    supportingText = if (timeoutError) {
                        stringResource(R.string.book_source_check_timeout_error)
                    } else {
                        null
                    },
                    keyboardType = KeyboardType.Number,
                    onDone = ::confirm,
                )
                Spacer(Modifier.height(6.dp))
                NgFormSwitchSettingRow(
                    title = stringResource(R.string.write_source_comment),
                    checked = writeSourceComment,
                    onCheckedChange = { writeSourceComment = it },
                )
                NgFormSwitchSettingRow(
                    title = stringResource(R.string.book_source_check_block_dialogs),
                    summary = stringResource(R.string.book_source_check_block_dialogs_summary),
                    checked = blockSourceDialogs,
                    onCheckedChange = { blockSourceDialogs = it },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.check_source_item),
                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
                    color = Color(NgTheme.colors.primary),
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.Medium,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    BookSourceCheckOption(
                        text = stringResource(R.string.domain),
                        checked = checkDomain,
                        onToggle = ::toggleDomain,
                    )
                    BookSourceCheckOption(
                        text = stringResource(R.string.search),
                        checked = checkSearch,
                        onToggle = ::toggleSearch,
                    )
                    BookSourceCheckOption(
                        text = stringResource(R.string.discovery),
                        checked = checkDiscovery,
                        onToggle = ::toggleDiscovery,
                    )
                    BookSourceCheckOption(
                        text = stringResource(R.string.source_tab_info),
                        checked = checkInfo,
                        enabled = infoEnabled,
                        onToggle = ::toggleInfo,
                    )
                    BookSourceCheckOption(
                        text = stringResource(R.string.chapter_list),
                        checked = checkCategory,
                        enabled = categoryEnabled,
                        onToggle = ::toggleCategory,
                    )
                    BookSourceCheckOption(
                        text = stringResource(R.string.main_body),
                        checked = checkContent,
                        enabled = contentEnabled,
                        onToggle = { checkContent = !checkContent },
                    )
                }
            }
        }
    }
}

@Composable
private fun BookSourceCheckUnderlinedField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean = false,
    supportingText: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    onDone: () -> Unit = {},
) {
    Text(
        text = label,
        modifier = Modifier.padding(horizontal = 2.dp),
        color = Color(NgTheme.colors.primary),
        fontSize = 13.sp,
        lineHeight = 17.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(2.dp))
    NgFormField(
        label = label,
        value = value,
        onValueChange = onValueChange,
        isError = isError,
        supportingText = supportingText,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
            onDone = { onDone() },
        ),
        variant = NgFormFieldVariant.PLAIN_UNDERLINE,
    )
}

@Composable
private fun BookSourceCheckOption(
    text: String,
    checked: Boolean,
    onToggle: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .height(40.dp)
            .widthIn(min = 68.dp)
            .clickable(enabled = enabled, role = Role.Checkbox, onClick = onToggle)
            .padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NgFileSelectionCheckbox(
            checked = checked,
            onCheckedChange = { onToggle() },
            enabled = enabled,
            variant = NgFileSelectionCheckboxVariant.COMPACT,
        )
        Text(
            text = text,
            color = Color(NgTheme.colors.onSurface).copy(alpha = if (enabled) 1f else 0.45f),
            fontSize = 14.sp,
            lineHeight = 18.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
