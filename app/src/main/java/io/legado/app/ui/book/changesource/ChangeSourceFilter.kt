package io.legado.app.ui.book.changesource

import io.legado.app.constant.AppPattern
import io.legado.app.constant.BookSourceType
import io.legado.app.constant.BookType

/** 换源的书源范围；类型和分组始终取交集，不隐式扩大范围。 */
internal data class ChangeSourceFilter(
    val sourceType: Int,
    val sameTypeOnly: Boolean,
    val group: String,
) {
    fun acceptsSource(type: Int, enabled: Boolean, groups: String?): Boolean =
        enabled && (!sameTypeOnly || type == sourceType) &&
            (group.isBlank() || group in splitGroups(groups))

    companion object {
        fun sourceTypeForBook(type: Int, customType: Int? = null): Int {
            val effectiveType = when (customType) {
                BookType.text, BookType.audio, BookType.image, BookType.video -> customType
                else -> type
            }
            // 与通用 startActivityForBook 的媒体路由优先级一致；其它位是书籍状态。
            return when {
                effectiveType and BookType.video != 0 -> BookSourceType.video
                effectiveType and BookType.audio != 0 -> BookSourceType.audio
                effectiveType and BookType.image != 0 -> BookSourceType.image
                effectiveType and BookType.webFile != 0 -> BookSourceType.file
                else -> BookSourceType.default
            }
        }

        fun splitGroups(groups: String?): List<String> = groups.orEmpty()
            .split(AppPattern.splitGroupRegex).map(String::trim).filter(String::isNotBlank)
    }
}

/** 停止或切换范围后，异步准备及网络迟到结果都属于过期轮次。 */
internal class ChangeSourceRequestGuard {
    private var revision = 0L

    data class Request(val revision: Long, val filter: ChangeSourceFilter)

    @Synchronized
    fun begin(filter: ChangeSourceFilter): Request = Request(++revision, filter)

    @Synchronized
    fun invalidate() {
        revision++
    }

    @Synchronized
    fun snapshot(filter: ChangeSourceFilter): Request = Request(revision, filter)

    @Synchronized
    fun isCurrent(request: Request, filter: ChangeSourceFilter): Boolean =
        request.revision == revision && request.filter == filter
}
