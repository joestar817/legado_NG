package io.legado.app.help.resource

import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.help.tts.TtsEngineStore
import io.legado.app.help.tts.TtsVoice
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.service.BaseReadAloudService
import io.legado.app.service.HttpReadAloudService
import io.legado.app.ui.book.read.page.entities.TextChapter
import io.legado.app.ui.book.read.page.entities.TextLine
import io.legado.app.ui.book.read.page.entities.TextPage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import splitties.init.appCtx
import java.io.File
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlin.math.sin

/** Run only in a disposable Android user: this test starts the real foreground playback service. */
@RunWith(AndroidJUnit4::class)
class TtsResourcePlaybackInstrumentedTest {
    private val suffix = UUID.randomUUID().toString().replace("-", "")
    private val engineId = "device_tts_$suffix"
    private val resourceId = "tts.device_$suffix"
    private val category = ResourceCategory("tts", "朗读", "tts", "resources/tts/index.json", 1)
    private val commit = "a".repeat(40)

    @Before fun requireDisposableUser() {
        assumeTrue("requires an explicitly selected disposable Android user",
            InstrumentationRegistry.getArguments().getString("isolated") == "1")
        check(android.os.Process.myUid() / 100_000 != 0) { "do not run this test in the owner user" }
    }

    private fun script(version: Int, port: Int) = """
        // @name 设备更新测试
        // @uuid $engineId
        // @version $version.0.0
        // @audioType audio/wav
        // @capabilities synthesis_speed,synthesis_volume,synthesis_pitch
        function options() { return []; }
        function voices(options, ctx) { return []; }
        function synthesize(text, voice, params, options, ctx) {
            if (voice.id !== 'voice-a' || !voice.extra || voice.extra.marker !== 'keep') {
                throw new Error('voice metadata missing');
            }
            return 'http://127.0.0.1:$port/v$version?voice=' + voice.id + '&speed=' + params.speed;
        }
    """.trimIndent()

    private fun entry(version: Int, content: String): ResourceEntry {
        val bytes = content.toByteArray()
        val hash = ResourceProtocol.hash(bytes)
        val file = File(ResourceFiles.root, "payload-cache/$hash")
        file.parentFile!!.mkdirs()
        file.writeBytes(bytes)
        return ResourceEntry(category, commit, ResourceItem(
            id = resourceId, name = "设备更新测试", author = "测试", versionCode = version.toLong(),
            payload = ResourcePayload("resources/tts/device.js", "js", bytes.size.toLong(), hash, engineId),
        ), "b".repeat(40))
    }

    @Suppress("UNCHECKED_CAST")
    private fun publish(entry: ResourceEntry) {
        val field = ResourceRepository::class.java.getDeclaredField("mutableState").apply { isAccessible = true }
        val state = field.get(null) as MutableStateFlow<ResourceCenterState>
        state.value = ResourceCenterState(
            snapshot = ResourceSnapshot(catalogCommit = entry.catalogCommit,
                catalog = ResourceCatalog(1, commit, listOf(category)), entries = listOf(entry)),
            installations = ResourceInstallStore.load(), localStates = mapOf(resourceId to ResourceLocalState.PRESENT),
            loadedCategories = setOf("tts"), localChecked = true,
        )
    }

