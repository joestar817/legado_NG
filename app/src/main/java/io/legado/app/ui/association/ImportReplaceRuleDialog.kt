package io.legado.app.ui.association

import android.os.Bundle
import androidx.fragment.app.viewModels
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext

class ImportReplaceRuleDialog() : BaseRuleImportDialog<ReplaceRule>() {

    constructor(source: String, finishOnDismiss: Boolean = false) : this() {
        arguments = Bundle().apply {
            putString("source", source)
            putBoolean("finishOnDismiss", finishOnDismiss)
        }
    }

    private val viewModel by viewModels<ImportReplaceRuleViewModel>()

    override val titleRes: Int = R.string.import_replace_rule
    override val allItems: MutableList<ReplaceRule>
        get() = viewModel.allRules
    override val localItems: List<ReplaceRule?>
        get() = viewModel.checkRules
    override val selectStatus: MutableList<Boolean>
        get() = viewModel.selectStatus
    override val errorLiveData
        get() = viewModel.errorLiveData
    override val successLiveData
        get() = viewModel.successLiveData

    override val supportsGroupConfig: Boolean = true

    override fun importSource(source: String) = viewModel.import(source)

    override fun importSelected(onFinally: () -> Unit) = viewModel.importSelect(onFinally)

    override fun itemName(item: ReplaceRule): String = if (item.group.isNullOrBlank()) {
        item.name
    } else {
        "${item.name}(${item.group})"
    }

    override fun itemState(item: ReplaceRule, localItem: ReplaceRule?): RuleImportState = when {
        localItem == null -> RuleImportState.NEW
        item.pattern != localItem.pattern ||
            item.replacement != localItem.replacement ||
            item.isRegex != localItem.isRegex ||
            item.scope != localItem.scope -> RuleImportState.UPDATE
        else -> RuleImportState.EXISTING
    }

    override fun serialize(item: ReplaceRule): String = GSON.toJson(item)

    override fun deserialize(text: String): ReplaceRule? =
        GSON.fromJsonObject<ReplaceRule>(text).getOrNull()

    override fun initialGroupName(): String? = viewModel.groupName

    override fun initialAddToExistingGroup(): Boolean = viewModel.isAddGroup

    override suspend fun loadGroupSuggestions(): List<String> = withContext(IO) {
        appDb.replaceRuleDao.allGroups().toList()
    }

    override fun applyGroup(name: String?, addToExisting: Boolean) {
        viewModel.groupName = name
        viewModel.isAddGroup = addToExisting
    }
}
