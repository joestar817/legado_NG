package io.legado.app.help.ai

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiSharedModelConfigurationTest {
    @Test
    fun `OpenRouter uses routed fields and disallows Off on mandatory reasoning`() {
        val provider = provider("openrouter")
        val source = provider.modelConfigurationKey()
        val info = readModelInfo(json("""
            {
              "context_length":1048576,
              "top_provider":{"max_completion_tokens":943718},
              "architecture":{"input_modalities":["file","text","image"],"output_modalities":["text"]},
              "supported_parameters":["tools","reasoning"],
              "reasoning":{"mandatory":true,"supported_efforts":["xhigh","high","medium","low"],"default_effort":"medium"}
            }
        """), source, AiModelCatalogFormat.OPENROUTER)
        val model = AiModel("google/gemini-4-neon", modelInfo = info).withCatalogCapabilities(info)
        assertEquals(1_048_576, provider.assistantContextWindowLimit(model))
        assertEquals(943_718, info.maxOutputTokens?.value)
        assertEquals(setOf(AiModelModality.TEXT, AiModelModality.IMAGE, AiModelModality.FILE), model.inputModalities.toSet())
        assertTrue(AiModelAbility.TOOL in model.abilities)
        val reasoning = provider.modelAssistantReasoning(model, "xhigh", AiReasoningLevel.OFF)
        assertEquals(listOf("auto", "low", "medium", "high", "xhigh"), reasoning.options)
        assertEquals("xhigh", payload(provider, model, reasoning).getAsJsonObject("reasoning").get("effort").asString)
        assertEquals("auto", provider.modelAssistantReasoning(model, "off", AiReasoningLevel.HIGH).selected)
    }

    @Test
    fun `OpenRouter distinguishes missing supported efforts from gateway supported null`() {
        val router = provider("openrouter")
        val source = router.modelConfigurationKey()
        fun info(body: String) = readModelInfo(json(body), source, AiModelCatalogFormat.OPENROUTER)
        val previous = info("""{"reasoning":{"supported_efforts":["high"],"default_effort":"high"}}""")
        val switch = info("""{"reasoning":{"mandatory":false}}""").withPrevious(previous)
        val model = textModel("deepseek-flash", switch)
        assertEquals(emptyList<String>(), switch.effortLevels?.value)
        assertNull(switch.defaultEffort)
        val on = router.modelAssistantReasoning(model, "on", null)
        assertEquals(listOf("off", "auto", "on"), on.options)
        assertTrue(payload(router, model, on).getAsJsonObject("reasoning").get("enabled").asBoolean)

        val gateway = model.copy(modelInfo = info("""{"reasoning":{"supported_efforts":null}}"""))
        val choices = router.modelAssistantReasoning(gateway, "off", null)
        assertEquals(listOf("off", "auto", "minimal", "low", "medium", "high", "xhigh", "max"), choices.options)
        assertFalse(payload(router, gateway, choices).getAsJsonObject("reasoning").get("enabled").asBoolean)
    }

    @Test
    fun `native catalogs and SenseNova have separate specification fields`() {
        val claude = readModelInfo(json("""
            {"max_input_tokens":1000000,"max_tokens":128000,
             "capabilities":{"thinking":{"supported":true,"types":{"disabled":{"supported":false}}},
             "effort":{"high":{"supported":true},"low":{"supported":false},"max":{"supported":true}}}}
        """), "claude", AiModelCatalogFormat.CLAUDE)
        assertEquals(1_000_000, claude.contextWindow?.value)
        assertEquals(128_000, claude.maxOutputTokens?.value)
        assertEquals(listOf("high", "max"), claude.selectableEffortLevels())
        assertEquals(false, claude.canDisableThinking?.value)

        val gemini = readModelInfo(json("""{"inputTokenLimit":1048576,"outputTokenLimit":65536,"thinking":true}"""),
            "gemini", AiModelCatalogFormat.GEMINI)
        assertEquals(1_048_576, gemini.contextWindow?.value)
        assertEquals(65_536, gemini.maxOutputTokens?.value)
        assertEquals(true, gemini.reasoning?.value)

        val senseNova = readModelInfo(json("""{"context_length":262144,"max_output_length":65536,
            "input_modalities":["text"],"output_modalities":["image"],"supported_features":["tools","reasoning"]}"""),
            "sense", AiModelCatalogFormat.SENSENOVA)
        assertEquals(262_144, senseNova.contextWindow?.value)
        assertEquals(65_536, senseNova.maxOutputTokens?.value)
        assertEquals(AiModelType.IMAGE, AiModel("image-model").withCatalogCapabilities(senseNova).type)
    }

    @Test
    fun `every built-in and custom provider can save manual model specifications`() {
        val custom = AiProviderSetting("custom", AiProviderType.OPENAI, builtIn = false,
            name = "Custom", baseUrl = "https://example.invalid/v1")
        for (provider in AiDefaultProviders.all() + custom) {
            val key = provider.modelConfigurationKey()
            val model = AiModel("manual-example", modelSettings = AiModelSettings(key,
                referenceContextWindowTokens = 128_999, referenceMaxOutputTokens = 7_777))
            val info = provider.effectiveModelInfo(model)
            assertEquals(provider.id, "128K", assistantContextWindowLabel(requireNotNull(provider.assistantContextWindowLimit(model))))
            assertEquals(provider.id, 7_777, model.modelSettingsFor(key)?.referenceMaxOutput(info))
        }
    }

    @Test
    fun `catalog refresh preserves same-source overrides and never borrows an older endpoint`() {
        val provider = provider("openrouter")
        val key = provider.modelConfigurationKey()
        val model = AiModel("m", displayName = "My model", abilities = emptyList(),
            modelInfo = AiModelInfo(key, contextWindow = AiModelValue(1_048_576)),
            modelSettings = AiModelSettings(key, capabilitiesEdited = true, referenceContextWindowTokens = 128_000))
        val fetched = AiModel("m", abilities = listOf(AiModelAbility.TOOL), modelInfo = AiModelInfo(key))
        val refreshed = provider.copy(models = listOf(model)).mergeModelInfo(listOf(fetched)).single()
        assertEquals("My model", refreshed.displayName)
        assertTrue(refreshed.abilities.isEmpty())
        assertEquals(128_000, provider.assistantContextWindowLimit(refreshed))
        assertEquals(true, refreshed.modelInfo?.contextWindow?.fromPreviousCatalog)

        val changed = provider.copy(models = listOf(model), baseUrl = "https://proxy.example.invalid/v1")
        val newModel = changed.mergeModelInfo(listOf(fetched)).single()
        assertNull(newModel.modelInfo)
        assertNull(newModel.modelSettings)
        assertNull(changed.assistantContextWindowLimit(model))
    }

    @Test
    fun `Bailian id-only model uses its official modalities and efforts in both chat payloads`() {
        val bailian = provider("aliyun_bailian")
        val qwen = importModel(bailian, """{"id":"qwen4-max"}""")
        val effective = bailian.effectiveModelCapabilities(qwen)
        assertEquals(listOf(AiModelModality.TEXT, AiModelModality.IMAGE, AiModelModality.VIDEO), effective.inputModalities)
        assertEquals(listOf(AiModelModality.TEXT), effective.outputModalities)
        assertEquals(setOf(AiModelAbility.TOOL, AiModelAbility.REASONING), effective.abilities.toSet())
        assertEquals(1_000_000, bailian.assistantContextWindowLimit(qwen))
        assertEquals(262_144, bailian.effectiveModelInfo(qwen).maxOutputTokens?.value)
        assertEquals(listOf("low", "high", "xhigh"), bailian.modelReasoning(qwen).efforts.candidates)
        val high = bailian.modelAssistantReasoning(qwen, "xhigh", AiReasoningLevel.OFF)
        assertEquals(listOf("off", "auto", "low", "high", "xhigh"), high.options)
        for (stream in listOf(false, true)) {
            val request = requestBody(bailian, qwen, high, stream)
            assertEquals("qwen4-max", request.get("model").asString)
            assertEquals(stream, request.get("stream").asBoolean)
            assertTrue(request.get("enable_thinking").asBoolean)
            assertEquals("xhigh", request.get("reasoning_effort").asString)
        }
        assertFalse(payload(bailian, qwen, bailian.modelAssistantReasoning(qwen, "off", AiReasoningLevel.AUTO))
            .get("enable_thinking").asBoolean)
        assertEquals(0, payload(bailian, qwen, bailian.modelAssistantReasoning(qwen, "auto", AiReasoningLevel.OFF)).size())
    }

    @Test
    fun `required reasoning cannot be disabled even with the previous Off choice`() {
        val moonshot = provider("moonshot")
        val kimi = AiModel("kimi-k3", abilities = listOf(AiModelAbility.REASONING))
        val max = moonshot.modelAssistantReasoning(kimi, "max", AiReasoningLevel.OFF)
        assertFalse("off" in max.options)
        assertEquals("max", payload(moonshot, kimi, max).get("reasoning_effort").asString)
        assertEquals("auto", moonshot.modelAssistantReasoning(kimi, "off", AiReasoningLevel.OFF).selected)
    }

    @Test
    fun `SenseNova and a custom route share exact model levels and keep their own request fields`() {
        val sense = provider("sensenova")
        val model = importModel(sense, """{"id":"deepseek-flash","context_length":1048576,
            "max_output_length":65536,"supported_features":["tools","reasoning"]}""")
        val efforts = listOf("low", "high", "max")
        assertEquals(65536, sense.effectiveModelInfo(model).maxOutputTokens?.value)
        assertEquals(efforts, sense.modelReasoning(model).efforts.enabled)
        for (stream in listOf(false, true)) {
            val request = requestBody(sense, model, sense.modelAssistantReasoning(model, "max", null), stream)
            assertEquals("deepseek-flash", request.get("model").asString)
            assertEquals("max", request.get("reasoning_effort").asString)
            assertFalse(request.has("thinking"))
        }
        assertEquals("none", payload(sense, model, sense.modelAssistantReasoning(model, "off", null))
            .get("reasoning_effort").asString)

        val custom = customProvider()
        assertEquals(efforts, custom.modelReasoning(importModel(custom, """{"id":"deepseek-flash"}""")).efforts.enabled)
        assertEquals(efforts, custom.modelReasoning(textModel("DeepSeek-V4.1-Flash")).efforts.enabled)
        assertTrue(custom.modelReasoning(textModel("deepseek-flash-distill")).isTrial)
    }

    @Test
    fun `current restrictions beat old checks cache and model defaults`() {
        val custom = customProvider()
        val key = custom.modelConfigurationKey()
        val settings = AiModelSettings(key, enabledReasoningEfforts = listOf("ultra"))
        val previous = AiModelInfo(key, effortLevels = AiModelValue(listOf("ultra")), reasoning = AiModelValue(true))
        val model = textModel("deepseek-flash", AiModelInfo(key).withPrevious(previous)).copy(modelSettings = settings)
        assertEquals(listOf("ultra"), custom.modelReasoning(model).efforts.enabled)
        assertEquals(listOf("ultra"), custom.modelReasoning(
            model.copy(modelInfo = previous.copy(endpoint = "old-source")),
        ).efforts.enabled)
        assertEquals(listOf("low", "high", "max"), custom.modelReasoning(
            model.copy(modelInfo = previous.copy(endpoint = "old-source"), modelSettings = null),
        ).efforts.enabled)

        val narrowed = readModelInfo(json("""{"effort":{"supported_levels":["high","max"]}}"""),
            key, AiModelCatalogFormat.COMPATIBLE).withPrevious(previous)
        val narrowedModel = model.copy(modelInfo = narrowed)
        val options = custom.modelReasoning(narrowedModel).efforts
        assertTrue(options.checked.isEmpty())
        assertEquals(listOf("high", "max"), options.enabled)
        assertEquals("auto", custom.modelAssistantReasoning(narrowedModel, "ultra", null).selected)
        assertEquals("max", custom.modelAssistantReasoning(narrowedModel, "max", null).selected)

        val unsupported = readModelInfo(json("""{"reasoning":false}"""), key, AiModelCatalogFormat.COMPATIBLE)
            .withPrevious(narrowed)
        val unsupportedModel = model.copy(modelInfo = unsupported, abilities = listOf(AiModelAbility.REASONING),
            modelSettings = settings.copy(capabilitiesEdited = true))
        val choice = custom.modelAssistantReasoning(unsupportedModel, "high", null, modelEffort = "max")
        assertFalse(choice.supported)
        assertEquals(listOf("auto"), choice.options)
        assertEquals(0, payload(custom, unsupportedModel, choice).size())
    }

    @Test
    fun `unknown text chat can try six levels after making a model-specific choice`() {
        val custom = customProvider()
        val unknown = textModel("new-chat-model")
        val initial = custom.modelAssistantReasoning(unknown, "ultra", AiReasoningLevel.HIGH)
        assertTrue(initial.isTrial)
        assertEquals(listOf("auto", "low", "medium", "high", "xhigh", "max", "ultra"), initial.options)
        assertEquals("auto", initial.selected)
        assertEquals(0, payload(custom, unknown, initial).size())
        val selected = custom.modelAssistantReasoning(unknown, "ultra", AiReasoningLevel.HIGH, modelEffort = "xhigh")
        assertEquals("xhigh", payload(custom, unknown, selected).get("reasoning_effort").asString)
        assertTrue(custom.modelReasoning(AiModel("unknown-without-modalities")).isTrial)
    }

    @Test
    fun `legacy custom fields and values are preserved without writing a generic effort`() {
        val custom = customProvider().copy(effortParam = "depth", disableEffortValue = "disabled")
        val model = textModel("custom-chat").copy(abilities = listOf(AiModelAbility.REASONING))
        val high = custom.modelAssistantReasoning(model, null, AiReasoningLevel.HIGH)
        assertEquals("high", high.selected)
        for (stream in listOf(false, true)) {
            val request = requestBody(custom, model, high, stream)
            assertEquals("high", request.get("depth").asString)
            assertFalse(request.has("reasoning_effort"))
        }
        assertEquals("disabled", payload(custom, model, custom.modelAssistantReasoning(model, "off", null))
            .get("depth").asString)
        assertEquals(0, payload(custom, model, custom.modelAssistantReasoning(model, "auto", AiReasoningLevel.HIGH)).size())

        val switch = customProvider().copy(thinkingParam = "thought_mode")
        val switchModel = textModel("deepseek-flash").copy(modelSettings = AiModelSettings(
            switch.modelConfigurationKey(), enabledReasoningEfforts = listOf("high"),
        ))
        val on = switch.modelAssistantReasoning(switchModel, null, AiReasoningLevel.HIGH)
        assertEquals(listOf("off", "auto", "on"), on.options)
        val body = payload(switch, switchModel, on)
        assertEquals(setOf("thought_mode"), body.keySet())
        assertEquals("enabled", body.getAsJsonObject("thought_mode").get("type").asString)

        // Text workflows still use the original inference and fallback for their own requests.
        val textRequest = JsonObject().apply {
            addReasoningParams(AiTextParams(enableThinking = true), switch.reasoningOptions(switchModel.id))
        }
        assertEquals("enabled", textRequest.getAsJsonObject("thought_mode").get("type").asString)
        assertEquals("high", textRequest.get("reasoning_effort").asString)

        val sense = provider("sensenova")
        val summarizer = textModel("deepseek-flash")
        val summary = AiChatClient().buildRequestBody(
            sense, summarizer, emptyList(), emptyList(),
            sense.compactionTextParams(summarizer), stream = false,
        )
        assertEquals("none", summary.get("reasoning_effort").asString)
        assertFalse(summary.has("thinking"))
    }

    @Test
    fun `MiMo has only the thinking switch and SiliconFlow applies its documented strength limits`() {
        val mimo = provider("xiaomi_mimo")
        val model = importModel(mimo, """{"id":"mimo-v2.6-pro"}""")
        assertEquals("mimo-v2.6-pro", mimo.model)
        assertEquals(1_048_576, mimo.assistantContextWindowLimit(model))
        assertEquals(131_072, mimo.effectiveModelInfo(model).maxOutputTokens?.value)
        val on = mimo.modelAssistantReasoning(model, "on", null)
        assertEquals(listOf("off", "auto", "on"), on.options)
        assertTrue(mimo.modelReasoning(model).efforts.candidates.isEmpty())
        assertEquals("enabled", payload(mimo, model, on).getAsJsonObject("thinking").get("type").asString)

        val siliconFlow = provider("siliconflow")
        val flash = importModel(siliconFlow, """{"id":"deepseek-ai/DeepSeek-V4-Flash"}""")
        assertEquals(1_048_576, siliconFlow.assistantContextWindowLimit(flash))
        assertEquals(393_216, siliconFlow.effectiveModelInfo(flash).maxOutputTokens?.value)
        val choice = siliconFlow.modelAssistantReasoning(flash, "max", null)
        assertEquals(listOf("off", "auto", "high", "max"), choice.options)
        val body = payload(siliconFlow, flash, choice)
        assertTrue(body.get("enable_thinking").asBoolean)
        assertEquals("max", body.get("reasoning_effort").asString)
    }

    @Test
    fun `Bailian Flash falls back to its own specification and medium effort`() {
        val bailian = provider("aliyun_bailian")
        val model = importModel(bailian, """{"id":"qwen3.8-flash"}""")
        assertEquals(1_000_000, bailian.assistantContextWindowLimit(model))
        assertEquals(131_072, bailian.effectiveModelInfo(model).maxOutputTokens?.value)
        val selection = bailian.modelAssistantReasoning(model, "medium", null)
        assertEquals(listOf("off", "auto", "low", "medium", "xhigh"), selection.options)
        assertEquals("medium", payload(bailian, model, selection).get("reasoning_effort").asString)
    }

    @Test
    fun `published specifications fill only missing fields for the same provider and model ID`() {
        val siliconFlow = provider("siliconflow")
        val seed = importModel(siliconFlow, """{"id":"ByteDance-Seed/Seed-OSS-36B-Instruct"}""")
        assertEquals(262_144, siliconFlow.assistantContextWindowLimit(seed))
        assertNull(siliconFlow.effectiveModelInfo(seed).maxOutputTokens)
        assertNull(siliconFlow.copy(builtIn = false).effectiveModelInfo(seed).contextWindow)
        assertNull(siliconFlow.copy(baseUrl = "https://api.siliconflow.com/v1").effectiveModelInfo(seed).contextWindow)
        assertNull(siliconFlow.effectiveModelInfo(AiModel("deepseek-ai/DeepSeek-OCR")).contextWindow)

        val bailian = provider("aliyun_bailian")
        val routed = importModel(bailian, """{"id":"vanchin/deepseek-v3.2-think","context_window":65536}""")
        assertEquals(65_536, bailian.assistantContextWindowLimit(routed))
        assertEquals(65_536, bailian.effectiveModelInfo(routed).maxOutputTokens?.value)
        val fromSiliconFlow = importModel(bailian, """{"id":"siliconflow/deepseek-v3.2","max_output_tokens":8192}""")
        assertEquals(163_840, bailian.assistantContextWindowLimit(fromSiliconFlow))
        assertEquals(8_192, bailian.effectiveModelInfo(fromSiliconFlow).maxOutputTokens?.value)
        assertNull(bailian.effectiveModelInfo(AiModel(seed.id)).contextWindow)

        val manual = routed.copy(modelSettings = AiModelSettings(
            catalogEndpoint = bailian.modelConfigurationKey(),
            referenceContextWindowTokens = 32_768,
            referenceMaxOutputTokens = 4_096,
        ))
        assertEquals(32_768, bailian.assistantContextWindowLimit(manual))
        assertEquals(4_096, manual.modelSettings?.referenceMaxOutput(bailian.effectiveModelInfo(manual)))
    }

    @Test
    fun `SiliconFlow Qwen LoRA variants fill specifications separately by full ID`() {
        val siliconFlow = provider("siliconflow")
        for (size in listOf("7B", "14B", "32B", "72B")) {
            val id = "LoRA/Qwen/Qwen2.5-$size-Instruct"
            val model = importModel(siliconFlow, """{"id":"$id"}""")
            assertEquals(id, 32_768, siliconFlow.assistantContextWindowLimit(model))
            assertEquals(id, 8_192, siliconFlow.effectiveModelInfo(model).maxOutputTokens?.value)
        }
        val live = importModel(siliconFlow, """{
            "id":"LoRA/Qwen/Qwen2.5-32B-Instruct","context_window":65536,"max_output_tokens":4096
        }""")
        assertEquals(65_536, siliconFlow.assistantContextWindowLimit(live))
        assertEquals(4_096, siliconFlow.effectiveModelInfo(live).maxOutputTokens?.value)
        val manual = live.copy(modelSettings = AiModelSettings(
            catalogEndpoint = siliconFlow.modelConfigurationKey(),
            referenceContextWindowTokens = 16_384,
            referenceMaxOutputTokens = 2_048,
        ))
        assertEquals(16_384, siliconFlow.assistantContextWindowLimit(manual))
        assertEquals(2_048, manual.modelSettings?.referenceMaxOutput(siliconFlow.effectiveModelInfo(manual)))
        val global = siliconFlow.copy(baseUrl = "https://api.siliconflow.com/v1")
        assertNull(global.effectiveModelInfo(AiModel(live.id)).contextWindow)
    }

    @Test
    fun `Qwen reference lengths stay scoped to their provider and exact release`() {
        val siliconFlow = provider("siliconflow")
        val longContext = siliconFlow.effectiveModelInfo(AiModel("Qwen/Qwen2.5-72B-Instruct-128K"))
        assertEquals(131_072, longContext.contextWindow?.value)
        assertEquals(8_192, longContext.maxOutputTokens?.value)
        val coder = siliconFlow.effectiveModelInfo(AiModel("Qwen/Qwen3-Coder-30B-A3B-Instruct"))
        assertEquals(65_536, coder.maxOutputTokens?.value)
        val bailian = provider("aliyun_bailian")
        val older = bailian.effectiveModelInfo(AiModel("qwen2.5-1.5b-instruct"))
        assertEquals(32_768, older.contextWindow?.value)
        assertEquals(8_192, older.maxOutputTokens?.value)
        assertNull(bailian.effectiveModelInfo(AiModel("qwen-plus-2025-11-05")).contextWindow)
    }

    @Test
    fun `Bailian keeps historical Qwen windows and exact realtime specifications`() {
        val bailian = provider("aliyun_bailian")
        val legacy = mapOf(
            "qwen-1.8b-chat" to (8_000 to 2_000),
            "qwen-1.8b-longcontext-chat" to (32_000 to 2_000),
            "qwen-14b-chat" to (8_000 to 2_000),
            "qwen-72b-chat" to (32_000 to 2_000),
            "qwen-7b-chat" to (7_500 to 1_500),
        )
        for ((id, limits) in legacy) {
            val info = bailian.effectiveModelInfo(importModel(bailian, """{"id":"$id"}"""))
            assertEquals(id, limits.first, info.contextWindow?.value)
            assertEquals(id, limits.second, info.maxOutputTokens?.value)
        }
        for (variant in listOf("flash", "plus")) {
            val v30 = bailian.effectiveModelInfo(AiModel("qwen-audio-3.0-realtime-$variant"))
            assertEquals(40_960, v30.contextWindow?.value)
            assertEquals(8_192, v30.maxOutputTokens?.value)
            val v31 = bailian.effectiveModelInfo(AiModel("qwen-audio-3.1-realtime-$variant"))
            assertEquals(262_144, v31.contextWindow?.value)
            assertEquals(16_384, v31.maxOutputTokens?.value)
        }
    }

    @Test
    fun `Bailian reasoning profiles distinguish canonical levels switches and mandatory thinking`() {
        val bailian = provider("aliyun_bailian")
        val qwen = importModel(bailian, """{"id":"qwen3.8-max-0902"}""")
        val medium = bailian.modelAssistantReasoning(qwen, "medium", null)
        assertEquals(listOf("off", "auto", "low", "medium", "xhigh"), medium.options)
        assertEquals("medium", payload(bailian, qwen, medium).get("reasoning_effort").asString)
        assertNull(sharedModelEfforts(qwen.id))
        val updated = importModel(bailian, """{"id":"qwen3.8-max-0902","effort":{"supported_levels":["high"]}}""")
        assertEquals(listOf("off", "auto", "high"), bailian.modelAssistantReasoning(updated, null, null).options)

        val switch = importModel(bailian, """{"id":"antdigital/Ling-3.1-flash"}""")
        val on = bailian.modelAssistantReasoning(switch, "on", null)
        assertEquals(listOf("off", "auto", "on"), on.options)
        assertTrue(payload(bailian, switch, on).get("enable_thinking").asBoolean)
        assertFalse(payload(bailian, switch, on).has("reasoning_effort"))

        val mandatory = importModel(bailian, """{"id":"stepfun/step-5-preview"}""")
        val high = bailian.modelAssistantReasoning(mandatory, "high", null)
        assertEquals(listOf("auto", "low", "medium", "high"), high.options)
        val body = payload(bailian, mandatory, high)
        assertEquals("high", body.get("reasoning_effort").asString)
        assertFalse(body.has("enable_thinking"))
    }

    @Test
    fun `Bailian automatic indicator follows the documented default without forcing thinking`() {
        val bailian = provider("aliyun_bailian")
        val plus = importModel(bailian, """{"id":"qwen-plus"}""")
        val automatic = bailian.modelAssistantReasoning(plus, "auto", null)
        assertEquals(listOf("off", "auto", "on"), automatic.options)
        assertFalse(automatic.active)
        assertEquals(0, payload(bailian, plus, automatic).size())
    }

    @Test
    fun `OpenRouter reasoning budget sends only its documented budget and expires if it is removed`() {
        val router = provider("openrouter")
        val model = importModel(router, """{"id":"~anthropic/claude-example","max_completion_tokens":8192,
            "architecture":{"input_modalities":["text","file"],"output_modalities":["text"]},
            "reasoning":{"mandatory":true,"supports_max_tokens":true}}""")
        val budget = reasoningBudgetChoice(2048)
        val selection = router.modelAssistantReasoning(model, null, null, modelEffort = budget)
        assertTrue(selection.hasControls)
        assertEquals(1024..8191, selection.budgetRange)
        assertEquals(listOf("auto", budget), selection.options)
        for (stream in listOf(false, true)) {
            val body = requestBody(router, model, selection, stream)
            assertEquals(8192, body.get("max_tokens").asInt)
            val request = body.getAsJsonObject("reasoning")
            assertEquals(setOf("max_tokens"), request.keySet())
            assertEquals(2048, request.get("max_tokens").asInt)
        }
        val tooSmall = router.modelAssistantReasoning(model, null, null, modelEffort = reasoningBudgetChoice(999))
        assertEquals("auto", tooSmall.selected)
        assertFalse(payload(router, model, tooSmall).has("reasoning"))
        val newer = importModel(router, """{"id":"~anthropic/claude-example","reasoning":{"supported_efforts":["high"]}}""")
        val refreshed = router.copy(models = listOf(model)).withFetchedModels(listOf(newer)).models.single()
        val changed = router.modelAssistantReasoning(refreshed, null, null, modelEffort = budget)
        assertNull(changed.budgetRange)
        assertEquals("auto", changed.selected)
        assertFalse(payload(router, refreshed, changed).has("reasoning"))
        val unknownLimit = importModel(router, """{"id":"~anthropic/claude-example",
            "reasoning":{"mandatory":true,"supports_max_tokens":true}}""")
        val missingOutput = router.modelAssistantReasoning(unknownLimit, null, null, modelEffort = budget)
        assertNull(missingOutput.budgetRange)
        assertFalse(payload(router, unknownLimit, missingOutput).has("reasoning"))
    }

    @Test
    fun `required reasoning is never disabled by compaction and limits come from the current catalog`() {
        val sense = provider("sensenova")
        val model = importModel(sense, """{"id":"deepseek-flash","supported_features":["tools"],
            "can_disable_thinking":false,"effort":{"supported_levels":["high"]}}""")
        assertEquals(listOf("auto", "high"), sense.modelAssistantReasoning(model, "high", null).options)
        val summary = AiChatClient().buildRequestBody(
            sense, model, emptyList(), emptyList(), sense.compactionTextParams(model),
            stream = false,
        )
        assertFalse(summary.has("reasoning_effort"))
        assertFalse(summary.has("thinking"))
    }

    @Test
    fun `unknown OpenRouter strengths are bounded by its gateway and dynamic routes have no effort list`() {
        val router = provider("openrouter")
        val model = textModel("provider/model", AiModelInfo(router.modelConfigurationKey(), reasoning = AiModelValue(true)))
        val selection = router.modelAssistantReasoning(model, null, null, modelEffort = "ultra")
        assertFalse("ultra" in selection.options)
        assertEquals("auto", selection.selected)
        val dynamic = router.modelAssistantReasoning(model.copy(id = "openrouter/auto"), null, null, modelEffort = "high")
        assertEquals(listOf("auto"), dynamic.options)
    }

    @Test
    fun `thinking budget and automatic indicator follow the current channel`() {
        val siliconFlow = provider("siliconflow")
        val model = importModel(siliconFlow, """{"id":"Pro/deepseek-ai/DeepSeek-V3.2"}""")
        val selected = siliconFlow.modelAssistantReasoning(model, null, null, modelEffort = reasoningBudgetChoice(512))
        assertEquals(128..32_768, selected.budgetRange)
        val body = payload(siliconFlow, model, selected)
        assertEquals(setOf("enable_thinking", "thinking_budget"), body.keySet())
        assertTrue(body.get("enable_thinking").asBoolean)
        assertEquals(512, body.get("thinking_budget").asInt)

        val router = provider("openrouter")
        val offByDefault = importModel(router, """{"id":"provider/model",
            "reasoning":{"supported_efforts":["high"],"default_enabled":false}}""")
        val auto = router.modelAssistantReasoning(offByDefault, "auto", null)
        assertFalse(auto.active)
        assertFalse(payload(router, offByDefault, auto).has("reasoning"))
        val mandatory = importModel(router, """{"id":"provider/model",
            "reasoning":{"supported_efforts":["high"],"mandatory":true}}""")
        val refreshed = router.copy(models = listOf(offByDefault)).withFetchedModels(listOf(mandatory)).models.single()
        val mandatoryAuto = router.modelAssistantReasoning(refreshed, "auto", null)
        assertTrue(mandatoryAuto.active)
        assertFalse(payload(router, refreshed, mandatoryAuto).has("reasoning"))
    }

    @Test
    fun `models saved with the original DeepSeek keys retain data and manual settings`() {
        val gson = Gson()
        val model = gson.fromJson("""
            {"id":"deepseek-flash","deepSeekModelInfo":{"endpoint":"https://api.deepseek.com/models",
             "contextWindow":{"value":1048576}},"deepSeekModelSettings":{"catalogEndpoint":"https://api.deepseek.com/models",
             "referenceContextWindowTokens":32000,"enabledReasoningEfforts":["max"]}}
        """, AiModel::class.java)
        assertEquals(1_048_576, model.modelInfo?.contextWindow?.value)
        assertEquals(32_000, provider("deepseek").assistantContextWindowLimit(model))
        assertEquals(listOf("max"), model.modelSettings?.enabledReasoningEfforts)
    }

    private fun provider(id: String) = AiDefaultProviders.all().single { it.id == id }

    private fun customProvider() = AiProviderSetting(
        "custom-test", AiProviderType.OPENAI, builtIn = false, name = "Custom", baseUrl = "https://example.invalid/v1",
    )

    private fun json(value: String) = JsonParser.parseString(value).asJsonObject

    private fun textModel(id: String, info: AiModelInfo? = null) = AiModel(
        id, inputModalities = listOf(AiModelModality.TEXT), outputModalities = listOf(AiModelModality.TEXT), modelInfo = info,
    )

    private fun importModel(provider: AiProviderSetting, entry: String): AiModel {
        val listed = OpenAiCompatibleProvider().parseModels(
            json("""{"data":[$entry]}"""), provider.modelConfigurationKey(), provider.openAiCatalogFormat(),
        ).map { AiModelRegistry.enrich(it).withCatalogCapabilities(it.modelInfo) }
        return provider.mergeModelInfo(listed).single()
    }

    private fun requestBody(provider: AiProviderSetting, model: AiModel, reasoning: AiAssistantReasoning, stream: Boolean) =
        AiChatClient().buildRequestBody(
            provider, model, emptyList(), emptyList(), reasoning.applyTo(AiTextParams()), stream,
        )

    private fun payload(provider: AiProviderSetting, model: AiModel, reasoning: AiAssistantReasoning) = JsonObject().apply {
        addReasoningParams(reasoning.applyTo(AiTextParams()), provider.assistantReasoningFields(model.id))
    }
}
