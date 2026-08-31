package io.legado.app.ui.config

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.view.Window
import androidx.activity.ComponentDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.input.KeyboardType
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import io.legado.app.R
import io.legado.app.base.BaseFragment
import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.AppWebDav
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.storage.Backup
import io.legado.app.help.storage.BackupConfig
import io.legado.app.help.storage.ImportOldData
import io.legado.app.help.storage.Restore
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.selector
import io.legado.app.lib.permission.Permissions
import io.legado.app.lib.permission.PermissionsCompat
import io.legado.app.ui.about.AppLogDialog
import io.legado.app.ui.about.NetworkLogDialog
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.widget.dialog.WaitDialog
import io.legado.app.ui.widget.dialog.applyNgWindow
import io.legado.app.utils.FileDoc
import io.legado.app.utils.SelectDirectoryContract
import io.legado.app.utils.SelectFileContract
import io.legado.app.utils.checkWrite
import io.legado.app.utils.getPrefString
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefString
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.showHelp
import io.legado.app.utils.takePersistableReadPermission
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.appCtx

class BackupConfigFragment : BaseFragment(R.layout.fragment_backup_config),
    SharedPreferences.OnSharedPreferenceChangeListener {

    private val viewModel by activityViewModels<ConfigViewModel>()
    private val waitDialog by lazy { WaitDialog(requireContext()) }
    private var screenState by mutableStateOf(BackupConfigScreenState())
    private lateinit var sharedPreferences: SharedPreferences
    private var inputDialog: ComponentDialog? = null
    private var backupJob: Job? = null
    private var restoreJob: Job? = null

    private val selectBackupPath = registerForActivityResult(SelectDirectoryContract()) {
        it.uri?.let { uri ->
            AppConfig.backupPath = if (uri.isContentScheme()) uri.toString() else uri.path
        }
    }

    private val backupDir = registerForActivityResult(SelectDirectoryContract()) { result ->
        result.uri?.let { uri ->
            if (uri.isContentScheme()) {
                AppConfig.backupPath = uri.toString()
                backup(uri.toString())
            } else {
                uri.path?.let { path ->
                    AppConfig.backupPath = path
                    backup(path)
                }
            }
        }
    }

    private val restoreDoc = registerForActivityResult(SelectFileContract()) { uri ->
        uri?.let {
            it.takePersistableReadPermission()
            waitDialog.setText("恢复中…")
            waitDialog.show()
            val task = Coroutine.async {
                Restore.restore(appCtx, uri)
            }.onFinally {
                waitDialog.dismiss()
            }
            waitDialog.setOnCancelListener {
                task.cancel()
            }
        }
    }

    private val restoreOld = registerForActivityResult(SelectDirectoryContract()) {
        it.uri?.let { uri ->
            ImportOldData.importUri(appCtx, uri)
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        activity?.setTitle(R.string.backup_restore)
        setSharedTitleBarVisible(false)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
        refreshContent()
        (view as ComposeView).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
            )
            setContent {
                NgAppTheme {
                    BackupConfigScreen(
                        state = screenState,
                        onBack = { requireActivity().onBackPressedDispatcher.onBackPressed() },
                        onMenuAction = ::onToolbarMenuAction,
                        onWebDavUrlClick = ::showWebDavUrlDialog,
                        onWebDavAccountClick = ::showWebDavAccountDialog,
                        onWebDavPasswordClick = ::showWebDavPasswordDialog,
                        onWebDavDirClick = ::showWebDavDirDialog,
                        onWebDavDeviceNameClick = ::showWebDavDeviceNameDialog,
                        onSyncBookProgressChange = {
                            requireContext().putPrefBoolean(PreferKey.syncBookProgress, it)
                        },
                        onSyncBookProgressPlusChange = {
                            requireContext().putPrefBoolean(PreferKey.syncBookProgressPlus, it)
                        },
                        onLocalPasswordClick = ::showLocalPasswordDialog,
                        onBackupPathClick = { selectBackupPath.launch(null) },
                        onBackupClick = ::backup,
                        onRestoreClick = ::restore,
                        onRestoreLongClick = ::restoreFromLocal,
                        onRestoreIgnoreClick = ::backupIgnore,
                        onImportOldClick = { restoreOld.launch(null) },
                        onOnlyLatestBackupChange = {
                            requireContext().putPrefBoolean(PreferKey.onlyLatestBackup, it)
                        },
                        onAutoCheckNewBackupChange = {
                            requireContext().putPrefBoolean(PreferKey.autoCheckNewBackup, it)
                        },
                    )
                }
            }
        }
        if (!LocalConfig.backupHelpVersionIsLast) {
            showHelp("webDavHelp")
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.setTitle(R.string.backup_restore)
        setSharedTitleBarVisible(false)
        if (view != null) refreshContent()
    }

    private fun onToolbarMenuAction(itemId: Int) {
        when (itemId) {
            R.id.menu_help -> {
                showHelp("webDavHelp")
            }

            R.id.menu_log -> showDialogFragment<AppLogDialog>()
            R.id.menu_network_log -> showDialogFragment<NetworkLogDialog>()
        }
    }

    private fun setSharedTitleBarVisible(visible: Boolean) {
        activity?.findViewById<View>(R.id.title_bar)?.visibility = if (visible) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        view?.post {
            refreshContent()
            if (
                key == PreferKey.webDavUrl ||
                key == PreferKey.webDavAccount ||
                key == PreferKey.webDavPassword ||
                key == PreferKey.webDavDir
            ) {
                viewModel.upWebDavConfig()
            }
        }
    }

    private fun refreshContent() {
        val webDavUrl = getPrefString(PreferKey.webDavUrl)
        val webDavAccount = getPrefString(PreferKey.webDavAccount)
        val webDavPassword = getPrefString(PreferKey.webDavPassword)
        screenState = BackupConfigScreenState(
            webDavUrlSummary = webDavUrl.takeUnless { it.isNullOrBlank() }
                ?: getString(R.string.web_dav_url_s),
            webDavAccountSummary = webDavAccount.takeUnless { it.isNullOrBlank() }
                ?: getString(R.string.web_dav_account_s),
            webDavPasswordSummary = if (webDavPassword.isNullOrEmpty()) {
                getString(R.string.web_dav_pw_s)
            } else {
                "*".repeat(webDavPassword.length)
            },
            webDavDirSummary = AppConfig.webDavDir ?: "legado",
            webDavDeviceNameSummary = AppConfig.webDavDeviceName.orEmpty(),
            syncBookProgress = AppConfig.syncBookProgress,
            syncBookProgressPlus = AppConfig.syncBookProgressPlus,
            backupPathSummary = getPrefString(PreferKey.backupPath),
            onlyLatestBackup = AppConfig.onlyLatestBackup,
            autoCheckNewBackup = AppConfig.autoCheckNewBackup,
        )
    }

    private fun showWebDavUrlDialog() {
        showTextInputDialog(
            title = getString(R.string.web_dav_url),
            initialValue = getPrefString(PreferKey.webDavUrl).orEmpty(),
            placeholder = getString(R.string.web_dav_url_s),
            keyboardType = KeyboardType.Uri,
        ) { requireContext().putPrefString(PreferKey.webDavUrl, it) }
    }

    private fun showWebDavAccountDialog() {
        showTextInputDialog(
            title = getString(R.string.web_dav_account),
            initialValue = getPrefString(PreferKey.webDavAccount).orEmpty(),
            placeholder = getString(R.string.web_dav_account_s),
        ) { requireContext().putPrefString(PreferKey.webDavAccount, it) }
    }

    private fun showWebDavPasswordDialog() {
        showTextInputDialog(
            title = getString(R.string.web_dav_pw),
            initialValue = getPrefString(PreferKey.webDavPassword).orEmpty(),
            placeholder = getString(R.string.web_dav_pw_s),
            password = true,
            keyboardType = KeyboardType.Password,
        ) { requireContext().putPrefString(PreferKey.webDavPassword, it) }
    }

    private fun showWebDavDirDialog() {
        showTextInputDialog(
            title = getString(R.string.sub_dir),
            initialValue = AppConfig.webDavDir ?: "legado",
            placeholder = "legado",
        ) { requireContext().putPrefString(PreferKey.webDavDir, it) }
    }

    private fun showWebDavDeviceNameDialog() {
        showTextInputDialog(
            title = getString(R.string.webdav_device_name),
            initialValue = AppConfig.webDavDeviceName.orEmpty(),
            placeholder = getString(R.string.webdav_device_name),
        ) { requireContext().putPrefString(PreferKey.webDavDeviceName, it) }
    }

    private fun showLocalPasswordDialog() {
        showTextInputDialog(
            title = getString(R.string.set_local_password),
            initialValue = "",
            placeholder = "password",
            password = true,
            message = getString(R.string.set_local_password_summary),
            keyboardType = KeyboardType.Password,
        ) { LocalConfig.password = it }
    }

    private fun showTextInputDialog(
        title: String,
        initialValue: String,
        placeholder: String,
        password: Boolean = false,
        message: String? = null,
        keyboardType: KeyboardType = KeyboardType.Text,
        onConfirm: (String) -> Unit,
    ) {
        inputDialog?.dismiss()
        val dialog = ComponentDialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(
            ComposeView(requireContext()).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    NgAppTheme(updateSystemBars = false) {
                        BackupTextInputDialogContent(
                            title = title,
                            initialValue = initialValue,
                            placeholder = placeholder,
                            cancelText = getString(android.R.string.cancel),
                            confirmText = getString(android.R.string.ok),
                            password = password,
                            message = message,
                            keyboardType = keyboardType,
                            onCancel = dialog::dismiss,
                            onConfirm = { value ->
                                dialog.dismiss()
                                onConfirm(value)
                                refreshContent()
                            },
                        )
                    }
                }
            }
        )
        dialog.setOnDismissListener {
            if (inputDialog === dialog) inputDialog = null
        }
        inputDialog = dialog
        dialog.show()
        dialog.applyNgWindow()
    }

    private fun backupIgnore() {
        val checkedItems = BooleanArray(BackupConfig.ignoreKeys.size) {
            BackupConfig.ignoreConfig[BackupConfig.ignoreKeys[it]] ?: false
        }
        alert(R.string.restore_ignore) {
            multiChoiceItems(BackupConfig.ignoreTitle, checkedItems) { _, which, isChecked ->
                BackupConfig.ignoreConfig[BackupConfig.ignoreKeys[which]] = isChecked
            }
            onDismiss {
                BackupConfig.saveIgnoreConfig()
            }
        }
    }

    fun backup() {
        val backupPath = AppConfig.backupPath
        if (backupPath.isNullOrEmpty()) {
            backupDir.launch(null)
        } else if (backupPath.isContentScheme()) {
            lifecycleScope.launch {
                val canWrite = withContext(IO) {
                    FileDoc.fromDir(backupPath).checkWrite()
                }
                if (canWrite) {
                    backup(backupPath)
                } else {
                    backupDir.launch(null)
                }
            }
        } else {
            backupUsePermission(backupPath)
        }
    }

    private fun backup(backupPath: String) {
        waitDialog.setText("备份中…")
        waitDialog.setOnCancelListener {
            backupJob?.cancel()
        }
        waitDialog.show()
        backupJob?.cancel()
        backupJob = lifecycleScope.launch {
            try {
                Backup.backupLocked(requireContext(), backupPath)
                appCtx.toastOnUi(R.string.backup_success)
            } catch (e: Throwable) {
                ensureActive()
                AppLog.put("备份出错\n${e.localizedMessage}", e)
                appCtx.toastOnUi(
                    appCtx.getString(R.string.backup_fail, e.localizedMessage)
                )
            } finally {
                ensureActive()
                waitDialog.dismiss()
            }
        }
    }

    private fun backupUsePermission(path: String) {
        PermissionsCompat.Builder()
            .addPermissions(*Permissions.Group.STORAGE)
            .rationale(R.string.tip_perm_request_storage)
            .onGranted {
                backup(path)
            }
            .request()
    }

    fun restore() {
        waitDialog.setText(R.string.loading)
        waitDialog.setOnCancelListener {
            restoreJob?.cancel()
        }
        waitDialog.show()
        Coroutine.async {
            restoreJob = coroutineContext[Job]
            showRestoreDialog(requireContext())
        }.onError {
            AppLog.put("恢复备份出错WebDavError\n${it.localizedMessage}", it)
            if (context == null) return@onError
            alert {
                setTitle(R.string.restore)
                setMessage("WebDavError\n${it.localizedMessage}\n将从本地备份恢复。")
                okButton {
                    restoreFromLocal()
                }
                cancelButton()
            }
        }.onFinally {
            waitDialog.dismiss()
        }
    }

    private suspend fun showRestoreDialog(context: Context) {
        val names = withContext(IO) { AppWebDav.getBackupNames() }
        if (AppWebDav.isJianGuoYun && names.size > 700) {
            context.toastOnUi("由于坚果云限制列出文件数量，部分备份可能未显示，请及时清理旧备份")
        }
        if (names.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            withContext(Main) {
                context.selector(
                    title = context.getString(R.string.select_restore_file),
                    items = names,
                ) { _, index ->
                    if (index in names.indices) {
                        view?.post { restoreWebDav(names[index]) }
                    }
                }
            }
        } else {
            throw NoStackTraceException("Web dav no back up file")
        }
    }

    private fun restoreWebDav(name: String) {
        waitDialog.setText("恢复中…")
        waitDialog.show()
        val task = Coroutine.async {
            AppWebDav.restoreWebDav(name)
        }.onError {
            AppLog.put("WebDav恢复出错\n${it.localizedMessage}", it)
            appCtx.toastOnUi("WebDav恢复出错\n${it.localizedMessage}")
        }.onFinally {
            waitDialog.dismiss()
        }
        waitDialog.setOnCancelListener {
            task.cancel()
        }
    }

    private fun restoreFromLocal() {
        restoreDoc.launch(
            arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")
        )
    }

    override fun onDestroyView() {
        if (::sharedPreferences.isInitialized) {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        }
        inputDialog?.dismiss()
        inputDialog = null
        waitDialog.dismiss()
        setSharedTitleBarVisible(true)
        super.onDestroyView()
    }
}
