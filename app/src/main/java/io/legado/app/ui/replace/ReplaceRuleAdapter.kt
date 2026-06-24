package io.legado.app.ui.replace

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.databinding.ItemReplaceRuleBinding
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.ui.widget.recycler.ItemTouchCallback
import io.legado.app.utils.ColorUtils


class ReplaceRuleAdapter(context: Context, var callBack: CallBack) :
    RecyclerAdapter<ReplaceRuleListItem, ItemReplaceRuleBinding>(context),
    ItemTouchCallback.Callback {

    private val selected = linkedSetOf<ReplaceRule>()

    val selection: List<ReplaceRule>
        get() = selectableRules().filter { selected.contains(it) }

    val ruleCount: Int
        get() = selectableRules().size

    val diffItemCallBack = object : DiffUtil.ItemCallback<ReplaceRuleListItem>() {

        override fun areItemsTheSame(
            oldItem: ReplaceRuleListItem,
            newItem: ReplaceRuleListItem
        ): Boolean {
            return oldItem.sameItemKey == newItem.sameItemKey
        }

        override fun areContentsTheSame(
            oldItem: ReplaceRuleListItem,
            newItem: ReplaceRuleListItem
        ): Boolean {
            return oldItem == newItem && sectionSelectionSame(oldItem, newItem)
        }

        override fun getChangePayload(
            oldItem: ReplaceRuleListItem,
            newItem: ReplaceRuleListItem
        ): Any? {
            if (oldItem is ReplaceRuleListItem.Section && newItem is ReplaceRuleListItem.Section) {
                val payload = Bundle()
                if (oldItem.title != newItem.title || oldItem.rules.size != newItem.rules.size) {
                    payload.putBoolean("upSection", true)
                }
                if (oldItem.expanded != newItem.expanded) {
                    payload.putBoolean("expanded", true)
                }
                return if (payload.isEmpty) null else payload
            }
            if (oldItem !is ReplaceRuleListItem.Rule || newItem !is ReplaceRuleListItem.Rule) {
                return null
            }
            val payload = Bundle()
            if (oldItem.rule.name != newItem.rule.name
                || oldItem.rule.group != newItem.rule.group
                || oldItem.meta != newItem.meta
                || oldItem.showMeta != newItem.showMeta
                || oldItem.inPanel != newItem.inPanel
            ) {
                payload.putBoolean("upName", true)
            }
            if (oldItem.rule.isEnabled != newItem.rule.isEnabled) {
                payload.putBoolean("enabled", true)
            }
            return if (payload.isEmpty) null else payload
        }
    }

    fun selectAll() {
        selectableRules().forEach { selected.add(it) }
        notifyItemRangeChanged(0, itemCount, bundleOf(Pair("selected", null)))
        callBack.upCountView()
    }

    fun revertSelection() {
        selectableRules().forEach {
            if (selected.contains(it)) {
                selected.remove(it)
            } else {
                selected.add(it)
            }
        }
        notifyItemRangeChanged(0, itemCount, bundleOf(Pair("selected", null)))
        callBack.upCountView()
    }

    override fun getViewBinding(parent: ViewGroup): ItemReplaceRuleBinding {
        return ItemReplaceRuleBinding.inflate(inflater, parent, false)
    }

    override fun onCurrentListChanged() {
        selected.retainAll(selectableRules().toSet())
        callBack.upCountView()
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemReplaceRuleBinding,
        item: ReplaceRuleListItem,
        payloads: MutableList<Any>
    ) {
        binding.run {
            sectionContainer.isVisible = item is ReplaceRuleListItem.Section
            ruleContainer.isVisible = item is ReplaceRuleListItem.Rule
            if (item is ReplaceRuleListItem.Section) {
                bindSection(item, payloads)
                return
            }
            bindRule(item as ReplaceRuleListItem.Rule, payloads)
        }
    }

    private fun ItemReplaceRuleBinding.bindSection(
        item: ReplaceRuleListItem.Section,
        payloads: MutableList<Any>
    ) {
        root.setBackgroundColor(ColorUtils.withAlpha(context.backgroundColor, 0.5f))
        if (payloads.isEmpty()) {
            tvSectionTitle.text = item.title
            tvSectionCount.text = item.rules.size.toString()
        } else {
            payloads.filterIsInstance<Bundle>().forEach { bundle ->
                if (bundle.containsKey("upSection")) {
                    tvSectionTitle.text = item.title
                    tvSectionCount.text = item.rules.size.toString()
                }
            }
        }
        ivSectionExpand.rotation = if (item.expanded) 0f else -90f
        cbSection.isChecked = item.rules.isNotEmpty() && item.rules.all { selected.contains(it) }
        viewSectionStatus.background = statusDotDrawable(
            item.rules.isNotEmpty() && item.rules.all { it.isEnabled }
        )
    }

    private fun ItemReplaceRuleBinding.bindRule(
        item: ReplaceRuleListItem.Rule,
        payloads: MutableList<Any>
    ) {
        root.setBackgroundColor(ColorUtils.withAlpha(context.backgroundColor, 0.5f))
        ruleContainer.minHeight = 52
        ruleContainer.setPadding(
            dp(if (item.inPanel) 28 else 16),
            dp(6),
            dp(16),
            dp(6)
        )
        if (payloads.isEmpty()) {
            cbName.text = item.rule.getDisplayNameGroup()
            tvRuleMeta.text = item.meta
        } else {
            payloads.filterIsInstance<Bundle>().forEach { bundle ->
                if (bundle.containsKey("upName")) {
                    cbName.text = item.rule.getDisplayNameGroup()
                    tvRuleMeta.text = item.meta
                }
            }
        }
        tvRuleMeta.isVisible = false
        cbName.isChecked = selected.contains(item.rule)
        viewRuleStatus.background = statusDotDrawable(item.rule.isEnabled)
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemReplaceRuleBinding) {
        binding.apply {
            sectionContainer.setOnClickListener {
                (currentItem(holder) as? ReplaceRuleListItem.Section)?.let {
                    callBack.toggleSection(it.key)
                }
            }
            cbSection.setOnClickListener {
                (currentItem(holder) as? ReplaceRuleListItem.Section)?.let {
                    toggleSectionSelection(it)
                }
            }
            ivSectionMore.setOnClickListener {
                (currentItem(holder) as? ReplaceRuleListItem.Section)?.let {
                    showSectionMenu(ivSectionMore, it)
                }
            }
            ivSectionExpand.setOnClickListener {
                (currentItem(holder) as? ReplaceRuleListItem.Section)?.let {
                    callBack.toggleSection(it.key)
                }
            }
            cbName.setOnClickListener {
                (currentItem(holder) as? ReplaceRuleListItem.Rule)?.let {
                    if (cbName.isChecked) {
                        selected.add(it.rule)
                    } else {
                        selected.remove(it.rule)
                    }
                }
                notifyItemRangeChanged(0, itemCount, bundleOf(Pair("selected", null)))
                callBack.upCountView()
            }
            ivMenuMore.setOnClickListener {
                val position = currentPosition(holder)
                if (position != RecyclerView.NO_POSITION) {
                    showRuleMenu(ivMenuMore, position)
                }
            }
        }
    }

    private fun showRuleMenu(view: View, position: Int) {
        val item = (getItem(position) as? ReplaceRuleListItem.Rule)?.rule ?: return
        val popupMenu = PopupMenu(context, view)
        popupMenu.inflate(R.menu.replace_rule_item)
        popupMenu.menu.findItem(R.id.menu_enable)?.isVisible = !item.isEnabled
        popupMenu.menu.findItem(R.id.menu_disable)?.isVisible = item.isEnabled
        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_enable -> callBack.update(item.copy(isEnabled = true))
                R.id.menu_disable -> callBack.update(item.copy(isEnabled = false))
                R.id.menu_edit -> callBack.edit(item)
                R.id.menu_top -> callBack.toTop(item)
                R.id.menu_bottom -> callBack.toBottom(item)
                R.id.menu_del -> {
                    callBack.delete(item)
                    selected.remove(item)
                }
            }
            true
        }
        popupMenu.show()
    }

    private fun showSectionMenu(view: View, item: ReplaceRuleListItem.Section) {
        val popupMenu = PopupMenu(context, view)
        val hasDisabled = item.rules.any { !it.isEnabled }
        val hasEnabled = item.rules.any { it.isEnabled }
        if (hasDisabled) {
            popupMenu.menu.add(0, R.id.menu_enable, 0, R.string.enable)
        }
        if (hasEnabled) {
            popupMenu.menu.add(0, R.id.menu_disable, 1, R.string.replace_rule_disable)
        }
        popupMenu.menu.add(0, R.id.menu_del, 2, R.string.delete)
        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_enable -> callBack.updateSectionEnabled(item.title, item.rules, true)
                R.id.menu_disable -> callBack.updateSectionEnabled(item.title, item.rules, false)
                R.id.menu_del -> callBack.deleteSection(item.title, item.rules)
            }
            true
        }
        popupMenu.show()
    }

    override fun swap(srcPosition: Int, targetPosition: Int): Boolean {
        val srcItem = (getItem(srcPosition) as? ReplaceRuleListItem.Rule)?.rule
        val targetItem = (getItem(targetPosition) as? ReplaceRuleListItem.Rule)?.rule
        if (srcItem != null && targetItem != null) {
            if (srcItem.order == targetItem.order) {
                callBack.upOrder()
            } else {
                val srcOrder = srcItem.order
                srcItem.order = targetItem.order
                targetItem.order = srcOrder
                movedItems.add(srcItem)
                movedItems.add(targetItem)
            }
        }
        swapItem(srcPosition, targetPosition)
        return true
    }

    private val movedItems = linkedSetOf<ReplaceRule>()

    override fun onClearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        if (movedItems.isNotEmpty()) {
            callBack.update(*movedItems.toTypedArray())
            movedItems.clear()
        }
    }

    private fun sectionSelectionSame(
        oldItem: ReplaceRuleListItem,
        newItem: ReplaceRuleListItem
    ): Boolean {
        if (oldItem !is ReplaceRuleListItem.Section || newItem !is ReplaceRuleListItem.Section) {
            return true
        }
        val oldSelected = oldItem.rules.isNotEmpty() && oldItem.rules.all { selected.contains(it) }
        val newSelected = newItem.rules.isNotEmpty() && newItem.rules.all { selected.contains(it) }
        return oldSelected == newSelected
    }

    private fun selectableRules(): List<ReplaceRule> {
        return getItems()
            .flatMap {
                when (it) {
                    is ReplaceRuleListItem.Section -> it.rules
                    is ReplaceRuleListItem.Rule -> listOf(it.rule)
                }
            }
            .distinctBy { it.id }
    }

    private fun toggleSectionSelection(section: ReplaceRuleListItem.Section) {
        val isAllSelected = section.rules.isNotEmpty() && section.rules.all { selected.contains(it) }
        selectSection(section, !isAllSelected)
    }

    private fun selectSection(section: ReplaceRuleListItem.Section, isSelected: Boolean) {
        if (isSelected) {
            selected.addAll(section.rules)
        } else {
            selected.removeAll(section.rules.toSet())
        }
        notifyItemRangeChanged(0, itemCount, bundleOf(Pair("selected", null)))
        callBack.upCountView()
    }

    private fun currentItem(holder: ItemViewHolder): ReplaceRuleListItem? {
        val position = currentPosition(holder)
        return if (position == RecyclerView.NO_POSITION) null else getItem(position)
    }

    private fun currentPosition(holder: ItemViewHolder): Int {
        val position = holder.bindingAdapterPosition
        return if (position in 0 until itemCount) position else RecyclerView.NO_POSITION
    }

    private fun dp(value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }

    private fun statusDotDrawable(enabled: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(context.getColor(if (enabled) R.color.success else R.color.error))
        }
    }

    interface CallBack {
        fun update(vararg rule: ReplaceRule)
        fun delete(rule: ReplaceRule)
        fun edit(rule: ReplaceRule)
        fun toTop(rule: ReplaceRule)
        fun toBottom(rule: ReplaceRule)
        fun upOrder()
        fun upCountView()
        fun toggleSection(key: String)
        fun updateSectionEnabled(title: String, rules: List<ReplaceRule>, isEnabled: Boolean)
        fun deleteSection(title: String, rules: List<ReplaceRule>)
    }
}

sealed class ReplaceRuleListItem {

    abstract val sameItemKey: String

    data class Section(
        val key: String,
        val title: String,
        val rules: List<ReplaceRule>,
        val expanded: Boolean
    ) : ReplaceRuleListItem() {
        override val sameItemKey: String = "section:$key"
    }

    data class Rule(
        val sectionKey: String?,
        val rule: ReplaceRule,
        val meta: String,
        val showMeta: Boolean,
        val inPanel: Boolean
    ) : ReplaceRuleListItem() {
        override val sameItemKey: String = "rule:${sectionKey.orEmpty()}:${rule.id}"
    }
}
