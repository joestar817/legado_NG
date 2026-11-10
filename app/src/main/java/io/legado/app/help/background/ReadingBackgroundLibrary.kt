package io.legado.app.help.background

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.AtomicFile
import androidx.annotation.Keep
import androidx.documentfile.provider.DocumentFile
import com.google.gson.annotations.SerializedName
import io.legado.app.help.resource.ResourceFiles
import io.legado.app.help.resource.ResourceInstallStore
import io.legado.app.utils.externalFiles
import io.legado.app.utils.inputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import splitties.init.appCtx
import java.io.File
import java.security.DigestInputStream
import java.security.MessageDigest

internal data class ReadingBackground(
    val id: String,
    val type: Int,
    val reference: String,
    val name: String,
    val source: String,
    val file: File? = null,
    val deletable: Boolean = false,
    val size: Long = 0,
    val modified: Long = 0,
)

internal data class ReadingBackgroundSnapshot(
    val entries: List<ReadingBackground>,
    val usages: Map<String, List<String>>,
    val usageError: String? = null,
)

internal data class BackgroundDeletionTarget(val entry: ReadingBackground, val hash: String)
internal data class BackgroundDeletionResult(val deleted: Set<String>, val failures: List<String>)

@Keep
internal data class ReadingBackgroundNames(
    @SerializedName("names") val names: Map<String, String> = emptyMap(),
)

/** Owns only library enumeration/import. Presets and downloaded state keep their existing owners. */
internal object ReadingBackgroundLibrary {
    val lock = Any()
    private val mutableRevision = MutableStateFlow(0L)
    val revision = mutableRevision.asStateFlow()
    private val directory get() = File(appCtx.externalFiles, "bg")
    private val namesFile get() = File(appCtx.filesDir, "reading-background-names.json")

    fun resolveFile(reference: String): File =
        if (reference.contains(File.separator)) File(reference) else File(directory, reference)

    internal fun isOwned(file: File, roots: List<File>): Boolean {
        val absolute = file.absoluteFile
        val canonical = file.canonicalFile
        return absolute == canonical && roots.any { canonical.parentFile == it.canonicalFile }
    }

    private fun isOwned(file: File): Boolean = isOwned(file, listOf(directory, ResourceFiles.backgrounds))

    fun scan(): ReadingBackgroundSnapshot = synchronized(lock) {
        val names = ResourceFiles.readJson(namesFile, ReadingBackgroundNames::class.java)?.names.orEmpty()
        val records = ResourceInstallStore.load().filter { it.entry.category.type == "background" }
        val references = runCatching { ReadingBackgroundUsage.pathsByLocation() }
        val files = linkedMapOf<String, File>()
        fun add(file: File) {
            if (file.isFile) files.putIfAbsent(file.canonicalPath, file)
        }
        directory.listFiles()?.filter {
            it.isFile && !it.name.startsWith('.') && !it.name.endsWith(".bak") && !it.name.endsWith(".new")
        }?.forEach(::add)
        ResourceFiles.backgroundFiles().forEach(::add)
        references.getOrDefault(emptyList()).forEach { (path, _) -> add(File(path)) }
        val custom = files.mapNotNull { (path, file) ->
            if (!isImage(file)) return@mapNotNull null
            val record = records.firstOrNull { resolveFile(it.target).canonicalPath == path }
            ReadingBackground(
                id = path, type = 2, reference = path,
                name = record?.entry?.item?.name ?: names[file.name] ?: file.nameWithoutExtension,
                source = if (!isOwned(file)) "外部" else if (record != null || file.parentFile?.canonicalFile == ResourceFiles.backgrounds.canonicalFile) "下载" else "导入",
                file = file, deletable = isOwned(file), size = file.length(), modified = file.lastModified(),
            )
        }.sortedWith(compareBy<ReadingBackground> { if (it.source == "下载") 0 else 1 }.thenBy { it.name })
        val assetNames = appCtx.assets.list("bg").orEmpty().toList()
        val preferred = listOf("午后沙滩.jpg", "宁静夜色.jpg", "山水墨影.jpg", "山水画.jpg")
        val builtIn = (preferred.filter(assetNames::contains) + assetNames.filterNot(preferred::contains)).map {
            ReadingBackground("asset:$it", 1, it, File(it).nameWithoutExtension, "内置")
        }
        ReadingBackgroundSnapshot(
            custom + builtIn,
            ReadingBackgroundUsage.match(files.keys, references.getOrDefault(emptyList())),
            references.exceptionOrNull()?.localizedMessage,
        )
    }

