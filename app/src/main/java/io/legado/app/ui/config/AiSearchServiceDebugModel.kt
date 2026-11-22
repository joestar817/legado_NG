package io.legado.app.ui.config

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.help.ai.AiExternalSearchClient
import io.legado.app.help.ai.AiExternalSearchDebugResult
import io.legado.app.help.ai.AiExternalSearchHit
import io.legado.app.help.ai.AiExternalSearchService
import io.legado.app.help.ai.AiExternalSearchTool
import io.legado.app.help.ai.AiExternalWebPage
import io.legado.app.help.ai.webUrl
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal data class AiSearchFetchRecord(
    val address: String,
    val siteName: String = "",
    val titleHint: String = "",
    val result: AiExternalSearchDebugResult<AiExternalWebPage>? = null,
)

internal class AiSearchServiceDebugModel(
    private val client: AiExternalSearchClient = AiExternalSearchClient(),
) : ViewModel() {
    private var request: Job? = null
    private var articleAddress by mutableStateOf<String?>(null)
    private var completedFetches by mutableStateOf(emptyList<AiSearchFetchRecord>())
    private var pendingFetch by mutableStateOf<AiSearchFetchRecord?>(null)

    var resultTool by mutableStateOf<AiExternalSearchTool?>(null)
        private set
    var searchResult by mutableStateOf<AiExternalSearchDebugResult<List<AiExternalSearchHit>>?>(null)
        private set
    var activeTool by mutableStateOf<AiExternalSearchTool?>(null)
        private set

    val fetches: List<AiSearchFetchRecord>
        get() = pendingFetch?.let { pending ->
            listOf(pending) + completedFetches.filterNot { it.address == pending.address }
        } ?: completedFetches

    val article: AiSearchFetchRecord?
        get() = fetches.firstOrNull { it.address == articleAddress }

    fun startTest(service: AiExternalSearchService, tool: AiExternalSearchTool, input: String) {
        if (resultTool != null || activeTool != null || input.isBlank()) return
        resultTool = tool
        when (tool) {
            AiExternalSearchTool.SEARCH -> search(service, input.trim())
            AiExternalSearchTool.READ_LINK -> fetch(service, input)
        }
    }

    private fun search(service: AiExternalSearchService, query: String) {
        searchResult = null
        runRequest(AiExternalSearchTool.SEARCH) {
            val result = client.searchForDebug(service, query)
            currentCoroutineContext().ensureActive()
            searchResult = result
        }
    }

    fun fetch(
        service: AiExternalSearchService,
        input: String,
        hit: AiExternalSearchHit? = null,
    ) {
        val text = input.trim()
        if (activeTool != null || text.isEmpty()) return
        val address = text.webUrl() ?: text
        val previous = completedFetches.firstOrNull { it.address == address }
        if (hit != null && previous?.result?.data != null) {
            openArticle(previous)
            return
        }
        val record = AiSearchFetchRecord(
            address = address,
            siteName = hit?.siteName ?: previous?.siteName.orEmpty(),
            titleHint = hit?.title ?: previous?.titleHint.orEmpty(),
        )
        pendingFetch = record
        if (hit != null) openArticle(record)
        runRequest(AiExternalSearchTool.READ_LINK) {
            val result = client.fetchForDebug(service, text)
            currentCoroutineContext().ensureActive()
            completedFetches = listOf(record.copy(result = result)) + completedFetches.filterNot { it.address == address }
            pendingFetch = null
        }
    }

    fun openArticle(record: AiSearchFetchRecord) {
        articleAddress = record.address
    }

    fun handleBack(): Boolean {
        if (articleAddress != null) {
            articleAddress = null
            return true
        }
        if (resultTool == null) return false
        cancelRequest()
        resultTool = null
        return true
    }

    fun reset() {
        cancelRequest()
        articleAddress = null
        resultTool = null
        searchResult = null
        completedFetches = emptyList()
    }

    private fun cancelRequest() {
        request?.cancel()
        request = null
        activeTool = null
        pendingFetch = null
    }

    private fun runRequest(tool: AiExternalSearchTool, block: suspend () -> Unit) {
        activeTool = tool
        request = viewModelScope.launch {
            try {
                block()
            } finally {
                if (isActive) activeTool = null
            }
        }
    }
}
