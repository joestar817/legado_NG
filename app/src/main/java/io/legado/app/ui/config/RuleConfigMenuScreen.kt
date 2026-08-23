package io.legado.app.ui.config

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.design.components.compose.NgSettingsGroup
import io.legado.app.ui.design.components.compose.NgSettingsIcon
import io.legado.app.ui.design.components.compose.NgSettingsItem

@Composable
internal fun RuleConfigMenuScreen(
    onOpenTxtTocRules: () -> Unit,
    onOpenReplaceRules: () -> Unit,
    onOpenDictRules: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        NgSettingsGroup {
            RuleConfigMenuEntry(
                title = stringResource(R.string.txt_toc_rule),
                summary = stringResource(R.string.config_txt_toc_rule),
                iconRes = R.drawable.ic_cfg_source,
                onClick = onOpenTxtTocRules
            )
            RuleConfigMenuEntry(
                title = stringResource(R.string.replace_purify),
                summary = stringResource(R.string.replace_purify_desc),
                iconRes = R.drawable.ic_cfg_replace,
                onClick = onOpenReplaceRules
            )
            RuleConfigMenuEntry(
                title = stringResource(R.string.dict_rule),
                summary = stringResource(R.string.config_dict_rule),
                iconRes = R.drawable.ic_translate,
                onClick = onOpenDictRules
            )
        }
    }
}

@Composable
private fun RuleConfigMenuEntry(
    title: String,
    summary: String,
    @DrawableRes iconRes: Int,
    onClick: () -> Unit
) {
    NgSettingsItem(
        title = title,
        summary = summary,
        onClick = onClick,
        leading = {
            NgSettingsIcon(
                painter = painterResource(iconRes),
                contentDescription = null
            )
        }
    )
}
