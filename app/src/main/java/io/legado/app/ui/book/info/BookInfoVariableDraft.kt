package io.legado.app.ui.book.info

internal enum class BookInfoVariableKind {
    Book,
    Source,
}

/** 抽屉内的临时输入，使用显式字符串列表恢复，不参与 Gson、备份或业务存储。 */
internal data class BookInfoVariableDraft(
    val bookUrl: String,
    val sourceKey: String,
    val initialBookValue: String,
    val initialSourceValue: String,
    val bookComment: String,
    val sourceComment: String,
    val bookValue: String = initialBookValue,
    val sourceValue: String = initialSourceValue,
) {
    fun edit(kind: BookInfoVariableKind, value: String): BookInfoVariableDraft = when (kind) {
        BookInfoVariableKind.Book -> copy(bookValue = value)
        BookInfoVariableKind.Source -> copy(sourceValue = value)
    }

    fun matchesContext(bookUrl: String?, sourceKey: String?): Boolean =
        this.bookUrl == bookUrl && this.sourceKey == sourceKey

    fun savedState(): ArrayList<String> = arrayListOf(
        bookUrl, sourceKey, initialBookValue, initialSourceValue,
        bookComment, sourceComment, bookValue, sourceValue,
    )

    companion object {
        fun restore(values: List<String>?): BookInfoVariableDraft? =
            values?.takeIf { it.size == 8 }?.let {
                BookInfoVariableDraft(it[0], it[1], it[2], it[3], it[4], it[5], it[6], it[7])
            }
    }
}

/** 只由“完成”调用；每项写入后推进基线，后续失败重试不会重复提交已完成项。 */
internal fun commitBookInfoVariableDraft(
    draft: BookInfoVariableDraft,
    isCurrent: () -> Boolean,
    write: (BookInfoVariableKind, String) -> Unit,
    onCommitted: (BookInfoVariableDraft) -> Unit,
): Boolean {
    if (!isCurrent()) return false
    var current = draft
    for (kind in BookInfoVariableKind.entries) {
        val value = when (kind) {
            BookInfoVariableKind.Book -> current.bookValue
            BookInfoVariableKind.Source -> current.sourceValue
        }
        val initial = when (kind) {
            BookInfoVariableKind.Book -> current.initialBookValue
            BookInfoVariableKind.Source -> current.initialSourceValue
        }
        if (value == initial) continue
        if (!isCurrent()) return false
        write(kind, value)
        current = when (kind) {
            BookInfoVariableKind.Book -> current.copy(initialBookValue = value)
            BookInfoVariableKind.Source -> current.copy(initialSourceValue = value)
        }
        onCommitted(current)
    }
    return isCurrent()
}
