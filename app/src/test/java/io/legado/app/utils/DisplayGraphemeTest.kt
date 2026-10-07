package io.legado.app.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayGraphemeTest {
    @Test fun keepsFlagsFromReportedGroupNames() {
        listOf("🇯🇵", "🇰🇷", "🇺🇸").forEach { flag ->
            assertEquals(flag, "${flag}漫画".firstDisplayGrapheme())
        }
        assertEquals("🇯🇵", "🇯🇵🇺🇸漫画".firstDisplayGrapheme())
    }

    @Test fun keepsEmojiFromReportedSourceNames() {
        listOf("💬", "🏆", "🥇", "🏫", "🎨").forEach { emoji ->
            assertEquals(emoji, "${emoji}漫画💞".firstDisplayGrapheme())
        }
    }

    @Test fun keepsJoinedModifiedAndVariationEmoji() {
        listOf("👩‍💻", "👨‍👩‍👧‍👦", "👍🏽", "🏳️‍🌈", "❤️", "1️⃣").forEach { emoji ->
            assertEquals(emoji, "${emoji}分组".firstDisplayGrapheme())
        }
    }

    @Test fun keepsTagFlagAndCombiningCharacters() {
        val flag = "\uD83C\uDFF4\uDB40\uDC67\uDB40\uDC62\uDB40\uDC65\uDB40\uDC6E\uDB40\uDC67\uDB40\uDC7F"
        assertEquals(flag, "${flag}组".firstDisplayGrapheme())
        assertEquals("e\u0301", "e\u0301clair".firstDisplayGrapheme())
        assertEquals("𠮷", "𠮷野家".firstDisplayGrapheme())
    }

    @Test fun keepsExistingPlainTextAndEmptySemantics() {
        assertEquals("腾", "腾讯".firstDisplayGrapheme())
        assertEquals("a", "abc".firstDisplayGrapheme())
        assertEquals(" ", " 空格".firstDisplayGrapheme())
        assertEquals("", "".firstDisplayGrapheme())
        assertEquals("源", "  ".trim().firstDisplayGrapheme().ifEmpty { "源" })
        assertEquals("A", "  abc".trim().firstDisplayGrapheme().uppercase())
    }
}
