package io.legado.app.help.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AiSkillPackageRegistryTest {

    @Test
    fun packageHashIsStableAcrossInputOrder() {
        val first = AiSkillPackageRegistry.buildSnapshot(
            "book_scan",
            linkedMapOf(
                "SKILL.md" to "entry".toByteArray(),
                "references/quick-scan.md" to "quick".toByteArray()
            )
        )
        val second = AiSkillPackageRegistry.buildSnapshot(
            "book_scan",
            linkedMapOf(
                "references/quick-scan.md" to "quick".toByteArray(),
                "SKILL.md" to "entry".toByteArray()
            )
        )

        assertEquals(first.contentHash, second.contentHash)
        assertEquals(first.resourcePaths, second.resourcePaths)
    }

    @Test
    fun packageHashChangesWhenLinkedResourceChanges() {
        val first = snapshot("quick-v1")
        val second = snapshot("quick-v2")

        assertNotEquals(first.contentHash, second.contentHash)
    }

    @Test
    fun packageSetPinsEveryAvailableSkillAndKeepsSinglePackageCompatibility() {
        val workflow = AiSkillPackageRegistry.buildSnapshot(
            "book_scan",
            mapOf("SKILL.md" to "workflow".toByteArray())
        )
        val facts = AiSkillPackageRegistry.buildSnapshot(
            "book_scan_facts",
            mapOf("SKILL.md" to "facts".toByteArray())
        )
        val single = AiSkillPackageRegistry.buildSetSnapshot(mapOf(workflow.skillId to workflow))
        val combined = AiSkillPackageRegistry.buildSetSnapshot(
            mapOf(facts.skillId to facts, workflow.skillId to workflow)
        )

        assertEquals(workflow.contentHash, single.contentHash)
        assertEquals(listOf("book_scan", "book_scan_facts"), combined.skillIds)
        assertNotEquals(single.contentHash, combined.contentHash)
    }

    @Test
    fun rejectsMissingEntryAndUnsafePaths() {
        expectIllegalArgument("缺少 SKILL.md") {
            AiSkillPackageRegistry.buildSnapshot(
                "book_scan",
                mapOf("references/quick-scan.md" to byteArrayOf())
            )
        }
        listOf("../secret.md", "/secret.md", "references\\secret.md", "references/./secret.md")
            .forEach { path ->
                expectIllegalArgument("") {
                    AiSkillPackageRegistry.normalizeRelativePath(path)
                }
            }
    }

    private fun snapshot(quick: String): AiSkillPackageSnapshot {
        return AiSkillPackageRegistry.buildSnapshot(
            "book_scan",
            mapOf(
                "SKILL.md" to "entry".toByteArray(),
                "references/quick-scan.md" to quick.toByteArray()
            )
        )
    }

    private fun expectIllegalArgument(messagePart: String, block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message.orEmpty().contains(messagePart))
        }
    }
}
