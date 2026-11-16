package io.legado.app.help.storage

import android.content.ContextWrapper
import android.content.SharedPreferences
import io.legado.app.utils.getSharedPreferences
import splitties.init.appCtx

/** Keep Android's per-context preference-name cache separate for each backup directory. */
internal object BackupPreferences {
    fun open(directory: String, name: String): SharedPreferences? {
        val context = ContextWrapper(appCtx.createPackageContext(appCtx.packageName, 0))
        return context.getSharedPreferences(directory, name)
    }
}
