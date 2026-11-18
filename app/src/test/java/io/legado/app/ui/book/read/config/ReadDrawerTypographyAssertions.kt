package io.legado.app.ui.book.read.config

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

internal fun ComposeContentTestRule.assertDrawerTextStyle(
    label: String,
    sizeSp: Int,
    weight: FontWeight = FontWeight.Normal,
) {
    val layouts = mutableListOf<TextLayoutResult>()
    onNodeWithText(label, useUnmergedTree = true)
        .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(layouts)) }
    val style = layouts.single().layoutInput.style
    assertEquals(label, sizeSp.sp, style.fontSize)
    assertEquals(label, weight, style.fontWeight ?: FontWeight.Normal)
}
