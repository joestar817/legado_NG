package io.legado.app.ui.design.theme

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.text.style.MetricAffectingSpan
import android.text.TextPaint
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.test.core.app.ApplicationProvider
import io.legado.app.help.config.NgInterfaceFontStore
import io.legado.app.R
import io.legado.app.utils.defaultSharedPreferences
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString
import io.noties.markwon.Markwon
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = InterfaceFontTestApplication::class)
class NgAppViewInflaterTest {
    class FontActivity : AppCompatActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            setTheme(R.style.AppTheme_Light)
            super.onCreate(savedInstanceState)
        }
    }

    @Test
    fun `parsed markdown keeps dynamic code sizes while changing inline and block fonts`() {
        val controller = Robolectric.buildActivity(FontActivity::class.java).setup()
        val activity = controller.get()
        val original = activity.getPrefString("ngInterfaceFont.choice")
        try {
            activity.putPrefString("ngInterfaceFont.choice", "serif")
            val baseline = Markwon.builder(activity).build()
            val themed = Markwon.builder(activity)
                .usePlugin(NgInterfaceFontMarkwonPlugin(activity))
                .build()
            val samples = listOf("`sample`", "```\nsample\n```", "    sample")
            for (source in samples) {
                val originalText = baseline.toMarkdown(source)
                val themedText = themed.toMarkdown(source)
                assertEquals(originalText.toString(), themedText.toString())
                val start = originalText.toString().indexOf("sample")
                assertTrue(start >= 0)
                val originalSpans = originalText.getSpans(start, start + 1, MetricAffectingSpan::class.java)
                val themedSpans = themedText.getSpans(start, start + 1, MetricAffectingSpan::class.java)
                assertTrue(originalSpans.isNotEmpty())
                assertTrue(themedSpans.size > originalSpans.size)
                for (size in listOf(16f, 24f)) {
                    for (measure in listOf(true, false)) {
                        fun applySpans(spans: Array<MetricAffectingSpan>) = TextPaint().apply {
                            textSize = size
                            typeface = Typeface.DEFAULT
                            spans.forEach { span ->
                                if (measure) span.updateMeasureState(this) else span.updateDrawState(this)
                            }
                        }
                        val originalPaint = applySpans(originalSpans)
                        val themedPaint = applySpans(themedSpans)
                        assertEquals(size * 0.87f, originalPaint.textSize, 0.01f)
                        assertEquals(originalPaint.textSize, themedPaint.textSize, 0.01f)
                        assertEquals(Typeface.SERIF, themedPaint.typeface)
                    }
                }
            }
        } finally {
            activity.putPrefString("ngInterfaceFont.choice", original)
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `real activity inflater applies interface font and never revisits preview text`() {
        val controller = Robolectric.buildActivity(FontActivity::class.java).setup()
        val activity = controller.get()
        val original = activity.getPrefString("ngInterfaceFont.choice")
        try {
            activity.putPrefString("ngInterfaceFont.choice", "serif")
            val root = activity.layoutInflater.inflate(R.layout.item_1line_text, null)
            val text = root.findViewById<TextView>(R.id.text_view)
            assertEquals(Typeface.SERIF, text.typeface)
            // A font picker can deliberately assign its sample after creation.
            text.typeface = Typeface.MONOSPACE
            activity.setContentView(root)
            root.measure(0, 0)
            root.layout(0, 0, 300, 100)
            assertEquals(Typeface.MONOSPACE, text.typeface)
        } finally {
            activity.putPrefString("ngInterfaceFont.choice", original)
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `installed factory themes explicit monospace edit text while preserving size and bold`() {
        val controller = Robolectric.buildActivity(FontActivity::class.java).setup()
        val activity = controller.get()
        val original = activity.getPrefString("ngInterfaceFont.choice")
        try {
            activity.putPrefString("ngInterfaceFont.choice", "serif")
            val attrs = Robolectric.buildAttributeSet()
                .addAttribute(android.R.attr.fontFamily, "monospace")
                .addAttribute(android.R.attr.textStyle, "bold")
                .addAttribute(android.R.attr.textSize, "19sp")
                .build()
            val factory = activity.layoutInflater.factory2
            assertNotNull(factory)
            val edit = factory!!.onCreateView(null, "EditText", activity, attrs) as EditText
            assertEquals(Typeface.create(Typeface.SERIF, Typeface.BOLD), edit.typeface)
            assertEquals(19f * activity.resources.displayMetrics.scaledDensity, edit.textSize, 0.01f)
            // The custom XML class path must use the same policy as simple tags.
            val custom = factory.onCreateView(
                null, "io.legado.app.lib.theme.view.ThemeEditText", activity, attrs,
            ) as EditText
            assertEquals(edit.typeface, custom.typeface)
        } finally {
            activity.putPrefString("ngInterfaceFont.choice", original)
            controller.pause().stop().destroy()
        }
    }
    @Test
    fun `explicit file reload detects changed bytes with identical path size and timestamp`() {
        assertReloadLifecycle(useContentUri = false)
    }

    @Test
    fun `explicit content reload reads same URI again without changing saved choice on cancel or failure`() {
        assertReloadLifecycle(useContentUri = true)
    }

    private fun assertReloadLifecycle(useContentUri: Boolean) = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        FontReloadFixture(context, useContentUri).use { fixture ->
            val outputFiles = fontOutputFiles(context)
            val first = NgInterfaceFontStore.load(context, fixture.choice, reloadFromSource = true)
            NgInterfaceFontStore.save(context, fixture.choice, "original", first)
            val savedBefore = fixture.savedPreferences()
            assertNull(fixture.preferences.getString("ngInterfaceFont.file", null))
            assertEquals(fixture.choice, NgInterfaceFontStore.choice(context))
            val originalTimestamp = fixture.source.lastModified()
            val originalLength = fixture.source.length()
            fixture.replaceSource()
            assertEquals(originalTimestamp, fixture.source.lastModified())
            assertEquals(originalLength, fixture.source.length())

            val refreshed = NgInterfaceFontStore.load(context, fixture.choice, reloadFromSource = true)
            assertNotEquals(first.fingerprint, refreshed.fingerprint)
            assertEquals(if (useContentUri) null else fixture.source, refreshed.file)
            // Loading and cancelling a candidate do not replace the already applied in-memory font.
            assertEquals(savedBefore, fixture.savedPreferences())
            assertSame(first.typeface, NgInterfaceFontStore.typeface(context))
            assertEquals(first.fingerprint, NgInterfaceFontStore.load(context, fixture.choice).fingerprint)

            NgInterfaceFontStore.save(context, fixture.choice, "updated", refreshed)
            assertNull(fixture.preferences.getString("ngInterfaceFont.file", null))
            assertEquals(fixture.choice, NgInterfaceFontStore.choice(context))
            assertEquals("updated", NgInterfaceFontStore.name(context))
            assertSame(refreshed.typeface, NgInterfaceFontStore.typeface(context))

            val savedAfter = fixture.savedPreferences()
            fixture.source.writeBytes(ByteArray(32))
            assertTrue("Malformed existing font must not silently decode as DEFAULT", runCatching {
                NgInterfaceFontStore.load(context, fixture.choice, reloadFromSource = true)
            }.isFailure)
            assertEquals(savedAfter, fixture.savedPreferences())
            assertSame(refreshed.typeface, NgInterfaceFontStore.typeface(context))
            assertTrue(fixture.source.delete())
            assertSame(refreshed.typeface, NgInterfaceFontStore.typeface(context))
            assertTrue("Explicit reload must report the missing source", runCatching {
                NgInterfaceFontStore.load(context, fixture.choice, reloadFromSource = true)
            }.isFailure)
            assertEquals(savedAfter, fixture.savedPreferences())
            assertEquals(outputFiles, fontOutputFiles(context))
        }
    }

    @Test
    fun `existing explicit private copy stays usable until the user reselects its source`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        FontReloadFixture(context, useContentUri = true).use { fixture ->
            val legacy = File(context.filesDir, "interface-fonts/legacy-${UUID.randomUUID()}.font")
            legacy.parentFile!!.mkdirs()
            legacy.writeBytes(fixture.originalBytes)
            try {
                fixture.preferences.edit().putString("ngInterfaceFont.choice", fixture.choice)
                    .putString("ngInterfaceFont.file", legacy.path).putString("ngInterfaceFont.name", "旧字体").commit()
                assertTrue(fixture.source.delete())
                assertEquals(legacy, NgInterfaceFontStore.load(context, fixture.choice).file)
                assertEquals(legacy.path, fixture.preferences.getString("ngInterfaceFont.file", null))
                fixture.source.writeBytes(fixture.updatedBytes)
                val direct = NgInterfaceFontStore.load(context, fixture.choice, reloadFromSource = true)
                NgInterfaceFontStore.save(context, fixture.choice, "直接引用", direct)
                assertNull(fixture.preferences.getString("ngInterfaceFont.file", null))
                assertEquals(fixture.choice, NgInterfaceFontStore.choice(context))
                assertTrue(legacy.isFile)
                assertArrayEquals(fixture.originalBytes, legacy.readBytes())
            } finally { legacy.delete() }
        }
    }

    private fun fontOutputFiles(context: Context): Set<String> = listOf(
        File(context.cacheDir, "interface-font-preview"), File(context.filesDir, "interface-fonts"),
        File(context.getExternalFilesDir(null) ?: context.filesDir, "font"),
    ).flatMap { root -> if (root.isDirectory) root.walkTopDown().filter(File::isFile).map { it.path }.toList() else emptyList() }.toSet()

}

/** Actual content-resolver reads, with a bounded gate for overlapping picker requests. */
internal class ReloadFontProvider : ContentProvider() {
    lateinit var source: File
    @Volatile var gate: CountDownLatch? = null
    @Volatile var entered = CountDownLatch(1)
    @Volatile var returned = CountDownLatch(1)

    override fun onCreate() = true
    override fun getType(uri: Uri) = "font/ttf"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?,
        selectionArgs: Array<out String>?) = 0

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val currentGate = gate
        if (currentGate != null) {
            entered.countDown()
            check(currentGate.await(10, TimeUnit.SECONDS)) { "Timed out waiting for test font release" }
        }
        return ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY).also {
            returned.countDown()
        }
    }
}

