package io.legado.app.ui.config

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.databinding.ItemTtsEngineBinding
import io.legado.app.help.tts.TtsEngineSetting
import io.legado.app.ui.widget.dialog.NgLongListBottomSheet
import io.legado.app.utils.dpToPx

class TtsEngineSelectionSheet(
    private val context: android.content.Context,
    private val title: CharSequence,
    private val searchHint: CharSequence,
    private val emptyText: CharSequence,
    private val engines: List<TtsEngineSetting>,
    private val selectedEngineId: String?,
    private val onSelect: (TtsEngineSetting) -> Unit,
    private val titleAction: Pair<CharSequence, () -> Unit>? = null
) {
    private val adapter = TtsEngineSelectionAdapter(selectedEngineId) { engine ->
        onSelect(engine)
        sheet.dismiss()
    }
    private lateinit var sheet: NgLongListBottomSheet

    fun show() {
        sheet = NgLongListBottomSheet(
            context = context,
            searchHint = searchHint,
            title = title,
            heightRatio = 0.68f,
            compact = true
        )
        titleAction?.let { (text, action) ->
            sheet.setTitleAction(text) {
                action()
                sheet.dismiss()
            }
        }
        val recyclerView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@TtsEngineSelectionSheet.adapter
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setPadding(0, 0, 0, 6.dpToPx())
        }
        val emptyView = TextView(context).apply {
            text = emptyText
            gravity = android.view.Gravity.CENTER
            setTextColor(ContextCompat.getColor(context, R.color.ng_on_surface_variant))
            textSize = 15f
            isVisible = false
        }
        val content = FrameLayout(context).apply {
            addView(
                recyclerView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            addView(
                emptyView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
        sheet.setContent(content) { query ->
            val normalized = query.trim()
            val filtered = engines.filter { engine ->
                normalized.isBlank() || engine.name.contains(normalized, ignoreCase = true)
            }
            adapter.submitItems(filtered)
            recyclerView.isVisible = filtered.isNotEmpty()
            emptyView.isVisible = filtered.isEmpty()
        }
        sheet.show()
    }

}

private class TtsEngineSelectionAdapter(
    private val selectedEngineId: String?,
    private val onSelect: (TtsEngineSetting) -> Unit
) : RecyclerView.Adapter<TtsEngineSelectionAdapter.EngineHolder>() {
    private val items = mutableListOf<TtsEngineSetting>()

    fun submitItems(newItems: List<TtsEngineSetting>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EngineHolder {
        return EngineHolder(
            ItemTtsEngineBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onBindViewHolder(holder: EngineHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class EngineHolder(
        private val binding: ItemTtsEngineBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(engine: TtsEngineSetting) = binding.run {
            TtsEngineCardBinder.bind(
                context = root.context,
                binding = this,
                engine = engine,
                trailing = if (engine.id == selectedEngineId) {
                    TtsEngineCardBinder.Trailing.SELECTED
                } else {
                    TtsEngineCardBinder.Trailing.NONE
                }
            )
            root.setOnClickListener { onSelect(engine) }
            layoutSelectEngine.setOnClickListener { onSelect(engine) }
            root.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 10.dpToPx()
            }
        }
    }
}
