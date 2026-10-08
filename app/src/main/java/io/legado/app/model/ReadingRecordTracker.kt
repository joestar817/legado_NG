package io.legado.app.model

import android.os.SystemClock
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.DailyReadingRecord
import io.legado.app.help.config.AppConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.ZoneId

/** Page/chapter checkpoints and session boundaries feed both views of the same reading record. */
internal object ReadingRecordTracker {
    private val lock = Any()
    private val accumulator = ReadingRecordAccumulator(AppConfig.enableReadRecord)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writes = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch {
            var failureReported = false
            for (request in writes) {
                while (true) {
                    val batch = synchronized(lock) { accumulator.pendingSnapshot() }
                    if (batch.isEmpty()) break
                    try {
                        appDb.runInTransaction {
                            batch.books.forEach { (bookName, total) ->
                                appDb.readRecordDao.addTime(bookName, total.readTime, total.lastRead)
                            }
                            appDb.dailyReadingRecordDao.addTimes(batch.days.map { (day, duration) ->
                                DailyReadingRecord(epochDay = day, readTime = duration)
                            })
                        }
                        synchronized(lock) { accumulator.acknowledge(batch) }
                        failureReported = false
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        if (!failureReported) AppLog.put("保存阅读时长失败，将自动重试", error)
                        failureReported = true
                        // Retry only already settled intervals. There is no independent calendar timer.
                        delay(30_000L)
                    }
                }
            }
        }
    }

    fun bindBook(kind: ReadingRecordKind, key: String, name: String) = update { time ->
        bindBook(kind, key, name, time)
    }

    fun checkpoint(kind: ReadingRecordKind) = update { time -> checkpoint(kind, time) }

    fun register(source: ReadingRecordSource, owner: Any) = update { time ->
        register(source, owner, time)
    }

    fun pageResumed(source: ReadingRecordSource, owner: Any, bookKey: String?) = update { time ->
        pageResumed(source, owner, bookKey, time)
    }

    fun setActive(source: ReadingRecordSource, owner: Any, active: Boolean) = update { time ->
        setActive(source, owner, active, time)
    }

    fun setPlaybackActive(source: ReadingRecordSource, owner: Any, active: Boolean, bookKey: String?) = update { time ->
        setPlaybackActive(source, owner, active, bookKey, time)
    }

    fun remove(source: ReadingRecordSource, owner: Any) = update { time ->
        remove(source, owner, time)
    }

    fun setEnabled(enabled: Boolean) = update { time -> setEnabled(enabled, time) }

    private inline fun update(action: ReadingRecordAccumulator.(ReadingRecordTime) -> Unit) {
        synchronized(lock) {
            accumulator.action(ReadingRecordTime(
                elapsedMillis = SystemClock.elapsedRealtime(),
                wallMillis = System.currentTimeMillis(),
                zone = ZoneId.systemDefault(),
            ))
            if (accumulator.hasPending) writes.trySend(Unit)
        }
    }
}
