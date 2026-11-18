package io.legado.app.ui.code

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CodeEditSessionStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun largeTextRoundTripsWithoutIntent() {
        val store = CodeEditSessionStore(temporaryFolder.newFolder("sessions"))
        val text = "const chapter = '正文';\n".repeat(40_000)

        val sessionId = store.create(text)

        assertEquals(text, store.read(sessionId))
    }

    @Test
    fun updateAndDeleteKeepOneSessionIdentity() {
        val store = CodeEditSessionStore(temporaryFolder.newFolder("sessions"))
        val sessionId = store.create("before")

        store.write(sessionId, "after")
        assertEquals("after", store.read(sessionId))

        store.delete(sessionId)
        assertNull(store.read(sessionId))
    }

    @Test
    fun workingTextCanBeEmptyAndDoesNotReplaceTheOriginalBeforeAcceptance() {
        val store = CodeEditSessionStore(temporaryFolder.newFolder("sessions"))
        val id = store.create("original")
        assertNull(store.readWorkingText(id))
        store.writeWorkingText(id, "")
        assertEquals("", store.readWorkingText(id))
        assertEquals("original", store.read(id))
        store.delete(id)
        assertNull(store.readWorkingText(id))
        assertNull(store.read(id))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsPathAsSessionId() {
        val store = CodeEditSessionStore(temporaryFolder.newFolder("sessions"))

        store.read("../outside")
    }
}
