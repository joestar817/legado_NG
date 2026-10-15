package io.legado.app.service

import android.app.Notification
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseService
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.constant.IntentAction
import io.legado.app.constant.NotificationId
import io.legado.app.data.appDb
import io.legado.app.help.book.update
import io.legado.app.help.config.AppConfig
import io.legado.app.model.CacheBook
import io.legado.app.model.webBook.WebBook
import io.legado.app.ui.main.MainActivity
import io.legado.app.utils.activityPendingIntent
import io.legado.app.utils.postEvent
import io.legado.app.utils.servicePendingIntent
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import splitties.init.appCtx
import splitties.systemservices.notificationManager
import java.util.concurrent.Executors
import kotlin.math.min

/**
 * 缓存书籍服务
 */
class CacheBookService : BaseService() {

    companion object {
        var isRun = false
            private set
    }

    private val threadCount = AppConfig.threadCount
    private var cachePool =
        Executors.newFixedThreadPool(min(threadCount, AppConst.MAX_THREAD)).asCoroutineDispatcher()
    private var downloadJob: Job? = null
    private var notificationContent = appCtx.getString(R.string.service_starting)
    private var mutex = Mutex()

    /**
     * 通知栏:标题是正在缓存的书名,副标题是状态与进度,不再出现「离线缓存」字样。
     * 多本时用 InboxStyle 逐本列名与进度。
     */
    private fun buildNotification(): Notification {
        val books = CacheBook.activeBookProgress
        val first = books.firstOrNull()
        val title = when {
            first == null -> getString(R.string.cache_notification_idle)
            books.size == 1 -> first.name
            else -> getString(R.string.cache_notification_more, first.name, books.size)
        }
        val done = books.sumOf { it.done }
        val total = books.sumOf { it.total }
        val statusRes = when {
            books.isEmpty() -> R.string.cache_notification_done
            books.any { it.paused } || CacheBook.isUserPaused -> R.string.cache_paused
            done == 0 -> R.string.cache_notification_queued
            else -> R.string.cache_notification_caching
        }
        val progressText = if (total > 0) {
            getString(R.string.cache_notification_progress, done, total)
        } else {
            notificationContent
        }
        val builder = NotificationCompat.Builder(this, AppConst.channelIdDownload)
            .setSmallIcon(R.drawable.ic_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentTitle(title)
            .setContentText("${getString(statusRes)} · $progressText")
            .setContentIntent(activityPendingIntent<MainActivity>("mainActivity") {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
            .addAction(
                R.drawable.ic_stop_black_24dp,
                getString(R.string.cancel),
                servicePendingIntent<CacheBookService>(IntentAction.stop)
            )
            .apply {
                //暂停/继续二选一,与「停止」并列常驻。
                if (CacheBook.isUserPaused) {
                    addAction(
                        R.drawable.ic_play_24dp,
                        getString(R.string.resume),
                        servicePendingIntent<CacheBookService>(IntentAction.resume, requestCode = 1)
                    )
                } else {
                    addAction(
                        R.drawable.ic_pause_24dp,
                        getString(R.string.pause),
                        servicePendingIntent<CacheBookService>(IntentAction.pause, requestCode = 2)
                    )
                }
            }
        when {
            //队列已空:不再转圈,直接收起进度条,避免「已完成」旁边还挂个转圈。
            books.isEmpty() -> builder.setProgress(0, 0, false)
            total > 0 -> builder.setProgress(total, done, false)
            else -> builder.setProgress(0, 0, true)
        }
        if (books.size > 1) {
            //多本时逐本列名,每行带自己的进度,而不是把书名挤成一坨。
            val style = NotificationCompat.InboxStyle()
            books.take(5).forEach { book ->
                val label = if (book.paused) {
                    getString(R.string.cache_paused)
                } else {
                    getString(R.string.cache_notification_caching)
                }
                style.addLine("${book.name} · $label ${book.done}/${book.total}")
            }
            if (books.size > 5) {
                style.setSummaryText(getString(R.string.cache_notification_more, books.first().name, books.size))
            }
            builder.setStyle(style)
        }
        return builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC).build()
    }

    override fun onCreate() {
        super.onCreate()
        isRun = true
        lifecycleScope.launch {
            while (isActive) {
                delay(1000)
                notificationContent = CacheBook.downloadSummary
                upCacheBookNotification()
                postEvent(EventBus.UP_DOWNLOAD, "")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            when (action) {
                IntentAction.start -> {
                    //显式发起缓存视为要继续,否则暂停期间点「缓存」会毫无反应。
                    CacheBook.setUserPaused(false)
                    addDownloadData(
                        intent.getStringExtra("bookUrl"),
                        intent.getIntExtra("start", 0),
                        intent.getIntExtra("end", 0)
                    )
                }

                IntentAction.remove -> removeDownload(intent.getStringExtra("bookUrl"))
                IntentAction.pause -> {
                    CacheBook.setUserPaused(true)
                    notificationContent = CacheBook.downloadSummary
                    upCacheBookNotification()
                }

                IntentAction.resume -> {
                    CacheBook.setUserPaused(false)
                    notificationContent = CacheBook.downloadSummary
                    upCacheBookNotification()
                }

                IntentAction.stop -> stopSelf()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        isRun = false
        cachePool.close()
        CacheBook.close()
        super.onDestroy()
        postEvent(EventBus.UP_DOWNLOAD, "")
    }

    private fun addDownloadData(bookUrl: String?, start: Int, end: Int) {
        bookUrl ?: return
        execute {
            val cacheBook = CacheBook.getOrCreate(bookUrl) ?: return@execute
            val chapterCount = appDb.bookChapterDao.getChapterCount(bookUrl)
            val book = cacheBook.book
            if (chapterCount == 0) {
                cacheBook.setLoading()
                mutex.withLock {
                    val name = book.name
                    if (book.tocUrl.isEmpty()) {
                        kotlin.runCatching {
                            WebBook.getBookInfoAwait(cacheBook.bookSource, book)
                        }.onFailure {
                            removeDownload(bookUrl)
                            val msg = "《$name》目录为空且加载详情页失败\n${it.localizedMessage}"
                            AppLog.put(msg, it, true)
                            return@execute
                        }
                    }
                    WebBook.getChapterListAwait(cacheBook.bookSource, book).onFailure {
                        if (book.totalChapterNum > 0) {
                            book.totalChapterNum = 0
                            book.update()
                        }
                        removeDownload(bookUrl)
                        val msg = "《$name》目录为空且加载目录失败\n${it.localizedMessage}"
                        AppLog.put(msg, it, true)
                        return@execute
                    }.getOrNull()?.let { toc ->
                        appDb.bookChapterDao.insert(*toc.toTypedArray())
                    }
                    book.update()
                }
            }
            val end2 = if (end < 0) {
                book.lastChapterIndex
            } else {
                min(end, book.lastChapterIndex)
            }
            cacheBook.addDownload(start, end2)
            notificationContent = CacheBook.downloadSummary
            upCacheBookNotification()
        }.onFinally {
            if (downloadJob == null) {
                download()
            }
        }
    }

    private fun removeDownload(bookUrl: String?) {
        CacheBook.cacheBookMap[bookUrl]?.stop()
        postEvent(EventBus.UP_DOWNLOAD, "")
        if (downloadJob == null && CacheBook.isRun) {
            download()
            return
        }
        if (CacheBook.cacheBookMap.isEmpty()) {
            stopSelf()
        }
    }

    private fun download() {
        downloadJob?.cancel()
        downloadJob = lifecycleScope.launch(cachePool) {
            CacheBook.startProcessJob(cachePool)
            stopSelf()
        }
    }

    private fun upCacheBookNotification() {
        notificationManager.notify(NotificationId.CacheBookService, buildNotification())
    }

    /**
     * 更新通知
     */
    override fun startForegroundNotification() {
        startForeground(NotificationId.CacheBookService, buildNotification())
    }

}
