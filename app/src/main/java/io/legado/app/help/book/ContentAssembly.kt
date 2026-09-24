package io.legado.app.help.book

/** Records the ranges a StringBuilder actually copies; no matching against its final text. */
internal class ContentAssembly(private val source: String) {
    private data class Piece(val start: Int?, val end: Int?, val value: String, val nested: ContentPositionMap? = null)
    private val pieces = ArrayList<Piece>()

    fun copy(start: Int, end: Int, nested: ContentPositionMap? = null) {
        require(start in 0..end && end <= source.length)
        if (nested != null) require(nested.source == source.substring(start, end))
        pieces.add(Piece(start, end, nested?.text ?: source.substring(start, end), nested))
    }

    fun append(value: String) { if (value.isNotEmpty()) pieces.add(Piece(null, null, value)) }

    fun insert(offset: Int, value: String) {
        require(offset >= 0)
        var cursor = 0
        for (index in pieces.indices) {
            val piece = pieces[index]
            val end = cursor + piece.value.length
            if (offset in cursor..end) {
                require(piece.nested == null) { "Split the actual source before inserting into a transformed piece" }
                val at = offset - cursor
                val replacement = buildList {
                    if (at > 0) add(Piece(piece.start, piece.start?.plus(at), piece.value.substring(0, at)))
                    add(Piece(null, null, value))
                    if (at < piece.value.length) add(Piece(piece.start?.plus(at), piece.end, piece.value.substring(at)))
                }
                pieces.removeAt(index)
                pieces.addAll(index, replacement)
                return
            }
            cursor = end
        }
        require(offset == cursor)
        append(value)
    }

    fun record(trace: ContentPositionMap, actual: String, display: Boolean = true) {
        require(pieces.joinToString("") { it.value } == actual)
        val edits = ArrayList<ContentEdit>()
        var cursor = 0
        for (piece in pieces) {
            val start = piece.start
            val end = piece.end
            if (start == null || end == null) {
                edits.add(ContentEdit(cursor, cursor, piece.value))
            } else {
                require(start >= cursor) { "Content assembly copied source out of order" }
                if (start > cursor) edits.add(ContentEdit(cursor, start, ""))
                if (piece.nested != null) edits.add(ContentEdit(start, end, piece.value, piece.nested))
                cursor = end
            }
        }
        if (cursor < source.length) edits.add(ContentEdit(cursor, source.length, ""))
        trace.record(source, actual, edits, display)
    }
}
