package io.legado.app.help.ai

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private const val WORKS_ALBUM = "阅读 NG"

internal suspend fun saveAiImageWorksToAlbum(context: Context, ids: Set<String>): Set<String> =
    withContext(Dispatchers.IO) {
        val failed = mutableSetOf<String>()
        for (id in ids) {
            ensureActive()
            try {
                val source = requireNotNull(AiImageAssets.file(id)?.takeIf(File::isFile))
                val mime = requireNotNull(source.imageMimeType())
                val name = "ng-image-${UUID.randomUUID()}.${imageFileExtension(mime)}"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    saveToMediaStore(context, source, name, mime)
                } else {
                    saveToPublicPictures(context, source, name, mime)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                failed += id
            }
        }
        failed
    }

@RequiresApi(Build.VERSION_CODES.Q)
private suspend fun saveToMediaStore(context: Context, source: File, name: String, mime: String) {
    val resolver = context.contentResolver
    val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, mime)
        put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$WORKS_ALBUM")
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val uri = checkNotNull(resolver.insert(collection, values))
    var published = false
    try {
        checkNotNull(resolver.openOutputStream(uri, "w")).use { target ->
            source.inputStream().use { it.copyTo(target) }
        }
        currentCoroutineContext().ensureActive()
        val ready = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
        check(resolver.update(uri, ready, null, null) == 1)
        published = true
    } finally {
        if (!published) runCatching { resolver.delete(uri, null, null) }
    }
}

@Suppress("DEPRECATION")
private suspend fun saveToPublicPictures(context: Context, source: File, name: String, mime: String) {
    val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), WORKS_ALBUM)
    check(directory.mkdirs() || directory.isDirectory)
    val temporary = File.createTempFile(".ng-image-", ".tmp", directory)
    val target = File(directory, name)
    try {
        source.copyTo(temporary, overwrite = true)
        currentCoroutineContext().ensureActive()
        check(!target.exists() && temporary.renameTo(target))
        try {
            MediaScannerConnection.scanFile(context, arrayOf(target.path), arrayOf(mime), null)
        } catch (error: Exception) {
            target.delete()
            throw error
        }
    } finally {
        temporary.delete()
    }
}
