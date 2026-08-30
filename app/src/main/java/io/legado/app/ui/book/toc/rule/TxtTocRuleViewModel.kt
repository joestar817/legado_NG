package io.legado.app.ui.book.toc.rule

import android.app.Application
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.TxtTocRule
import io.legado.app.help.DefaultData

class TxtTocRuleViewModel(app: Application) : BaseViewModel(app) {

    fun save(txtTocRule: TxtTocRule) {
        execute {
            appDb.txtTocRuleDao.insert(txtTocRule)
        }
    }

    fun del(vararg txtTocRule: TxtTocRule) {
        execute {
            appDb.txtTocRuleDao.delete(*txtTocRule)
        }
    }

    fun update(vararg txtTocRule: TxtTocRule) {
        execute {
            appDb.txtTocRuleDao.update(*txtTocRule)
        }
    }

    fun importDefault() {
        execute {
            DefaultData.importDefaultTocRules()
        }
    }

    fun toTop(vararg rules: TxtTocRule) {
        execute {
            val minOrder = appDb.txtTocRuleDao.minOrder - 1
            val updated = rules.mapIndexed { index, source ->
                source.copy(serialNumber = minOrder - index)
            }
            appDb.txtTocRuleDao.update(*updated.toTypedArray())
        }
    }

    fun toBottom(vararg sources: TxtTocRule) {
        execute {
            val maxOrder = appDb.txtTocRuleDao.maxOrder + 1
            val updated = sources.mapIndexed { index, source ->
                source.copy(serialNumber = maxOrder + index)
            }
            appDb.txtTocRuleDao.update(*updated.toTypedArray())
        }
    }

    fun updateOrder(rules: List<TxtTocRule>) {
        execute {
            val updated = rules.mapIndexed { index, rule ->
                rule.copy(serialNumber = index + 1)
            }
            appDb.txtTocRuleDao.update(*updated.toTypedArray())
        }
    }

    fun enableSelection(vararg txtTocRule: TxtTocRule) {
        execute {
            val array = txtTocRule.map { it.copy(enable = true) }.toTypedArray()
            appDb.txtTocRuleDao.insert(*array)
        }
    }

    fun disableSelection(vararg txtTocRule: TxtTocRule) {
        execute {
            val array = txtTocRule.map { it.copy(enable = false) }.toTypedArray()
            appDb.txtTocRuleDao.insert(*array)
        }
    }

}
