package io.legado.app.help.config

import android.net.Uri
import com.google.gson.JsonObject
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
import kotlin.math.roundToInt

/** MD3/ARC/Legado 阅读排版 ZIP 的安全解析、资源安装和 NG 字段规范化边界。 */
internal object ReadStylePackageManager {

    private const val CONFIG_NAME = "readConfig.json"
    private const val PACKAGE_DIR = "read_style_packages"
    private const val MAX_ENTRY_COUNT = 512
    private const val MAX_ENTRY_BYTES = 64L * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 256L * 1024 * 1024
    private const val MAX_CONFIG_BYTES = 2L * 1024 * 1024

    data class ImportResult(
        val config: ReadBookConfig.Config,
        val sourceFormat: String,
        val warnings: List<String>,
        val installedRoot: File,
    )

    data class ExportResult(
        val warnings: List<String>,
    )

    fun import(bytes: ByteArray): ImportResult = import(
        input = bytes.inputStream(),
        packageHash = bytes.sha256(),
        packageParent = File(appCtx.filesDir, PACKAGE_DIR),
    )

    fun export(config: ReadBookConfig.Config, output: OutputStream): ExportResult {
        val stagingRoot = File(appCtx.cacheDir, ".read-style-export-${UUID.randomUUID()}")
        return export(config, output, stagingRoot, ::openResource)
    }

