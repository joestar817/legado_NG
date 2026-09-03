package io.legado.app.help.config

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadHighlightRulePackageManagerTest {

    @Test
    fun readsStandaloneHighlightRuleDocument() {
        val rules = decodePackagedHighlightRules(
            """[{"id":"standalone","name":"独立","pattern":"standalone"}]""",
        )

        assertEquals(listOf("standalone"), rules.map(ReadHighlightRule::id))
    }
}
