package io.legado.app.ui.association

import android.os.Bundle
import androidx.fragment.app.viewModels
import io.legado.app.R
import io.legado.app.data.entities.DictRule
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject

class ImportDictRuleDialog() : BaseRuleImportDialog<DictRule>() {

    constructor(source: String, finishOnDismiss: Boolean = false) : this() {
        arguments = Bundle().apply {
            putString("source", source)
            putBoolean("finishOnDismiss", finishOnDismiss)
        }
    }

    private val viewModel by viewModels<ImportDictRuleViewModel>()

    override val titleRes: Int = R.string.import_dict_rule
    override val allItems: MutableList<DictRule>
        get() = viewModel.allSources
    override val localItems: List<DictRule?>
        get() = viewModel.checkSources
    override val selectStatus: MutableList<Boolean>
        get() = viewModel.selectStatus
    override val errorLiveData
        get() = viewModel.errorLiveData
    override val successLiveData
        get() = viewModel.successLiveData

    override fun importSource(source: String) = viewModel.importSource(source)

    override fun importSelected(onFinally: () -> Unit) = viewModel.importSelect(onFinally)

    override fun itemName(item: DictRule): String = item.name

    override fun itemState(item: DictRule, localItem: DictRule?): RuleImportState =
        if (localItem == null) RuleImportState.NEW else RuleImportState.EXISTING

    override fun serialize(item: DictRule): String = GSON.toJson(item)

    override fun deserialize(text: String): DictRule? =
        GSON.fromJsonObject<DictRule>(text).getOrNull()
}
