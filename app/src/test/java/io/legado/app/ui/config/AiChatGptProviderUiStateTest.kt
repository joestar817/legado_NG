package io.legado.app.ui.config

import io.legado.app.R
import io.legado.app.help.ai.AiChatGptAccount
import io.legado.app.help.ai.AiChatGptTokens
import io.legado.app.help.ai.AiDefaultProviders
import io.legado.app.help.ai.CHATGPT_PLAN_SCOPE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiChatGptProviderUiStateTest {
    private val account = AiChatGptAccount(
        clientId = "oaiapp_reader",
        subject = "reader",
        email = "reader@example.test",
        tokens = AiChatGptTokens("access", "refresh", Long.MAX_VALUE, setOf(CHATGPT_PLAN_SCOPE)),
    )

    @Test
    fun chatGptAndOpenAiHaveSeparateHelpDocuments() {
        val providers = AiDefaultProviders.all().associateBy { it.id }
        val chatGpt = providers.getValue(AiDefaultProviders.CHATGPT_ID)
        val openAi = providers.getValue("openai")

        assertEquals(
            "chatGptProviderHelp",
            AiProviderHelpDocuments.forProvider(chatGpt.id, chatGpt.builtIn, chatGpt.baseUrl),
        )
        assertEquals(
            "openAiProviderHelp",
            AiProviderHelpDocuments.forProvider(openAi.id, openAi.builtIn, openAi.baseUrl),
        )
    }

    @Test
    fun signingOutHidesTheRememberedAccountAndItsSelector() {
        val signedIn = accountState(listOf(account))
        assertTrue(signedIn.signedIn)
        assertTrue(signedIn.planAuthorized)
        assertEquals(account.email, signedIn.currentAccount?.label)
        assertFalse(signedIn.canSwitchAccounts)
        assertNull(signedIn.authorizationActionRes)

        val signedOut = accountState(listOf(account.copy(tokens = null)))
        assertFalse(signedOut.signedIn)
        assertFalse(signedOut.planAuthorized)
        assertNull(signedOut.currentAccount)
        assertTrue(signedOut.accounts.isEmpty())
        assertFalse(signedOut.canSwitchAccounts)
        assertTrue(signedOut.hasRegisteredAccounts)
        assertEquals(R.string.ai_chatgpt_sign_in, signedOut.authorizationActionRes)
    }

    @Test
    fun signingOutLeavesOtherRegistrationsAvailableWithoutSelectingThem() {
        val other = account.copy(clientId = "oaiapp_workspace")
        val signedOut = accountState(listOf(account.copy(tokens = null), other))

        assertNull(signedOut.currentAccount)
        assertTrue(signedOut.canSwitchAccounts)
        assertEquals(listOf(other.clientId), signedOut.accounts.map { it.value })
        assertEquals("reader@example.test (2)", signedOut.accounts.single().label)
    }

    @Test
    fun signedInAccountWithoutPlanPermissionStillAppearsAsSignedIn() {
        val withoutPlan = account.copy(tokens = AiChatGptTokens("access", null, Long.MAX_VALUE, emptySet()))
        val state = accountState(listOf(withoutPlan))

        assertTrue(state.signedIn)
        assertFalse(state.planAuthorized)
        assertEquals(account.clientId, state.currentAccount?.value)
        assertFalse(state.canSwitchAccounts)
        assertEquals(R.string.ai_chatgpt_authorize_plan, state.authorizationActionRes)
    }

    @Test
    fun expiredAccessTokenStillRenewableDoesNotRequestReauthorization() {
        val renewable = account.copy(tokens = AiChatGptTokens("access", "refresh", 999, setOf(CHATGPT_PLAN_SCOPE)))

        val state = accountState(listOf(renewable))

        assertTrue(state.signedIn)
        assertTrue(state.planAuthorized)
        assertNull(state.authorizationActionRes)
    }

    @Test
    fun expiredTokenWithoutRenewalKeepsTheAccountAndOffersReauthorization() {
        val expired = account.copy(tokens = AiChatGptTokens("access", null, 1_000, setOf(CHATGPT_PLAN_SCOPE)))

        assertExpired(accountState(listOf(expired)))
    }

    @Test
    fun rejectedSessionOffersReauthorizationWithOrWithoutStoredTokens() {
        val rejected = account.copy(reauthorizationRequired = true)

        assertExpired(accountState(listOf(rejected)))
        assertExpired(accountState(listOf(rejected.copy(tokens = null))))
    }

    @Test
    fun rejectionOfAnotherAccountDoesNotReplaceTheCurrentAccountActions() {
        val other = account.copy(clientId = "oaiapp_workspace", reauthorizationRequired = true)

        val state = accountState(listOf(account, other))

        assertTrue(state.signedIn)
        assertTrue(state.canSwitchAccounts)
        assertNull(state.authorizationActionRes)
    }

    private fun assertExpired(state: AiChatGptProviderUiState) {
        assertFalse(state.signedIn)
        assertFalse(state.planAuthorized)
        assertTrue(state.needsReauthorization)
        assertEquals(account.email, state.currentAccount?.label)
        assertEquals(R.string.ai_chatgpt_reauthorize, state.authorizationActionRes)
    }

    private fun accountState(accounts: List<AiChatGptAccount>) =
        AiChatGptProviderUiState.fromAccounts(accounts, account.clientId, "ChatGPT account", nowSeconds = 1_000)
}
