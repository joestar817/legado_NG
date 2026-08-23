package io.legado.app.ui.config

import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.fragment.app.Fragment
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.EventBus
import io.legado.app.databinding.ActivityConfigBinding
import io.legado.app.utils.observeEvent
import io.legado.app.utils.viewbindingdelegate.viewBinding

interface ConfigBackHandler {
    fun onConfigBackPressed(): Boolean
}

class ConfigActivity : VMBaseActivity<ActivityConfigBinding, ConfigViewModel>() {

    override val binding by viewBinding(ActivityConfigBinding::inflate)
    override val viewModel by viewModels<ConfigViewModel>()

    override fun onActivityCreated(savedInstanceState: Bundle?) {
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
            ConfigTag.GENERAL_CONFIG,
            ConfigTag.STORAGE_CONFIG,
            ConfigTag.ADVANCED_CONFIG -> replaceFragment<OtherConfigFragment>(configTag)
            ConfigTag.OTHER_CONFIG -> replaceFragment<OtherConfigFragment>(configTag)
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
    }

    override fun setTitle(title: CharSequence?) {
        super.setTitle(title)
        binding.titleBar.title = title
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
