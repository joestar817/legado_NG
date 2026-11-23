package io.legado.app.ui.config

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.design.components.NgManagementListCardVariant
import io.legado.app.ui.design.components.NgStatusTagSpec
import io.legado.app.ui.design.components.NgStatusTagStyle
import io.legado.app.ui.design.components.NgStatusTagVariant
import io.legado.app.ui.design.components.compose.NgManagementLeadingIcon
import io.legado.app.ui.design.components.compose.NgManagementListCard

internal val aiServiceListPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 24.dp)

@Composable
internal fun AiServiceListCard(
    title: String,
    host: String,
    @DrawableRes iconRes: Int,
    enabled: Boolean,
    keyConfigured: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    infoTag: NgStatusTagSpec? = null,
    capabilityTags: List<NgStatusTagSpec> = emptyList(),
    trailingContent: (@Composable () -> Unit)? = null,
    @StringRes missingCredentialRes: Int = R.string.ai_provider_no_api_key,
) {
    val statusTags = buildList {
        add(NgStatusTagSpec(
            text = stringResource(if (enabled) R.string.enabled else R.string.disabled),
            variant = if (enabled) NgStatusTagVariant.SUCCESS else NgStatusTagVariant.WARNING,
            style = NgStatusTagStyle.COMPACT,
        ))
        if (!keyConfigured) {
            add(NgStatusTagSpec(
                text = stringResource(missingCredentialRes),
                variant = NgStatusTagVariant.WARNING,
                style = NgStatusTagStyle.COMPACT,
            ))
        } else if (infoTag != null) {
            add(infoTag)
        }
    }
    val hasHost = host.isNotBlank()
    val hasDetails = hasHost || capabilityTags.isNotEmpty()
    NgManagementListCard(
        title = title,
        modifier = modifier,
        variant = NgManagementListCardVariant.PROVIDER_TWO_ROW,
        summaryTag = if (hasHost) NgStatusTagSpec(host, NgStatusTagVariant.INFO, NgStatusTagStyle.COMPACT) else null,
        headerTags = if (hasDetails) statusTags else emptyList(),
        detailTags = if (hasDetails) capabilityTags else statusTags,
        containerColor = colorResource(R.color.ng_translucent_management_card_surface),
        borderColor = colorResource(R.color.ng_translucent_management_card_stroke),
        borderWidth = 0.6.dp,
        leading = {
            NgManagementLeadingIcon(
                iconRes = iconRes,
                contentDescription = null,
                tint = Color.Unspecified,
                containerColor = colorResource(R.color.ng_translucent_management_avatar_surface),
            )
        },
        trailingContent = trailingContent,
        onClick = onClick,
    )
}
