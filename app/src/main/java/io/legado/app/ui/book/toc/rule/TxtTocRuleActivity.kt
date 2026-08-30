package io.legado.app.ui.book.toc.rule

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
import io.legado.app.data.entities.TxtTocRule
import io.legado.app.ui.association.ImportTxtTocRuleDialog
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

/** TXT目录规则。主页面顶栏、列表、选择和拖排均使用Compose。 */
class TxtTocRuleActivity :
    VMBaseActivity<ComposeActivityBinding, TxtTocRuleViewModel>(),
    TxtTocRuleEditDialog.Callback,
    TxtTocRuleOnlineImportDialog.Callback {

    override val viewModel by viewModels<TxtTocRuleViewModel>()
    override val binding by viewBinding(ComposeActivityBinding::inflate)
    override val bindNgToolbarMenu: Boolean = false

    private var rules by mutableStateOf<List<TxtTocRule>>(
        emptyList(),
        referentialEqualityPolicy(),
    )
    private var pendingDelete by mutableStateOf<TxtRuleDeleteRequest?>(null)
    private val qrCodeResult = registerForActivityResult(QrCodeResult()) {
        it ?: return@registerForActivityResult
        showDialogFragment(ImportTxtTocRuleDialog(it))
    }
    private val importDoc = registerForActivityResult(SelectFileContract()) { uri ->
        uri?.let {
            it.takePersistableReadPermission()
            showDialogFragment(ImportTxtTocRuleDialog(uri.toString()))
        }
    }
    private val exportResult = registerForActivityResult(CreateFileContract()) {
        it.save(this, this) { toastOnUi(R.string.export_success) }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        initContent()
        initData()
    }

    private fun initContent() {
        binding.composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
        )
        binding.composeView.setContent {
            NgAppTheme {
                TxtTocRuleScreen(
                    rules = rules,
                    onBack = ::finish,
                    onAdd = { showDialogFragment(TxtTocRuleEditDialog()) },
                    onImportLocal = {
                        importDoc.launch(arrayOf("text/*", "application/json"))
                    },
                    onImportOnline = {
                        showDialogFragment(TxtTocRuleOnlineImportDialog())
                    },
                    onImportQr = { qrCodeResult.launch() },
                    onImportDefault = viewModel::importDefault,
                    onHelp = { showHelp("txtTocRuleHelp") },
                    onEdit = { showDialogFragment(TxtTocRuleEditDialog(it.id)) },
                    onToggleEnabled = { rule, enabled ->
                        viewModel.update(rule.copy(enable = enabled))
                    },
                    onMoveToTop = { viewModel.toTop(it) },
                    onMoveToBottom = { viewModel.toBottom(it) },
                    onDelete = { rule ->
                        pendingDelete = TxtRuleDeleteRequest(listOf(rule), rule.name)
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
                            pendingDelete = TxtRuleDeleteRequest(selection, null)
                        }
                    },
                )
                pendingDelete?.let { request ->
                    RuleDeleteConfirmDialog(
                        itemName = request.itemName,
                        onDismiss = { pendingDelete = null },
                        onConfirm = {
                            pendingDelete = null
                            viewModel.del(*request.rules.toTypedArray())
                        },
                    )
                }
            }
        }
    }

    private fun initData() {
        lifecycleScope.launch {
            appDb.txtTocRuleDao.observeAll().catch {
                AppLog.put("TXT目录规则界面获取数据失败\n${it.localizedMessage}", it)
            }.flowOn(IO).conflate().collect { rules = it }
        }
    }

    private fun exportRules(selection: List<TxtTocRule>) {
        if (selection.isEmpty()) return
        exportResult.launch(
            CreateFileContract.FileData(
                "exportTxtTocRule.json",
                GSON.toJson(selection).toByteArray(),
                "application/json",
            ),
        )
    }

    override fun saveTxtTocRule(txtTocRule: TxtTocRule) {
        viewModel.save(txtTocRule)
    }

    override fun onTxtTocRuleOnlineImportConfirmed(text: String) {
        showDialogFragment(ImportTxtTocRuleDialog(text))
    }
}

private data class TxtRuleDeleteRequest(
    val rules: List<TxtTocRule>,
    val itemName: String?,
)
