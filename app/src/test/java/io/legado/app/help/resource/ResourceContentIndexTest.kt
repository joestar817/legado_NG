package io.legado.app.help.resource

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceContentIndexTest {
    @Test
    fun validatesExactWireBytesAndBindsTheCurrentSourceCommit() {
        val bytes = """{"schemaVersion":1,"type":"theme","resources":[]}""".toByteArray()
        val category = ResourceCategory("themes", "主题", "theme", "resources/themes/index.json", 0, ResourceProtocol.hash(bytes))
        val catalog = ResourceCatalog(1, "a".repeat(40), listOf(category))
        val index = decodeResourceContentIndex(bytes, catalog, category)
        assertEquals(catalog.sourceCommit, index.sourceCommit)
        assertTrue(index.resources.isEmpty())
        assertTrue(runCatching { decodeResourceContentIndex(bytes + 10.toByte(), catalog, category) }.isFailure)
    }

    @Test
    fun malformedCategoryHashIsRejectedAndLegacyCategoryStillValidates() {
        val legacy = ResourceCategory("themes", "主题", "theme", "resources/themes/index.json", 0)
        val catalog = ResourceCatalog(1, "a".repeat(40), listOf(legacy))
        ResourceProtocol.validateCatalog(catalog)
        assertTrue(runCatching {
            ResourceProtocol.validateCatalog(catalog.copy(categories = listOf(legacy.copy(indexSha256 = "../file"))))
        }.isFailure)
    }
}
