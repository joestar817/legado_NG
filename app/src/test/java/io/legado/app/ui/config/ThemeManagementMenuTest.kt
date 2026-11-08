package io.legado.app.ui.config

import io.legado.app.R
import io.legado.app.help.config.NgEffectThemeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeManagementMenuTest {

    @Test
    fun regularThemeOffersApplyBeforeManagementUntilSelected() {
        val inactive = appThemeMenuItems(false, false, false, null)
        assertEquals(
            listOf(R.string.ng_theme_menu_apply, R.string.edit, R.string.export, R.string.delete),
            inactive.map { it.titleRes },
        )
        assertTrue(inactive.first().enabled)
        assertEquals(R.drawable.ic_export, inactive.single { it.titleRes == R.string.export }.iconRes)
        assertTrue(inactive.last().danger)
        val active = appThemeMenuItems(false, true, false, null)
        assertEquals(inactive.drop(1), active)
    }

    @Test
    fun readyEffectThemeUsesTheSameMenuAsARegularTheme() {
        val inactive = appThemeMenuItems(true, false, true, NgEffectThemeStatus.READY)
        assertEquals(appThemeMenuItems(false, false, false, null), inactive)
        val active = appThemeMenuItems(true, true, true, NgEffectThemeStatus.READY)
        assertEquals(inactive.drop(1), active)
    }

    @Test
    fun effectThemeCanSwitchFromStaticToLiveWhenReady() {
        val activeStatic = appThemeMenuItems(true, true, false, NgEffectThemeStatus.READY)
        assertEquals(R.string.ng_theme_menu_apply, activeStatic.first().titleRes)
        assertTrue(activeStatic.first().enabled)
    }

    @Test
    fun effectThemeCanUseStaticAppearanceAfterRuntimeFailure() {
        for (status in listOf(NgEffectThemeStatus.UNSUPPORTED, NgEffectThemeStatus.LOAD_ERROR)) {
            val activeEffect = appThemeMenuItems(true, true, true, status)
            assertEquals(R.string.effect_theme_apply_static, activeEffect.first().titleRes)
            assertTrue(activeEffect.first().enabled)
            val activeStatic = appThemeMenuItems(true, true, false, status)
            assertEquals(R.string.edit, activeStatic.first().titleRes)
        }
    }

    @Test
    fun effectThemeCannotApplyBeforeCheckOrWithDamagedPackage() {
        val checking = appThemeMenuItems(true, false, false, null)
        assertEquals(R.string.effect_theme_checking, checking.first().titleRes)
        assertFalse(checking.first().enabled)
        val invalid = appThemeMenuItems(true, false, false, NgEffectThemeStatus.CORRUPT)
        assertEquals(R.string.ng_theme_menu_apply, invalid.first().titleRes)
        assertFalse(invalid.first().enabled)
        assertTrue(invalid.single { it.titleRes == R.string.edit }.enabled)
    }
}
