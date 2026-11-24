package io.legado.app.ui.config

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.ConfigurationCompat
import io.legado.app.R
import io.legado.app.help.ai.AiChatGptAccount
import io.legado.app.help.ai.AiCodexUsage
import io.legado.app.help.ai.AiWorkBuddyCheckIn
import io.legado.app.help.ai.AiWorkBuddyCheckInStatus
import io.legado.app.help.ai.AiWorkBuddyCredits
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.compose.NgFormActionButton
import io.legado.app.ui.design.components.compose.NgFormActionButtonAppearance
import io.legado.app.ui.design.components.compose.NgFormControlGroup
import io.legado.app.ui.design.components.compose.NgFormFieldGroup
import io.legado.app.ui.design.components.compose.NgFormSelectMenuVariant
import io.legado.app.ui.design.components.compose.NgFormSelectOption
import io.legado.app.ui.design.components.compose.NgFormSelectRow
import io.legado.app.ui.design.components.compose.NgFormSwitchGroup
import io.legado.app.ui.design.components.compose.NgFormSwitchRow
import io.legado.app.ui.design.components.compose.NgFormSwitchRowVariant
import io.legado.app.ui.design.components.compose.NgIconButton
import io.legado.app.ui.design.theme.NgTheme
import java.text.NumberFormat
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
    val workBuddy: Boolean = false,
    val showWorkBuddyCredits: Boolean = false,
    val workBuddyCredits: AiWorkBuddyCredits? = null,
    val workBuddyCreditsError: String? = null,
    val workBuddyCheckIn: AiWorkBuddyCheckIn? = null,
    val workBuddyCheckInLoading: Boolean = false,
    val workBuddyClaimingCredits: Boolean = false,
    val noticeText: String? = null,
) {
    val signedIn: Boolean get() = currentAccount != null && !needsReauthorization
    val canSwitchAccounts: Boolean get() = accounts.any { it.value != currentAccount?.value }
    @get:StringRes
    val authorizationActionRes: Int?
        get() = when {
            needsReauthorization -> R.string.ai_chatgpt_reauthorize
            !signedIn -> when {
                codex -> R.string.ai_codex_sign_in
                workBuddy -> R.string.ai_workbuddy_sign_in
                else -> R.string.ai_chatgpt_sign_in
            }
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
        !state.signedIn -> when {
            state.codex -> R.string.ai_codex_signed_out
            state.workBuddy -> R.string.ai_workbuddy_signed_out
            else -> R.string.ai_chatgpt_signed_out
        }
        !state.planAuthorized -> R.string.ai_chatgpt_plan_permission_required
        else -> null
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val currentAccount = state.currentAccount
        val workBuddyAccount = currentAccount?.takeIf {
            state.showWorkBuddyCredits && state.signedIn && !state.signingOut
        }
        val notice = state.noticeText ?: noticeRes?.let { stringResource(it) }
        if (workBuddyAccount != null) {
            WorkBuddyAccountCard(state, workBuddyAccount, notice, onAction)
        } else {
            NgFormFieldGroup {
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
                    currentAccount != null -> ChatGptAccountRow(
                        accountLabel = currentAccount.label,
                        avatarRes = R.drawable.ic_workbuddy_account_avatar.takeIf { state.workBuddy },
                    )
                    else -> Text(
                        text = stringResource(R.string.ai_chatgpt_not_signed_in),
                        modifier = Modifier.padding(top = 4.dp),
                        color = Color(NgTheme.colors.onSurface),
                        fontSize = 17.sp,
                        lineHeight = 22.sp,
                    )
                }
                ChatGptAccountNotice(notice, state.needsReauthorization)
                ChatGptAccountActions(
                    state = state,
                    onAction = onAction,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
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
private fun ChatGptAccountNotice(notice: String?, isError: Boolean) {
    notice?.let {
        Text(
            text = it,
            modifier = Modifier.padding(end = 4.dp),
            color = Color(if (isError) NgTheme.colors.error else NgTheme.colors.onSurfaceVariant),
            fontSize = 14.sp,
            lineHeight = 19.sp,
        )
    }
}

@Composable
private fun ChatGptAccountRow(accountLabel: String, @DrawableRes avatarRes: Int?) {
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
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (avatarRes != null) {
                Image(
                    painter = painterResource(avatarRes),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            }
            Text(
                text = accountLabel,
                modifier = Modifier.weight(1f, fill = avatarRes == null),
                color = Color(NgTheme.colors.onSurfaceVariant),
                textAlign = TextAlign.End,
                fontSize = 13.sp,
                lineHeight = 16.sp,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatGptAccountActions(
    state: AiChatGptProviderUiState,
    onAction: (AiProviderDetailAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.signingIn) {
        NgFormActionButton(
            text = stringResource(android.R.string.cancel),
            onClick = { onAction(AiProviderDetailAction.ChatGptCancelSignIn) },
            appearance = NgFormActionButtonAppearance.SURFACE_CARD_BORDERLESS,
        )
        return
    }

    val enabled = !state.signingOut && !state.workBuddyClaimingCredits
    val hasCurrentAccount = state.currentAccount != null
    val authorizationActionRes = state.authorizationActionRes
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = when {
            state.workBuddy && state.signedIn -> Arrangement.End
            hasCurrentAccount -> Arrangement.SpaceBetween
            else -> Arrangement.spacedBy(12.dp)
        },
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
        if (!state.codex && !state.workBuddy &&
            (hasCurrentAccount || state.noticeRes == R.string.ai_chatgpt_revoke_unconfirmed)
        ) {
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
private fun WorkBuddyCheckInRow(
    state: AiChatGptProviderUiState,
    accountId: String,
    dailyCredits: String?,
    onAction: (AiProviderDetailAction) -> Unit,
) {
    val accent = Color(NgTheme.colors.primary)
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        horizontalArrangement = Arrangement.spacedBy(13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_workbuddy_gift_outline),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = accent,
        )
        Column(
            modifier = Modifier.weight(1f).padding(bottom = 3.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = stringResource(R.string.ai_workbuddy_check_in_title),
                color = Color(NgTheme.colors.onSurface),
                fontSize = 15.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Normal,
            )
            dailyCredits?.let {
                Text(
                    text = stringResource(R.string.ai_workbuddy_daily_credits, it),
                    color = accent,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
        }
        WorkBuddyCheckInAction(state, accountId, onAction)
    }
}

@Composable
private fun WorkBuddyCheckInAction(
    state: AiChatGptProviderUiState,
    accountId: String,
    onAction: (AiProviderDetailAction) -> Unit,
) {
    val status = state.workBuddyCheckIn?.status
    val checking = state.workBuddyCheckInLoading || state.workBuddyClaimingCredits
    if (!checking && status == AiWorkBuddyCheckInStatus.CLAIMED) {
        WorkBuddyClaimedStatus()
        return
    }
    val label = when {
        state.workBuddyClaimingCredits -> R.string.ai_workbuddy_credits_claiming
        state.workBuddyCheckInLoading -> R.string.loading
        status == AiWorkBuddyCheckInStatus.UNAVAILABLE -> R.string.ai_workbuddy_credits_claim_unavailable
        status == AiWorkBuddyCheckInStatus.AVAILABLE -> R.string.ai_workbuddy_credits_claim
        else -> R.string.ai_workbuddy_credits_check_again
    }
    val canClaim = !checking && status == AiWorkBuddyCheckInStatus.AVAILABLE
    NgFormActionButton(
        text = stringResource(label),
        onClick = {
            onAction(if (canClaim) {
                AiProviderDetailAction.WorkBuddyClaimCredits(accountId)
            } else {
                AiProviderDetailAction.WorkBuddyRefreshCredits(accountId)
            })
        },
        enabled = !state.usageLoading && !checking && (canClaim || status == null),
        variant = if (canClaim) NgButtonVariant.TONAL else NgButtonVariant.OUTLINE,
        appearance = if (canClaim) NgFormActionButtonAppearance.DEFAULT else NgFormActionButtonAppearance.TEXT,
        minimumWidth = 0.dp,
    )
}

@Composable
private fun WorkBuddyClaimedStatus() {
    val colors = NgTheme.colors
    val accent = Color(colors.primary)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(colors.selectedContainer).copy(alpha = 0.80f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = accent)
        Text(
            text = stringResource(R.string.ai_workbuddy_credits_claimed_today),
            color = accent,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
    }
}

@Composable
private fun WorkBuddyAccountCard(
    state: AiChatGptProviderUiState,
    account: NgFormSelectOption,
    notice: String?,
    onAction: (AiProviderDetailAction) -> Unit,
) {
    val refresh = { onAction(AiProviderDetailAction.WorkBuddyRefreshCredits(account.value)) }
    LaunchedEffect(account.value) { refresh() }
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()
    val format = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    val balance = state.workBuddyCredits?.remaining
    val amount = when {
        balance != null -> format.format(balance)
        state.workBuddyCreditsError != null -> "—"
        else -> stringResource(R.string.loading)
    }
    NgFormControlGroup(
        contentPadding = PaddingValues(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 4.dp),
    ) {
        WorkBuddyAccountOverview(account.label, amount, state.usageLoading, refresh)
        state.workBuddyCreditsError?.let { error ->
            AccountUsageLine(
                if (balance != null) stringResource(R.string.ai_workbuddy_credits_stale) else error,
            )
        }
        AccountUsageDivider(alpha = 0.28f)
        WorkBuddyCheckInRow(
            state,
            account.value,
            state.workBuddyCheckIn?.dailyCredits?.let(format::format),
            onAction,
        )
        ChatGptAccountNotice(notice, state.needsReauthorization)
        AccountUsageDivider(alpha = 0.28f)
        ChatGptAccountActions(state, onAction)
    }
}

@Composable
private fun WorkBuddyAccountOverview(
    accountLabel: String,
    amount: String,
    loading: Boolean,
    onRefresh: () -> Unit,
) {
    val colors = NgTheme.colors
    val labelColor = Color(colors.onSurface)
    Layout(
        modifier = Modifier.fillMaxWidth().padding(bottom = 5.dp),
        content = {
            Text(
                text = stringResource(R.string.ai_chatgpt_current_account),
                color = labelColor,
                fontSize = 14.sp,
                lineHeight = 18.sp,
            )
            Row(
                modifier = Modifier.padding(end = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_workbuddy_account_avatar),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = accountLabel,
                    modifier = Modifier.weight(1f, fill = false),
                    color = Color(colors.onSurfaceVariant),
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = stringResource(R.string.ai_workbuddy_credits_balance),
                color = labelColor,
                fontSize = 14.sp,
                lineHeight = 18.sp,
            )
            Text(
                text = amount,
                color = labelColor,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                AccountUsageRefreshButton(loading, onRefresh, compact = true)
            }
        },
    ) { measurables, constraints ->
        val (accountLabelContent, accountValueContent, balanceLabelContent, amountContent, refreshContent) = measurables
        val width = constraints.maxWidth
        val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val valueConstraints = childConstraints.copy(maxWidth = width * 3 / 5)
        val accountValue = accountValueContent.measure(valueConstraints)
        val refresh = refreshContent.measure(valueConstraints)
        val amountWidth = valueConstraints.maxWidth - refresh.width
        val amountGap = minOf(2.dp.roundToPx(), amountWidth)
        val amountValue = amountContent.measure(valueConstraints.copy(maxWidth = amountWidth - amountGap))
        // 两行的右侧内容共用起点，刷新键始终靠卡片内沿。
        val valueStart = width - maxOf(accountValue.width, amountValue.width + amountGap + refresh.width)
        val labelConstraints = childConstraints.copy(maxWidth = (valueStart - 12.dp.roundToPx()).coerceAtLeast(0))
        val accountTitle = accountLabelContent.measure(labelConstraints)
        val balanceTitle = balanceLabelContent.measure(labelConstraints)
        val accountHeight = maxOf(44.dp.roundToPx(), accountTitle.height, accountValue.height)
        val balanceHeight = maxOf(balanceTitle.height, amountValue.height, refresh.height)
        layout(width, accountHeight + balanceHeight) {
            accountTitle.placeRelative(0, (accountHeight - accountTitle.height) / 2)
            accountValue.placeRelative(valueStart, (accountHeight - accountValue.height) / 2)
            balanceTitle.placeRelative(0, accountHeight + (balanceHeight - balanceTitle.height) / 2)
            amountValue.placeRelative(valueStart, accountHeight + (balanceHeight - amountValue.height) / 2)
            refresh.placeRelative(width - refresh.width, accountHeight + (balanceHeight - refresh.height) / 2)
        }
    }
}

@Composable
private fun CodexUsageCard(state: AiChatGptProviderUiState, onRefresh: () -> Unit) {
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()
    NgFormFieldGroup {
        AccountUsageHeader(R.string.ai_codex_usage_title, state.usageLoading, onRefresh)
        val usage = state.codexUsage
        if (usage == null) {
            AccountUsageLine(stringResource(if (state.usageLoading) R.string.loading else R.string.ai_codex_usage_unavailable))
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
                if (usage.windows.isNotEmpty()) AccountUsageDivider()
            }
            usage.windows.forEach { window -> CodexUsageWindow(window, locale) }
            if (credits != null) {
                if (plan != null || usage.windows.isNotEmpty()) AccountUsageDivider()
                AccountUsageAmount(R.string.ai_codex_usage_credits, credits)
            }
            if (plan == null && usage.windows.isEmpty() && credits == null) {
                AccountUsageLine(stringResource(R.string.ai_codex_usage_unavailable))
            }
        }
    }
}

@Composable
private fun CodexUsageWindow(window: AiCodexUsage.Window, locale: Locale) {
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
                AccountUsageLine(stringResource(R.string.ai_codex_usage_reset, resetTime), Modifier.weight(1f))
            } else {
                Spacer(Modifier.weight(1f))
            }
            AccountUsageLine(stringResource(R.string.ai_codex_usage_remaining, window.remainingPercent))
        }
        AccountUsageProgress(window.remainingPercent / 100f)
    }
}

@Composable
private fun AccountUsageHeader(@StringRes title: Int, loading: Boolean, onRefresh: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(title),
            modifier = Modifier.weight(1f),
            color = Color(NgTheme.colors.onSurface),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
        AccountUsageRefreshButton(loading, onRefresh)
    }
}

@Composable
private fun AccountUsageRefreshButton(loading: Boolean, onRefresh: () -> Unit, compact: Boolean = false) {
    val accent = Color(NgTheme.colors.primary)
    val tint = when {
        loading -> accent.copy(alpha = 0.38f)
        compact -> accent.copy(alpha = 0.78f)
        else -> accent
    }
    NgIconButton(
        onClick = onRefresh,
        // WorkBuddy 为小图标留出紧凑的布局空间，点击区仍为 48 dp。
        modifier = if (compact) Modifier.requiredSize(48.dp) else Modifier.size(38.dp),
        enabled = !loading,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_refresh_black_24dp),
            contentDescription = stringResource(R.string.refresh),
            modifier = Modifier.size(if (compact) 16.dp else 24.dp),
            tint = tint,
        )
    }
}

@Composable
private fun AccountUsageAmount(@StringRes label: Int, amount: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(label),
            modifier = Modifier.weight(1f),
            color = Color(NgTheme.colors.onSurface),
            fontSize = 14.sp,
        )
        Text(
            text = amount,
            color = Color(NgTheme.colors.onSurface),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun AccountUsageProgress(fraction: Float) {
    val accent = Color(NgTheme.colors.primary)
    val pill = RoundedCornerShape(percent = 50)
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

@Composable
private fun AccountUsageDivider(alpha: Float = 0.38f) {
    HorizontalDivider(color = Color(NgTheme.colors.outlineVariant).copy(alpha = alpha))
}

@Composable
private fun AccountUsageLine(text: String, modifier: Modifier = Modifier) {
    Text(text = text, modifier = modifier, color = Color(NgTheme.colors.onSurfaceVariant), fontSize = 13.sp, lineHeight = 18.sp)
}
