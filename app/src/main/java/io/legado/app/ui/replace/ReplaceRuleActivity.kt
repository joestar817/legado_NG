package io.legado.app.ui.replace

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.ComposeActivityBinding
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.ui.association.ImportReplaceRuleDialog
import io.legado.app.ui.association.RuleDeleteConfirmDialog
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.qrcode.QrCodeResult
import io.legado.app.ui.replace.edit.ReplaceRuleEditDialog
import io.legado.app.utils.CreateFileContract
import io.legado.app.utils.GSON
import io.legado.app.utils.SelectFileContract
import io.legado.app.utils.launch
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.takePersistableReadPermission
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

/** 替换净化规则管理。 */
class ReplaceRuleActivity :
    VMBaseActivity<ComposeActivityBinding, ReplaceRuleViewModel>(),
    ReplaceRuleEditDialog.Callback,
    ReplaceRuleOnlineImportDialog.Callback {

    override val binding by viewBinding(ComposeActivityBinding::inflate)
    override val viewModel by viewModels<ReplaceRuleViewModel>()
    override val bindNgToolbarMenu: Boolean = false

    private var rules by mutableStateOf<List<ReplaceRule>>(
        emptyList(),
        referentialEqualityPolicy(),
    )
    private var groups by mutableStateOf<List<String>>(
        emptyList(),
        referentialEqualityPolicy(),
    )
    private var pendingDelete by mutableStateOf<ReplaceRuleDeleteRequest?>(null)
    private var dataInitialized = false
    private val currentBookName by lazy {
        intent.getStringExtra(EXTRA_BOOK_NAME).orEmpty()
    }
    private val qrCodeResult = registerForActivityResult(QrCodeResult()) {
        it ?: return@registerForActivityResult
        showDialogFragment(ImportReplaceRuleDialog(it))
    }
    private val importDoc = registerForActivityResult(SelectFileContract()) { uri ->
        uri?.let {
            it.takePersistableReadPermission()
            showDialogFragment(ImportReplaceRuleDialog(uri.toString()))
        }
    }
    private val exportResult = registerForActivityResult(CreateFileContract()) {
        it.save(this, this) { toastOnUi(R.string.export_success) }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
        )
        binding.composeView.setContent {
            NgAppTheme {
                ReplaceRuleScreen(
                    rules = rules,
                    groups = groups,
                    currentBookName = currentBookName,
                    onBack = ::finish,
                    onAdd = {
                        showDialogFragment(ReplaceRuleEditDialog())
                    },
                    onManageGroups = {
                        showDialogFragment<GroupManageDialog>()
                    },
                    onImportLocal = {
                        importDoc.launch(arrayOf("text/*", "application/json"))
                    },
                    onImportOnline = {
                        showDialogFragment(ReplaceRuleOnlineImportDialog())
                    },
                    onImportQr = { qrCodeResult.launch() },
                    onEdit = { rule ->
                        showDialogFragment(ReplaceRuleEditDialog(rule.id))
                    },
                    onToggleEnabled = { rule, enabled ->
                        setResult(RESULT_OK)
                        viewModel.update(rule.copy(isEnabled = enabled))
                    },
                    onMoveToTop = { rule ->
                        setResult(RESULT_OK)
                        viewModel.toTop(rule)
                    },
                    onMoveToBottom = { rule ->
                        setResult(RESULT_OK)
                        viewModel.toBottom(rule)
                    },
                    onDelete = { rule ->
                        pendingDelete = ReplaceRuleDeleteRequest(listOf(rule), rule.name)
                    },
                    onReorder = { orderedRules ->
                        setResult(RESULT_OK)
                        viewModel.updateOrder(orderedRules)
                    },
                    onEnableSelection = { selection ->
                        setResult(RESULT_OK)
                        viewModel.enableSelection(selection)
                    },
                    onDisableSelection = { selection ->
                        setResult(RESULT_OK)
                        viewModel.disableSelection(selection)
                    },
                    onMoveSelectionToTop = { selection ->
                        setResult(RESULT_OK)
                        viewModel.topSelect(selection)
                    },
                    onMoveSelectionToBottom = { selection ->
                        setResult(RESULT_OK)
                        viewModel.bottomSelect(selection)
                    },
                    onExportSelection = ::exportRules,
                    onDeleteSelection = { selection ->
                        if (selection.isNotEmpty()) {
                            pendingDelete = ReplaceRuleDeleteRequest(selection, null)
                        }
                    },
                    onDeleteSection = { title, selection ->
                        if (selection.isNotEmpty()) {
                            pendingDelete = ReplaceRuleDeleteRequest(selection, title)
                        }
                    },
                )
                pendingDelete?.let { request ->
                    RuleDeleteConfirmDialog(
                        itemName = request.itemName,
                        onDismiss = { pendingDelete = null },
                        onConfirm = {
                            pendingDelete = null
                            setResult(RESULT_OK)
                            viewModel.delSelection(request.rules)
                        },
                    )
                }
            }
        }
        observeData()
    }

    private fun observeData() {
        lifecycleScope.launch {
            appDb.replaceRuleDao.flowAll().catch {
                AppLog.put("替换规则管理界面更新数据出错", it)
            }.flowOn(IO).conflate().collect {
                if (dataInitialized) setResult(RESULT_OK)
                rules = it
                dataInitialized = true
            }
        }
        lifecycleScope.launch {
            appDb.replaceRuleDao.flowGroups().collect { groups = it }
        }
    }

    private fun exportRules(selection: List<ReplaceRule>) {
        if (selection.isEmpty()) return
        exportResult.launch(
            CreateFileContract.FileData(
                "exportReplaceRule.json",
                GSON.toJson(selection).toByteArray(),
                "application/json",
            )
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        Coroutine.async { ContentProcessor.upReplaceRules() }
    }

    override fun onReplaceRuleSaved() {
        setResult(RESULT_OK)
    }

    override fun onReplaceRuleOnlineImportConfirmed(text: String) {
        showDialogFragment(ImportReplaceRuleDialog(text))
    }

    companion object {
        private const val EXTRA_BOOK_NAME = "bookName"
        private const val EXTRA_SOURCE_NAME = "sourceName"
        private const val EXTRA_SOURCE_URL = "sourceUrl"

        fun startIntent(
            context: android.content.Context,
            bookName: String? = null,
            sourceName: String? = null,
            sourceUrl: String? = null,
        ): Intent {
            return Intent(context, ReplaceRuleActivity::class.java).apply {
                putExtra(EXTRA_BOOK_NAME, bookName)
                putExtra(EXTRA_SOURCE_NAME, sourceName)
                putExtra(EXTRA_SOURCE_URL, sourceUrl)
            }
        }
    }
}

private data class ReplaceRuleDeleteRequest(
    val rules: List<ReplaceRule>,
    val itemName: String?,
)
