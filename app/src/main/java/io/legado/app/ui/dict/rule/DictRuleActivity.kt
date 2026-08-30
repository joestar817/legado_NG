package io.legado.app.ui.dict.rule

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
import io.legado.app.data.entities.DictRule
import io.legado.app.ui.association.ImportDictRuleDialog
import io.legado.app.ui.association.RuleDeleteConfirmDialog
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.qrcode.QrCodeResult
import io.legado.app.utils.CreateFileContract
import io.legado.app.utils.GSON
import io.legado.app.utils.SelectFileContract
import io.legado.app.utils.launch
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.showHelp
import io.legado.app.utils.takePersistableReadPermission
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

class DictRuleActivity :
    VMBaseActivity<ComposeActivityBinding, DictRuleViewModel>(),
    DictRuleOnlineImportDialog.Callback {

    override val viewModel by viewModels<DictRuleViewModel>()
    override val binding by viewBinding(ComposeActivityBinding::inflate)
    override val bindNgToolbarMenu: Boolean = false

    private var rules by mutableStateOf<List<DictRule>>(
        emptyList(),
        referentialEqualityPolicy(),
    )
    private var pendingDelete by mutableStateOf<DictRuleDeleteRequest?>(null)
    private val qrCodeResult = registerForActivityResult(QrCodeResult()) {
        it ?: return@registerForActivityResult
        showDialogFragment(ImportDictRuleDialog(it))
    }
    private val importDoc = registerForActivityResult(SelectFileContract()) { uri ->
        uri?.let {
            it.takePersistableReadPermission()
            showDialogFragment(ImportDictRuleDialog(uri.toString()))
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
                DictRuleScreen(
                    rules = rules,
                    onBack = ::finish,
                    onAdd = { showDialogFragment(DictRuleEditDialog()) },
                    onImportLocal = {
                        importDoc.launch(arrayOf("text/*", "application/json"))
                    },
                    onImportOnline = {
                        showDialogFragment(DictRuleOnlineImportDialog())
                    },
                    onImportQr = { qrCodeResult.launch() },
                    onImportDefault = viewModel::importDefault,
                    onHelp = { showHelp("dictRuleHelp") },
                    onEdit = { showDialogFragment(DictRuleEditDialog(it.name)) },
                    onToggleEnabled = { rule, enabled ->
                        viewModel.update(rule.copy(enabled = enabled))
                    },
                    onMoveToTop = viewModel::toTop,
                    onMoveToBottom = viewModel::toBottom,
                    onDelete = { rule ->
                        pendingDelete = DictRuleDeleteRequest(listOf(rule), rule.name)
                    },
                    onReorder = viewModel::updateOrder,
                    onEnableSelection = { selection ->
                        viewModel.enableSelection(*selection.toTypedArray())
                    },
                    onDisableSelection = { selection ->
                        viewModel.disableSelection(*selection.toTypedArray())
                    },
                    onExportSelection = ::exportRules,
                    onDeleteSelection = { selection ->
                        if (selection.isNotEmpty()) {
                            pendingDelete = DictRuleDeleteRequest(selection, null)
                        }
                    },
                )
                pendingDelete?.let { request ->
                    RuleDeleteConfirmDialog(
                        itemName = request.itemName,
                        onDismiss = { pendingDelete = null },
                        onConfirm = {
                            pendingDelete = null
                            viewModel.delete(*request.rules.toTypedArray())
                        },
                    )
                }
            }
        }
        lifecycleScope.launch {
            appDb.dictRuleDao.flowAll().catch {
                AppLog.put("字典规则获取数据失败\n${it.localizedMessage}", it)
            }.flowOn(IO).conflate().collect { rules = it }
        }
    }

    private fun exportRules(selection: List<DictRule>) {
        if (selection.isEmpty()) return
        exportResult.launch(
            CreateFileContract.FileData(
                "exportDictRule.json",
                GSON.toJson(selection).toByteArray(),
                "application/json",
            )
        )
    }

    override fun onDictRuleOnlineImportConfirmed(text: String) {
        showDialogFragment(ImportDictRuleDialog(text))
    }
}

private data class DictRuleDeleteRequest(
    val rules: List<DictRule>,
    val itemName: String?,
)
