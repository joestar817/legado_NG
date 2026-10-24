package io.legado.app.help.book

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.HttpUrl.Companion.toHttpUrl

/** 只编码新文件名，保留用户配置的 WebDAV 根路径。 */
internal fun bookExportWebDavUrl(directory: String, fileName: String): String =
    directory.toHttpUrl().newBuilder().addPathSegment(fileName).build().toString()

/** 本地文件已写完；上传失败单独反馈，取消仍由导出任务处理。 */
internal suspend fun attemptBookExportUpload(upload: suspend () -> Unit): Result<Unit> = try {
    currentCoroutineContext().ensureActive()
    upload()
    Result.success(Unit)
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    currentCoroutineContext().ensureActive()
    Result.failure(e)
}
