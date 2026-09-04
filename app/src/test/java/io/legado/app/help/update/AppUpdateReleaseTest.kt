package io.legado.app.help.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.security.MessageDigest

class AppUpdateReleaseTest {

    @Test
    fun `build channel is explicit`() {
        assertEquals(AppVariant.OFFICIAL, AppVariant.fromUpdateChannel("official"))
        assertEquals(AppVariant.BETA_RELEASE, AppVariant.fromUpdateChannel("beta"))
        assertEquals(AppVariant.UNKNOWN, AppVariant.fromUpdateChannel("other"))
    }

    @Test
    fun `version comparison ignores debug suffix and compares numeric fields`() {
        val current = requireNotNull(AppVersion.parse("3.26.090407debug"))
        val sameRelease = requireNotNull(AppVersion.parse("3.26.090407"))
        val nextRelease = requireNotNull(AppVersion.parse("3.26.090408"))

        assertEquals(0, current.compareTo(sameRelease))
        assertTrue(nextRelease > current)
    }

    @Test
    fun `asset version parser requires the expected channel suffix`() {
        assertEquals(
            "3.26.090407",
            AppVersion.fromAssetName(
                "legado_NG_3.26.09040726_release.apk",
                AppVariant.OFFICIAL,
            )?.value
        )
        assertNull(
            AppVersion.fromAssetName(
                "legado_NG_3.26.09040726_release.apk",
                AppVariant.BETA_RELEASE,
            )
        )
    }

    @Test
    fun `release mapping keeps github digest size and authoritative version`() {
        val release = GithubRelease(
            assets = listOf(
                Asset(
                    apkUrl = OFFICIAL_DOWNLOAD_URL,
                    contentType = "application/vnd.android.package-archive",
                    createdAt = "2026-09-04T01:00:00Z",
                    downloadCount = 12,
                    digest = "sha256:${"a".repeat(64)}",
                    id = 1L,
                    name = OFFICIAL_FILE_NAME,
                    size = 36_392_690L,
                    state = "uploaded",
                    url = "https://api.github.com/repos/joestar817/legado_NG/releases/assets/1",
                )
            ),
            body = "更新内容",
            isPreRelease = false,
            tagName = "3.26.090407",
        )

        val mapped = release.gitReleaseToAppReleaseInfo().single()

        assertEquals(AppVariant.OFFICIAL, mapped.appVariant)
        assertEquals("3.26.090407", mapped.versionName)
        assertEquals(36_392_690L, mapped.fileSize)
        assertEquals("a".repeat(64), mapped.sha256)
    }

    @Test
    fun `relay candidates keep github as final fallback`() {
        val apiUrl = "https://api.github.com/repos/joestar817/legado_NG/releases/latest"

        assertEquals(
            listOf("https://gh-proxy.com/$apiUrl", apiUrl),
            AppUpdateRelay.apiCandidates(apiUrl)
        )
        assertEquals(
            listOf(
                "https://download.githubcdn.com?url=" +
                    "https%3A%2F%2Fgithub.com%2Fjoestar817%2Flegado_NG%2Freleases%2Fdownload%2F" +
                    "3.26.090407%2Flegado_NG_3.26.09040726_release.apk",
                "https://gh-proxy.com/$OFFICIAL_DOWNLOAD_URL",
                OFFICIAL_DOWNLOAD_URL,
            ),
            AppUpdateRelay.downloadCandidates(OFFICIAL_DOWNLOAD_URL, OFFICIAL_FILE_NAME)
        )
    }

    @Test
    fun `download verifier checks size and sha256`() {
        val bytes = "legado-ng".toByteArray()
        val sha256 = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

        assertNull(
            AppUpdateDownloadVerifier.verify(
                ByteArrayInputStream(bytes),
                bytes.size.toLong(),
                sha256,
            )
        )
        assertEquals(
            "文件大小不一致",
            AppUpdateDownloadVerifier.verify(
                ByteArrayInputStream(bytes),
                bytes.size + 1L,
                sha256,
            )
        )
    }

    private companion object {
        const val OFFICIAL_FILE_NAME = "legado_NG_3.26.09040726_release.apk"
        const val OFFICIAL_DOWNLOAD_URL =
            "https://github.com/joestar817/legado_NG/releases/download/3.26.090407/" +
                OFFICIAL_FILE_NAME
    }
}
