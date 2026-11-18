package io.legado.app.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.net.URLEncoder
import java.util.Base64

class ParagraphCommentIconParserTest {
    private fun source(svg: String, options: Map<String, Any>): String =
        "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(svg.toByteArray()) + "," + Gson().toJson(options)

    @Test
    fun legacyActionReadsTheCountAndSkipsSvgHintText() {
        val src = source("<svg><text>刷新图片查看段评</text><text>99+</text></svg>",
            mapOf("js" to "showCmt('book','chapter','paragraph')"))
        assertEquals("99+", ParagraphCommentIconParser.displayText(src))
    }

    @Test
    fun clickActionAndNumericMetadataAreSupported() {
        val src = source("<svg><text>4</text></svg>", mapOf("click" to "showCmt('b','c','p')", "count" to 12))
        assertEquals("12", ParagraphCommentIconParser.displayText(src))
    }

    @Test
    fun fanqieReviewActionReadsTheActualInlineBubble() {
        // Same SVG and action shape as the reading-page failure; source IDs are synthetic.
        val svg = """<svg width="160" height="120" xmlns="http://www.w3.org/2000/svg"><path d="M55 10h65q30 0 30 30v40q0 30-30 30H55Q25 110 25 80v-5L3 60l22-15v-5q0-30 30-30Z" fill="none" stroke="#777" stroke-width="5" stroke-linejoin="round"/><text x="87" y="77" font-family="Arial,sans-serif" text-anchor="middle" dominant-baseline="middle" font-size="50" font-weight="bold" fill="#777">61</text></svg>"""
        for (count in listOf("61", "99+")) {
            val src = source(svg.replace(">61<", ">$count<"),
                mapOf("click" to "fqShowReviews('book','chapter',10,'')", "style" to "TEXT"))
            assertEquals(count, ParagraphCommentIconParser.displayText(src))
        }
    }

    @Test
    fun pluralReviewAndCommentActionsAreSupported() {
        for (action in listOf("showReviews()", "showComments()")) {
            assertEquals("12", ParagraphCommentIconParser.displayText(
                source("<svg><text>12</text></svg>", mapOf("click" to action))))
        }
    }

    @Test
    fun customClickUsesExplicitCommentType() {
        assertEquals("0", ParagraphCommentIconParser.displayText(
            "https://example.test/icon.png,{\"type\":\"paragraph\",\"num\":\"0\",\"click\":\"openComment()\"}"))
    }

    @Test
    fun inlineSvgUsesTheDispatchedEventRegardlessOfScriptOrDrawing() {
        val svg = "<svg><text>14</text></svg>"
        for (key in listOf("click", "js")) {
            for (script in listOf("qdShowReviews('b','c','p')", "qmShowReviews('b','c','p')",
                "actions[chapter](42)", "const p = 42; open(p)")) {
                assertEquals("14", ParagraphCommentIconParser.displayText(
                    source(svg, mapOf("style" to "TEXT", key to script))))
            }
        }
    }

    @Test
    fun ordinaryImagesWithoutAnInlineCountArePreserved() {
        val options = mapOf("style" to "TEXT", "click" to "open(7)")
        val svg = "<svg><text>12</text></svg>"
        for (src in listOf(
            source(svg, options - "click"),
            source(svg, options - "style"),
            source(svg, options + ("style" to "FULL")),
            source(svg, mapOf("style" to "TEXT", "pclick" to "open(7)")),
            source("<svg><text>插图</text></svg>", options),
            source("<svg><text>12</text><text>28</text></svg>", options),
            "data:image/png;base64,eA==," + Gson().toJson(options),
        )) {
            assertNull(src, ParagraphCommentIconParser.displayText(src))
        }
    }

    @Test
    fun privateEventPrefersTheVisibleCountAndRejectsContradictoryLabels() {
        val options = mapOf("style" to "TEXT", "click" to "handlers[p](book)", "count" to 154)
        fun parse(body: String) = ParagraphCommentIconParser.displayText(source("<svg>$body</svg>", options))
        assertEquals("99+", parse("<text>99+</text>"))
        assertEquals("99+", parse("<text>刷新图片查看段评</text><text>99+</text>"))
        assertEquals("154", parse("<path d='M1 1 L2 2'/>"))
        assertNull(parse("<text>图号</text><text>14</text>"))
        assertNull(parse("<text>10</text><text>20</text>"))
        assertNull(ParagraphCommentIconParser.displayText("data:image/svg+xml;base64,eA==," + Gson().toJson(options)))
    }

