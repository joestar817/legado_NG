package io.legado.app.ui.config

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Rect
import android.os.Bundle
import android.text.InputType
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.BaseFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.databinding.FragmentAiConfigBinding
import io.legado.app.databinding.ItemAiProviderBinding
import io.legado.app.help.ai.AiConfig
import io.legado.app.help.ai.AiManager
import io.legado.app.help.ai.AiPromptStore
import io.legado.app.help.ai.AiProviderSetting
import io.legado.app.help.ai.AiProviderStore
import io.legado.app.help.ai.AiProviderType
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.accentColor
import io.legado.app.databinding.ItemAiPromptBinding
import io.legado.app.ui.widget.TitleBar
import io.legado.app.ui.widget.dialog.WaitDialog
import io.legado.app.ui.widget.recycler.ItemTouchCallback
import io.legado.app.utils.hideSoftInput
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.DecimalFormat

class AiConfigFragment : BaseFragment(R.layout.fragment_ai_config) {

    private enum class Page { MAIN, PROVIDERS, DETAIL, PROMPTS, PROMPT_DETAIL }

    private val binding by viewBinding(FragmentAiConfigBinding::bind)
    private val waitDialog by lazy { WaitDialog(requireContext()) }
    private val providerAdapter by lazy { AiProviderAdapter() }
    private val promptAdapter by lazy { AiPromptAdapter() }
    private lateinit var providerItemTouchHelper: ItemTouchHelper
    private var currentPage = Page.MAIN
    private var currentProviderId: String? = null
    private var currentPrompt: AiPromptStore.Prompt? = null
    private var providerSearchQuery: String = ""
    private var requestJob: Job? = null
    private var modelOptions: List<String> = emptyList()
    private var ignoreModelSelection = false
    private var ignoreProviderFormChanges = false
    private var apiKeyVisible = false
    private val balanceNumberFormat by lazy { DecimalFormat("0.####") }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        initMain()
        initProviderList()
        initDetail()
        initPromptList()
        initPromptDetail()
        initBack()
        showMain()
    }

    override fun onResume() {
        super.onResume()
        bindTitleBarBack()
        refreshCurrentPage()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        requestJob?.cancel()
        waitDialog.dismiss()
    }

    private fun initMain() {
        binding.layoutProviderEntry.setOnClickListener {
            showProviderList()
        }
        binding.layoutPromptEntry.setOnClickListener {
            showPromptList()
        }
        binding.layoutAiPurifyAuto.setOnClickListener {
            binding.switchAiPurifyAuto.isChecked = !binding.switchAiPurifyAuto.isChecked
        }
        binding.switchAiPurifyAuto.setOnCheckedChangeListener { _, isChecked ->
            AiConfig.purifyAutoApply = isChecked
        }
    }

    private fun initProviderList() {
        binding.recyclerProviders.layoutManager = LinearLayoutManager(requireContext())
        providerAdapter.bindToRecyclerView(binding.recyclerProviders)
        providerItemTouchHelper = ItemTouchHelper(ItemTouchCallback(providerAdapter))
        providerItemTouchHelper.attachToRecyclerView(binding.recyclerProviders)
        providerAdapter.setOnItemClickListener { _, item ->
            showDetail(item.id)
        }
        providerAdapter.onSelectProvider = { item ->
            AiProviderStore.setActiveProvider(item.id)
            refreshProviders()
            refreshMain()
        }
        binding.editSearchProvider.doOnTextChanged { text, _, _, _ ->
            providerSearchQuery = text?.toString().orEmpty()
            refreshProviders()
        }
    }

    private fun initDetail() {
        updateApiKeyVisibility()
        setupProviderAutoSave()
        setupProviderFocusClear()
        binding.buttonToggleApiKey.setOnClickListener {
            apiKeyVisible = !apiKeyVisible
            updateApiKeyVisibility()
        }
        binding.spinnerModel.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (ignoreModelSelection || modelOptions.isEmpty()) {
                    return
                }
                saveCurrentProvider()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
            }
        }
        binding.buttonFetchModels.setOnClickListener {
            fetchModels()
        }
        binding.buttonTestConnection.setOnClickListener {
            testConnection()
        }
        binding.buttonQueryBalance.setOnClickListener {
            queryBalance()
        }
    }

    private fun setupProviderAutoSave() {
        val fields = listOf(
            binding.editProviderName,
            binding.editApiKey,
            binding.editBaseUrl,
            binding.editChatPath,
            binding.editModelsUrl,
            binding.editTimeoutSeconds
        )
        fields.forEach { editText ->
            editText.doOnTextChanged { _, _, _, _ ->
                if (!ignoreProviderFormChanges && currentPage == Page.DETAIL) {
                    saveCurrentProvider(updateHeader = editText === binding.editProviderName)
                }
            }
        }
        binding.switchEnabled.setOnCheckedChangeListener { _, _ ->
            if (!ignoreProviderFormChanges && currentPage == Page.DETAIL) {
                saveCurrentProvider()
            }
        }
    }

    private fun setupProviderFocusClear() {
        val listener = View.OnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                clearProviderInputFocusIfOutside(event)
            }
            false
        }
        binding.layoutProviderDetail.setOnTouchListener(listener)
        binding.scrollDetail.setOnTouchListener(listener)
    }

    private fun initPromptList() {
        binding.recyclerPrompts.layoutManager = LinearLayoutManager(requireContext())
        promptAdapter.bindToRecyclerView(binding.recyclerPrompts)
        promptAdapter.setOnItemClickListener { _, item ->
            showPromptDetail(item)
        }
    }

    private fun initPromptDetail() {
        binding.buttonSavePrompt.setOnClickListener {
            val prompt = currentPrompt ?: return@setOnClickListener
            AiPromptStore.save(prompt, binding.editPrompt.text?.toString().orEmpty())
            Toast.makeText(requireContext(), R.string.ai_prompt_saved, Toast.LENGTH_SHORT).show()
            showPromptList()
        }
        binding.buttonResetPrompt.setOnClickListener {
            val prompt = currentPrompt ?: return@setOnClickListener
            AiPromptStore.reset(prompt)
            binding.editPrompt.setText(prompt.defaultPrompt)
            Toast.makeText(requireContext(), R.string.ai_prompt_reset_done, Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun initBack() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    navigateBack()
                }
            }
        )
        bindTitleBarBack()
    }

    private fun bindTitleBarBack() {
        requireActivity().findViewById<TitleBar>(R.id.title_bar)
            ?.setNavigationOnClickListener {
                navigateBack()
            }
    }

    private fun navigateBack() {
        when (currentPage) {
            Page.DETAIL -> showProviderList()
            Page.PROMPT_DETAIL -> showPromptList()
            Page.PROVIDERS -> showMain()
            Page.PROMPTS -> showMain()
            Page.MAIN -> requireActivity().finish()
        }
    }

    private fun setPageTitle(title: CharSequence) {
        activity?.title = title
        requireActivity().findViewById<TitleBar>(R.id.title_bar)?.title = title
    }

    private fun setPageTitle(resId: Int) {
        setPageTitle(getString(resId))
    }

    private fun showMain() {
        currentPage = Page.MAIN
        currentProviderId = null
        currentPrompt = null
        setPageTitle(R.string.ai_setting)
        binding.layoutMainMenu.isVisible = true
        binding.layoutProviderList.isVisible = false
        binding.layoutProviderDetail.isVisible = false
        binding.layoutPromptList.isVisible = false
        binding.layoutPromptDetail.isVisible = false
        bindTitleBarBack()
        refreshMain()
    }

    private fun showProviderList() {
        currentPage = Page.PROVIDERS
        currentProviderId = null
        currentPrompt = null
        setPageTitle(R.string.ai_provider_menu)
        binding.layoutMainMenu.isVisible = false
        binding.layoutProviderList.isVisible = true
        binding.layoutProviderDetail.isVisible = false
        binding.layoutPromptList.isVisible = false
        binding.layoutPromptDetail.isVisible = false
        bindTitleBarBack()
        refreshProviders()
    }

    private fun showDetail(providerId: String) {
        currentPage = Page.DETAIL
        currentProviderId = providerId
        currentPrompt = null
        binding.layoutMainMenu.isVisible = false
        binding.layoutProviderList.isVisible = false
        binding.layoutProviderDetail.isVisible = true
        binding.layoutPromptList.isVisible = false
        binding.layoutPromptDetail.isVisible = false
        bindTitleBarBack()
        refreshCurrentDetail()
    }

    private fun showPromptList() {
        currentPage = Page.PROMPTS
        currentProviderId = null
        currentPrompt = null
        setPageTitle(R.string.ai_prompt_menu)
        binding.layoutMainMenu.isVisible = false
        binding.layoutProviderList.isVisible = false
        binding.layoutProviderDetail.isVisible = false
        binding.layoutPromptList.isVisible = true
        binding.layoutPromptDetail.isVisible = false
        bindTitleBarBack()
        refreshPrompts()
    }

    private fun showPromptDetail(prompt: AiPromptStore.Prompt) {
        currentPage = Page.PROMPT_DETAIL
        currentProviderId = null
        currentPrompt = prompt
        setPageTitle(prompt.displayName())
        binding.layoutMainMenu.isVisible = false
        binding.layoutProviderList.isVisible = false
        binding.layoutProviderDetail.isVisible = false
        binding.layoutPromptList.isVisible = false
        binding.layoutPromptDetail.isVisible = true
        bindTitleBarBack()
        refreshPromptDetail()
    }

    private fun refreshCurrentPage() {
        when (currentPage) {
            Page.MAIN -> refreshMain()
            Page.PROVIDERS -> refreshProviders()
            Page.DETAIL -> refreshCurrentDetail()
            Page.PROMPTS -> refreshPrompts()
            Page.PROMPT_DETAIL -> refreshPromptDetail()
        }
    }

    private fun refreshMain() {
        val providers = AiProviderStore.providers()
        val activeProvider = AiProviderStore.activeProvider()
        val entryIconTint = ColorStateList.valueOf(accentColor)
        binding.imageProviderEntryIcon.imageTintList = entryIconTint
        binding.imagePromptEntryIcon.imageTintList = entryIconTint
        binding.imagePurifyEntryIcon.imageTintList = entryIconTint
        binding.switchAiPurifyAuto.isChecked = AiConfig.purifyAutoApply
        binding.textProviderEntrySummary.text = getString(
            R.string.ai_provider_menu_summary,
            providers.size.toString(),
            activeProvider.name
        )
        binding.textPromptEntrySummary.text = getString(
            R.string.ai_prompt_menu_summary,
            AiPromptStore.Prompt.entries.size.toString()
        )
    }

    private fun refreshProviders() {
        val providers = AiProviderStore.providers()
            .filter {
                providerSearchQuery.isBlank()
                    || it.name.contains(providerSearchQuery, ignoreCase = true)
                    || it.type.displayName.contains(providerSearchQuery, ignoreCase = true)
            }
        providerAdapter.activeProviderId = AiProviderStore.activeProviderId()
        providerAdapter.setItems(providers)
    }

    private fun refreshCurrentDetail() {
        val provider = currentProviderId?.let { AiProviderStore.provider(it) } ?: return
        setPageTitle(provider.name)
        binding.textDetailTitle.text = provider.name
        binding.imageDetailIcon.setImageResource(provider.iconRes())
        ignoreProviderFormChanges = true
        try {
            binding.switchEnabled.isChecked = provider.enabled
            binding.editProviderName.setText(provider.name)
            binding.editBaseUrl.setText(provider.baseUrl)
            binding.editApiKey.setText(provider.apiKey)
            binding.editChatPath.setText(provider.chatCompletionsPath)
            binding.editModelsUrl.setText(provider.modelsUrl)
            binding.editTimeoutSeconds.setText(provider.timeoutSeconds.toString())
            val openAiCompatible = provider.type == AiProviderType.OPENAI
            binding.textOpenaiPathLabel.isVisible = openAiCompatible
            binding.editChatPath.isVisible = openAiCompatible
            binding.textModelsUrlLabel.isVisible = openAiCompatible
            binding.editModelsUrl.isVisible = openAiCompatible
            refreshModelSpinner(provider)
            updateApiKeyVisibility()
        } finally {
            ignoreProviderFormChanges = false
        }
    }

    private fun refreshPrompts() {
        promptAdapter.setItems(AiPromptStore.Prompt.entries.toList())
    }

    private fun refreshPromptDetail() {
        val prompt = currentPrompt ?: return
        binding.textPromptDetailTitle.text = prompt.displayName()
        binding.textPromptDetailSummary.text = prompt.summary()
        binding.editPrompt.setText(AiPromptStore.prompt(prompt))
    }

    private fun readProviderFromForm(): AiProviderSetting? {
        val source = currentProviderId?.let { AiProviderStore.provider(it) } ?: return null
        val selectedModel = binding.spinnerModel.selectedItem?.toString()
            ?.takeIf { it in modelOptions }
            .orEmpty()
        return source.copy(
            enabled = binding.switchEnabled.isChecked,
            name = binding.editProviderName.text?.toString()?.trim().orEmpty().ifBlank {
                source.name
            },
            apiKey = binding.editApiKey.text?.toString()?.trim().orEmpty(),
            baseUrl = binding.editBaseUrl.text?.toString()?.trim().orEmpty().ifBlank {
                source.baseUrl
            },
            model = selectedModel,
            timeoutSeconds = binding.editTimeoutSeconds.text?.toString()
                ?.toIntOrNull()
                ?.coerceIn(5, 600)
                ?: source.timeoutSeconds,
            chatCompletionsPath = binding.editChatPath.text?.toString()?.trim().orEmpty()
                .ifBlank { source.chatCompletionsPath },
            modelsUrl = binding.editModelsUrl.text?.toString()?.trim().orEmpty()
        )
    }

    private fun saveCurrentProvider(
        showToast: Boolean = false,
        updateHeader: Boolean = false
    ): AiProviderSetting? {
        val provider = readProviderFromForm() ?: return null
        AiProviderStore.saveProvider(provider)
        if (updateHeader) {
            setPageTitle(provider.name)
            binding.textDetailTitle.text = provider.name
        }
        if (showToast) {
            Toast.makeText(requireContext(), R.string.ai_provider_saved, Toast.LENGTH_SHORT).show()
        }
        return provider
    }

    private fun clearProviderInputFocusIfOutside(event: MotionEvent) {
        val focus = requireActivity().currentFocus as? EditText ?: return
        if (!focus.isShown || isTouchInsideView(focus, event.rawX.toInt(), event.rawY.toInt())) {
            return
        }
        focus.clearFocus()
        focus.hideSoftInput()
    }

    private fun isTouchInsideView(view: View, rawX: Int, rawY: Int): Boolean {
        val rect = Rect()
        view.getGlobalVisibleRect(rect)
        return rect.contains(rawX, rawY)
    }

    private fun fetchModels() {
        val provider = saveCurrentProvider() ?: return
        if (provider.apiKey.isBlank()) {
            alert(getString(R.string.ai_fetch_models)) {
                setMessage(getString(R.string.ai_api_key_required))
                okButton()
            }
            return
        }
        waitDialog.setText(R.string.ai_fetch_models)
        waitDialog.setOnCancelListener { requestJob?.cancel() }
        waitDialog.show()
        requestJob?.cancel()
        requestJob = lifecycleScope.launch {
            try {
                val models = AiManager.fetchAndSaveModels(provider.id)
                check(models.isNotEmpty()) { getString(R.string.ai_model_list_empty) }
                val saved = AiProviderStore.provider(provider.id) ?: provider
                if (saved.model.isBlank() || models.none { it.id == saved.model }) {
                    AiProviderStore.saveProvider(saved.copy(model = models.first().id))
                }
                refreshCurrentDetail()
            } catch (e: Throwable) {
                alert(getString(R.string.ai_fetch_models)) {
                    setMessage(getString(R.string.ai_test_failed, e.localizedMessage ?: e.toString()))
                    okButton()
                }
            } finally {
                waitDialog.dismiss()
            }
        }
    }

    private fun testConnection() {
        val provider = saveCurrentProvider() ?: return
        if (provider.apiKey.isBlank()) {
            alert(getString(R.string.ai_test_connection)) {
                setMessage(getString(R.string.ai_api_key_required))
                okButton()
            }
            return
        }
        waitDialog.setText(R.string.ai_test_connection)
        waitDialog.setOnCancelListener { requestJob?.cancel() }
        waitDialog.show()
        requestJob?.cancel()
        requestJob = lifecycleScope.launch {
            try {
                val result = AiManager.testConnection(provider.id)
                alert(getString(R.string.ai_test_connection)) {
                    setMessage(
                        getString(
                            R.string.ai_test_success,
                            result.content.take(300),
                            result.model ?: provider.model
                        )
                    )
                    okButton()
                }
            } catch (e: Throwable) {
                alert(getString(R.string.ai_test_connection)) {
                    setMessage(getString(R.string.ai_test_failed, e.localizedMessage ?: e.toString()))
                    okButton()
                }
            } finally {
                waitDialog.dismiss()
            }
        }
    }

    private fun queryBalance() {
        val provider = saveCurrentProvider() ?: return
        if (provider.apiKey.isBlank()) {
            alert(getString(R.string.ai_query_balance)) {
                setMessage(getString(R.string.ai_api_key_required))
                okButton()
            }
            return
        }
        waitDialog.setText(R.string.ai_query_balance)
        waitDialog.setOnCancelListener { requestJob?.cancel() }
        waitDialog.show()
        requestJob?.cancel()
        requestJob = lifecycleScope.launch {
            try {
                val result = AiManager.queryBalance(provider.id)
                alert(getString(R.string.ai_query_balance)) {
                    setMessage(formatBalanceResult(result))
                    okButton()
                }
            } catch (e: Throwable) {
                alert(getString(R.string.ai_query_balance)) {
                    setMessage(getString(R.string.ai_test_failed, e.localizedMessage ?: e.toString()))
                    okButton()
                }
            } finally {
                waitDialog.dismiss()
            }
        }
    }

    private fun updateApiKeyVisibility() {
        val selection = binding.editApiKey.selectionStart.coerceAtLeast(0)
        binding.editApiKey.inputType = if (apiKeyVisible) {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        } else {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        binding.buttonToggleApiKey.setImageResource(
            if (apiKeyVisible) R.drawable.ic_visibility else R.drawable.ic_visibility_off
        )
        binding.editApiKey.setSelection(selection.coerceAtMost(binding.editApiKey.text?.length ?: 0))
    }

    private fun formatBalanceResult(result: io.legado.app.help.ai.AiBalanceResult): String {
        val items = result.items.joinToString("\n") { item ->
            buildString {
                append(
                    getString(
                        R.string.ai_balance_item,
                        item.name,
                        item.remaining?.formatBalanceNumber() ?: "-",
                        item.unit.orEmpty()
                    ).trim()
                )
                if (item.total != null || item.used != null) {
                    append('\n')
                    append(
                        getString(
                            R.string.ai_balance_item_detail,
                            item.total?.formatBalanceNumber() ?: "-",
                            item.used?.formatBalanceNumber() ?: "-",
                            item.unit.orEmpty()
                        ).trim()
                    )
                }
                if (item.isValid == false && !item.invalidMessage.isNullOrBlank()) {
                    append('\n')
                    append(item.invalidMessage)
                }
            }
        }
        return getString(R.string.ai_balance_result, result.providerName, items)
    }

    private fun Double.formatBalanceNumber(): String {
        return balanceNumberFormat.format(this)
    }

    private fun refreshModelSpinner(provider: AiProviderSetting) {
        val options = buildList {
            if (provider.model.isNotBlank()) {
                add(provider.model)
            }
            addAll(provider.models.map { it.id }.filter { it.isNotBlank() })
        }.distinct()
        modelOptions = options
        val displayItems = if (options.isEmpty()) {
            listOf(getString(R.string.ai_model_list_empty))
        } else {
            options
        }
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            displayItems
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        ignoreModelSelection = true
        binding.spinnerModel.adapter = adapter
        binding.spinnerModel.isEnabled = options.isNotEmpty()
        val selectedIndex = options.indexOf(provider.model).takeIf { it >= 0 } ?: 0
        binding.spinnerModel.setSelection(selectedIndex, false)
        ignoreModelSelection = false
    }

    private fun AiProviderSetting.iconRes(): Int {
        return when (id) {
            "openai" -> R.drawable.ic_provider_openai
            "claude" -> R.drawable.ic_provider_claude
            "gemini" -> R.drawable.ic_provider_gemini
            "deepseek" -> R.drawable.ic_provider_deepseek
            "xiaomi_mimo" -> R.drawable.ic_provider_mimo
            else -> R.drawable.ic_ai_provider
        }
    }

    private fun AiProviderSetting.visibleModelCount(): Int {
        return if (apiKey.isBlank()) 0 else models.size
    }

    private fun AiPromptStore.Prompt.displayName(): String {
        return when (this) {
            AiPromptStore.Prompt.PARAGRAPH_PURIFY -> getString(R.string.ai_prompt_paragraph_purify)
            AiPromptStore.Prompt.CHAPTER_OPTIMIZE -> getString(R.string.ai_prompt_chapter_optimize)
        }
    }

    private fun AiPromptStore.Prompt.summary(): String {
        return when (this) {
            AiPromptStore.Prompt.PARAGRAPH_PURIFY ->
                getString(R.string.ai_prompt_paragraph_purify_summary)
            AiPromptStore.Prompt.CHAPTER_OPTIMIZE ->
                getString(R.string.ai_prompt_chapter_optimize_summary)
        }
    }

    private fun AiPromptStore.Prompt.iconText(): String {
        return when (this) {
            AiPromptStore.Prompt.PARAGRAPH_PURIFY -> "段"
            AiPromptStore.Prompt.CHAPTER_OPTIMIZE -> "章"
        }
    }

    private inner class AiProviderAdapter :
        RecyclerAdapter<AiProviderSetting, ItemAiProviderBinding>(requireContext()),
        ItemTouchCallback.Callback {

        var activeProviderId: String = ""
        var onSelectProvider: ((AiProviderSetting) -> Unit)? = null
        private var isMoved = false

        override fun getViewBinding(parent: ViewGroup): ItemAiProviderBinding {
            return ItemAiProviderBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemAiProviderBinding,
            item: AiProviderSetting,
            payloads: MutableList<Any>
        ) {
            val isActive = item.id == activeProviderId
            binding.imageIcon.setImageResource(item.iconRes())
            binding.viewActiveIndicator.isVisible = isActive
            binding.imageDragHandle.isVisible = providerSearchQuery.isBlank()
            binding.textName.text = item.name
            binding.textEnabled.setBackgroundResource(
                if (item.enabled) R.drawable.ng_bg_tag_success else R.drawable.ng_bg_tag_warning
            )
            binding.textEnabled.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (item.enabled) R.color.ng_success else R.color.ng_warning
                )
            )
            binding.textEnabled.text =
                if (item.enabled) getString(R.string.enabled) else getString(R.string.disabled)
            binding.textModelCount.text =
                getString(R.string.ai_model_list_count, item.visibleModelCount().toString())
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun registerListener(holder: ItemViewHolder, binding: ItemAiProviderBinding) {
            binding.layoutSelectProvider.setOnClickListener {
                getItemByLayoutPosition(holder.layoutPosition)?.let { item ->
                    onSelectProvider?.invoke(item)
                }
            }
            binding.imageDragHandle.setOnTouchListener { _, event ->
                if (providerSearchQuery.isBlank()) {
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        providerItemTouchHelper.startDrag(holder)
                    }
                    true
                } else {
                    false
                }
            }
        }

        override fun swap(srcPosition: Int, targetPosition: Int): Boolean {
            if (providerSearchQuery.isNotBlank()) {
                return false
            }
            swapItem(srcPosition, targetPosition)
            isMoved = true
            return true
        }

        override fun onClearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            if (isMoved && providerSearchQuery.isBlank()) {
                AiProviderStore.saveProviders(getItems())
                refreshMain()
            }
            isMoved = false
        }
    }

    private inner class AiPromptAdapter :
        RecyclerAdapter<AiPromptStore.Prompt, ItemAiPromptBinding>(requireContext()) {

        override fun getViewBinding(parent: ViewGroup): ItemAiPromptBinding {
            return ItemAiPromptBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemAiPromptBinding,
            item: AiPromptStore.Prompt,
            payloads: MutableList<Any>
        ) {
            binding.textIcon.text = item.iconText()
            binding.textName.text = item.displayName()
            binding.textSummary.text = item.summary()
            binding.textCustom.isVisible = AiPromptStore.isCustom(item)
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemAiPromptBinding) {
        }
    }
}
