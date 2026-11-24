package io.legado.app.ui.main.chatentry

import android.app.Application
import android.graphics.Rect
import android.view.View
import androidx.compose.ui.platform.ComposeView
import io.legado.app.ui.config.AiChatTask
import io.legado.app.ui.config.AiChatTaskState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [35])
class AiChatPetTaskWindowTest {
    private val context = RuntimeEnvironment.getApplication()
    private val toolbar = ComposeView(context).apply { visibility = View.GONE }
    private val messages = ComposeView(context).apply { visibility = View.GONE }
    private val menuChanges = mutableListOf<Boolean>()
    private val viewport = Rect(0, 40, 500, 1000)
    private val parkedBounds = Rect(330, 650, 450, 770)
    private val window = AiChatPetTaskWindow(toolbar, messages, View(context), { menuChanges.add(it) }, {}, {})

    @Test
    fun automaticPanelRequiresRunningWorkAndAParkedPet() {
        window.moveTo(parkedBounds, viewport, isParked = false)
        val running = task("scan", AiChatTaskState.THINKING)
        val waiting = task("card", AiChatTaskState.WAITING)
        val completed = task("chat", AiChatTaskState.COMPLETED)
        val failed = task("failed", AiChatTaskState.FAILED)
        window.showTasks(listOf(running, waiting, completed, failed))
        assertEquals(View.GONE, messages.visibility)

        window.moveTo(parkedBounds, viewport, isParked = true)
        assertEquals(View.VISIBLE, messages.visibility)
        assertEquals(View.GONE, toolbar.visibility)
        assertTrue(messages.y < parkedBounds.top)
        assertTrue(menuChanges.isEmpty())

        window.showTasks(listOf(running.copy(state = AiChatTaskState.BOOK_SOURCES), waiting, completed))
        val startX = messages.x
        val startY = messages.y
        window.moveTo(Rect(parkedBounds).apply { offset(3, -4) }, viewport, isParked = true)
        assertEquals(startX, messages.x, 0f)
        assertEquals(startY, messages.y, 0f)

        window.showTasks(listOf(waiting, completed, failed, running.copy(state = AiChatTaskState.CONFIRM_BOOK)))
        assertEquals(View.GONE, messages.visibility)
        window.showTasks(listOf(running, waiting))
        window.moveTo(Rect(), viewport, isParked = false)
        assertEquals(View.GONE, messages.visibility)
    }

    @Test
    fun openingTheMenuHidesAutomaticMessagesUntilItCloses() {
        toolbar.layout(0, 0, 104, 48)
        window.moveTo(parkedBounds, viewport, isParked = true)
        window.showTasks(listOf(task("scan", AiChatTaskState.GENERATING)))
        assertEquals(View.VISIBLE, messages.visibility)

        window.toggle()
        assertTrue(window.isOpen)
        assertEquals(View.VISIBLE, toolbar.visibility)
        assertEquals(View.GONE, messages.visibility)

        assertTrue(window.dismiss())
        assertFalse(window.isOpen)
        assertEquals(View.GONE, toolbar.visibility)
        assertEquals(View.VISIBLE, messages.visibility)

        window.toggle()
        window.moveTo(parkedBounds, viewport, isParked = false)
        assertTrue(window.dismiss())
        assertEquals(View.GONE, messages.visibility)
        assertEquals(listOf(true, false, true, false), menuChanges)
    }

    private fun task(id: String, state: AiChatTaskState) = AiChatTask(id, id, state)
}
