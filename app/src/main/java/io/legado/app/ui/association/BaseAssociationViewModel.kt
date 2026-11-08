package io.legado.app.ui.association

import android.app.Application
import android.net.Uri
import androidx.lifecycle.MutableLiveData
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.utils.inputStream

abstract class BaseAssociationViewModel(application: Application) : BaseViewModel(application) {

    val successLive = MutableLiveData<Pair<String, String>>()
    val errorLive = MutableLiveData<String>()

    fun importJson(uri: Uri) {
        val keys = uri.inputStream(context).getOrThrow().bufferedReader().use {
            firstImportObjectKeys(it)
        }

        when {
            "bookSourceUrl" in keys ->
                successLive.postValue("bookSource" to uri.toString())

            "sourceUrl" in keys ->
                successLive.postValue("rssSource" to uri.toString())

            "pattern" in keys ->
                successLive.postValue("replaceRule" to uri.toString())

            "themeName" in keys ->
                errorLive.postValue(context.getString(R.string.legacy_theme_import_unsupported))

            "showRule" in keys ->
                successLive.postValue("dictRule" to uri.toString())

            "name" in keys && "rule" in keys ->
                successLive.postValue("txtRule" to uri.toString())

            else -> errorLive.postValue("格式不对")
        }
    }

}