    private fun waitUntil(description: String, timeout: Long = 20_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeout
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(100)
        }
        fail("Timed out: $description")
    }

    private fun playerState(): Pair<Any, Long> {
        var result: Pair<Any, Long>? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val field = BaseReadAloudService::class.java.getDeclaredField("playbackStateOwner")
                .apply { isAccessible = true }
            val service = field.get(null) as HttpReadAloudService
            val getter = HttpReadAloudService::class.java.getDeclaredMethod("getExoPlayer")
                .apply { isAccessible = true }
            val player = getter.invoke(service) as ExoPlayer
            result = service to player.currentPosition
        }
        return requireNotNull(result)
    }

    /** The shell force-stops the process after this method and before verifyRecoveryAfterRestart. */
    @Test fun stageInterruptedInstall() = runBlocking {
        val phase = recoveryPhase()
        val id = "device_recovery_$phase"
        val resource = "tts.device_recovery_$phase"
        TtsEngineStore.engine(id)?.let { TtsEngineStore.deleteEngine(id) }
        ResourceInstallStore.save(ResourceInstallStore.load().filterNot { it.entry.item.id == resource })
        fun recoveryEntry(version: Int): ResourceEntry {
            val content = "// @name 恢复测试\n// @uuid $id\n// @version $version.0.0\nfunction voices() { return []; }"
            val bytes = content.toByteArray()
            val hash = ResourceProtocol.hash(bytes)
            File(ResourceFiles.root, "payload-cache/$hash").apply { parentFile!!.mkdirs(); writeBytes(bytes) }
            return ResourceEntry(category, commit, ResourceItem(
                id = resource, name = "恢复测试", author = "测试", versionCode = version.toLong(),
                payload = ResourcePayload("resources/tts/recovery.js", "js", bytes.size.toLong(), hash, id),
            ), "b".repeat(40))
        }
        val v1 = recoveryEntry(1)
        val first = ResourceInstaller.prepare(v1, File(ResourceFiles.root, "payload-cache/${v1.item.payload.sha256}"),
            null, ResourceInstallMode.NORMAL)
        first.commit()
        val old = ResourceInstallation(entry = v1, target = first.target, baseline = first.baseline)
        ResourceInstallStore.save(ResourceInstallStore.load().filterNot { it.entry.item.id == resource } + old)
        val v2 = recoveryEntry(2)
        val next = ResourceInstaller.prepare(v2, File(ResourceFiles.root, "payload-cache/${v2.item.payload.sha256}"),
            old, ResourceInstallMode.NORMAL)
        ResourceInstallStore.prepare(ResourceInstallation(entry = v2, target = next.target, baseline = next.baseline))
        if (phase == "after") next.commit()
        assertEquals(2L, ResourceInstallStore.pending()?.entry?.item?.versionCode)
    }

    @Test fun verifyRecoveryAfterRestart() {
        val phase = recoveryPhase()
        val resource = "tts.device_recovery_$phase"
        val state = ResourceRepository.state
        waitUntil("resource journal recovery") { state.value.localChecked && ResourceInstallStore.pending() == null }
        assertNull(ResourceInstallStore.pending())
        val record = ResourceInstallStore.load().single { it.entry.item.id == resource }
        assertEquals(if (phase == "after") 2L else 1L, record.entry.item.versionCode)
        assertEquals(record.baseline, ResourceInstaller.fingerprint(record))
        assertTrue(requireNotNull(TtsEngineStore.engine(record.target)).resourceManaged)
    }

    private fun recoveryPhase(): String {
        val phase = InstrumentationRegistry.getArguments().getString("phase")
        assumeTrue("requires a recovery phase", phase == "before" || phase == "after")
        return requireNotNull(phase)
    }

    @Test fun playbackContinuesDuringUpdateAndNewSynthesisUsesTheSelectedVoice() {
        val server = ServerSocket(0)
        val requests = CopyOnWriteArrayList<String>()
        val wave = wave()
        val serverThread = thread(name = "tts-update-test-server", isDaemon = true) {
            while (!server.isClosed) {
                val socket = try { server.accept() } catch (_: Exception) { break }
                socket.use {
                    val reader = it.getInputStream().bufferedReader()
                    val first = reader.readLine() ?: return@use
                    requests.add(first)
                    while (!reader.readLine().isNullOrEmpty()) Unit
                    val output = it.getOutputStream()
                    output.write("HTTP/1.1 200 OK\r\nContent-Type: audio/wav\r\nContent-Length: ${wave.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    output.write(wave)
                    output.flush()
                }
            }
        }
        try {
            val v1 = entry(1, script(1, server.localPort))
            val initial = runBlocking {
                ResourceInstaller.prepare(v1, File(ResourceFiles.root, "payload-cache/${v1.item.payload.sha256}"),
                    null, ResourceInstallMode.NORMAL).also { it.commit() }
            }
            ResourceInstallStore.save(listOf(ResourceInstallation(entry = v1, target = initial.target, baseline = initial.baseline)))
            TtsEngineStore.saveEngine(requireNotNull(TtsEngineStore.engine(engineId)).copy(enabled = true), false)
            val extra = JsonObject().apply { addProperty("marker", "keep") }
            TtsEngineStore.upsertVoiceList(engineId, listOf(TtsVoice("voice-a", "测试声音", extra = extra)), false)
            TtsEngineStore.saveRuntimeParams(engineId, 63, 72, 41)
            TtsEngineStore.selectEngine(engineId)

            val paragraphs = listOf("第一段用于测试朗读更新。", "第二段用于继续测试朗读。")
            val book = Book(bookUrl = "device-test-$suffix", name = "更新测试", author = "测试", totalChapterNum = 1)
            val chapter = TextChapter(BookChapter(bookUrl = book.bookUrl, title = "测试", index = 0),
                0, "测试", 1, false, paragraphs, false, false, null)
            val page = TextPage(text = paragraphs.joinToString("\n"), title = "测试")
            page.textChapter = chapter
            var position = 0
            paragraphs.forEachIndexed { index, text ->
                page.addLine(TextLine(text = text, paragraphNum = index + 1, chapterPosition = position, isParagraphEnd = true))
                position += text.length + 1
            }
            (chapter.pages as MutableList<TextPage>).add(page)
            chapter.isCompleted = true
            ReadBook.book = book
            ReadBook.curTextChapter = chapter
            ReadBook.chapterSize = 1
            ReadBook.durChapterIndex = 0
            ReadBook.durChapterPos = 0
            ReadAloud.play(appCtx, pageIndex = 0, engineVerified = true)
            waitUntil("v1 actual playback, requests=$requests") { BaseReadAloudService.isActualPlaybackConfirmed() }
            assertTrue(requests.any { it.contains("/v1?voice=voice-a&speed=63") })
            val before = playerState()

            val v2 = entry(2, script(2, server.localPort))
            publish(v2)
            ResourceRepository.install(v2)
            waitUntil("v2 resource installation") {
                ResourceRepository.state.value.tasks[resourceId]?.let { !it.running } == true
            }
            assertEquals("已加入资源库", ResourceRepository.state.value.tasks[resourceId]?.message)
            Thread.sleep(1800)
            val after = playerState()
            assertSame("updating must not recreate the playback service", before.first, after.first)
            assertTrue("audio must still be playing after the update", BaseReadAloudService.isActualPlaybackConfirmed())
            assertTrue("playback position must advance across the update: ${before.second} -> ${after.second}",
                after.second > before.second + 400)
            assertEquals("voice-a", TtsEngineStore.engine(engineId)?.activeVoiceId)

            // Explicitly rebuild after observing uninterrupted playback to exercise the new script.
            ReadAloud.play(appCtx, pageIndex = 0, forceRebuild = true, engineVerified = true)
            waitUntil("v2 synthesis with the same voice, requests=$requests") {
                requests.any { it.contains("/v2?voice=voice-a&speed=63") }
            }
            waitUntil("v2 actual playback") { BaseReadAloudService.isActualPlaybackConfirmed() }

            ReadAloud.pause(appCtx)
            waitUntil("playback paused") { BaseReadAloudService.pause && !BaseReadAloudService.isActualPlaybackConfirmed() }
            val paused = playerState()
            val v3 = entry(3, script(3, server.localPort))
            publish(v3)
            ResourceRepository.install(v3)
            waitUntil("v3 resource installation") {
                ResourceRepository.state.value.tasks[resourceId]?.let { !it.running } == true
            }
            assertEquals("已加入资源库", ResourceRepository.state.value.tasks[resourceId]?.message)
            Thread.sleep(1200)
            val stillPaused = playerState()
            assertSame(paused.first, stillPaused.first)
            assertTrue("update must preserve the paused state", BaseReadAloudService.pause)
            assertFalse(BaseReadAloudService.isActualPlaybackConfirmed())
            assertTrue("paused playback position must not move", kotlin.math.abs(paused.second - stillPaused.second) < 100)
            ReadAloud.resume(appCtx)
            waitUntil("resume after update") { BaseReadAloudService.isActualPlaybackConfirmed() }
            val resumed = playerState()
            Thread.sleep(1000)
            assertTrue("playback must advance after resume", playerState().second > resumed.second + 400)
            ReadAloud.play(appCtx, pageIndex = 0, forceRebuild = true, engineVerified = true)
            waitUntil("v3 synthesis with the same voice, requests=$requests") {
                requests.any { it.contains("/v3?voice=voice-a&speed=63") }
            }
        } finally {
            ReadAloud.stop(appCtx)
            server.close()
            serverThread.join(1000)
        }
    }

    private fun wave(): ByteArray {
        val sampleRate = 8_000
        val dataSize = sampleRate * 2 * 12
        val result = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        result.put("RIFF".toByteArray()).putInt(36 + dataSize).put("WAVEfmt ".toByteArray())
        result.putInt(16).putShort(1).putShort(1).putInt(sampleRate).putInt(sampleRate * 2)
            .putShort(2).putShort(16).put("data".toByteArray()).putInt(dataSize)
        repeat(dataSize / 2) { result.putShort((sin(it * 2.0 * Math.PI * 440 / sampleRate) * 3000).toInt().toShort()) }
        return result.array()
    }
}
