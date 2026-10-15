package io.legado.app.help.cache

import io.legado.app.exception.ConcurrentException
import io.legado.app.help.config.AppConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * 拟人化缓存节奏控制。
 *
 * 后台离线缓存过去是以固定、均匀、可预测的周期持续请求书源，很容易被源站风控
 * 识别为爬虫。本控制器把缓存请求改造成更接近真人阅读的节奏：
 *
 * 1. 同一书源串行化，同一时刻只允许一个缓存请求在途；
 * 2. 请求之间插入随机间隔，避免固定周期；
 * 3. 依据上一章正文长度轻微缩放间隔，长章节多停顿；
 * 4. 每缓存若干章后安排一次较长的休息；
 * 5. 错误重试使用指数退避加抖动，而不是固定的一秒；
 * 6. 可选夜间静默。
 *
 * 只作用于后台缓存路径，不涉及前台阅读：前台阅读由用户操作驱动，节奏天然是慢的。
 * 全部行为以 [AppConfig.readingPaceEnabled] 为总开关。
 */
object ReadingPaceController {

    /** 长休息：每缓存这么多章安排一次 */
    private const val LONG_REST_EVERY = 30

    /** 长休息时长区间（毫秒） */
    private const val LONG_REST_MIN_MILLIS = 45_000L
    private const val LONG_REST_MAX_MILLIS = 150_000L

    /** 正文长度参考值，用于把间隔按章节长短缩放 */
    private const val CONTENT_LENGTH_REFERENCE = 6000.0

    /** 错误退避：基准与上限（毫秒） */
    private const val BACKOFF_BASE_MILLIS = 1_000L
    private const val BACKOFF_MAX_MILLIS = 60_000L

    /** 夜间静默时段 [QUIET_START_HOUR, QUIET_END_HOUR) */
    private const val QUIET_START_HOUR = 23
    private const val QUIET_END_HOUR = 7

    /** 同一书源的串行门 */
    private val sourceGates = ConcurrentHashMap<String, Mutex>()

    /** 每个书源上一次请求结束的时间点 */
    private val lastFinishTime = ConcurrentHashMap<String, Long>()

    /** 每个书源已缓存章节数，用于长休息调度 */
    private val chapterCounter = ConcurrentHashMap<String, Int>()

    /** 每个书源上一次取到的正文长度，用于间隔的长度自适应 */
    private val lastContentLength = ConcurrentHashMap<String, Int>()

    /** 当前等待提示，供通知栏展示；无等待时为 null */
    @Volatile
    private var waitingText: String? = null

    /** 通知栏用的一行状态，例如「节奏等待:3s」 */
    val statusText: String?
        get() = waitingText

    /**
     * 取得一次受节奏约束的请求机会。同一书源串行，不同书源仍可并行。
     */
    suspend fun <T> withPacedTurn(sourceKey: String, block: suspend () -> T): T {
        if (!AppConfig.readingPaceEnabled || sourceKey.isBlank()) {
            return block()
        }
        return gateOf(sourceKey).withLock {
            awaitTurn(sourceKey)
            try {
                return@withLock block()
            } finally {
                lastFinishTime[sourceKey] = System.currentTimeMillis()
            }
        }
    }

    /**
     * 记录一次成功取回的正文，供下一轮间隔做长度自适应并推进长休息计数。
     */
    fun onContentFetched(sourceKey: String, contentLength: Int) {
        if (sourceKey.isBlank()) {
            return
        }
        lastContentLength[sourceKey] = contentLength.coerceAtLeast(0)
        chapterCounter[sourceKey] = (chapterCounter[sourceKey] ?: 0) + 1
    }

