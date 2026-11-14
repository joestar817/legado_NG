package io.legado.app.ui.book.read.page

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import io.legado.app.R
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.ui.design.theme.InterfaceFontTestApplication
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = InterfaceFontTestApplication::class)
class PageDecorationImageVisibilityTest {
    @Test
    fun selectedImagesDisplayAndRemovingOneHidesOnlyThatImage() {
        val config = ReadBookConfig.config
        val original = config.copy()
        try {
            config.headerImage = "header.png"
            config.headerImageWidth = 1600
            config.headerImageHeight = 400
            config.headerImageEnabled = false
            config.footerImage = "footer.png"
            config.footerImageWidth = 1600
            config.footerImageHeight = 160
            config.footerImageEnabled = false

            val page = PageView(ApplicationProvider.getApplicationContext<Context>())
            val header = page.findViewById<View>(R.id.iv_header_image)
            val footer = page.findViewById<View>(R.id.iv_footer_image)
            assertEquals(View.VISIBLE, header.visibility)
            assertEquals(View.VISIBLE, footer.visibility)

            config.headerImage = ""
            config.headerImageEnabled = true
            page.upStyle()
            assertEquals(View.GONE, header.visibility)
            assertEquals(View.VISIBLE, footer.visibility)

            config.footerImage = ""
            config.footerImageEnabled = true
            page.upStyle()
            assertEquals(View.GONE, footer.visibility)
        } finally {
            config.headerImage = original.headerImage
            config.headerImageWidth = original.headerImageWidth
            config.headerImageHeight = original.headerImageHeight
            config.headerImageEnabled = original.headerImageEnabled
            config.footerImage = original.footerImage
            config.footerImageWidth = original.footerImageWidth
            config.footerImageHeight = original.footerImageHeight
            config.footerImageEnabled = original.footerImageEnabled
        }
    }
}
