package io.legado.app.model

import android.content.Context
import io.legado.app.constant.IntentAction
import io.legado.app.service.DownloadService
import io.legado.app.utils.startService

object Download {

    internal const val EXTRA_URL = "url"
    internal const val EXTRA_URLS = "urls"
    internal const val EXTRA_FILE_NAME = "fileName"
    internal const val EXTRA_EXPECTED_SIZE = "expectedSize"
    internal const val EXTRA_EXPECTED_SHA256 = "expectedSha256"

    fun start(context: Context, url: String, fileName: String) {
        context.startService<DownloadService> {
            action = IntentAction.start
            putExtra(EXTRA_URL, url)
            putExtra(EXTRA_FILE_NAME, fileName)
        }
    }

    fun start(
        context: Context,
        urls: List<String>,
        fileName: String,
        expectedSize: Long,
        expectedSha256: String,
    ) {
        context.startService<DownloadService> {
            action = IntentAction.start
            putStringArrayListExtra(EXTRA_URLS, ArrayList(urls))
            putExtra(EXTRA_FILE_NAME, fileName)
            putExtra(EXTRA_EXPECTED_SIZE, expectedSize)
            putExtra(EXTRA_EXPECTED_SHA256, expectedSha256)
        }
    }
}
