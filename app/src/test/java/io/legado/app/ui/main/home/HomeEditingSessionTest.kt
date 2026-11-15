package io.legado.app.ui.main.home

import android.app.Application
import androidx.core.content.edit
import androidx.lifecycle.SavedStateHandle
import io.legado.app.ui.design.theme.InterfaceFontTestApplication
import io.legado.app.utils.defaultSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import splitties.init.appCtx

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = InterfaceFontTestApplication::class)
class HomeEditingSessionTest {
    private val original = listOf(
        HomeWidgetInstance("updates", "updates", "large"),
        HomeWidgetInstance("listening", "listening", "large"),
        HomeWidgetInstance("reading", "reading", "large"),
        HomeWidgetInstance("calendar", "calendar", "large"),
    )
    private val preferences get() = appCtx.defaultSharedPreferences

    @Before
    fun seedLayout() {
        preferences.edit {
            putString("homeWidgetLayout", HomeWidgetLayoutCodec.encode(original))
            putBoolean("homeBookNewsAutoRefresh", true)
        }
    }

    private fun newModel(savedState: SavedStateHandle = SavedStateHandle()) =
        HomeViewModel(appCtx as Application, savedState)

    private fun storedLayout() = HomeWidgetLayoutCodec.decode(
        requireNotNull(preferences.getString("homeWidgetLayout", null)),
    )

    private fun changeEverything(model: HomeViewModel) {
        model.selectEditingWidget("updates")
        model.moveWidget("updates", "reading")
        model.removeWidget("listening")
        assertTrue(model.addWidget("listening", "white-large"))
        assertTrue(model.saveSuite("white"))
        assertTrue(model.saveVariant("updates", "white-small", false))
        assertTrue(model.saveVariant("reading", "small", null))
        assertTrue(model.state.value.pendingEdit)
    }

    @Test
    fun cancellingRestoresLayoutAndSettingsAfterAllKindsOfEdits() {
        val saved = SavedStateHandle()
        val model = newModel(saved)
        changeEverything(model)
        assertEquals(original, storedLayout())
        assertTrue(preferences.getBoolean("homeBookNewsAutoRefresh", false))
        assertTrue(model.autoRefreshUpdates.value)
        assertEquals(false, model.state.value.draftAutoRefreshUpdates)
        assertTrue(model.cancelEditing())
        assertEquals(HomeUiState(original), model.state.value)
        assertEquals(original, storedLayout())
        assertTrue(preferences.getBoolean("homeBookNewsAutoRefresh", false))
        assertTrue(saved.keys().isEmpty())
    }

    @Test
    fun donePersistsTheWholeDraftAndSettingsAndTheySurviveReopening() {
        val saved = SavedStateHandle()
        val model = newModel(saved)
        changeEverything(model)
        val expected = model.state.value.displayedWidgets
        model.saveEditing()
        assertEquals(expected, storedLayout())
        assertFalse(preferences.getBoolean("homeBookNewsAutoRefresh", true))
        assertFalse(model.autoRefreshUpdates.value)
        assertEquals(HomeUiState(expected), model.state.value)
        assertTrue(saved.keys().isEmpty())
        val reopened = newModel()
        assertEquals(expected, reopened.state.value.widgets)
        assertFalse(reopened.autoRefreshUpdates.value)
        assertFalse(reopened.cancelEditing())
        model.saveEditing()
        assertEquals(expected, storedLayout())
    }

    @Test
    fun recreatedDraftKeepsTheOriginalSnapshotAndUncommittedSettings() {
        listOf(false, true).forEach { save ->
            seedLayout()
            val saved = SavedStateHandle()
            val initial = newModel(saved)
            changeEverything(initial)
            val restored = newModel(SavedStateHandle(saved.keys().associateWith { saved.get<Any>(it) }))
            assertEquals(initial.state.value, restored.state.value)
            assertEquals(original, storedLayout())
            assertTrue(restored.autoRefreshUpdates.value)
            if (save) {
                restored.saveEditing()
                assertEquals(initial.state.value.displayedWidgets, storedLayout())
                assertFalse(preferences.getBoolean("homeBookNewsAutoRefresh", true))
            } else {
                restored.cancelEditing()
                assertEquals(HomeUiState(original), restored.state.value)
                assertTrue(preferences.getBoolean("homeBookNewsAutoRefresh", false))
            }
        }
    }

    @Test
    fun lateDragCallbacksCannotChangeTheLayoutAfterCancelOrDone() {
        val model = newModel()
        model.beginEditing()
        model.moveWidget("updates", "reading")
        model.cancelEditing()
        model.moveWidget("updates", "calendar")
        assertEquals(original, model.state.value.widgets)
        assertEquals(original, storedLayout())

        model.beginEditing()
        model.moveWidget("updates", "reading")
        val expected = model.state.value.displayedWidgets
        model.saveEditing()
        model.moveWidget("updates", "calendar")
        assertEquals(expected, model.state.value.widgets)
        assertEquals(expected, storedLayout())
    }

    @Test
    fun emptyLayoutCanBeSavedAndAddingAgainStillSupportsCancel() {
        val model = newModel()
        model.beginEditing()
        original.forEach { model.removeWidget(it.id) }
        assertTrue(model.state.value.displayedWidgets.isEmpty())
        assertFalse(model.saveSuite("white"))
        model.saveEditing()
        assertTrue(storedLayout().isEmpty())
        model.beginEditing()
        assertTrue(model.addWidget("reading", "large"))
        model.cancelEditing()
        assertTrue(model.state.value.widgets.isEmpty())
        model.beginEditing()
        assertTrue(model.addWidget("reading", "large"))
        model.saveEditing()
        assertEquals(listOf("reading"), storedLayout().map { it.typeId })
    }

    @Test
    fun drawerConfirmWithoutChangesAndRejectedStyleNeverCommitEarlierEdits() {
        val model = newModel()
        model.beginEditing()
        model.removeWidget("listening")
        assertTrue(model.saveVariant("updates", "large", true))
        val pending = model.state.value
        assertFalse(model.saveVariant("missing", "large", false))
        assertFalse(model.saveSuite("missing"))
        assertEquals(pending, model.state.value)
        assertEquals(original, storedLayout())
        model.cancelEditing()
        assertNull(model.state.value.draftAutoRefreshUpdates)
        assertEquals(original, model.state.value.widgets)
    }
}
