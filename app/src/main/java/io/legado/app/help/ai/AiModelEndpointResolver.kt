package io.legado.app.help.ai

object AiModelEndpointResolver {

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

    fun candidates(baseUrl: String, modelsUrl: String = ""): List<String> {
        val explicit = modelsUrl.trim()
        if (explicit.isNotBlank()) {
            return listOf(explicit)
        }

        val base = baseUrl.trimEndSlash()
        if (base.isBlank()) {
            return emptyList()
        }

        val candidates = linkedSetOf<String>()
        if (base.endsWithVersionSegment()) {
            candidates += "$base/models"
            if (!base.endsWith("/v1", ignoreCase = true)) {
                candidates += "$base/v1/models"
            }
        } else {
            candidates += "$base/v1/models"
        }

        knownCompatSuffixes.forEach { suffix ->
            if (base.endsWith(suffix, ignoreCase = true)) {
                val root = base.dropLast(suffix.length).trimEndSlash()
                if (root.isNotBlank()) {
                    candidates += "$root/v1/models"
                    candidates += "$root/models"
                }
            }
        }

        return candidates.toList()
    }

    private fun String.endsWithVersionSegment(): Boolean {
        return Regex(""".*/v\d+$""", RegexOption.IGNORE_CASE).matches(this)
    }

}
