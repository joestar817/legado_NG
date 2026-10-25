package io.legado.app.model.remote

import com.google.gson.annotations.SerializedName
import io.legado.app.data.entities.Book
import io.legado.app.help.book.getRemoteUrl
import io.legado.app.model.analyzeRule.CustomUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import io.legado.app.data.appDb
import io.legado.app.help.AppWebDav
import io.legado.app.lib.webdav.Authorization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.net.Uri
import androidx.core.net.toUri
import java.io.File

/** A file identity, independent of display name, bookshelf identity and credentials. */
internal data class WebDavBookLocation(
    @SerializedName("serverId") val serverId: Long? = null,
    @SerializedName("path") val path: String,
) {
    val key: String get() = webDavBookKey(serverId, path)
    val directory: String get() = path.substringBeforeLast('/') + "/"

    companion object {
        const val EXTRA_SERVER = "webdavBookServer"
        const val EXTRA_PATH = "webdavBookPath"
        const val EXTRA_CHECK_UPDATE = "webdavBookCheckUpdate"

        fun fromBook(book: Book): WebDavBookLocation? {
            val remote = book.getRemoteUrl() ?: return null
            val url = CustomUrl(remote)
            val id = when (val value = url.getAttr()["serverID"]) {
                null -> null
                is Number -> value.toLong().also { require(value.toDouble() == it.toDouble()) { "无效的 WebDAV 服务器 ID" } }
                is String -> value.toLongOrNull() ?: error("无效的 WebDAV 服务器 ID")
                else -> error("无效的 WebDAV 服务器 ID")
            }
            return WebDavBookLocation(id, url.getUrl())
        }
    }
}

internal fun webDavBookKey(serverId: Long?, path: String): String =
    "${serverId ?: "default"}\n${normalizedWebDavBookUrl(path)}"

internal fun normalizedWebDavBookUrl(path: String): String {
    val url = CustomUrl(path).getUrl().toHttpUrl()
    val builder = url.newBuilder().encodedPath("/").fragment(null)
    url.pathSegments.forEach { builder.addPathSegment(it) }
    return builder.build().toString()
}

internal suspend fun webDavBookManager(serverId: Long?): RemoteBookWebDav = withContext(Dispatchers.IO) {
    if (serverId == null) AppWebDav.defaultBookWebDav ?: error("WebDAV 没有配置")
    else {
        val config = appDb.serverDao.get(serverId)?.getWebDavConfig() ?: error("原 WebDAV 服务器已不存在")
        RemoteBookWebDav(config.url, Authorization(config), serverId)
    }
}

internal fun webDavLocalUri(path: String): Uri =
    if (path.startsWith("content://") || path.startsWith("file:")) path.toUri() else Uri.fromFile(File(path))

internal enum class WebDavBookChange {
    SAME, REMOTE_CHANGED, LOCAL_CHANGED, CONFLICT, UNTRACKED, LOCAL_MISSING,
}

/** Unknown baselines never grant permission to replace a local book. */
internal fun webDavBookChange(baseline: String?, local: String?, remote: String): WebDavBookChange = when {
    local == null -> WebDavBookChange.LOCAL_MISSING
    local == remote -> WebDavBookChange.SAME
    baseline == null -> WebDavBookChange.UNTRACKED
    local != baseline && remote != baseline -> WebDavBookChange.CONFLICT
    local != baseline -> WebDavBookChange.LOCAL_CHANGED
    else -> WebDavBookChange.REMOTE_CHANGED
}

internal enum class WebDavBookChoice { KEEP_LOCAL, SAVE_COPY, USE_CLOUD }

internal data class WebDavBookConflict(
    val fileName: String,
    val change: WebDavBookChange,
    val sameWork: Boolean = false,
    val allowCopy: Boolean = true,
)

/** Stored file checkpoints; reading progress and annotations remain in the existing database. */
internal data class WebDavBookRecord(
    @SerializedName("serverId") val serverId: Long? = null,
    @SerializedName("path") val path: String,
    @SerializedName("localUri") val localUri: String,
    @SerializedName("sha256") val sha256: String,
    @SerializedName("remoteModified") val remoteModified: Long,
    @SerializedName("remoteSize") val remoteSize: Long,
    @SerializedName("localModified") val localModified: Long,
    @SerializedName("localSize") val localSize: Long,
    @SerializedName("etag") val etag: String? = null,
    @SerializedName("uploadDirectory") val uploadDirectory: String? = null,
) {
    val location: WebDavBookLocation get() = WebDavBookLocation(serverId, path)
}
