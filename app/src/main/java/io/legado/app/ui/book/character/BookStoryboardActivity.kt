package io.legado.app.ui.book.character

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookCharacter
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.databinding.ActivityBookStoryboardBinding
import io.legado.app.databinding.ItemBookStoryboardSceneBinding
import io.legado.app.databinding.ItemBookStoryboardSegmentBinding
import io.legado.app.help.ai.AiTtsStoryboardHelper
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.model.ReadBook
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BookStoryboardActivity : BaseActivity<ActivityBookStoryboardBinding>() {

    override val binding by viewBinding(ActivityBookStoryboardBinding::inflate)
    private val adapter by lazy { StoryboardAdapter() }
    private lateinit var workKey: String
    private var bookName: String = ""
    private var bookAuthor: String = ""

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        bookName = intent.getStringExtra(BookCharacterActivity.EXTRA_BOOK_NAME).orEmpty()
        bookAuthor = intent.getStringExtra(BookCharacterActivity.EXTRA_BOOK_AUTHOR).orEmpty()
        workKey = intent.getStringExtra(BookCharacterActivity.EXTRA_WORK_KEY)
            ?: BookCharacterProfile.workKey(bookName, bookAuthor)
        binding.recyclerView.setEdgeEffectColor(primaryColor)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        loadStoryboard()
    }

    private fun loadStoryboard() {
        val chapter = ReadBook.curTextChapter
        val content = chapter?.let { AiTtsStoryboardHelper.readAloudContentFromChapter(it) }.orEmpty()
        if (chapter == null || content.isBlank()) {
            renderEmpty(getString(R.string.book_storyboard_empty))
            return
        }
        lifecycleScope.launch {
            val result = withContext(IO) {
                val characters = appDb.bookCharacterDao.getCharacters(workKey)
                val book = ReadBook.book ?: return@withContext Result.failure<Pair<ChapterStoryboard, List<BookCharacter>>>(
                    IllegalStateException("当前书籍为空")
                )
                runCatching {
                    AiTtsStoryboardHelper.getOrGenerate(
                        book = book,
                        chapterIndex = ReadBook.durChapterIndex,
                        chapterTitle = chapter.title,
                        content = content,
                        characters = characters
                    ) to characters
                }
            }
            result
                .onSuccess { renderStoryboard(it.first, it.second) }
                .onFailure { renderEmpty("AI 分镜生成失败：${it.localizedMessage ?: "未知错误"}") }
        }
    }

    private fun renderEmpty(message: String) = binding.run {
        tvSummary.text = ""
        tvEmpty.text = message
        tvEmpty.isVisible = true
        recyclerView.isVisible = false
    }

    private fun renderStoryboard(
        storyboard: ChapterStoryboard,
        characters: List<BookCharacter>
    ) = binding.run {
        if (storyboard.scenes.isEmpty()) {
            renderEmpty(getString(R.string.book_storyboard_empty))
            return@run
        }
        val rows = storyboard.scenes.flatMap { scene ->
            listOf(StoryboardRow.Scene(scene)) + scene.segments.map { StoryboardRow.Segment(it) }
        }
        adapter.submitRows(rows)
        tvSummary.text = getString(
            R.string.book_storyboard_summary,
            storyboard.chapterTitle,
            storyboard.scenes.size,
            storyboard.segmentCount,
            storyboard.dialogueCount,
            storyboard.thoughtCount,
            characters.count { it.enabled }
        )
        tvEmpty.isVisible = false
        recyclerView.isVisible = true
    }

    private inner class StoryboardAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val rows = mutableListOf<StoryboardRow>()

        fun submitRows(newRows: List<StoryboardRow>) {
            rows.clear()
            rows.addAll(newRows)
            notifyDataSetChanged()
        }

        override fun getItemViewType(position: Int): Int {
            return when (rows[position]) {
                is StoryboardRow.Scene -> VIEW_TYPE_SCENE
                is StoryboardRow.Segment -> VIEW_TYPE_SEGMENT
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == VIEW_TYPE_SCENE) {
                SceneHolder(ItemBookStoryboardSceneBinding.inflate(inflater, parent, false))
            } else {
                SegmentHolder(ItemBookStoryboardSegmentBinding.inflate(inflater, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val row = rows[position]) {
                is StoryboardRow.Scene -> (holder as SceneHolder).bind(row.scene)
                is StoryboardRow.Segment -> (holder as SegmentHolder).bind(row.segment)
            }
        }

        override fun getItemCount(): Int = rows.size
    }

    private inner class SceneHolder(
        private val binding: ItemBookStoryboardSceneBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(scene: StoryboardScene) = binding.run {
            tvTitle.text = scene.title
            tvSummary.text = scene.summary
            tvMeta.text = getString(
                R.string.book_storyboard_scene_meta,
                scene.characters.joinToString("、").ifBlank { getString(R.string.book_storyboard_no_character) },
                scene.narrationCount,
                scene.dialogueCount,
                scene.thoughtCount
            )
        }
    }

    private inner class SegmentHolder(
        private val binding: ItemBookStoryboardSegmentBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(segment: StoryboardSegment) = binding.run {
            tvType.text = segment.type.label()
            tvType.backgroundTintList = ColorStateList.valueOf(segment.type.tintColor())
            tvSpeaker.text = when (segment.type) {
                StoryboardSegmentType.NARRATION -> getString(R.string.book_storyboard_narrator)
                else -> segment.speakerName
                    ?: getString(R.string.book_storyboard_unknown_speaker)
            }
            tvText.text = segment.text
            tvEvidence.text = getString(
                R.string.book_storyboard_segment_evidence,
                segment.paragraphIndex + 1,
                segment.evidence
            )
        }

        private fun StoryboardSegmentType.label(): String {
            return when (this) {
                StoryboardSegmentType.NARRATION -> getString(R.string.book_storyboard_type_narration)
                StoryboardSegmentType.DIALOGUE -> getString(R.string.book_storyboard_type_dialogue)
                StoryboardSegmentType.THOUGHT -> getString(R.string.book_storyboard_type_thought)
            }
        }

        private fun StoryboardSegmentType.tintColor(): Int {
            return when (this) {
                StoryboardSegmentType.NARRATION -> ColorUtils.withAlpha(
                    ContextCompat.getColor(this@BookStoryboardActivity, R.color.tv_text_summary),
                    0.16f
                )
                StoryboardSegmentType.DIALOGUE -> ColorUtils.withAlpha(primaryColor, 0.18f)
                StoryboardSegmentType.THOUGHT -> ColorUtils.withAlpha(accentColor, 0.18f)
            }
        }
    }

    private sealed interface StoryboardRow {
        data class Scene(val scene: StoryboardScene) : StoryboardRow
        data class Segment(val segment: StoryboardSegment) : StoryboardRow
    }

    companion object {
        private const val VIEW_TYPE_SCENE = 0
        private const val VIEW_TYPE_SEGMENT = 1
    }
}
