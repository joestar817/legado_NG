package io.legado.app.ui.about

import android.app.Application
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.viewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.BaseViewModel
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.databinding.DialogNgRecyclerViewBinding
import io.legado.app.databinding.ItemNgLogFileBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.accentColor
import io.legado.app.ui.widget.dialog.TextDialog
import io.legado.app.ui.widget.dialog.applyNgDialogWindow
import io.legado.app.ui.widget.dialog.ngDialogMaxHeight
import io.legado.app.utils.applyTint
import io.legado.app.utils.FileDoc
import io.legado.app.utils.FileUtils
import io.legado.app.utils.delete
import io.legado.app.utils.find
import io.legado.app.utils.getFile
import io.legado.app.utils.list
import io.legado.app.utils.exportTextContent
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.tintTitle
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.isActive
import java.io.FileFilter

class CrashLogsDialog : BaseDialogFragment(R.layout.dialog_ng_recycler_view),
    Toolbar.OnMenuItemClickListener {

    private val binding by viewBinding(DialogNgRecyclerViewBinding::bind)
    private val viewModel by viewModels<CrashViewModel>()
    private val adapter by lazy { LogAdapter() }

    override fun onStart() {
        super.onStart()
        applyNgDialogWindow(height = ngDialogMaxHeight(0.82f))
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        view.setBackgroundResource(R.drawable.ng_bg_dialog)
        binding.toolBar.setTitle(R.string.crash_log)
        binding.toolBar.inflateMenu(R.menu.crash_log)
        binding.toolBar.menu.applyTint(requireContext())
        binding.toolBar.menu.tintTitle(R.id.menu_copy_content, requireContext().accentColor)
        binding.toolBar.menu.tintTitle(R.id.menu_clear, requireContext().accentColor)
        binding.toolBar.setOnMenuItemClickListener(this)
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        viewModel.logLiveData.observe(viewLifecycleOwner) {
            adapter.setItems(it)
            updateExportMenu(it)
        }
        viewModel.initData()
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_copy_content -> {
                val files = adapter.getItems()
                if (files.isEmpty()) {
                    requireContext().toastOnUi(R.string.export_content_empty)
                    return true
                }
                viewModel.readFiles(files) {
                    if (lifecycleScope.isActive) {
                        requireContext().exportTextContent(it, filePrefix = "legado-crash-log")
                    }
                }
            }
            R.id.menu_clear -> viewModel.clearCrashLog()
        }
        return true
    }

    private fun updateExportMenu(files: List<FileDoc>) {
        binding.toolBar.menu.findItem(R.id.menu_copy_content)?.isVisible = files.isNotEmpty()
    }

    private fun showLogFile(fileDoc: FileDoc) {
        viewModel.readFile(fileDoc) {
            if (lifecycleScope.isActive) {
                showDialogFragment(TextDialog(fileDoc.name, it))
            }
        }

    }

    inner class LogAdapter : RecyclerAdapter<FileDoc, ItemNgLogFileBinding>(requireContext()) {

        override fun getViewBinding(parent: ViewGroup): ItemNgLogFileBinding {
            return ItemNgLogFileBinding.inflate(inflater, parent, false)
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemNgLogFileBinding) {
            binding.root.setOnClickListener {
                getItemByLayoutPosition(holder.layoutPosition)?.let { item ->
                    showLogFile(item)
                }
            }
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemNgLogFileBinding,
            item: FileDoc,
            payloads: MutableList<Any>
        ) {
            binding.textView.text = item.name
        }

    }

    class CrashViewModel(application: Application) : BaseViewModel(application) {

        val logLiveData = MutableLiveData<List<FileDoc>>()

        fun initData() {
            execute {
                val list = arrayListOf<FileDoc>()
                context.externalCacheDir
                    ?.getFile("crash")
                    ?.listFiles(FileFilter { it.isFile })
                    ?.forEach {
                        list.add(FileDoc.fromFile(it))
                    }
                val backupPath = AppConfig.backupPath
                if (!backupPath.isNullOrEmpty()) {
                    val uri = Uri.parse(backupPath)
                    FileDoc.fromUri(uri, true)
                        .find("crash")
                        ?.list {
                            !it.isDir
                        }?.let {
                            list.addAll(it)
                        }
                }
                return@execute list.sortedByDescending { it.name }.distinctBy { it.name }
            }.onSuccess {
                logLiveData.postValue(it)
            }
        }

        fun readFile(fileDoc: FileDoc, success: (String) -> Unit) {
            execute {
                String(fileDoc.readBytes())
            }.onSuccess {
                success.invoke(it)
            }.onError {
                context.toastOnUi(it.localizedMessage)
            }
        }

        fun readFiles(fileDocs: List<FileDoc>, success: (String) -> Unit) {
            execute {
                fileDocs.joinToString("\n\n") { fileDoc ->
                    buildString {
                        append("// ===== ").append(fileDoc.name).append(" =====\n")
                        append(String(fileDoc.readBytes()))
                    }
                }
            }.onSuccess {
                success.invoke(it)
            }.onError {
                context.toastOnUi(it.localizedMessage)
            }
        }

        fun clearCrashLog() {
            execute {
                context.externalCacheDir
                    ?.getFile("crash")
                    ?.let {
                        FileUtils.delete(it, false)
                    }
                val backupPath = AppConfig.backupPath
                if (!backupPath.isNullOrEmpty()) {
                    val uri = Uri.parse(backupPath)
                    FileDoc.fromUri(uri, true)
                        .find("crash")
                        ?.delete()
                }
            }.onError {
                context.toastOnUi(it.localizedMessage)
            }.onFinally {
                initData()
            }
        }

    }

}
