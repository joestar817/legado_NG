package io.legado.app.ui.config

import android.os.Bundle
import android.view.View
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.EventBus
import io.legado.app.databinding.ActivityConfigBinding
import io.legado.app.ui.design.components.compose.NgFloatingTitleToolbar
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.utils.observeEvent
import io.legado.app.utils.viewbindingdelegate.viewBinding

interface ConfigBackHandler {
    fun onConfigBackPressed(): Boolean
}

class ConfigActivity : VMBaseActivity<ActivityConfigBinding, ConfigViewModel>() {

    override val binding by viewBinding(ActivityConfigBinding::inflate)
    override val viewModel by viewModels<ConfigViewModel>()

    private var floatingTitle by mutableStateOf("")

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        initFloatingTitleBar()
        binding.titleBar.setNavigationOnClickListener {
            handleConfigBackPressed()
        }
        onBackPressedDispatcher.addCallback(this) {
            handleConfigBackPressed()
        }
        if (
            savedInstanceState != null &&
            supportFragmentManager.findFragmentById(R.id.configFrameLayout) != null
        ) {
            return
        }
        when (val configTag = intent.getStringExtra("configTag")) {
            ConfigTag.SETTINGS_CONFIG -> replaceFragment<SettingsMenuFragment>(configTag)
            ConfigTag.APPEARANCE_CONFIG,
            ConfigTag.INTERFACE_CONFIG -> replaceFragment<ThemeConfigFragment>(configTag)
            ConfigTag.GENERAL_CONFIG -> replaceFragment<GeneralConfigFragment>(configTag)
            ConfigTag.STORAGE_CONFIG -> replaceFragment<StorageConfigFragment>(configTag)
            ConfigTag.ADVANCED_CONFIG -> replaceFragment<AdvancedConfigFragment>(configTag)
            ConfigTag.RULE_CONFIG -> replaceFragment<RuleConfigMenuFragment>(configTag)
            ConfigTag.SERVICE_CONFIG -> replaceFragment<ServiceConfigFragment>(configTag)
            ConfigTag.AI_CONFIG -> {
                if (intent.hasExtra(AiConfigFragment.EXTRA_INITIAL_PAGE)) {
                    replaceFragment<AiConfigFragment>(configTag)
                } else {
                    replaceFragment<AiConfigMenuFragment>(configTag)
                }
            }
            ConfigTag.READ_ALOUD_CONFIG -> replaceFragment<ReadAloudConfigFragment>(configTag)
            ConfigTag.TTS_ENGINE_CONFIG -> replaceFragment<TtsEngineConfigFragment>(configTag)
            ConfigTag.DEFAULT_TTS_VOICE_CONFIG -> replaceFragment<DefaultTtsVoiceConfigFragment>(configTag)
            ConfigTag.THEME_CONFIG -> replaceFragment<ThemeConfigFragment>(configTag)
            ConfigTag.BACKUP_CONFIG -> replaceFragment<BackupConfigFragment>(configTag)
            ConfigTag.COVER_CONFIG -> replaceFragment<CoverConfigFragment>(configTag)
            else -> finish()
        }
    }

    private fun initFloatingTitleBar() {
        floatingTitle = binding.titleBar.title?.toString().orEmpty()
        binding.floatingTitleBar.apply {
            // 只负责顶部避让，仍向下方独立的 Fragment 分发导航栏和键盘 Insets。
            consumeWindowInsets = false
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                NgAppTheme(updateSystemBars = false) {
                    NgFloatingTitleToolbar(
                        title = floatingTitle,
                        onBack = ::handleConfigBackPressed,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }
        supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentViewCreated(
                    fm: FragmentManager,
                    fragment: Fragment,
                    view: View,
                    savedInstanceState: Bundle?,
                ) {
                    refreshTitleBarChrome(fragment)
                }

                override fun onFragmentResumed(fm: FragmentManager, fragment: Fragment) {
                    refreshTitleBarChrome(fragment)
                }
            },
            false,
        )
        refreshTitleBarChrome()
    }

    /** 仅本批普通设置页使用悬浮栏；其它页面继续持有各自的顶栏显隐与菜单。 */
    internal fun refreshTitleBarChrome(
        fragment: Fragment? = supportFragmentManager.findFragmentById(R.id.configFrameLayout),
    ) {
        if (fragment !== supportFragmentManager.findFragmentById(R.id.configFrameLayout)) return
        val floating = when (fragment) {
            is ThemeConfigFragment ->
                intent.getStringExtra("configTag") == ConfigTag.APPEARANCE_CONFIG ||
                    intent.getStringExtra("configTag") == ConfigTag.INTERFACE_CONFIG
            is SettingsMenuFragment,
            is CoverConfigFragment,
            is GeneralConfigFragment,
            is StorageConfigFragment,
            is AdvancedConfigFragment,
            is RuleConfigMenuFragment,
            is ServiceConfigFragment,
            is AiConfigMenuFragment,
            is ReadAloudConfigFragment,
            is DefaultTtsVoiceConfigFragment,
            is ThemeColorConfigFragment -> true
            is AiConfigFragment -> fragment.usesFloatingSettingsTitleBar()
            else -> false
        }
        binding.floatingTitleBar.isVisible = floating
        if (floating || fragment is ThemeConfigFragment) {
            binding.titleBar.isVisible = !floating
        }
    }

    private fun handleConfigBackPressed() {
        val configTag = intent.getStringExtra("configTag")
        val fragment = supportFragmentManager.findFragmentById(R.id.configFrameLayout)
            ?: configTag?.let { supportFragmentManager.findFragmentByTag(it) }
        if ((fragment as? ConfigBackHandler)?.onConfigBackPressed() == true) {
            return
        }
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
            return
        }
        finish()
    }

    fun openAiConfigPage(page: String) {
        supportFragmentManager.beginTransaction()
            .replace(
                R.id.configFrameLayout,
                AiConfigFragment.newMenuPageInstance(page),
                "${ConfigTag.AI_CONFIG}:$page"
            )
            .addToBackStack(page)
            .commit()
    }

    fun openThemeColorConfigPage() {
        supportFragmentManager.beginTransaction()
            .replace(
                R.id.configFrameLayout,
                ThemeColorConfigFragment(),
                ConfigTag.THEME_COLOR_CONFIG
            )
            .addToBackStack(ConfigTag.THEME_COLOR_CONFIG)
            .commit()
    }

    fun openThemeManagerPage() {
        supportFragmentManager.beginTransaction()
            .replace(
                R.id.configFrameLayout,
                ThemeManagerFragment(),
                ConfigTag.THEME_MANAGER
            )
            .addToBackStack(ConfigTag.THEME_MANAGER)
            .commit()
    }

    override fun onHomeNavigationSelected() {
        handleConfigBackPressed()
    }

    override fun setTitle(resId: Int) {
        super.setTitle(resId)
        binding.titleBar.setTitle(resId)
        floatingTitle = getString(resId)
    }

    override fun setTitle(title: CharSequence?) {
        super.setTitle(title)
        binding.titleBar.title = title
        floatingTitle = title?.toString().orEmpty()
    }

    inline fun <reified T : Fragment> replaceFragment(configTag: String) {
        intent.putExtra("configTag", configTag)
        @Suppress("DEPRECATION")
        val configFragment = supportFragmentManager.findFragmentByTag(configTag)
            ?: T::class.java.newInstance()
        supportFragmentManager.beginTransaction()
            .replace(R.id.configFrameLayout, configFragment, configTag)
            .commit()
    }

    override fun observeLiveBus() {
        super.observeLiveBus()
        observeEvent<String>(EventBus.RECREATE) {
            recreate()
        }
    }

}
