package io.legado.app.ui.source.edit

import io.legado.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceEditorMenuTest {
    @Test
    fun bothEditorsKeepTheirShortcutsAndConditionalActions() {
        for (search in listOf(false, true)) for (login in listOf(false, true)) {
            val menu = sourceEditorMenuItems(hasSearch = search, hasLogin = login)
            val top = menu.map { it.itemId }
            assertEquals(search, R.id.menu_search in top)
            assertEquals(login, R.id.menu_login in top)
            assertTrue(top.containsAll(listOf(R.id.menu_copy_source, R.id.menu_paste_source,
                R.id.menu_set_source_variable, R.id.menu_help)))
            assertTrue(menu.single { it.itemId == R.id.source_editor_settings }.children.isEmpty())
            assertEquals(listOf(R.id.menu_qr_code_camera, R.id.menu_share_qr, R.id.menu_share_str),
                menu.single { it.itemId == R.id.source_editor_transfer }.children.map { it.itemId })
            assertEquals(listOf(R.id.menu_clear_cookie, R.id.menu_log, R.id.menu_network_log),
                menu.single { it.itemId == R.id.source_editor_diagnostics }.children.map { it.itemId })
            val ids = top + menu.flatMap { item -> item.children.map { it.itemId } }
            assertEquals(ids.size, ids.distinct().size)
            assertFalse(R.id.menu_auto_complete in ids)
        }
    }
}