    private fun isImage(file: File): Boolean {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        return options.outWidth > 0 && options.outHeight > 0
    }

    /** Copy to private app storage; source gallery/document is never changed. Call on IO. */
    fun importImage(context: Context, uri: Uri): String = synchronized(lock) {
        directory.mkdirs()
        val originalName = if (uri.scheme == "content") {
            DocumentFile.fromSingleUri(context, uri)?.name
        } else uri.lastPathSegment
        val suffix = originalName?.substringAfterLast('.', "jpg")?.lowercase()
            ?.takeIf { it.matches(Regex("[a-z0-9]{1,8}")) } ?: "jpg"
        val extension = if (originalName?.endsWith(".9.png", true) == true) ".9.png" else ".$suffix"
        val pending = File.createTempFile(".import-", ".tmp", directory)
        try {
            val digest = MessageDigest.getInstance("MD5")
            DigestInputStream(uri.inputStream(context).getOrThrow(), digest).use { input ->
                pending.outputStream().use { output -> input.copyTo(output) }
            }
            check(isImage(pending)) { "无法读取这张图片" }
            val fileName = digest.digest().joinToString("") { "%02x".format(it) } + extension
            val file = File(directory, fileName)
            val names = ResourceFiles.readJson(namesFile, ReadingBackgroundNames::class.java)?.names.orEmpty()
            val atomic = AtomicFile(file)
            val output = atomic.startWrite()
            try {
                pending.inputStream().use { it.copyTo(output) }
                atomic.finishWrite(output)
            } catch (error: Throwable) {
                atomic.failWrite(output)
                throw error
            }
            ResourceFiles.writeJson(namesFile, ReadingBackgroundNames(names + (fileName to
                (originalName?.substringBeforeLast('.')?.removeSuffix(".9")?.takeIf(String::isNotBlank) ?: "导入图片"))))
            changed()
            fileName
        } finally {
            pending.delete()
        }
    }

    fun prepareDeletion(entries: List<ReadingBackground>): List<BackgroundDeletionTarget> = synchronized(lock) {
        entries.map { entry ->
            val file = requireNotNull(entry.file)
            check(entry.deletable && isOwned(file) && file.isFile && file.length() == entry.size &&
                file.lastModified() == entry.modified) { "图片已变化，请刷新后重试" }
            BackgroundDeletionTarget(entry, ResourceFiles.hash(file))
        }
    }

    /** Called only under ResourceRepository's installation mutex, then our library lock. */
    fun deleteLocked(targets: List<BackgroundDeletionTarget>): BackgroundDeletionResult = synchronized(lock) {
        val usages = ReadingBackgroundUsage.match(targets.map { it.entry.id }, ReadingBackgroundUsage.pathsByLocation())
        val deleted = linkedSetOf<String>()
        val failures = mutableListOf<String>()
        targets.forEach { target ->
            val entry = target.entry
            runCatching {
                val file = requireNotNull(entry.file)
                check(entry.deletable && isOwned(file)) { "仅可删除应用内的图片副本" }
                check(usages[entry.id].isNullOrEmpty()) { "仍被${usages[entry.id]?.first()}使用，请先更换背景" }
                check(file.isFile && ResourceFiles.hash(file) == target.hash) { "图片已变化，请刷新后重试" }
                check(file.delete()) { "删除失败，请重试" }
                deleted.add(entry.id)
            }.onFailure { failures.add("${entry.name}：${it.localizedMessage}") }
        }
        if (deleted.isNotEmpty()) changed()
        BackgroundDeletionResult(deleted, failures)
    }

    private fun changed() { mutableRevision.update { it + 1 } }
}
