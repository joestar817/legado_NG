package io.legado.app.integration.source

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.legado.app.ui.design.components.compose.NgDialogTextActionButton
import io.legado.app.ui.design.components.compose.NgFormField
import io.legado.app.ui.design.components.compose.NgFormActionButton
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.theme.NgTheme
import io.legado.features.adapter.ExtensionFeatures
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun NgExtensionRepositoriesScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val repositories by remember { ExtensionFeatures.repositoryChanges() }
        .collectAsState(ExtensionFeatures.repositories())
    var url by rememberSaveable { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    fun perform(action: suspend () -> String) {
        if (busy) return
        scope.launch {
            busy = true
            status = "正在处理…"
            try { status = withContext(Dispatchers.IO) { action() } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { status = "操作失败：${e.message ?: e.javaClass.simpleName}" }
            finally { busy = false }
        }
    }
    LaunchedEffect(Unit) {
        perform { ExtensionFeatures.refresh(); "刷新完成，请查看各仓库状态" }
    }
    NgSourceManagementPage(title = "Mihon / Aniyomi 仓库管理", onBack = onBack) {
        // Keep the form and entries in one scroll container, including on small screens.
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Column {
                    NgFormField(label = "仓库 URL（repo.json / index.min.json / index.pb）", value = url, onValueChange = { url = it })
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                        NgFormActionButton(text = "添加仓库", variant = NgButtonVariant.TONAL, enabled = !busy && url.isNotBlank(), onClick = {
                            val input = url.trim()
                            perform {
                                val repo = ExtensionFeatures.addRepository(input).getOrThrow()
                                withContext(Dispatchers.Main) { url = "" }
                                "添加成功：${repo.name}，索引已验证"
                            }
                        })
                        NgDialogTextActionButton(text = "刷新全部", enabled = !busy, onClick = {
                            perform { ExtensionFeatures.refresh(); "刷新完成，请查看各仓库状态" }
                        })
                    }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (status.isNotBlank()) Text(status, Modifier.padding(vertical = 6.dp))
                    Text("已保存 ${repositories.size} 个仓库", Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.labelLarge, color = Color(NgTheme.colors.onSurfaceVariant))
                }
            }
            if (repositories.isEmpty()) item { Text("未预置仓库，请自行输入 Mihon / Aniyomi 仓库地址添加。") }
            items(repositories, key = { it.url }) { repo ->
                NgRepositoryCard(title = repo.name, address = repo.url, status = repo.status, actions = {
                        NgDialogTextActionButton(text = "刷新", enabled = !busy, onClick = {
                            perform { ExtensionFeatures.refreshRepository(repo.url); "${repo.name} 刷新完成" }
                        })
                        NgDialogTextActionButton(text = "移除仓库", enabled = !busy, onClick = {
                            perform { ExtensionFeatures.removeRepository(repo.url); "已移除仓库：${repo.name}" }
                        })
                })
            }
        }
    }
}