    /**
     * 错误重试的等待时长：指数退避加抖动，并尊重源站给出的限流等待时间。
     *
     * @param attempt 当前第几次尝试（1 起）
     * @param error   本次错误，若为 [ConcurrentException] 则采用其 waitTime 下限
     */
    fun nextBackoffMillis(attempt: Int, error: Throwable? = null): Long {
        val rateLimitWait = (error as? ConcurrentException)?.waitTime ?: 0L
        if (!AppConfig.readingPaceEnabled) {
            return max(1_000L, rateLimitWait)
        }
        val safeAttempt = attempt.coerceIn(1, 8)
        val base = BACKOFF_BASE_MILLIS shl (safeAttempt - 1)
        val capped = min(base, BACKOFF_MAX_MILLIS)
        val jitter = capped / 5
        val backedOff = if (jitter <= 0L) {
            capped
        } else {
            randomBetween(capped - jitter, capped + jitter)
        }
        return max(backedOff, rateLimitWait)
    }

    /**
     * 为既有并发率限流器附加少量抖动，使受限请求不再是完美周期。
     * 只放大等待，不缩短，因此不会让请求变得更激进。
     */
    fun withRateLimitJitter(waitTime: Long): Long {
        if (waitTime <= 0L || !AppConfig.readingPaceEnabled) {
            return waitTime
        }
        val jitter = max(1L, waitTime / 8)
        return waitTime + Random.nextLong(0L, jitter + 1L)
    }

    private fun gateOf(sourceKey: String): Mutex {
        sourceGates[sourceKey]?.let { return it }
        val created = Mutex()
        return sourceGates.putIfAbsent(sourceKey, created) ?: created
    }

    private suspend fun awaitTurn(sourceKey: String) {
        val quietWait = quietHoursWaitMillis()
        if (quietWait > 0L) {
            sleep(quietWait)
        }
        val target = nextIntervalMillis(sourceKey)
        val lastFinish = lastFinishTime[sourceKey] ?: 0L
        if (lastFinish <= 0L) {
            return
        }
        val elapsed = System.currentTimeMillis() - lastFinish
        val remaining = target - elapsed
        if (remaining > 0L) {
            sleep(remaining)
        }
    }

    private fun nextIntervalMillis(sourceKey: String): Long {
        val minInterval = AppConfig.readingPaceMinInterval.toLong()
        val maxInterval = max(minInterval, AppConfig.readingPaceMaxInterval.toLong())
        var interval = randomBetween(minInterval, maxInterval)
        val count = chapterCounter[sourceKey] ?: 0
        if (AppConfig.readingPaceLongRest && count > 0 && count % LONG_REST_EVERY == 0) {
            interval += randomBetween(LONG_REST_MIN_MILLIS, LONG_REST_MAX_MILLIS)
        }
        val length = lastContentLength[sourceKey] ?: 0
        if (length > 0 && interval > 0L) {
            val scale = 0.8 + 0.6 * min(1.0, length / CONTENT_LENGTH_REFERENCE)
            interval = max(minInterval, (interval * scale).toLong())
        }
        return interval
    }

    private suspend fun sleep(millis: Long) {
        waitingText = formatWaiting(millis)
        try {
            delay(millis)
        } finally {
            waitingText = null
        }
    }

    private fun formatWaiting(millis: Long): String {
        val seconds = (millis + 999L) / 1000L
        return if (seconds <= 1L) "节奏等待:<1s" else "节奏等待:${seconds}s"
    }

    /** 夜间静默：返回需要等待到静默结束的毫秒数，0 表示无需等待 */
    private fun quietHoursWaitMillis(): Long {
        if (!AppConfig.readingPaceQuietHours) {
            return 0L
        }
        val now = Calendar.getInstance()
        val hour = now.get(Calendar.HOUR_OF_DAY)
        val inQuiet = hour >= QUIET_START_HOUR || hour < QUIET_END_HOUR
        if (!inQuiet) {
            return 0L
        }
        val resume = Calendar.getInstance().apply {
            if (hour >= QUIET_START_HOUR) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
            set(Calendar.HOUR_OF_DAY, QUIET_END_HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return max(0L, resume.timeInMillis - now.timeInMillis)
    }

    private fun randomBetween(minValue: Long, maxValue: Long): Long {
        if (maxValue <= minValue) {
            return minValue
        }
        return Random.nextLong(minValue, maxValue + 1L)
    }
}
