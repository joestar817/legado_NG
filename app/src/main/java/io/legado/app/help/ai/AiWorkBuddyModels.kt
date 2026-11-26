package io.legado.app.help.ai

import com.google.gson.JsonObject
import io.legado.app.R

private val nonChatTags = setOf("text-to-image", "image-to-image", "text-to-video", "image-to-video")
private val nonChatPrefixes = listOf("nes-", "completion-", "codewise-")
private val creditMultiplierPattern = Regex("""x([0-9]+(?:\.[0-9]+)?)(?:\s+credits)?""")

internal fun parseWorkBuddyModels(data: JsonObject, modelSource: String): List<AiModel> {
    val agent = data.arrayOrNull("agents")?.filterIsInstance<JsonObject>()
        ?.firstOrNull { it.workBuddyText("name") == "cli" } ?: throw invalidWorkBuddyResponse()
    val offeredIds = agent.arrayOrNull("models")?.mapNotNull {
        it.takeIf { entry -> entry.isJsonPrimitive && entry.asJsonPrimitive.isString }?.asString
    }?.distinct() ?: throw invalidWorkBuddyResponse()
    val details = data.arrayOrNull("models")?.filterIsInstance<JsonObject>()
        ?.associateBy { it.workBuddyText("id") } ?: throw invalidWorkBuddyResponse()
    val models = offeredIds.mapNotNull { id ->
        val item = details[id] ?: return@mapNotNull null
        val disabled = item.get("disabled")?.takeIf { it.isJsonPrimitive }?.asBoolean == true
        val output = item.workBuddyLong("maxOutputTokens")?.takeIf { it in 1..Int.MAX_VALUE }?.toInt()
        val tags = item.arrayOrNull("tags")?.mapNotNull {
            it.takeIf { tag -> tag.isJsonPrimitive && tag.asJsonPrimitive.isString }?.asString
        }.orEmpty()
        if (disabled || nonChatPrefixes.any { id.startsWith(it, ignoreCase = true) } ||
            (output != null && output <= 256) || tags.any { it in nonChatTags }
        ) return@mapNotNull null
        val name = item.workBuddyText("name") ?: id
        val vendor = item.workBuddyText("vendor")
        val context = item.workBuddyLong("maxInputTokens")?.takeIf { it in 1..Int.MAX_VALUE }?.toInt()
        val reasoning = item.get("supportsReasoning")?.takeIf { it.isJsonPrimitive }?.asBoolean == true
        val text = listOf(AiModelModality.TEXT)
        AiModel(
            id = id,
            name = name,
            ownedBy = vendor.orEmpty(),
            inputModalities = text,
            outputModalities = text,
            abilities = buildList {
                add(AiModelAbility.TOOL)
                if (reasoning) add(AiModelAbility.REASONING)
            },
            modelInfo = AiModelInfo(
                endpoint = modelSource,
                name = AiModelValue(name),
                publisher = vendor?.let { AiModelValue(it) },
                contextWindow = context?.let { AiModelValue(it) },
                maxOutputTokens = output?.let { AiModelValue(it) },
                inputModalities = AiModelValue(listOf("text")),
                outputModalities = AiModelValue(listOf("text")),
                toolCalling = AiModelValue(true),
                reasoning = AiModelValue(reasoning),
                creditMultiplier = item.workBuddyText("credits")?.let(::parseCreditMultiplier),
            ),
        )
    }
    if (models.isEmpty()) throw AiWorkBuddyException(R.string.ai_workbuddy_no_models)
    return models
}

private fun parseCreditMultiplier(credits: String): String? {
    val amount = creditMultiplierPattern.matchEntire(credits)?.groupValues?.get(1) ?: return null
    val multiplier = amount.toBigDecimal().stripTrailingZeros()
    return multiplier.setScale(maxOf(2, multiplier.scale())).toPlainString()
}

internal fun AiProviderSetting.workBuddyStoryboardOutputLimit(modelId: String): Int? {
    if (!isWorkBuddyAccountProvider()) return null
    return models.firstOrNull { it.id == modelId }?.modelInfo?.maxOutputTokens?.value?.takeIf { it > 0 }
}
