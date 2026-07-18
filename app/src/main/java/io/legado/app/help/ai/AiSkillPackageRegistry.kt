package io.legado.app.help.ai

import splitties.init.appCtx
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * APK 内只读 Skill Package 的通用资源加载器。
 *
 * 模型只获得 SKILL.md 主入口；其它文件通过 use_skill 按需读取。包哈希覆盖路径和内容，
 * 确保工具回执、会话固定版本与实际读取资源属于同一个不可变快照。
 */
object AiSkillPackageRegistry {

    private const val ASSET_ROOT = "skills"
    private const val ENTRY_FILE = "SKILL.md"
    private const val MAX_FILES = 64
    private const val MAX_FILE_BYTES = 256 * 1024
    private const val MAX_PACKAGE_BYTES = 1024 * 1024

    private val cache = ConcurrentHashMap<String, AiSkillPackageSnapshot>()
    private val setCache = ConcurrentHashMap<String, AiSkillPackageSetSnapshot>()

    fun systemPackage(skillId: String): AiSkillPackageSnapshot? {
        val normalizedId = normalizeSkillId(skillId) ?: return null
        cache[normalizedId]?.let { return it }
        val root = "$ASSET_ROOT/$normalizedId"
        val files = runCatching { readAssetFiles(root) }.getOrNull().orEmpty()
        if (files.isEmpty()) return null
        return buildSnapshot(normalizedId, files).also { cache[normalizedId] = it }
    }

    fun readText(
        skillId: String,
        relativePath: String?,
        expectedContentHash: String
    ): AiSkillPackageResource {
        val snapshot = requireNotNull(systemPackage(skillId)) {
            "Skill Package 不存在：$skillId"
        }
        require(expectedContentHash.isNotBlank() && snapshot.contentHash == expectedContentHash) {
            "当前会话固定的 Skill Package 已过期，请新建会话后重试"
        }
        val path = normalizeRelativePath(relativePath?.takeIf { it.isNotBlank() } ?: ENTRY_FILE)
        val bytes = requireNotNull(snapshot.files[path]) {
            "Skill '$skillId' 中不存在文件：$path"
        }
        val content = bytes.toString(Charsets.UTF_8)
        return AiSkillPackageResource(
            skillId = snapshot.skillId,
            path = path,
            content = if (path == ENTRY_FILE) extractBody(content) else content,
            contentHash = snapshot.contentHash
        )
    }

    /**
     * 构建一组只读 System Skill Package 的不可变快照。
     *
     * 与单包快照一样，集合哈希会固定到会话中。任一成员、路径或内容变化都会
     * 产生新哈希，避免一次工作流混读不同 APK 版本的评审规则。
     */
    fun systemPackageSet(skillIds: Collection<String>): AiSkillPackageSetSnapshot? {
        val normalizedIds = skillIds
            .mapNotNull(::normalizeSkillId)
            .distinct()
            .sorted()
        if (normalizedIds.isEmpty() || normalizedIds.size != skillIds.size) return null
        val cacheKey = normalizedIds.joinToString("\u0000")
        setCache[cacheKey]?.let { return it }
        val packages = normalizedIds.associateWith { skillId ->
            systemPackage(skillId) ?: return null
        }
        return buildSetSnapshot(packages).also { setCache[cacheKey] = it }
    }

    internal fun buildSetSnapshot(
        packages: Map<String, AiSkillPackageSnapshot>
    ): AiSkillPackageSetSnapshot {
        require(packages.isNotEmpty()) { "Skill Package 集合不能为空" }
        val normalizedPackages = packages.toSortedMap()
        normalizedPackages.forEach { (skillId, skillPackage) ->
            require(skillId == skillPackage.skillId) { "Skill Package 集合 id 不一致：$skillId" }
        }
        val contentHash = if (packages.size == 1) {
            normalizedPackages.values.single().contentHash
        } else {
            val digest = MessageDigest.getInstance("SHA-256")
            normalizedPackages.forEach { (skillId, skillPackage) ->
                digest.update(skillId.toByteArray(Charsets.UTF_8))
                digest.update(0.toByte())
                digest.update(skillPackage.contentHash.toByteArray(Charsets.UTF_8))
                digest.update(0.toByte())
            }
            digest.digest().toHexString()
        }
        return AiSkillPackageSetSnapshot(
            skillIds = normalizedPackages.keys.toList(),
            packages = normalizedPackages,
            contentHash = contentHash
        )
    }

