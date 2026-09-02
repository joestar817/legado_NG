package io.legado.app.ui.config

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.ui.design.components.compose.NgPullRefreshBox
import io.legado.app.ui.design.components.compose.NgPullRefreshIndicatorVariant
import io.legado.app.ui.widget.code.CodeView

internal enum class TtsEngineConfigRoute {
    ENGINE_LIST,
    SCRIPT_FORM,
    SCRIPT_SOURCE,
    SCRIPT_VOICES,
    SYSTEM_DETAIL;

    val showsSharedTitleBar: Boolean
        get() = this == SCRIPT_FORM || this == SCRIPT_SOURCE || this == SYSTEM_DETAIL

    val showsDetailTabs: Boolean
        get() = this == SCRIPT_FORM || this == SCRIPT_VOICES

    val detailTabIndex: Int
        get() = if (this == SCRIPT_VOICES) 1 else 0

    fun backDestination(): TtsEngineConfigRoute? = when (this) {
        ENGINE_LIST -> null
        SCRIPT_SOURCE -> SCRIPT_FORM
        SCRIPT_FORM,
        SCRIPT_VOICES,
        SYSTEM_DETAIL -> ENGINE_LIST
    }
}

/**
 * 朗读引擎的单一 Compose 页面外壳。
 *
 * Store、保存、导入、试听和异步请求仍由 Fragment 持有；这里只根据显式路由组合
 * 已验收的列表、表单、源码和发音人内容，并分别承载两条刷新手势。
 */
@Composable
internal fun TtsEngineConfigScreen(
    route: TtsEngineConfigRoute,
    engineRefreshing: Boolean,
    voiceRefreshing: Boolean,
    voiceRefreshEnabled: Boolean,
    scriptEditorView: CodeView,
    onRefreshEngines: () -> Unit,
    onRefreshVoices: () -> Unit,
    engineListContent: @Composable (LazyListState) -> Unit,
    formContent: @Composable () -> Unit,
    formActions: @Composable (sourceMode: Boolean) -> Unit,
    voiceControlsContent: @Composable () -> Unit,
    voiceListContent: @Composable (LazyListState) -> Unit,
    detailTabsContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val engineListState = rememberLazyListState()
    val voiceListState = rememberLazyListState()
    val formScrollState = rememberScrollState()

    when (route) {
        TtsEngineConfigRoute.ENGINE_LIST -> NgPullRefreshBox(
            isRefreshing = engineRefreshing,
            onRefresh = onRefreshEngines,
            modifier = modifier.fillMaxSize(),
            indicatorVariant = NgPullRefreshIndicatorVariant.SINGLE_SPINNER,
        ) {
            engineListContent(engineListState)
        }

        else -> TtsEngineDetailContent(
            route = route,
            voiceRefreshing = voiceRefreshing,
            voiceRefreshEnabled = voiceRefreshEnabled,
            scriptEditorView = scriptEditorView,
            formScrollState = formScrollState,
            voiceListState = voiceListState,
            onRefreshVoices = onRefreshVoices,
            formContent = formContent,
            formActions = formActions,
            voiceControlsContent = voiceControlsContent,
            voiceListContent = voiceListContent,
            detailTabsContent = detailTabsContent,
            modifier = modifier,
        )
    }
}

@Composable
private fun TtsEngineDetailContent(
    route: TtsEngineConfigRoute,
    voiceRefreshing: Boolean,
    voiceRefreshEnabled: Boolean,
    scriptEditorView: CodeView,
    formScrollState: ScrollState,
    voiceListState: LazyListState,
    onRefreshVoices: () -> Unit,
    formContent: @Composable () -> Unit,
    formActions: @Composable (sourceMode: Boolean) -> Unit,
    voiceControlsContent: @Composable () -> Unit,
    voiceListContent: @Composable (LazyListState) -> Unit,
    detailTabsContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .then(
                if (route == TtsEngineConfigRoute.SCRIPT_VOICES) {
                    Modifier
                } else {
                    Modifier.padding(top = 16.dp)
                }
            ),
    ) {
        when (route) {
            TtsEngineConfigRoute.SCRIPT_FORM -> TtsEngineFormRoute(
                scrollState = formScrollState,
                formContent = formContent,
                formActions = formActions,
            )

            TtsEngineConfigRoute.SCRIPT_SOURCE -> TtsEngineSourceRoute(
                scriptEditorView = scriptEditorView,
                formActions = formActions,
            )

            TtsEngineConfigRoute.SCRIPT_VOICES,
            TtsEngineConfigRoute.SYSTEM_DETAIL -> TtsEngineVoicesRoute(
                systemEngine = route == TtsEngineConfigRoute.SYSTEM_DETAIL,
                refreshing = voiceRefreshing,
                refreshEnabled = voiceRefreshEnabled,
                listState = voiceListState,
                onRefresh = onRefreshVoices,
                controlsContent = voiceControlsContent,
                listContent = voiceListContent,
            )

            TtsEngineConfigRoute.ENGINE_LIST -> Unit
        }

        if (route.showsDetailTabs) {
            detailTabsContent()
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ColumnScope.TtsEngineFormRoute(
    scrollState: ScrollState,
    formContent: @Composable () -> Unit,
    formActions: @Composable (sourceMode: Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .padding(top = 10.dp)
            .verticalScroll(scrollState)
            .padding(bottom = 16.dp),
    ) {
        formContent()
        Spacer(Modifier.height(12.dp))
        formActions(false)
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun ColumnScope.TtsEngineSourceRoute(
    scriptEditorView: CodeView,
    formActions: @Composable (sourceMode: Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .padding(top = 10.dp),
    ) {
        AndroidView(
            factory = { scriptEditorView },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
        Spacer(Modifier.height(12.dp))
        formActions(true)
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun ColumnScope.TtsEngineVoicesRoute(
    systemEngine: Boolean,
    refreshing: Boolean,
    refreshEnabled: Boolean,
    listState: LazyListState,
    onRefresh: () -> Unit,
    controlsContent: @Composable () -> Unit,
    listContent: @Composable (LazyListState) -> Unit,
) {
    controlsContent()
    if (systemEngine) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 6.dp),
        ) {
            listContent(listState)
        }
    } else {
        NgPullRefreshBox(
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 6.dp),
            enabled = refreshEnabled,
            indicatorVariant = NgPullRefreshIndicatorVariant.SINGLE_SPINNER,
        ) {
            listContent(listState)
        }
    }
}
