package io.legado.app.help.font

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import splitties.init.appCtx
import java.io.File

/** Resolve SAF aliases using roots reported by Android, never by assuming a /storage/<id> path. */
internal object FontStoragePaths {
    private const val PROVIDER = "content://com.android.externalstorage.documents/"

    fun normalize(value: String?): String? {
        if (value?.startsWith(PROVIDER) != true) return FontReferenceIdentity.normalize(value)
        val primary = Environment.getExternalStorageDirectory().canonicalPath
        val primaryMatch = FontReferenceIdentity.normalize(value, primary)
        if (primaryMatch?.startsWith("document:") != true) return primaryMatch
        val roots = linkedMapOf<String, String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val manager = appCtx.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            manager.storageVolumes.forEach { volume ->
                val id = volume.uuid
                val directory = volume.directory
                if (!volume.isPrimary && id != null && directory?.isDirectory == true) {
                    roots[id] = directory.canonicalPath
                }
            }
        }
        // Public API on 21-29 also identifies each mounted app-accessible secondary volume.
        appCtx.getExternalFilesDirs(null).filterNotNull().forEach { files ->
            val actual = files.canonicalFile
            val packageDir = actual.parentFile
            val dataDir = packageDir?.parentFile
            val androidDir = dataDir?.parentFile
            val root = androidDir?.parentFile
            if (actual.name == "files" && packageDir?.name == appCtx.packageName && dataDir?.name == "data" &&
                androidDir?.name == "Android" && root != null && root.isDirectory &&
                root.name.matches(Regex("[A-Fa-f0-9]{4,}(?:-[A-Fa-f0-9]+)+")) &&
                File(root, "Android/data/${appCtx.packageName}/files").canonicalFile == actual) {
                if (root.name !in roots) roots[root.name] = root.path
            }
        }
        return FontReferenceIdentity.normalize(value, primary, roots)
    }

    fun hasUnresolvedVolume(value: String): Boolean = value.startsWith(PROVIDER) &&
        normalize(value)?.startsWith("document:com.android.externalstorage.documents:") == true
}
