package io.legado.app.help.rhino

import com.script.ScriptBindings
import com.script.rhino.ReadOnlyJavaObject
import com.script.rhino.RhinoScriptEngine
import com.script.rhino.RhinoWrapFactory
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

class NativeBookTest {

    @Test
    fun scriptCannotDisablePurificationOrReplaceReadConfig() {
        val book = Book(name = "测试书籍")
        book.setUseReplaceRule(true)
        val originalConfig = book.readConfig
        val replacementConfig = Book.ReadConfig(useReplaceRule = false)

        val result = RhinoScriptEngine.eval(
            """
                book.setUseReplaceRule(false);
                book["setUseReplaceRule(boolean)"](false);
                book.useReplaceRule = false;
                book.readConfig = replacementConfig;
                book.setReadConfig(replacementConfig);
                book.config.useReplaceRule = false;
                "continued";
            """.trimIndent(),
            ScriptBindings().apply {
                put("book", book)
                put("replacementConfig", replacementConfig)
            }
        )

        assertEquals("continued", result)
        assertTrue(book.getUseReplaceRule())
        assertSame(originalConfig, book.readConfig)

        book.setUseReplaceRule(false)
        assertFalse(book.getUseReplaceRule())
    }

    @Test
    fun scriptSaveAndDeleteRemainCallableWithoutSideEffects() {
        val book = Book(bookUrl = "test://book", name = "测试书籍")

        val result = RhinoScriptEngine.eval(
            """
                book.save();
                book["save()"]();
                book.delete();
                book["delete()"]();
                "continued";
            """.trimIndent(),
            ScriptBindings().apply { put("book", book) }
        )

        assertEquals("continued", result)
        assertEquals("test://book", book.bookUrl)
    }

    @Test
    fun userOwnedFieldsRemainReadableButCannotBeModified() {
        val book = Book(
            group = 1L,
            order = 2,
            customTag = "用户标签",
            customCoverUrl = "user://cover",
            customIntro = "用户简介",
            intro = "来源简介"
        )

        val result = RhinoScriptEngine.eval(
            """
                var before = [
                    book.group,
                    book.order,
                    book.customTag,
                    book.customCoverUrl,
                    book.customIntro
                ].join("|");
                book.group = 10;
                book.setGroup(11);
                book["setGroup(long)"](12);
                book.order = 20;
                book.setOrder(21);
                book.customTag = "来源标签";
                book.setCustomTag("来源标签2");
                book.customCoverUrl = "source://cover";
                book.setCustomCoverUrl("source://cover2");
                book.customIntro = "来源覆盖简介";
                book.setCustomIntro("来源覆盖简介2");
                book.upCustomIntro();
                before;
            """.trimIndent(),
            ScriptBindings().apply { put("book", book) }
        )

        assertEquals("1|2|用户标签|user://cover|用户简介", result)
        assertEquals(1L, book.group)
        assertEquals(2, book.order)
        assertEquals("用户标签", book.customTag)
        assertEquals("user://cover", book.customCoverUrl)
        assertEquals("用户简介", book.customIntro)
    }

    @Test
    fun existingBusinessRoutingProgressAndDisplayCapabilitiesRemainWritable() {
        val book = Book(bookUrl = "old://book", name = "旧书名", author = "旧作者")

        val result = RhinoScriptEngine.eval(
            """
                book.name = "新书名";
                book.setAuthor("新作者");
                book.bookUrl = "new://book";
                book.tocUrl = "new://toc";
                book.origin = "aggregate://source";
                book.type = 2;
                book.canUpdate = false;
                book.charset = "GBK";
                book.durChapterIndex = 12;
                book["setDurChapterIndex(int)"](13);
                book.durChapterPos = 34;
                book.syncTime = 99;
                book.setReverseToc(true);
                book.imageStyle = "FULL";
                "continued";
            """.trimIndent(),
            ScriptBindings().apply { put("book", book) }
        )

        assertEquals("continued", result)
        assertEquals("新书名", book.name)
        assertEquals("新作者", book.author)
        assertEquals("new://book", book.bookUrl)
        assertEquals("new://toc", book.tocUrl)
        assertEquals("aggregate://source", book.origin)
        assertEquals(2, book.type)
        assertFalse(book.canUpdate)
        assertEquals("GBK", book.charset)
        assertEquals(13, book.durChapterIndex)
        assertEquals(34, book.durChapterPos)
        assertEquals(99L, book.syncTime)
        assertTrue(book.getReverseToc())
        assertEquals("FULL", book.getImageStyle())
    }

