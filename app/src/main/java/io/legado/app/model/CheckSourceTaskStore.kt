package io.legado.app.model

import io.legado.app.data.entities.BookSourcePart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class CheckSourceTaskStatus {
    IDLE,
    RUNNING,
    COMPLETED,
    CANCELLED,
}

enum class CheckSourceItemStatus {
    WAITING,
    RUNNING,
    PASSED,
    FAILED,
    BLOCKED,
    CANCELLED,
}

enum class CheckSourceStage {
    PREPARING,
    DOMAIN,
    SEARCH,
    DISCOVERY,
    INFO,
    CATALOG,
    CONTENT,
}

enum class CheckSourceResultKind {
    STANDARD,
    CONTENT_PARSED,
}

data class CheckSourceItemState(
    val origin: String,
    val sourceName: String,
    val sourceType: Int,
    val status: CheckSourceItemStatus = CheckSourceItemStatus.WAITING,
    val stage: CheckSourceStage = CheckSourceStage.PREPARING,
    val message: String = "",
    val durationMillis: Long = 0L,
    val resultKind: CheckSourceResultKind = CheckSourceResultKind.STANDARD,
    val updatedAt: Long = 0L,
)

data class CheckSourceTaskState(
    val runId: Long = 0L,
    val status: CheckSourceTaskStatus = CheckSourceTaskStatus.IDLE,
    val items: List<CheckSourceItemState> = emptyList(),
    val currentOrigin: String? = null,
    val currentSourceName: String = "",
    val currentStage: CheckSourceStage = CheckSourceStage.PREPARING,
    val startedAtMillis: Long = 0L,
    val finishedAtMillis: Long = 0L,
    val resultsAcknowledged: Boolean = false,
) {
    val totalCount: Int get() = items.size
    val passedCount: Int get() = items.count { it.status == CheckSourceItemStatus.PASSED }
    val failedCount: Int get() = items.count { it.status == CheckSourceItemStatus.FAILED }
    val blockedCount: Int get() = items.count { it.status == CheckSourceItemStatus.BLOCKED }
    val processedCount: Int
        get() = items.count {
                it.status == CheckSourceItemStatus.PASSED ||
                it.status == CheckSourceItemStatus.FAILED ||
                it.status == CheckSourceItemStatus.BLOCKED ||
                it.status == CheckSourceItemStatus.CANCELLED
        }
    val remainingCount: Int get() = (totalCount - processedCount).coerceAtLeast(0)
    val progressFraction: Float
        get() = if (totalCount <= 0) 0f else processedCount.toFloat() / totalCount
    val showManageEntry: Boolean
        get() = status == CheckSourceTaskStatus.RUNNING ||
            ((status == CheckSourceTaskStatus.COMPLETED ||
                status == CheckSourceTaskStatus.CANCELLED) && !resultsAcknowledged)
}

/**
 * 进程内保存最近一次书源校验任务。服务与页面共享同一份结构化状态，Activity 重建或
 * 前后台切换不再依赖一次性的 EventBus 文本消息。
 */
object CheckSourceTaskStore {
    private val mutableState = MutableStateFlow(CheckSourceTaskState())
    val state: StateFlow<CheckSourceTaskState> = mutableState.asStateFlow()

    @Synchronized
    fun begin(sources: List<BookSourcePart>) {
        val now = System.currentTimeMillis()
        mutableState.value = CheckSourceTaskState(
            runId = now,
            status = CheckSourceTaskStatus.RUNNING,
            startedAtMillis = now,
            items = sources.distinctBy(BookSourcePart::bookSourceUrl).map { source ->
                CheckSourceItemState(
                    origin = source.bookSourceUrl,
                    sourceName = source.bookSourceName,
                    sourceType = source.bookSourceType,
                )
            },
        )
    }

