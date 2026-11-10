package io.legado.app.help.font

import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Process
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.AtomicFile
import androidx.annotation.Keep
import androidx.documentfile.provider.DocumentFile
import com.google.gson.annotations.SerializedName
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.NgInterfaceFontStore
import io.legado.app.help.resource.ResourceFiles
import io.legado.app.help.resource.ResourceRepository
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

internal data class FontFileEntry(
    val id: String,
    val name: String,
    val uri: String,
    val size: Long,
    val managed: LocalFontAsset?,
    val canDelete: Boolean,
    val directoryUri: String?,
    val sha256: String?,
    val deleteUnavailableReason: String? = null,
)

internal data class FontDirectorySnapshot(val entries: List<FontFileEntry>, val errors: List<String>)

/** Read-only directory discovery. Only resource downloads create app-owned font files. */
internal object FontDirectoryCatalog {
    private data class Stamp(val uri: String, val size: Long, val modified: Long)
    private data class Fingerprint(val sha256: String, val size: Long)
    private data class Metadata(val name: String, val size: Long, val modified: Long, val canDelete: Boolean)
    @Keep
    private data class PendingFontReset(
        @SerializedName("uri") val uri: String,
        @SerializedName("directory") val directory: String?,
    )

    private val pendingResetFile get() = File(ResourceFiles.root, "pending-external-font-reset.json")
    private val fingerprints = object : LinkedHashMap<Stamp, Fingerprint>(32, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Stamp, Fingerprint>?) = size > 256
    }

    fun identity(value: String): String = requireNotNull(
        FontStoragePaths.normalize(value),
    ) { "无效字体路径" }

    fun list(refresh: Boolean = false): List<FontFileEntry> = scan(refresh).entries

    /** Put the resource that opened a picker first without changing the active font. */
    fun prioritize(entries: List<FontFileEntry>, preferredPath: String?): List<FontFileEntry> {
        if (preferredPath.isNullOrBlank()) return entries
        val preferred = runCatching { identity(preferredPath) }.getOrNull() ?: return entries
        val index = entries.indexOfFirst { it.id == preferred }
        if (index <= 0) return entries
        return listOf(entries[index]) + entries.take(index) + entries.drop(index + 1)
    }

    fun scan(refresh: Boolean = false): FontDirectorySnapshot {
        if (refresh) synchronized(fingerprints) { fingerprints.clear() }
        val entries = linkedMapOf<String, FontFileEntry>()
        val errors = mutableListOf<String>()
        synchronized(FontLibraryStore.lock) {
            runCatching { recoverPendingReset() }.onFailure {
                errors += "字体删除后尚未完成设置切换，请检查目录权限后重试"
            }
        }
        FontLibraryStore.list(refresh).forEach { asset ->
            asset.paths.forEach { path ->
                val file = File(path)
                val one = asset.copy(path = path, paths = listOf(path), size = file.length())
                val entry = FontFileEntry(identity(path), asset.name, file.toURI().toString(), file.length(),
                    one, file.parentFile?.canWrite() == true, null, asset.id)
                entries[entry.id] = entry
            }
        }
        directories().forEach { directory ->
            try {
                val uri = Uri.parse(directory)
                if (uri.scheme == "content") {
                    val tree = requireNotNull(DocumentFile.fromTreeUri(appCtx, uri)) { "目录不可用" }
                    require(tree.isDirectory && tree.canRead()) { "目录不可读取，请重新授权" }
                    // Query failures must remain distinguishable from an empty directory.
                    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
                    val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE)
                    requireNotNull(appCtx.contentResolver.query(childrenUri, projection, null, null, null)) { "无法读取目录" }.use { cursor ->
                        while (cursor.moveToNext()) {
                            if (cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) continue
                            val name = cursor.getString(1).orEmpty()
                            if (!isFontName(name)) continue
                            val child = DocumentsContract.buildDocumentUriUsingTree(uri, cursor.getString(0))
                            addExternal(entries, child, directory, name, refresh, errors)
                        }
                    }
                } else {
                    val folder = localFile(directory)
                    require(folder.isDirectory && folder.canRead()) { "目录不可读取" }
                    requireNotNull(folder.listFiles()) { "无法读取目录" }.filter { it.isFile && isFontName(it.name) }.forEach {
                        addExternal(entries, Uri.fromFile(it), directory, it.name, refresh, errors)
                    }
                }
            } catch (_: Exception) {
                errors += "部分字体目录无法读取，请重新选择目录"
            }
        }
        // Old selections may reference a single file outside the currently selected directory.
        appCtx.getPrefString("ngInterfaceFont.choice")?.takeUnless { it.isBlank() || it in NgInterfaceFontStore.systemChoices }?.let { choice ->
            if (identity(choice) !in entries) {
                addExternal(entries, Uri.parse(choice), null, NgInterfaceFontStore.name(appCtx), refresh, errors)
            }
        }
        val sourceNames = entries.values.filter { it.managed == null && it.sha256 != null }.associate { it.sha256 to it.name }
        val named = entries.values.map { entry ->
            if (entry.managed != null && entry.name.matches(Regex("本地字体 [a-f0-9]{8}"))) {
                sourceNames[entry.sha256]?.let { entry.copy(name = it) } ?: entry
            } else entry
        }
        return FontDirectorySnapshot(named.sortedWith(compareBy<FontFileEntry> { it.name.lowercase() }.thenBy { it.id }), errors.distinct())
    }

    fun registerDirectory(uri: Uri) {
        if (uri.scheme == "content") {
            val tree = requireNotNull(DocumentFile.fromTreeUri(appCtx, uri)) { "目录不可用" }
            require(tree.isDirectory && tree.canRead()) { "无法读取目录，请重新授权" }
        } else {
            val folder = localFile(uri.toString())
            require(folder.isDirectory && folder.canRead()) { "无法读取目录" }
        }
        appCtx.putPrefString(NgInterfaceFontStore.DIRECTORY, uri.toString())
        FontLibraryStore.notifyChanged()
    }

    suspend fun delete(entry: FontFileEntry, confirmedUsages: List<String>? = null) = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        if (entry.managed != null) {
            ResourceRepository.deleteFont(entry.managed, confirmedUsages)
            return@withContext
        }
        withContext(NonCancellable) {
            synchronized(FontLibraryStore.lock) {
                recoverPendingReset()
                deleteExternal(entry, confirmedUsages)
            }
        }
    }

    private fun deleteExternal(entry: FontFileEntry, confirmedUsages: List<String>?) {
        val allowed = entry.directoryUri?.let { directory ->
            directory in directories() && isChild(entry.uri, directory)
        } ?: (appCtx.getPrefString("ngInterfaceFont.choice")
            ?.takeUnless { it.isBlank() || it in NgInterfaceFontStore.systemChoices }
            ?.let { identity(it) == entry.id } == true)
        check(allowed) { "字体来源已变化，请刷新列表后重试" }
        val current = readExternal(Uri.parse(entry.uri), entry.directoryUri, entry.name, refresh = true)
        check(current.id == entry.id && current.sha256 != null && current.sha256 == entry.sha256) {
            "字体文件已变化，请刷新后重新确认"
        }
        check(current.canDelete) { current.deleteUnavailableReason ?: "没有删除权限或此来源不支持删除，请重新选择目录授权" }
        FontUsageInspector.verifyDeletion(listOf(entry.uri), confirmedUsages)
        if (confirmedUsages != null) {
            ResourceFiles.writeJson(pendingResetFile, PendingFontReset(entry.uri, entry.directoryUri))
        }
        val uri = Uri.parse(current.uri)
        if (uri.scheme == "content") {
            check(DocumentsContract.deleteDocument(appCtx.contentResolver, uri)) { "未能删除原文件，请检查目录权限" }
            confirmDocumentDeleted(uri, entry.directoryUri)
        } else {
            val file = localFile(current.uri)
            check(file.isFile && file.delete() && !file.exists()) { "未能删除原文件，请检查目录权限" }
        }
        if (confirmedUsages != null) completePendingReset(entry.uri)
        synchronized(fingerprints) { fingerprints.keys.removeAll { identity(it.uri) == entry.id } }
        FontLibraryStore.notifyChanged()
    }

    /** Recovery only switches references after confirming absence; it never deletes an external file. */
    private fun recoverPendingReset() {
        val pending = ResourceFiles.readJson(pendingResetFile, PendingFontReset::class.java) ?: return
        val uri = Uri.parse(pending.uri)
        val exists = if (uri.scheme == "content") {
            documentExists(uri, pending.directory)
        } else {
            val file = localFile(pending.uri)
            val names = requireNotNull(file.parentFile?.list()) { "无法核对字体目录" }
            file.name in names
        }
        if (exists) {
            AtomicFile(pendingResetFile).delete()
        } else {
            completePendingReset(pending.uri)
            FontLibraryStore.notifyChanged()
        }
    }

    private fun completePendingReset(uri: String) {
        FontUsageInspector.resetAfterDeletion(listOf(uri))
        AtomicFile(pendingResetFile).delete()
    }

    /** Missing documents may return null or lose their grant; confirm absence before reporting success. */
    internal fun confirmDocumentDeleted(uri: Uri, directory: String?) {
        check(!documentExists(uri, directory)) { "原文件仍存在，未能删除" }
    }

    private fun documentExists(uri: Uri, directory: String?): Boolean {
        val directoryConfirmed = directory?.let {
            if (isChild(uri.toString(), it, requireComplete = true)) return true
            true
        } ?: false
        val documentCursor = try {
            appCtx.contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // A deleted tree URI can fail enforceTree with a missing-file exception, not just lose its grant.
            // Only a complete parent listing can independently prove absence when that query fails.
            if (!directoryConfirmed) throw IllegalStateException("无法确认删除结果，请刷新后重试", error)
            null
        }
        val documentExists = documentCursor?.use { cursor ->
            // Keep malformed cursor/iteration failures outside the query exception handling.
            cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            when {
                cursor.moveToFirst() -> true
                isCompleteDocumentQuery(cursor) -> false
                else -> null
            }
        }
        return documentExists ?: if (directoryConfirmed) false else error("无法确认删除结果，请刷新后重试")
    }

    private fun isCompleteDocumentQuery(cursor: Cursor): Boolean =
        !cursor.extras.getBoolean(DocumentsContract.EXTRA_LOADING) &&
            cursor.extras.getCharSequence(DocumentsContract.EXTRA_ERROR).isNullOrEmpty()

    private fun directories(): List<String> = listOfNotNull(
        appCtx.getPrefString(NgInterfaceFontStore.DIRECTORY), appCtx.getPrefString(PreferKey.fontFolder),
    ).filter { it.isNotBlank() }.distinct()

    private fun addExternal(
        entries: MutableMap<String, FontFileEntry>, uri: Uri, directory: String?, name: String,
        refresh: Boolean, errors: MutableList<String>,
    ) {
        try {
            val id = identity(uri.toString())
            if (entries[id]?.managed != null) return
            val entry = readExternal(uri, directory, name, refresh)
            val previous = entries[id]
            if (previous == null || entry.canDelete && !previous.canDelete) entries[id] = entry
        } catch (_: Exception) { errors += "部分字体文件无法读取，请检查目录权限" }
    }

    private fun readExternal(uri: Uri, directory: String?, fallbackName: String, refresh: Boolean): FontFileEntry {
        val value = uri.toString()
        val metadata = if (uri.scheme == "content") {
            val projection = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED, DocumentsContract.Document.COLUMN_FLAGS,
                DocumentsContract.Document.COLUMN_MIME_TYPE)
            requireNotNull(appCtx.contentResolver.query(uri, projection, null, null, null)) { "字体不可读取" }.use { cursor ->
                check(cursor.moveToFirst()) { "字体已不存在" }
                fun text(column: String): String? = cursor.getColumnIndex(column).takeIf { it >= 0 }?.let(cursor::getString)
                fun number(column: String): Long = cursor.getColumnIndex(column).takeIf { it >= 0 }?.let(cursor::getLong) ?: 0L
                check(text(DocumentsContract.Document.COLUMN_MIME_TYPE) != DocumentsContract.Document.MIME_TYPE_DIR) { "请选择字体文件" }
                Metadata(
                    text(OpenableColumns.DISPLAY_NAME)?.takeIf { it.isNotBlank() } ?: fallbackName,
                    number(OpenableColumns.SIZE), number(DocumentsContract.Document.COLUMN_LAST_MODIFIED),
                    number(DocumentsContract.Document.COLUMN_FLAGS) and DocumentsContract.Document.FLAG_SUPPORTS_DELETE.toLong() != 0L &&
                        appCtx.checkUriPermission(uri, Process.myPid(), Process.myUid(), Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == PackageManager.PERMISSION_GRANTED,
                )
            }
        } else {
            val file = localFile(value)
            require(file.isFile && file.canRead()) { "字体不可读取" }
            Metadata(file.name, file.length(), file.lastModified(), file.parentFile?.canWrite() == true)
        }
        val stamp = Stamp(value, metadata.size, metadata.modified)
        val cached = synchronized(fingerprints) { if (refresh) null else fingerprints[stamp] }
        val fingerprint = cached ?: (if (uri.scheme == "content") appCtx.contentResolver.openInputStream(uri)
            else localFile(value).inputStream()).let { input ->
            requireNotNull(input) { "字体不可读取" }.use(::fingerprint)
        }.also { synchronized(fingerprints) { fingerprints[stamp] = it } }
        val unresolved = FontStoragePaths.hasUnresolvedVolume(value)
        return FontFileEntry(identity(value), metadata.name, value, fingerprint.size, null, metadata.canDelete && !unresolved,
            directory, fingerprint.sha256, if (unresolved) "无法核对此存储卷的字体使用情况，请使用文件管理器删除" else null)
    }

    private fun isChild(value: String, directory: String, requireComplete: Boolean = false): Boolean {
        val uri = Uri.parse(value)
        val parent = Uri.parse(directory)
        if (parent.scheme != "content") return localFile(value).canonicalFile.parentFile == localFile(directory).canonicalFile
        if (uri.scheme != "content" || uri.authority != parent.authority) return false
        // Match an actual current tree child; do not authorize arbitrary URIs merely by a prefix.
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(parent, DocumentsContract.getTreeDocumentId(parent))
        return requireNotNull(appCtx.contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)) {
            "无法核对目录权限"
        }.use { cursor ->
            if (requireComplete) check(isCompleteDocumentQuery(cursor)) { "无法确认删除结果，请刷新后重试" }
            val documentIdColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            var found = false
            while (cursor.moveToNext()) {
                val child = DocumentsContract.buildDocumentUriUsingTree(parent, cursor.getString(documentIdColumn))
                if (identity(child.toString()) == identity(value)) found = true
            }
            found
        }
    }

    private fun isFontName(name: String) = name.substringAfterLast('.', "").lowercase() in setOf("ttf", "otf")
    private fun localFile(value: String): File {
        File(value).takeIf { it.isAbsolute }?.let { return it }
        val uri = Uri.parse(value)
        require(uri.scheme == "file") { "无效本地字体路径" }
        return File(java.net.URI(value.replace(" ", "%20")))
    }
    private fun fingerprint(input: InputStream): Fingerprint {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = ByteArray(32 * 1024)
        var size = 0L
        while (true) {
            val count = input.read(bytes)
            if (count < 0) break
            digest.update(bytes, 0, count)
            size += count
        }
        return Fingerprint(digest.digest().joinToString("") { "%02x".format(it) }, size)
    }
}
