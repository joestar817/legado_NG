package io.legado.app.ui.book.character

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
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
import io.legado.app.help.tts.ReadAloudTtsRouter
import io.legado.app.help.tts.TtsEngineStore
import io.legado.app.help.tts.TtsEngineType
import io.legado.app.help.tts.TtsScriptEngineClient
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class BookStoryboardActivity : BaseActivity<ActivityBookStoryboardBinding>() {

    override val binding by viewBinding(ActivityBookStoryboardBinding::inflate)
    private val adapter by lazy { StoryboardAdapter() }
    private lateinit var workKey: String
    private var bookName: String = ""
    private var bookAuthor: String = ""
    private var previewJob: Job? = null
    private var previewPlayer: ExoPlayer? = null
    private var loadingAnimator: ObjectAnimator? = null

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        bookName = intent.getStringExtra(BookCharacterActivity.EXTRA_BOOK_NAME).orEmpty()
        bookAuthor = intent.getStringExtra(BookCharacterActivity.EXTRA_BOOK_AUTHOR).orEmpty()
        workKey = intent.getStringExtra(BookCharacterActivity.EXTRA_WORK_KEY)
            ?: BookCharacterProfile.workKey(bookName, bookAuthor)
        binding.recyclerView.setEdgeEffectColor(primaryColor)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        binding.ivStoryboardLoading.imageTintList = ColorStateList.valueOf(accentColor)
        binding.btnClose.setOnClickListener { finish() }
        loadStoryboard()
    }

    private fun loadStoryboard() {
        val chapter = ReadBook.curTextChapter
        val content = chapter?.let { AiTtsStoryboardHelper.readAloudContentFromChapter(it) }.orEmpty()
        if (chapter == null || content.isBlank()) {
            renderEmpty(getString(R.string.book_storyboard_empty))
            return
        }
        setLoading(true)
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
                .onSuccess { renderStoryboard(it.first) }
                .onFailure { renderEmpty("AI 分镜生成失败：${it.localizedMessage ?: "未知错误"}") }
        }
    }

    private fun renderEmpty(message: String) = binding.run {
        setLoading(false)
        tvChapterTitle.text = ReadBook.curTextChapter?.title ?: getString(R.string.book_storyboard)
        tvSummary.text = ""
        tvEmpty.text = message
        tvEmpty.isVisible = true
        recyclerView.isVisible = false
    }

    private fun renderStoryboard(storyboard: ChapterStoryboard) = binding.run {
        setLoading(false)
        if (storyboard.scenes.isEmpty()) {
            renderEmpty(getString(R.string.book_storyboard_empty))
            return@run
        }
        val rows = storyboard.scenes.flatMap { scene ->
            val segments = scene.segments.filterNot { it.isChapterTitleSegment(storyboard.chapterTitle) }
            listOf(StoryboardRow.Scene(scene)) + segments.map { StoryboardRow.Segment(it) }
        }
        val visibleSegments = rows.count { it is StoryboardRow.Segment }
        val visibleDialogueCount = rows.count {
            it is StoryboardRow.Segment && it.segment.type == StoryboardSegmentType.DIALOGUE
        }
        val visibleThoughtCount = rows.count {
            it is StoryboardRow.Segment && it.segment.type == StoryboardSegmentType.THOUGHT
        }
        tvChapterTitle.text = storyboard.chapterTitle
        tvSummary.text = "${storyboard.scenes.size} 个分镜 · $visibleSegments 个片段 · 对白 $visibleDialogueCount · 心声 $visibleThoughtCount"
        if (rows.isEmpty()) {
            renderEmpty(getString(R.string.book_storyboard_empty))
            return@run
        }
        adapter.submitRows(rows)
        tvEmpty.isVisible = false
        recyclerView.isVisible = true
    }

    private fun setLoading(loading: Boolean) = binding.run {
        layoutStoryboardLoading.isVisible = loading
        recyclerView.isVisible = !loading
        tvEmpty.isVisible = false
        if (loading) {
            tvStoryboardLoading.text = "正在生成 AI 分镜…"
            startLoadingAnimation()
        } else {
            stopLoadingAnimation()
        }
    }

    private fun startLoadingAnimation() {
        val icon = binding.ivStoryboardLoading
        if (loadingAnimator?.isStarted == true) return
        icon.rotation = 0f
        loadingAnimator = ObjectAnimator.ofFloat(icon, "rotation", 0f, 360f).apply {
            duration = 750L
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            start()
        }
    }

    private fun stopLoadingAnimation() {
        loadingAnimator?.cancel()
        loadingAnimator = null
        binding.ivStoryboardLoading.rotation = 0f
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
            ivVisual.background = accentCircleBackground()
            ivVisual.imageTintList = ColorStateList.valueOf(accentColor)
            tvTitle.text = scene.title.toSceneTitle()
            tvSummary.text = scene.characters.joinToString("、").ifBlank { scene.summary }
            tvMeta.text = buildList {
                add("旁白 ${scene.narrationCount}")
                add("对白 ${scene.dialogueCount}")
                if (scene.thoughtCount > 0) {
                    add("心声 ${scene.thoughtCount}")
                }
            }.joinToString(" · ")
        }

        private fun String.toSceneTitle(): String {
            val number = Regex("""分镜\s*(\d+)""").find(this)?.groupValues?.getOrNull(1)
            return number?.let { "分镜 $it" } ?: this.substringBefore("·").trim()
        }
    }

    private inner class SegmentHolder(
        private val binding: ItemBookStoryboardSegmentBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(segment: StoryboardSegment) = binding.run {
            val identity = when (segment.type) {
                StoryboardSegmentType.NARRATION -> "旁白"
                StoryboardSegmentType.DIALOGUE -> segment.speakerName ?: segment.virtualSpeakerName()
                StoryboardSegmentType.THOUGHT -> segment.speakerName ?: segment.virtualSpeakerName()
            }
            val evidence = segment.evidence.trim()
            tvType.text = identity
            tvType.setTextColor(accentColor)
            tvSpeaker.text = "· 第 ${segment.paragraphIndex + 1} 段"
            tvText.text = segment.text
            tvEvidence.text = evidence
            tvEvidence.isVisible = evidence.isNotBlank() && evidence != identity
            btnPreview.background = accentCircleBackground()
            btnPreview.imageTintList = ColorStateList.valueOf(accentColor)
            btnPreview.setOnClickListener { previewStoryboardSegment(segment) }
        }
    }

    private fun StoryboardSegment.virtualSpeakerName(): String {
        return when (speakerGender) {
            StoryboardSegment.SpeakerGender.MALE -> "对白男"
            StoryboardSegment.SpeakerGender.FEMALE -> "对白女"
            else -> if (type == StoryboardSegmentType.THOUGHT) "心声" else "待确认说话人"
        }
    }

    private fun StoryboardSegment.isChapterTitleSegment(chapterTitle: String): Boolean {
        if (type != StoryboardSegmentType.NARRATION || paragraphIndex != 0) {
            return false
        }
        val normalizedTitle = chapterTitle.normalizedStoryboardTitle()
        return normalizedTitle.isNotBlank() && text.normalizedStoryboardTitle() == normalizedTitle
    }

    private fun String.normalizedStoryboardTitle(): String {
        return filterNot { it.isWhitespace() || it == '\u3000' }
    }

    private sealed interface StoryboardRow {
        data class Scene(val scene: StoryboardScene) : StoryboardRow
        data class Segment(val segment: StoryboardSegment) : StoryboardRow
    }

    private fun accentCircleBackground(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ColorUtils.withAlpha(accentColor, 0.12f))
        }
    }

    private fun previewStoryboardSegment(segment: StoryboardSegment) {
        stopPreview()
        val text = segment.text.trim()
        if (text.isBlank()) {
            toastOnUi("片段内容为空")
            return
        }
        val baseEngine = (ReadAloud.httpTtsEngineV2 ?: runCatching { TtsEngineStore.activeEngine() }.getOrNull())
            ?.takeIf { it.enabled && it.type == TtsEngineType.SCRIPT }
        if (baseEngine == null) {
            toastOnUi("当前朗读引擎不支持片段试听")
            return
        }
        toastOnUi("正在合成片段试听...")
        previewJob = lifecycleScope.launch {
            val result = runCatching {
                val file = withContext(IO) {
                    val router = ReadBook.book?.let { ReadAloudTtsRouter.create(it) }
                    val route = router?.route(segment, baseEngine)
                    val engine = (route?.engine ?: baseEngine)
                        .takeIf { it.enabled && it.type == TtsEngineType.SCRIPT }
                        ?: error("角色绑定的朗读引擎不可用")
                    val response = TtsScriptEngineClient.getSynthesisResponse(
                        engine = engine,
                        text = text,
                        voiceId = route?.voiceId ?: engine.activeVoiceId,
                        styleId = route?.styleId
                    )
                    File(cacheDir, "storyboard_preview_${System.currentTimeMillis()}.audio").apply {
                        response.use {
                            outputStream().use { out ->
                                it.body.byteStream().use { input ->
                                    input.copyTo(out)
                                }
                            }
                        }
                    }
                }
                previewPlayer?.release()
                previewPlayer = ExoPlayer.Builder(this@BookStoryboardActivity).build().apply {
                    setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                    prepare()
                    play()
                }
            }
            result.onFailure {
                if (it !is CancellationException) {
                    toastOnUi("片段试听失败：${it.localizedMessage ?: it.javaClass.simpleName}")
                }
            }
        }
    }

    private fun stopPreview() {
        previewJob?.cancel()
        previewJob = null
        previewPlayer?.release()
        previewPlayer = null
    }

    override fun onDestroy() {
        stopPreview()
        stopLoadingAnimation()
        super.onDestroy()
    }

    companion object {
        private const val VIEW_TYPE_SCENE = 0
        private const val VIEW_TYPE_SEGMENT = 1
    }
}
