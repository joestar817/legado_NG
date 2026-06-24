package io.legado.app.ui.about

import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.constant.AppLog
import io.legado.app.databinding.DialogNgRecyclerViewBinding
import io.legado.app.databinding.ItemAppLogBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.accentColor
import io.legado.app.ui.widget.dialog.TextDialog
import io.legado.app.ui.widget.dialog.applyNgDialogWindow
import io.legado.app.ui.widget.dialog.ngDialogMaxHeight
import io.legado.app.utils.applyTint
import io.legado.app.utils.LogUtils
import io.legado.app.utils.exportTextContent
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.tintTitle
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import splitties.views.onClick
import java.util.*

class AppLogDialog : BaseDialogFragment(R.layout.dialog_ng_recycler_view),
    Toolbar.OnMenuItemClickListener {

    private val binding by viewBinding(DialogNgRecyclerViewBinding::bind)
    private val adapter by lazy {
        LogAdapter(requireContext())
    }

    override fun onStart() {
        super.onStart()
        applyNgDialogWindow(height = ngDialogMaxHeight(0.82f))
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.run {
            view.setBackgroundResource(R.drawable.ng_bg_dialog)
            toolBar.setTitle(R.string.log)
            toolBar.inflateMenu(R.menu.app_log)
            toolBar.menu.applyTint(requireContext())
            toolBar.menu.tintTitle(R.id.menu_copy_content, requireContext().accentColor)
            toolBar.menu.tintTitle(R.id.menu_clear, requireContext().accentColor)
            toolBar.setOnMenuItemClickListener(this@AppLogDialog)
            recyclerView.layoutManager = LinearLayoutManager(requireContext())
            recyclerView.adapter = adapter
        }
        val logs = AppLog.logs
        adapter.setItems(logs)
        updateEmptyState(logs)
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_copy_content -> {
                val logs = AppLog.logs
                if (logs.isEmpty()) {
                    requireContext().toastOnUi(R.string.export_content_empty)
                    return true
                }
                requireContext().exportTextContent(
                    formatLogs(logs),
                    filePrefix = "legado-app-log"
                )
            }
            R.id.menu_clear -> {
                AppLog.clear()
                adapter.clearItems()
                updateEmptyState(emptyList())
            }
        }
        return true
    }

    private fun updateEmptyState(logs: List<Triple<Long, String, Throwable?>>) {
        val hasLogs = logs.isNotEmpty()
        binding.toolBar.menu.findItem(R.id.menu_copy_content)?.isVisible = hasLogs
        binding.recyclerView.visibility = if (hasLogs) View.VISIBLE else View.GONE
        binding.tvMsg.visibility = if (hasLogs) View.GONE else View.VISIBLE
        binding.tvMsg.setText(
            if (AppConfig.recordLog) {
                R.string.log_empty
            } else {
                R.string.log_feature_disabled
            }
        )
    }

    private fun formatLogs(logs: List<Triple<Long, String, Throwable?>>): String {
        return logs.joinToString("\n\n") { item ->
            buildString {
                append(LogUtils.logTimeFormat.format(Date(item.first)))
                append('\n')
                append(item.second)
                item.third?.let {
                    append('\n')
                    append(it.stackTraceToString())
                }
            }
        }
    }

    inner class LogAdapter(context: Context) :
        RecyclerAdapter<Triple<Long, String, Throwable?>, ItemAppLogBinding>(context) {

        override fun getViewBinding(parent: ViewGroup): ItemAppLogBinding {
            return ItemAppLogBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemAppLogBinding,
            item: Triple<Long, String, Throwable?>,
            payloads: MutableList<Any>
        ) {
            binding.textTime.text = LogUtils.logTimeFormat.format(Date(item.first))
            binding.textMessage.text = item.second
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemAppLogBinding) {
            binding.root.onClick {
                getItem(holder.layoutPosition)?.let { item ->
                    item.third?.let {
                        showDialogFragment(TextDialog("Log", it.stackTraceToString()))
                    }
                }
            }
        }

    }

}
