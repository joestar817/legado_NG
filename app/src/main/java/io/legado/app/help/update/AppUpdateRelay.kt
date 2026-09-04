package io.legado.app.help.update

import java.io.InputStream
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest

internal object AppUpdateRelay {

    private const val API_RELAY_PREFIX = "https://gh-proxy.com/"
    private const val DOWNLOAD_RELAY_PREFIX = "https://gh-proxy.com/"
    private const val STREAM_DOWNLOAD_URL = "https://download.githubcdn.com?url="
    private const val RELEASE_OWNER = "joestar817"
    private const val RELEASE_REPOSITORY = "legado_NG"

    fun apiCandidates(gitHubUrl: String): List<String> {
        require(isAllowedGitHubApiUrl(gitHubUrl)) { "不支持的 GitHub 更新接口" }
        return listOf(API_RELAY_PREFIX + gitHubUrl, gitHubUrl)
    }

    fun downloadCandidates(gitHubUrl: String, fileName: String): List<String> {
        require(isAllowedReleaseDownloadUrl(gitHubUrl, fileName)) { "不支持的 GitHub 下载地址" }
        val encodedUrl = URLEncoder.encode(gitHubUrl, Charsets.UTF_8.name())
        return listOf(
            STREAM_DOWNLOAD_URL + encodedUrl,
            DOWNLOAD_RELAY_PREFIX + gitHubUrl,
            gitHubUrl,
        )
    }

    fun isAllowedReleaseDownloadUrl(url: String, fileName: String): Boolean {
        val uri = url.toUriOrNull() ?: return false
        if (uri.scheme != "https" || uri.host != "github.com" || uri.port != -1) return false
        if (uri.rawQuery != null || uri.rawFragment != null || uri.userInfo != null) return false
        val pathSegments = uri.path.split('/').filter(String::isNotEmpty)
        return pathSegments.size == 6 &&
            pathSegments[0] == RELEASE_OWNER &&
            pathSegments[1] == RELEASE_REPOSITORY &&
            pathSegments[2] == "releases" &&
            pathSegments[3] == "download" &&
            pathSegments[4].isNotBlank() &&
            pathSegments[5] == fileName
    }

    private fun isAllowedGitHubApiUrl(url: String): Boolean {
        val uri = url.toUriOrNull() ?: return false
        if (uri.scheme != "https" || uri.host != "api.github.com" || uri.port != -1) return false
        if (uri.rawQuery != null || uri.rawFragment != null || uri.userInfo != null) return false
        return uri.path == "/repos/$RELEASE_OWNER/$RELEASE_REPOSITORY/releases/latest" ||
            uri.path == "/repos/$RELEASE_OWNER/$RELEASE_REPOSITORY/releases/tags/beta"
    }

    private fun String.toUriOrNull(): URI? = runCatching { URI(this) }.getOrNull()
}

internal object AppUpdateDownloadVerifier {

    private val sha256Regex = Regex("^[0-9a-f]{64}$")

    fun verify(
        inputStream: InputStream,
        expectedSize: Long,
        expectedSha256: String,
    ): String? {
        if (expectedSize <= 0L) return "文件大小无效"
        if (!sha256Regex.matches(expectedSha256)) return "SHA-256格式无效"

        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var totalSize = 0L
        while (true) {
            val read = inputStream.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
            totalSize += read
        }
        if (totalSize != expectedSize) return "文件大小不一致"

        val actualSha256 = digest.digest().joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
        return if (actualSha256 == expectedSha256) null else "SHA-256不一致"
    }
}
