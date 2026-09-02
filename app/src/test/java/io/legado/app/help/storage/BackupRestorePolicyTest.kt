package io.legado.app.help.storage

import io.legado.app.constant.PreferKey
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRestorePolicyTest {

    @Test
    fun keepsThemeAndBarAppearanceOutsideWholeBackupRestore() {
        val appearanceKeys = listOf(
            PreferKey.themeMode,
            PreferKey.ngThemePresentationMode,
            PreferKey.ngStandardThemeMode,
            PreferKey.ngInternalThemeMode,
            PreferKey.ngSoftGradientColor,
            PreferKey.ngSoftGradientLightField,
            PreferKey.readNightTheme,
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
    fun skipsMd3ReadStylesAndTheirDependentPreferences() {
        assertFalse(BackupRestorePolicy.shouldRestoreReadConfigs(isMd3Backup = true))
        assertTrue(BackupRestorePolicy.shouldRestoreReadConfigs(isMd3Backup = false))

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
}