    @Test
    fun bookVariableMethodsRemainCallable() {
        val book = VariableBookFixture()

        val result = RhinoScriptEngine.eval(
            """
                book.putVariable("token", "value");
                book.getVariable("token");
            """.trimIndent(),
            ScriptBindings().apply { put("book", book) }
        )

        assertEquals("value", result)
        assertEquals("value", book.getVariable("token"))
    }

    @Test
    fun chapterUpdateIsCallableNoOpAndChapterRemainsReadOnly() {
        val chapter = BookChapter(title = "原章节")

        val result = RhinoScriptEngine.eval(
            """
                chapter.title = "恶意章节";
                chapter.update();
                chapter["update()"]();
                chapter.title;
            """.trimIndent(),
            ScriptBindings().apply { put("chapter", chapter) }
        )

        assertEquals("原章节", result)
        assertEquals("原章节", chapter.title)
    }

    @Test
    fun publicBookMutationSurfaceRequiresExplicitReviewWhenChanged() {
        val mutationPrefixes = listOf(
            "set",
            "put",
            "add",
            "remove",
            "save",
            "delete",
            "migrate",
            "copy",
            "update",
            "up",
            "writeToParcel"
        )
        val actual = Book::class.java.methods
            .map { it.name }
            .filter { name -> mutationPrefixes.any(name::startsWith) }
            .toSortedSet()
        val reviewed = setOf(
            "addDelTag",
            "copy",
            "copy\$default",
            "delete",
            "migrateTo",
            "putBigVariable",
            "putCustomVariable",
            "putVariable",
            "removeDelTag",
            "save",
            "setAuthor",
            "setBookUrl",
            "setCanUpdate",
            "setChapterInVolumeIndex",
            "setCharset",
            "setCloseCredits",
            "setCoverUrl",
            "setCustomCoverUrl",
            "setCustomIntro",
            "setCustomTag",
            "setDailyChapters",
            "setDownloadUrls",
            "setDurChapterIndex",
            "setDurChapterPos",
            "setDurChapterTime",
            "setDurChapterTitle",
            "setDurVolumeIndex",
            "setGroup",
            "setImageStyle",
            "setInfoHtml",
            "setIntro",
            "setKind",
            "setLastCheckCount",
            "setLastCheckTime",
            "setLatestChapterTime",
            "setLatestChapterTitle",
            "setName",
            "setOpenCredits",
            "setOrder",
            "setOrigin",
            "setOriginName",
            "setOriginOrder",
            "setPageAnim",
            "setPlayMode",
            "setPlaySpeed",
            "setReadConfig",
            "setReadSimulating",
            "setReSegment",
            "setRemoveSameTitle",
            "setReverseToc",
            "setSplitLongChapter",
            "setStartChapter",
            "setStartDate",
            "setSyncTime",
            "setTocHtml",
            "setTocUrl",
            "setTotalChapterNum",
            "setTtsEngine",
            "setType",
            "setUseReplaceRule",
            "setVariable",
            "setWordCount",
            "upCustomIntro",
            "writeToParcel"
        ).toSortedSet()

        assertEquals(reviewed, actual)
    }

    class VariableBookFixture {
        private val variables = hashMapOf<String, String>()

        fun putVariable(key: String, value: String?) {
            if (value == null) {
                variables.remove(key)
            } else {
                variables[key] = value
            }
        }

        fun getVariable(key: String): String {
            return variables[key].orEmpty()
        }
    }

    companion object {
        @JvmStatic
        @BeforeClass
        fun registerWrappers() {
            RhinoWrapFactory.register(Book::class.java, NativeBook.factory)
            RhinoWrapFactory.register(Book.ReadConfig::class.java, ReadOnlyJavaObject.factory)
            RhinoWrapFactory.register(
                BookChapter::class.java,
                ReadOnlyJavaObject.factory(setOf("update"))
            )
            RhinoWrapFactory.register(VariableBookFixture::class.java, NativeBook.factory)
            RhinoScriptEngine
        }
    }
}
