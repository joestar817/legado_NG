package io.legado.app.ui.config

import android.os.Bundle
import android.view.View
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import io.legado.app.R
import io.legado.app.base.BaseFragment
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.utils.startActivity

class SettingsMenuFragment : BaseFragment(R.layout.fragment_settings_menu) {

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        activity?.setTitle(R.string.setting)
        (view as ComposeView).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                NgAppTheme {
                    SettingsMenuScreen(onOpenPage = ::openPage)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.setTitle(R.string.setting)
    }

    private fun openPage(configTag: String) {
        startActivity<ConfigActivity> {
            putExtra("configTag", configTag)
        }
    }
}
