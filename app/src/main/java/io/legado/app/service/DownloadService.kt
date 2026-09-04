package io.legado.app.service

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseService
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.constant.IntentAction
import io.legado.app.constant.NotificationId
import io.legado.app.help.update.AppUpdateDownloadVerifier
import io.legado.app.model.Download
import io.legado.app.utils.IntentType
import io.legado.app.utils.openFileUri
import io.legado.app.utils.servicePendingIntent
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import splitties.systemservices.downloadManager
import splitties.systemservices.notificationManager

/**
 * 下载文件
 */
class DownloadService : BaseService() {
    private val groupKey = "${appCtx.packageName}.download"
    private val downloads = hashMapOf<Long, DownloadInfo>()
    private val completeDownloads = hashSetOf<Long>()
    private val validatingDownloads = hashSetOf<Long>()
    private var upStateJob: Job? = null
    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            queryState()
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate() {
        super.onCreate()
        ContextCompat.registerReceiver(
            this,
            downloadReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(downloadReceiver)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            IntentAction.start -> startDownload(
                intent.getStringArrayListExtra(Download.EXTRA_URLS)
                    ?: intent.getStringExtra(Download.EXTRA_URL)?.let { url -> arrayListOf(url) },
                intent.getStringExtra(Download.EXTRA_FILE_NAME),
                intent.getLongExtra(Download.EXTRA_EXPECTED_SIZE, -1L),
                intent.getStringExtra(Download.EXTRA_EXPECTED_SHA256),
            )

            IntentAction.play -> {
                val id = intent.getLongExtra("downloadId", 0)
                if (completeDownloads.contains(id)) {
                    openDownload(id, downloads[id]?.fileName)
                } else {
                    toastOnUi("未完成,下载的文件夹Download")
                }
            }

            IntentAction.stop -> {
                val downloadId = intent.getLongExtra("downloadId", 0)
                removeDownload(downloadId)
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    /**
     * 开始下载
     */
    @Synchronized
    private fun startDownload(
        urls: List<String>?,
        fileName: String?,
        expectedSize: Long,
        expectedSha256: String?,
    ) {
        val candidates = urls?.filter(String::isNotBlank)?.distinct().orEmpty()
        if (candidates.isEmpty() || fileName == null) {
            if (downloads.isEmpty()) {
                stopSelf()
            }
            return
        }
        if (downloads.values.any { it.fileName == fileName && it.urls == candidates }) {
            toastOnUi("已在下载列表")
            return
        }
        kotlin.runCatching {
            enqueueDownload(
                DownloadInfo(
                    urls = candidates,
                    fileName = fileName,
                    notificationId = NotificationId.Download + downloads.size,
                    expectedSize = expectedSize,
                    expectedSha256 = expectedSha256,
                )
            )
            queryState()
            if (upStateJob == null) {
                checkDownloadState()
            }
        }.onFailure {
            it.printStackTrace()
            val msg = when (it) {
                is SecurityException -> "下载出错,没有存储权限"
                else -> "下载出错,${it.localizedMessage}"
            }
            toastOnUi(msg)
            AppLog.put(msg, it)
        }
    }

    private fun enqueueDownload(downloadInfo: DownloadInfo): Long {
        val request = DownloadManager.Request(Uri.parse(downloadInfo.url))
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN)
        request.setDestinationInExternalPublicDir(
            Environment.DIRECTORY_DOWNLOADS,
            downloadInfo.fileName
        )
        val downloadId = downloadManager.enqueue(request)
        downloads[downloadId] = downloadInfo
        return downloadId
    }

    /**
     * 取消下载
     */
    @Synchronized
    private fun removeDownload(downloadId: Long) {
        if (!completeDownloads.contains(downloadId)) {
            downloadManager.remove(downloadId)
        }
        downloads.remove(downloadId)
        completeDownloads.remove(downloadId)
        validatingDownloads.remove(downloadId)
        notificationManager.cancel(downloadId.toInt())
    }

    /**
     * 下载成功
     */
    @Synchronized
    private fun successDownload(downloadId: Long) {
        if (completeDownloads.contains(downloadId) || validatingDownloads.contains(downloadId)) {
            return
        }
        val downloadInfo = downloads[downloadId] ?: return
        if (downloadInfo.expectedSha256 == null || downloadInfo.expectedSize <= 0L) {
            completeDownload(downloadId, downloadInfo.fileName)
            return
        }

        validatingDownloads.add(downloadId)
        lifecycleScope.launch {
            val validationError = validateDownload(downloadId, downloadInfo)
            synchronized(this@DownloadService) {
                validatingDownloads.remove(downloadId)
                if (downloads[downloadId] != downloadInfo) {
                    return@synchronized
                }
                if (validationError == null) {
                    completeDownload(downloadId, downloadInfo.fileName)
                } else {
                    AppLog.put("下载文件校验失败($validationError)")
                    retryDownload(downloadId, validationError)
                }
            }
        }
    }

    private fun completeDownload(downloadId: Long, fileName: String) {
        completeDownloads.add(downloadId)
        openDownload(downloadId, fileName)
    }

    private suspend fun validateDownload(
        downloadId: Long,
        downloadInfo: DownloadInfo,
    ): String? = withContext(Dispatchers.IO) {
        val uri = downloadManager.getUriForDownloadedFile(downloadId)
            ?: return@withContext "无法读取下载文件"
        val inputStream = contentResolver.openInputStream(uri)
            ?: return@withContext "无法打开下载文件"
        inputStream.use {
            AppUpdateDownloadVerifier.verify(
                inputStream = it,
                expectedSize = downloadInfo.expectedSize,
                expectedSha256 = requireNotNull(downloadInfo.expectedSha256),
            )
        }
    }

    @Synchronized
    private fun retryDownload(downloadId: Long, reason: String) {
        val failedDownload = downloads.remove(downloadId) ?: return
        completeDownloads.remove(downloadId)
        validatingDownloads.remove(downloadId)
        downloadManager.remove(downloadId)

        var nextDownload = failedDownload.nextSource()
        var lastError: Throwable? = null
        while (nextDownload != null) {
            val result = kotlin.runCatching { enqueueDownload(nextDownload) }
            if (result.isSuccess) {
                return
            }
            lastError = result.exceptionOrNull()
            nextDownload = nextDownload.nextSource()
        }

        AppLog.put("下载失败($reason)", lastError)
        upDownloadNotification(
            downloadId = downloadId,
            notificationId = failedDownload.notificationId,
            content = "${failedDownload.fileName} ${getString(R.string.download_error)}",
            max = 0,
            progress = 0,
            startTime = failedDownload.startTime,
        )
        toastOnUi(R.string.download_error)
        if (downloads.isEmpty()) {
            stopSelf()
        }
    }

    private fun checkDownloadState() {
        upStateJob?.cancel()
        upStateJob = lifecycleScope.launch {
            while (isActive) {
                queryState()
                delay(1000)
            }
        }
    }

    /**
     * 查询下载进度
     */
    @Synchronized
    private fun queryState() {
        if (downloads.isEmpty()) {
            stopSelf()
            return
        }
        val ids = downloads.keys
        val query = DownloadManager.Query()
        query.setFilterById(*ids.toLongArray())
        val failedDownloads = arrayListOf<Long>()
        downloadManager.query(query).use { cursor ->
            if (cursor.moveToFirst()) {
                val idIndex = cursor.getColumnIndex(DownloadManager.COLUMN_ID)
                val progressIndex =
                    cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val fileSizeIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                do {
                    val id = cursor.getLong(idIndex)
                    val progress = cursor.getInt(progressIndex)
                    val max = cursor.getInt(fileSizeIndex)
                    val status = when (cursor.getInt(statusIndex)) {
                        DownloadManager.STATUS_PAUSED -> getString(R.string.pause)
                        DownloadManager.STATUS_PENDING -> getString(R.string.wait_download)
                        DownloadManager.STATUS_RUNNING -> getString(R.string.downloading)
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            successDownload(id)
                            if (validatingDownloads.contains(id)) {
                                getString(R.string.download_verifying)
                            } else {
                                getString(R.string.download_success)
                            }
                        }

                        DownloadManager.STATUS_FAILED -> {
                            if (downloads[id]?.hasNextSource == true) {
                                failedDownloads.add(id)
                            }
                            getString(R.string.download_error)
                        }
                        else -> getString(R.string.unknown_state)
                    }
                    downloads[id]?.let { downloadInfo ->
                        upDownloadNotification(
                            id,
                            downloadInfo.notificationId,
                            "${downloadInfo.fileName} $status",
                            max,
                            progress,
                            downloadInfo.startTime
                        )
                    }
                } while (cursor.moveToNext())
            }
        }
        failedDownloads.forEach { downloadId ->
            retryDownload(downloadId, getString(R.string.download_error))
        }
    }

    /**
     * 打开下载文件
     */
    private fun openDownload(downloadId: Long, fileName: String?) {
        kotlin.runCatching {
            downloadManager.getUriForDownloadedFile(downloadId)?.let { uri ->
                val type = IntentType.from(fileName)
                openFileUri(uri, type)
            }
        }.onFailure {
            AppLog.put("打开下载文件${fileName}出错", it)
        }
    }

    override fun startForegroundNotification() {
        val notification = NotificationCompat.Builder(this, AppConst.channelIdDownload)
            .setSmallIcon(R.drawable.ic_download)
            .setSubText(getString(R.string.action_download))
            .setGroup(groupKey)
            .setGroupSummary(true)
            .setOngoing(true)
            .build()
        startForeground(NotificationId.DownloadService, notification)
    }

    /**
     * 更新通知
     */
    private fun upDownloadNotification(
        downloadId: Long,
        notificationId: Int,
        content: String,
        max: Int,
        progress: Int,
        startTime: Long
    ) {
        val notificationBuilder = NotificationCompat.Builder(this, AppConst.channelIdDownload)
            .setSmallIcon(R.drawable.ic_download)
            .setSubText(getString(R.string.action_download))
            .setContentTitle(content)
            .setOnlyAlertOnce(true)
            .setContentIntent(
                servicePendingIntent<DownloadService>(IntentAction.play, downloadId.toInt()) {
                    putExtra("downloadId", downloadId)
                }
            )
            .setDeleteIntent(
                servicePendingIntent<DownloadService>(IntentAction.stop, downloadId.toInt()) {
                    putExtra("downloadId", downloadId)
                }
            )
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setGroup(groupKey)
            .setWhen(startTime)
        if (progress < max) {
            notificationBuilder.setProgress(max, progress, false)
        }
        notificationManager.notify(notificationId, notificationBuilder.build())
    }

    private data class DownloadInfo(
        val urls: List<String>,
        val fileName: String,
        val notificationId: Int,
        val sourceIndex: Int = 0,
        val expectedSize: Long = -1L,
        val expectedSha256: String? = null,
        val startTime: Long = System.currentTimeMillis(),
    ) {
        val url: String
            get() = urls[sourceIndex]

        val hasNextSource: Boolean
            get() = sourceIndex + 1 < urls.size

        fun nextSource(): DownloadInfo? {
            val nextIndex = sourceIndex + 1
            return if (nextIndex < urls.size) copy(sourceIndex = nextIndex) else null
        }
    }

}
