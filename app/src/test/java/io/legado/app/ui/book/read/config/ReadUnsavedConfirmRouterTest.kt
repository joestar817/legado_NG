package io.legado.app.ui.book.read.config

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 「继续编辑」语义契约：Cancel/点外部关闭不触发 rollback、不关闭配置界面；
 * 三个按钮互斥且各只生效一次。
 */
class ReadUnsavedConfirmRouterTest {

    private class Recorder {
        var dismissed = 0
        var kept = 0
        var discarded = 0
        var cancelled = 0
    }

    private fun router(
        recorder: Recorder,
        onCancelled: (() -> Unit)? = { recorder.cancelled++ },
    ) = ReadUnsavedConfirmRouter(
        dismiss = { recorder.dismissed++ },
        onKeep = { recorder.kept++ },
        onDiscard = { recorder.discarded++ },
        onCancelled = onCancelled,
    )

    @Test
    fun `keep editing only closes confirm and fires cancelled`() {
        val recorder = Recorder()
        val router = router(recorder)
        router.cancel()
        assertEquals(1, recorder.dismissed)
        assertEquals(1, recorder.cancelled)
        assertEquals(0, recorder.kept)
        assertEquals(0, recorder.discarded)
    }

    @Test
    fun `outside dismiss equals cancel without action`() {
        val recorder = Recorder()
        val router = router(recorder)
        router.outsideDismiss()
        assertEquals(0, recorder.dismissed)
        assertEquals(1, recorder.cancelled)
        assertEquals(0, recorder.kept)
        assertEquals(0, recorder.discarded)
    }

    @Test
    fun `discard rolls back and leaves`() {
        val recorder = Recorder()
        val router = router(recorder)
        router.discard()
        assertEquals(1, recorder.dismissed)
        assertEquals(1, recorder.discarded)
        assertEquals(0, recorder.kept)
        assertEquals(0, recorder.cancelled)
    }

    @Test
    fun `keep saves and leaves`() {
        val recorder = Recorder()
        val router = router(recorder)
        router.keep()
        assertEquals(1, recorder.dismissed)
        assertEquals(1, recorder.kept)
        assertEquals(0, recorder.discarded)
        assertEquals(0, recorder.cancelled)
    }

    @Test
    fun `each action takes effect at most once`() {
        val recorder = Recorder()
        val router = router(recorder)
        router.cancel()
        router.outsideDismiss()
        router.keep()
        router.discard()
        assertEquals(1, recorder.dismissed)
        assertEquals(1, recorder.cancelled)
        assertEquals(0, recorder.kept)
        assertEquals(0, recorder.discarded)
    }
}
