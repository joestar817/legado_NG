package io.legado.app.ui.book.character

import io.legado.app.ui.book.character.BookCharacterSilhouetteStyle.ANCIENT
import io.legado.app.ui.book.character.BookCharacterSilhouetteStyle.AUTO
import io.legado.app.ui.book.character.BookCharacterSilhouetteStyle.FANTASY
import io.legado.app.ui.book.character.BookCharacterSilhouetteStyle.GENERIC
import io.legado.app.ui.book.character.BookCharacterSilhouetteStyle.MODERN
import org.junit.Assert.assertEquals
import org.junit.Test

class BookCharacterSilhouetteStyleTest {

    @Test
    fun lightNovelCanUseFantasyCluesFromTitleAndIntro() {
        assertEquals(
            FANTASY,
            detectBookCharacterSilhouetteStyle(
                title = "丹药武魂？金丹也是丹！",
                intro = "叶天穿越斗罗。觉醒丹药武魂，成为先天满魂力辅助系魂师。",
                sourceCategories = "QQ阅读 · 轻小说 · 8.2",
            ),
        )
    }

    @Test
    fun uncategorizedBookCanUseConsistentClues() {
        assertEquals(
            ANCIENT,
            detectBookCharacterSilhouetteStyle("赶考路上", "他去县衙报名，准备参加科举。", null),
        )
        assertEquals(
            MODERN,
            detectBookCharacterSilhouetteStyle("第一天上班", "她下了地铁，走进写字楼。", null),
        )
    }

    @Test
    fun missingOrBroadInformationUsesGeneric() {
        assertEquals(GENERIC, detectBookCharacterSilhouetteStyle("未命名", null, null))
        assertEquals(
            GENERIC,
            detectBookCharacterSilhouetteStyle("一次穿越", "穿越后的一段故事。", "轻小说、同人、连载"),
        )
        assertEquals(GENERIC, detectBookCharacterSilhouetteStyle("魂师", "一位魂师走来。", "同人"))
    }

    @Test
    fun explicitCategoryWinsOverDescription() {
        assertEquals(
            ANCIENT,
            detectBookCharacterSilhouetteStyle("武林的外来客", "他从地铁走进写字楼。", "武侠,连载"),
        )
        assertEquals(
            FANTASY,
            detectBookCharacterSilhouetteStyle("山中", null, "FANTASY / 东方玄幻"),
        )
    }

    @Test
    fun userCategoryWinsOverSourceCategory() {
        assertEquals(
            MODERN,
            detectBookCharacterSilhouetteStyle("城市一角", null, "古代言情", "现言"),
        )
        assertEquals(
            ANCIENT,
            detectBookCharacterSilhouetteStyle("山中", null, "武侠", "收藏"),
        )
    }

    @Test
    fun ambiguousCategoriesOrDescriptionUseGeneric() {
        assertEquals(GENERIC, detectBookCharacterSilhouetteStyle("混合题材", null, "都市，玄幻"))
        assertEquals(
            GENERIC,
            detectBookCharacterSilhouetteStyle("都市修真", "他下了地铁走进写字楼，觉醒武魂成为魂师。", ""),
        )
    }

    @Test
    fun explicitGenericAndFantasySelectionsOverrideAutomaticStyle() {
        assertEquals(GENERIC, GENERIC.resolve(FANTASY))
        assertEquals(FANTASY, FANTASY.resolve(MODERN))
        assertEquals(ANCIENT, AUTO.resolve(ANCIENT))
    }

    @Test
    fun preferenceKeysRoundTripAndMissingKeyMeansAutomatic() {
        BookCharacterSilhouetteStyle.entries.forEach { style ->
            assertEquals(style, BookCharacterSilhouetteStyle.fromKey(style.key))
        }
        assertEquals(AUTO, BookCharacterSilhouetteStyle.fromKey(null))
        assertEquals(AUTO, BookCharacterSilhouetteStyle.fromKey("future"))
    }
}
