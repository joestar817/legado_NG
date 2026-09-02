package io.legado.app.ui.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TtsEngineConfigScreenTest {

    @Test
    fun `source back returns to form before leaving detail`() {
        assertEquals(
            TtsEngineConfigRoute.SCRIPT_FORM,
            TtsEngineConfigRoute.SCRIPT_SOURCE.backDestination(),
        )
    }

    @Test
    fun `all non-source detail routes return to engine list`() {
        listOf(
            TtsEngineConfigRoute.SCRIPT_FORM,
            TtsEngineConfigRoute.SCRIPT_VOICES,
            TtsEngineConfigRoute.SYSTEM_DETAIL,
        ).forEach { route ->
            assertEquals(TtsEngineConfigRoute.ENGINE_LIST, route.backDestination())
        }
        assertNull(TtsEngineConfigRoute.ENGINE_LIST.backDestination())
    }

    @Test
    fun `shared title bar belongs only to form source and system detail`() {
        assertEquals(false, TtsEngineConfigRoute.ENGINE_LIST.showsSharedTitleBar)
        assertEquals(true, TtsEngineConfigRoute.SCRIPT_FORM.showsSharedTitleBar)
        assertEquals(true, TtsEngineConfigRoute.SCRIPT_SOURCE.showsSharedTitleBar)
        assertEquals(false, TtsEngineConfigRoute.SCRIPT_VOICES.showsSharedTitleBar)
        assertEquals(true, TtsEngineConfigRoute.SYSTEM_DETAIL.showsSharedTitleBar)
    }

    @Test
    fun `detail tabs stay on script form and voices only`() {
        assertEquals(true, TtsEngineConfigRoute.SCRIPT_FORM.showsDetailTabs)
        assertEquals(true, TtsEngineConfigRoute.SCRIPT_VOICES.showsDetailTabs)
        assertEquals(false, TtsEngineConfigRoute.SCRIPT_SOURCE.showsDetailTabs)
        assertEquals(false, TtsEngineConfigRoute.SYSTEM_DETAIL.showsDetailTabs)
        assertEquals(0, TtsEngineConfigRoute.SCRIPT_FORM.detailTabIndex)
        assertEquals(1, TtsEngineConfigRoute.SCRIPT_VOICES.detailTabIndex)
    }
}
