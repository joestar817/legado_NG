package io.legado.app.ui.config

import android.os.Bundle
import android.view.View
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import io.legado.app.R
import io.legado.app.base.BaseFragment
import io.legado.app.ui.book.toc.rule.TxtTocRuleActivity
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.dict.rule.DictRuleActivity
import io.legado.app.ui.replace.ReplaceRuleActivity
import io.legado.app.utils.startActivity

class RuleConfigMenuFragment : BaseFragment(R.layout.fragment_rule_config_menu) {

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        activity?.setTitle(R.string.rule_management)
        (view as ComposeView).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                NgAppTheme {
                    RuleConfigMenuScreen(
                        onOpenTxtTocRules = { startActivity<TxtTocRuleActivity>() },
                        onOpenReplaceRules = { startActivity<ReplaceRuleActivity>() },
                        onOpenDictRules = { startActivity<DictRuleActivity>() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.setTitle(R.string.rule_management)
    }
}
