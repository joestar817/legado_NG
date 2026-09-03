package io.legado.app.help.config

import android.net.Uri
import io.legado.app.utils.GSON
import splitties.init.appCtx
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

internal fun decodePackagedHighlightRules(json: String): List<ReadHighlightRule> =
    GSON.fromJson(json, Array<ReadHighlightRule>::class.java)?.toList()
        ?: error("高亮规则文件为空")

/** 全局阅读高亮规则 ZIP／JSON 的安全导入导出边界。 */
internal object ReadHighlightRulePackageManager {

    private const val PACKAGE_DIR = "read_highlight_packages"
    private const val READ_STYLE_CONFIG_NAME = "readConfig.json"
    private const val MAX_ENTRY_COUNT = 256
    private const val MAX_ENTRY_BYTES = 64L * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 128L * 1024 * 1024
    private const val MAX_CONFIG_BYTES = 2L * 1024 * 1024

    data class ImportResult(
        val rules: List<ReadHighlightRule>,
        val warnings: List<String>,
    )

    data class ExportResult(
        val warnings: List<String>,
    )

    fun import(bytes: ByteArray): ImportResult {
        require(bytes.isNotEmpty()) { "高亮规则文件为空" }
        return if (bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
            importZip(bytes)
        } else {
            require(bytes.size <= MAX_CONFIG_BYTES) { "高亮规则文件过大" }
            val rules = decodePackagedHighlightRules(bytes.toString(Charsets.UTF_8))
            val warnings = mutableListOf<String>()
            ImportResult(
                rules = normalizeRules(rules, warnings) { reference, label ->
                    resolveExistingReference(reference, label, warnings)
                },
                warnings = warnings.distinct(),
            )
        }
    }

