package io.legado.app.help.font

import java.io.File
import java.net.URI
import java.net.URLDecoder

/** A font is a referenced file, not a hash group. Tree and single-document aliases share identity. */
internal object FontReferenceIdentity {
    fun normalize(value: String?, primaryStorage: String? = null, volumeRoots: Map<String, String> = emptyMap()): String? {
        if (value.isNullOrBlank() || value in setOf("system", "serif", "monospace")) return null
        if (!value.startsWith("content:", true)) {
            return if (value.startsWith("file:", true)) File(URI(value.replace(" ", "%20"))).canonicalPath
                else File(value).canonicalPath
        }
        val uri = URI(value.replace(" ", "%20"))
        val path = uri.rawPath.orEmpty()
        val marker = "/document/"
        if (marker !in path) return uri.normalize().toString()
        val document = URLDecoder.decode(path.substringAfter(marker).replace("+", "%2B"), "UTF-8")
        if (uri.authority == "com.android.externalstorage.documents" && ':' in document) {
            val volume = document.substringBefore(':')
            val volumeRoot = if (volume == "primary") primaryStorage else
                volumeRoots.entries.firstOrNull { it.key.equals(volume, ignoreCase = true) }?.value
            if (volumeRoot != null) {
                val root = File(volumeRoot).canonicalFile
                val file = File(root, document.substringAfter(':')).canonicalFile
                if (file.path.startsWith(root.path + File.separator)) return file.path
            }
        }
        return "document:${uri.authority}:$document"
    }
}
