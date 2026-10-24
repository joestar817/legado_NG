package io.legado.app.model.remote

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import io.legado.app.lib.webdav.WebDavFile

@Keep
data class RemoteBook(
    @SerializedName("filename") val filename: String,
    @SerializedName("path") val path: String,
    @SerializedName("size") val size: Long,
    @SerializedName("lastModify") val lastModify: Long,
    @SerializedName("contentType") var contentType: String = "folder",
    @SerializedName("isOnBookShelf") var isOnBookShelf: Boolean = false,
    @SerializedName("etag") val etag: String? = null,
) {

    val isDir get() = contentType == "folder"

    constructor(webDavFile: WebDavFile) : this(
        webDavFile.displayName,
        webDavFile.path,
        webDavFile.size,
        webDavFile.lastModify,
        etag = webDavFile.etag,
    ) {
        if (!webDavFile.isDir) {
            contentType = webDavFile.displayName.substringAfterLast(".")
        }
    }

}