    @Test
    fun hiddenGroupsAndNestedTextDoNotSupplyTheVisibleCount() {
        val options = mapOf("style" to "TEXT", "click" to "handlers[p](book)")
        for (attributes in listOf("style='display: none !important'", "style='visibility:hidden'",
            "opacity='0.0'", "style='opacity: 0%'")) {
            val onlyHidden = "<svg><g $attributes><text>14</text></g></svg>"
            assertNull(ParagraphCommentIconParser.displayText(source(onlyHidden, options)))
            val nested = "<svg><text>14<tspan $attributes>28</tspan></text></svg>"
            assertEquals("14", ParagraphCommentIconParser.displayText(source(nested, options)))
        }
        val stylesheet = "<svg><style>.key {display:none}</style><text class='key'>14</text></svg>"
        assertNull(ParagraphCommentIconParser.displayText(source(stylesheet, options)))
        assertEquals("14", ParagraphCommentIconParser.displayText(
            source(stylesheet.replace("display:none", "fill:red"), options)))
    }

    @Test
    fun generatedPrivateIconsUseTheResolvedImageEvenWhenGeneratorTextContainsNumbers() {
        val options = mapOf("style" to "TEXT", "click" to "handlers[p](book)",
            "js" to "log('createSvg(77, grey)'); build()")
        val src = "data:image/svg+xml;base64,," + Gson().toJson(options)
        assertNull(ParagraphCommentIconParser.displayText(src))
        val file = File.createTempFile("paragraph-svg", ".svg")
        try {
            file.writeText("<svg><text>无</text></svg>")
            assertEquals("无", ParagraphCommentIconParser.displayText(src) { file })
            file.writeText("<svg><text>10</text><text>20</text></svg>")
            assertNull(ParagraphCommentIconParser.displayText(src) { file })
        } finally {
            file.delete()
        }
    }

    @Test
    fun missingCountIsPreserved() {
        val src = source("<svg><path d='M0 0'/></svg>", mapOf("click" to "showCmt()"))
        assertNull(ParagraphCommentIconParser.displayText(src))
    }

    @Test
    fun legacyTapScriptIsNotEvaluatedToIdentifyAnUnlabelledInlineSvg() {
        val src = source("<svg/>", mapOf("style" to "TEXT", "js" to "onTap(createSvg(14, 'red'))"))
        assertNull(ParagraphCommentIconParser.displayText(src) { error("tap handler used during layout") })
    }

    @Test
    fun imageWithoutAnActionIsPreserved() {
        val src = source("<svg><text>12</text></svg>", mapOf("type" to "paragraph", "num" to 12))
        assertNull(ParagraphCommentIconParser.displayText(src))
    }

    @Test
    fun urlEncodedSvgKeepsThePlusInDisplayText() {
        val svg = URLEncoder.encode("<svg><text>99+</text></svg>", "UTF-8").replace("+", "%20")
        assertEquals("99+", ParagraphCommentIconParser.displayText(
            "data:image/svg+xml,$svg,{\"click\":\"showComment()\"}"))
    }

    @Test
    fun malformedOptionsArePreserved() {
        assertNull(ParagraphCommentIconParser.displayText("data:image/svg+xml;base64,eA==,{broken"))
    }

    @Test
    fun shushanCommentProvidersSupportBothGenerationsOfClickActions() {
        for (action in listOf("showCmt", "showJJCmt", "showQmCmt", "showQQCmt")) {
            for (key in listOf("click", "js")) {
                val src = source("<svg><text>99+</text></svg>", mapOf(
                    key to "$action('book','chapter','paragraph','123456')", "style" to "TEXT",
                    "headers" to mapOf("Accept" to "image/svg+xml"),
                ))
                assertEquals("$key $action", "99+", ParagraphCommentIconParser.displayText(src))
            }
        }
    }

    @Test
    fun sourceSvgKeepsItsDisplayedNumberAndSkipsUnusedDefinitions() {
        val src = source("<svg><defs><text>1</text></defs><text>99+</text></svg>",
            mapOf("click" to "showCmt()", "js" to "createSvg(154, 'grey')"))
        assertEquals("99+", ParagraphCommentIconParser.displayText(src))
        val raw = """data:image/svg+xml,<svg width='100%'><text>1.2万+</text></svg>,{"click":"showReviews()"}"""
        assertEquals("1.2万+", ParagraphCommentIconParser.displayText(raw))
    }

    @Test
    fun anOrdinaryClickWinsOverImageGenerationAndQuotedFunctionNames() {
        for (click in listOf("showPicture()", "showPicture('showCmt()')", "showPicture() /* showComments() */")) {
            val src = source("<svg><text>12</text></svg>",
                mapOf("click" to click, "js" to "showCmt()"))
            assertNull(click, ParagraphCommentIconParser.displayText(src))
        }
    }

    @Test
    fun onlyActionsTheReaderCanActuallyDispatchCountAsEvidence() {
        for (options in listOf(mapOf("CLICK" to "showCmt()"), mapOf("click" to false),
            mapOf("click" to "", "js" to "showCmt()"))) {
            val src = source("<svg><text>12</text></svg>", options + ("type" to "paragraph"))
            assertNull(options.toString(), ParagraphCommentIconParser.displayText(src))
        }
    }
}
