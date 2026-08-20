package io.legado.app.help.book

import org.junit.Assert.assertEquals
import org.junit.Test

class ExportContentImageProcessorTest {

    @Test
    fun plainTextRemovesAllImageTags() {
        val content = """前文<img src="normal.jpg">中间<IMG alt='x' src='other.png' />后文"""

        assertEquals(
            "前文中间后文",
            ExportContentImageProcessor.process(
                content = content,
                plainText = true,
                filterInteractiveImages = false,
            ),
        )
    }

    @Test
    fun interactiveFilterAcceptsUppercaseImageTag() {
        val interactive = """<IMG src="bubble.svg,{"click":"showComments()"}">"""

        assertEquals(
            "前后",
            ExportContentImageProcessor.process(
                content = "前${interactive}后",
                plainText = false,
                filterInteractiveImages = true,
            ),
        )
    }

    @Test
    fun interactiveFilterRemovesOnlyImagesWithNonBlankClick() {
        val normal = """<img src="normal.jpg,{"style":"TEXT","width":"40"}">"""
        val interactive =
            """<img src="bubble.svg,{"style":"TEXT","click":"showComments()"}">"""
        val blankClick = """<img src="blank.svg,{"click":""}">"""

        assertEquals(
            "前${normal}${blankClick}后",
            ExportContentImageProcessor.process(
                content = "前${normal}${interactive}${blankClick}后",
                plainText = false,
                filterInteractiveImages = true,
            ),
        )
    }

    @Test
    fun malformedOptionsKeepImageToAvoidFalsePositive() {
        val malformed = """<img src="normal.jpg,{click:not-json}">"""

        assertEquals(
            malformed,
            ExportContentImageProcessor.process(
                content = malformed,
                plainText = false,
                filterInteractiveImages = true,
            ),
        )
    }
}
