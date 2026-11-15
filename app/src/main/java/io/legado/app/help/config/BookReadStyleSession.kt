package io.legado.app.help.config

import io.legado.app.data.entities.Book
import io.legado.app.utils.GSON

internal data class BookReadStyleSnapshot(val active: String?, val saved: String?)

/** 当前书籍的预设副本；排版、编辑和导出仍使用 ReadBookConfig.Config。 */
internal class BookReadStyleSession(
    private val persist: (bookUrl: String, snapshot: BookReadStyleSnapshot) -> Unit,
) {
    private var book: Book? = null
    var config: ReadBookConfig.Config? = null
        private set

    val isBound: Boolean get() = book != null

    /** Read the requested book without rebinding or persisting the active reading session. */
    fun configFor(owner: Book): ReadBookConfig.Config? =
        if (book?.bookUrl == owner.bookUrl) config else decode(owner.readConfig?.independentReadStyle)

    fun bind(next: Book): Boolean {
        save()
        if (book?.bookUrl == next.bookUrl) {
            next.config.independentReadStyle = book?.config?.independentReadStyle
            next.config.savedIndependentReadStyle = book?.config?.savedIndependentReadStyle
            book = next
            return false
        }
        val previous = config
        val loaded = decode(next.config.independentReadStyle)
        book = next
        config = loaded
        return previous != null || loaded != null
    }

    fun use(style: ReadBookConfig.Config) {
        check(isBound)
        config = style.copyForBook()
        save()
    }

    fun restoreSaved(): Boolean {
        val saved = book?.readConfig?.savedIndependentReadStyle ?: return false
        use(requireNotNull(decode(saved)))
        return true
    }

    fun savedConfig(): ReadBookConfig.Config? = decode(book?.readConfig?.savedIndependentReadStyle)

    fun followGlobal() {
        val saved = config?.let { GSON.toJson(it) } ?: book?.readConfig?.savedIndependentReadStyle
        config = null
        write(active = null, saved = saved)
    }

    fun save() {
        config?.let { write(active = GSON.toJson(it), saved = null) }
    }

    fun saveFor(owner: Book) {
        if (book?.bookUrl == owner.bookUrl) save()
    }

    fun updateSaved(transform: (String) -> String?) {
        val current = book?.readConfig?.savedIndependentReadStyle ?: return
        val updated = transform(current) ?: return
        write(active = book?.readConfig?.independentReadStyle, saved = updated)
    }

    private fun write(active: String?, saved: String?) {
        val owner = book ?: return
        if (owner.config.independentReadStyle == active && owner.config.savedIndependentReadStyle == saved) return
        owner.config.independentReadStyle = active
        owner.config.savedIndependentReadStyle = saved
        persist(owner.bookUrl, BookReadStyleSnapshot(active, saved))
    }

    companion object {
        fun decode(serialized: String?): ReadBookConfig.Config? = serialized?.let {
            requireNotNull(GSON.fromJson(it, ReadBookConfig.Config::class.java)) {
                "本书预设无法读取"
            }
        }
    }
}
