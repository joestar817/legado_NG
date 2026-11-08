package io.legado.app.help.http

import io.legado.app.help.update.AppUpdateRelay
import org.junit.Assert.*
import org.junit.Test

class GitHubRelayTest {
    @Test fun resourceRefsAndPinnedContentUseTheUpdateProxyFirst() {
        listOf(
            "https://github.com/joestar817/legado-resources.git/info/refs?service=git-upload-pack",
            "https://raw.githubusercontent.com/joestar817/legado-resources/${"a".repeat(40)}/catalog.json",
            "https://raw.githubusercontent.com/joestar817/legado-resources/${"b".repeat(40)}/resources/fonts/中文/font.ttf",
        ).forEach { url ->
            assertEquals(listOf("https://gh-proxy.com/$url", url), GitHubRelay.proxyFirst(url))
        }
    }

    @Test fun updateReleaseWhitelistAndExistingCandidateOrderAreUnchanged() {
        val api = "https://api.github.com/repos/joestar817/legado_NG/releases/latest"
        assertEquals(listOf("https://gh-proxy.com/$api", api), AppUpdateRelay.apiCandidates(api))
        assertThrows(IllegalArgumentException::class.java) {
            AppUpdateRelay.apiCandidates("https://api.github.com/repos/joestar817/legado-resources/commits/catalog")
        }
        val file = "app.apk"
        val download = "https://github.com/joestar817/legado_NG/releases/download/v1/$file"
        val candidates = AppUpdateRelay.downloadCandidates(download, file)
        assertTrue(candidates.first().startsWith("https://download.githubcdn.com?url="))
        assertEquals(listOf("https://gh-proxy.com/$download", download), candidates.drop(1))
    }

    @Test fun nonGitHubCredentialsFragmentsAndNestedProxyAddressesAreRejected() {
        listOf(
            "http://github.com/owner/repo", "https://github.com.evil.invalid/owner/repo",
            "https://user:secret@github.com/owner/repo", "https://github.com:8443/owner/repo",
            "https://raw.githubusercontent.com/owner/repo/file#fragment",
            "https://gh-proxy.com/https://github.com/owner/repo",
        ).forEach { url ->
            assertThrows(IllegalArgumentException::class.java) { GitHubRelay.proxyFirst(url) }
        }
    }
}

