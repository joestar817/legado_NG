package io.legado.app.ui.main.explore

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import io.legado.app.data.entities.BaseSource
import io.legado.app.ui.login.SourceLoginJsExtensions
import io.legado.app.utils.InfoMap
import kotlinx.coroutines.launch

@Composable
internal fun rememberExploreSourceJsExtensions(
    source: BaseSource?,
    infoMap: InfoMap,
    onRefresh: () -> Unit,
): SourceLoginJsExtensions {
    val activity = LocalContext.current as? AppCompatActivity
    return key(activity, source?.getKey() ?: infoMap.sourceUrl, infoMap) {
        val scope = rememberCoroutineScope()
        val currentOnRefresh by rememberUpdatedState(onRefresh)
        // 桥接类仅弱持有回调，页面必须强持有；离开当前书源后旧回调的协程也随之取消。
        val callback = remember(scope) {
            object : SourceLoginJsExtensions.Callback {
                override fun upUiData(data: Map<String, Any?>?) = Unit

                override fun reUiView(deltaUp: Boolean) {
                    scope.launch { currentOnRefresh() }
                }
            }
        }
        remember(activity, source, callback) {
            SourceLoginJsExtensions(activity, source, callback = callback)
        }
    }
}
