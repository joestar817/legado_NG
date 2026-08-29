package io.legado.app.ui.login

import android.content.DialogInterface
import android.graphics.Color.TRANSPARENT
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.script.rhino.runScriptWithContext
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.data.entities.BaseSource
import io.legado.app.data.entities.rule.RowUi
import io.legado.app.model.login.LoginUiV2
import io.legado.app.model.login.evalLoginActionV2
import io.legado.app.model.login.evalLoginUiV2
import io.legado.app.model.login.isLoginUiV2
import io.legado.app.ui.about.AppLogDialog
import io.legado.app.ui.about.NetworkLogDialog
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.NgDialogTextActionButton
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.openUrl
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.sendToClip
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 动态登录表单的 Compose 宿主；无登录表单时由Activity的Compose外围承载真实WebView内核。 */
class SourceLoginDialog : DialogFragment(), SourceLoginJsExtensions.Callback {

    private val viewModel by activityViewModels<SourceLoginViewModel>()
    private var rows by mutableStateOf<List<RowUi>>(emptyList())
    private var displayNames by mutableStateOf<List<String>>(emptyList())
    private val values = mutableStateMapOf<String, String>()
    private val errors = mutableStateMapOf<String, String>()
    private val enabledActions = mutableStateMapOf<String, Boolean>()
    private val countdowns = mutableStateMapOf<String, Int>()
    private var loading by mutableStateOf(false)
    private var headerDialogText by mutableStateOf<String?>(null)
    private var isV2 = false
    private var stateJson = "{}"
    private var loginUrl: String? = null
    private var hasChange = false
    private var okToClose = false
    private var firstV2Render = true
    private lateinit var source: BaseSource
    private lateinit var sourceLoginJsExtensions: SourceLoginJsExtensions
    private val debounceJobs = mutableMapOf<String, Job>()
    private val countdownJobs = mutableMapOf<String, Job>()
    private var renderJob: Job? = null
    private var actionJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) setStyle(STYLE_NO_TITLE, 0)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        setBackgroundColor(TRANSPARENT)
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        source = viewModel.source ?: run {
            dismissAllowingStateLoss()
            return
        }
        loginUrl = source.getLoginJs()
        isV2 = source.isLoginUiV2()
        sourceLoginJsExtensions = SourceLoginJsExtensions(
            activity as? androidx.appcompat.app.AppCompatActivity,
            source,
            viewModel.bookType,
            this,
        )
        (view as ComposeView).setContent {
            NgAppTheme(updateSystemBars = false) {
                SourceLoginScreen(
                    title = getString(R.string.login_source, source.getTag()),
                    rows = rows,
                    values = values,
                    displayNames = displayNames,
                    errors = errors,
                    isV2 = isV2,
                    isLoading = loading,
                    enabledActions = enabledActions,
                    countdowns = countdowns,
                    onConfirm = ::login,
                    onShowLoginHeader = {
                        headerDialogText = source.getLoginHeader().orEmpty()
                    },
                    onDeleteLoginHeader = { source.removeLoginHeader() },
                    onAppLog = { showDialogFragment<AppLogDialog>() },
                    onNetworkLog = { showDialogFragment<NetworkLogDialog>() },
                    onValueChange = ::onValueChange,
                    onButton = ::onButton,
                )
                headerDialogText?.let { header ->
                    SourceLoginHeaderDialog(
                        header = header,
                        onDismiss = { headerDialogText = null },
                        onCopy = {
                            requireContext().sendToClip(header)
                            headerDialogText = null
                        },
                    )
                }
            }
        }
        if (isV2) renderV2() else renderLegacy()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawableResource(R.color.transparent)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }

    override fun onDestroyView() {
        renderJob?.cancel()
        actionJob?.cancel()
        debounceJobs.values.forEach(Job::cancel)
        countdownJobs.values.forEach(Job::cancel)
        super.onDestroyView()
    }

    override fun upUiData(data: Map<String, Any?>?) {
        activity?.runOnUiThread { applyLegacyData(data) }
    }

    override fun reUiView(deltaUp: Boolean) {
        activity?.runOnUiThread { renderLegacy() }
    }

    private fun renderLegacy() {
        renderJob?.cancel()
        renderJob = viewLifecycleOwner.lifecycleScope.launch {
            loading = true
            val result = withContext(IO) {
                runCatching {
                    val loginUi = source.loginUi.orEmpty()
                    val code = when {
                        loginUi.startsWith("@js:") -> loginUi.substring(4)
                        loginUi.startsWith("<js>") -> loginUi.substring(4, loginUi.lastIndexOf("<"))
                        else -> null
                    }
                    val json = code?.let { evalUiJs(it) } ?: loginUi
                    GSON.fromJsonArray<RowUi>(json).getOrThrow()
                }
            }
            loading = false
            result.onFailure {
                AppLog.put("loginUi json parse err:${it.localizedMessage}", it)
                rows = listOf(RowUi(name = it.localizedMessage ?: "loginUi error", type = RowUi.Type.label))
                displayNames = rows.map(RowUi::name)
            }.onSuccess { newRows ->
                setLegacyRows(newRows)
                resolveLegacyDisplayNames(newRows)
            }
        }
    }

    private fun setLegacyRows(newRows: List<RowUi>) {
        newRows.forEach { row ->
            when (row.type) {
                RowUi.Type.text,
                RowUi.Type.password -> values.putIfAbsent(
                    row.name,
                    viewModel.loginInfo[row.name] ?: row.default.orEmpty(),
                )
                RowUi.Type.select,
                RowUi.Type.toggle -> {
                    if (!values.containsKey(row.name)) {
                        val options = row.chars?.filterNotNull().orEmpty()
                        val value = viewModel.loginInfo[row.name]
                            ?.takeIf { it in options }
                            ?: row.default
                            ?: options.firstOrNull().orEmpty()
                        values[row.name] = value
                        viewModel.loginInfo[row.name] = value
                        hasChange = true
                    }
                }
            }
        }
        rows = newRows
        displayNames = newRows.map(::literalOrDefaultName)
    }

    private fun resolveLegacyDisplayNames(newRows: List<RowUi>) {
        newRows.forEachIndexed { index, row ->
            val expression = row.viewName?.takeUnless(::isLiteralName) ?: return@forEachIndexed
            viewLifecycleOwner.lifecycleScope.launch {
                val resolved = withContext(IO) { evalUiJs(expression) }
                    ?.takeIf(String::isNotEmpty)
                    ?: "null"
                displayNames = displayNames.toMutableList().apply { set(index, resolved) }
                row.viewName = resolved
            }
        }
    }

    private fun literalOrDefaultName(row: RowUi): String {
        val viewName = row.viewName ?: return row.name
        return if (isLiteralName(viewName)) viewName.substring(1, viewName.length - 1) else row.name
    }

    private fun isLiteralName(value: String): Boolean {
        return value.length in 3..19 && value.first() == '\'' && value.last() == '\''
    }

    private suspend fun evalUiJs(js: String): String? {
        val result = collectLegacyForm()
        return try {
            runScriptWithContext {
                source.evalJS("${loginUrl.orEmpty()}\n$js") {
                    put("result", result)
                    put("book", viewModel.book)
                    put("chapter", viewModel.chapter)
                }.toString()
            }
        } catch (e: Exception) {
            AppLog.put(source.getTag() + " loginUi err:" + (e.localizedMessage ?: e.toString()), e)
            null
        }
    }

    private fun applyLegacyData(data: Map<String, Any?>?) {
        hasChange = true
        if (data == null) {
            rows.forEach { row ->
                when (row.type) {
                    RowUi.Type.text,
                    RowUi.Type.password,
                    RowUi.Type.select,
                    RowUi.Type.toggle -> {
                        val options = row.chars?.filterNotNull().orEmpty()
                        values[row.name] = row.default ?: options.firstOrNull().orEmpty()
                    }
                }
            }
        } else {
            data.forEach { (key, value) -> values[key] = value?.toString().orEmpty() }
        }
        viewModel.loginInfo.putAll(values)
    }

    private fun onValueChange(row: RowUi, value: String) {
        val key = if (isV2) row.key.orEmpty() else row.name
        values[key] = value
        errors.remove(key)
        if (isV2) {
            if (row.type == RowUi.Type.toggle && row.action != null) {
                dispatchV2(requireNotNull(row.action), null)
            }
            return
        }
        hasChange = true
        viewModel.loginInfo[row.name] = value
        when (row.type) {
            RowUi.Type.text,
            RowUi.Type.password -> row.action?.let { action ->
                debounceJobs.remove(row.name)?.cancel()
                debounceJobs[row.name] = viewLifecycleOwner.lifecycleScope.launch {
                    delay(600L)
                    dispatchLegacy(action, row.name, false)
                }
            }
            RowUi.Type.select -> row.action?.let { dispatchLegacy(it, row.name, false) }
        }
    }

    private fun onButton(row: RowUi, longClick: Boolean) {
        if (isV2) {
            row.action?.let { dispatchV2(it, row.countdown) }
            return
        }
        if (row.type == RowUi.Type.toggle) {
            val options = row.chars?.filterNotNull().orEmpty()
            if (options.isNotEmpty()) {
                val current = values[row.name]
                val next = options[(options.indexOf(current).coerceAtLeast(0) + 1) % options.size]
                values[row.name] = next
                viewModel.loginInfo[row.name] = next
                hasChange = true
            }
        }
        dispatchLegacy(row.action, row.name, longClick)
    }

    private fun dispatchLegacy(action: String?, name: String, longClick: Boolean) {
        if (action == null) return
        viewLifecycleOwner.lifecycleScope.launch(IO) {
            if (action.isAbsUrl()) {
                context?.openUrl(action)
                return@launch
            }
            runCatching {
                runScriptWithContext {
                    source.evalJS("${loginUrl.orEmpty()}\n$action") {
                        put("java", sourceLoginJsExtensions)
                        put("result", collectLegacyForm())
                        put("book", viewModel.book)
                        put("chapter", viewModel.chapter)
                        put("isLongClick", longClick)
                    }
                }
            }.onFailure {
                ensureActive()
                AppLog.put("LoginUI Button $name JavaScript error", it)
            }
        }
    }

    private fun collectLegacyForm(): MutableMap<String, String> {
        return viewModel.loginInfo.toMutableMap().apply {
            rows.forEach { row ->
                if (row.type == RowUi.Type.text || row.type == RowUi.Type.password ||
                    row.type == RowUi.Type.select || row.type == RowUi.Type.toggle
                ) {
                    put(row.name, this@SourceLoginDialog.values[row.name].orEmpty())
                }
            }
        }
    }

    private fun login() {
        okToClose = true
        viewLifecycleOwner.lifecycleScope.launch(IO) {
            val loginData = collectLegacyForm()
            if (loginData.isEmpty()) {
                source.removeLoginInfo()
                withContext(Main) { dismiss() }
            } else if (source.putLoginInfo(GSON.toJson(loginData))) {
                try {
                    runScriptWithContext {
                        source.evalJS(
                            "${loginUrl.orEmpty()}\n" +
                                "if (typeof login=='function'){ login.apply(this); } " +
                                "else { throw('Function login not implements!!!') }"
                        ) {
                            put("java", sourceLoginJsExtensions)
                            put("result", loginData)
                            put("book", viewModel.book)
                            put("chapter", viewModel.chapter)
                            put("isLongClick", false)
                        }
                    }
                    context?.toastOnUi(R.string.success)
                    withContext(Main) { dismiss() }
                } catch (e: Exception) {
                    AppLog.put("登录出错\n${e.localizedMessage}", e)
                    context?.toastOnUi("登录出错\n${e.localizedMessage}")
                    e.printOnDebug()
                }
            }
        }
    }

    private fun renderV2(
        candidateState: String = stateJson,
        commandErrors: Map<String, String> = emptyMap(),
        restoreAction: String? = null,
    ) {
        renderJob?.cancel()
        renderJob = viewLifecycleOwner.lifecycleScope.launch {
            val showLoading = firstV2Render
            if (showLoading) loading = true
            val sessionValues = values.toMap()
            val result = withContext(IO) {
                runCatching {
                    runScriptWithContext {
                        LoginUiV2.parseRender(source.evalLoginUiV2(candidateState)) to
                            source.getLoginInfoMap()
                    }
                }.onFailure { ensureActive() }
            }
            if (showLoading) {
                firstV2Render = false
                loading = false
            }
            val newRows = result.getOrNull()?.first
            if (newRows == null) {
                restoreAction?.let { enabledActions[it] = true }
                result.exceptionOrNull()?.let { AppLog.put("登录 UI v2 渲染出错", it) }
                    ?: AppLog.put("登录 UI v2 渲染结果格式错误")
                if (rows.isEmpty()) {
                    rows = listOf(
                        RowUi(
                            name = getString(R.string.login_ui_v2_render_error),
                            type = RowUi.Type.label,
                        )
                    )
                    displayNames = rows.map(RowUi::name)
                } else {
                    context?.toastOnUi(R.string.login_ui_v2_render_error)
                    errors.putAll(commandErrors)
                }
                return@launch
            }
            stateJson = candidateState
            values.clear()
            newRows.forEach { row ->
                if (row.type == RowUi.Type.text || row.type == RowUi.Type.password ||
                    row.type == RowUi.Type.select || row.type == RowUi.Type.toggle
                ) {
                    val key = requireNotNull(row.key)
                    val options = row.options.orEmpty()
                    values[key] = LoginUiV2.resolveFieldValue(
                        row.value,
                        sessionValues[key],
                        result.getOrThrow().second[key],
                    )?.takeIf { row.type != RowUi.Type.select || it in options }
                        ?: options.firstOrNull().orEmpty()
                }
            }
            rows = newRows
            displayNames = newRows.map(RowUi::name)
            errors.clear()
            errors.putAll(commandErrors)
        }
    }

    private fun dispatchV2(action: String, countdownSeconds: Int?) {
        if (renderJob?.isActive == true || actionJob?.isActive == true ||
            countdowns.getOrDefault(action, 0) > 0
        ) return
        errors.clear()
        enabledActions[action] = false
        val formJson = GSON.toJson(values.toMap())
        actionJob = viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(IO) {
                runCatching {
                    runScriptWithContext {
                        source.evalLoginActionV2(action, stateJson, formJson)
                    }
                }.onFailure { ensureActive() }
            }
            result.exceptionOrNull()?.let { error ->
                enabledActions[action] = true
                AppLog.put("登录 UI v2 动作 $action 出错", error)
                context?.toastOnUi(
                    getString(
                        R.string.login_ui_v2_action_error,
                        error.localizedMessage ?: error.toString(),
                    )
                )
                return@launch
            }
            val command = LoginUiV2.parseActionResult(result.getOrNull())
            if (command.malformed) {
                enabledActions[action] = true
                AppLog.put("登录 UI v2 动作 $action 返回了无效命令")
                context?.toastOnUi(R.string.login_ui_v2_invalid_action)
                return@launch
            }
            command.unknownKeys.forEach {
                AppLog.put("登录 UI v2 动作 $action 返回未知命令 $it, 已忽略")
            }
            command.loginJson?.let { loginJson ->
                if (!withContext(IO) { source.putLoginInfo(loginJson) }) {
                    enabledActions[action] = true
                    context?.toastOnUi(R.string.login_ui_v2_save_error)
                    return@launch
                }
            }
            if (command.close) {
                dismissAllowingStateLoss()
                return@launch
            }
            val commandErrors = command.error.orEmpty()
            if (command.stateJson == null) enabledActions[action] = true
            if (commandErrors.isEmpty() && countdownSeconds != null && countdownSeconds > 0) {
                startCountdown(action, countdownSeconds)
            }
            command.stateJson?.let {
                renderV2(it, commandErrors, action)
            } ?: errors.putAll(commandErrors)
        }
    }

    private fun startCountdown(action: String, seconds: Int) {
        countdownJobs.remove(action)?.cancel()
        countdownJobs[action] = viewLifecycleOwner.lifecycleScope.launch {
            for (left in seconds downTo 1) {
                countdowns[action] = left
                delay(1000L)
            }
            countdowns.remove(action)
            enabledActions[action] = true
            countdownJobs.remove(action)
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        if (!isV2 && !okToClose && hasChange) {
            val loginInfo = collectLegacyForm()
            if (loginInfo.isEmpty()) source.removeLoginInfo()
            else source.putLoginInfo(GSON.toJson(loginInfo))
        }
        super.onDismiss(dialog)
        activity?.finish()
    }
}

@Composable
private fun SourceLoginHeaderDialog(
    header: String,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        NgDialog(
            title = stringResource(R.string.login_header),
            variant = NgDialogVariant.CLASSIC_CONFIRMATION,
            actions = {
                NgDialogTextActionButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                )
                NgDialogTextActionButton(
                    text = stringResource(R.string.copy_text),
                    onClick = onCopy,
                    enabled = header.isNotEmpty(),
                )
            },
        ) {
            Text(
                text = header,
                modifier = Modifier.padding(vertical = 4.dp),
                color = Color(NgTheme.colors.onSurface),
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
        }
    }
}