    fun export(
        rules: List<ReadHighlightRule>,
        output: OutputStream,
    ): ExportResult {
        val stagingRoot = File(appCtx.cacheDir, ".highlight-rule-export-${UUID.randomUUID()}")
        val warnings = mutableListOf<String>()
        val exportedFiles = arrayListOf<File>()
        var totalBytes = 0L
        stagingRoot.deleteRecursively()
        stagingRoot.mkdirs()
        try {
            fun copyResource(reference: String?, prefix: String, label: String): String? {
                val source = reference?.trim()?.takeIf(String::isNotEmpty) ?: return null
                if (source.startsWith("assets://")) return source
                val target = File(stagingRoot, "${prefix}_${resourceName(source).safeFileName()}")
                val copied = runCatching {
                    openResource(source)?.use { input ->
                        FileOutputStream(target).use { fileOutput ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var entryBytes = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                entryBytes += read
                                totalBytes += read
                                require(entryBytes <= MAX_ENTRY_BYTES) { "$label 文件过大" }
                                require(totalBytes <= MAX_TOTAL_BYTES) { "高亮规则资源总体积过大" }
                                fileOutput.write(buffer, 0, read)
                            }
                        }
                    } != null
                }.getOrElse {
                    target.delete()
                    throw it
                }
                if (!copied || !target.isFile) {
                    target.delete()
                    warnings += "$label 无法读取，未写入高亮规则包"
                    return null
                }
                exportedFiles += target
                return target.name
            }

            val portableRules = rules.mapIndexed { index, source ->
                source.normalized().copy(
                    bgImage = copyResource(
                        source.bgImage,
                        "highlight_rule_bg_$index",
                        "高亮规则“${source.name.ifBlank { source.id }}”的背景图",
                    ),
                    fontPath = copyResource(
                        source.fontPath,
                        "highlight_rule_font_$index",
                        "高亮规则“${source.name.ifBlank { source.id }}”的字体",
                    ),
                )
            }
            val configFile = File(stagingRoot, ReadHighlightRuleStore.fileName)
            configFile.writeText(GSON.toJson(portableRules))
            exportedFiles += configFile

            ZipOutputStream(output).use { zip ->
                exportedFiles.forEach { file ->
                    zip.putNextEntry(ZipEntry(file.name))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            return ExportResult(warnings.distinct())
        } finally {
            stagingRoot.deleteRecursively()
        }
    }

    private fun importZip(bytes: ByteArray): ImportResult {
        val packageParent = File(appCtx.filesDir, PACKAGE_DIR).apply { mkdirs() }
        val stagingRoot = File(packageParent, ".staging-${UUID.randomUUID()}")
        val installedRoot = File(packageParent, bytes.sha256())
        val warnings = mutableListOf<String>()
        var installedByThisImport = false
        try {
            val entries = extract(bytes.inputStream(), stagingRoot)
            val standaloneConfigFile = File(stagingRoot, ReadHighlightRuleStore.fileName)
            val readStyleConfigFile = File(stagingRoot, READ_STYLE_CONFIG_NAME)
            val configFile = when {
                standaloneConfigFile.isFile -> standaloneConfigFile
                readStyleConfigFile.isFile -> error("这是完整阅读预设包，请在“预设”页导入")
                else -> error("高亮规则包缺少 ${ReadHighlightRuleStore.fileName}")
            }
            require(configFile.length() <= MAX_CONFIG_BYTES) { "高亮规则配置文件过大" }
            val rules = decodePackagedHighlightRules(configFile.readText())
            if (installedRoot.exists()) {
                stagingRoot.deleteRecursively()
            } else {
                require(stagingRoot.renameTo(installedRoot)) { "无法安装高亮规则资源" }
                installedByThisImport = true
            }
            return ImportResult(
                rules = normalizeRules(rules, warnings) { reference, label ->
                    resolvePackagedReference(reference, label, entries, installedRoot, warnings)
                },
                warnings = warnings.distinct(),
            )
        } catch (error: Throwable) {
            stagingRoot.deleteRecursively()
            if (installedByThisImport) installedRoot.deleteRecursively()
            throw error
        }
    }

    private fun normalizeRules(
        rules: List<ReadHighlightRule>,
        warnings: MutableList<String>,
        resolveResource: (String, String) -> String?,
    ): List<ReadHighlightRule> = rules.mapIndexed { index, source ->
        val rule = source.normalized()
        val validPattern = rule.pattern.isNotBlank() && runCatching { Regex(rule.pattern) }.isSuccess
        if (!validPattern) warnings += "高亮规则“${rule.name.ifBlank { rule.id }}”的正则无效，已停用"
        rule.copy(
            enabled = rule.enabled && validPattern,
            position = index,
            bgImage = rule.bgImage?.let { resolveResource(it, "高亮规则背景图") },
            fontPath = rule.fontPath?.let { resolveResource(it, "高亮规则字体") },
        )
    }

    private fun resolvePackagedReference(
        reference: String,
        label: String,
        entries: Set<String>,
        installedRoot: File,
        warnings: MutableList<String>,
    ): String? {
        val value = reference.trim().takeIf(String::isNotEmpty) ?: return null
        if (value.startsWith("assets://")) {
            return resolveExistingReference(value, label, warnings)
        }
        val candidates = buildList {
            val normalized = value.replace('\\', '/')
            if (!normalized.startsWith('/') && ':' !in normalized) add(normalized)
            add(File(normalized).name)
            runCatching { Uri.decode(Uri.parse(value).lastPathSegment.orEmpty()) }
                .getOrNull()
                ?.substringAfterLast('/')
                ?.substringAfterLast(':')
                ?.takeIf(String::isNotEmpty)
                ?.let(::add)
        }.distinct()
        val entry = candidates.firstNotNullOfOrNull { candidate ->
            entries.firstOrNull { it == candidate }
                ?: entries.singleOrNull { File(it).name == File(candidate).name }
        }
        if (entry == null) {
            warnings += "$label 未随高亮规则包携带，已忽略"
            return null
        }
        val file = File(installedRoot, entry).canonicalFile
        return file.takeIf {
            it.isFile && it.toPath().startsWith(installedRoot.canonicalFile.toPath())
        }?.absolutePath
    }

    private fun resolveExistingReference(
        reference: String,
        label: String,
        warnings: MutableList<String>,
    ): String? {
        val value = reference.trim().takeIf(String::isNotEmpty) ?: return null
        val readable = if (value.startsWith("assets://")) {
            runCatching {
                appCtx.assets.open(value.removePrefix("assets://")).close()
            }.isSuccess
        } else {
            runCatching { openResource(value)?.use { } != null }.getOrDefault(false)
        }
        if (!readable) warnings += "$label 无法读取，已忽略"
        return value.takeIf { readable }
    }

    private fun extract(input: InputStream, stagingRoot: File): Set<String> {
        stagingRoot.mkdirs()
        val entries = linkedSetOf<String>()
        val caseInsensitiveEntries = hashSetOf<String>()
        var entryCount = 0
        var totalBytes = 0L
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount++
                require(entryCount <= MAX_ENTRY_COUNT) { "高亮规则包文件数量过多" }
                val normalizedName = validateEntryPath(entry.name)
                require(caseInsensitiveEntries.add(normalizedName.lowercase(Locale.ROOT))) {
                    "高亮规则包包含重名路径: $normalizedName"
                }
                entries += normalizedName
                val target = File(stagingRoot, normalizedName).canonicalFile
                require(target.toPath().startsWith(stagingRoot.canonicalFile.toPath())) {
                    "高亮规则包路径越界: $normalizedName"
                }
                if (entry.isDirectory) {
                    target.mkdirs()
                    zip.closeEntry()
                    continue
                }
                target.parentFile?.mkdirs()
                var entryBytes = 0L
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = zip.read(buffer)
                        if (read < 0) break
                        entryBytes += read
                        totalBytes += read
                        require(entryBytes <= MAX_ENTRY_BYTES) { "高亮规则包单个文件过大: $normalizedName" }
                        require(totalBytes <= MAX_TOTAL_BYTES) { "高亮规则包解压后体积过大" }
                        output.write(buffer, 0, read)
                    }
                }
                zip.closeEntry()
            }
        }
        return entries
    }

    private fun validateEntryPath(name: String): String {
        val normalized = name.trim().replace('\\', '/').trimEnd('/')
        require(normalized.isNotEmpty()) { "高亮规则包包含空路径" }
        require(!normalized.startsWith('/') && ':' !in normalized) { "高亮规则包路径非法: $name" }
        require(normalized.split('/').none { it.isEmpty() || it == "." || it == ".." }) {
            "高亮规则包路径非法: $name"
        }
        return normalized
    }

    private fun openResource(reference: String): InputStream? {
        val uri = Uri.parse(reference)
        return when (uri.scheme?.lowercase(Locale.ROOT)) {
            "content" -> appCtx.contentResolver.openInputStream(uri)
            "file" -> uri.path?.let(::File)?.takeIf(File::isFile)?.inputStream()
            else -> File(reference).takeIf(File::isFile)?.inputStream()
        }
    }

    private fun resourceName(reference: String): String {
        val uriName = runCatching {
            Uri.decode(Uri.parse(reference).lastPathSegment.orEmpty())
                .substringAfterLast('/')
                .substringAfterLast(':')
        }.getOrNull()
        return uriName?.takeIf(String::isNotBlank)
            ?: File(reference).name.takeIf(String::isNotBlank)
            ?: "asset"
    }

    private fun String.safeFileName(): String =
        replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "asset" }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { "%02x".format(it) }
}
