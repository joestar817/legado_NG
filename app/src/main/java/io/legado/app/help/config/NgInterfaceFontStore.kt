package io.legado.app.help.config

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.util.LruCache
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** 界面字体与阅读正文的字体偏好分离；自定义字体使用私有副本。 */
internal object NgInterfaceFontStore {
    private const val CHOICE = "ngInterfaceFont.choice"
    private const val FILE = "ngInterfaceFont.file"
    private const val NAME = "ngInterfaceFont.name"
    const val DIRECTORY = "ngInterfaceFont.directory"
    private val cache = LruCache<String, Typeface>(4)
    private data class PreparedFontKey(val choice: String, val version: String)
    private val preparedFiles = LruCache<PreparedFontKey, File>(12)
    private var previewGeneration = 0L
    private val loader = Semaphore(2)
    suspend fun load(
        context: Context,
        choice: String,
        reloadFromSource: Boolean = false,
    ): Pair<Typeface, File?> {
        val generation = synchronized(preparedFiles) {
            if (reloadFromSource) {
                previewGeneration++
                preparedFiles.snapshot().keys.filter { it.choice == choice }
                    .forEach(preparedFiles::remove)
            }
            previewGeneration
        }
        return withContext(Dispatchers.IO) {
            loader.withPermit { prepare(context, choice, reloadFromSource, generation) }
        }
    }
    val systemChoices = listOf("system", "serif", "monospace")

    fun choice(context: Context): String = context.getPrefString(CHOICE)
        ?: NgThemeLibraryStore.activeTheme(context)?.let { theme ->
            theme.resolvePackageAsset(theme.resourceProfile?.appFont)?.toURI()?.toString()
        } ?: "system"
    fun name(context: Context): String = context.getPrefString(NAME).orEmpty()
    fun hasOverride(context: Context): Boolean = context.getPrefString(CHOICE) != null
    fun systemTypeface(choice: String): Typeface = when (choice) {
        "serif" -> Typeface.SERIF
        "monospace" -> Typeface.MONOSPACE
        else -> Typeface.DEFAULT
    }
    private fun fromFile(file: File): Typeface = synchronized(cache) {
        cache.get(file.absolutePath) ?: decodeFile(file).also { cache.put(file.absolutePath, it) }
    }
    private fun decodeFile(file: File): Typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        // createFromFile silently returns DEFAULT for an existing but invalid font on modern Android.
        requireNotNull(Typeface.Builder(file).build()) { "字体文件无法解析" }
    } else {
        Typeface.createFromFile(file)
    }
    fun typeface(context: Context): Typeface {
        val choice = choice(context)
        if (choice in systemChoices) return systemTypeface(choice)
        return runCatching { fromFile(File(context.getPrefString(FILE).orEmpty())) }.getOrDefault(Typeface.DEFAULT)
    }
    private suspend fun prepare(
        context: Context,
        choice: String,
        reloadFromSource: Boolean,
        generation: Long,
    ): Pair<Typeface, File?> {
        if (choice in systemChoices) return systemTypeface(choice) to null
        // Normal display uses the installed copy even if its original URI is no longer readable.
        if (!reloadFromSource && choice == context.getPrefString(CHOICE)) {
            val active = File(context.getPrefString(FILE).orEmpty())
            if (active.isFile) return fromFile(active) to active
        }
        val version = if (choice.startsWith("file:")) {
            val file = File(Uri.parse(choice).path.orEmpty())
            "${file.lastModified()}:${file.length()}"
        } else ""
        val sourceKey = PreparedFontKey(choice, version)
        if (!reloadFromSource) {
            synchronized(preparedFiles) { preparedFiles.get(sourceKey) }
                ?.takeIf { it.isFile }?.let { return fromFile(it) to it }
        }
        val root = File(context.cacheDir, "interface-font-preview").apply { mkdirs() }
        val temp = File.createTempFile("font-", ".tmp", root)
        try {
            val uri = Uri.parse(choice)
            val input = if (uri.scheme == "content") context.contentResolver.openInputStream(uri)
                else File(uri.path ?: choice).inputStream()
            requireNotNull(input) { "无法读取字体" }.use { source ->
                temp.outputStream().use { target ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = source.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= 64L * 1024 * 1024) { "字体文件超过64MB" }
                        target.write(buffer, 0, count)
                    }
                }
            }
            val digest = MessageDigest.getInstance("SHA-256")
            temp.inputStream().use { source ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = source.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                }
            }
            val target = File(root, digest.digest().joinToString("") { "%02x".format(it) } + ".font")
            if (!target.exists()) check(temp.renameTo(target) || target.isFile) { "无法缓存字体" }
            val typeface = fromFile(target)
            currentCoroutineContext().ensureActive()
            synchronized(preparedFiles) {
                // A preview that started before a reload must not republish its old source data.
                if (generation == previewGeneration) preparedFiles.put(sourceKey, target)
            }
            return typeface to target
        } finally { temp.delete() }
    }
    fun save(context: Context, choice: String, name: String, prepared: File?) {
        if (choice !in systemChoices) {
            val source = requireNotNull(prepared) { "请等待字体加载完成" }
            val root = File(context.filesDir, "interface-fonts").apply { mkdirs() }
            val target = File(root, source.name)
            if (source.canonicalPath != target.canonicalPath && !target.exists()) {
                val temporary = File.createTempFile("install-", ".tmp", root)
                try {
                    source.copyTo(temporary, overwrite = true)
                    check(temporary.renameTo(target) || target.isFile) { "无法保存字体" }
                } finally { temporary.delete() }
            }
            // Validate the final immutable file before publishing its preference reference.
            fromFile(target)
            context.putPrefString(FILE, target.absolutePath)
        }
        context.putPrefString(NAME, name)
        context.putPrefString(CHOICE, choice)
    }
}
