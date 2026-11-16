package io.legado.app.ui.book.read

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.databinding.ViewReadMenuBinding
import io.legado.app.help.config.ReadBottomTool
import io.legado.app.help.config.ReadBottomToolbarSettings
import io.legado.app.ui.design.theme.InterfaceFontTestApplication
import io.legado.app.utils.defaultSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h891dp-xxhdpi", application = InterfaceFontTestApplication::class)
class ReadBottomToolbarTest {
    @Test
    fun defaultsKeepTheOriginalRowsAndMoreRemainsReachable() {
        assertEquals(
            ReadBottomTool.entries.chunked(4),
            ReadBottomToolbarSettings.visibleRows(emptySet()),
        )
        assertEquals(
            listOf(listOf(ReadBottomTool.MORE)),
            ReadBottomToolbarSettings.visibleRows(ReadBottomTool.entries.toSet()),
        )
    }

    @Test
    fun hiddenShortcutsExcludeScrollAndMore() {
        assertEquals(
            listOf(
                ReadBottomTool.CATALOG,
                ReadBottomTool.READ_ALOUD,
                ReadBottomTool.INTERFACE,
                ReadBottomTool.PAGE_TURNING,
                ReadBottomTool.CONTENT,
                ReadBottomTool.FLOATING,
            ),
            ReadBottomToolbarSettings.shortcuts(ReadBottomTool.entries.toSet()),
        )
    }

    @Test
    fun storesHiddenButtonsByKey() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.defaultSharedPreferences
        val key = PreferKey.readBottomToolbarHidden
        val original = preferences.getString(key, null)
        try {
            preferences.edit().remove(key).commit()
            assertEquals(emptySet<ReadBottomTool>(), ReadBottomToolbarSettings.hiddenTools(context))
            val hidden = setOf(ReadBottomTool.INTERFACE, ReadBottomTool.SCROLL)
            ReadBottomToolbarSettings.saveHiddenTools(context, hidden + ReadBottomTool.MORE)
            assertEquals("interface,scroll", preferences.getString(key, null))
            assertEquals(hidden, ReadBottomToolbarSettings.hiddenTools(context))
        } finally {
            preferences.edit().putString(key, original).commit()
        }
    }

    @Test
    fun savesTheOrderAndKeepsMoreInItsSortedPosition() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.defaultSharedPreferences
        val key = PreferKey.readBottomToolbarOrder
        val original = preferences.getString(key, null)
        try {
            preferences.edit().remove(key).commit()
            assertEquals(ReadBottomTool.entries, ReadBottomToolbarSettings.order(context))
            val customOrder = listOf(ReadBottomTool.FLOATING, ReadBottomTool.MORE) +
                ReadBottomTool.entries.filterNot { it == ReadBottomTool.FLOATING || it == ReadBottomTool.MORE }
            ReadBottomToolbarSettings.saveOrder(context, customOrder)
            val savedOrder = ReadBottomToolbarSettings.order(context)
            assertEquals(customOrder, savedOrder)

            val hidden = setOf(ReadBottomTool.MORE, ReadBottomTool.CATALOG, ReadBottomTool.SCROLL)
            assertEquals(
                listOf(
                    listOf(ReadBottomTool.FLOATING, ReadBottomTool.MORE, ReadBottomTool.READ_ALOUD, ReadBottomTool.INTERFACE),
                    listOf(ReadBottomTool.PAGE_TURNING, ReadBottomTool.CONTENT),
                ),
                ReadBottomToolbarSettings.visibleRows(hidden, savedOrder),
            )
        } finally {
            preferences.edit().putString(key, original).commit()
        }
    }

    @Test
    fun reflowsTheOriginalButtonsAndCollapsesAnEmptySecondRow() {
        val context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext<Context>(), R.style.AppTheme_Light,
        )
        val binding = ViewReadMenuBinding.inflate(LayoutInflater.from(context))
        val hidden = setOf(
            ReadBottomTool.READ_ALOUD,
            ReadBottomTool.INTERFACE,
            ReadBottomTool.SCROLL,
            ReadBottomTool.FLOATING,
        )

        binding.applyBottomToolRows(ReadBottomToolbarSettings.visibleRows(hidden))

        val firstRow = binding.llBottomActionsPrimary
        assertEquals(4, firstRow.childCount)
        assertSame(binding.llCatalog, firstRow.getChildAt(0).findViewById<View>(R.id.ll_catalog))
        assertSame(binding.llSetting, firstRow.getChildAt(1).findViewById<View>(R.id.ll_setting))
        assertSame(binding.llPageTurning, firstRow.getChildAt(2).findViewById<View>(R.id.ll_page_turning))
        assertSame(binding.llContent, firstRow.getChildAt(3).findViewById<View>(R.id.ll_content))
        assertEquals(View.GONE, binding.llBottomActionsSecondary.visibility)
        assertNull(binding.llAutoPage.parent)

        binding.applyBottomToolRows(ReadBottomToolbarSettings.visibleRows(emptySet()))
        assertEquals(View.VISIBLE, binding.llBottomActionsSecondary.visibility)
        assertSame(binding.llAutoPage, binding.llBottomActionsSecondary.findViewById<View>(R.id.ll_auto_page))
    }

    @Test
    fun secondaryItemsUseTheSameColumnsAsTheFirstRow() {
        val context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext<Context>(), R.style.AppTheme_Light,
        )
        val binding = ViewReadMenuBinding.inflate(LayoutInflater.from(context))
        val firstRow = binding.llBottomActionsPrimary
        val secondRow = binding.llBottomActionsSecondary
        val width = View.MeasureSpec.makeMeasureSpec(1003, View.MeasureSpec.EXACTLY)
        val height = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        val optionalTools = listOf(ReadBottomTool.PAGE_TURNING, ReadBottomTool.SCROLL, ReadBottomTool.CONTENT)

        for (secondRowSize in 1..4) {
            val hidden = optionalTools.take(4 - secondRowSize).toSet()
            binding.applyBottomToolRows(ReadBottomToolbarSettings.visibleRows(hidden))

            listOf(firstRow, secondRow).forEach { row ->
                row.measure(width, height)
                row.layout(0, 0, row.measuredWidth, row.measuredHeight)
            }
            assertEquals(secondRowSize, secondRow.childCount)
            for (column in 0 until secondRowSize) {
                val primary = firstRow.getChildAt(column)
                val secondary = secondRow.getChildAt(column)
                assertEquals(primary.left, secondary.left)
                assertEquals(primary.right, secondary.right)
            }
        }
    }
}
