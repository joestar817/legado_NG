package io.legado.app.ui.file

import android.app.Application
import androidx.lifecycle.MutableLiveData
import io.legado.app.base.BaseViewModel
import io.legado.app.utils.toastOnUi
import java.io.File

internal data class FileManageEntry(
    val file: File,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val extension: String,
)

class FileManageViewModel(application: Application) : BaseViewModel(application) {

    val rootDoc = context.getExternalFilesDir(null)?.parentFile
    var subDocs = mutableListOf<File>()
    internal val filesLiveData = MutableLiveData<List<FileManageEntry>>()
    val loadingLiveData = MutableLiveData(false)

    val lastDir: File? get() = subDocs.lastOrNull() ?: rootDoc

    fun upFiles(parentFile: File?) {
        execute {
            parentFile ?: return@execute emptyList()
            parentFile.listFiles()?.map { file ->
                val isDirectory = file.isDirectory
                FileManageEntry(
                    file = file,
                    name = file.name,
                    isDirectory = isDirectory,
                    size = if (isDirectory) 0L else file.length(),
                    lastModified = file.lastModified(),
                    extension = if (isDirectory) "" else file.extension,
                )
            } ?: emptyList()
        }.onStart {
            loadingLiveData.postValue(true)
            filesLiveData.postValue(emptyList())
        }.onSuccess {
            loadingLiveData.postValue(false)
            filesLiveData.postValue(it)
        }.onError {
            loadingLiveData.postValue(false)
            context.toastOnUi(it.localizedMessage)
        }
    }

    fun delFile(file: File) {
        execute {
            file.delete()
        }.onSuccess {
            upFiles(lastDir)
        }.onError {
            context.toastOnUi(it.localizedMessage)
        }
    }

}