internal class FontReloadFixture(val context: Context, useContentUri: Boolean) : AutoCloseable {
    val preferences = context.defaultSharedPreferences
    private val keys = listOf("ngInterfaceFont.choice", "ngInterfaceFont.file", "ngInterfaceFont.name",
        NgInterfaceFontStore.DIRECTORY)
    private val previous = keys.associateWith { preferences.getString(it, null) }
    private val directory = File(context.cacheDir, "reload-test-${UUID.randomUUID()}").apply { mkdirs() }
    val source = File(directory, "reload.ttf")
    // Trailing bytes are outside TTF tables; both fonts are valid and have identical lengths.
    val originalBytes = context.assets.open("font/number.ttf").use { it.readBytes() } + byteArrayOf(0, 0, 0, 1)
    val updatedBytes = originalBytes.copyOf().also { it[it.lastIndex] = 2 }
    val provider = ReloadFontProvider().apply { source = this@FontReloadFixture.source }
    val choice: String

    init {
        source.writeBytes(originalBytes)
        preferences.edit().remove("ngInterfaceFont.choice").remove("ngInterfaceFont.file")
            .remove("ngInterfaceFont.name").putString(NgInterfaceFontStore.DIRECTORY, directory.path).commit()
        if (useContentUri) {
            val authority = "font.reload.${UUID.randomUUID()}"
            provider.attachInfo(context, ProviderInfo().apply { this.authority = authority })
            ShadowContentResolver.registerProviderInternal(authority, provider)
            choice = "content://$authority/font.ttf"
        } else {
            choice = source.toURI().toString()
        }
    }

    fun replaceSource() {
        val stamp = source.lastModified()
        source.writeBytes(updatedBytes)
        assertTrue(source.setLastModified(stamp))
    }

    fun savedPreferences() = keys.take(3).associateWith { preferences.getString(it, null) }

    override fun close() {
        provider.gate?.countDown()
        preferences.edit().apply { previous.forEach { (key, value) -> putString(key, value) } }.commit()
        directory.deleteRecursively()
    }
}
