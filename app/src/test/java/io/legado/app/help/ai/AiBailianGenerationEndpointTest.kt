package io.legado.app.help.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiBailianGenerationEndpointTest {
    private val preset = AiDefaultProviders.all().first { it.id == "aliyun_bailian" }
    private val responses = AiModelGenerationTestProtocol.RESPONSES

    @Test
    fun officialBasesKeepTheirOwnHostForResponses() {
        val hosts = listOf(
            "dashscope.aliyuncs.com",
            "dashscope-intl.aliyuncs.com",
            "dashscope-us.aliyuncs.com",
            "cn-hongkong.dashscope.aliyuncs.com",
            "llm-test.cn-beijing.maas.aliyuncs.com",
            "llm-test.ap-southeast-1.maas.aliyuncs.com",
            "llm-test.us-east-1.maas.aliyuncs.com",
            "llm-test.eu-central-1.maas.aliyuncs.com",
            "llm-test.ap-northeast-1.maas.aliyuncs.com",
            "llm-test.cn-hongkong.maas.aliyuncs.com",
        )
        hosts.forEach { host ->
            val base = "https://$host/compatible-mode/v1"
            val provider = preset.copy(baseUrl = "$base/")
            assertTrue(host, provider.isOfficialBailianGenerationEndpoint())
            assertTrue(host, responses in provider.modelGenerationTestProtocols())
            val path = requireNotNull(provider.generationApiPath(responses))
            assertEquals("$base/responses", buildAiApiEndpoint(provider.baseUrl, path))
        }
    }

    @Test
    fun unrelatedUrlsDoNotGetTheOfficialRoute() {
        val official = "llm-test.cn-beijing.maas.aliyuncs.com"
        val basePath = "/compatible-mode/v1"
        val addresses = listOf(
            "https://$official.example.com$basePath",
            "https://nested.$official$basePath",
            "https://trial.cn-beijing.maas.aliyuncs.com$basePath",
            "https://token-plan.cn-beijing.maas.aliyuncs.com$basePath",
            "https://llm-test.cn-shanghai.maas.aliyuncs.com$basePath",
            "https://dashscope-not-a-region.aliyuncs.com$basePath",
            "http://$official$basePath",
            "https://$official:8443$basePath",
            "https://user@$official$basePath",
            "https://$official$basePath?query=1",
            "https://$official$basePath#fragment",
            "https://$official/apps/anthropic",
            "https://$official$basePath/responses",
        )
        addresses.forEach { address ->
            val provider = preset.copy(baseUrl = address)
            assertFalse(address, provider.isOfficialBailianGenerationEndpoint())
            assertNull(address, provider.generationApiPath(responses))
        }
    }

    @Test
    fun workspaceRecognitionDoesNotRebindKeysOrReuseThePublicCatalog() {
        val public = preset.copy(apiKey = "fixture-key").withRequestConfiguration(null)
        val workspace = public.copy(
            baseUrl = "https://llm-test.cn-beijing.maas.aliyuncs.com/compatible-mode/v1",
        ).withRequestConfiguration(public)

        assertTrue(workspace.isOfficialBailianGenerationEndpoint())
        assertNull(workspace.officialModelProviderId())
        assertTrue(workspace.apiKeyNeedsReentry())
        assertTrue(workspace.catalogRefreshRequired)
        assertNotEquals(public.modelConfigurationKey(), workspace.modelConfigurationKey())
        assertEquals(
            workspace.modelConfigurationKey(),
            workspace.copy(useResponsesApi = true).modelConfigurationKey(),
        )
    }
}
