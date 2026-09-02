package io.legado.app.ui.main.my

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import io.legado.app.lib.theme.transparentNavBar
import io.legado.app.ui.about.AboutActivity
import io.legado.app.ui.about.ReadRecordActivity
import io.legado.app.ui.book.bookmark.AllBookmarkActivity
import io.legado.app.ui.book.source.manage.BookSourceActivity
import io.legado.app.ui.config.ConfigActivity
import io.legado.app.ui.config.ConfigTag
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.file.FileManageActivity
import io.legado.app.ui.main.MainActivity
import io.legado.app.ui.main.MainFragmentInterface
import io.legado.app.utils.LogUtils
import io.legado.app.utils.startActivity

/** “我的”根页使用单一 Compose 页面，导航与业务状态继续由 Fragment 持有。 */
class MyFragment() : Fragment(), MainFragmentInterface,
    SharedPreferences.OnSharedPreferenceChangeListener {

    constructor(position: Int) : this() {
        arguments = Bundle().apply { putInt("position", position) }
    }

    override val position: Int? get() = arguments?.getInt("position")

    private lateinit var composeView: ComposeView
    private var bottomInsetPx by mutableIntStateOf(0)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        composeView = ComposeView(requireContext()).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
            )
            setContent {
                NgAppTheme {
                    MyScreen(
                        bottomInsetPx = bottomInsetPx,
                        transparentTopBar = requireContext().transparentNavBar,
                        onAction = ::handleAction,
                    )
                }
            }
        }
        return composeView
    }

    override fun onResume() {
        super.onResume()
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .registerOnSharedPreferenceChangeListener(this)
        (activity as? MainActivity)?.resolveFloatingBottomContentInset {
            bottomInsetPx = it
        }
    }

    override fun onPause() {
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .unregisterOnSharedPreferenceChangeListener(this)
        if (this::composeView.isInitialized) composeView.clearFocus()
        super.onPause()
    }

    override fun onSharedPreferenceChanged(
        sharedPreferences: SharedPreferences?,
        key: String?,
    ) {
        if (key == "recordLog") LogUtils.upLevel()
    }

    private fun handleAction(action: MyMenuAction) {
        when (action) {
            MyMenuAction.BOOK_SOURCE -> startActivity<BookSourceActivity>()
            MyMenuAction.RULE -> startActivity<ConfigActivity> {
                putExtra("configTag", ConfigTag.RULE_CONFIG)
            }
            MyMenuAction.AI -> startActivity<ConfigActivity> {
                putExtra("configTag", ConfigTag.AI_CONFIG)
            }
            MyMenuAction.READ_ALOUD -> startActivity<ConfigActivity> {
                putExtra("configTag", ConfigTag.READ_ALOUD_CONFIG)
            }
            MyMenuAction.SERVICE -> startActivity<ConfigActivity> {
                putExtra("configTag", ConfigTag.SERVICE_CONFIG)
            }
            MyMenuAction.BACKUP -> startActivity<ConfigActivity> {
                putExtra("configTag", ConfigTag.BACKUP_CONFIG)
            }
            MyMenuAction.SETTINGS -> startActivity<ConfigActivity> {
                putExtra("configTag", ConfigTag.SETTINGS_CONFIG)
            }
            MyMenuAction.BOOKMARK -> startActivity<AllBookmarkActivity>()
            MyMenuAction.READ_RECORD -> startActivity<ReadRecordActivity>()
            MyMenuAction.FILE_MANAGE -> startActivity<FileManageActivity>()
            MyMenuAction.ABOUT -> startActivity<AboutActivity>()
        }
    }
}
