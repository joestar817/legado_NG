package io.legado.app.ui.book.character

import android.os.Bundle
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.base.ComposeActivityBinding
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookCharacter
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.data.entities.BookCharacterTtsBinding
import io.legado.app.data.entities.BookTtsCastRole
import io.legado.app.help.config.AppConfig
import io.legado.app.help.tts.BookTtsBindingPolicy
import io.legado.app.help.tts.BookTtsCastingCoordinator
import io.legado.app.help.tts.TtsEngineSetting
import io.legado.app.help.tts.TtsEngineStore
import io.legado.app.help.tts.TtsEngineType
import io.legado.app.lib.dialogs.alert
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.config.TtsSheetLaunchDebouncer
import io.legado.app.ui.config.TtsVoiceOption
import io.legado.app.ui.config.TtsVoiceSelectionSheet
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.utils.getPrefString
import io.legado.app.utils.observeEvent
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.appCtx

open class BookCharacterTtsActivity : BaseActivity<ComposeActivityBinding>(),
    BookCharacterEditDialog.Callback {

    override val binding by viewBinding(ComposeActivityBinding::inflate)
    override val bindNgToolbarMenu: Boolean = false
    private val cardClickDebouncer = TtsSheetLaunchDebouncer()
    private lateinit var workKey: String
    private var bookName: String = ""
    private var bookAuthor: String = ""
    private var bookUrl: String? = null
    private var page by mutableStateOf(BookCharacterTtsPage.TEMPORARY)
    private var snapshot = Snapshot()
    private var renderedRows: List<Row> = emptyList()
    private var uiRows by mutableStateOf<List<BookCharacterTtsUiRow>>(emptyList())
    private var disabledRoles by mutableStateOf<List<DisabledRoleUiItem>>(emptyList())
    private var formalCount by mutableIntStateOf(0)
    private var temporaryCount by mutableIntStateOf(0)
    private var routeWarningVisible by mutableStateOf(false)
    private var disabledRoleDialogVisible by mutableStateOf(false)
    private var scrollTargetKey by mutableStateOf<String?>(null)
    private var scrollToTopSignal by mutableIntStateOf(0)
    private var pendingCharacterId: Long? = null
    private var reassigning by mutableStateOf(false)

    protected open fun initialPage(): BookCharacterTtsPage = BookCharacterTtsPage.TEMPORARY

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        bookName = intent.getStringExtra(BookCharacterActivity.EXTRA_BOOK_NAME).orEmpty()
        bookAuthor = intent.getStringExtra(BookCharacterActivity.EXTRA_BOOK_AUTHOR).orEmpty()
        bookUrl = intent.getStringExtra(BookCharacterActivity.EXTRA_BOOK_URL)
        workKey = intent.getStringExtra(BookCharacterActivity.EXTRA_WORK_KEY)
            ?: BookCharacterProfile.workKey(bookName, bookAuthor)
        page = initialPage()
        appDb.bookCharacterDao.getOrCreateProfile(bookName, bookAuthor, bookUrl)
        initContent()
        observeData()
        linkPromotedRolesOnce()
        renderRouteWarning()
    }

    private fun initContent() {
        binding.composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
        )
        binding.composeView.setContent {
            NgAppTheme {
                BookCharacterTtsScreen(
                    page = page,
                    rows = uiRows,
                    formalCount = formalCount,
                    temporaryCount = temporaryCount,
                    reassigning = reassigning,
                    disabledRoles = disabledRoles,
                    disabledRoleDialogVisible = disabledRoleDialogVisible,
                    routeWarningVisible = routeWarningVisible,
                    scrollTargetKey = scrollTargetKey,
                    scrollToTopSignal = scrollToTopSignal,
                    onBack = { onBackPressedDispatcher.onBackPressed() },
                    onPageSelected = ::selectPage,
                    onAdd = { showCharacterDialog() },
                    onReassign = ::confirmReassign,
                    onShowDisabledRoles = ::showDisabledRoleManager,
                    onDismissDisabledRoles = { disabledRoleDialogVisible = false },
                    onReenableDisabledRoles = ::reenableDisabledRoles,
                    onDeleteDisabledRecords = ::confirmDeleteDisabledRecords,
                    onRowClick = ::onRowClick,
                    onVoiceClick = ::onVoiceClick,
                    onPromote = ::onPromote,
                    onDeleteRequested = ::deleteRows,
                    onMove = ::moveRow,
                    onMoveFinished = ::persistFormalOrder,
                    onScrollTargetConsumed = { key ->
                        if (scrollTargetKey == key) scrollTargetKey = null
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        renderRouteWarning()
    }

    private fun linkPromotedRolesOnce() {
        lifecycleScope.launch(IO) {
            BookTtsCastingCoordinator.linkPromotedRoles(
                workKey,
                appDb.bookCharacterDao.getCharacters(workKey)
            )
        }
    }

    override fun observeLiveBus() {
        super.observeLiveBus()
        observeEvent<Boolean>(EventBus.TTS_ROUTE_WARNING) {
            renderRouteWarning()
        }
    }

    private fun renderRouteWarning() {
        val warning = BaseReadAloudService.ttsRouteWarning
            ?.takeIf {
                it.bookUrl == bookUrl && it.engineId == AppConfig.multiRoleTtsEngineId
            }
        routeWarningVisible = warning != null
    }

    private fun observeData() {
        lifecycleScope.launch {
            combine(
                appDb.bookCharacterDao.flowCharacters(workKey),
                appDb.bookCharacterDao.flowTtsCastRoles(workKey),
                appDb.bookCharacterDao.flowTtsBindings(workKey)
            ) { characters, castRoles, bindings ->
                Snapshot(characters, castRoles, bindings)
            }.catch {
                toastOnUi(it.localizedMessage)
            }.flowOn(IO).collect { value ->
                snapshot = value.copy(voiceCatalog = snapshot.voiceCatalog)
                renderPage()
            }
        }
        lifecycleScope.launch(IO) {
            val voiceCatalog = VoiceCatalogSnapshot.load()
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                snapshot = snapshot.copy(voiceCatalog = voiceCatalog)
                renderPage()
            }
        }
    }

    private fun selectPage(newPage: BookCharacterTtsPage) {
        if (page == newPage && renderedRows.isNotEmpty()) return
        page = newPage
        renderPage()
        scrollToTopSignal++
    }

    private fun renderPage() {
        val rows = buildRows(snapshot.characters, snapshot.castRoles, snapshot.bindings)
        renderedRows = rows
        uiRows = rows.map { it.toUiRow() }
        formalCount = snapshot.characters.count { it.enabled }
        temporaryCount = activeTemporaryRoles().size
        disabledRoles = buildList {
            snapshot.characters.filter { !it.enabled }
                .sortedWith(compareBy<BookCharacter> { it.sortOrder }.thenBy { it.id })
                .forEach { character ->
                    add(
                        DisabledRoleUiItem(
                            key = "character_${character.id}",
                            name = character.name,
                            summary = getString(
                                R.string.character_disabled_formal_summary,
                                BookCharacterLabels.roleLabel(
                                    this@BookCharacterTtsActivity,
                                    character.roleTag,
                                ),
                            ),
                        ),
                    )
                }
            ignoredTemporaryRoles().forEach { role ->
                add(
                    DisabledRoleUiItem(
                        key = "cast_${role.id}",
                        name = role.name,
                        summary = getString(
                            R.string.character_disabled_temporary_summary,
                            BookCharacterLabels.genderLabel(this@BookCharacterTtsActivity, role.gender),
                            role.occurrenceCount,
                        ),
                    ),
                )
            }
        }
        if (disabledRoles.isEmpty()) disabledRoleDialogVisible = false
        val characterId = pendingCharacterId ?: return
        val row = rows.firstOrNull {
            it is Row.Character && it.character.id == characterId
        }
        if (row != null) {
            pendingCharacterId = null
            scrollTargetKey = row.key()
        }
    }

    private fun buildRows(
        characters: List<BookCharacter>,
        castRoles: List<BookTtsCastRole>,
        bindings: List<BookCharacterTtsBinding>
    ): List<Row> {
        val currentEngineId = AppConfig.multiRoleTtsEngineId.orEmpty()
        val bindingMap = bindings.associateBy { Triple(it.targetType, it.targetId, it.engineId) }
        val narratorBinding = bindings
            .filter { it.targetType == BookCharacterTtsBinding.TargetType.NARRATOR }
            .maxByOrNull { it.updatedAt }
        return when (page) {
            BookCharacterTtsPage.FORMAL -> characters.filter { it.enabled }.map { character ->
                Row.Character(
                    character = character,
                    binding = bindingMap[
                        Triple(
                            BookCharacterTtsBinding.TargetType.CHARACTER,
                            character.id,
                            currentEngineId
                        )
                    ]
                )
            }

            BookCharacterTtsPage.TEMPORARY -> castRoles
                .filter { !it.ignored && it.linkedCharacterId == null && it.isVisibleTemporaryRole() }
                .sortedWith(temporaryRoleComparator())
                .map { role ->
                    Row.CastRole(
                        role = role,
                        binding = bindingMap[
                            Triple(
                                BookCharacterTtsBinding.TargetType.CAST_ROLE,
                                role.id,
                                currentEngineId
                            )
                        ]
                    )
                }

            BookCharacterTtsPage.DEFAULTS -> listOf(
                Row.Narrator(narratorBinding),
                Row.DialogueFallback(
                    gender = BookCharacter.Gender.MALE,
                    binding = bindingMap[
                        Triple(
                            BookCharacterTtsBinding.TargetType.DIALOGUE_MALE,
                            0L,
                            currentEngineId
                        )
                    ]
                ),
                Row.DialogueFallback(
                    gender = BookCharacter.Gender.FEMALE,
                    binding = bindingMap[
                        Triple(
                            BookCharacterTtsBinding.TargetType.DIALOGUE_FEMALE,
                            0L,
                            currentEngineId
                        )
                    ]
                )
            )
        }
    }

    private fun temporaryRoleComparator(): Comparator<BookTtsCastRole> {
        val currentChapterIndex = ReadBook.book
            ?.takeIf { BookCharacterProfile.workKey(it.name, it.author) == workKey }
            ?.let { ReadBook.durChapterIndex }
        return compareByDescending<BookTtsCastRole> {
            currentChapterIndex != null && it.lastChapterIndex == currentChapterIndex
        }.thenByDescending { it.lastChapterIndex }
            .thenByDescending { it.occurrenceCount }
            .thenBy { it.id }
    }

    private fun showCharacterDialog(characterId: Long = 0L, castRoleId: Long = 0L) {
        showDialogFragment(BookCharacterEditDialog(workKey, characterId, castRoleId))
    }

    private fun showPromoteDialog(role: BookTtsCastRole) {
        showCharacterDialog(castRoleId = role.id)
    }

    override fun onCharacterSaved(characterId: Long, castRoleId: Long) {
        setResult(RESULT_OK)
        pendingCharacterId = characterId
        if (castRoleId > 0L || page != BookCharacterTtsPage.FORMAL) {
            selectPage(BookCharacterTtsPage.FORMAL)
        }
    }

    private fun onRowClick(key: String) {
        val row = rowForKey(key) ?: return
        if (row is Row.Character) {
            showCharacterDialog(characterId = row.character.id)
        } else if (cardClickDebouncer.tryAcquire(SystemClock.elapsedRealtime())) {
            showVoiceSheet(row)
        }
    }

    private fun onVoiceClick(key: String) {
        if (cardClickDebouncer.tryAcquire(SystemClock.elapsedRealtime())) {
            rowForKey(key)?.let(::showVoiceSheet)
        }
    }

    private fun onPromote(key: String) {
        (rowForKey(key) as? Row.CastRole)?.let { showPromoteDialog(it.role) }
    }

    private fun rowForKey(key: String): Row? = renderedRows.firstOrNull { it.key() == key }

    private fun moveRow(fromIndex: Int, toIndex: Int) {
        if (page != BookCharacterTtsPage.FORMAL) return
        val from = renderedRows.getOrNull(fromIndex)
        val to = renderedRows.getOrNull(toIndex)
        if (from !is Row.Character || to !is Row.Character) return
        val moved = renderedRows.toMutableList()
        moved.add(toIndex, moved.removeAt(fromIndex))
        renderedRows = moved
        uiRows = moved.map { it.toUiRow() }
    }

    private fun persistFormalOrder() {
        if (page != BookCharacterTtsPage.FORMAL) return
        val now = System.currentTimeMillis()
        val sorted = renderedRows.mapIndexedNotNull { index, row ->
            (row as? Row.Character)?.character?.apply {
                sortOrder = index
                updatedAt = now
            }
        }
        lifecycleScope.launch(IO) {
            appDb.bookCharacterDao.updateCharacters(*sorted.toTypedArray())
            appDb.bookCharacterDao.updateCharacterCount(workKey, now)
            setResult(RESULT_OK)
        }
    }

    private fun showVoiceSheet(row: Row) {
        val selectedBinding = row.binding()
        TtsVoiceSelectionSheet(
            context = this,
            lifecycleScope = lifecycleScope,
            title = row.title(),
            searchHint = getString(R.string.default_tts_voice_search),
            emptyText = getString(R.string.character_tts_no_voice_options),
            engines = { selectableEngines(row) },
            isSelected = { option -> isSelected(selectedBinding, option) },
            onSelect = { option -> saveBinding(row, option) },
            beforePreview = {
                if (BaseReadAloudService.isPlay()) ReadAloud.pause(this)
            },
            titleAction = when {
                selectedBinding == null -> null
                row is Row.Narrator || row is Row.DialogueFallback -> {
                    getString(R.string.clear) to { clearBinding(row) }
                }
                else -> {
                    getString(R.string.character_tts_use_dialogue_fallback) to {
                        saveInheritBinding(row)
                    }
                }
            }
        ).show()
    }

    private fun selectableEngines(row: Row) = when (row) {
        is Row.Narrator -> TtsEngineStore.engines().filter { it.enabled }
        else -> listOfNotNull(
            TtsEngineStore.engine(AppConfig.multiRoleTtsEngineId)
                ?.takeIf { it.enabled && it.type == TtsEngineType.SCRIPT }
        )
    }

    private fun saveBinding(row: Row, option: TtsVoiceOption) {
        lifecycleScope.launch(IO) {
            val now = System.currentTimeMillis()
            val old = row.binding()
            val oldEngineId = old?.engineId
            val stored = (old ?: row.newBinding()).apply {
                engineId = option.engine.id
                voiceId = option.voice.id.takeUnless { option.systemDefault }
                bindingMode = BookCharacterTtsBinding.BindingMode.MANUAL
                if (createdAt <= 0L) createdAt = now
                updatedAt = now
            }
            if (row is Row.Narrator && !oldEngineId.isNullOrBlank() && oldEngineId != option.engine.id) {
                appDb.bookCharacterDao.deleteTtsBinding(
                    workKey,
                    BookCharacterTtsBinding.TargetType.NARRATOR,
                    0L,
                    oldEngineId
                )
            }
            appDb.bookCharacterDao.upsertTtsBinding(stored)
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                toastOnUi(getString(R.string.character_tts_binding_saved))
                refreshRunningReadAloud()
            }
        }
    }

    private fun clearBinding(row: Row) {
        val target = row.target()
        val engineId = row.binding()?.engineId ?: AppConfig.multiRoleTtsEngineId.orEmpty()
        lifecycleScope.launch(IO) {
            appDb.bookCharacterDao.deleteTtsBinding(workKey, target.first, target.second, engineId)
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                toastOnUi(getString(R.string.character_tts_binding_cleared))
                refreshRunningReadAloud()
            }
        }
    }

    private fun saveInheritBinding(row: Row) {
        val engineId = AppConfig.multiRoleTtsEngineId.orEmpty()
        if (engineId.isBlank()) return
        lifecycleScope.launch(IO) {
            val now = System.currentTimeMillis()
            val stored = (row.binding() ?: row.newBinding()).apply {
                this.engineId = engineId
                voiceId = null
                bindingMode = BookCharacterTtsBinding.BindingMode.INHERIT
                if (createdAt <= 0L) createdAt = now
                updatedAt = now
            }
            appDb.bookCharacterDao.upsertTtsBinding(stored)
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                toastOnUi(getString(R.string.character_tts_binding_saved))
                refreshRunningReadAloud()
            }
        }
    }

    private fun refreshRunningReadAloud() {
        if (BaseReadAloudService.isRun && AppConfig.readAloudMultiRole) {
            ReadAloud.refreshTtsRoute(this)
        }
    }

    private fun ignoredTemporaryRoles(): List<BookTtsCastRole> {
        return snapshot.castRoles
            .filter { it.ignored && it.linkedCharacterId == null && it.isVisibleTemporaryRole() }
            .sortedWith(
                compareByDescending<BookTtsCastRole> { it.occurrenceCount }
                    .thenBy { it.id }
            )
    }

    private fun activeTemporaryRoles(): List<BookTtsCastRole> {
        return snapshot.castRoles.filter {
            !it.ignored && it.linkedCharacterId == null && it.isVisibleTemporaryRole()
        }
    }

    private fun confirmReassign() {
        if (reassigning || activeTemporaryRoles().isEmpty()) return
        if (TtsEngineStore.engine(AppConfig.multiRoleTtsEngineId) == null) {
            toastOnUi(R.string.multi_role_tts_engine_unset)
            return
        }
        alert(titleResource = R.string.character_reassign) {
            setMessage(getString(R.string.character_reassign_message))
            yesButton { reassignTemporaryRoles() }
            noButton()
        }
    }

    private fun reassignTemporaryRoles() {
        if (reassigning) return
        reassigning = true
        if (BaseReadAloudService.isRun && AppConfig.readAloudMultiRole) {
            ReadAloud.prepareTtsCasting(this)
        }
        lifecycleScope.launch(IO) {
            val result = runCatching {
                BookTtsCastingCoordinator.reassignTemporaryRoles(workKey)
            }
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                reassigning = false
                result.onSuccess { count ->
                    toastOnUi(getString(R.string.character_reassign_done, count))
                }.onFailure { error ->
                    toastOnUi(error.localizedMessage ?: getString(R.string.character_reassign_failed))
                }
                refreshRunningReadAloud()
            }
        }
    }

    private fun showDisabledRoleManager() {
        disabledRoleDialogVisible = true
    }

    private fun reenableDisabledRoles(keys: Set<String>) {
        val characters = snapshot.characters.filter { !it.enabled && "character_${it.id}" in keys }
        val roles = ignoredTemporaryRoles().filter { "cast_${it.id}" in keys }
        val count = characters.size + roles.size
        if (count == 0) return
        disabledRoleDialogVisible = false
        lifecycleScope.launch(IO) {
            appDb.runInTransaction {
                val now = System.currentTimeMillis()
                characters.forEach { character ->
                    character.enabled = true
                    character.updatedAt = now
                    appDb.bookCharacterDao.updateCharacter(character)
                }
                roles.forEach { role ->
                    appDb.bookCharacterDao.restoreTtsCastRole(role.id, now)
                }
                if (characters.isNotEmpty()) {
                    appDb.bookCharacterDao.updateCharacterCount(workKey, now)
                }
            }
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                setResult(RESULT_OK)
                toastOnUi(getString(R.string.character_reenable_done, count))
                refreshRunningReadAloud()
            }
        }
    }

    private fun confirmDeleteDisabledRecords(keys: Set<String>) {
        val count = disabledRoles.count { it.key in keys }
        if (count == 0) return
        alert(titleResource = R.string.character_disabled_delete_title) {
            setMessage(getString(R.string.character_disabled_delete_message, count))
            yesButton {
                disabledRoleDialogVisible = false
                deleteDisabledRecords(keys)
            }
            noButton()
        }
    }

    private fun deleteDisabledRecords(keys: Set<String>) {
        val characters = snapshot.characters.filter { !it.enabled && "character_${it.id}" in keys }
        val roles = ignoredTemporaryRoles().filter { "cast_${it.id}" in keys }
        if (characters.isEmpty() && roles.isEmpty()) return
        lifecycleScope.launch(IO) {
            var deletedCount = 0
            appDb.runInTransaction {
                characters.forEach { character ->
                    appDb.bookCharacterDao.deleteCharacterWithTts(character)
                    deletedCount++
                }
                roles.forEach { role ->
                    if (appDb.bookCharacterDao.permanentlyDeleteIgnoredTtsCastRole(role.id)) {
                        deletedCount++
                    }
                }
            }
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                setResult(RESULT_OK)
                toastOnUi(getString(R.string.character_disabled_delete_done, deletedCount))
                refreshRunningReadAloud()
            }
        }
    }

    private fun deleteRows(keys: Set<String>, mode: BookCharacterDeleteMode) {
        val rows = renderedRows.filter { it.key() in keys }
            .filter { it is Row.Character || it is Row.CastRole }
        if (rows.isEmpty()) return
        lifecycleScope.launch(IO) {
            appDb.runInTransaction {
                val now = System.currentTimeMillis()
                var disabledFormal = false
                rows.forEach { row ->
                    when (row) {
                        is Row.Character -> if (mode == BookCharacterDeleteMode.DELETE_ONLY) {
                            appDb.bookCharacterDao.deleteCharacterWithTts(row.character)
                        } else {
                            row.character.enabled = false
                            row.character.updatedAt = now
                            appDb.bookCharacterDao.updateCharacter(row.character)
                            disabledFormal = true
                        }

                        is Row.CastRole -> if (mode == BookCharacterDeleteMode.DELETE_ONLY) {
                            appDb.bookCharacterDao.deleteTtsCastRoleWithTts(row.role)
                        } else {
                            appDb.bookCharacterDao.ignoreTtsCastRole(row.role)
                        }

                        else -> Unit
                    }
                }
                if (disabledFormal) {
                    appDb.bookCharacterDao.updateCharacterCount(workKey, now)
                }
            }
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                setResult(RESULT_OK)
                toastOnUi(
                    getString(
                        if (mode == BookCharacterDeleteMode.DELETE_ONLY) {
                            R.string.character_delete_only_done
                        } else {
                            R.string.character_delete_and_disable_done
                        },
                        rows.size,
                    ),
                )
                refreshRunningReadAloud()
            }
        }
    }

    private fun Row.voiceSummary(): String {
        val speaker = when (this) {
            is Row.Narrator -> bindingVoiceName(binding())
                ?: defaultNarratorVoiceName()
                ?: globalVoiceName()

            is Row.DialogueFallback -> bindingVoiceName(binding())
                ?: defaultDialogueVoiceName(gender)
                ?: getString(R.string.character_tts_voice_unset)

            is Row.Character -> roleVoiceName(binding(), character.gender)
            is Row.CastRole -> roleVoiceName(binding(), role.gender)
        }
        return getString(R.string.character_tts_speaker_summary, speaker)
    }

    private fun roleVoiceName(binding: BookCharacterTtsBinding?, gender: String): String {
        if (binding?.bindingMode == BookCharacterTtsBinding.BindingMode.INHERIT) {
            return fallbackVoiceName(gender)
        }
        if (!snapshot.voiceCatalog.loaded) {
            return bindingVoiceName(binding) ?: fallbackVoiceName(gender)
        }
        val engine = binding?.let { snapshot.voiceCatalog.engines[it.engineId] }
            ?.takeIf { it.enabled }
        val usableVoiceIds = engine
            ?.let { snapshot.voiceCatalog.enabledVoiceIds[it.id] }
            .orEmpty()
        return when (BookTtsBindingPolicy.autoState(binding, usableVoiceIds)) {
            BookTtsBindingPolicy.AutoState.PENDING -> pendingVoiceName(gender)
            BookTtsBindingPolicy.AutoState.PROVISIONAL -> getString(
                R.string.character_tts_provisional_voice,
                bindingVoiceName(binding) ?: pendingVoiceName(gender)
            )

            BookTtsBindingPolicy.AutoState.STABLE,
            BookTtsBindingPolicy.AutoState.PROTECTED ->
                bindingVoiceName(binding) ?: fallbackVoiceName(gender)
        }
    }

    private fun bindingVoiceName(binding: BookCharacterTtsBinding?): String? {
        binding ?: return null
        if (binding.bindingMode == BookCharacterTtsBinding.BindingMode.INHERIT) return null
        val voiceId = binding.voiceId
        return if (voiceId.isNullOrBlank()) {
            getString(R.string.character_tts_system_default_voice)
        } else {
            snapshot.voiceCatalog.voiceNames[binding.engineId to voiceId] ?: voiceId
        }
    }

    private fun fallbackVoiceName(gender: String): String {
        return getString(
            when (gender) {
                BookCharacter.Gender.MALE -> R.string.character_tts_fallback_male
                BookCharacter.Gender.FEMALE -> R.string.character_tts_fallback_female
                else -> R.string.character_tts_fallback_generic
            }
        )
    }

    private fun pendingVoiceName(gender: String): String {
        return getString(
            when (gender) {
                BookCharacter.Gender.MALE -> R.string.character_tts_pending_male
                BookCharacter.Gender.FEMALE -> R.string.character_tts_pending_female
                else -> R.string.character_tts_pending_generic
            }
        )
    }

    private fun globalVoiceName(): String {
        val engine = snapshot.voiceCatalog.activeEngineId
            ?.let(snapshot.voiceCatalog.engines::get)
        val voice = engine?.activeVoiceId?.let { voiceId ->
            snapshot.voiceCatalog.voiceNames[engine.id to voiceId]
        }
        return when {
            engine == null || !engine.enabled -> getString(R.string.character_tts_voice_unset)
            !voice.isNullOrBlank() -> voice
            else -> getString(R.string.character_tts_system_default_voice)
        }
    }

    private fun defaultNarratorVoiceName(): String? {
        return configuredVoiceName(
            engineId = AppConfig.defaultNarratorTtsEngineId,
            voiceId = AppConfig.defaultNarratorTtsVoiceId,
            allowSystemDefault = true
        )
    }

    private fun defaultDialogueVoiceName(gender: String): String? {
        val voiceId = if (gender == BookCharacter.Gender.MALE) {
            AppConfig.defaultDialogueMaleTtsVoiceId
        } else {
            AppConfig.defaultDialogueFemaleTtsVoiceId
        }
        return configuredVoiceName(
            engineId = AppConfig.multiRoleTtsEngineId,
            voiceId = voiceId,
            allowSystemDefault = false
        )
    }

    private fun configuredVoiceName(
        engineId: String?,
        voiceId: String?,
        allowSystemDefault: Boolean
    ): String? {
        val engine = engineId
            ?.let(snapshot.voiceCatalog.engines::get)
            ?.takeIf { it.enabled }
            ?: return null
        return when {
            !voiceId.isNullOrBlank() &&
                voiceId in snapshot.voiceCatalog.enabledVoiceIds[engine.id].orEmpty() ->
                snapshot.voiceCatalog.voiceNames[engine.id to voiceId]

            allowSystemDefault && engine.type == TtsEngineType.SYSTEM -> {
                getString(R.string.character_tts_system_default_voice)
            }

            else -> null
        }
    }

    private fun isSelected(
        binding: BookCharacterTtsBinding?,
        option: TtsVoiceOption
    ): Boolean {
        binding ?: return false
        return binding.engineId == option.engine.id && if (option.systemDefault) {
            binding.voiceId.isNullOrBlank()
        } else {
            binding.voiceId == option.voice.id
        }
    }

    private fun Row.title(): String = when (this) {
        is Row.Narrator -> getString(R.string.character_tts_narrator)
        is Row.DialogueFallback -> getString(
            if (gender == BookCharacter.Gender.MALE) {
                R.string.character_tts_dialogue_male
            } else {
                R.string.character_tts_dialogue_female
            }
        )

        is Row.Character -> character.name
        is Row.CastRole -> role.name
    }

    private fun Row.gender(): String? = when (this) {
        is Row.Narrator -> null
        is Row.DialogueFallback -> gender
        is Row.Character -> character.gender
        is Row.CastRole -> role.gender
    }

    private fun Row.binding(): BookCharacterTtsBinding? {
        val stored = when (this) {
            is Row.Narrator -> binding
            is Row.DialogueFallback -> binding
            is Row.Character -> binding
            is Row.CastRole -> binding
        }
        return stored?.takeIf {
            this is Row.Narrator || it.engineId == AppConfig.multiRoleTtsEngineId
        }
    }

    private fun Row.newBinding(): BookCharacterTtsBinding = when (this) {
        is Row.Narrator -> BookCharacterTtsBinding.narrator(workKey)
        is Row.DialogueFallback -> if (gender == BookCharacter.Gender.MALE) {
            BookCharacterTtsBinding.dialogueMale(workKey)
        } else {
            BookCharacterTtsBinding.dialogueFemale(workKey)
        }

        is Row.Character -> BookCharacterTtsBinding.character(workKey, character.id)
        is Row.CastRole -> BookCharacterTtsBinding.castRole(workKey, role.id)
    }

    private fun Row.target(): Pair<String, Long> = when (this) {
        is Row.Narrator -> BookCharacterTtsBinding.TargetType.NARRATOR to 0L
        is Row.DialogueFallback -> if (gender == BookCharacter.Gender.MALE) {
            BookCharacterTtsBinding.TargetType.DIALOGUE_MALE to 0L
        } else {
            BookCharacterTtsBinding.TargetType.DIALOGUE_FEMALE to 0L
        }

        is Row.Character -> BookCharacterTtsBinding.TargetType.CHARACTER to character.id
        is Row.CastRole -> BookCharacterTtsBinding.TargetType.CAST_ROLE to role.id
    }

    private fun Row.toUiRow(): BookCharacterTtsUiRow {
        return BookCharacterTtsUiRow(
            key = key(),
            title = title(),
            avatar = when (this) {
                is Row.Narrator -> getString(R.string.character_tts_narrator_avatar)
                is Row.DialogueFallback -> getString(
                    if (gender == BookCharacter.Gender.MALE) {
                        R.string.character_tts_dialogue_male_avatar
                    } else {
                        R.string.character_tts_dialogue_female_avatar
                    },
                )

                is Row.Character -> character.name.firstOrNull()?.toString().orEmpty()
                is Row.CastRole -> role.name.firstOrNull()?.toString().orEmpty()
            },
            gender = when (gender()) {
                BookCharacter.Gender.MALE -> BookCharacterTtsGender.MALE
                BookCharacter.Gender.FEMALE -> BookCharacterTtsGender.FEMALE
                else -> BookCharacterTtsGender.UNKNOWN
            },
            roleLabel = when (this) {
                is Row.Narrator -> null
                is Row.DialogueFallback -> getString(R.string.character_tts_dialogue_fallback)
                is Row.Character -> BookCharacterLabels.roleLabel(
                    this@BookCharacterTtsActivity,
                    character.roleTag,
                )

                is Row.CastRole -> getString(R.string.character_temporary_role)
            },
            voiceSummary = voiceSummary(),
            kind = when (this) {
                is Row.Character -> BookCharacterTtsRowKind.FORMAL
                is Row.CastRole -> BookCharacterTtsRowKind.TEMPORARY
                is Row.Narrator, is Row.DialogueFallback -> BookCharacterTtsRowKind.DEFAULT
            },
        )
    }

    private fun Row.key(): String = when (this) {
        is Row.Narrator -> "narrator"
        is Row.DialogueFallback -> if (gender == BookCharacter.Gender.MALE) {
            "dialogue_male"
        } else {
            "dialogue_female"
        }

        is Row.Character -> "character_${character.id}"
        is Row.CastRole -> "cast_${role.id}"
    }

    private data class Snapshot(
        val characters: List<BookCharacter> = emptyList(),
        val castRoles: List<BookTtsCastRole> = emptyList(),
        val bindings: List<BookCharacterTtsBinding> = emptyList(),
        val voiceCatalog: VoiceCatalogSnapshot = VoiceCatalogSnapshot()
    )

    private data class VoiceCatalogSnapshot(
        val loaded: Boolean = false,
        val engines: Map<String, TtsEngineSetting> = emptyMap(),
        val enabledVoiceIds: Map<String, Set<String>> = emptyMap(),
        val voiceNames: Map<Pair<String, String>, String> = emptyMap(),
        val activeEngineId: String? = null
    ) {
        companion object {
            fun load(): VoiceCatalogSnapshot {
                val engines = TtsEngineStore.engines()
                val activeEngineId = appCtx.getPrefString(PreferKey.ttsEngineV2ActiveId)
                    ?.takeIf { savedId ->
                        engines.any { engine -> engine.id == savedId && engine.enabled }
                    }
                    ?: engines.firstOrNull { it.enabled }?.id
                return VoiceCatalogSnapshot(
                    loaded = true,
                    engines = engines.associateBy { it.id },
                    enabledVoiceIds = engines.associate { engine ->
                        engine.id to engine.enabledVoices().mapTo(mutableSetOf()) { it.id }
                    },
                    voiceNames = engines.flatMap { engine ->
                        engine.effectiveVoices().map { voice ->
                            (engine.id to voice.id) to voice.name
                        }
                    }.toMap(),
                    activeEngineId = activeEngineId
                )
            }
        }
    }

    private sealed interface Row {
        data class Narrator(val binding: BookCharacterTtsBinding?) : Row
        data class DialogueFallback(
            val gender: String,
            val binding: BookCharacterTtsBinding?
        ) : Row

        data class Character(
            val character: BookCharacter,
            val binding: BookCharacterTtsBinding?
        ) : Row

        data class CastRole(
            val role: BookTtsCastRole,
            val binding: BookCharacterTtsBinding?
        ) : Row
    }

}
