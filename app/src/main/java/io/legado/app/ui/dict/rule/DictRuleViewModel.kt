package io.legado.app.ui.dict.rule

import android.app.Application
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.DictRule
import io.legado.app.help.DefaultData
import io.legado.app.utils.toastOnUi

class DictRuleViewModel(application: Application) : BaseViewModel(application) {


    fun update(vararg dictRule: DictRule) {
        execute {
            appDb.dictRuleDao.update(*dictRule)
        }.onError {
            val msg = "更新字典规则出错\n${it.localizedMessage}"
            AppLog.put(msg, it)
            context.toastOnUi(msg)
        }
    }

    fun delete(vararg dictRule: DictRule) {
        execute {
            appDb.dictRuleDao.delete(*dictRule)
        }.onError {
            val msg = "删除字典规则出错\n${it.localizedMessage}"
            AppLog.put(msg, it)
            context.toastOnUi(msg)
        }
    }

    fun toTop(rule: DictRule) {
        execute {
            val minOrder = appDb.dictRuleDao.all.minOfOrNull(DictRule::sortNumber) ?: 0
            appDb.dictRuleDao.update(rule.copy(sortNumber = minOrder - 1))
        }
    }

    fun toBottom(rule: DictRule) {
        execute {
            val maxOrder = appDb.dictRuleDao.all.maxOfOrNull(DictRule::sortNumber) ?: 0
            appDb.dictRuleDao.update(rule.copy(sortNumber = maxOrder + 1))
        }
    }

    fun updateOrder(rules: List<DictRule>) {
        execute {
            val updated = rules.mapIndexed { index, rule ->
                rule.copy(sortNumber = index + 1)
            }
            appDb.dictRuleDao.insert(*updated.toTypedArray())
        }
    }

    fun enableSelection(vararg dictRule: DictRule) {
        execute {
            val array = dictRule.map { it.copy(enabled = true) }.toTypedArray()
            appDb.dictRuleDao.insert(*array)
        }
    }

    fun disableSelection(vararg dictRule: DictRule) {
        execute {
            val array = dictRule.map { it.copy(enabled = false) }.toTypedArray()
            appDb.dictRuleDao.insert(*array)
        }
    }

    fun importDefault() {
        execute {
            DefaultData.importDefaultDictRules()
        }
    }

}
