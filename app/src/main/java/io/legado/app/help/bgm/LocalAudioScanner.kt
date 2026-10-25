package io.legado.app.help.bgm

import android.net.Uri
import android.os.SystemClock
import io.legado.app.utils.FileDoc
import io.legado.app.utils.list
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import splitties.init.appCtx
import java.io.File

internal data class LocalAudioEntry(
    val file: FileDoc,
    val relativePath: String,
    val metadata: LocalAudioMetadata? = null,
    val error: String = "",
    val coverChecked: Boolean = false,
    val key: String = LocalAudioAccess.identity(file.uri),
) {
    val title: String get() = metadata?.title ?: file.name
}

/** Serial directory discovery; metadata decoding has a separate, bounded queue in the screen model. */
internal object LocalAudioScanner {
    suspend fun scan(root: FileDoc, directory: FileDoc, recursive: Boolean, showHidden: Boolean,
        onBatch: suspend (List<LocalAudioEntry>, String) -> Unit,
        onFailure: suspend (String) -> Unit) {
        val pending = ArrayDeque<Pair<FileDoc, String>>()
        val visited = HashSet<String>()
        val batch = ArrayList<LocalAudioEntry>()
        pending.add(directory to "")
        var lastDelivery = 0L
        while (pending.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val (folder, path) = pending.removeFirst()
            val children = try {
                val identity = if (folder.uri.scheme == "file") File(folder.uri.path.orEmpty()).canonicalPath
                    else LocalAudioAccess.identity(folder.uri)
                if (!visited.add(identity)) continue
                folder.list() ?: throw MusicProviderException("目录不可访问")
            }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { onFailure(path.ifBlank { folder.name }); continue }
            for (child in children) {
                currentCoroutineContext().ensureActive()
                val relative = if (path.isBlank()) child.name else "$path/${child.name}"
                val file = try {
                    if (child.uri.scheme == "file") child.copy(uri = Uri.fromFile(File(child.uri.path.orEmpty()).canonicalFile)) else child
                } catch (_: Exception) {
                    onFailure(relative)
                    continue
                }
                if (!showHidden && file.name.startsWith('.')) continue
                if (!LocalAudioAccess.belongsToRoot(file.uri, root.uri)) continue
                if (file.isDir) {
                    if (recursive) pending.add(file to relative)
                    else batch += LocalAudioEntry(file, relative)
                } else {
                    val hint = if (!LocalAudioFormat.isCandidate(file.name) && file.uri.scheme == "content")
                        runCatching { appCtx.contentResolver.getType(file.uri) }.getOrNull() else null
                    if (LocalAudioFormat.isCandidate(file.name, hint)) batch += LocalAudioEntry(file, relative)
                }
                if (batch.size >= 32 || (batch.isNotEmpty() && SystemClock.elapsedRealtime() - lastDelivery >= 150)) {
                    onBatch(batch.toList(), path.ifBlank { folder.name })
                    batch.clear()
                    lastDelivery = SystemClock.elapsedRealtime()
                }
            }
            if (batch.isNotEmpty()) { onBatch(batch.toList(), path.ifBlank { folder.name }); batch.clear() }
        }
    }
}
