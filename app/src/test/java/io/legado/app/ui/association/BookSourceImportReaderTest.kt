package io.legado.app.ui.association

import io.legado.app.data.entities.BookSource
import io.legado.app.help.source.isEmptyConfiguration
import io.legado.app.utils.GSON
import java.io.Reader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookSourceImportReaderTest {
    @Test
    fun emptyAddressesAreSkippedWithoutLosingSurroundingSources() = runBlocking {
        val urls = arrayListOf<String>()
        val skipped = readBookSourceImport(
            """[{"bookSourceUrl":"before"},{"bookSourceUrl":"","bookSourceGroup":"已检"},{},{"bookSourceUrl":"after"}]""".reader(),
            false, { urls.add(it.bookSourceUrl) }, { error("No links") }
        )
        assertEquals(2, skipped)
        assertEquals(listOf("before", "after"), urls)
    }

    @Test
    fun emptyAddressOnlyInputsStillFail() = runBlocking {
        for (text in listOf("""[{"bookSourceUrl":""},{}]""", """{"bookSourceUrl":""}""")) {
            val failure = runCatching {
                readBookSourceImport(text.reader(), false, { error("No source expected") }, {})
            }.exceptionOrNull()
            assertTrue(failure?.message.orEmpty().contains("不是书源"))
        }
    }

    @Test
    fun arrayIsDeliveredIncrementallyWithoutReadingTheNextLargeItem() = runBlocking {
        val source = """{"bookSourceUrl":"one","ruleContent":{"content":"content"}}"""
        val input = ChunkReader(sequence {
            yield("[" + source + ",")
            yield(source.replace("one", "two"))
            yield("]")
        }.iterator())
        val names = arrayListOf<String>()
        readBookSourceImport(input, false, {
            if (names.isEmpty()) assertFalse(input.finished)
            names.add(it.bookSourceUrl)
        }, { error("No links expected") })
        assertEquals(listOf("one", "two"), names)
    }

    @Test
    fun fullConfigurationSurvivesObjectArrayAndStringEncodedRules() = runBlocking {
        val source = BookSource(bookSourceUrl = "source", mainJs = "function search() {}",
            loginUi = "[]", bookSourceComment = "说明", header = "{\"A\":\"B\"}")
        for (input in listOf(GSON.toJson(source), "[${GSON.toJson(source)}]")) {
            var actual: BookSource? = null
            readBookSourceImport(input.reader(), false, { actual = it }, { error("No links") })
            assertEquals(GSON.toJson(source), GSON.toJson(actual))
        }
        var decoded: BookSource? = null
        readBookSourceImport("""[{"bookSourceUrl":"x","ruleContent":"{\"content\":\"#text\"}"}]""".reader(),
            false, { decoded = it }, { error("No links") })
        assertEquals("#text", decoded!!.ruleContent!!.content)
    }

    @Test
    fun emptyConfigurationIsKeptForTheDisabledPreviewRow() = runBlocking {
        var count = 0
        readBookSourceImport("""[{"bookSourceUrl":"empty","ruleContent":{}}]""".reader(), false, {
            assertTrue(it.isEmptyConfiguration())
            count++
        }, { error("No links") })
        assertEquals(1, count)
    }

    @Test
    fun linkWrapperIsOnlyExpandedForAllowedEntry() = runBlocking {
        val links = arrayListOf<String>()
        readBookSourceImport("""{"sourceUrls":["https://a","https://b"]}""".reader(), true,
            { error("No source") }, { links.add(it) })
        assertEquals(listOf("https://a", "https://b"), links)
    }

    @Test
    fun standaloneJsStillUsesTheExistingConfigExtractor() = runBlocking {
        val script = """
            var config = {bookSourceUrl: "https://example.com", bookSourceName: "JS测试"};
            function search(key, page) { return []; }
            function getChapters(book) { return []; }
            function getContent(chapter, book, nextChapterUrl) { return "正文"; }
        """.trimIndent()
        var source: BookSource? = null
        readBookSourceImport(script.reader(), false, { source = it }, { error("No links") })
        assertEquals("https://example.com", source!!.bookSourceUrl)
        assertEquals(script, source!!.mainJs)
    }

    @Test
    fun oversizedJsIsRejectedBeforeExecutingAnyScript() = runBlocking {
        val input = ChunkReader(sequence {
            yield("var x = '")
            repeat(130) { yield("x".repeat(8192)) }
            yield("';")
        }.iterator())
        val error = runCatching {
            readBookSourceImport(input, false, { error("Must not emit oversized JS") }, {})
        }.exceptionOrNull()
        assertTrue(error?.message.orEmpty().contains("1 MiB"))
        assertFalse(input.finished)
    }

    @Test
    fun nullItemsAndTrailingGarbageAreNotSilentlyAccepted() = runBlocking {
        for (text in listOf("[null]", """[{"bookSourceUrl":"x"}] trailing""")) {
            val error = runCatching {
                readBookSourceImport(text.reader(), false, {}, {})
            }.exceptionOrNull()
            assertTrue(text, error != null)
        }
    }

    @Test
    fun callbackCancellationStopsConsumption() = runBlocking {
        var count = 0
        val error = runCatching {
            readBookSourceImport("""[{"bookSourceUrl":"a"},{"bookSourceUrl":"b"}]""".reader(), false, {
                count++
                throw CancellationException("stop")
            }, {})
        }.exceptionOrNull()
        assertTrue(error is CancellationException)
        assertEquals(1, count)
    }

    @Test
    fun generatedLargeCollectionDoesNotNeedAWholeDocumentString() = runBlocking {
        // 超过128MiB的总输入，由小块生成；回调不持有历史完整配置。
        val row = """{"bookSourceUrl":"x","mainJs":"${"x".repeat(32 * 1024)}"}"""
        val count = 4200
        val input = ChunkReader(sequence {
            yield("[")
            repeat(count) { if (it > 0) yield(","); yield(row) }
            yield("]")
        }.iterator())
        var received = 0
        readBookSourceImport(input, false, { received++ }, { error("No links") })
        assertEquals(count, received)
        assertTrue(input.characters > 128L * 1024 * 1024)
    }

    @Test
    fun typeDetectionReadsOnlyTheFirstObjectAndDoesNotKeepValues() {
        val input = ChunkReader(sequence {
            yield("""[{"bookSourceUrl":"a","mainJs":"${"x".repeat(8192)}"},""")
            error("Type detection must not read the remaining collection")
        }.iterator())
        assertEquals(setOf("bookSourceUrl", "mainJs"), firstImportObjectKeys(input))
        assertEquals(setOf("pattern"), firstImportObjectKeys("""{"pattern":"x"}""".reader()))
        assertEquals(setOf("script", "type"), firstImportObjectKeys("""[{"script":"x","type":"SCRIPT"}]""".reader()))
    }

    private class ChunkReader(private val chunks: Iterator<String>) : Reader() {
        private var current = ""
        private var offset = 0
        var finished = false
            private set
        var characters = 0L
            private set

        override fun read(buffer: CharArray, start: Int, length: Int): Int {
            if (length == 0) return 0
            while (offset == current.length) {
                if (!chunks.hasNext()) { finished = true; return -1 }
                current = chunks.next()
                offset = 0
            }
            val size = minOf(length, current.length - offset)
            current.toCharArray(buffer, start, offset, offset + size)
            offset += size
            characters += size
            return size
        }

        override fun close() = Unit
    }
}