    internal fun export(
        config: ReadBookConfig.Config,
        output: OutputStream,
        stagingRoot: File,
        openResource: (String) -> InputStream?,
    ): ExportResult {
        val warnings = mutableListOf<String>()
        val exportedFiles = arrayListOf<File>()
        var totalBytes = 0L
        stagingRoot.deleteRecursively()
        stagingRoot.mkdirs()
        try {
            val portableConfig = config.copy(
                highlightRules = ArrayList(config.highlightRules.map { it.copy() })
            )

            fun copyResource(reference: String?, prefix: String, label: String): String? {
                val source = reference?.trim()?.takeIf(String::isNotEmpty) ?: return null
                if (source.startsWith("assets://")) return source
                val sourceName = resourceName(source)
                val target = File(stagingRoot, "${prefix}_${sourceName.safeFileName()}")
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
                                require(totalBytes <= MAX_TOTAL_BYTES) { "排版包资源总体积过大" }
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
                    warnings += "$label 无法读取，未写入排版包"
                    return null
                }
                exportedFiles += target
                return target.name
            }

            fun exportBackground(index: Int, label: String) {
                val type = when (index) {
                    0 -> portableConfig.bgType
                    1 -> portableConfig.bgTypeNight
                    else -> portableConfig.bgTypeEInk
                }
                if (type != 2) return
                val reference = when (index) {
                    0 -> portableConfig.bgStr
                    1 -> portableConfig.bgStrNight
                    else -> portableConfig.bgStrEInk
                }
                val bundled = copyResource(reference, "read_background_$index", label)
                if (bundled != null) {
                    when (index) {
                        0 -> portableConfig.bgStr = bundled
                        1 -> portableConfig.bgStrNight = bundled
                        else -> portableConfig.bgStrEInk = bundled
                    }
                } else {
                    when (index) {
                        0 -> {
                            portableConfig.bgType = 0
                            portableConfig.bgStr = "#FFFFFF"
                        }

                        1 -> {
                            portableConfig.bgTypeNight = 0
                            portableConfig.bgStrNight = "#000000"
                        }

                        else -> {
                            portableConfig.bgTypeEInk = 0
                            portableConfig.bgStrEInk = "#FFFFFF"
                        }
                    }
                }
            }

            exportBackground(0, "日间阅读背景")
            exportBackground(1, "夜间阅读背景")
            exportBackground(2, "墨水屏阅读背景")
            portableConfig.textFont = copyResource(
                portableConfig.textFont,
                "read_font_text",
                "正文字体",
            ).orEmpty()
            portableConfig.titleFont = copyResource(
                portableConfig.titleFont,
                "read_font_title",
                "标题字体",
            ).orEmpty()
            portableConfig.highlightRules = ArrayList(
                portableConfig.highlightRules.mapIndexed { index, rule ->
                    rule.copy(
                        bgImage = copyResource(
                            rule.bgImage,
                            "highlight_rule_bg_$index",
                            "高亮规则“${rule.name.ifBlank { rule.id }}”的背景图",
                        ),
                        fontPath = copyResource(
                            rule.fontPath,
                            "highlight_rule_font_$index",
                            "高亮规则“${rule.name.ifBlank { rule.id }}”的字体",
                        ),
                    )
                }
            )

            val configJson = GSON.toJsonTree(portableConfig).asJsonObject.apply {
                remove("ngReadStyleSource")
                remove("ngUnknownFields")
            }
            val configFile = File(stagingRoot, CONFIG_NAME)
            configFile.writeText(GSON.toJson(configJson))
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

    internal fun import(
        input: InputStream,
        packageHash: String,
        packageParent: File,
    ): ImportResult {
        packageParent.mkdirs()
        val stagingRoot = File(packageParent, ".staging-${UUID.randomUUID()}")
        val installedRoot = File(packageParent, packageHash)
        val warnings = mutableListOf<String>()
        var installedByThisImport = false
        try {
            val entries = extract(input, stagingRoot)
            val configFile = File(stagingRoot, CONFIG_NAME)
            require(configFile.isFile) { "排版包缺少 $CONFIG_NAME" }
            require(configFile.length() <= MAX_CONFIG_BYTES) { "排版配置文件过大" }
            val root = GSON.fromJson(configFile.readText(), JsonObject::class.java)
                ?: error("排版配置为空")
            val sourceFormat = detectSourceFormat(root)
            val config = GSON.fromJson(root, ReadBookConfig.Config::class.java)
                ?: error("无法解析排版配置")
            config.ngReadStyleSource = sourceFormat
            config.ngUnknownFields = root.entrySet()
                .filterNot { (name, _) -> name in knownFieldNames }
                .associate { (name, value) -> name to value.toString() }

            if (installedRoot.exists()) {
                stagingRoot.deleteRecursively()
            } else {
                require(stagingRoot.renameTo(installedRoot)) { "无法安装排版包资源" }
                installedByThisImport = true
            }
            normalizeResources(config, entries, installedRoot, warnings)
            normalizeValues(config, root, sourceFormat, warnings)
            return ImportResult(config, sourceFormat, warnings.distinct(), installedRoot)
        } catch (error: Throwable) {
            stagingRoot.deleteRecursively()
            if (installedByThisImport) installedRoot.deleteRecursively()
            throw error
        }
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
                require(entryCount <= MAX_ENTRY_COUNT) { "排版包文件数量过多" }
                val normalizedName = validateEntryPath(entry.name)
                require(caseInsensitiveEntries.add(normalizedName.lowercase(Locale.ROOT))) {
                    "排版包包含重名路径: $normalizedName"
                }
                entries += normalizedName
                val target = File(stagingRoot, normalizedName).canonicalFile
                require(target.toPath().startsWith(stagingRoot.canonicalFile.toPath())) {
                    "排版包路径越界: $normalizedName"
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
                        require(entryBytes <= MAX_ENTRY_BYTES) { "排版包单个文件过大: $normalizedName" }
                        require(totalBytes <= MAX_TOTAL_BYTES) { "排版包解压后体积过大" }
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
        require(normalized.isNotEmpty()) { "排版包包含空路径" }
        require(!normalized.startsWith('/') && ':' !in normalized) { "排版包路径非法: $name" }
        require(normalized.split('/').none { it.isEmpty() || it == "." || it == ".." }) {
            "排版包路径非法: $name"
        }
        return normalized
    }

    private fun detectSourceFormat(root: JsonObject): String = when {
        isArcPackage(root) -> "arc-read-style"
        root.has("highlightRules") || root.has("titleFont") || root.has("textItalic") ||
            root.has("underline") || root.has("tipHeaderColor") -> "md3-read-style"
        else -> "legado-read-style"
    }

    private fun isArcPackage(root: JsonObject): Boolean =
        root.has("paperEffect") || root.has("readScrollFollowBackground") ||
            root.has("readScrollFollowBackgroundNight") || root.has("readScrollFollowBackgroundEInk")

    private fun normalizeResources(
        config: ReadBookConfig.Config,
        entries: Set<String>,
        installedRoot: File,
        warnings: MutableList<String>,
    ) {
        fun resolve(reference: String?, label: String): String? {
            val value = reference?.trim()?.takeIf(String::isNotEmpty) ?: return null
            if (value.startsWith("assets://")) {
                val assetPath = value.removePrefix("assets://")
                return runCatching {
                    appCtx.assets.open(assetPath).close()
                    value
                }.getOrElse {
                    warnings += "$label 在 NG 内置资源中不存在，已忽略"
                    null
                }
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
                warnings += "$label 未随排版包携带，已忽略"
                return null
            }
            val file = File(installedRoot, entry).canonicalFile
            return file.takeIf { it.isFile && it.toPath().startsWith(installedRoot.canonicalFile.toPath()) }
                ?.absolutePath
        }

        config.textFont = resolve(config.textFont, "正文字体").orEmpty()
        config.titleFont = resolve(config.titleFont, "标题字体").orEmpty()
        config.headerFont = resolve(config.headerFont, "页眉字体").orEmpty()
        config.footerFont = resolve(config.footerFont, "页脚字体").orEmpty()

        fun resolveBackground(type: Int, value: String, label: String): Pair<Int, String> {
            if (type != 2) return type to value
            val path = resolve(value, label)
            return if (path != null) 2 to path else 0 to if (label.contains("夜间")) "#000000" else "#FFFFFF"
        }
        resolveBackground(config.bgType, config.bgStr, "日间阅读背景").also {
            config.bgType = it.first
            config.bgStr = it.second
        }
        resolveBackground(config.bgTypeNight, config.bgStrNight, "夜间阅读背景").also {
            config.bgTypeNight = it.first
            config.bgStrNight = it.second
        }
        resolveBackground(config.bgTypeEInk, config.bgStrEInk, "墨水屏阅读背景").also {
            config.bgTypeEInk = it.first
            config.bgStrEInk = it.second
        }

        var missingRuleBackgrounds = 0
        var missingRuleFonts = 0
        config.highlightRules = ArrayList(config.highlightRules.map { source ->
            val rule = source.normalized()
            val bg = rule.bgImage?.let { resolve(it, "高亮规则背景图") }.also {
                if (rule.bgImage != null && it == null) missingRuleBackgrounds++
            }
            val font = rule.fontPath?.let { resolve(it, "高亮规则字体") }.also {
                if (rule.fontPath != null && it == null) missingRuleFonts++
            }
            rule.copy(bgImage = bg, fontPath = font)
        })
        warnings.removeAll { it == "高亮规则背景图 未随排版包携带，已忽略" }
        warnings.removeAll { it == "高亮规则字体 未随排版包携带，已忽略" }
        if (missingRuleBackgrounds > 0) warnings += "$missingRuleBackgrounds 条高亮规则的背景图未随包携带"
        if (missingRuleFonts > 0) warnings += "$missingRuleFonts 条高亮规则的字体未随包携带"
    }

    private fun normalizeValues(
        config: ReadBookConfig.Config,
        root: JsonObject,
        sourceFormat: String,
        warnings: MutableList<String>,
    ) {
        if (config.bgType == 1) {
            config.bgStr = resolveBundledReadBackgroundName(config.bgStr)
        }
        if (config.bgTypeNight == 1) {
            config.bgStrNight = resolveBundledReadBackgroundName(config.bgStrNight)
        }
        if (config.bgTypeEInk == 1) {
            config.bgStrEInk = resolveBundledReadBackgroundName(config.bgStrEInk)
        }
        config.textSize = config.textSize.coerceIn(8, 72)
        config.textBold = normalizeFontWeight(config.textBold)
        config.titleBold = normalizeFontWeight(config.titleBold)
        config.letterSpacing = config.letterSpacing.coerceIn(-0.5f, 0.5f)
        config.lineSpacingExtra = config.lineSpacingExtra.coerceIn(0, 100)
        config.paragraphSpacing = config.paragraphSpacing.coerceIn(0, 100)
        config.titleSegScaling = config.titleSegScaling.coerceIn(0.1f, 2f)
        config.shadowRadius = config.shadowRadius.coerceIn(0f, 50f)
        config.shadowDx = config.shadowDx.coerceIn(-50f, 50f)
        config.shadowDy = config.shadowDy.coerceIn(-50f, 50f)
        config.underlineHeight = config.underlineHeight.coerceIn(1, 20)
        config.underlinePadding = config.underlinePadding.coerceIn(-20, 100)
        config.dottedBase = config.dottedBase.coerceIn(1f, 100f)
        config.dottedRatio = config.dottedRatio.coerceIn(1f, 100f)
        if (sourceFormat == "arc-read-style") {
            val underlineMode = root.intOrNull("underlineMode") ?: config.underlineMode
            config.underline = underlineMode != 0
            config.dottedLine = underlineMode == 2
            root.floatOrNull("underlineStrokeWidth")?.let {
                config.underlineHeight = it.roundToInt().coerceIn(1, 20)
            }
            root.floatOrNull("underlineDashLength")?.coerceIn(1f, 100f)?.let {
                config.dottedBase = it
                config.dottedRatio = it
            }
            if (config.titleMode == 3) {
                config.titleMode = 1
                warnings += "ARC 高级标题在当前版本按居中标题显示"
            }
            val paperEffectEnabled = root.booleanOrNull("paperEffect") == true ||
                (root.floatOrNull("paperInkStrength") ?: 0f) > 0f
            if (paperEffectEnabled) warnings += "ARC 纸张质感当前不支持，已忽略"
            val scrollFollowEnabled = listOf(
                "readScrollFollowBackground",
                "readScrollFollowBackgroundNight",
                "readScrollFollowBackgroundEInk",
            ).any { root.booleanOrNull(it) == true }
            if (scrollFollowEnabled) warnings += "ARC 滚动背景跟随当前不支持，已忽略"
        }
        if (config.titleMode !in 0..2) {
            warnings += "未知标题模式 ${config.titleMode} 已按左对齐显示"
            config.titleMode = 0
        }
        config.highlightRules = ArrayList(config.highlightRules.map { rule ->
            if (rule.pattern.isBlank() || runCatching { Regex(rule.pattern) }.isFailure) {
                warnings += "高亮规则“${rule.name.ifBlank { rule.id }}”的正则无效，已停用"
                rule.copy(enabled = false)
            } else {
                rule
            }
        })
    }

    private fun JsonObject.booleanOrNull(name: String): Boolean? = runCatching {
        get(name)?.takeUnless { it.isJsonNull }?.asBoolean
    }.getOrNull()

    private fun JsonObject.intOrNull(name: String): Int? = runCatching {
        get(name)?.takeUnless { it.isJsonNull }?.asInt
    }.getOrNull()

    private fun JsonObject.floatOrNull(name: String): Float? = runCatching {
        get(name)?.takeUnless { it.isJsonNull }?.asFloat
    }.getOrNull()

    private fun normalizeFontWeight(value: Int): Int = when (value) {
        0 -> 400
        1 -> 900
        2 -> 300
        else -> value.coerceIn(100, 900)
    }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { "%02x".format(it) }

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

    private val knownFieldNames = setOf(
        "name", "bgStr", "bgStrNight", "bgStrEInk", "bgAlpha", "bgType", "bgTypeNight",
        "bgTypeEInk", "darkStatusIcon", "darkStatusIconNight", "darkStatusIconEInk",
        "textColor", "textColorNight", "textColorEInk", "textAccentColor",
        "textAccentColorNight", "textAccentColorEInk", "pageAnim", "pageAnimEInk", "textFont",
        "titleFont", "headerFont", "footerFont", "headerFontSize", "footerFontSize",
        "applyHeaderStyle", "textBold", "textSize", "textItalic", "textShadow", "shadowRadius",
        "shadowDx", "shadowDy", "shadowColor", "shadowColorN", "letterSpacing",
        "lineSpacingExtra", "paragraphSpacing", "titleMode", "titleSize", "titleTopSpacing",
        "titleBottomSpacing", "titleColor", "titleColorNight", "titleBold",
        "titleLineSpacingExtra", "titleLineSpacingSub", "titleSegType", "titleSegScaling",
        "titleSegDistance", "titleSegFlag", "paragraphIndent", "underlineMode", "underline",
        "underlinePadding", "underlineHeight", "underlineExtend", "underlineColor",
        "underlineColorNight", "dottedLine", "dottedBase", "dottedRatio", "paddingBottom",
        "paddingLeft", "paddingRight", "paddingTop", "headerPaddingBottom", "headerPaddingLeft",
        "headerPaddingRight", "headerPaddingTop", "footerPaddingBottom", "footerPaddingLeft",
        "footerPaddingRight", "footerPaddingTop", "showHeaderLine", "showFooterLine",
        "tipHeaderLeft", "tipHeaderMiddle", "tipHeaderRight", "tipFooterLeft", "tipFooterMiddle",
        "tipFooterRight", "tipColor", "tipHeaderColor", "tipHeaderColorNight", "tipFooterColor",
        "tipFooterColorNight", "tipDividerColor", "headerMode", "footerMode", "highlightRules",
        "paperEffect", "paperInkStrength", "readScrollFollowBackground",
        "readScrollFollowBackgroundNight", "readScrollFollowBackgroundEInk",
        "underlineDashLength", "underlineStrokeWidth",
        "ngReadStyleSource", "ngUnknownFields",
    )
}
