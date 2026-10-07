package io.legado.app.ui.main

internal data class BookNewsRefreshState(
    val checking: Boolean = false,
    val lastCheckedAt: Long? = null,
    val checkFailed: Boolean = false
)

internal data class BookNewsCheckTarget(val bookUrl: String, val origin: String)

internal object BookNewsRefreshPolicy {
    const val CHECK_INTERVAL_MS = 30 * 60 * 1000L
    const val ATTEMPT_COOLDOWN_MS = 60 * 1000L
    const val LAST_CHECKED_AT_PREF = "homeBookNewsLastCheckedAt"

    fun isEligible(isLocal: Boolean, isNotShelf: Boolean, canUpdate: Boolean): Boolean =
        !isLocal && !isNotShelf && canUpdate

    fun shouldCheck(
        state: BookNewsRefreshState,
        now: Long,
        elapsed: Long,
        lastAttemptElapsed: Long?,
        force: Boolean
    ): Boolean {
        if (state.checking) return false
        if (force) return true
        if (lastAttemptElapsed != null && elapsed >= lastAttemptElapsed &&
            elapsed - lastAttemptElapsed < ATTEMPT_COOLDOWN_MS
        ) return false
        if (state.checkFailed) return true
        val checkedAt = state.lastCheckedAt ?: return true
        // 时钟回拨时允许重查，避免未来时间把首页检查无限期锁住。
        return now < checkedAt || now - checkedAt >= CHECK_INTERVAL_MS
    }
}

/** 所有调用由 MainViewModel 的队列锁串行化；本类不发起 I/O。 */
internal class BookNewsRefreshTracker(lastCheckedAt: Long? = null) {
    var state = BookNewsRefreshState(lastCheckedAt = lastCheckedAt)
        private set
    private var lastAttemptElapsed: Long? = null
    private var pending: MutableMap<String, String>? = null
    private var attemptedTarget = false
    private val successfulTargets = mutableMapOf<BookNewsCheckTarget, Long>()
    private val reusedCheckTimes = mutableListOf<Long>()

    fun tryStart(now: Long, elapsed: Long, force: Boolean): Boolean {
        if (!BookNewsRefreshPolicy.shouldCheck(state, now, elapsed, lastAttemptElapsed, force)) {
            return false
        }
        lastAttemptElapsed = elapsed
        pending = null
        attemptedTarget = false
        reusedCheckTimes.clear()
        state = state.copy(checking = true, checkFailed = false)
        return true
    }

    fun setTargets(targets: List<BookNewsCheckTarget>, now: Long, reuseSuccessful: Boolean) {
        if (!state.checking) return
        pending = targets.filterNot { target ->
            val checkedAt = successfulTargets[target]
            if (reuseSuccessful && wasSuccessfullyChecked(target, now) && checkedAt != null) {
                reusedCheckTimes.add(checkedAt)
                true
            } else false
        }.associateTo(linkedMapOf()) { it.bookUrl to it.origin }
        if (pending?.isEmpty() == true) finish(now)
    }

    fun needsCheck(target: BookNewsCheckTarget): Boolean = pending?.get(target.bookUrl) == target.origin

    fun complete(bookUrl: String, origin: String?, success: Boolean, now: Long) {
        if (success && origin != null) {
            successfulTargets[BookNewsCheckTarget(bookUrl, origin)] = now
        } else if (origin != null) {
            successfulTargets.remove(BookNewsCheckTarget(bookUrl, origin))
        }
        val expectedOrigin = pending?.remove(bookUrl) ?: return
        if (!success || origin != expectedOrigin) {
            successfulTargets.remove(BookNewsCheckTarget(bookUrl, expectedOrigin))
        }
        // 失败或身份失效只跳过该目标；完成时间表示本轮尝试结束，非所有书源成功。
        attemptedTarget = true
        if (pending?.isEmpty() == true) finish(now)
    }

    fun wasSuccessfullyChecked(target: BookNewsCheckTarget, now: Long): Boolean {
        val checkedAt = successfulTargets[target] ?: return false
        return now >= checkedAt && now - checkedAt < BookNewsRefreshPolicy.CHECK_INTERVAL_MS
    }

    fun cancel() {
        if (!state.checking) return
        pending = null
        attemptedTarget = false
        reusedCheckTimes.clear()
        state = state.copy(checking = false, checkFailed = true)
    }

    private fun finish(now: Long) {
        pending = null
        state = state.copy(
            checking = false,
            // 纯复用不把再次展示当作新检查；有实际尝试时从本轮结束开始冷却。
            lastCheckedAt = if (attemptedTarget) now else reusedCheckTimes.minOrNull() ?: now,
            checkFailed = false
        )
    }
}
