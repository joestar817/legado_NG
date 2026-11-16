package io.legado.app.ui.book.info

/** 将异步结果绑定到发起它的详情页请求，并在实际发布时检查请求是否仍有效。 */
internal class BookInfoRequestGate<T : Any>(
    initial: T,
    private val dispatch: (() -> Unit) -> Unit,
) {
    @Volatile
    var current: T = initial
        private set

    fun begin(request: T) {
        current = request
    }

    fun isCurrent(request: T): Boolean = current === request

    fun publish(request: T, update: () -> Unit) {
        dispatch {
            if (isCurrent(request)) update()
        }
    }
}
