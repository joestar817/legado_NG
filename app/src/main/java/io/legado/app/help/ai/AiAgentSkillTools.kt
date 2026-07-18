package io.legado.app.help.ai

import com.google.gson.JsonObject

/** Agent Kernel 内置只读工具，不属于 App MCP 业务接口。 */
object AiAgentSkillTools {

    const val USE_SKILL = "use_skill"

    fun toolDefinition(
        activeSkillId: String,
        contentHash: String,
        availableSkillIds: Collection<String> = listOf(activeSkillId)
    ): JsonObject? {
        if (!isAvailable(activeSkillId, contentHash, availableSkillIds)) return null
        val available = availableSkillIds.distinct().sorted()
        return JsonObject().apply {
            addProperty("name", USE_SKILL)
            addProperty(
                "description",
                "Load one available Skill's instructions or a linked resource. " +
                    "Available skills: ${available.joinToString()}. " +
                    "Only use paths copied from Markdown links in that Skill's SKILL.md; never guess paths."
            )
            add("inputSchema", JsonObject().apply {
                addProperty("type", "object")
                add("properties", JsonObject().apply {
                    add("name", JsonObject().apply {
                        addProperty("type", "string")
                        addProperty("description", "One name from the available skills list")
                    })
                    add("path", JsonObject().apply {
                        addProperty("type", "string")
                        addProperty(
                            "description",
                            "Optional linked relative path inside the skill package. Omit to reload SKILL.md."
                        )
                    })
                })
                add("required", com.google.gson.JsonArray().apply { add("name") })
                addProperty("additionalProperties", false)
            })
        }
    }

    fun call(
        name: String,
        arguments: JsonObject,
        activeSkillId: String,
        contentHash: String,
        availableSkillIds: Collection<String> = listOf(activeSkillId)
    ): JsonObject? {
        if (name != USE_SKILL) return null
        require(isAvailable(activeSkillId, contentHash, availableSkillIds)) {
            "当前会话没有可读取的 Skill Package 集合"
        }
        val requestedName = arguments.get("name")?.takeIf { it.isJsonPrimitive }
            ?.asString?.trim().orEmpty()
        require(requestedName in availableSkillIds) {
            "Skill '$requestedName' 不可用；当前允许读取：${availableSkillIds.joinToString()}"
        }
        val path = arguments.get("path")?.takeIf { it.isJsonPrimitive }?.asString
        val resource = AiSkillPackageRegistry.readTextFromSet(
            skillId = requestedName,
            relativePath = path,
            availableSkillIds = availableSkillIds,
            expectedSetContentHash = contentHash
        )
        return JsonObject().apply {
            addProperty("ok", true)
            addProperty("skill", resource.skillId)
            addProperty("path", resource.path)
            addProperty("content_hash", resource.contentHash)
            addProperty("content", resource.content)
        }
    }

    private fun isAvailable(
        activeSkillId: String,
        contentHash: String,
        availableSkillIds: Collection<String>
    ): Boolean {
        if (activeSkillId.isBlank() || contentHash.isBlank()) return false
        if (activeSkillId !in availableSkillIds) return false
        return AiSkillPackageRegistry.systemPackageSet(availableSkillIds)?.contentHash == contentHash
    }
}
