package io.legado.app.help.ai

import io.legado.app.data.appDb
import io.legado.app.data.entities.AiSkill
import splitties.init.appCtx
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Locale

enum class AiSkillScope {
    APP,
    AGENT
}

data class AiSkillDefinition(
    val id: String,
    val name: String,
    val summary: String,
    val scope: AiSkillScope,
    val prompt: String,
    val suggestions: List<String> = emptyList(),
    val editablePrompt: AiPromptStore.Prompt? = null,
    val builtIn: Boolean = false,
    val enabled: Boolean = true,
    val userModified: Boolean = false,
    val content: String = prompt
)

class AiSkillExistsException(
    val skillId: String,
    skillName: String
) : IllegalArgumentException("Skill 已存在：$skillName")

object AiSkillRegistry {

    const val SKILL_PARAGRAPH_PURIFY = "paragraph_purify"
    const val SKILL_CHAPTER_PURIFY = "chapter_purify"
    const val SKILL_BOOKSHELF_MANAGEMENT = "bookshelf_management"
    const val SKILL_CHARACTER_CARD_GENERATE = "character_card_generate"
    const val SKILL_BOOK_SCAN = "book_scan"
    private const val MAX_SKILL_FILE_LENGTH = 512 * 1024
    private const val BUILT_IN_SKILL_ASSET_DIR = "skills"

    fun all(): List<AiSkillDefinition> {
        ensureBuiltInSkills()
        return appDb.aiSkillDao.all.map { it.toDefinition() }
    }

    fun agentSkills(): List<AiSkillDefinition> {
        ensureBuiltInSkills()
        return appDb.aiSkillDao.all
            .filter { it.scope == AiSkillScope.AGENT.name }
            .map { it.toDefinition() }
    }

    fun get(id: String): AiSkillDefinition? {
        return all().firstOrNull { it.id == id }
    }

    fun saveSkillContent(skillId: String?, content: String): AiSkill {
        ensureBuiltInSkills()
        val current = skillId?.let { appDb.aiSkillDao.get(it) }
        require(current != null) { "Skill 不存在" }
        val parsed = parseSkillContent(content)
        require(parsed.id == current.id) { "已绑定功能的 Skill 不能修改 id" }
        validateUniqueSkill(parsed, current.id)
        val now = System.currentTimeMillis()
        val skill = parsed.copy(
            id = current.id,
            scope = current.scope,
            builtIn = current.builtIn,
            enabled = current.enabled,
            userModified = true,
            customOrder = current.customOrder,
            createdAt = current.createdAt.takeIf { it > 0 } ?: now,
            updatedAt = now
        )
        appDb.aiSkillDao.insert(skill)
        return skill
    }

    fun importFromText(content: String, overwriteExisting: Boolean = false): AiSkill {
        ensureBuiltInSkills()
        val parsed = parseSkillContent(content)
        val current = appDb.aiSkillDao.get(parsed.id)
        validateUniqueSkill(parsed, current?.id)
        if (current != null && !overwriteExisting) {
            throw AiSkillExistsException(current.id, current.name)
        }
        val metadata = parseFrontmatter(parsed.content)
        val scope = metadata["scope"]
            ?.trim()
            ?.uppercase(Locale.ROOT)
            ?.takeIf { value -> AiSkillScope.entries.any { it.name == value } }
            ?: AiSkillScope.AGENT.name
        val now = System.currentTimeMillis()
        val skill = parsed.copy(
            id = current?.id ?: parsed.id,
            scope = current?.scope ?: scope,
            builtIn = current?.builtIn == true,
            enabled = current?.enabled ?: true,
            userModified = current?.builtIn == true,
            customOrder = current?.customOrder ?: nextCustomOrder(),
            createdAt = current?.createdAt?.takeIf { it > 0 } ?: now,
            updatedAt = now
        )
        appDb.aiSkillDao.insert(skill)
        return skill
    }

    fun importFromUrl(url: String, overwriteExisting: Boolean = false): AiSkill {
        return importFromText(downloadSkillContent(url), overwriteExisting)
    }

    fun resetBuiltIn(id: String): Boolean {
        val default = builtInSkills().firstOrNull { it.id == id } ?: return false
        val current = appDb.aiSkillDao.get(id)
        val now = System.currentTimeMillis()
        appDb.aiSkillDao.insert(
            default.copy(
                enabled = current?.enabled ?: true,
                userModified = false,
                customOrder = current?.customOrder ?: default.customOrder,
                createdAt = current?.createdAt?.takeIf { it > 0 } ?: now,
                updatedAt = now
            )
        )
        return true
    }

