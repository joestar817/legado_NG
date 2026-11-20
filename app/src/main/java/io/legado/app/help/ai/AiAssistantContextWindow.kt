package io.legado.app.help.ai

private const val CONTEXT_WINDOW_UNIT = 32_000
private const val REGULAR_INTERVALS = 4
private val fallbackContextWindows = listOf(
    32_000, 64_000, 128_000, 256_000, 512_000, 1_000_000,
)

internal fun AiProviderSetting.assistantContextWindowLimit(model: AiModel?): Int? {
    model ?: return null
    val info = effectiveModelInfo(model)
    val limit = model.modelSettingsFor(info.endpoint)?.referenceContextWindow(info)
        ?: info.contextWindow?.value
    return limit?.takeIf { it > 0 }
}

internal fun capAssistantContextWindow(tokens: Int, limit: Int?): Int =
    minOf(tokens, limit ?: fallbackContextWindows.last())

internal fun assistantContextWindowLabel(tokens: Int): String = when {
    tokens >= 1_000_000 -> "${tokens / 1_000_000}M"
    tokens >= 1_000 -> "${tokens / 1_000}K"
    else -> tokens.toString()
}

internal fun assistantContextWindowOptions(limit: Int?): List<Int> {
    if (limit == null) return fallbackContextWindows

    val unitsBelowLimit = (limit - 1) / CONTEXT_WINDOW_UNIT
    val unitsPerStep = ((unitsBelowLimit + REGULAR_INTERVALS - 1) / REGULAR_INTERVALS).coerceAtLeast(1)
    val stride = unitsPerStep * CONTEXT_WINDOW_UNIT
    val regularOptions = buildList {
        if (limit > CONTEXT_WINDOW_UNIT) add(CONTEXT_WINDOW_UNIT)
        for (tokens in stride until limit step stride) {
            if (tokens > CONTEXT_WINDOW_UNIT) add(tokens)
        }
    }
    val endpointLabel = assistantContextWindowLabel(limit)
    return regularOptions
        .filterNot { assistantContextWindowLabel(it) == endpointLabel }
        .distinctBy(::assistantContextWindowLabel) + limit
}

internal fun assistantContextWindowPosition(options: List<Int>, tokens: Int): Float {
    if (options.size == 1) return (tokens.toFloat() / options.single()).coerceIn(0f, 1f)
    val upperIndex = options.indexOfFirst { it >= tokens }
    if (upperIndex == -1) return options.lastIndex.toFloat()
    if (upperIndex == 0) return 0f
    val lower = options[upperIndex - 1]
    val upper = options[upperIndex]
    return upperIndex - 1 + (tokens - lower).toFloat() / (upper - lower)
}
