package io.legado.app.ui.replace

import android.app.Application
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.help.book.ContentProcessor
import io.legado.app.utils.splitNotBlank

/**
 * 替换规则数据修改
 * 修改数据要copy,直接修改会导致界面不刷新
 */
class ReplaceRuleViewModel(application: Application) : BaseViewModel(application) {

    fun update(vararg rule: ReplaceRule) {
        execute {
            appDb.replaceRuleDao.update(*rule)
            ContentProcessor.upReplaceRules()
        }
    }

    fun delete(rule: ReplaceRule) {
        execute {
            appDb.replaceRuleDao.delete(rule)
            ContentProcessor.upReplaceRules()
        }
    }

    fun toTop(rule: ReplaceRule) {
        execute {
            appDb.replaceRuleDao.update(
                rule.copy(order = appDb.replaceRuleDao.minOrder - 1)
            )
            ContentProcessor.upReplaceRules()
        }
    }

    fun topSelect(rules: List<ReplaceRule>) {
        execute {
            var minOrder = appDb.replaceRuleDao.minOrder - rules.size
            val updates = rules.map {
                it.copy(order = ++minOrder)
            }
            appDb.replaceRuleDao.update(*updates.toTypedArray())
            ContentProcessor.upReplaceRules()
        }
    }

    fun toBottom(rule: ReplaceRule) {
        execute {
            appDb.replaceRuleDao.update(
                rule.copy(order = appDb.replaceRuleDao.maxOrder + 1)
            )
            ContentProcessor.upReplaceRules()
        }
    }

    fun bottomSelect(rules: List<ReplaceRule>) {
        execute {
            var maxOrder = appDb.replaceRuleDao.maxOrder
            val updates = rules.map {
                it.copy(order = maxOrder++)
            }
            appDb.replaceRuleDao.update(*updates.toTypedArray())
            ContentProcessor.upReplaceRules()
        }
    }

    fun updateOrder(rules: List<ReplaceRule>) {
        execute {
            val updates = rules.mapIndexed { index, rule ->
                rule.copy(order = index + 1)
            }
            appDb.replaceRuleDao.update(*updates.toTypedArray())
            ContentProcessor.upReplaceRules()
        }
    }

    fun upOrder() {
        execute {
            val rules = appDb.replaceRuleDao.all
            for ((index, rule) in rules.withIndex()) {
                rule.order = index + 1
            }
            appDb.replaceRuleDao.update(*rules.toTypedArray())
            ContentProcessor.upReplaceRules()
        }
    }

    fun enableSelection(rules: List<ReplaceRule>) {
        execute {
            val array = Array(rules.size) {
                rules[it].copy(isEnabled = true)
            }
            appDb.replaceRuleDao.update(*array)
            ContentProcessor.upReplaceRules()
        }
    }

    fun disableSelection(rules: List<ReplaceRule>) {
        execute {
            val array = Array(rules.size) {
                rules[it].copy(isEnabled = false)
            }
            appDb.replaceRuleDao.update(*array)
            ContentProcessor.upReplaceRules()
        }
    }

    fun delSelection(rules: List<ReplaceRule>) {
        execute {
            appDb.replaceRuleDao.delete(*rules.toTypedArray())
            ContentProcessor.upReplaceRules()
        }
    }

    fun addGroup(group: String) {
        execute {
            val updates = appDb.replaceRuleDao.noGroup.map { source ->
                source.copy(group = group)
            }
            appDb.replaceRuleDao.update(*updates.toTypedArray())
            ContentProcessor.upReplaceRules()
        }
    }

    fun upGroup(oldGroup: String, newGroup: String?) {
        execute {
            val updates = appDb.replaceRuleDao.getByGroup(oldGroup).map { source ->
                val groups = source.group?.splitNotBlank(",")?.toMutableSet() ?: mutableSetOf()
                groups.remove(oldGroup)
                if (!newGroup.isNullOrEmpty()) groups.add(newGroup)
                source.copy(group = groups.joinToString(","))
            }
            appDb.replaceRuleDao.update(*updates.toTypedArray())
            ContentProcessor.upReplaceRules()
        }
    }

    fun delGroup(group: String) {
        execute {
            val updates = appDb.replaceRuleDao.getByGroup(group).map { source ->
                val groups = source.group?.splitNotBlank(",")?.toMutableSet() ?: mutableSetOf()
                groups.remove(group)
                source.copy(group = groups.joinToString(","))
            }
            appDb.replaceRuleDao.update(*updates.toTypedArray())
            ContentProcessor.upReplaceRules()
        }
    }
}
