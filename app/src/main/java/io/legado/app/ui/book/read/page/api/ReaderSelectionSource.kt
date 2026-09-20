package io.legado.app.ui.book.read.page.api

/** 页面提供文字与内容位置；书签生成、存取和菜单动作由阅读业务负责。 */
interface ReaderSelectionSource {
    val selectedText: String

    fun bookmarkSelection(): ReaderSelection?

    fun highlightSelection(): ReaderSelection?

    fun contentEditTarget(highlight: ReaderSelection?): ReaderContentEditTarget?
}

/** 当前页面选区的内存快照，不用于序列化；位置沿用现有章内 UTF-16 偏移。 */
data class ReaderSelection(
    val chapterIndex: Int,
    val chapterPosition: Int,
    val chapterTitle: String,
    val text: String,
    val endChapterIndex: Int = chapterIndex,
    val endChapterPosition: Int = chapterPosition,
)

/** 现有正文编辑器需要的位置，不向编辑器暴露原生行列或 DOM 节点。 */
data class ReaderContentEditTarget(
    val chapterIndex: Int,
    val chapterTitle: String,
    val paragraphIndex: Int,
    val paragraphOffset: Int,
    val selectedTextPrefix: String,
)
