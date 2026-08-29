package io.legado.app.ui.book.source.edit

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withStateAtLeast
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.base.ComposeActivityBinding
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.model.jsSource.JsSourceUpsert
import io.legado.app.ui.book.source.debug.BookSourceDebugActivity
import io.legado.app.ui.code.CodeEditActivity
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.utils.StartActivityContract
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** JS 书源的保存、调试与登录编排，实际代码编辑复用统一代码编辑器。 */
class JsSourceEditActivity : BaseActivity<ComposeActivityBinding>(imageBg = false) {

    companion object {
        private const val STATE_OPENED_SOURCE_URL = "openedSourceUrl"
        private const val STATE_PENDING_TEXT = "pendingText"
        private const val STATE_STAGE = "stage"
    }

    override val binding by viewBinding(ComposeActivityBinding::inflate)

    private var openedSourceUrl: String? = null
    private var pendingText: String? = null
    private var stage = JsSourceEditStage.READY

    private val debugResult = registerForActivityResult(
        StartActivityContract(BookSourceDebugActivity::class.java)
    ) {
        stage = stage.afterDebugResult()
        pendingText?.let(::openEditor) ?: super.finish()
    }

    private val editorResult = registerForActivityResult(
        StartActivityContract(CodeEditActivity::class.java)
    ) { result ->
        val text = result.data?.getStringExtra("text")
        if (result.resultCode != Activity.RESULT_OK || text == null) {
            stage = JsSourceEditStage.READY
            super.finish()
            return@registerForActivityResult
        }
        pendingText = text
        val action = result.data?.getStringExtra(CodeEditActivity.EXTRA_RESULT_ACTION)
        val debugRequested = action == CodeEditActivity.RESULT_ACTION_DEBUG_SOURCE
        val loginRequested = action == CodeEditActivity.RESULT_ACTION_LOGIN_SOURCE
        stage = stageForEditorResult(debugRequested, loginRequested)
        when {
            debugRequested -> saveForDebug(text)
            loginRequested -> saveForLogin(text)
            else -> saveSource(text)
        }
    }

    private val loginResult = registerForActivityResult(
        StartActivityContract(SourceLoginActivity::class.java)
    ) {
        stage = stage.afterLoginResult()
        pendingText?.let(::openEditor) ?: super.finish()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.composeView.setContent {
            NgAppTheme {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
        openedSourceUrl = savedInstanceState?.getString(STATE_OPENED_SOURCE_URL)
            ?: intent.getStringExtra("sourceUrl")
        pendingText = savedInstanceState?.getString(STATE_PENDING_TEXT)
        stage = savedInstanceState?.getString(STATE_STAGE)
            ?.let { runCatching { JsSourceEditStage.valueOf(it) }.getOrNull() }
            ?: JsSourceEditStage.READY
        lifecycleScope.launch {
            val text = pendingText ?: withContext(Dispatchers.IO) {
                openedSourceUrl?.let { appDb.bookSourceDao.getBookSource(it)?.mainJs }
                    ?: assets.open("js_source_template.js").bufferedReader().use { it.readText() }
            }
            pendingText = text
            when (stage.restoreAction()) {
                JsSourceEditRestoreAction.OPEN_EDITOR -> openEditor(text)
                JsSourceEditRestoreAction.SAVE_AND_FINISH -> saveSource(text)
                JsSourceEditRestoreAction.SAVE_FOR_DEBUG -> saveForDebug(text)
                JsSourceEditRestoreAction.SAVE_FOR_LOGIN -> saveForLogin(text)
                JsSourceEditRestoreAction.LAUNCH_DEBUG -> launchDebugWhenResumed()
                JsSourceEditRestoreAction.LAUNCH_LOGIN -> launchLoginWhenResumed()
                JsSourceEditRestoreAction.AWAIT_RESULT -> Unit
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_OPENED_SOURCE_URL, openedSourceUrl)
        outState.putString(STATE_PENDING_TEXT, pendingText)
        outState.putString(STATE_STAGE, stage.name)
    }

    private fun openEditor(text: String) {
        if (stage == JsSourceEditStage.EDITOR_OPEN || isFinishing) return
        stage = JsSourceEditStage.EDITOR_OPEN
        editorResult.launch {
            putExtra("text", text)
            putExtra("title", getString(R.string.js_source_edit))
            putExtra("languageName", "source.js")
            putExtra("returnUnchangedText", true)
            putExtra(CodeEditActivity.EXTRA_CONFIRM_SAVE_ON_EXIT, openedSourceUrl == null)
            putExtra(CodeEditActivity.EXTRA_SHOW_DEBUG_SOURCE, true)
            putExtra(CodeEditActivity.EXTRA_SHOW_LOGIN_SOURCE, true)
        }
    }

    private fun saveForDebug(text: String) {
        saveSource(text, showSuccessToast = false, finishAfterSave = false) {
            launchDebugWhenResumed()
        }
    }

    private fun launchDebugWhenResumed() {
        val sourceUrl = openedSourceUrl ?: run {
            val text = pendingText ?: return super.finish()
            stage = JsSourceEditStage.SAVING_FOR_DEBUG
            saveForDebug(text)
            return
        }
        lifecycleScope.launch {
            lifecycle.withStateAtLeast(Lifecycle.State.RESUMED) {
                if (stage != JsSourceEditStage.DEBUG_READY) return@withStateAtLeast
                stage = JsSourceEditStage.DEBUG_OPEN
                debugResult.launch { putExtra("key", sourceUrl) }
            }
        }
    }

    private fun saveForLogin(text: String) {
        saveSource(text, showSuccessToast = false, finishAfterSave = false) { source ->
            if (!source.loginUrl.isNullOrBlank() || !source.loginUi.isNullOrBlank()) {
                launchLoginWhenResumed()
            } else {
                stage = JsSourceEditStage.READY
                toastOnUi(R.string.source_no_login)
                pendingText?.let(::openEditor) ?: super.finish()
            }
        }
    }

    private fun launchLoginWhenResumed() {
        val sourceUrl = openedSourceUrl ?: run {
            val text = pendingText ?: return super.finish()
            stage = JsSourceEditStage.SAVING_FOR_LOGIN
            saveForLogin(text)
            return
        }
        lifecycleScope.launch {
            lifecycle.withStateAtLeast(Lifecycle.State.RESUMED) {
                if (stage != JsSourceEditStage.LOGIN_READY) return@withStateAtLeast
                stage = JsSourceEditStage.LOGIN_OPEN
                loginResult.launch {
                    putExtra("type", "bookSource")
                    putExtra("key", sourceUrl)
                }
            }
        }
    }

    private fun saveSource(
        text: String,
        showSuccessToast: Boolean = true,
        finishAfterSave: Boolean = true,
        onSuccess: ((BookSource) -> Unit)? = null,
    ) {
        lifecycleScope.launch {
            try {
                val source = JsSourceUpsert.save(text, openedSourceUrl)
                openedSourceUrl = source.bookSourceUrl
                stage = stage.afterSuccessfulSave()
                pendingText = source.mainJs ?: text
                if (showSuccessToast) toastOnUi(R.string.success)
                setResult(Activity.RESULT_OK, Intent().putExtra("origin", source.bookSourceUrl))
                onSuccess?.invoke(source)
                if (finishAfterSave) super.finish()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                stage = JsSourceEditStage.READY
                toastOnUi(error.localizedMessage)
                openEditor(text)
            }
        }
    }
}
