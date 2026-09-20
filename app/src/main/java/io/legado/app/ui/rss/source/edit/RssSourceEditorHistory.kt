package io.legado.app.ui.rss.source.edit

import java.util.ArrayDeque

internal data class RssEditorSelection(val key: String, val start: Int, val end: Int)

internal data class RssEditorSnapshot(val text: String, val start: Int, val end: Int) {
    fun insert(value: String): RssEditorSnapshot {
        val from = minOf(start, end).coerceIn(0, text.length)
        val to = maxOf(start, end).coerceIn(0, text.length)
        val cursor = from + value.length
        return RssEditorSnapshot(text.replaceRange(from, to, value), cursor, cursor)
    }
}

/** 仅保存编辑会话的撤销记录；按字段隔离，并限制长脚本的总字符占用。 */
internal class RssSourceEditorHistory {
    private data class Entry(val key: String, val snapshot: RssEditorSnapshot)

    private val undo = ArrayDeque<Entry>()
    private val redo = ArrayDeque<Entry>()

    fun record(key: String, before: RssEditorSnapshot, after: String) {
        if (before.text == after) return
        redo.removeAll { it.key == key }
        undo.addLast(Entry(key, before))
        trim()
    }

    fun undo(key: String, current: RssEditorSnapshot): RssEditorSnapshot? =
        move(key, current, undo, redo)

    fun redo(key: String, current: RssEditorSnapshot): RssEditorSnapshot? =
        move(key, current, redo, undo)

    fun clear() {
        undo.clear()
        redo.clear()
    }

    private fun move(
        key: String,
        current: RssEditorSnapshot,
        from: ArrayDeque<Entry>,
        to: ArrayDeque<Entry>,
    ): RssEditorSnapshot? {
        val iterator = from.descendingIterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key != key) continue
            iterator.remove()
            to.addLast(Entry(key, current))
            trim()
            return entry.snapshot
        }
        return null
    }

    private fun trim() {
        var characters = undo.sumOf { it.snapshot.text.length.toLong() } +
            redo.sumOf { it.snapshot.text.length.toLong() }
        while (undo.size + redo.size > 100 || characters > 2 * 1024 * 1024) {
            val oldest = if (undo.isNotEmpty()) undo.removeFirst() else redo.removeFirst()
            characters -= oldest.snapshot.text.length
        }
    }
}
