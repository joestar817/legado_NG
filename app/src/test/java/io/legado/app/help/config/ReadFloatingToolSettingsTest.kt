package io.legado.app.help.config

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.constant.PreferKey
import io.legado.app.help.storage.BackupModule
import io.legado.app.help.storage.BackupModules
import io.legado.app.ui.design.theme.InterfaceFontTestApplication
import io.legado.app.utils.defaultSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = InterfaceFontTestApplication::class)
class ReadFloatingToolSettingsTest {
    @Test
    fun savedOrderAndVisibilityAreIndependentAndIncludedInReaderBackup() {
        val preferences = ApplicationProvider.getApplicationContext<Context>().defaultSharedPreferences
        val orderKey = PreferKey.readFloatingToolOrder
        val disabledKey = PreferKey.readFloatingToolDisabled
        val optInKey = PreferKey.readFloatingToolOptIn
        val originalOrder = preferences.getString(orderKey, null)
        val originalDisabled = preferences.getString(disabledKey, null)
        val originalOptIn = preferences.getString(optInKey, null)
        try {
            preferences.edit().remove(orderKey).remove(disabledKey).remove(optInKey).commit()
            assertEquals(ReadFloatingTool.entries.toList(), ReadFloatingToolSettings.order())
            val optional = ReadFloatingTool.entries.filterNot { it.enabledByDefault }
            val optionalKeys = optional.map { it.key }.toSet()
            assertEquals(ReadFloatingTool.entries.filter { it.enabledByDefault }, ReadFloatingToolSettings.visibleTools())
            assertEquals(optionalKeys, ReadFloatingToolSettings.disabledKeys())
            // Existing configurations with only the original disabled keys must not enable new tools.
            preferences.edit().putString(disabledKey, ReadFloatingTool.SEARCH.key).commit()
            assertEquals(optionalKeys + ReadFloatingTool.SEARCH.key, ReadFloatingToolSettings.disabledKeys())

            val reordered = listOf(ReadFloatingTool.AI, ReadFloatingTool.THEME, ReadFloatingTool.REPLACE,
                ReadFloatingTool.SEARCH, ReadFloatingTool.BRIGHTNESS)
            ReadFloatingToolSettings.saveOrder(reordered)
            ReadFloatingToolSettings.setDisabledKeys(optionalKeys + setOf(ReadFloatingTool.THEME.key, ReadFloatingTool.SEARCH.key))
            assertEquals(reordered + optional, ReadFloatingToolSettings.order())
            assertEquals(listOf(ReadFloatingTool.AI, ReadFloatingTool.REPLACE, ReadFloatingTool.BRIGHTNESS),
                ReadFloatingToolSettings.visibleTools())

            ReadFloatingToolSettings.setDisabledKeys(ReadFloatingToolSettings.disabledKeys() - ReadFloatingTool.READ_ALOUD.key)
            assertEquals(listOf(ReadFloatingTool.AI, ReadFloatingTool.REPLACE, ReadFloatingTool.BRIGHTNESS, ReadFloatingTool.READ_ALOUD),
                ReadFloatingToolSettings.visibleTools())
            assertTrue(ReadFloatingTool.PAGE_ANIM.key in ReadFloatingToolSettings.disabledKeys())
            ReadFloatingToolSettings.setDisabledKeys(emptySet())
            assertEquals(reordered + optional, ReadFloatingToolSettings.visibleTools())
            listOf(orderKey, disabledKey, optInKey).forEach { key ->
                assertEquals(BackupModule.READER, BackupModules.preferenceModule(key))
                assertTrue(BackupModules.includesPreference(key, setOf(BackupModule.READER.id)))
            }
        } finally {
            val editor = preferences.edit()
            if (originalOrder == null) editor.remove(orderKey) else editor.putString(orderKey, originalOrder)
            if (originalDisabled == null) editor.remove(disabledKey) else editor.putString(disabledKey, originalDisabled)
            if (originalOptIn == null) editor.remove(optInKey) else editor.putString(optInKey, originalOptIn)
            editor.commit()
        }
    }
}
