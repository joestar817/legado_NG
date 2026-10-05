package io.legado.app.help.config

import android.net.Uri
import com.google.gson.JsonObject
import io.legado.app.utils.GSON
import io.legado.app.utils.externalFiles
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
    private const val MAX_CONFIG_BYTES = 5L * 1024 * 1024

    data class ImportResult(
        val config: ReadBookConfig.Config,
        val sourceFormat: String,
        val warnings: List<String>,
        val installedRoot: File,
        val readerSettings: JsonObject? = null,
    )

    data class ExportResult(
        val warnings: List<String>,
    )

    fun import(bytes: ByteArray): ImportResult = import(
        input = bytes.inputStream(),
        packageHash = bytes.sha256(),
        packageParent = File(appCtx.filesDir, PACKAGE_DIR),
    )

    fun export(config: ReadBookConfig.Config, output: OutputStream, readerSettings: JsonObject? = null): ExportResult {
        val stagingRoot = File(appCtx.cacheDir, ".read-style-export-${UUID.randomUUID()}")
        return export(config, output, stagingRoot, ::openResource, readerSettings)
    }

    internal fun export(
        config: ReadBookConfig.Config,
        output: OutputStream,
        stagingRoot: File,
        openResource: (String) -> InputStream?,
        readerSettings: JsonObject? = null,
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

            val referenceNames = mutableMapOf<String, String>()
            val contentNames = mutableMapOf<String, String>()
            fun copyResource(reference: String?, prefix: String, label: String): String? {
                val source = reference?.trim()?.takeIf(String::isNotEmpty) ?: return null
                referenceNames[source]?.let { return it }
                val target = File(stagingRoot, "${prefix}_${resourceName(source).safeFileName()}")
                val digest = MessageDigest.getInstance("SHA-256")
                var entryBytes = 0L
                val input = openResource(source) ?: error("$label 无法读取，导出已取消")
                input.use {
                    FileOutputStream(target).use { fileOutput ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = it.read(buffer)
                            if (read < 0) break
                            entryBytes += read
                            require(entryBytes <= MAX_ENTRY_BYTES) { "$label 文件过大" }
                            digest.update(buffer, 0, read)
                            fileOutput.write(buffer, 0, read)
                        }
                    }
                }
                require(entryBytes > 0) { "$label 是空文件，导出已取消" }
                val hash = digest.digest().joinToString("") { "%02x".format(it) }
                val existing = contentNames[hash]
                if (existing != null) {
                    target.delete()
                    referenceNames[source] = existing
                    return existing
                }
                require(exportedFiles.size + 2 <= MAX_ENTRY_COUNT) { "排版包资源数量过多" }
                totalBytes += entryBytes
                require(totalBytes <= MAX_TOTAL_BYTES) { "排版包资源总体积过大" }
                exportedFiles += target
                referenceNames[source] = target.name
                contentNames[hash] = target.name
                return target.name
            }
            fun exportBackground(index: Int, label: String) {
                val type = when (index) {
                    0 -> portableConfig.bgType
                    1 -> portableConfig.bgTypeNight
                    else -> portableConfig.bgTypeEInk
                }
                if (type != 1 && type != 2) return
                val reference = when (index) {
                    0 -> portableConfig.bgStr
                    1 -> portableConfig.bgStrNight
                    else -> portableConfig.bgStrEInk
                }
                val portableReference = when {
                    type == 1 -> "assets://bg/${resolveBundledReadBackgroundName(reference)}"
                    !reference.contains('/') && !reference.contains('\\') && ':' !in reference ->
                        File(File(appCtx.externalFiles, "bg"), reference).absolutePath
                    else -> reference
                }
                val bundled = requireNotNull(copyResource(portableReference, "read_background_$index", label)) {
                    "$label 未设置图片，导出已取消"
                }
                when (index) {
                    0 -> { portableConfig.bgStr = bundled; portableConfig.bgType = 2 }
                    1 -> { portableConfig.bgStrNight = bundled; portableConfig.bgTypeNight = 2 }
                    else -> { portableConfig.bgStrEInk = bundled; portableConfig.bgTypeEInk = 2 }
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
            portableConfig.headerFont = copyResource(portableConfig.headerFont, "read_font_header", "页眉字体").orEmpty()
            portableConfig.footerFont = copyResource(portableConfig.footerFont, "read_font_footer", "页脚字体").orEmpty()
            portableConfig.scriptFonts = portableConfig.scriptFonts?.let { fonts ->
                fun copyScript(value: String?, prefix: String, label: String): String? {
                    val source = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
                    if (source.startsWith("system:")) return source
                    return copyResource(source, prefix, label)
                }
                SparseFontOverrides(
                    default = copyScript(fonts.default, "read_font_script_default", "脚本默认字体"),
                    latin = copyScript(fonts.latin, "read_font_script_latin", "拉丁脚本字体"),
                    cjk = copyScript(fonts.cjk, "read_font_script_cjk", "CJK脚本字体"),
                    other = copyScript(fonts.other, "read_font_script_other", "其他脚本字体"),
                ).takeUnless { it.isEmpty() }
            }
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
                addProperty("ngResourcePackageVersion", 1)
                readerSettings?.let { add("ngReaderSettings", it.deepCopy()) }
            }
            val configFile = File(stagingRoot, CONFIG_NAME)
            configFile.writeText(GSON.toJson(configJson))
            require(configFile.length() <= MAX_CONFIG_BYTES) { "阅读预设配置超过5MB，导出已取消" }
            require(totalBytes + configFile.length() <= MAX_TOTAL_BYTES) { "阅读预设包总体积过大" }
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
            val readerSettings = root.get("ngReaderSettings")?.let {
                require(it.isJsonObject) { "阅读设置格式错误" }
                it.asJsonObject.also(ReadPresetPreferences::validate)
            }
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
            normalizeResources(config, entries, installedRoot, warnings,
                strict = root.intOrNull("ngResourcePackageVersion") == 1)
            normalizeValues(config, root, sourceFormat, warnings)
            return ImportResult(config, sourceFormat, warnings.distinct(), installedRoot,
                readerSettings)
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
        strict: Boolean,
    ) {
        fun resolve(reference: String?, label: String): String? {
            val value = reference?.trim()?.takeIf(String::isNotEmpty) ?: return null
            if (value.startsWith("system:")) return value
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
                if (strict) error("$label 未随排版包携带，导入已取消")
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
        config.scriptFonts = config.scriptFonts?.let { fonts ->
            SparseFontOverrides(
                default = fonts.default?.let { resolve(it, "脚本默认字体") },
                latin = fonts.latin?.let { resolve(it, "拉丁脚本字体") },
                cjk = fonts.cjk?.let { resolve(it, "CJK脚本字体") },
                other = fonts.other?.let { resolve(it, "其他脚本字体") },
            ).takeUnless { it.isEmpty() }
        }

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
        if (reference.startsWith("assets://")) return appCtx.assets.open(reference.removePrefix("assets://"))
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
        "textAccentColorNight", "textAccentColorEInk", "pageAnim", "pageAnimEInk",         "textFont",
        "scriptFonts",
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
        "tipFooterColorNight", "tipDividerColor", "headerMode", "showHeaderBackButton", "footerMode", "highlightRules",
        "paperEffect", "paperInkStrength", "readScrollFollowBackground",
        "readScrollFollowBackgroundNight", "readScrollFollowBackgroundEInk",
        "underlineDashLength", "underlineStrokeWidth",
        "ngReadStyleSource", "ngUnknownFields", "ngReaderSettings", "ngResourcePackageVersion",
    )
}
