package io.legado.app.help.config

import io.legado.app.data.entities.Book
import io.legado.app.utils.GSON

/** 当前书籍的预设副本；排版、编辑和导出仍使用 ReadBookConfig.Config。 */
internal class BookReadStyleSession(
    private val persist: (bookUrl: String, style: String?) -> Unit,
) {
    private var book: Book? = null
    var config: ReadBookConfig.Config? = null
        private set

    val isBound: Boolean get() = book != null

    fun bind(next: Book): Boolean {
        save()
        if (book?.bookUrl == next.bookUrl) {
            next.config.independentReadStyle = book?.config?.independentReadStyle
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

    fun followGlobal() {
        config = null
        write(null)
    }

    fun save() {
        config?.let { write(GSON.toJson(it)) }
    }

    fun saveFor(owner: Book) {
        if (book?.bookUrl == owner.bookUrl) save()
    }

    private fun write(serialized: String?) {
        val owner = book ?: return
        if (owner.config.independentReadStyle == serialized) return
        owner.config.independentReadStyle = serialized
        persist(owner.bookUrl, serialized)
    }

    companion object {
        fun decode(serialized: String?): ReadBookConfig.Config? = serialized?.let {
            requireNotNull(GSON.fromJson(it, ReadBookConfig.Config::class.java)) {
                "本书预设无法读取"
            }
        }
    }
}