    fun deleteSkill(id: String): Boolean {
        ensureBuiltInSkills()
        return appDb.aiSkillDao.deleteCustom(id) > 0
    }

    fun exportContent(skill: AiSkillDefinition): String {
        return skill.content
    }

    fun parseSkillContent(content: String): AiSkill {
        require(content.length <= MAX_SKILL_FILE_LENGTH) { "Skill 文件过大" }
        val trimmed = content.trim()
        val frontmatter = parseFrontmatter(trimmed)
        val id = frontmatter["id"]?.trim().orEmpty()
        val name = frontmatter["name"]?.trim().orEmpty()
        val description = frontmatter["description"]?.trim().orEmpty()
        require(id.isNotBlank()) { "Skill 缺少 id 字段" }
        require(name.isNotBlank()) { "Skill 缺少 name 字段" }
        require(description.isNotBlank()) { "Skill 缺少 description 字段" }
        return AiSkill(
            id = normalizeId(id),
            name = name,
            description = description,
            content = trimmed,
            scope = AiSkillScope.AGENT.name,
            builtIn = false,
            enabled = true
        )
    }

    fun skillPrompt(id: String): String? {
        ensureBuiltInSkills()
        return appDb.aiSkillDao.get(id)?.toDefinition()?.prompt
    }

