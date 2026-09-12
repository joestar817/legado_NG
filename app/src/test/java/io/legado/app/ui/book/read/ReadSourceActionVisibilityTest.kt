package io.legado.app.ui.book.read

import org.junit.Test
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class ReadSourceActionVisibilityTest {

    @Test
    fun loginUiOnlySourceShowsLoginWithoutShowingChapterPay() {
        val visibility = resolveReadSourceActionVisibility(
            loginUrl = "",
            loginUi = "{\"version\":2}",
            chapterIsVip = true,
            chapterIsPay = false,
        )

        assertTrue(visibility.showLogin)
        assertFalse(visibility.showChapterPay)
    }

    @Test
    fun webLoginSourceKeepsExistingChapterPayRule() {
        val unpaidVip = resolveReadSourceActionVisibility(
            loginUrl = "https://example.com/login",
            loginUi = null,
            chapterIsVip = true,
            chapterIsPay = false,
        )
        val paidVip = resolveReadSourceActionVisibility(
            loginUrl = "https://example.com/login",
            loginUi = null,
            chapterIsVip = true,
            chapterIsPay = true,
        )

        assertTrue(unpaidVip.showLogin)
        assertTrue(unpaidVip.showChapterPay)
        assertFalse(paidVip.showChapterPay)
    }

    @Test
    fun sourceWithoutLoginCapabilityKeepsLoginHidden() {
        val visibility = resolveReadSourceActionVisibility(
            loginUrl = null,
            loginUi = " ",
            chapterIsVip = false,
            chapterIsPay = false,
        )

        assertFalse(visibility.showLogin)
        assertFalse(visibility.showChapterPay)
    }
}
