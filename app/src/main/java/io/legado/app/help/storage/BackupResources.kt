package io.legado.app.help.storage

import android.net.Uri
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ReadHighlightRuleStore
import io.legado.app.help.config.ThemeConfig
import io.legado.app.utils.externalFiles
import splitties.init.appCtx
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipInputStream

/** Custom resources for native NG backups; bundled assets retain their built-in references. */
internal object BackupResources {
    const val MANIFEST = "ngBackup.json"
    const val ASSETS = "resources"
    private const val TOKEN = "backup-resource://"
    private const val MAX_FILE = 256L * 1024 * 1024
    private const val MAX_TOTAL = 1024L * 1024 * 1024
    private const val MAX_ENTRIES = 10000

    fun safeFile(root: File, path: String): File {
        require(path.isNotBlank() && !path.startsWith('/') && '\\' !in path && ':' !in path &&
            path.split('/').none { it == ".." || it == "." || it.isEmpty() }) { "备份包含非法路径" }
        return File(root, path).canonicalFile.also {
            require(it.toPath().startsWith(root.canonicalFile.toPath())) { "备份路径越界" }
        }
    }

    /** Stream limits also apply to old backups, before any app data is changed. */
    fun extract(input: InputStream, root: File) {
        root.mkdirs()
        var total = 0L
        val names = hashSetOf<String>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.trimEnd('/')
                require(names.add(name) && names.size <= MAX_ENTRIES) { "备份条目重复或过多" }
                val file = safeFile(root, name)
                if (entry.isDirectory) { file.mkdirs(); continue }
                file.parentFile!!.mkdirs()
                file.outputStream().use { output ->
                    var size = 0L
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        size += count; total += count
                        require(size <= MAX_FILE && total <= MAX_TOTAL) { "备份解压后体积过大" }
                        output.write(buffer, 0, count)
                    }
                }
            }
        }
    }

    fun prepare(root: File, modules: Set<String>, preferences: Map<String, *>): Map<String, Any?> {
        val collector = Collector(root)
        val prefs = preferences.toMutableMap()
        if (BackupModule.BOOKS.id in modules) {
            val file = File(root, "bookshelf.json")
            if (file.isFile) {
                val books = JsonParser.parseString(file.readText()).asJsonArray
                books.forEach { element ->
                    val book = element.asJsonObject
                    book.get("customCoverUrl")?.takeIf { !it.isJsonNull }?.asString?.let { ref ->
                        if (ref.startsWith('/') || ref.startsWith("file:") || ref.startsWith("content:")) {
                            book.addProperty("customCoverUrl", collector.copy(ref))
                        }
                    }
                }
                file.writeText(books.toString())
            }
        }
        if (BackupModule.READER.id in modules) {
            listOf(ReadBookConfig.configFileName, ReadBookConfig.shareConfigFileName).forEach { name ->
                val file = File(root, name)
                val json = JsonParser.parseString(file.readText())
                val configs = if (json.isJsonArray) json.asJsonArray.toList() else listOf(json)
                configs.forEach { element ->
                    val config = element.asJsonObject
                    listOf("", "Night", "EInk").forEach { suffix ->
                        val typeKey = "bgType$suffix"
                        val valueKey = "bgStr$suffix"
                        val type = config.get(typeKey)?.asInt ?: 0
                        // Type 1 is an APK asset. Keep both its name and type for the reader.
                        if (type == 2) {
                            val value = config.get(valueKey).asString
                            val ref = if ('/' !in value && ':' !in value) File(appCtx.externalFiles, "bg/$value").path else value
                            config.addProperty(valueKey, collector.copy(ref))
                        }
                    }
                    listOf("textFont", "titleFont", "headerFont", "footerFont").forEach { key ->
                        config.get(key)?.takeIf { !it.isJsonNull && it.asString.isNotBlank() }?.let {
                            config.addProperty(key, collector.copy(it.asString))
                        }
                    }
                    config.getAsJsonArray("highlightRules")?.let { collectRules(it, collector) }
                }
                file.writeText(json.toString())
            }
            val file = File(root, ReadHighlightRuleStore.fileName)
            val rules = JsonParser.parseString(file.readText()).asJsonArray
            collectRules(rules, collector)
            file.writeText(rules.toString())
        }
        if (BackupModule.APPEARANCE.id in modules) {
            val themeFile = File(root, ThemeConfig.configFileName)
            val legacyThemes = JsonParser.parseString(themeFile.readText()).asJsonArray
            legacyThemes.forEach { element ->
                val theme = element.asJsonObject
                theme.get("backgroundImgPath")?.takeIf { !it.isJsonNull && it.asString.isNotBlank() }?.let {
                    theme.addProperty("backgroundImgPath", collector.copy(it.asString))
                }
            }
            themeFile.writeText(legacyThemes.toString())
            if (prefs["ngInterfaceFont.choice"] in listOf("system", "serif", "monospace")) prefs.remove("ngInterfaceFont.file")
            listOf(PreferKey.bgImage, PreferKey.bgImageN, "ngInterfaceFont.file").forEach { key ->
                (prefs[key] as? String)?.takeIf(String::isNotBlank)?.let { prefs[key] = collector.copy(it) }
            }
            // The private font copy remains valid even when the original SAF grant has gone away.
            if ((prefs["ngInterfaceFont.file"] as? String)?.startsWith(TOKEN) == true &&
                prefs["ngInterfaceFont.choice"] !in listOf("system", "serif", "monospace")) {
                prefs["ngInterfaceFont.choice"] = prefs["ngInterfaceFont.file"]
            }
            (prefs["ngManagedThemes.v1"] as? String)?.let { raw ->
                val themes = JsonParser.parseString(raw).asJsonArray
                themes.forEach { item ->
                    val theme = item.asJsonObject
                    theme.get("packageRootPath")?.takeIf { !it.isJsonNull && it.asString.isNotBlank() }?.let {
                        val oldRoot = it.asString
                        val newRoot = collector.directory(File(oldRoot))
                        theme.addProperty("packageRootPath", newRoot)
                        listOf("lightBackground", "darkBackground").forEach { key ->
                            theme.getAsJsonObject(key)?.get("path")?.takeIf { !it.isJsonNull }?.let { path ->
                                val value = path.asString
                                if (value.startsWith("$oldRoot/")) {
                                    theme.getAsJsonObject(key).addProperty("path", newRoot + value.removePrefix(oldRoot))
                                }
                            }
                        }
                    }
                    listOf("lightBackground", "darkBackground").forEach { key ->
                        theme.getAsJsonObject(key)?.get("path")?.takeIf { !it.isJsonNull && it.asString.isNotBlank() }?.let {
                            if (!it.asString.startsWith(TOKEN)) theme.getAsJsonObject(key).addProperty("path", collector.copy(it.asString))
                        }
                    }
                }
                prefs["ngManagedThemes.v1"] = themes.toString()
            }
        }
        if (BackupModule.COVERS.id in modules) {
            listOf(PreferKey.defaultCover, PreferKey.defaultCoverDark).forEach { key ->
                (prefs[key] as? String)?.takeIf(String::isNotBlank)?.let { prefs[key] = collector.copy(it) }
            }
            (prefs["ngCoverAlbumLibrary.v1"] as? String)?.let { raw ->
                val json = JsonParser.parseString(raw)
                prefs["ngCoverAlbumLibrary.v1"] = transform(json) { value ->
                    if (value.startsWith('/') || value.startsWith("file:") || value.startsWith("content:")) collector.copy(value)
                    else value
                }.toString()
            }
        }
        collector.manifest.add("modules", JsonArray().apply { modules.sorted().forEach { add(it) } })
        File(root, MANIFEST).writeText(collector.manifest.toString())
        return prefs
    }

    private fun collectRules(rules: JsonArray, collector: Collector) {
        rules.forEach { element ->
            val rule = element.asJsonObject
            listOf("bgImage", "fontPath").forEach { key ->
                rule.get(key)?.takeIf { !it.isJsonNull && it.asString.isNotBlank() }?.let {
                    rule.addProperty(key, collector.copy(it.asString))
                }
            }
        }
    }

    private class Collector(private val root: File) {
        val manifest = JsonObject().apply { addProperty("version", 1) }
        private val entries = JsonObject().also { manifest.add("files", it) }
        private val refs = hashMapOf<String, String>()
        private var total = 0L

        fun directory(source: File): String = refs.getOrPut(source.path) {
            require(source.isDirectory) { "主题资源目录不存在：${source.name}" }
            val targetRoot = "$ASSETS/themes/${UUID.randomUUID()}"
            safeFile(root, targetRoot).mkdirs()
            source.walkTopDown().filter(File::isFile).forEach { file ->
                require(file.canonicalFile.toPath().startsWith(source.canonicalFile.toPath())) { "主题资源路径越界" }
                write(file.inputStream(), "$targetRoot/${file.relativeTo(source).invariantSeparatorsPath}")
            }
            "$TOKEN$targetRoot"
        }

        fun copy(reference: String): String = refs.getOrPut(reference) {
            // Bundled resources are supplied by the APK and remain usable after restore.
            if (reference.startsWith(TOKEN) || reference.startsWith("assets://") ||
                reference.startsWith("asset://") || reference.startsWith("file:///android_asset/")) {
                return@getOrPut reference
            }
            val uri = Uri.parse(reference)
            val input = when {
                uri.scheme == "https" || uri.scheme == "http" -> java.net.URL(reference).openConnection().apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                }.getInputStream()
                uri.scheme == "content" -> appCtx.contentResolver.openInputStream(uri)
                uri.scheme == "file" -> File(requireNotNull(uri.path)).inputStream()
                else -> File(reference).inputStream()
            } ?: error("无法读取备份资源：$reference")
            val extension = reference.substringAfterLast('/').substringAfter('.', "bin")
                .takeIf { it.matches(Regex("[A-Za-z0-9.]{1,16}")) } ?: "bin"
            val temporary = "$ASSETS/files/${UUID.randomUUID()}.$extension"
            write(input, temporary)
            require(safeFile(root, temporary).length() > 0) { "备份资源为空：$reference" }
            "$TOKEN$temporary"
        }

        private fun write(input: InputStream, path: String) {
            val file = safeFile(root, path)
            file.parentFile!!.mkdirs()
            input.use { source ->
                file.outputStream().use { output ->
                    var size = 0L
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        size += count; total += count
                        require(size <= MAX_FILE && total <= MAX_TOTAL) { "备份资源体积超过限制" }
                        output.write(buffer, 0, count)
                    }
                }
            }
            require(entries.size() < MAX_ENTRIES - 100) { "备份资源过多" }
            entries.addProperty(path, hash(file))
        }
    }

    fun finishManifest(root: File, files: List<String>) {
        val file = File(root, MANIFEST)
        val json = JsonParser.parseString(file.readText()).asJsonObject
        val entries = json.getAsJsonObject("files")
        files.filter { it != MANIFEST && it != ASSETS }.forEach { name ->
            File(root, name).takeIf(File::isFile)?.let { entries.addProperty(name, hash(it)) }
        }
        file.writeText(json.toString())
        val actualFiles = root.walkTopDown().filter(File::isFile).toList()
        require(actualFiles.size <= MAX_ENTRIES && actualFiles.all { it.length() <= MAX_FILE } &&
            actualFiles.sumOf { it.length() } <= MAX_TOTAL) { "备份体积超过限制" }
    }

    fun validate(root: File): Set<String>? {
        val file = File(root, MANIFEST)
        if (!file.exists()) return null
        require(file.length() <= 5 * 1024 * 1024) { "备份清单过大" }
        val manifest = JsonParser.parseString(file.readText()).asJsonObject
        require(manifest.get("version").asInt == 1) { "不支持的备份版本" }
        val modules = manifest.getAsJsonArray("modules").map { it.asString }.toSet()
        require(modules.isNotEmpty() && modules.all { id -> BackupModule.entries.any { it.id == id } }) { "备份模块无效" }
        val entries = manifest.getAsJsonObject("files")
        entries.entrySet().forEach { (path, digest) ->
            require(path.startsWith("$ASSETS/") || path == "config.xml" || BackupModules.fileModule(path).id in modules) {
                "备份文件不属于已选模块：$path"
            }
            val resource = safeFile(root, path)
            require(resource.isFile && hash(resource) == digest.asString) { "备份文件缺失或损坏：$path" }
        }
        root.walkTopDown().filter(File::isFile).forEach {
            val path = it.relativeTo(root).invariantSeparatorsPath
            require(path == MANIFEST || entries.has(path)) { "备份存在未登记的文件：$path" }
        }
        require(entries.has("config.xml")) { "备份缺少设置文件" }
        if (BackupModule.READER.id in modules) {
            listOf(ReadBookConfig.configFileName, ReadBookConfig.shareConfigFileName, ReadHighlightRuleStore.fileName).forEach {
                require(entries.has(it)) { "备份缺少阅读配置：$it" }
            }
        }
        return modules
    }

    /** Validate every token before installing immutable resources or restoring any database row. */
    fun restoreResources(root: File, preferences: Map<String, *>): Map<String, Any?> {
        val installed = File(appCtx.filesDir, "ng_backup_resources/${UUID.randomUUID()}")
        fun resolve(value: String): String {
            if (!value.startsWith(TOKEN)) return value
            val relative = value.removePrefix(TOKEN)
            require(relative.startsWith("$ASSETS/")) { "无效的备份资源引用" }
            require(safeFile(root, relative).exists()) { "备份资源缺失：$relative" }
            return safeFile(installed, relative).path
        }
        val configs = listOf("bookshelf.json", ReadBookConfig.configFileName, ReadBookConfig.shareConfigFileName, ReadHighlightRuleStore.fileName, ThemeConfig.configFileName)
            .map { File(root, it) }.filter(File::isFile).associateWith { file ->
                transform(JsonParser.parseString(file.readText()), ::resolve).toString()
            }
        val prefs = preferences.mapValues { (key, value) ->
            when {
                value !is String -> value
                key == "ngManagedThemes.v1" || key == "ngCoverAlbumLibrary.v1" ->
                    transform(JsonParser.parseString(value), ::resolve).toString()
                key in setOf(PreferKey.bgImage, PreferKey.bgImageN, PreferKey.defaultCover,
                    PreferKey.defaultCoverDark, "ngInterfaceFont.file", "ngInterfaceFont.choice") -> resolve(value)
                else -> value
            }
        }
        val assets = File(root, ASSETS)
        try {
            if (assets.exists()) check(assets.copyRecursively(File(installed, ASSETS))) { "无法安装备份资源" }
            configs.forEach { (file, text) -> file.writeText(text) }
        } catch (error: Throwable) {
            installed.deleteRecursively()
            throw error
        }
        return prefs
    }

    internal fun transform(value: JsonElement, replace: (String) -> String): JsonElement {
        val fields = setOf("bgStr", "bgStrNight", "bgStrEInk", "textFont", "titleFont", "headerFont",
            "footerFont", "bgImage", "fontPath", "backgroundImgPath", "customCoverUrl", "path",
            "packageRootPath", "lightImages", "darkImages")
        fun visit(node: JsonElement, field: String?): JsonElement = when {
            node.isJsonObject -> JsonObject().apply {
                node.asJsonObject.entrySet().forEach { (key, child) -> add(key, visit(child, key)) }
            }
            node.isJsonArray -> JsonArray().apply { node.asJsonArray.forEach { add(visit(it, field)) } }
            field in fields && node.isJsonPrimitive && node.asJsonPrimitive.isString -> JsonPrimitive(replace(node.asString))
            else -> node
        }
        return visit(value, null)
    }

    private fun hash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
