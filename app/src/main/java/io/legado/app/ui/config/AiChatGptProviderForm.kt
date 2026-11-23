package io.legado.app.ui.config

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.help.ai.AiChatGptAccount
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.compose.NgFormActionButton
import io.legado.app.ui.design.components.compose.NgFormActionButtonAppearance
import io.legado.app.ui.design.components.compose.NgFormFieldGroup
import io.legado.app.ui.design.components.compose.NgFormSelectMenuVariant
import io.legado.app.ui.design.components.compose.NgFormSelectOption
import io.legado.app.ui.design.components.compose.NgFormSelectRow
import io.legado.app.ui.design.components.compose.NgFormSwitchGroup
import io.legado.app.ui.design.components.compose.NgFormSwitchRow
import io.legado.app.ui.design.components.compose.NgFormSwitchRowVariant
import io.legado.app.ui.design.theme.NgTheme

@Immutable
internal data class AiChatGptProviderUiState(
    val currentAccount: NgFormSelectOption? = null,
    val accounts: List<NgFormSelectOption> = emptyList(),
    val hasRegisteredAccounts: Boolean = false,
    val planAuthorized: Boolean = false,
    val needsReauthorization: Boolean = false,
    val signingIn: Boolean = false,
    val signingOut: Boolean = false,
    @param:StringRes val noticeRes: Int? = null,
) {
    val signedIn: Boolean get() = currentAccount != null && !needsReauthorization
    val canSwitchAccounts: Boolean get() = accounts.any { it.value != currentAccount?.value }
    @get:StringRes
    val authorizationActionRes: Int?
        get() = when {
            needsReauthorization -> R.string.ai_chatgpt_reauthorize
            !signedIn -> R.string.ai_chatgpt_sign_in
            !planAuthorized -> R.string.ai_chatgpt_authorize_plan
            else -> null
        }

    companion object {
        fun fromAccounts(
            registeredAccounts: List<AiChatGptAccount>,
            registeredAccountId: String?,
            defaultLabel: String,
            nowSeconds: Long = System.currentTimeMillis() / 1000,
        ): AiChatGptProviderUiState {
            val selected = registeredAccounts.firstOrNull { it.clientId == registeredAccountId }
            val needsReauthorization = selected?.needsReauthorization(nowSeconds) == true
            val current = selected?.takeIf { it.tokens != null || needsReauthorization }
            val labels = registeredAccounts.map { it.email ?: defaultLabel }
            val counts = labels.groupingBy { it }.eachCount()
            val seen = mutableMapOf<String, Int>()
            val options = registeredAccounts.mapIndexed { index, account ->
                val label = labels[index]
                val ordinal = (seen[label] ?: 0) + 1
                seen[label] = ordinal
                val suffix = if (counts.getValue(label) > 1) " ($ordinal)" else ""
                NgFormSelectOption(label + suffix, account.clientId)
            }
            return AiChatGptProviderUiState(
                currentAccount = options.firstOrNull { it.value == current?.clientId },
                accounts = if (current != null) options else options.filterNot { it.value == registeredAccountId },
                hasRegisteredAccounts = registeredAccounts.isNotEmpty(),
                planAuthorized = !needsReauthorization && current?.tokens?.allowsPlan == true,
                needsReauthorization = needsReauthorization,
            )
        }
    }
}

@Composable
internal fun AiChatGptProviderForm(
    state: AiChatGptProviderUiState,
    providerEnabled: Boolean,
    onAction: (AiProviderDetailAction) -> Unit,
) {
    val busy = state.signingIn || state.signingOut
    val noticeRes = state.noticeRes?.takeUnless { it == R.string.ai_chatgpt_reauthorize_required } ?: when {
        state.signingIn -> R.string.ai_chatgpt_wait_for_browser
        state.signingOut -> R.string.ai_chatgpt_signing_out
        state.needsReauthorization -> R.string.ai_chatgpt_session_expired
        !state.signedIn -> R.string.ai_chatgpt_signed_out
        !state.planAuthorized -> R.string.ai_chatgpt_plan_permission_required
        else -> null
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        NgFormFieldGroup {
            val currentAccount = state.currentAccount
            when {
                state.canSwitchAccounts -> NgFormSelectRow(
                    title = stringResource(R.string.ai_chatgpt_current_account),
                    selectedValue = currentAccount?.value ?: stringResource(R.string.ai_chatgpt_not_signed_in),
                    options = state.accounts,
                    onValueChange = { onAction(AiProviderDetailAction.ChatGptSelectAccount(it)) },
                    arrowIcon = painterResource(R.drawable.ic_ng_spinner_arrow_down),
                    enabled = !busy,
                    menuVariant = NgFormSelectMenuVariant.END_ANCHORED_CONTENT,
                    contentStartPadding = 0.dp,
                )
                currentAccount != null -> ChatGptAccountRow(currentAccount.label)
                else -> Text(
                    text = stringResource(R.string.ai_chatgpt_not_signed_in),
                    modifier = Modifier.padding(top = 4.dp),
                    color = Color(NgTheme.colors.onSurface),
                    fontSize = 17.sp,
                    lineHeight = 22.sp,
                )
            }
            noticeRes?.let {
                Text(
                    text = stringResource(it),
                    modifier = Modifier.padding(end = 4.dp),
                    color = Color(if (it == R.string.ai_chatgpt_session_expired) {
                        NgTheme.colors.error
                    } else NgTheme.colors.onSurfaceVariant),
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                )
            }
            ChatGptAccountActions(state, onAction)
        }
        NgFormSwitchGroup {
            NgFormSwitchRow(
                title = stringResource(R.string.ai_enabled),
                checked = providerEnabled,
                enabled = !busy,
                onCheckedChange = { onAction(AiProviderDetailAction.ChatGptEnabled(it)) },
                variant = NgFormSwitchRowVariant.GROUPED,
            )
        }
    }
}

