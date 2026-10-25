package io.legado.app.help.book

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookMenuCapabilitiesTest {
    private fun capabilities(
        local: Boolean = false,
        epub: Boolean = false,
        audio: Boolean = false,
        video: Boolean = false,
        image: Boolean = false,
        webFile: Boolean = false,
        pdf: Boolean = false,
    ) = BookMenuCapabilities(local, epub, audio, video, image, webFile, pdf)

    @Test
    fun onlineTextRetainsAllActions() {
        val menu = capabilities()
        assertTrue(menu.textContent && menu.listen && menu.changeSource && menu.download)
        assertTrue(menu.export && menu.simulatedReading && menu.update && menu.showCache)
    }

    @Test
    fun localEpubRetainsTextAbilitiesWithoutNetworkOrSimulatedReading() {
        val menu = capabilities(local = true, epub = true)
        assertFalse(menu.changeSource || menu.download || menu.update || menu.simulatedReading)
        assertTrue(menu.textContent && menu.listen && menu.export && menu.chapterList && menu.showCache)
    }

    @Test
    fun otherLocalTextKeepsExistingSimulation() {
        val menu = capabilities(local = true)
        assertFalse(menu.changeSource || menu.download || menu.update)
        assertTrue(menu.textContent && menu.listen && menu.export && menu.simulatedReading)
    }

    @Test
    fun mangaKeepsSimulationCacheAndIllustratedExport() {
        val menu = capabilities(image = true)
        assertFalse(menu.textContent || menu.listen)
        assertTrue(menu.simulatedReading && menu.download && menu.export && menu.changeSource)
    }

    @Test
    fun audioKeepsPlaybackAndSimulationButCannotExportLinksAsText() {
        val menu = capabilities(audio = true)
        assertFalse(menu.textContent || menu.export)
        assertTrue(menu.listen && menu.simulatedReading && menu.download && menu.showCache)
    }

    @Test
    fun videoDoesNotOfferTextOrOfflineAudioCapabilities() {
        val menu = capabilities(video = true)
        assertFalse(menu.textContent || menu.listen || menu.simulatedReading || menu.download || menu.export || menu.showCache)
        assertTrue(menu.changeSource && menu.chapterList && menu.update)
    }

    @Test
    fun downloadableFileIsNotChapterContent() {
        val menu = capabilities(webFile = true)
        assertFalse(menu.chapterList || menu.textContent || menu.listen || menu.download || menu.export || menu.showCache)
        assertFalse(menu.simulatedReading)
        assertTrue(menu.changeSource)
    }

    @Test
    fun pdfIsNotTreatedAsParsedNovelText() {
        val menu = capabilities(local = true, pdf = true)
        assertFalse(menu.textContent || menu.listen || menu.export || menu.simulatedReading)
        assertFalse(menu.changeSource || menu.update || menu.download || menu.showCache)
    }

    @Test
    fun mixedMediaDoesNotGainTextActions() {
        val menu = capabilities(audio = true, video = true, image = true)
        assertFalse(menu.textContent || menu.export || menu.download || menu.simulatedReading)
    }
}
