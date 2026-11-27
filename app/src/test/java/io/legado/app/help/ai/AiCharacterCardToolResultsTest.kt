package io.legado.app.help.ai

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AiCharacterCardToolResultsTest {
    @Test
    fun cacheCompactionPreservesGapsTitlesVolumesAndContinuation() {
        val result = structuredResult("""
            {"ok":true,"normalized_data":{"next_start":8,"truncated_by_mcp":true,"chapters":[
              {"index":0,"chapter_number":1,"title":"序章","has_content":true,"is_volume":true},
              {"index":1,"chapter_number":2,"title":"第1章","has_content":false,"is_volume":false},
              {"index":2,"chapter_number":3,"title":"第2章","has_content":true,"is_volume":false},
              {"index":5,"chapter_number":6,"title":"第5章","has_content":true,"is_volume":false},
              {"index":6,"chapter_number":7,"title":"第6章","has_content":true,"is_volume":false}
            ]},"warnings":[]}
        """)
        val original = result.deepCopy()
        val projected = modelData(AiCharacterCardToolResults.compact("bookshelf_cache_status_get", result))

        assertEquals(JsonParser.parseString("[[0,2],[5,6]]"), projected.get("chapter_index_ranges"))
        assertEquals(JsonParser.parseString("[[0,0],[2,2],[5,6]]"), projected.get("cached_chapter_index_ranges"))
        assertEquals(JsonParser.parseString("[[0,0]]"), projected.get("volume_chapter_index_ranges"))
        assertEquals(JsonParser.parseString("""["序章","第1章","第2章","第5章","第6章"]"""), projected.get("chapter_titles"))
        assertEquals(8, projected.get("next_start").asInt)
        assertTrue(projected.get("truncated_by_mcp").asBoolean)
        assertTrue(projected.get("chapter_number_is_index_plus_one").asBoolean)
        assertFalse(projected.has("chapters"))
        assertEquals(original, result)
    }

    @Test
    fun searchCompactionPreservesPerNameHitsOptionalSnippetsAndPagination() {
        val result = structuredResult("""
            {"ok":true,"_agent_result":{"receipt_id":"receipt"},"normalized_data":{
              "has_more":true,"next_start_chapter_index":9,"truncated_by_snippet_budget":true,
              "queries":[{"query":"主名","chapter_indexes":[1,8],"total_matches":3}],
              "chapters":[
                {"index":1,"chapter_number":2,"title":"第1章","count":3,"hits":{"主名":2,"别名":1},
                 "snippets":[{"query":"别名","text":"原文片段"}]},
                {"index":8,"chapter_number":9,"title":"第8章","count":1,"hits":{"主名":1}}
              ]
            },"warnings":["部分片段已达上限"]}
        """)
        val compact = AiCharacterCardToolResults.compact("bookshelf_text_search", result)
        val formatted = JsonParser.parseString(
            AiModelToolContent.format(compact, "bookshelf_text_search", Int.MAX_VALUE),
        ).asJsonObject
        val projected = formatted.getAsJsonObject("normalized_data")

        assertEquals(
            JsonParser.parseString("""["index","title","count","hits","snippets"]"""),
            projected.get("chapter_columns"),
        )
        assertEquals(JsonParser.parseString("""
            [[1,"第1章",3,{"主名":2,"别名":1},[{"query":"别名","text":"原文片段"}]],
             [8,"第8章",1,{"主名":1},null]]
        """), projected.get("chapters"))
        assertEquals(modelData(result).get("queries"), projected.get("queries"))
        assertEquals(9, projected.get("next_start_chapter_index").asInt)
        assertTrue(projected.get("has_more").asBoolean)
        assertTrue(projected.get("truncated_by_snippet_budget").asBoolean)
        assertEquals("部分片段已达上限", formatted.getAsJsonArray("warnings")[0].asString)
        assertEquals("receipt", formatted.getAsJsonObject("_agent_result").get("receipt_id").asString)
    }

    @Test
    fun errorsUnsupportedToolsAndUnrecognizedRowsKeepTheirOriginalShape() {
        val error = structuredResult("""{"ok":false,"normalized_data":null,"warnings":["未找到书籍"]}""")
        val unexpected = structuredResult("""
            {"ok":true,"normalized_data":{"chapters":[
              {"index":1,"chapter_number":2,"title":"序章","count":1,"hits":{"主名":1},"new_field":true}
            ]}}
        """)
        val normal = structuredResult("""
            {"ok":true,"normalized_data":{"chapters":[
              {"index":1,"chapter_number":2,"title":"序章","count":1,"hits":{"主名":1}}
            ]}}
        """)

        assertSame(error, AiCharacterCardToolResults.compact("bookshelf_cache_status_get", error))
        assertSame(unexpected, AiCharacterCardToolResults.compact("bookshelf_text_search", unexpected))
        assertSame(normal, AiCharacterCardToolResults.compact("bookshelf_text_window_get", normal))
    }

    private fun structuredResult(content: String): JsonObject = JsonObject().apply {
        add("structuredContent", JsonParser.parseString(content))
    }

    private fun modelData(result: JsonObject): JsonObject =
        result.getAsJsonObject("structuredContent").getAsJsonObject("normalized_data")
}