    @Synchronized
    fun markRunning(origin: String, sourceName: String, sourceType: Int) {
        updateItem(origin) { item ->
            item.copy(
                sourceName = sourceName,
                sourceType = sourceType,
                status = CheckSourceItemStatus.RUNNING,
                stage = CheckSourceStage.PREPARING,
                message = "",
                durationMillis = 0L,
                updatedAt = System.currentTimeMillis(),
            )
        }
        updateCurrent(origin, sourceName, CheckSourceStage.PREPARING)
    }

    @Synchronized
    fun markStage(origin: String, sourceName: String, stage: CheckSourceStage) {
        updateItem(origin) { item ->
            item.copy(
                status = CheckSourceItemStatus.RUNNING,
                stage = stage,
                updatedAt = System.currentTimeMillis(),
            )
        }
        updateCurrent(origin, sourceName, stage)
    }

    @Synchronized
    fun markPassed(
        origin: String,
        durationMillis: Long,
        resultKind: CheckSourceResultKind = CheckSourceResultKind.STANDARD,
    ) {
        completeItem(
            origin = origin,
            status = CheckSourceItemStatus.PASSED,
            message = "",
            durationMillis = durationMillis,
            resultKind = resultKind,
        )
    }

    @Synchronized
    fun markFailed(origin: String, message: String, durationMillis: Long) {
        completeItem(
            origin = origin,
            status = CheckSourceItemStatus.FAILED,
            message = message,
            durationMillis = durationMillis,
        )
    }

    @Synchronized
    fun markBlocked(
        origin: String,
        durationMillis: Long,
    ) {
        completeItem(
            origin = origin,
            status = CheckSourceItemStatus.BLOCKED,
            message = "",
            durationMillis = durationMillis,
        )
    }

    @Synchronized
    fun finish(cancelled: Boolean) {
        val current = mutableState.value
        if (current.status != CheckSourceTaskStatus.RUNNING) return
        val now = System.currentTimeMillis()
        val items = if (cancelled) {
            current.items.map { item ->
                if (item.status == CheckSourceItemStatus.RUNNING) {
                    item.copy(
                        status = CheckSourceItemStatus.CANCELLED,
                        message = "",
                        updatedAt = now,
                    )
                } else {
                    item
                }
            }
        } else {
            current.items
        }
        mutableState.value = current.copy(
            status = if (cancelled) {
                CheckSourceTaskStatus.CANCELLED
            } else {
                CheckSourceTaskStatus.COMPLETED
            },
            items = items,
            currentOrigin = null,
            currentSourceName = "",
            finishedAtMillis = now,
            resultsAcknowledged = false,
        )
    }

    @Synchronized
    fun markResultsAcknowledged() {
        val current = mutableState.value
        if (current.status == CheckSourceTaskStatus.RUNNING || current.resultsAcknowledged) return
        mutableState.value = current.copy(resultsAcknowledged = true)
    }

    @Synchronized
    fun dismissManageEntry() {
        mutableState.value = mutableState.value.copy(resultsAcknowledged = true)
    }

    private fun completeItem(
        origin: String,
        status: CheckSourceItemStatus,
        message: String,
        durationMillis: Long,
        resultKind: CheckSourceResultKind = CheckSourceResultKind.STANDARD,
    ) {
        updateItem(origin) { item ->
            item.copy(
                status = status,
                message = message,
                durationMillis = durationMillis,
                resultKind = resultKind,
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    private fun updateCurrent(origin: String, sourceName: String, stage: CheckSourceStage) {
        val current = mutableState.value
        if (current.status != CheckSourceTaskStatus.RUNNING) return
        mutableState.value = current.copy(
            currentOrigin = origin,
            currentSourceName = sourceName,
            currentStage = stage,
        )
    }

    private inline fun updateItem(
        origin: String,
        transform: (CheckSourceItemState) -> CheckSourceItemState,
    ) {
        val current = mutableState.value
        if (current.status != CheckSourceTaskStatus.RUNNING) return
        val index = current.items.indexOfFirst { it.origin == origin }
        if (index < 0) return
        val updated = current.items.toMutableList()
        updated[index] = transform(updated[index])
        mutableState.value = current.copy(items = updated)
    }
}
