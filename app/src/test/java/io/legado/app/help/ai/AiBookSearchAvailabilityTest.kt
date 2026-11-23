package io.legado.app.help.ai

import io.legado.app.help.ai.AiBookSearchAvailability.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiBookSearchAvailabilityTest {
    private val providers = AiDefaultProviders.all().associateBy { it.id }
    private val mimo = provider("xiaomi_mimo")
    private val deepSeek = provider("deepseek")
    private val chatGpt = provider(AiDefaultProviders.CHATGPT_ID).copy(streamResponseEnabled = true)
    private val mimoFlash = model("mimo-v2.6-flash", tools = true)
    private val deepSeekFlash = model("deepseek-v4-flash")
    private val luna = model("gpt-6-luna")
    private val automatic = AiExternalSearchSettings()
    private val external = automatic.copy(
        routing = AiWebRoutingMode.EXTERNAL,
        selectedService = AiExternalSearchProvider.DEEPSEEK.id,
        deepSeek = AiExternalSearchService(apiKey = "fixture"),
    )

    @Test
    fun nativeBookSearchFollowsTheProviderAndProtocolEvenWithoutFunctionTools() {
        assertEquals(READY, automatic.bookSearchAvailability(true, chatGpt, luna, hasCredentials = true))
        assertEquals(AiWebAccess.Native, automatic.webAccess(chatGpt, luna, requireSearch = true))
        assertEquals(READY, automatic.bookSearchAvailability(true, deepSeek, deepSeekFlash))
        assertEquals(READY, automatic.bookSearchAvailability(true, deepSeek, model("deepseek-flash")))
        val pro = model("deepseek-v4-pro")
        assertEquals(UNAVAILABLE, automatic.bookSearchAvailability(true, deepSeek, pro))
        assertEquals(READY, automatic.bookSearchAvailability(true, deepSeek.copy(useResponsesApi = true), pro))
        assertEquals(UNAVAILABLE, automatic.bookSearchAvailability(true, mimo, mimoFlash))
        assertEquals(UNAVAILABLE, automatic.bookSearchAvailability(true, provider("openai").copy(useResponsesApi = true), luna))
        assertNull(deepSeek.copy(baseUrl = "https://proxy.example.org").nativeWebRoute(deepSeekFlash))
        assertNull(mimo.nativeWebRoute(deepSeekFlash))
        assertNull(mimo.nativeWebRoute(luna))
    }

    @Test
    fun distinguishesMissingSkillModelAndCredentialsFromWebSettings() {
        assertEquals(SKILL, automatic.bookSearchAvailability(false, mimo, mimoFlash))
        assertEquals(MODEL, automatic.bookSearchAvailability(true, null, null))
        assertEquals(MODEL, automatic.bookSearchAvailability(true, mimo.copy(enabled = false), mimoFlash))
        assertEquals(MODEL, automatic.bookSearchAvailability(true, mimo, null))
        assertEquals(MODEL, automatic.bookSearchAvailability(true, mimo, mimoFlash.copy(type = AiModelType.EMBEDDING)))
        assertEquals(PROVIDER, automatic.bookSearchAvailability(true, mimo.copy(apiKey = ""), null))
        assertEquals(PROVIDER, automatic.bookSearchAvailability(true, mimo.copy(apiKeyOrigin = "https://other.example/"), null))
        assertEquals(ACCOUNT, automatic.bookSearchAvailability(true, chatGpt, null, hasCredentials = false))
        assertEquals(GENERATION, automatic.bookSearchAvailability(
            true, chatGpt.copy(streamResponseEnabled = false), luna, hasCredentials = true,
        ))
        assertEquals(OFF, automatic.copy(webEnabled = false).bookSearchAvailability(true, deepSeek, deepSeekFlash))
    }

    @Test
    fun readOnlyDeepSeekNeedsExternalSearchAndDoesNotAppearReady() {
        val readOnly = deepSeekFlash.copy(
            modelSettings = AiModelSettings(deepSeek.modelConfigurationKey(), nativeWebSearch = false),
        )
        assertEquals(UNAVAILABLE, automatic.bookSearchAvailability(true, deepSeek, readOnly))
        assertEquals(AiWebAccess.Unavailable, automatic.webAccess(deepSeek, readOnly, requireSearch = true))
        assertEquals(EXTERNAL_TOOLS, external.bookSearchAvailability(true, deepSeek, readOnly))
        assertEquals(READY, external.bookSearchAvailability(
            true, deepSeek, readOnly.copy(abilities = listOf(AiModelAbility.TOOL)),
        ))
    }

    @Test
    fun externalRequiresAnEnabledSelectedServiceAndFunctionTools() {
        val plain = mimoFlash.copy(abilities = emptyList())
        val disabled = external.copy(deepSeek = external.deepSeek.copy(enabled = false))
        val keyMissing = external.copy(deepSeek = external.deepSeek.copy(apiKey = ""))
        for (unavailable in listOf(disabled, keyMissing, external.copy(selectedService = ""),
            external.copy(selectedService = "unknown"))) {
            assertEquals(EXTERNAL_SETUP, unavailable.bookSearchAvailability(true, mimo, mimoFlash))
            assertEquals(AiWebAccess.Unavailable, unavailable.webAccess(mimo, mimoFlash, requireSearch = true))
        }
        assertEquals(EXTERNAL_TOOLS, external.bookSearchAvailability(true, mimo, plain))
        assertEquals(READY, external.bookSearchAvailability(true, mimo, mimoFlash))
        assertTrue(external.webAccess(mimo, mimoFlash, requireSearch = true) is AiWebAccess.External)
        assertEquals(OFF, external.copy(webEnabled = false).bookSearchAvailability(true, mimo, mimoFlash))
    }

    @Test
    fun forcedExternalAndAutomaticFallbackRespectTheSelectedRoute() {
        val autoWithoutFallback = external.copy(routing = AiWebRoutingMode.AUTO)
        val autoWithFallback = autoWithoutFallback.copy(backupEnabled = true)
        assertEquals(UNAVAILABLE, autoWithoutFallback.bookSearchAvailability(true, mimo, mimoFlash))
        assertEquals(READY, autoWithFallback.bookSearchAvailability(true, mimo, mimoFlash))
        assertEquals(AiWebAccess.Native, autoWithFallback.webAccess(deepSeek, deepSeekFlash, requireSearch = true))
        assertEquals(EXTERNAL_TOOLS, external.bookSearchAvailability(true, deepSeek, deepSeekFlash))
        assertEquals(EXTERNAL_SETUP, external.copy(selectedService = "").bookSearchAvailability(true, deepSeek, deepSeekFlash))
        assertTrue(external.webAccess(
            deepSeek, deepSeekFlash.copy(abilities = listOf(AiModelAbility.TOOL)), requireSearch = true,
        ) is AiWebAccess.External)
    }

    private fun provider(id: String) = providers.getValue(id).copy(apiKey = "fixture")

    private fun model(id: String, tools: Boolean = false) = AiModel(
        id,
        inputModalities = listOf(AiModelModality.TEXT),
        outputModalities = listOf(AiModelModality.TEXT),
        abilities = if (tools) listOf(AiModelAbility.TOOL) else emptyList(),
    )
}
