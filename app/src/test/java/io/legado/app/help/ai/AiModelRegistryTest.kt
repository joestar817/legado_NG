package io.legado.app.help.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiModelRegistryTest {

    @Test
    fun inferDeepSeekReasoningFromModelId() {
        val capabilities = AiModelRegistry.capabilities("deepseek-v4-pro")

        assertTrue(AiModelAbility.REASONING in capabilities.abilities)
        assertTrue(AiModelAbility.TOOL in capabilities.abilities)
        assertEquals("thinking", capabilities.reasoning.thinkingParam)
        assertEquals("reasoning_effort", capabilities.reasoning.effortParam)
        assertEquals("reasoning_content", capabilities.reasoning.reasoningOutputField)
    }

    @Test
    fun inferDeepSeekFlashAsVisionReasoningModel() {
        val model = AiModelRegistry.enrich(AiModel(id = "deepseek-v4-flash"))

        assertTrue(AiModelModality.IMAGE in model.inputModalities)
        assertTrue(AiModelAbility.REASONING in model.abilities)
        assertTrue(AiModelAbility.TOOL in model.abilities)
    }

    @Test
    fun inferCurrentDeepSeekFlashIdsWithThinkingAndTools() {
        listOf("deepseek-flash", "DeepSeek-V4.1-Flash").forEach { id ->
            val capabilities = AiModelRegistry.capabilities(id)

            assertTrue(AiModelModality.IMAGE in capabilities.inputModalities)
            assertTrue(AiModelAbility.TOOL in capabilities.abilities)
            assertTrue(AiModelAbility.REASONING in capabilities.abilities)
            assertEquals("thinking", capabilities.reasoning.thinkingParam)
            assertEquals("reasoning_effort", capabilities.reasoning.effortParam)
        }
    }

    @Test
    fun inferMimoReasoningWithoutEffortFromModelId() {
        val capabilities = AiModelRegistry.capabilities("mimo-v2.5-pro")

        assertTrue(AiModelAbility.REASONING in capabilities.abilities)
        assertTrue(AiModelAbility.TOOL in capabilities.abilities)
        assertEquals("thinking", capabilities.reasoning.thinkingParam)
        assertEquals("", capabilities.reasoning.effortParam)
    }

    @Test
    fun inferMimoV26OmniInputAndKeepSpeechModelsSeparate() {
        listOf("mimo-v2.6-flash", "mimo-v2.6-pro", "mimo-v2.6-pro-ultraspeed").forEach { id ->
            val capabilities = AiModelRegistry.capabilities(id)

            assertEquals(
                listOf(
                    AiModelModality.TEXT,
                    AiModelModality.IMAGE,
                    AiModelModality.AUDIO,
                    AiModelModality.VIDEO
                ),
                capabilities.inputModalities
            )
            assertTrue(AiModelAbility.TOOL in capabilities.abilities)
            assertTrue(AiModelAbility.REASONING in capabilities.abilities)
            assertEquals("thinking", capabilities.reasoning.thinkingParam)
        }
        val tts = AiModelRegistry.enrich(AiModel(id = "mimo-v2.6-flash-tts"))
        assertEquals(AiModelType.TTS, tts.type)
        assertFalse(AiModelAbility.TOOL in tts.abilities)
        assertFalse(AiModelAbility.REASONING in tts.abilities)
    }

    @Test
    fun excludeMimoSpeechModelsFromChatCapabilities() {
        val ttsModel = AiModelRegistry.enrich(AiModel(id = "mimo-v2.5-tts"))
        val voiceCloneModel = AiModelRegistry.enrich(AiModel(id = "mimo-v2.5-tts-voiceclone"))
        val asrModel = AiModelRegistry.enrich(AiModel(id = "mimo-v2.5-asr"))

        listOf(ttsModel, voiceCloneModel).forEach { model ->
            assertEquals(AiModelType.TTS, model.type)
            assertEquals(listOf(AiModelModality.TEXT), model.inputModalities)
            assertEquals(listOf(AiModelModality.AUDIO), model.outputModalities)
            assertFalse(AiModelModality.IMAGE in model.inputModalities)
            assertTrue(AiModelAbility.TTS in model.abilities)
            assertFalse(AiModelAbility.TOOL in model.abilities)
            assertFalse(AiModelAbility.REASONING in model.abilities)
        }
        assertEquals(AiModelType.ASR, asrModel.type)
        assertEquals(listOf(AiModelModality.AUDIO), asrModel.inputModalities)
        assertEquals(listOf(AiModelModality.TEXT), asrModel.outputModalities)
        assertTrue(AiModelAbility.ASR in asrModel.abilities)
        assertFalse(AiModelAbility.TTS in asrModel.abilities)
        assertFalse(AiModelAbility.TOOL in asrModel.abilities)
        assertFalse(AiModelAbility.REASONING in asrModel.abilities)
    }

    @Test
    fun inferGeminiVisionAndReasoningFromModelId() {
        val model = AiModelRegistry.enrich(AiModel(id = "gemini-2.5-pro"))

        assertTrue(AiModelModality.IMAGE in model.inputModalities)
        assertTrue(AiModelAbility.REASONING in model.abilities)
        assertTrue(AiModelAbility.TOOL in model.abilities)
    }

    @Test
    fun inferClaudeVisionAndReasoningFromModelIdOnly() {
        val model = AiModelRegistry.enrich(AiModel(id = "claude-sonnet-4-5"))

        assertTrue(AiModelModality.IMAGE in model.inputModalities)
        assertTrue(AiModelAbility.REASONING in model.abilities)
        assertTrue(AiModelAbility.TOOL in model.abilities)
    }

    @Test
    fun inferGeminiImageOutputWithoutToolFromBestModelMatch() {
        val model = AiModelRegistry.enrich(AiModel(id = "gemini-2.5-flash-image"))

        assertEquals(AiModelType.IMAGE, model.type)
        assertTrue(AiModelModality.IMAGE in model.inputModalities)
        assertTrue(AiModelModality.IMAGE in model.outputModalities)
        assertFalse(AiModelAbility.TOOL in model.abilities)
        assertFalse(AiModelAbility.REASONING in model.abilities)
    }

    @Test
    fun excludeGpt5ChatFromGenericGpt5ReasoningRule() {
        val model = AiModelRegistry.enrich(AiModel(id = "gpt-5-chat-latest"))

        assertFalse(AiModelAbility.TOOL in model.abilities)
        assertFalse(AiModelAbility.REASONING in model.abilities)
    }

    @Test
    fun inferDeepSeekChatAsToolOnly() {
        val model = AiModelRegistry.enrich(AiModel(id = "deepseek-chat"))

        assertTrue(AiModelAbility.TOOL in model.abilities)
        assertFalse(AiModelAbility.REASONING in model.abilities)
    }

    @Test
    fun inferOcrAsVisionInputAndTextOutput() {
        val model = AiModelRegistry.enrich(AiModel(id = "deepseek-ai/DeepSeek-OCR"))

        assertEquals(AiModelType.CHAT, model.type)
        assertTrue(AiModelModality.TEXT in model.inputModalities)
        assertTrue(AiModelModality.IMAGE in model.inputModalities)
        assertEquals(listOf(AiModelModality.TEXT), model.outputModalities)
        assertFalse(AiModelAbility.TOOL in model.abilities)
        assertFalse(AiModelAbility.REASONING in model.abilities)
    }

    @Test
    fun unknownModelDoesNotPretendTextCapability() {
        val model = AiModelRegistry.enrich(AiModel(id = "vendor/unknown-model"))

        assertEquals(emptyList<AiModelModality>(), model.inputModalities)
        assertEquals(emptyList<AiModelModality>(), model.outputModalities)
        assertEquals(emptyList<AiModelAbility>(), model.abilities)
    }

    @Test
    fun inferSiliconFlowImageGenerationModelsAsImageType() {
        val zImage = AiModelRegistry.enrich(AiModel(id = "Tongyi-MAI/Z-Image-Turbo"))
        val ernieImage = AiModelRegistry.enrich(AiModel(id = "baidu/ERNIE-Image-Turbo"))
        val qwenImageEdit = AiModelRegistry.enrich(AiModel(id = "Qwen/Qwen-Image-Edit-2509"))
        val kolors = AiModelRegistry.enrich(AiModel(id = "Kwai-Kolors/Kolors"))

        listOf(zImage, ernieImage, kolors).forEach { model ->
            assertEquals(AiModelType.IMAGE, model.type)
            assertEquals(listOf(AiModelModality.TEXT), model.inputModalities)
            assertEquals(listOf(AiModelModality.IMAGE), model.outputModalities)
            assertFalse(AiModelAbility.TOOL in model.abilities)
            assertFalse(AiModelAbility.REASONING in model.abilities)
        }
        assertEquals(AiModelType.IMAGE, qwenImageEdit.type)
        assertTrue(AiModelModality.TEXT in qwenImageEdit.inputModalities)
        assertTrue(AiModelModality.IMAGE in qwenImageEdit.inputModalities)
        assertEquals(listOf(AiModelModality.IMAGE), qwenImageEdit.outputModalities)
    }

    @Test
    fun inferSiliconFlowVideoGenerationModelsAsVideoType() {
        val textToVideo = AiModelRegistry.enrich(AiModel(id = "Wan-AI/Wan2.2-T2V-A14B"))
        val imageToVideo = AiModelRegistry.enrich(AiModel(id = "Wan-AI/Wan2.2-I2V-A14B"))

        assertEquals(AiModelType.VIDEO, textToVideo.type)
        assertEquals(listOf(AiModelModality.TEXT), textToVideo.inputModalities)
        assertEquals(listOf(AiModelModality.VIDEO), textToVideo.outputModalities)
        assertEquals(emptyList<AiModelAbility>(), textToVideo.abilities)

        assertEquals(AiModelType.VIDEO, imageToVideo.type)
        assertTrue(AiModelModality.TEXT in imageToVideo.inputModalities)
        assertTrue(AiModelModality.IMAGE in imageToVideo.inputModalities)
        assertEquals(listOf(AiModelModality.VIDEO), imageToVideo.outputModalities)
        assertEquals(emptyList<AiModelAbility>(), imageToVideo.abilities)
    }

    @Test
    fun inferSiliconFlowEmbeddingAndRerankerModelsAsEmbeddingType() {
        val qwenVlEmbedding = AiModelRegistry.enrich(AiModel(id = "Qwen/Qwen3-VL-Embedding-8B"))
        val qwenVlReranker = AiModelRegistry.enrich(AiModel(id = "Qwen/Qwen3-VL-Reranker-8B"))
        val qwenTextEmbedding = AiModelRegistry.enrich(AiModel(id = "Qwen/Qwen3-Embedding-8B"))
        val bge = AiModelRegistry.enrich(AiModel(id = "BAAI/bge-m3"))
        val bgeReranker = AiModelRegistry.enrich(AiModel(id = "BAAI/bge-reranker-v2-m3"))

        listOf(qwenVlEmbedding, qwenVlReranker).forEach { model ->
            assertEquals(AiModelType.EMBEDDING, model.type)
            assertTrue(AiModelModality.TEXT in model.inputModalities)
            assertTrue(AiModelModality.IMAGE in model.inputModalities)
            assertEquals(listOf(AiModelModality.TEXT), model.outputModalities)
            assertEquals(emptyList<AiModelAbility>(), model.abilities)
        }
        listOf(qwenTextEmbedding, bge, bgeReranker).forEach { model ->
            assertEquals(AiModelType.EMBEDDING, model.type)
            assertEquals(listOf(AiModelModality.TEXT), model.inputModalities)
            assertEquals(listOf(AiModelModality.TEXT), model.outputModalities)
            assertEquals(emptyList<AiModelAbility>(), model.abilities)
        }
    }

    @Test
    fun inferSiliconFlowSpeechModelsAsSpeechTypes() {
        val teleSpeech = AiModelRegistry.enrich(AiModel(id = "TeleAI/TeleSpeechASR"))
        val senseVoice = AiModelRegistry.enrich(AiModel(id = "FunAudioLLM/SenseVoiceSmall"))
        val cosyVoice = AiModelRegistry.enrich(AiModel(id = "FunAudioLLM/CosyVoice2-0.5B"))
        val mossTts = AiModelRegistry.enrich(AiModel(id = "fnlp/MOSS-TTSD-v0.5"))

        listOf(teleSpeech, senseVoice).forEach { model ->
            assertEquals(AiModelType.ASR, model.type)
            assertEquals(listOf(AiModelModality.AUDIO), model.inputModalities)
            assertEquals(listOf(AiModelModality.TEXT), model.outputModalities)
            assertEquals(listOf(AiModelAbility.ASR), model.abilities)
        }
        listOf(cosyVoice, mossTts).forEach { model ->
            assertEquals(AiModelType.TTS, model.type)
            assertEquals(listOf(AiModelModality.TEXT), model.inputModalities)
            assertEquals(listOf(AiModelModality.AUDIO), model.outputModalities)
            assertEquals(listOf(AiModelAbility.TTS), model.abilities)
        }
    }

    @Test
    fun inferSiliconFlowVisionAndReasoningChatModels() {
        val glmVision = AiModelRegistry.enrich(AiModel(id = "zai-org/GLM-4.5V"))
        val qwenVlThinking = AiModelRegistry.enrich(AiModel(id = "Qwen/Qwen3-VL-32B-Thinking"))
        val qwenOmni = AiModelRegistry.enrich(AiModel(id = "Qwen/Qwen3-Omni-30B-A3B-Instruct"))

        listOf(glmVision, qwenVlThinking, qwenOmni).forEach { model ->
            assertEquals(AiModelType.CHAT, model.type)
            assertTrue(AiModelModality.TEXT in model.inputModalities)
            assertTrue(AiModelModality.IMAGE in model.inputModalities)
            assertTrue(AiModelAbility.TOOL in model.abilities)
            assertTrue(AiModelAbility.REASONING in model.abilities)
        }
        assertTrue(AiModelModality.AUDIO in qwenOmni.inputModalities)
        assertTrue(AiModelModality.AUDIO in qwenOmni.outputModalities)
    }

    @Test
    fun inferSiliconFlowNewTextReasoningModels() {
        val models = listOf(
            "zai-org/GLM-5.2",
            "nex-agi/Nex-N2-Pro",
            "inclusionAI/Ling-flash-2.0",
            "ByteDance-Seed/Seed-OSS-36B-Instruct",
            "tencent/Hunyuan-A13B-Instruct"
        ).map { AiModelRegistry.enrich(AiModel(id = it)) }

        models.forEach { model ->
            assertEquals(AiModelType.CHAT, model.type)
            assertEquals(listOf(AiModelModality.TEXT), model.inputModalities)
            assertEquals(listOf(AiModelModality.TEXT), model.outputModalities)
            assertTrue(AiModelAbility.TOOL in model.abilities)
            assertTrue(AiModelAbility.REASONING in model.abilities)
        }
    }

    @Test
    fun distinguishNewChatModelsFromProtocolLimitedGpt6Tools() {
        val gpt6 = AiModelRegistry.enrich(AiModel(id = "gpt-6-astra"))
        assertEquals(AiModelType.CHAT, gpt6.type)
        assertTrue(AiModelModality.IMAGE in gpt6.inputModalities)
        assertTrue(AiModelAbility.REASONING in gpt6.abilities)
        assertFalse(AiModelAbility.TOOL in gpt6.abilities)

        listOf(
            "claude-fable-5-1",
            "claude-opus-5-5",
            "qwen3.8-flash",
            "doubao-seed-2-1-pro-260915",
            "grok-4.7",
            "kimi-k3",
            "stepfun/step-5-preview",
            "ZHIPU/GLM-5.3-FlashX",
            "nex-agi/Nex-N2.5-Pro",
            "Ling-3.0-flash-VL"
        ).forEach { id ->
            val model = AiModelRegistry.enrich(AiModel(id = id))
            assertEquals(id, AiModelType.CHAT, model.type)
            assertTrue(id, AiModelModality.IMAGE in model.inputModalities)
            assertTrue(id, AiModelAbility.TOOL in model.abilities)
            assertTrue(id, AiModelAbility.REASONING in model.abilities)
        }
    }

    @Test
    fun keepNewEmbeddingRerankAndSpeechModelsOutOfChat() {
        listOf("qwen3.7-text-embedding-flash", "qwen3.7-text-rerank").forEach { id ->
            val model = AiModelRegistry.enrich(AiModel(id = id))
            assertEquals(AiModelType.EMBEDDING, model.type)
            assertFalse(AiModelAbility.TOOL in model.abilities)
            assertFalse(AiModelAbility.REASONING in model.abilities)
        }
        listOf("gemini-3.5-transcribe-live", "gpt-live-transcribe", "grok-voice-transcribe-2.0").forEach { id ->
            val model = AiModelRegistry.enrich(AiModel(id = id))
            assertEquals(AiModelType.ASR, model.type)
            assertEquals(listOf(AiModelAbility.ASR), model.abilities)
        }
        listOf("gemini-3.8-flash-tts", "gemini-3.8-flash-lite-tts").forEach { id ->
            val model = AiModelRegistry.enrich(AiModel(id = id))
            assertEquals(AiModelType.TTS, model.type)
            assertEquals(listOf(AiModelAbility.TTS), model.abilities)
        }
    }

    @Test
    fun classifyNewImageAndVideoModelsByOutputType() {
        listOf("qwen-mt-image-2.0", "sensenova-u1.5-lite", "Hy-Image-3.5-preview").forEach { id ->
            val model = AiModelRegistry.enrich(AiModel(id = id))
            assertEquals(AiModelType.IMAGE, model.type)
            assertEquals(listOf(AiModelModality.IMAGE), model.outputModalities)
        }
        listOf("wan3.0-video", "wan3.0-video-prime", "MiniMax-H3").forEach { id ->
            val model = AiModelRegistry.enrich(AiModel(id = id))
            assertEquals(AiModelType.VIDEO, model.type)
            assertEquals(listOf(AiModelModality.VIDEO), model.outputModalities)
            assertFalse(AiModelAbility.TOOL in model.abilities)
        }
    }
}