@Composable
private fun ChatGptAccountRow(accountLabel: String) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
            .padding(end = 10.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.ai_chatgpt_current_account),
            modifier = Modifier.widthIn(max = 120.dp),
            color = Color(NgTheme.colors.onSurface),
            fontSize = 14.sp,
            lineHeight = 18.sp,
        )
        Text(
            text = accountLabel,
            modifier = Modifier.weight(1f),
            color = Color(NgTheme.colors.onSurfaceVariant),
            textAlign = TextAlign.End,
            fontSize = 13.sp,
            lineHeight = 16.sp,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatGptAccountActions(
    state: AiChatGptProviderUiState,
    onAction: (AiProviderDetailAction) -> Unit,
) {
    if (state.signingIn) {
        NgFormActionButton(
            text = stringResource(android.R.string.cancel),
            onClick = { onAction(AiProviderDetailAction.ChatGptCancelSignIn) },
            appearance = NgFormActionButtonAppearance.SURFACE_CARD_BORDERLESS,
        )
        return
    }

    val enabled = !state.signingOut
    val hasCurrentAccount = state.currentAccount != null
    val authorizationActionRes = state.authorizationActionRes
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = if (hasCurrentAccount) Arrangement.SpaceBetween else Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (!hasCurrentAccount && authorizationActionRes != null) {
            NgFormActionButton(
                text = stringResource(authorizationActionRes),
                onClick = { onAction(AiProviderDetailAction.ChatGptSignIn(addAccount = false)) },
                enabled = enabled,
                variant = NgButtonVariant.PRIMARY,
            )
        }
        if (state.hasRegisteredAccounts) {
            NgFormActionButton(
                text = stringResource(
                    if (hasCurrentAccount) R.string.ai_chatgpt_add_account else R.string.ai_chatgpt_use_another_account
                ),
                onClick = { onAction(AiProviderDetailAction.ChatGptSignIn(addAccount = true)) },
                enabled = enabled,
                appearance = NgFormActionButtonAppearance.TEXT,
                minimumWidth = 0.dp,
                contentPadding = if (hasCurrentAccount) PaddingValues(end = 16.dp) else PaddingValues(horizontal = 8.dp),
            )
        }
        if (hasCurrentAccount && authorizationActionRes != null) {
            NgFormActionButton(
                text = stringResource(authorizationActionRes),
                onClick = { onAction(AiProviderDetailAction.ChatGptSignIn(addAccount = false)) },
                enabled = enabled,
                appearance = NgFormActionButtonAppearance.TEXT,
                minimumWidth = 0.dp,
            )
        }
        if (hasCurrentAccount || state.noticeRes == R.string.ai_chatgpt_revoke_unconfirmed) {
            NgFormActionButton(
                text = stringResource(R.string.ai_chatgpt_view_usage),
                onClick = { onAction(AiProviderDetailAction.ChatGptOpenUsage) },
                appearance = NgFormActionButtonAppearance.TEXT,
                minimumWidth = 0.dp,
                trailingIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                },
            )
        }
        if (hasCurrentAccount) {
            NgFormActionButton(
                text = stringResource(R.string.ai_chatgpt_sign_out),
                onClick = { onAction(AiProviderDetailAction.ChatGptSignOut) },
                enabled = enabled,
                variant = NgButtonVariant.DANGER,
                appearance = NgFormActionButtonAppearance.TEXT,
                minimumWidth = 0.dp,
            )
        }
    }
}
