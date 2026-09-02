package io.legado.app.ui.file

import android.app.Application
import android.os.Bundle
import android.os.Environment
import androidx.lifecycle.MutableLiveData
import io.legado.app.base.BaseViewModel
import io.legado.app.exception.NoStackTraceException
import io.legado.app.utils.FileUtils
import io.legado.app.utils.toastOnUi
import java.io.File

internal data class FilePickerEntry(
    val file: File,
    val name: String,
    val isDirectory: Boolean,
    val extension: String,
)

class FilePickerViewModel(application: Application) : BaseViewModel(application) {

    var rootDoc: File? = Environment.getExternalStorageDirectory()
    var subDocs = mutableListOf<File>()
    internal val filesLiveData = MutableLiveData<List<FilePickerEntry>>()
    var mode: Int = BuiltInFilePickerActivity.FILE
    var isShowHideDir: Boolean = false
    var allowExtensions: Array<String>? = null
    val isSelectDir: Boolean get() = mode == BuiltInFilePickerActivity.DIRECTORY
    val isSelectFile: Boolean get() = mode == BuiltInFilePickerActivity.FILE
    val lastDir: File? get() = subDocs.lastOrNull() ?: rootDoc

    fun initData(arguments: Bundle?) {
        arguments?.let {
            mode = it.getInt("mode", BuiltInFilePickerActivity.FILE)
            isShowHideDir = it.getBoolean("isShowHideDir")
            it.getString("initPath")?.let { path ->
                rootDoc = File(path).takeIf { file -> file.isDirectory } ?: rootDoc
            }
            allowExtensions = it.getStringArray("allowExtensions")
                ?.map(String::lowercase)
                ?.toTypedArray()
        }
        upFiles(rootDoc)
    }

    fun upFiles(parentFile: File?) {
        execute {
            parentFile ?: return@execute emptyList()
            val files = if (parentFile == rootDoc) {
                parentFile.listFiles()?.sortedWith(
                    compareBy({ it.isFile }, { it.name })
                )
            } else {
                val list = arrayListOf(parentFile)
                parentFile.listFiles()?.sortedWith(
                    compareBy({ it.isFile }, { it.name })
                )?.let {
                    list.addAll(it)
                }
                list
            }
            files?.map { file ->
                val isDirectory = file.isDirectory
                FilePickerEntry(
                    file = file,
                    name = file.name,
                    isDirectory = isDirectory,
                    extension = if (isDirectory) {
                        ""
                    } else {
                        FileUtils.getExtension(file.path).lowercase()
                    },
                )
            }
        }.onStart {
            filesLiveData.postValue(emptyList())
        }.onSuccess {
            filesLiveData.postValue(it ?: emptyList())
        }.onError {
            context.toastOnUi(it.localizedMessage)
        }
    }

    fun createFolder(name: String) {
        execute {
            val dir = lastDir ?: throw NoStackTraceException("父文件夹不存在")
            val folder = File(dir, name)
            if (!folder.canonicalPath.contains(dir.canonicalPath)) {
                throw NoStackTraceException("非法文件名")
            }
            folder.mkdir()
        }.onSuccess {
            upFiles(lastDir)
        }.onError {
            context.toastOnUi(it.localizedMessage)
        }
    }

}
