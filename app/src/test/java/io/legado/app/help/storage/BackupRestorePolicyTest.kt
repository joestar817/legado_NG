package io.legado.app.help.storage

import io.legado.app.constant.PreferKey
import io.legado.app.help.config.ReadThemeMode
import io.legado.app.help.config.resolveRestoredReadThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRestorePolicyTest {

    @Test
    fun nativeResourcePackageRestoresAppearanceWithoutChangingLegacyPolicy() {
        listOf(PreferKey.themeMode, PreferKey.ngColorLightPrimary, "ngManagedThemes.v1").forEach {
            assertTrue(BackupRestorePolicy.shouldRestorePreference(it, false, nativePackage = true))
            assertFalse(BackupRestorePolicy.shouldRestorePreference(it, false))
            assertFalse(BackupRestorePolicy.shouldRestorePreference(it, true, nativePackage = true))
        }
    }

    @Test
    fun keepsThemeAndBarAppearanceOutsideWholeBackupRestore() {
        val appearanceKeys = listOf(
            PreferKey.themeMode,
            PreferKey.ngThemePresentationMode,
            PreferKey.ngStandardThemeMode,
            PreferKey.ngInternalThemeMode,
            PreferKey.ngSoftGradientColor,
            PreferKey.ngSoftGradientColorMode,
            PreferKey.ngSoftGradientCustomColor,
            PreferKey.ngSoftGradientLightField,
            PreferKey.readNightTheme,
            PreferKey.readThemeMode,
            PreferKey.cPrimary,
            PreferKey.ngColorLightPrimary,
            PreferKey.useFloatingBottomBar,
            PreferKey.bookshelfTopBarStyle,
            PreferKey.bookshelfFloatingDockTransparency,
            PreferKey.bookshelfFloatingDockSearchPosition,
            "ngManagedThemes.v1",
            "ngActiveManagedThemeId.v1"
        )

        appearanceKeys.forEach { key ->
            assertFalse(BackupRestorePolicy.shouldRestorePreference(key, isMd3Backup = false))
        }
        assertTrue(
            BackupRestorePolicy.shouldRestorePreference(
                PreferKey.autoReadSpeed,
                isMd3Backup = false
            )
        )
    }

    @Test
    fun restoresGlobalReadingFloatingColorPreferencesFromSameVersionBackup() {
        listOf(
            PreferKey.readFloatingFollowAppGlobally,
            PreferKey.readFloatingGlobalColorStyle,
        ).forEach { key ->
            assertTrue(BackupRestorePolicy.shouldRestorePreference(key, isMd3Backup = false))
        }
    }

    @Test
    fun skipsMd3ReadStylesAndTheirDependentPreferences() {
        assertFalse(BackupRestorePolicy.shouldRestoreReadConfigs(isMd3Backup = true))
        assertTrue(BackupRestorePolicy.shouldRestoreReadConfigs(isMd3Backup = false))
        assertFalse(BackupRestorePolicy.shouldRestoreHighlightRules(isMd3Backup = true))
        assertTrue(BackupRestorePolicy.shouldRestoreHighlightRules(isMd3Backup = false))

        listOf(
            PreferKey.readStyleSelect,
            PreferKey.comicStyleSelect,
            PreferKey.shareLayout,
            PreferKey.showBrightnessView,
            PreferKey.brightnessVwPos
        ).forEach { key ->
            assertFalse(BackupRestorePolicy.shouldRestorePreference(key, isMd3Backup = true))
        }
        assertTrue(
            BackupRestorePolicy.shouldRestorePreference(
                PreferKey.autoReadSpeed,
                isMd3Backup = true
            )
        )
    }

    // region Decision A：恢复期回填 readThemeMode

    @Test
    fun `missing readThemeMode restores to follow system`() {
        assertEquals(
            ReadThemeMode.FOLLOW_SYSTEM.storageValue,
            resolveRestoredReadThemeMode(null),
        )
        assertEquals(
            ReadThemeMode.FOLLOW_SYSTEM.storageValue,
            resolveRestoredReadThemeMode(""),
        )
    }

    @Test
    fun `present readThemeMode is preserved`() {
        assertEquals("day", resolveRestoredReadThemeMode("day"))
        assertEquals("night", resolveRestoredReadThemeMode("night"))
        assertEquals("follow", resolveRestoredReadThemeMode("follow"))
    }

    @Test
    fun `readThemeMode stays outside backup payload`() {
        // Decision A：缺失→FOLLOW_SYSTEM 的前提是负载里没有这个字段；schema/格式不变。
        assertFalse(BackupRestorePolicy.shouldRestorePreference(PreferKey.readThemeMode, isMd3Backup = false))
        assertFalse(BackupRestorePolicy.shouldRestorePreference(PreferKey.readThemeMode, isMd3Backup = true))
    }

    // endregion
}
