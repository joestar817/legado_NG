package io.legado.app.ui.resource

import io.legado.app.ui.pet.PetManagerActivity
import io.legado.app.help.resource.PetManagementActions

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import io.legado.app.base.BaseActivity
import io.legado.app.base.ComposeActivityBinding
import io.legado.app.data.appDb
import io.legado.app.help.config.AppConfig
import io.legado.app.help.resource.HOME_WIDGET_SKIN_TYPE
import io.legado.app.help.home.HomeWidgetSkinStore
import io.legado.app.help.resource.ResourceEntry
import io.legado.app.help.resource.ResourceProtocol
import io.legado.app.help.resource.ResourceRepository
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.config.ConfigActivity
import io.legado.app.ui.config.ConfigTag
import io.legado.app.ui.config.ThemeInterfaceFontEditorSheet
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.main.HOME_WIDGET_SKIN_REQUEST
import io.legado.app.ui.main.MainActivity
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ResourceCenterActivity : BaseActivity<ComposeActivityBinding>() {
    companion object {
        internal const val BACKGROUND_LIBRARY_OWNER = "backgroundLibraryOwner"
    }

    override val binding by viewBinding(ComposeActivityBinding::inflate)
    override val bindNgToolbarMenu: Boolean = false
    private val resourceSession by viewModels<ResourceCenterSessionViewModel>()

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        resourceSession.start()
        binding.composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        binding.composeView.setContent {
            NgAppTheme {
                val state by ResourceRepository.state.collectAsState()
                val scope = rememberCoroutineScope()
                var fontUseId by rememberSaveable { mutableStateOf<String?>(null) }
                var interfaceFontPath by rememberSaveable { mutableStateOf<String?>(null) }
                var fontUseRequest by remember { mutableIntStateOf(0) }
                ResourceCenterScreen(
                    state = state,
                    initialPurpose = intent.getStringExtra("resourcePurpose"),
                    initialType = intent.getStringExtra("resourceType"),
                    onPriorityChanged = resourceSession::prioritize,
                    onBack = ::finish,
                    onOpenRepository = {
                        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${ResourceProtocol.REPOSITORY}"))) }
                            .onFailure { toastOnUi("无法打开仓库，请检查浏览器") }
                    },
                    onOpenPetManager = { id ->
                        startActivity<PetManagerActivity> { putExtra(PetManagerActivity.PLUGIN_ID, id) }
                    },
                    onUninstallPet = { id ->
                        lifecycleScope.launch {
                            try {
                                PetManagementActions.uninstall(id)
                                toastOnUi("已卸载")
                            } catch (cancelled: CancellationException) { throw cancelled
                            } catch (error: Exception) { toastOnUi(error.message ?: "卸载失败") }
                        }
                    },
                    onUse = { entry ->
                        fontUseRequest++
                        if (entry.category.type == "font") fontUseId = entry.item.id else useResource(entry)
                    },
                )
                fontUseId?.let { id ->
                    ResourceFontUseDialog(
                        onDismiss = { fontUseRequest++; fontUseId = null },
                        onSelect = { destination ->
                            val request = fontUseRequest
                            fontUseId = null
                            scope.launch {
                                val path = try {
                                    ResourceRepository.installedFontPath(id)
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    null
                                }
                                if (request != fontUseRequest) return@launch
                                if (path == null) {
                                    toastOnUi("字体已不可用，请刷新资源目录")
                                } else if (destination == ResourceFontDestination.INTERFACE) {
                                    interfaceFontPath = path
                                } else {
                                    val book = ReadBook.book ?: withContext(Dispatchers.IO) { appDb.bookDao.lastReadBook }
                                    if (request != fontUseRequest) return@launch
                                    if (book == null) {
                                        toastOnUi("请先打开一本书，再设置阅读字体")
                                    } else {
                                        startActivity<ReadBookActivity> {
                                            putExtra("bookUrl", book.bookUrl)
                                            ResourceReaderFontRequest(id, destination, book.bookUrl).writeTo(this)
                                        }
                                    }
                                }
                            }
                        },
                    )
                }
                interfaceFontPath?.let { path ->
                    ThemeInterfaceFontEditorSheet(
                        onDismissRequest = { interfaceFontPath = null },
                        preferredFontPath = path,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ResourceRepository.checkLocalFiles()
        resourceSession.onForeground()
    }

    override fun onPause() {
        resourceSession.onBackground()
        super.onPause()
    }

    private fun useResource(entry: ResourceEntry) {
        when (entry.category.type) {
            "pet" -> startActivity<PetManagerActivity> {
                putExtra(PetManagerActivity.PLUGIN_ID, entry.item.payload.pluginId)
            }
            "theme" -> startActivity<ConfigActivity> { putExtra("configTag", ConfigTag.THEME_MANAGER) }
            HOME_WIDGET_SKIN_TYPE -> lifecycleScope.launch {
                if (!AppConfig.showHome) {
                    toastOnUi("请先在界面布局中开启首页")
                    return@launch
                }
                try {
                    withContext(Dispatchers.IO) { HomeWidgetSkinStore.refresh() }
                    if (HomeWidgetSkinStore.installed.value.none { it.id == entry.item.id }) {
                        toastOnUi("组件皮肤不可用，请重新导入或下载")
                        return@launch
                    }
                    startActivity<MainActivity> {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra(HOME_WIDGET_SKIN_REQUEST, entry.item.id)
                    }
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (error: Exception) { toastOnUi(error.message ?: "无法打开组件皮肤") }
            }
            "tts" -> startActivity<ConfigActivity> {
                putExtra("configTag", ConfigTag.TTS_ENGINE_CONFIG)
                putExtra("resourceEngineId", ResourceRepository.state.value.installed(entry.item.id)?.target)
            }
            "background", "read_preset" -> lifecycleScope.launch {
                val libraryOwner = intent.getStringExtra(BACKGROUND_LIBRARY_OWNER)
                if (entry.category.type == "background" && libraryOwner != null && ReadBook.book?.bookUrl == libraryOwner) {
                    // The original preset editor is still alive under this Activity.
                    finish()
                    return@launch
                }
                val book = ReadBook.book ?: withContext(Dispatchers.IO) { appDb.bookDao.lastReadBook }
                if (book == null) {
                    toastOnUi(if (entry.category.type == "read_preset") "请先打开一本书，再在阅读样式中选择预设" else "请先打开一本书，再在阅读样式中选择已下载背景")
                } else {
                    startActivity<ReadBookActivity> {
                        putExtra("bookUrl", book.bookUrl)
                        ResourceReaderStyleRequest(book.bookUrl).writeTo(this)
                    }
                }
            }
        }
    }
}

