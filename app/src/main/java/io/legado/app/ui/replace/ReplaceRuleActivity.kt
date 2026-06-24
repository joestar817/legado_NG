package io.legado.app.ui.replace

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.Menu
import android.view.MenuItem
import android.view.SubMenu
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.databinding.ActivityReplaceRuleBinding
import io.legado.app.databinding.DialogEditTextBinding
import io.legado.app.help.DirectLinkUpload
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.primaryColor
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.lib.theme.secondaryTextColor
import io.legado.app.ui.association.ImportReplaceRuleDialog
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.qrcode.QrCodeResult
import io.legado.app.ui.replace.edit.ReplaceEditActivity
import io.legado.app.ui.widget.SelectActionBar
import io.legado.app.ui.widget.recycler.ItemTouchCallback
import io.legado.app.ui.widget.recycler.VerticalDivider
import io.legado.app.utils.ACache
import io.legado.app.utils.GSON
import io.legado.app.utils.applyTint
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.launch
import io.legado.app.utils.sendToClip
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.showHelp
import io.legado.app.utils.splitNotBlank
import io.legado.app.utils.transaction
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

/**
 * 替换规则管理
 */
class ReplaceRuleActivity : VMBaseActivity<ActivityReplaceRuleBinding, ReplaceRuleViewModel>(),
    SearchView.OnQueryTextListener,
    PopupMenu.OnMenuItemClickListener,
    SelectActionBar.CallBack,
    ReplaceRuleAdapter.CallBack {
    override val binding by viewBinding(ActivityReplaceRuleBinding::inflate)
    override val viewModel by viewModels<ReplaceRuleViewModel>()
    private val importRecordKey = "replaceRuleRecordKey"
    private val adapter by lazy { ReplaceRuleAdapter(this, this) }
    private val searchView: SearchView by lazy {
        binding.titleBar.findViewById(R.id.search_view)
    }
    private var groups = arrayListOf<String>()
    private var groupMenu: SubMenu? = null
    private var replaceRuleFlowJob: Job? = null
    private var dataInit = false
    private lateinit var itemTouchCallback: ItemTouchCallback
    private var allRules: List<ReplaceRule> = emptyList()
    private var currentSearchKey: String? = null
    private var viewMode = ReplaceRuleViewMode.LIST
    private var sectionStateKey: String? = null
    private val expandedSections = linkedSetOf<String>()
    private val currentBookName by lazy { intent.getStringExtra(EXTRA_BOOK_NAME).orEmpty() }
    private val currentSourceName by lazy { intent.getStringExtra(EXTRA_SOURCE_NAME).orEmpty() }
    private val currentSourceUrl by lazy { intent.getStringExtra(EXTRA_SOURCE_URL).orEmpty() }
    private val qrCodeResult = registerForActivityResult(QrCodeResult()) {
        it ?: return@registerForActivityResult
        showDialogFragment(ImportReplaceRuleDialog(it))
    }
    private val editActivity =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                setResult(RESULT_OK)
            }
        }
    private val importDoc = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            showDialogFragment(ImportReplaceRuleDialog(uri.toString()))
        }
    }
    private val exportResult = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            alert(R.string.export_success) {
                if (uri.toString().isAbsUrl()) {
                    setMessage(DirectLinkUpload.getSummary())
                }
                val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                    editView.hint = getString(R.string.path)
                    editView.setText(uri.toString())
                }
                customView { alertBinding.root }
                okButton {
                    sendToClip(uri.toString())
                }
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        initRecyclerView()
        initSearchView()
        initViewModes()
        initSelectActionView()
        observeReplaceRuleData()
        observeGroupData()
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.replace_rule, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        groupMenu = menu.findItem(R.id.menu_group)?.subMenu
        upGroupMenu()
        return super.onPrepareOptionsMenu(menu)
    }

    private fun initRecyclerView() {
        binding.recyclerView.setEdgeEffectColor(primaryColor)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        binding.recyclerView.addItemDecoration(VerticalDivider(this))
        itemTouchCallback = ItemTouchCallback(adapter)
        itemTouchCallback.isCanDrag = true
        ItemTouchHelper(itemTouchCallback).attachToRecyclerView(binding.recyclerView)
    }

    private fun initSearchView() {
        searchView.applyTint(primaryTextColor)
        searchView.queryHint = getString(R.string.replace_purify_search)
        searchView.setOnQueryTextListener(this)
    }

    private fun initViewModes() {
        viewModeViews().forEach { (mode, view) ->
            view.setOnClickListener {
                viewMode = mode
                itemTouchCallback.isCanDrag = viewMode == ReplaceRuleViewMode.LIST
                upViewModeViews()
                submitReplaceRules()
            }
        }
        upViewModeViews()
    }

    override fun selectAll(selectAll: Boolean) {
        if (selectAll) {
            adapter.selectAll()
        } else {
            adapter.revertSelection()
        }
    }

    override fun revertSelection() {
        adapter.revertSelection()
    }

    override fun onClickSelectBarMainAction() {
        alert(titleResource = R.string.draw, messageResource = R.string.sure_del) {
            yesButton { viewModel.delSelection(adapter.selection) }
            noButton()
        }
    }

    private fun initSelectActionView() {
        binding.selectActionBar.setMainActionText(R.string.delete)
        binding.selectActionBar.inflateMenu(R.menu.replace_rule_sel)
        binding.selectActionBar.setOnMenuItemClickListener(this)
        binding.selectActionBar.setCallBack(this)
    }

    private fun observeReplaceRuleData(searchKey: String? = null) {
        currentSearchKey = searchKey
        dataInit = false
        replaceRuleFlowJob?.cancel()
        replaceRuleFlowJob = lifecycleScope.launch {
            when {
                searchKey.isNullOrEmpty() -> {
                    appDb.replaceRuleDao.flowAll()
                }

                searchKey == getString(R.string.enabled) -> {
                    appDb.replaceRuleDao.flowEnabled()
                }

                searchKey == getString(R.string.disabled) -> {
                    appDb.replaceRuleDao.flowDisabled()
                }

                searchKey == getString(R.string.no_group) -> {
                    appDb.replaceRuleDao.flowNoGroup()
                }

                searchKey.startsWith("group:") -> {
                    val key = searchKey.substringAfter("group:")
                    appDb.replaceRuleDao.flowGroupSearch("%$key%")
                }

                else -> {
                    appDb.replaceRuleDao.flowSearch("%$searchKey%")
                }
            }.catch {
                AppLog.put("替换规则管理界面更新数据出错", it)
            }.flowOn(IO).conflate().collect {
                if (dataInit) {
                    setResult(RESULT_OK)
                }
                allRules = it
                submitReplaceRules()
                dataInit = true
                delay(100)
            }
        }
    }

    private fun submitReplaceRules() {
        adapter.setItems(buildReplaceRuleItems(allRules), adapter.diffItemCallBack)
    }

    override fun onResume() {
        super.onResume()
        adapter.upResumed(true)
    }

    override fun onPause() {
        adapter.upResumed(false)
        super.onPause()
    }

    private fun observeGroupData() {
        lifecycleScope.launch {
            appDb.replaceRuleDao.flowGroups().collect {
                groups.clear()
                groups.addAll(it)
                upGroupMenu()
            }
        }
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_add_replace_rule ->
                editActivity.launch(ReplaceEditActivity.startIntent(this))

            R.id.menu_group_manage -> showDialogFragment<GroupManageDialog>()
            R.id.menu_enabled_group -> {
                searchView.setQuery(getString(R.string.enabled), true)
            }

            R.id.menu_disabled_group -> {
                searchView.setQuery(getString(R.string.disabled), true)
            }
            R.id.menu_del_selection -> viewModel.delSelection(adapter.selection)
            R.id.menu_import_onLine -> showImportDialog()
            R.id.menu_import_local -> importDoc.launch {
                mode = HandleFileContract.FILE
                allowExtensions = arrayOf("txt", "json")
            }

            R.id.menu_import_qr -> qrCodeResult.launch()
            R.id.menu_help -> showHelp("replaceRuleHelp")
            R.id.menu_group_null -> {
                searchView.setQuery(getString(R.string.no_group), true)
            }

            else -> if (item.groupId == R.id.replace_group) {
                searchView.setQuery("group:${item.title}", true)
            }
        }
        return super.onCompatOptionsItemSelected(item)
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_enable_selection -> viewModel.enableSelection(adapter.selection)
            R.id.menu_disable_selection -> viewModel.disableSelection(adapter.selection)
            R.id.menu_top_sel -> viewModel.topSelect(adapter.selection)
            R.id.menu_bottom_sel -> viewModel.bottomSelect(adapter.selection)
            R.id.menu_export_selection -> exportResult.launch {
                mode = HandleFileContract.EXPORT
                fileData = HandleFileContract.FileData(
                    "exportReplaceRule.json",
                    GSON.toJson(adapter.selection).toByteArray(),
                    "application/json"
                )
            }
        }
        return false
    }

    private fun upGroupMenu() = groupMenu?.transaction { menu ->
        menu.removeGroup(R.id.replace_group)
        groups.forEach {
            menu.add(R.id.replace_group, Menu.NONE, Menu.NONE, it)
        }
    }

    @SuppressLint("InflateParams")
    private fun showImportDialog() {
        val aCache = ACache.get(cacheDir = false)
        val cacheUrls: MutableList<String> = aCache
            .getAsString(importRecordKey)
            ?.splitNotBlank(",")
            ?.toMutableList() ?: mutableListOf()
        alert(titleResource = R.string.import_on_line) {
            val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.hint = "url"
                editView.setFilterValues(cacheUrls)
                editView.delCallBack = {
                    cacheUrls.remove(it)
                    aCache.put(importRecordKey, cacheUrls.joinToString(","))
                }
            }
            customView { alertBinding.root }
            okButton {
                val text = alertBinding.editView.text?.toString()
                text?.let {
                    if (it.isAbsUrl() && !cacheUrls.contains(it)) {
                        cacheUrls.add(0, it)
                        aCache.put(importRecordKey, cacheUrls.joinToString(","))
                    }
                    showDialogFragment(
                        ImportReplaceRuleDialog(it)
                    )
                }
            }
            cancelButton()
        }
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        observeReplaceRuleData(newText)
        return false
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        Coroutine.async { ContentProcessor.upReplaceRules() }
    }

    override fun upCountView() {
        binding.selectActionBar.upCountView(
            adapter.selection.size,
            adapter.ruleCount
        )
    }

    override fun update(vararg rule: ReplaceRule) {
        setResult(RESULT_OK)
        viewModel.update(*rule)
    }

    override fun delete(rule: ReplaceRule) {
        alert(R.string.draw) {
            setMessage(getString(R.string.sure_del) + "\n" + rule.name)
            noButton()
            yesButton {
                setResult(RESULT_OK)
                viewModel.delete(rule)
            }
        }
    }

    override fun edit(rule: ReplaceRule) {
        setResult(RESULT_OK)
        editActivity.launch(ReplaceEditActivity.startIntent(this, rule.id))
    }

    override fun toTop(rule: ReplaceRule) {
        setResult(RESULT_OK)
        viewModel.toTop(rule)
    }

    override fun toBottom(rule: ReplaceRule) {
        setResult(RESULT_OK)
        viewModel.toBottom(rule)
    }

    override fun upOrder() {
        setResult(RESULT_OK)
        viewModel.upOrder()
    }

    override fun updateSectionEnabled(
        title: String,
        rules: List<ReplaceRule>,
        isEnabled: Boolean
    ) {
        setResult(RESULT_OK)
        if (isEnabled) {
            viewModel.enableSelection(rules)
        } else {
            viewModel.disableSelection(rules)
        }
    }

    override fun deleteSection(title: String, rules: List<ReplaceRule>) {
        alert(R.string.draw) {
            setMessage(getString(R.string.sure_del) + "\n" + title)
            noButton()
            yesButton {
                setResult(RESULT_OK)
                viewModel.delSelection(rules)
            }
        }
    }

    override fun toggleSection(key: String) {
        if (!expandedSections.add(key)) {
            expandedSections.remove(key)
        }
        submitReplaceRules()
    }

    private fun buildReplaceRuleItems(rules: List<ReplaceRule>): List<ReplaceRuleListItem> {
        if (viewMode == ReplaceRuleViewMode.LIST) {
            return rules.map { rule ->
                ReplaceRuleListItem.Rule(
                    sectionKey = null,
                    rule = rule,
                    meta = ruleMeta(rule),
                    showMeta = false,
                    inPanel = false
                )
            }
        }
        val sections = when (viewMode) {
            ReplaceRuleViewMode.GROUP -> groupSections(rules)
            ReplaceRuleViewMode.SCOPE -> scopeSections(rules)
            ReplaceRuleViewMode.LIST -> emptyMap()
        }
        if (sections.isEmpty()) return emptyList()
        val newSectionStateKey = "${viewMode.name}:${currentSearchKey.orEmpty()}"
        if (sectionStateKey != newSectionStateKey) {
            sectionStateKey = newSectionStateKey
            expandedSections.clear()
            if (currentSearchKey?.isNotBlank() == true) {
                expandedSections.addAll(sections.keys.map { it.key })
            }
        }
        return buildList {
            sections.forEach { (section, sectionRules) ->
                val expanded = expandedSections.contains(section.key)
                add(
                    ReplaceRuleListItem.Section(
                        key = section.key,
                        title = section.title,
                        rules = sectionRules.distinctBy { it.id },
                        expanded = expanded
                    )
                )
                if (expanded) {
                    sectionRules.distinctBy { it.id }.forEach { rule ->
                        add(
                            ReplaceRuleListItem.Rule(
                                sectionKey = section.key,
                                rule = rule,
                                meta = ruleMeta(rule),
                                showMeta = false,
                                inPanel = true
                            )
                        )
                    }
                }
            }
        }
    }

    private fun groupSections(rules: List<ReplaceRule>): Map<ReplaceRuleSection, List<ReplaceRule>> {
        return rules
            .flatMap { rule ->
                val groups = rule.group?.splitNotBlank(",").orEmpty()
                if (groups.isEmpty()) {
                    listOf(ReplaceRuleSection("group:", getString(R.string.no_group), 0) to rule)
                } else {
                    groups.map {
                        ReplaceRuleSection("group:$it", it, 1) to rule
                    }
                }
            }
            .groupBy({ it.first }, { it.second })
            .toSortedMap(compareBy<ReplaceRuleSection> { it.rank }.thenBy { it.title })
    }

    private fun scopeSections(rules: List<ReplaceRule>): Map<ReplaceRuleSection, List<ReplaceRule>> {
        return rules
            .map { rule -> scopeSectionFor(rule) to rule }
            .groupBy({ it.first }, { it.second })
            .toSortedMap(compareBy<ReplaceRuleSection> { it.rank }.thenBy { it.title })
    }

    private fun scopeSectionFor(rule: ReplaceRule): ReplaceRuleSection {
        val scope = rule.scope.orEmpty().trim()
        val tokens = scopeTokens(scope)
        if (scope.isBlank()) {
            return ReplaceRuleSection(
                key = "global",
                title = getString(R.string.replace_scope_global_rules),
                rank = 30
            )
        }
        val title = scopeSectionTitle(tokens.ifEmpty { listOf(scope) })
        val rank = when {
            currentBookName.isNotBlank() && tokens.any {
                it == currentBookName || it.contains(currentBookName)
            } -> 0

            tokens.any { it.contains("://") } -> 10
            else -> 20
        }
        return ReplaceRuleSection(
            key = "scope:${tokens.joinToString("|").ifBlank { scope }}",
            title = title,
            rank = rank
        )
    }

    private fun scopeSectionTitle(tokens: List<String>): String {
        return tokens.joinToString(" | ") { token ->
            token.replace("\n", " ").trim()
        }.ifBlank {
            getString(R.string.replace_scope_global_rules)
        }
    }

    private fun scopeTokens(scope: String): List<String> {
        return scope.split(";", ",", "\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .ifEmpty { if (scope.isBlank()) emptyList() else listOf(scope) }
    }

    private fun ruleMeta(rule: ReplaceRule): String {
        val scopeText = when {
            rule.scopeTitle && rule.scopeContent -> getString(R.string.replace_scope_title_content)
            rule.scopeTitle -> getString(R.string.scope_title)
            else -> getString(R.string.scope_content)
        }
        val modeText = if (rule.isRegex) {
            getString(R.string.replace_rule_regex)
        } else {
            getString(R.string.replace_rule_plain)
        }
        val stateText = if (rule.isEnabled) getString(R.string.enabled) else getString(R.string.disabled)
        return "$scopeText · $modeText · $stateText"
    }

    private fun viewModeViews(): Map<ReplaceRuleViewMode, TextView> {
        return mapOf(
            ReplaceRuleViewMode.LIST to binding.viewModeList,
            ReplaceRuleViewMode.GROUP to binding.viewModeGroup,
            ReplaceRuleViewMode.SCOPE to binding.viewModeScope
        )
    }

    private fun upViewModeViews() {
        viewModeViews().forEach { (mode, view) ->
            val selected = mode == viewMode
            view.background = null
            view.typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            view.setTextColor(if (selected) primaryTextColor else secondaryTextColor)
        }
    }

    enum class ReplaceRuleViewMode {
        LIST,
        GROUP,
        SCOPE
    }

    data class ReplaceRuleSection(
        val key: String,
        val title: String,
        val rank: Int
    )

    companion object {
        private const val EXTRA_BOOK_NAME = "bookName"
        private const val EXTRA_SOURCE_NAME = "sourceName"
        private const val EXTRA_SOURCE_URL = "sourceUrl"

        fun startIntent(
            context: android.content.Context,
            bookName: String? = null,
            sourceName: String? = null,
            sourceUrl: String? = null
        ): Intent {
            return Intent(context, ReplaceRuleActivity::class.java).apply {
                putExtra(EXTRA_BOOK_NAME, bookName)
                putExtra(EXTRA_SOURCE_NAME, sourceName)
                putExtra(EXTRA_SOURCE_URL, sourceUrl)
            }
        }
    }
}
