package io.legado.app.ui.main.my

import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.Preference
import io.legado.app.R
import io.legado.app.base.BaseFragment
import io.legado.app.constant.PreferKey
import io.legado.app.constant.Theme
import io.legado.app.databinding.FragmentMyConfigBinding
import io.legado.app.help.config.ThemeConfig
import io.legado.app.lib.prefs.NameListPreference
import io.legado.app.lib.prefs.fragment.PreferenceFragment
import io.legado.app.lib.theme.primaryColor
import io.legado.app.lib.theme.transparentNavBar
import io.legado.app.ui.about.AboutActivity
import io.legado.app.ui.about.ReadRecordActivity
import io.legado.app.ui.book.bookmark.AllBookmarkActivity
import io.legado.app.ui.book.source.manage.BookSourceActivity
import io.legado.app.ui.book.toc.rule.TxtTocRuleActivity
import io.legado.app.ui.config.ConfigActivity
import io.legado.app.ui.config.ConfigTag
import io.legado.app.ui.dict.rule.DictRuleActivity
import io.legado.app.ui.file.FileManageActivity
import io.legado.app.ui.main.MainFragmentInterface
import io.legado.app.ui.replace.ReplaceRuleActivity
import io.legado.app.utils.LogUtils
import io.legado.app.utils.applyTint
import io.legado.app.utils.getCompatColor
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.removePref
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.showHelp
import io.legado.app.utils.startActivity
import io.legado.app.utils.viewbindingdelegate.viewBinding

class MyFragment() : BaseFragment(R.layout.fragment_my_config), MainFragmentInterface {

    constructor(position: Int) : this() {
        val bundle = Bundle()
        bundle.putInt("position", position)
        arguments = bundle
    }

    override val position: Int? get() = arguments?.getInt("position")

    private val binding by viewBinding(FragmentMyConfigBinding::bind)

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        setSupportToolbar(binding.titleBar.toolbar)
        applyTransparentModeUi()
        val fragmentTag = "prefFragment"
        var preferenceFragment = childFragmentManager.findFragmentByTag(fragmentTag)
        if (preferenceFragment == null) preferenceFragment = MyPreferenceFragment()
        childFragmentManager.beginTransaction()
            .replace(R.id.pre_fragment, preferenceFragment, fragmentTag).commit()
    }

    override fun onCompatCreateOptionsMenu(menu: Menu) {
        menuInflater.inflate(R.menu.main_my, menu)
        if (requireContext().transparentNavBar) {
            menu.applyTint(requireContext(), Theme.Light)
        }
    }

    override fun onCompatOptionsItemSelected(item: MenuItem) {
        when (item.itemId) {
            R.id.menu_help -> showHelp("appHelp")
        }
    }

    private fun applyTransparentModeUi() {
        if (requireContext().transparentNavBar) {
            binding.titleBar.setTitleTextColor(requireContext().getCompatColor(R.color.primaryText))
        }
        binding.preFragment.setBackgroundResource(R.color.transparent)
    }

    /**
     * 配置
     */
    class MyPreferenceFragment : PreferenceFragment(),
        SharedPreferences.OnSharedPreferenceChangeListener {

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            addPreferencesFromResource(R.xml.pref_main)
            preferenceScreen?.let(::applyMyMenuLayout)
            findPreference<NameListPreference>(PreferKey.themeMode)?.let {
                it.setOnPreferenceChangeListener { _, newValue ->
                    view?.post {
                        val themeMode = newValue as? String
                        ThemeConfig.applyThemeMode(requireContext(), themeMode ?: "4")
                    }
                    true
                }
            }
        }

        private fun applyMyMenuLayout(group: PreferenceGroup) {
            for (index in 0 until group.preferenceCount) {
                val preference = group.getPreference(index)
                preference.layoutResource = if (preference is PreferenceCategory) {
                    R.layout.view_my_preference_category
                } else {
                    R.layout.view_my_preference
                }
                if (preference is PreferenceGroup) {
                    applyMyMenuLayout(preference)
                }
            }
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            view.setBackgroundColor(Color.TRANSPARENT)
            listView.setBackgroundColor(Color.TRANSPARENT)
            listView.setPadding(
                0,
                resources.getDimensionPixelSize(R.dimen.ng_space_l),
                0,
                listView.paddingBottom
            )
            listView.setEdgeEffectColor(primaryColor)
        }

        override fun onResume() {
            super.onResume()
            preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
        }

        override fun onPause() {
            preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
            super.onPause()
        }

        override fun onSharedPreferenceChanged(
            sharedPreferences: SharedPreferences?,
            key: String?
        ) {
            when (key) {
                "recordLog" -> LogUtils.upLevel()
            }
        }

        override fun onPreferenceTreeClick(preference: Preference): Boolean {
            when (preference.key) {
                "bookSourceManage" -> startActivity<BookSourceActivity>()
                "replaceManage" -> startActivity<ReplaceRuleActivity>()
                "dictRuleManage" -> startActivity<DictRuleActivity>()
                "txtTocRuleManage" -> startActivity<TxtTocRuleActivity>()
                "bookmark" -> startActivity<AllBookmarkActivity>()
                "setting" -> startActivity<ConfigActivity> {
                    putExtra("configTag", ConfigTag.OTHER_CONFIG)
                }

                "web_dav_setting" -> startActivity<ConfigActivity> {
                    putExtra("configTag", ConfigTag.BACKUP_CONFIG)
                }

                "theme_setting" -> startActivity<ConfigActivity> {
                    putExtra("configTag", ConfigTag.THEME_CONFIG)
                }

                "fileManage" -> startActivity<FileManageActivity>()
                "readRecord" -> startActivity<ReadRecordActivity>()
                "aiConfig" -> startActivity<ConfigActivity> {
                    putExtra("configTag", ConfigTag.AI_CONFIG)
                }
                "ttsEngineConfig" -> startActivity<ConfigActivity> {
                    putExtra("configTag", ConfigTag.TTS_ENGINE_CONFIG)
                }
                "serviceManage" -> startActivity<ConfigActivity> {
                    putExtra("configTag", ConfigTag.SERVICE_CONFIG)
                }
                "about" -> startActivity<AboutActivity>()
                "exit" -> activity?.finish()
            }
            return super.onPreferenceTreeClick(preference)
        }


    }
}