    private fun ensureBuiltInSkills() {
        builtInSkills().forEach { defaultSkill ->
            val existing = appDb.aiSkillDao.get(defaultSkill.id)
            if (existing == null) {
                appDb.aiSkillDao.insert(defaultSkill.withLegacyPromptIfNeeded())
            } else if (!existing.builtIn) {
                appDb.aiSkillDao.insert(existing.copy(builtIn = true))
            } else if (!existing.userModified && shouldUpgradeBuiltInSkill(existing, defaultSkill)) {
                appDb.aiSkillDao.insert(
                    defaultSkill.copy(
                        enabled = existing.enabled,
                        userModified = false,
                        customOrder = existing.customOrder,
                        createdAt = existing.createdAt,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    private fun shouldUpgradeBuiltInSkill(existing: AiSkill, defaultSkill: AiSkill): Boolean {
        val currentVersion = parseFrontmatter(existing.content)["version"].orEmpty()
        val defaultVersion = parseFrontmatter(defaultSkill.content)["version"].orEmpty()
        val currentVersionNumber = currentVersion.toIntOrNull()
        val defaultVersionNumber = defaultVersion.toIntOrNull()
        if (currentVersionNumber != null && defaultVersionNumber != null && currentVersionNumber < defaultVersionNumber) {
            return true
        }
        if (currentVersion.isBlank() && existing.content.contains("不要只给固定按钮选项")) {
            return true
        }
        if (currentVersion == "2" && existing.content.contains("书架不是只有“分组整理”")) {
            return true
        }
        return false
    }

    private fun builtInSkills(): List<AiSkill> {
        val names = appCtx.assets.list(BUILT_IN_SKILL_ASSET_DIR).orEmpty()
        return names
            .filter { it.endsWith(".md", ignoreCase = true) }
            .sorted()
            .mapIndexedNotNull { index, fileName ->
                runCatching {
                    val content = appCtx.assets.open("$BUILT_IN_SKILL_ASSET_DIR/$fileName")
                        .bufferedReader()
                        .use { it.readText() }
                    parseBuiltInSkillContent(content, index)
                }.getOrNull()
            }
    }

    private fun AiSkill.toDefinition(): AiSkillDefinition {
        val metadata = parseFrontmatter(content)
        val body = extractBody(content)
        val suggestions = metadata["suggestions"]
            ?.split('|', ',', '，')
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()
        return AiSkillDefinition(
            id = id,
            name = name,
            summary = description,
            scope = runCatching { AiSkillScope.valueOf(scope) }.getOrDefault(AiSkillScope.AGENT),
            prompt = body,
            suggestions = suggestions,
            builtIn = builtIn,
            enabled = enabled,
            userModified = userModified,
            content = content
        )
    }

    private fun parseFrontmatter(content: String): Map<String, String> {
        if (!content.startsWith("---")) {
            return emptyMap()
        }
        val end = Regex("""\r?\n---(?:\r?\n|$)""").find(content, startIndex = 3)
            ?: return emptyMap()
        return content.substring(3, end.range.first).trim()
            .lineSequence()
            .mapNotNull { line ->
                val index = line.indexOf(':')
                if (index <= 0) return@mapNotNull null
                val key = line.substring(0, index).trim()
                val value = line.substring(index + 1).trim().removeSurrounding("\"")
                key.takeIf { it.isNotBlank() }?.let { it to value }
            }
            .toMap()
    }

    private fun parseBuiltInSkillContent(content: String, order: Int): AiSkill {
        val trimmed = content.trim()
        val frontmatter = parseFrontmatter(trimmed)
        val id = frontmatter["id"]?.trim().orEmpty()
        val name = frontmatter["name"]?.trim().orEmpty()
        val description = frontmatter["description"]?.trim().orEmpty()
        require(id.isNotBlank()) { "built-in Skill 缺少 id 字段" }
        require(name.isNotBlank()) { "built-in Skill 缺少 name 字段" }
        require(description.isNotBlank()) { "built-in Skill 缺少 description 字段" }
        val scope = frontmatter["scope"]
            ?.trim()
            ?.uppercase(Locale.ROOT)
            ?.takeIf { value -> AiSkillScope.entries.any { it.name == value } }
            ?: AiSkillScope.AGENT.name
        return AiSkill(
            id = normalizeId(id),
            name = name,
            description = description,
            content = trimmed,
            scope = scope,
            builtIn = true,
            enabled = true,
            customOrder = order
        )
    }

    private fun extractBody(content: String): String {
        if (!content.startsWith("---")) {
            return content
        }
        val end = Regex("""\r?\n---(?:\r?\n|$)""").find(content, startIndex = 3)
            ?: return content
        return content.substring(end.range.last + 1).trimStart('\r', '\n')
    }

    private fun normalizeId(value: String): String {
        return value.trim().lowercase(Locale.ROOT)
            .replace(Regex("""[^a-z0-9_\-\u4e00-\u9fa5]+"""), "_")
            .trim('_', '-')
            .ifBlank { "custom_skill" }
    }

    private fun validateUniqueSkill(skill: AiSkill, currentId: String?) {
        val duplicateId = appDb.aiSkillDao.get(skill.id)
        require(duplicateId == null || duplicateId.id == currentId) { "Skill id 已存在：${skill.id}" }
        val duplicateName = appDb.aiSkillDao.getByName(skill.name)
        require(duplicateName == null || duplicateName.id == currentId) { "Skill name 已存在：${skill.name}" }
    }

    private fun AiSkill.withLegacyPromptIfNeeded(): AiSkill {
        val legacyPrompt = when (id) {
            SKILL_PARAGRAPH_PURIFY -> AiPromptStore.legacyPrompt(AiPromptStore.Prompt.PARAGRAPH_PURIFY)
            SKILL_CHAPTER_PURIFY -> AiPromptStore.legacyPrompt(AiPromptStore.Prompt.RULE_GENERATE)
            else -> null
        }?.trim()?.takeIf { it.isNotBlank() } ?: return this
        return copy(
            content = replaceBody(content, legacyPrompt),
            userModified = true,
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun replaceBody(content: String, body: String): String {
        if (!content.startsWith("---")) {
            return body.trim()
        }
        val end = Regex("""\r?\n---(?:\r?\n|$)""").find(content, startIndex = 3)
            ?: return body.trim()
        return content.substring(0, end.range.last + 1).trimEnd() + "\n\n" + body.trim()
    }

    private fun nextCustomOrder(): Int {
        return (appDb.aiSkillDao.all.maxOfOrNull { it.customOrder } ?: 0) + 1
    }

    private fun downloadSkillContent(url: String): String {
        val target = normalizeDownloadUrl(url)
        val uri = URI(target)
        require(uri.scheme == "http" || uri.scheme == "https") { "仅支持 http/https 文件链接" }
        val connection = URL(target).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 30_000
        connection.setRequestProperty("Accept", "text/plain,*/*")
        return try {
            require(connection.responseCode in 200..299) { "下载失败：HTTP ${connection.responseCode}" }
            connection.inputStream.bufferedReader().readText().also { content ->
                require(content.length <= MAX_SKILL_FILE_LENGTH) { "Skill 文件过大" }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun normalizeDownloadUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.contains("raw.githubusercontent.com")) {
            return trimmed
        }
        val regex = Regex("""https://github\.com/([^/]+)/([^/]+)/blob/([^/]+)/(.*)""")
        val match = regex.matchEntire(trimmed) ?: return trimmed
        val owner = match.groupValues[1]
        val repo = match.groupValues[2]
        val branch = match.groupValues[3]
        val path = match.groupValues[4]
        return "https://raw.githubusercontent.com/$owner/$repo/$branch/$path"
    }
}
