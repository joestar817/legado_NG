package io.legado.app.ui.main.my

import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.Preference
import io.legado.app.R
import io.legado.app.base.BaseFragment
import io.legado.app.databinding.FragmentMyConfigBinding
import io.legado.app.lib.prefs.fragment.PreferenceFragment
import io.legado.app.lib.theme.primaryColor
import io.legado.app.lib.theme.transparentNavBar
import io.legado.app.ui.about.AboutActivity
import io.legado.app.ui.about.ReadRecordActivity
import io.legado.app.ui.book.bookmark.AllBookmarkActivity
import io.legado.app.ui.book.source.manage.BookSourceActivity
import io.legado.app.ui.config.ConfigActivity
import io.legado.app.ui.config.ConfigTag
import io.legado.app.ui.design.theme.NgThemeResolver
import io.legado.app.ui.file.FileManageActivity
import io.legado.app.ui.main.MainActivity
import io.legado.app.ui.main.MainFragmentInterface
import io.legado.app.utils.LogUtils
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.removePref
import io.legado.app.utils.setEdgeEffectColor
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

    private fun applyTransparentModeUi() {
        if (requireContext().transparentNavBar) {
            binding.titleBar.setTitleTextColor(
                NgThemeResolver.resolve(requireContext()).colors.onTopBar
            )
        }
        binding.preFragment.setBackgroundResource(R.color.transparent)
    }

    /**
     * 配置
     */
    class MyPreferenceFragment : PreferenceFragment(),
        SharedPreferences.OnSharedPreferenceChangeListener {

        private var listBaseBottomPadding = 0

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            addPreferencesFromResource(R.xml.pref_main)
            preferenceScreen?.let(::applyMyMenuLayout)
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
            listBaseBottomPadding = listView.paddingBottom
            listView.setPadding(
                0,
                resources.getDimensionPixelSize(R.dimen.ng_space_l),
                0,
                listBaseBottomPadding
            )
            listView.clipToPadding = false
            listView.setEdgeEffectColor(primaryColor)
            updateFloatingBottomInset()
        }

        override fun onResume() {
            super.onResume()
            updateFloatingBottomInset()
            preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
        }

        private fun updateFloatingBottomInset() {
            (activity as? MainActivity)?.applyFloatingBottomContentInset(
                target = listView,
                baseBottomPadding = listBaseBottomPadding
            )
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
                "ruleManage" -> startActivity<ConfigActivity> {
                    putExtra("configTag", ConfigTag.RULE_CONFIG)
                }
                "bookmark" -> startActivity<AllBookmarkActivity>()
                "setting" -> startActivity<ConfigActivity> {
                    putExtra("configTag", ConfigTag.SETTINGS_CONFIG)
                }

                "web_dav_setting" -> startActivity<ConfigActivity> {
                    putExtra("configTag", ConfigTag.BACKUP_CONFIG)
                }

                "fileManage" -> startActivity<FileManageActivity>()
                "readRecord" -> startActivity<ReadRecordActivity>()
                "aiConfig" -> startActivity<ConfigActivity> {
                    putExtra("configTag", ConfigTag.AI_CONFIG)
                }
                "readAloudConfig" -> startActivity<ConfigActivity> {
                    putExtra("configTag", ConfigTag.READ_ALOUD_CONFIG)
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
