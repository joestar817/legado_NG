package io.legado.app.model

import android.os.SystemClock
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.DailyReadingRecord
import io.legado.app.help.config.AppConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.ZoneId

/** Independent daily totals; existing per-book reading records keep their semantics. */
internal object DailyReadingTracker {
    private const val FLUSH_INTERVAL = 30_000L
    private val lock = Any()
    private val accumulator = DailyReadingAccumulator(AppConfig.enableReadRecord)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writes = Channel<Unit>(Channel.CONFLATED)
    private var ticker: Job? = null

    init {
        scope.launch {
            var failureReported = false
            for (request in writes) {
                val batch = synchronized(lock) { accumulator.pendingSnapshot() }
                if (batch.isEmpty()) continue
                try {
                    // One transaction and one writer: a failed batch is retained for retry;
                    // a successful batch is subtracted without consuming newer samples.
                    appDb.dailyReadingRecordDao.addTimes(batch.map { (day, duration) ->
                        DailyReadingRecord(epochDay = day, readTime = duration)
                    })
                    synchronized(lock) { accumulator.acknowledge(batch) }
                    failureReported = false
                } catch (error: Exception) {
                    if (!failureReported) AppLog.put("保存每日阅读时长失败，将自动重试", error)
                    failureReported = true
                }
            }
        }
    }

    fun register(source: DailyReadingSource, owner: Any) = synchronized(lock) {
        accumulator.register(source, owner, now())
        updateTicker()
    }

    fun pageResumed(owner: Any) = synchronized(lock) {
        val time = now()
        accumulator.register(DailyReadingSource.PAGE, owner, time)
        accumulator.setActive(DailyReadingSource.PAGE, owner, true, time)
        updateTicker()
    }

    fun setActive(
        source: DailyReadingSource,
        owner: Any,
        active: Boolean,
        flush: Boolean = false,
    ) = synchronized(lock) {
        accumulator.setActive(source, owner, active, now())
        if (flush) writes.trySend(Unit)
        updateTicker()
    }

    fun remove(source: DailyReadingSource, owner: Any) = synchronized(lock) {
        accumulator.remove(source, owner, now())
        writes.trySend(Unit)
        updateTicker()
    }

    fun setEnabled(enabled: Boolean) = synchronized(lock) {
        accumulator.setEnabled(enabled, now())
        writes.trySend(Unit)
        updateTicker()
    }

    private fun updateTicker() {
        if (ticker != null || (!accumulator.hasActiveSources && !accumulator.hasPending)) return
        ticker = scope.launch {
            while (true) {
                delay(FLUSH_INTERVAL)
                synchronized(lock) {
                    accumulator.checkpoint(now())
                    if (accumulator.hasPending) writes.trySend(Unit)
                    if (!accumulator.hasActiveSources && !accumulator.hasPending) {
                        ticker = null
                        return@launch
                    }
                }
            }
        }
    }

    private fun now() = DailyReadingTime(
        elapsedMillis = SystemClock.elapsedRealtime(),
        wallMillis = System.currentTimeMillis(),
        zone = ZoneId.systemDefault(),
    )
}
