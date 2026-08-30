package io.legado.app.ui.association

import android.os.Bundle
import androidx.fragment.app.viewModels
import io.legado.app.R
import io.legado.app.data.entities.TxtTocRule
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject

class ImportTxtTocRuleDialog() : BaseRuleImportDialog<TxtTocRule>() {

    constructor(source: String, finishOnDismiss: Boolean = false) : this() {
        arguments = Bundle().apply {
            putString("source", source)
            putBoolean("finishOnDismiss", finishOnDismiss)
        }
    }

    private val viewModel by viewModels<ImportTxtTocRuleViewModel>()

    override val titleRes: Int = R.string.import_txt_toc_rule
    override val allItems: MutableList<TxtTocRule>
        get() = viewModel.allSources
    override val localItems: List<TxtTocRule?>
        get() = viewModel.checkSources
    override val selectStatus: MutableList<Boolean>
        get() = viewModel.selectStatus
    override val errorLiveData
        get() = viewModel.errorLiveData
    override val successLiveData
        get() = viewModel.successLiveData

    override fun importSource(source: String) = viewModel.importSource(source)

    override fun importSelected(onFinally: () -> Unit) = viewModel.importSelect(onFinally)

    override fun itemName(item: TxtTocRule): String = item.name

    override fun itemComment(item: TxtTocRule): String? = item.example

    override fun itemState(item: TxtTocRule, localItem: TxtTocRule?): RuleImportState = when {
        localItem == null -> RuleImportState.NEW
        item != localItem -> RuleImportState.UPDATE
        else -> RuleImportState.EXISTING
    }

    override fun serialize(item: TxtTocRule): String = GSON.toJson(item)

    override fun deserialize(text: String): TxtTocRule? =
        GSON.fromJsonObject<TxtTocRule>(text).getOrNull()
}
