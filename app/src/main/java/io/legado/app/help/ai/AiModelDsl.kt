package io.legado.app.help.ai

internal interface AiModelSelector {
    fun match(modelId: String): Boolean
}

internal class AiModelDefinition(
    private val matcher: AiTokenMatcher,
    val type: AiModelType?,
    val inputModalities: Set<AiModelModality>,
    val outputModalities: Set<AiModelModality>,
    val abilities: Set<AiModelAbility>
) : AiModelSelector {

    override fun match(modelId: String): Boolean {
        val tokens = tokenizeModelId(modelId)
        return matcher.score(modelId, tokens) != null
    }

    fun matchScore(modelId: String): Int? {
        val tokens = tokenizeModelId(modelId)
        return matcher.score(modelId, tokens)
    }
}

internal class AiModelGroup internal constructor(
    private val members: List<AiModelSelector>
) : AiModelSelector {

    override fun match(modelId: String): Boolean {
        return members.any { it.match(modelId) }
    }
}

internal fun defineAiModel(block: AiModelDefinitionBuilder.() -> Unit): AiModelDefinition {
    return AiModelDefinitionBuilder().apply(block).build()
}

internal fun defineAiModelGroup(block: AiModelGroupBuilder.() -> Unit): AiModelGroup {
    return AiModelGroupBuilder().apply(block).build()
}

internal fun aiTokenRegex(pattern: String): AiTokenSpec {
    return AiTokenRegex(pattern.toRegex(RegexOption.IGNORE_CASE))
}

internal class AiModelDefinitionBuilder {

    private val matchers = mutableListOf<AiTokenMatcher>()
    private var type: AiModelType? = null
    private val inputModalities = mutableSetOf(AiModelModality.TEXT)
    private val outputModalities = mutableSetOf(AiModelModality.TEXT)
    private val abilities = mutableSetOf<AiModelAbility>()

    fun tokens(vararg specs: String) {
        matchers += AiTokenSequenceMatcher(specs.map(::parseAiTokenSpec))
    }

    fun tokens(vararg specs: AiTokenSpec) {
        matchers += AiTokenSequenceMatcher(specs.toList())
    }

    fun notTokens(vararg specs: String) {
        matchers += AiNotTokenSequenceMatcher(specs.map(::parseAiTokenSpec))
    }

    fun notTokens(vararg specs: AiTokenSpec) {
        matchers += AiNotTokenSequenceMatcher(specs.toList())
    }

    fun exact(id: String) {
        matchers += AiExactIdMatcher(id)
    }

    fun type(type: AiModelType) {
        this.type = type
    }

    fun input(vararg modalities: AiModelModality) {
        inputModalities.clear()
        inputModalities.addAll(modalities)
    }

    fun output(vararg modalities: AiModelModality) {
        outputModalities.clear()
        outputModalities.addAll(modalities)
    }

    fun ability(vararg abilities: AiModelAbility) {
        this.abilities.addAll(abilities)
    }

    fun build(): AiModelDefinition {
        val matcher = when {
            matchers.isEmpty() -> AiMatchNone
            matchers.size == 1 -> matchers.first()
            else -> AiAndMatcher(matchers.toList())
        }
        return AiModelDefinition(
            matcher = matcher,
            type = type,
            inputModalities = inputModalities.toSet(),
            outputModalities = outputModalities.toSet(),
            abilities = abilities.toSet()
        )
    }
}

internal class AiModelGroupBuilder {

    private val members = mutableListOf<AiModelSelector>()

    fun add(vararg models: AiModelSelector) {
        members.addAll(models)
    }

    fun build(): AiModelGroup {
        return AiModelGroup(members.toList())
    }
}

internal interface AiTokenSpec {
    fun matches(token: String): Boolean
}

private data class AiTokenAlternatives(
    val options: Set<String>
) : AiTokenSpec {

    override fun matches(token: String): Boolean {
        return options.contains(token)
    }
}

private data class AiTokenRegex(
    val regex: Regex
) : AiTokenSpec {

    override fun matches(token: String): Boolean {
        return regex.matches(token)
    }
}

internal interface AiTokenMatcher {
    fun score(modelId: String, tokens: List<String>): Int?
}

private object AiMatchNone : AiTokenMatcher {
    override fun score(modelId: String, tokens: List<String>): Int? {
        return null
    }
}

private class AiAndMatcher(
    private val matchers: List<AiTokenMatcher>
) : AiTokenMatcher {

    override fun score(modelId: String, tokens: List<String>): Int? {
        var total = 0
        for (matcher in matchers) {
            val score = matcher.score(modelId, tokens) ?: return null
            total += score
        }
        return total
    }
}

private class AiExactIdMatcher(
    private val id: String
) : AiTokenMatcher {

    override fun score(modelId: String, tokens: List<String>): Int? {
        return if (modelId.equals(id, ignoreCase = true)) {
            EXACT_ID_BONUS + tokens.size
        } else {
            null
        }
    }
}

private class AiTokenSequenceMatcher(
    private val specs: List<AiTokenSpec>
) : AiTokenMatcher {

    override fun score(modelId: String, tokens: List<String>): Int? {
        if (specs.isEmpty()) return null
        var specIndex = 0
        for (token in tokens) {
            if (specs[specIndex].matches(token)) {
                specIndex += 1
                if (specIndex == specs.size) return specs.size
            }
        }
        return null
    }
}

private class AiNotTokenSequenceMatcher(
    specs: List<AiTokenSpec>
) : AiTokenMatcher {

    private val matcher = AiTokenSequenceMatcher(specs)

    override fun score(modelId: String, tokens: List<String>): Int? {
        return if (matcher.score(modelId, tokens) == null) 0 else null
    }
}

private fun parseAiTokenSpec(spec: String): AiTokenSpec {
    val options = spec.split('|')
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() }
        .toSet()
    return AiTokenAlternatives(options)
}

private const val EXACT_ID_BONUS = 1000

private fun tokenizeModelId(modelId: String): List<String> {
    val tokens = mutableListOf<String>()
    val input = modelId.lowercase()
    var index = 0
    while (index < input.length) {
        val ch = input[index]
        when {
            ch.isLetter() -> {
                val start = index
                index += 1
                while (index < input.length && input[index].isLetter()) {
                    index += 1
                }
                tokens.add(input.substring(start, index))
            }

            ch.isDigit() -> {
                val start = index
                index += 1
                while (index < input.length && input[index].isDigit()) {
                    index += 1
                }
                tokens.add(input.substring(start, index))
            }

            else -> {
                tokens.add(ch.toString())
                index += 1
            }
        }
    }
    return tokens
}
