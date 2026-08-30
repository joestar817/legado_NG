package io.legado.app.ui.replace.edit

import android.app.Application
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.book.ContentProcessor
import io.legado.app.utils.*
import kotlinx.coroutines.Dispatchers

class ReplaceEditViewModel(application: Application) : BaseViewModel(application) {

    var replaceRule: ReplaceRule? = null

    fun initData(
        id: Long,
        pattern: String?,
        isRegex: Boolean,
        scope: String?,
        finally: (replaceRule: ReplaceRule) -> Unit,
    ) {
        replaceRule?.let {
            finally(it)
            return
        }
        execute {
            replaceRule = if (id > 0) {
                appDb.replaceRuleDao.findById(id)
            } else {
                val initialPattern = pattern ?: ""
                ReplaceRule(
                    name = initialPattern,
                    pattern = initialPattern,
                    isRegex = isRegex,
                    scope = scope
                )
            }
        }.onFinally {
            replaceRule?.let {
                finally(it)
            }
        }
    }

    fun pasteRule(success: (ReplaceRule) -> Unit) {
        execute(context = Dispatchers.Main) {
            val text = context.getClipText()
            if (text.isNullOrBlank()) {
                throw NoStackTraceException("剪贴板为空")
            }
            GSON.fromJsonObject<ReplaceRule>(text).getOrNull()
                ?: throw NoStackTraceException("格式不对")
        }.onSuccess {
            success.invoke(it)
        }.onError {
            context.toastOnUi(it.localizedMessage ?: "Error")
            it.printOnDebug()
        }
    }

    fun save(replaceRule: ReplaceRule, success: () -> Unit) {
        execute {
            replaceRule.checkValid()
            val savedRule = if (replaceRule.order == Int.MIN_VALUE) {
                replaceRule.copy(order = appDb.replaceRuleDao.maxOrder + 1)
            } else {
                replaceRule
            }
            appDb.replaceRuleDao.insert(savedRule)
            this@ReplaceEditViewModel.replaceRule = savedRule
            ContentProcessor.upReplaceRules()
        }.onSuccess {
            success()
        }.onError {
            context.toastOnUi("save error, ${it.localizedMessage}")
        }
    }

}
