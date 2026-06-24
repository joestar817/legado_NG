package io.legado.app.ui.book.source.manage

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.constant.BookSourceType
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.databinding.ItemBookSourceBinding
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.model.Debug
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.widget.recycler.ItemTouchCallback
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.buildMainHandler
import io.legado.app.utils.gone
import io.legado.app.utils.startActivity
import io.legado.app.utils.visible
import java.util.Collections


class BookSourceAdapter(
    context: Context,
    private val callBack: CallBack,
    private val recyclerView: RecyclerView
) : RecyclerAdapter<BookSourceListItem, ItemBookSourceBinding>(context),
    ItemTouchCallback.Callback {

    private val selected = linkedSetOf<BookSourcePart>()
    private val finalMessageRegex = Regex("成功|失败")
    private val handler = buildMainHandler()
    var showSourceHost = false

    val selection: List<BookSourcePart>
        get() = selectableSources().filter { selected.contains(it) }

    val sourceCount: Int
        get() = selectableSources().size

    val diffItemCallback = object : DiffUtil.ItemCallback<BookSourceListItem>() {

        override fun areItemsTheSame(
            oldItem: BookSourceListItem,
            newItem: BookSourceListItem
        ): Boolean {
            return oldItem.sameItemKey == newItem.sameItemKey
        }

        override fun areContentsTheSame(
            oldItem: BookSourceListItem,
            newItem: BookSourceListItem
        ): Boolean {
            return oldItem == newItem && sectionSelectionSame(oldItem, newItem)
        }

        override fun getChangePayload(
            oldItem: BookSourceListItem,
            newItem: BookSourceListItem
        ): Any? {
            if (oldItem is BookSourceListItem.Section && newItem is BookSourceListItem.Section) {
                val payload = Bundle()
                if (oldItem.title != newItem.title || oldItem.sources.size != newItem.sources.size) {
                    payload.putBoolean("upSection", true)
                }
                if (oldItem.expanded != newItem.expanded) {
                    payload.putBoolean("expanded", true)
                }
                return if (payload.isEmpty) null else payload
            }
            if (oldItem !is BookSourceListItem.Source || newItem !is BookSourceListItem.Source) {
                return null
            }
            val oldSource = oldItem.source
            val newSource = newItem.source
            val payload = Bundle()
            if (oldSource.bookSourceName != newSource.bookSourceName
                || oldSource.bookSourceGroup != newSource.bookSourceGroup
                || oldItem.inPanel != newItem.inPanel
            ) {
                payload.putBoolean("upName", true)
            }
            if (oldSource.enabled != newSource.enabled) payload.putBoolean("upTags", true)
            if (oldSource.hasSearchUrl != newSource.hasSearchUrl) payload.putBoolean("upTags", true)
            if (oldSource.enabledExplore != newSource.enabledExplore ||
                oldSource.hasExploreUrl != newSource.hasExploreUrl ||
                oldSource.bookSourceType != newSource.bookSourceType
            ) {
                payload.putBoolean("upTags", true)
            }
            return if (payload.isEmpty) null else payload
        }
    }

    override fun getViewBinding(parent: ViewGroup): ItemBookSourceBinding {
        return ItemBookSourceBinding.inflate(inflater, parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemBookSourceBinding,
        item: BookSourceListItem,
        payloads: MutableList<Any>
    ) {
        binding.run {
            sectionContainer.isVisible = item is BookSourceListItem.Section
            sourceContainer.isVisible = item is BookSourceListItem.Source
            if (item is BookSourceListItem.Section) {
                bindSection(item, payloads)
                return
            }
            bindSource(holder, item as BookSourceListItem.Source, payloads)
        }
    }

    private fun ItemBookSourceBinding.bindSection(
        item: BookSourceListItem.Section,
        payloads: MutableList<Any>
    ) {
        root.setBackgroundColor(ColorUtils.withAlpha(context.backgroundColor, 0.5f))
        if (payloads.isEmpty()) {
            tvSectionTitle.text = item.title
            tvSectionCount.text = item.sources.size.toString()
        } else {
            payloads.filterIsInstance<Bundle>().forEach { bundle ->
                if (bundle.containsKey("upSection")) {
                    tvSectionTitle.text = item.title
                    tvSectionCount.text = item.sources.size.toString()
                }
            }
        }
        ivSectionExpand.rotation = if (item.expanded) 0f else -90f
        cbSection.isChecked = item.sources.isNotEmpty() && item.sources.all { selected.contains(it) }
        viewSectionStatus.background = statusDotDrawable(isNormalSection(item.title))
    }

    private fun ItemBookSourceBinding.bindSource(
        holder: ItemViewHolder,
        item: BookSourceListItem.Source,
        payloads: MutableList<Any>
    ) {
        val source = item.source
        root.setBackgroundColor(ColorUtils.withAlpha(context.backgroundColor, 0.5f))
        sourceContainer.minHeight = dp(56)
        sourceContainer.setPadding(
            dp(if (item.inPanel) 28 else 16),
            dp(8),
            dp(12),
            dp(8)
        )
        if (payloads.isEmpty()) {
            cbBookSource.text = source.getDisPlayNameGroup()
            cbBookSource.isChecked = selected.contains(source)
            upCheckSourceMessage(this, source)
            upSourceTags(this, source)
            upSourceHost(this, holder)
        } else {
            payloads.filterIsInstance<Bundle>().forEach { bundle ->
                bundle.keySet().forEach {
                    when (it) {
                        "upName" -> cbBookSource.text = source.getDisPlayNameGroup()
                        "upTags" -> upSourceTags(this, source)
                        "selected" -> cbBookSource.isChecked = selected.contains(source)
                        "checkSourceMessage" -> upCheckSourceMessage(this, source)
                        "upSourceHost" -> upSourceHost(this, holder)
                    }
                }
            }
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemBookSourceBinding) {
        binding.apply {
            sectionContainer.setOnClickListener {
                (currentItem(holder) as? BookSourceListItem.Section)?.let {
                    callBack.toggleSection(it.key)
                }
            }
            cbSection.setOnClickListener {
                (currentItem(holder) as? BookSourceListItem.Section)?.let {
                    toggleSectionSelection(it)
                }
            }
            ivSectionMore.setOnClickListener {
                (currentItem(holder) as? BookSourceListItem.Section)?.let {
                    showSectionMenu(ivSectionMore, it)
                }
            }
            ivSectionExpand.setOnClickListener {
                (currentItem(holder) as? BookSourceListItem.Section)?.let {
                    callBack.toggleSection(it.key)
                }
            }
            cbBookSource.setOnUserCheckedChangeListener { checked ->
                (currentItem(holder) as? BookSourceListItem.Source)?.source?.let {
                    if (checked) {
                        selected.add(it)
                    } else {
                        selected.remove(it)
                    }
                    callBack.upCountView()
                }
            }
            ivMenuMore.setOnClickListener {
                val position = currentPosition(holder)
                if (position != RecyclerView.NO_POSITION) {
                    showMenu(ivMenuMore, position)
                }
            }
        }
    }

    override fun onCurrentListChanged() {
        selected.retainAll(selectableSources().toSet())
        callBack.upCountView()
        recyclerView.doOnLayout {
            handler.post {
                notifyItemRangeChanged(0, itemCount, bundleOf("upSourceHost" to null))
            }
        }
    }

    private fun showMenu(view: View, position: Int) {
        val source = (getItem(position) as? BookSourceListItem.Source)?.source ?: return
        val popupMenu = PopupMenu(context, view)
        popupMenu.inflate(R.menu.book_source_item)
        popupMenu.menu.findItem(R.id.menu_enable).isVisible = !source.enabled
        popupMenu.menu.findItem(R.id.menu_disable).isVisible = source.enabled
        popupMenu.menu.findItem(R.id.menu_top).isVisible = callBack.sort == BookSourceSort.Default
        popupMenu.menu.findItem(R.id.menu_bottom).isVisible =
            callBack.sort == BookSourceSort.Default
        val qyMenu = popupMenu.menu.findItem(R.id.menu_enable_explore)
        if (!source.hasExploreUrl) {
            qyMenu.isVisible = false
        } else {
            if (source.enabledExplore) {
                qyMenu.setTitle(R.string.disable_explore)
            } else {
                qyMenu.setTitle(R.string.enable_explore)
            }
        }
        val loginMenu = popupMenu.menu.findItem(R.id.menu_login)
        loginMenu.isVisible = source.hasLoginUrl
        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_enable -> callBack.enable(true, source)
                R.id.menu_disable -> callBack.enable(false, source)
                R.id.menu_edit -> callBack.edit(source)
                R.id.menu_top -> callBack.toTop(source)
                R.id.menu_bottom -> callBack.toBottom(source)
                R.id.menu_login -> context.startActivity<SourceLoginActivity> {
                    putExtra("type", "bookSource")
                    putExtra("key", source.bookSourceUrl)
                }

                R.id.menu_search -> callBack.searchBook(source)
                R.id.menu_debug_source -> callBack.debug(source)
                R.id.menu_del -> {
                    callBack.del(source)
                    selected.remove(source)
                }

                R.id.menu_enable_explore -> {
                    callBack.enableExplore(!source.enabledExplore, source)
                }
            }
            true
        }
        popupMenu.show()
    }

    private fun showSectionMenu(view: View, item: BookSourceListItem.Section) {
        val popupMenu = PopupMenu(context, view)
        val hasDisabled = item.sources.any { !it.enabled }
        val hasEnabled = item.sources.any { it.enabled }
        if (hasDisabled) {
            popupMenu.menu.add(0, R.id.menu_enable, 0, R.string.enable)
        }
        if (hasEnabled) {
            popupMenu.menu.add(0, R.id.menu_disable, 1, R.string.replace_rule_disable)
        }
        popupMenu.menu.add(0, R.id.menu_del, 2, R.string.delete)
        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_enable -> callBack.updateSectionEnabled(item.title, item.sources, true)
                R.id.menu_disable -> callBack.updateSectionEnabled(item.title, item.sources, false)
                R.id.menu_del -> callBack.deleteSection(item.title, item.sources)
            }
            true
        }
        popupMenu.show()
    }

    private fun upSourceTags(binding: ItemBookSourceBinding, source: BookSourcePart) = binding.run {
        tvTagSearch.isVisible = source.hasSearchUrl
        bindStateTag(tvTagSearch, source.enabled)
        tvTagExplore.isVisible = source.hasExploreUrl
        if (source.hasExploreUrl) {
            bindStateTag(tvTagExplore, source.enabledExplore)
            tvTagExplore.contentDescription = context.getString(
                if (source.enabledExplore) {
                    R.string.tag_explore_enabled
                } else {
                    R.string.tag_explore_disabled
                }
            )
        }
        bindTypeTag(tvTagImage, source.bookSourceType == BookSourceType.image)
        bindTypeTag(
            tvTagAudio,
            source.bookSourceType == BookSourceType.audio || source.bookSourceType == BookSourceType.video
        )
    }

    private fun bindStateTag(view: TextView, enabled: Boolean) {
        view.setBackgroundResource(if (enabled) R.drawable.ng_bg_tag_success else R.drawable.ng_bg_tag_error)
        view.setTextColor(context.getColor(if (enabled) R.color.ng_success else R.color.ng_error))
    }

    private fun bindTypeTag(view: TextView, visible: Boolean) {
        view.isVisible = visible
        if (visible) {
            view.setBackgroundResource(R.drawable.ng_bg_tag_neutral)
            view.setTextColor(context.getColor(R.color.ng_on_surface_variant))
        }
    }

    private fun upCheckSourceMessage(
        binding: ItemBookSourceBinding,
        item: BookSourcePart
    ) = binding.run {
        val msg = Debug.debugMessageMap[item.bookSourceUrl] ?: ""
        ivDebugText.text = msg
        val isEmpty = msg.isEmpty()
        var isFinalMessage = msg.contains(finalMessageRegex)
        if (!Debug.isChecking && !isFinalMessage) {
            Debug.updateFinalMessage(item.bookSourceUrl, "校验失败")
            ivDebugText.text = Debug.debugMessageMap[item.bookSourceUrl] ?: ""
            isFinalMessage = true
        }
        ivDebugText.visibility =
            if (!isEmpty) View.VISIBLE else View.GONE
        ivProgressBar.visibility =
            if (isFinalMessage || isEmpty || !Debug.isChecking) View.GONE else View.VISIBLE
    }

    private fun upSourceHost(binding: ItemBookSourceBinding, holder: ItemViewHolder) = binding.run {
        val position = currentPosition(holder)
        if (position == RecyclerView.NO_POSITION) {
            tvHostText.gone()
            return@run
        }
        val item = getItem(position) as? BookSourceListItem.Source
        if (item != null && showSourceHost && isItemHeader(position)) {
            tvHostText.text = getHeaderText(position)
            tvHostText.visible()
        } else {
            tvHostText.gone()
        }
    }

    fun selectAll() {
        selectableSources().forEach { selected.add(it) }
        notifyItemRangeChanged(0, itemCount, bundleOf(Pair("selected", null)))
        callBack.upCountView()
    }

    fun revertSelection() {
        selectableSources().forEach {
            if (selected.contains(it)) {
                selected.remove(it)
            } else {
                selected.add(it)
            }
        }
        notifyItemRangeChanged(0, itemCount, bundleOf(Pair("selected", null)))
        callBack.upCountView()
    }

    fun checkSelectedInterval() {
        val selectedPosition = linkedSetOf<Int>()
        getItems().forEachIndexed { index, item ->
            val source = (item as? BookSourceListItem.Source)?.source
            if (source != null && selected.contains(source)) {
                selectedPosition.add(index)
            }
        }
        if (selectedPosition.isEmpty()) return
        val minPosition = Collections.min(selectedPosition)
        val maxPosition = Collections.max(selectedPosition)
        val itemCount = maxPosition - minPosition + 1
        for (i in minPosition..maxPosition) {
            (getItem(i) as? BookSourceListItem.Source)?.source?.let {
                selected.add(it)
            }
        }
        notifyItemRangeChanged(minPosition, itemCount, bundleOf(Pair("selected", null)))
        callBack.upCountView()
    }

    fun getHeaderText(position: Int): String {
        val source = (getItem(position) as? BookSourceListItem.Source)?.source ?: return "#"
        return callBack.getSourceHost(source.bookSourceUrl)
    }

    fun isItemHeader(position: Int): Boolean {
        if (getItem(position) !is BookSourceListItem.Source) return false
        if (position == 0) return true
        val lastHost = getHeaderText(position - 1)
        val curHost = getHeaderText(position)
        return lastHost != curHost
    }

    override fun swap(srcPosition: Int, targetPosition: Int): Boolean {
        val srcItem = (getItem(srcPosition) as? BookSourceListItem.Source)?.source
        val targetItem = (getItem(targetPosition) as? BookSourceListItem.Source)?.source
        if (srcItem != null && targetItem != null) {
            val srcOrder = srcItem.customOrder
            srcItem.customOrder = targetItem.customOrder
            targetItem.customOrder = srcOrder
            movedItems.add(srcItem)
            movedItems.add(targetItem)
            swapItem(srcPosition, targetPosition)
            return true
        }
        return false
    }

    private val movedItems = hashSetOf<BookSourcePart>()

    override fun onClearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        if (movedItems.isNotEmpty()) {
            val sortNumberSet = hashSetOf<Int>()
            movedItems.forEach {
                sortNumberSet.add(it.customOrder)
            }
            if (movedItems.size > sortNumberSet.size) {
                callBack.upOrder(getItems().mapIndexedNotNull { index, item ->
                    val bookSourcePart = (item as? BookSourceListItem.Source)?.source
                    bookSourcePart?.customOrder = if (callBack.sortAscending) index else -index
                    bookSourcePart
                })
            } else {
                callBack.upOrder(movedItems.toList())
            }
            movedItems.clear()
        }
    }

    private fun sectionSelectionSame(
        oldItem: BookSourceListItem,
        newItem: BookSourceListItem
    ): Boolean {
        if (oldItem !is BookSourceListItem.Section || newItem !is BookSourceListItem.Section) {
            return true
        }
        val oldSelected = oldItem.sources.isNotEmpty() && oldItem.sources.all { selected.contains(it) }
        val newSelected = newItem.sources.isNotEmpty() && newItem.sources.all { selected.contains(it) }
        return oldSelected == newSelected
    }

    private fun selectableSources(): List<BookSourcePart> {
        return getItems()
            .flatMap {
                when (it) {
                    is BookSourceListItem.Section -> it.sources
                    is BookSourceListItem.Source -> listOf(it.source)
                }
            }
            .distinctBy { it.bookSourceUrl }
    }

    private fun toggleSectionSelection(section: BookSourceListItem.Section) {
        val isAllSelected = section.sources.isNotEmpty() && section.sources.all { selected.contains(it) }
        selectSection(section, !isAllSelected)
    }

    private fun selectSection(section: BookSourceListItem.Section, isSelected: Boolean) {
        if (isSelected) {
            selected.addAll(section.sources)
        } else {
            selected.removeAll(section.sources.toSet())
        }
        notifyItemRangeChanged(0, itemCount, bundleOf(Pair("selected", null)))
        callBack.upCountView()
    }

    private fun currentItem(holder: ItemViewHolder): BookSourceListItem? {
        val position = currentPosition(holder)
        return if (position == RecyclerView.NO_POSITION) null else getItem(position)
    }

    private fun currentPosition(holder: ItemViewHolder): Int {
        val position = holder.bindingAdapterPosition
        return if (position in 0 until itemCount) position else RecyclerView.NO_POSITION
    }

    private fun isNormalSection(title: String): Boolean {
        val abnormalKeywords = listOf("失效", "异常", "错误", "无效", "规则为空")
        if (title == "校验超时") {
            return false
        }
        return abnormalKeywords.none { title.contains(it) }
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
        val sort: BookSourceSort
        val sortAscending: Boolean
        fun del(bookSource: BookSourcePart)
        fun edit(bookSource: BookSourcePart)
        fun toTop(bookSource: BookSourcePart)
        fun toBottom(bookSource: BookSourcePart)
        fun searchBook(bookSource: BookSourcePart)
        fun debug(bookSource: BookSourcePart)
        fun upOrder(items: List<BookSourcePart>)
        fun enable(enable: Boolean, bookSource: BookSourcePart)
        fun enableExplore(enable: Boolean, bookSource: BookSourcePart)
        fun upCountView()
        fun getSourceHost(origin: String): String
        fun toggleSection(key: String)
        fun updateSectionEnabled(title: String, sources: List<BookSourcePart>, isEnabled: Boolean)
        fun deleteSection(title: String, sources: List<BookSourcePart>)
    }
}

sealed class BookSourceListItem {

    abstract val sameItemKey: String

    data class Section(
        val key: String,
        val title: String,
        val sources: List<BookSourcePart>,
        val expanded: Boolean
    ) : BookSourceListItem() {
        override val sameItemKey: String = "section:$key"
    }

    data class Source(
        val sectionKey: String?,
        val source: BookSourcePart,
        val inPanel: Boolean
    ) : BookSourceListItem() {
        override val sameItemKey: String = "source:${sectionKey.orEmpty()}:${source.bookSourceUrl}"
    }
}
