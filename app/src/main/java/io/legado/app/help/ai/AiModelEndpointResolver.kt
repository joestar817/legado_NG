package io.legado.app.help.ai

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object AiModelEndpointResolver {

    private val deepSeekModels = "https://api.deepseek.com/models".toHttpUrl()

    private val knownCompatSuffixes = listOf(
        "/api/claudecode",
        "/api/anthropic",
        "/apps/anthropic",
        "/api/coding",
        "/claudecode",
        "/anthropic",
        "/step_plan",
        "/coding",
        "/claude"
    )

    fun candidates(setting: AiProviderSetting): List<String> {
        if (setting.useCustomModelsUrl) {
            return listOf(buildAiApiEndpoint(setting.baseUrl, setting.modelsUrl))
        }
        return candidates(setting.baseUrl, setting.type)
    }

    fun candidates(baseUrl: String, type: AiProviderType = AiProviderType.OPENAI): List<String> {
        val parsedBase = baseUrl.trim().toHttpUrlOrNull()
            ?.takeUnless { it.hasUserInfo() || it.query != null || it.fragment != null }
            ?: return emptyList()
        val path = parsedBase.encodedPath.trimEnd('/')
        fun atPath(value: String) = parsedBase.newBuilder().encodedPath(value).build().toString()

        val candidates = linkedSetOf<String>()
        when {
            type == AiProviderType.CLAUDE -> {
                candidates += atPath("$path/models")
            }
            parsedBase.sameOriginAs(deepSeekModels) -> {
                candidates += deepSeekModels.toString()
            }
            path.endsWithVersionSegment() -> {
                candidates += atPath("$path/models")
                if (!path.endsWith("/v1", ignoreCase = true)) {
                    candidates += atPath("$path/v1/models")
                }
            }
            else -> {
                candidates += atPath("$path/v1/models")
            }
        }

        knownCompatSuffixes.forEach { suffix ->
            if (path.endsWith(suffix, ignoreCase = true)) {
                val root = path.dropLast(suffix.length).trimEnd('/')
                candidates += atPath("$root/v1/models")
                candidates += atPath("$root/models")
            }
        }

        return candidates.toList()
    }

    private fun String.endsWithVersionSegment(): Boolean {
        return Regex(""".*/v\d+$""", RegexOption.IGNORE_CASE).matches(this)
    }

}
