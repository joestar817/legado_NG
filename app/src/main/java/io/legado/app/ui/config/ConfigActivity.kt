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
        when (val configTag = intent.getStringExtra("configTag")) {
            ConfigTag.OTHER_CONFIG -> replaceFragment<OtherConfigFragment>(configTag)
            ConfigTag.SERVICE_CONFIG -> replaceFragment<ServiceConfigFragment>(configTag)
            ConfigTag.AI_CONFIG -> replaceFragment<AiConfigFragment>(configTag)
            ConfigTag.THEME_CONFIG -> replaceFragment<ThemeConfigFragment>(configTag)
            ConfigTag.BACKUP_CONFIG -> replaceFragment<BackupConfigFragment>(configTag)
            ConfigTag.COVER_CONFIG -> replaceFragment<CoverConfigFragment>(configTag)
            ConfigTag.WELCOME_CONFIG -> replaceFragment<WelcomeConfigFragment>(configTag)
            else -> finish()
        }
    }

    private fun handleConfigBackPressed() {
        val configTag = intent.getStringExtra("configTag")
        val fragment = configTag?.let { supportFragmentManager.findFragmentByTag(it) }
        if ((fragment as? ConfigBackHandler)?.onConfigBackPressed() == true) {
            return
        }
        finish()
    }

    override fun setTitle(resId: Int) {
        super.setTitle(resId)
        binding.titleBar.setTitle(resId)
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