    fun readTextFromSet(
        skillId: String,
        relativePath: String?,
        availableSkillIds: Collection<String>,
        expectedSetContentHash: String
    ): AiSkillPackageResource {
        val snapshot = requireNotNull(systemPackageSet(availableSkillIds)) {
            "Skill Package 集合不存在"
        }
        require(
            expectedSetContentHash.isNotBlank() &&
                snapshot.contentHash == expectedSetContentHash
        ) {
            "当前会话固定的 Skill Package 集合已过期，请新建会话后重试"
        }
        val skillPackage = requireNotNull(snapshot.packages[skillId]) {
            "Skill '$skillId' 不在当前可用集合中"
        }
        return readText(
            skillId = skillId,
            relativePath = relativePath,
            expectedContentHash = skillPackage.contentHash
        )
    }

    internal fun buildSnapshot(
        skillId: String,
        files: Map<String, ByteArray>
    ): AiSkillPackageSnapshot {
        val normalizedId = requireNotNull(normalizeSkillId(skillId)) { "Skill id 非法" }
        require(files.isNotEmpty()) { "Skill Package 不能为空" }
        require(files.size <= MAX_FILES) { "Skill Package 文件数不能超过 $MAX_FILES" }

        val normalizedFiles = linkedMapOf<String, ByteArray>()
        var totalBytes = 0
        files.toSortedMap().forEach { (rawPath, bytes) ->
            val path = normalizeRelativePath(rawPath)
            require(path !in normalizedFiles) { "Skill Package 路径重复：$path" }
            require(bytes.size <= MAX_FILE_BYTES) { "Skill Package 文件过大：$path" }
            totalBytes += bytes.size
            require(totalBytes <= MAX_PACKAGE_BYTES) { "Skill Package 总大小超过限制" }
            normalizedFiles[path] = bytes.copyOf()
        }
        require(ENTRY_FILE in normalizedFiles) { "Skill Package 缺少 $ENTRY_FILE" }

        val digest = MessageDigest.getInstance("SHA-256")
        normalizedFiles.forEach { (path, bytes) ->
            digest.update(path.toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
            digest.update(bytes)
            digest.update(0.toByte())
        }
        val contentHash = digest.digest().toHexString()
        return AiSkillPackageSnapshot(
            skillId = normalizedId,
            files = normalizedFiles,
            contentHash = contentHash
        )
    }

    internal fun normalizeRelativePath(value: String): String {
        val path = value.trim()
        require(path.isNotBlank()) { "Skill 文件路径不能为空" }
        require(path.length <= 240) { "Skill 文件路径过长" }
        require(!path.startsWith('/') && !path.startsWith('\\')) { "Skill 文件路径必须是相对路径" }
        require('\\' !in path) { "Skill 文件路径必须使用 / 分隔" }
        require(path.none { it.isISOControl() }) { "Skill 文件路径不能包含控制字符" }
        val segments = path.split('/')
        require(segments.none { it.isBlank() || it == "." || it == ".." }) {
            "Skill 文件路径不能越出包目录"
        }
        return segments.joinToString("/")
    }

    private fun readAssetFiles(root: String): Map<String, ByteArray> {
        val result = linkedMapOf<String, ByteArray>()

        fun visit(relativeDir: String) {
            val assetDir = if (relativeDir.isBlank()) root else "$root/$relativeDir"
            appCtx.assets.list(assetDir).orEmpty().sorted().forEach { child ->
                val relativePath = if (relativeDir.isBlank()) child else "$relativeDir/$child"
                val assetPath = "$root/$relativePath"
                val nested = appCtx.assets.list(assetPath).orEmpty()
                if (nested.isNotEmpty()) {
                    visit(relativePath)
                } else {
                    result[relativePath] = appCtx.assets.open(assetPath).use { it.readBytes() }
                }
            }
        }

        visit("")
        return result
    }

    private fun normalizeSkillId(value: String): String? {
        return value.trim().takeIf { SKILL_ID_REGEX.matches(it) }
    }

    private fun extractBody(content: String): String {
        if (!content.startsWith("---")) return content
        val end = Regex("""\r?\n---(?:\r?\n|$)""").find(content, startIndex = 3)
            ?: return content
        return content.substring(end.range.last + 1).trimStart('\r', '\n')
    }

    private val SKILL_ID_REGEX = Regex("^[a-z0-9][a-z0-9_-]*$")

    private fun ByteArray.toHexString(): String {
        return joinToString("") { byte -> "%02x".format(byte) }
    }
}

data class AiSkillPackageSnapshot internal constructor(
    val skillId: String,
    internal val files: Map<String, ByteArray>,
    val contentHash: String
) {
    val entryContent: String
        get() = requireNotNull(files["SKILL.md"]).toString(Charsets.UTF_8)

    val resourcePaths: Set<String>
        get() = files.keys
}

data class AiSkillPackageResource(
    val skillId: String,
    val path: String,
    val content: String,
    val contentHash: String
)

data class AiSkillPackageSetSnapshot internal constructor(
    val skillIds: List<String>,
    internal val packages: Map<String, AiSkillPackageSnapshot>,
    val contentHash: String
)
