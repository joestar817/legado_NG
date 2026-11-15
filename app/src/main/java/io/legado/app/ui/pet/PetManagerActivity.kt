package io.legado.app.ui.pet

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import io.legado.app.base.BaseActivity
import io.legado.app.base.ComposeActivityBinding
import io.legado.app.help.pet.PetLocalManager
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.resource.ResourceCenterActivity
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PetManagerActivity : BaseActivity<ComposeActivityBinding>() {
    companion object { const val PLUGIN_ID = "pluginId" }
    override val binding by viewBinding(ComposeActivityBinding::inflate)
    override val bindNgToolbarMenu: Boolean = false
    private val model by viewModels<PetManagerViewModel>()
    private val importZip = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.inspect(uri) else model.updateId = null
    }
    private val exportZip = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) model.export(uri) else model.exportId = null
    }
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        lifecycleScope.launch { model.events.collect { toastOnUi(it) } }
        binding.composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        binding.composeView.setContent {
            NgAppTheme {
                val state by model.state.collectAsState()
                PetManagerScreen(
                    state = state,
                    initialId = intent.getStringExtra(PLUGIN_ID),
                    onBack = ::finish,
                    onImport = { id ->
                        model.updateId = id
                        importZip.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                    },
                    onMore = {
                        startActivity(Intent(this, ResourceCenterActivity::class.java).putExtra("resourcePurpose", "PETS"))
                    },
                    onUse = model::use,
                    onUseFromDetail = model::useFromDetail,
                    onUseButton = model::useButton,
                    onEntryEnabledChanged = model::setEntryEnabled,
                    onReload = model::reload,
                    onUninstall = model::uninstall,
                    onExport = { id -> model.exportId = id; exportZip.launch("$id.zip") },
                    onDiagnostic = ::copyDiagnostic,
                    onConfirmImport = model::install,
                    onDismissImport = model::dismissImport,
                    onRetry = model::refresh,
                )
            }
        }
    }
    override fun onResume() { super.onResume(); model.refresh() }
    private fun copyDiagnostic(id: String) {
        lifecycleScope.launch {
            try {
                val text = withContext(Dispatchers.IO) { PetLocalManager.diagnostics(id) }
                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("宠物诊断", text))
                toastOnUi("已复制诊断")
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { toastOnUi("无法读取诊断") }
        }
    }
}
