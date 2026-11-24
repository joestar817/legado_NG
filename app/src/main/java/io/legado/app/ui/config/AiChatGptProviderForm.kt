package io.legado.app.ui.config

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.ConfigurationCompat
import io.legado.app.R
import io.legado.app.help.ai.AiChatGptAccount
import io.legado.app.help.ai.AiCodexUsage
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
import io.legado.app.ui.design.components.compose.NgIconButton
import io.legado.app.ui.design.theme.NgTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val codex: Boolean = false,
    val codexUsage: AiCodexUsage? = null,
    val usageLoading: Boolean = false,
) {
    val signedIn: Boolean get() = currentAccount != null && !needsReauthorization
    val canSwitchAccounts: Boolean get() = accounts.any { it.value != currentAccount?.value }
    @get:StringRes
    val authorizationActionRes: Int?
        get() = when {
            needsReauthorization -> R.string.ai_chatgpt_reauthorize
            !signedIn -> if (codex) R.string.ai_codex_sign_in else R.string.ai_chatgpt_sign_in
            !planAuthorized -> R.string.ai_chatgpt_authorize_plan
            else -> null
        }

    companion object {
        fun fromAccounts(
            registeredAccounts: List<AiChatGptAccount>,
            registeredAccountId: String?,
            defaultLabel: String,
            nowSeconds: Long = System.currentTimeMillis() / 1000,
            codex: Boolean = false,
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
                planAuthorized = !needsReauthorization && if (codex) {
                    current?.tokens != null && !current.codexWorkspaceId.isNullOrBlank()
                } else current?.tokens?.allowsPlan == true,
                needsReauthorization = needsReauthorization,
                codex = codex,
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
        !state.signedIn -> if (state.codex) R.string.ai_codex_signed_out else R.string.ai_chatgpt_signed_out
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
        if (state.codex && state.signedIn && state.planAuthorized && !state.signingOut) {
            CodexUsageCard(state, onRefresh = { onAction(AiProviderDetailAction.ChatGptOpenUsage) })
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
        if (!state.codex && (hasCurrentAccount || state.noticeRes == R.string.ai_chatgpt_revoke_unconfirmed)) {
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

@Composable
private fun CodexUsageCard(state: AiChatGptProviderUiState, onRefresh: () -> Unit) {
    val accent = Color(NgTheme.colors.primary)
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()
    NgFormFieldGroup {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.ai_codex_usage_title),
                modifier = Modifier.weight(1f),
                color = Color(NgTheme.colors.onSurface),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            NgIconButton(
                onClick = onRefresh,
                modifier = Modifier.size(38.dp),
                enabled = !state.usageLoading,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_refresh_black_24dp),
                    contentDescription = stringResource(R.string.refresh),
                    modifier = Modifier.size(24.dp),
                    tint = if (state.usageLoading) accent.copy(alpha = 0.38f) else accent,
                )
            }
        }
        val usage = state.codexUsage
        if (usage == null) {
            CodexUsageLine(stringResource(if (state.usageLoading) R.string.loading else R.string.ai_codex_usage_unavailable))
        } else {
            val plan = usage.planDisplayName
            val credits = if (usage.unlimitedCredits) {
                stringResource(R.string.ai_codex_usage_credits_unlimited)
            } else {
                remember(usage.creditsBalance, locale) { usage.formatCredits(locale) }
            }
            if (plan != null) {
                val label = stringResource(R.string.ai_codex_usage_plan)
                Text(
                    text = buildAnnotatedString {
                        append(label)
                        append(" ")
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(plan) }
                    },
                    color = Color(NgTheme.colors.onSurfaceVariant),
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                )
                if (usage.windows.isNotEmpty()) CodexUsageDivider()
            }
            usage.windows.forEach { window -> CodexUsageWindow(window, locale, accent) }
            if (credits != null) {
                if (plan != null || usage.windows.isNotEmpty()) CodexUsageDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.ai_codex_usage_credits),
                        modifier = Modifier.weight(1f),
                        color = Color(NgTheme.colors.onSurface),
                        fontSize = 14.sp,
                    )
                    Text(
                        text = credits,
                        color = Color(NgTheme.colors.onSurface),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.End,
                    )
                }
            }
            if (plan == null && usage.windows.isEmpty() && credits == null) {
                CodexUsageLine(stringResource(R.string.ai_codex_usage_unavailable))
            }
        }
    }
}

@Composable
private fun CodexUsageWindow(window: AiCodexUsage.Window, locale: Locale, accent: Color) {
    val seconds = window.durationSeconds
    val period = when {
        seconds == 604800 -> stringResource(R.string.ai_codex_usage_weekly)
        seconds != null && seconds % 3600 == 0 -> stringResource(R.string.ai_codex_usage_hours, seconds / 3600)
        else -> stringResource(R.string.ai_codex_usage_limit)
    }
    val datePattern = stringResource(R.string.ai_codex_usage_reset_time_pattern)
    val resetTime = remember(window.resetsAt, datePattern, locale) {
        window.resetsAt?.let { SimpleDateFormat(datePattern, locale).format(Date(it * 1000)) }
    }
    val fraction = window.remainingPercent / 100f
    val pill = RoundedCornerShape(percent = 50)
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = period,
            color = Color(NgTheme.colors.onSurface),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            if (resetTime != null) {
                CodexUsageLine(stringResource(R.string.ai_codex_usage_reset, resetTime), Modifier.weight(1f))
            } else {
                Spacer(Modifier.weight(1f))
            }
            CodexUsageLine(stringResource(R.string.ai_codex_usage_remaining, window.remainingPercent))
        }
        Box(
            modifier = Modifier.fillMaxWidth()
                .height(6.dp)
                .progressSemantics(fraction)
                .clip(pill)
                .background(accent.copy(alpha = 0.30f)),
        ) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(accent, pill))
        }
    }
}

@Composable
private fun CodexUsageDivider() {
    HorizontalDivider(color = Color(NgTheme.colors.outlineVariant).copy(alpha = 0.38f))
}

@Composable
private fun CodexUsageLine(text: String, modifier: Modifier = Modifier) {
    Text(text = text, modifier = modifier, color = Color(NgTheme.colors.onSurfaceVariant), fontSize = 13.sp, lineHeight = 18.sp)
}
