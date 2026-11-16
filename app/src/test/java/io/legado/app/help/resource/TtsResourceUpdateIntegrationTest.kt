package io.legado.app.help.resource

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.room.Room
import android.util.AtomicFile
import com.google.gson.JsonObject
import io.legado.app.data.AppDatabase
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.data.entities.BookCharacterTtsBinding
import io.legado.app.help.tts.ReadAloudTtsRouter
import io.legado.app.help.tts.TtsEngineStore
import io.legado.app.help.tts.TtsScriptEngineClient
import io.legado.app.help.tts.TtsVoice
import io.legado.app.help.tts.TtsVoicePlaybackParams
import io.legado.app.help.config.AppConfig
import io.legado.app.model.ReadAloud
import io.legado.app.ui.design.theme.InterfaceFontTestApplication
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Exercises the real resource installer, journal, engine store, Room cache and synthesis adapter. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = InterfaceFontTestApplication::class, shadows = [TestAtomicFileShadow::class])
class TtsResourceUpdateIntegrationTest {
    private val suffix = UUID.randomUUID().toString().replace("-", "")
    private val engineId = "test_tts_$suffix"
    private val resourceId = "tts.test_$suffix"
    private val category = ResourceCategory("tts", "朗读", "tts", "resources/tts/index.json", 1)
    private val commit = "a".repeat(40)

    @Before fun useIsolatedDatabase() {
        // Avoid the production onOpen seed callback, which uses Windows' file
        // separator under Robolectric; all DAOs still run against real Room tables.
        val delegateField = Class.forName("io.legado.app.data.AppDatabaseKt")
            .getDeclaredField("appDb\$delegate").apply { isAccessible = true }
        val delegate = delegateField.get(null)
        val value = delegate.javaClass.getDeclaredField("_value").apply { isAccessible = true }
        value.set(delegate, Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), AppDatabase::class.java,
        ).allowMainThreadQueries().build())
    }

    @After fun releaseDatabase() {
        appDb.close()
        ReadAloud.httpTtsEngineV2 = null
    }

    private fun script(version: Int, tail: String = "") = """
        // @name 更新测试
        // @uuid $engineId
        // @version $version.0.0
        // @audioType audio/wav
        // @capabilities synthesis_speed,synthesis_volume,synthesis_pitch
        function options() { return []; }
        function voices(options, ctx) { return []; }
        function synthesize(text, voice, params, options, ctx) {
            if (voice.id !== 'voice-a' || !voice.extra || voice.extra.marker !== 'keep') {
                throw new Error('voice and metadata lost');
            }
            return 'https://example.org/v$version/' + voice.id + '/' + params.speed + '/' + options.token;
        }
        $tail
    """.trimIndent()

    private fun entry(version: Int, text: String = script(version)): ResourceEntry {
        val bytes = text.toByteArray()
        val hash = ResourceProtocol.hash(bytes)
        val cache = File(ResourceFiles.root, "payload-cache/$hash")
        cache.parentFile!!.mkdirs()
        cache.writeBytes(bytes)
        return ResourceEntry(category, commit, ResourceItem(
            id = resourceId, name = "更新测试", author = "测试", versionCode = version.toLong(),
            payload = ResourcePayload("resources/tts/test.js", "js", bytes.size.toLong(), hash, engineId),
        ), "b".repeat(40))
    }

    @Suppress("UNCHECKED_CAST")
    private fun stateFlow(): MutableStateFlow<ResourceCenterState> =
        ResourceRepository::class.java.getDeclaredField("mutableState").let {
            it.isAccessible = true
            it.get(null) as MutableStateFlow<ResourceCenterState>
        }

    private fun publish(entry: ResourceEntry) {
        val snapshot = ResourceSnapshot(
            catalogCommit = entry.catalogCommit,
            catalog = ResourceCatalog(1, commit, listOf(category)),
            entries = listOf(entry),
        )
        stateFlow().value = ResourceCenterState(
            snapshot = snapshot, installations = ResourceInstallStore.load(),
            loadedCategories = setOf("tts"), localChecked = true,
        )
    }

    private suspend fun install(
        entry: ResourceEntry,
        mode: ResourceInstallMode = ResourceInstallMode.NORMAL,
        confirmed: TtsResourceConflict? = null,
    ): ResourceTask {
        ResourceRepository.dismissTask(resourceId)
        ResourceRepository.install(entry, mode, confirmedTtsConflict = confirmed)
        return withTimeout(20_000) {
            while (true) {
                val task = ResourceRepository.state.value.tasks[resourceId]
                if (task != null && !task.running) {
                    // Let the repository's completion callback release the task ID before a retry.
                    delay(100)
                    return@withTimeout task
                }
                delay(20)
            }
            @Suppress("UNREACHABLE_CODE") error("unreachable")
        }
    }

    private fun record() = ResourceInstallStore.load().single { it.entry.item.id == resourceId }

    @Test fun updateRetainsSelectedVoiceBindingAndPersonalSettings() = runBlocking {
        val v1 = entry(1)
        publish(v1)
        assertEquals("已加入资源库", install(v1).message)
        val initial = requireNotNull(TtsEngineStore.engine(engineId))
        TtsEngineStore.saveEngine(initial.copy(enabled = true, optionValues = mapOf("token" to "personal")), false)
        val extra = JsonObject().apply { addProperty("marker", "keep") }
        TtsEngineStore.upsertVoiceList(engineId, listOf(
            TtsVoice("voice-a", "A", extra = extra), TtsVoice("voice-b", "B"),
        ), false)
        TtsEngineStore.saveEngine(requireNotNull(TtsEngineStore.engine(engineId)).copy(
            activeVoiceId = "voice-a", disabledVoiceIds = listOf("voice-b"),
        ), false)
        val playback = TtsVoicePlaybackParams(1.2f, 0.8f, 1.1f)
        TtsEngineStore.saveRuntimeParams(engineId, 63, 72, 41)
        TtsEngineStore.saveVoiceParams(engineId, "voice-a", playback)
        TtsEngineStore.selectEngine(engineId)
        val binding = BookCharacterTtsBinding(workKey = "work_$suffix", targetId = 1,
            engineId = engineId, voiceId = "voice-a")
        appDb.bookCharacterDao.insertProfile(BookCharacterProfile(binding.workKey))
        appDb.bookCharacterDao.upsertTtsBinding(binding)
        assertFalse(ReadAloudTtsRouter.isBindingUnavailable(binding))
        val oldCacheKey = TtsScriptEngineClient.audioCacheKey(requireNotNull(TtsEngineStore.engine(engineId)), "测试")

        val v2 = entry(2)
        publish(v2)
        val result = install(v2)
        assertEquals("已加入资源库", result.message)
        assertEquals("task=$result engineHash=${ResourceInstaller.fingerprint(record())} expected=${v2.item.payload.sha256}",
            2L, record().entry.item.versionCode)
        val updated = requireNotNull(TtsEngineStore.engine(engineId))
        assertEquals(v2.item.payload.sha256, ResourceProtocol.hash(updated.script.toByteArray()))
        assertTrue(updated.enabled)
        assertEquals(engineId, TtsEngineStore.activeEngineId())
        assertEquals("voice-a", updated.activeVoiceId)
        assertEquals(listOf("voice-b"), updated.disabledVoiceIds)
        assertEquals(mapOf("token" to "personal"), updated.optionValues)
        assertEquals(63, updated.effectiveSpeed())
        assertEquals(72, updated.effectiveVolume())
        assertEquals(41, updated.effectivePitch())
        assertEquals(playback, updated.voiceParams["voice-a"])
        assertEquals(binding, appDb.bookCharacterDao.getTtsBindings(binding.workKey).single())
        assertFalse("the persisted character binding must still resolve", ReadAloudTtsRouter.isBindingUnavailable(binding))
        assertEquals("keep", updated.effectiveVoices().first { it.id == "voice-a" }.extra?.get("marker")?.asString)
        assertEquals("https://example.org/v2/voice-a/63/personal", TtsScriptEngineClient.buildSynthesisRequest(updated, "测试").url)
        assertNotEquals(oldCacheKey, TtsScriptEngineClient.audioCacheKey(updated, "测试"))
        assertEquals(updated.script, ReadAloud.httpTtsEngineV2?.script)
    }

    @Test fun localEditRequiresConfirmationAndRejectsStaleConfirmation() = runBlocking {
        val v1 = entry(1)
        publish(v1)
        assertEquals("已加入资源库", install(v1).message)
        val local1 = script(1, "// edited once")
        TtsEngineStore.saveEngine(requireNotNull(TtsEngineStore.engine(engineId)).copy(
            script = local1, enabled = true, activeVoiceId = "voice-a", optionValues = mapOf("token" to "personal"),
        ), false)
        TtsEngineStore.upsertVoiceList(engineId, listOf(TtsVoice("voice-a", "A")), false)
        val binding = BookCharacterTtsBinding(workKey = "work_$suffix", targetId = 1,
            engineId = engineId, voiceId = "voice-a")
        appDb.bookCharacterDao.insertProfile(BookCharacterProfile(binding.workKey))
        appDb.bookCharacterDao.upsertTtsBinding(binding)
        val v2 = entry(2)
        publish(v2)
        val conflict = install(v2)
        assertTrue(conflict.message, conflict.conflict)
        assertEquals(local1, requireNotNull(TtsEngineStore.engine(engineId)).script)
        assertEquals(1L, record().entry.item.versionCode)
        val beforeCancel = record()
        ResourceRepository.dismissTask(resourceId)
        assertNull(ResourceRepository.state.value.tasks[resourceId])
        val afterCancel = requireNotNull(TtsEngineStore.engine(engineId))
        assertEquals(local1, afterCancel.script)
        assertEquals("voice-a", afterCancel.activeVoiceId)
        assertEquals(mapOf("token" to "personal"), afterCancel.optionValues)
        assertEquals(binding, appDb.bookCharacterDao.getTtsBindings(binding.workKey).single())
        assertFalse(ReadAloudTtsRouter.isBindingUnavailable(binding))
        assertEquals(beforeCancel.target, record().target)
        assertEquals(beforeCancel.baseline, record().baseline)
        assertEquals(1L, record().entry.item.versionCode)
        val retried = install(v2)
        assertTrue(retried.message, retried.conflict)
        val confirmation = requireNotNull(retried.ttsConflict)
        val local2 = script(1, "// edited again after dialog")
        TtsEngineStore.saveEngine(requireNotNull(TtsEngineStore.engine(engineId)).copy(script = local2), false)
        TtsEngineStore.upsertVoiceList(engineId, listOf(TtsVoice("voice-a", "A")), false)
        val retry = install(v2, ResourceInstallMode.REPLACE, confirmation)
        assertTrue(retry.message, retry.conflict)
        assertEquals(local2, requireNotNull(TtsEngineStore.engine(engineId)).script)
        assertEquals(1L, record().entry.item.versionCode)
        val fresh = requireNotNull(retry.ttsConflict)
        assertEquals("已加入资源库", install(v2, ResourceInstallMode.REPLACE, fresh).message)
        assertEquals(2L, record().entry.item.versionCode)
        val afterReplacement = requireNotNull(TtsEngineStore.engine(engineId))
        assertEquals(script(2), afterReplacement.script)
        assertEquals("voice-a", afterReplacement.activeVoiceId)
        assertEquals(mapOf("token" to "personal"), afterReplacement.optionValues)
        assertEquals(binding, appDb.bookCharacterDao.getTtsBindings(binding.workKey).single())
        assertFalse(ReadAloudTtsRouter.isBindingUnavailable(binding))
        assertEquals(engineId, record().target)
        assertEquals(ResourceProtocol.hash(script(2).toByteArray()), record().baseline)
    }

    @Test fun installingACopyIsRejectedWithoutChangingTheEditedEngineOrReceipt() = runBlocking {
        val v1 = entry(1)
        publish(v1)
        assertEquals("已加入资源库", install(v1).message)
        val edited = script(1, "// local modification")
        TtsEngineStore.saveEngine(requireNotNull(TtsEngineStore.engine(engineId)).copy(script = edited), false)
        val v2 = entry(2)
        publish(v2)
        assertTrue(install(v2).conflict)
        val engineIds = TtsEngineStore.engines().map { it.id }.toSet()
        assertEquals("朗读脚本不支持安装副本", install(v2, ResourceInstallMode.COPY).message)
        assertEquals(edited, requireNotNull(TtsEngineStore.engine(engineId)).script)
        assertEquals(engineIds, TtsEngineStore.engines().map { it.id }.toSet())
        assertEquals(engineId, record().target)
        assertEquals(1L, record().entry.item.versionCode)
    }

    @Test fun updatingTheMultiRoleNarratorRefreshesItsPreparedEngine() = runBlocking {
        val v1 = entry(1)
        publish(v1)
        assertEquals("已加入资源库", install(v1).message)
        val narrator = requireNotNull(TtsEngineStore.engine(engineId)).copy(enabled = true)
        TtsEngineStore.saveEngine(narrator, false)
        TtsEngineStore.upsertVoiceList(engineId, listOf(TtsVoice("voice-a", "A")), false)
        val otherId = "other_$suffix"
        TtsEngineStore.saveEngine(narrator.copy(id = otherId), false)
        TtsEngineStore.selectEngine(otherId)
        AppConfig.readAloudScenarioMode = 1
        AppConfig.defaultNarratorTtsEngineId = engineId
        AppConfig.defaultNarratorTtsVoiceId = "voice-a"
        try {
            ReadAloud.refreshReadAloudClass()
            assertEquals(otherId, ReadAloud.preparedTtsEngineV2.id)
            assertEquals(engineId, ReadAloud.httpTtsEngineV2?.id)
            val v2 = entry(2)
            publish(v2)
            assertEquals("已加入资源库", install(v2).message)
            assertEquals(script(2), ReadAloud.httpTtsEngineV2?.script)
            assertEquals(otherId, ReadAloud.preparedTtsEngineV2.id)
        } finally {
            AppConfig.readAloudScenarioMode = 0
            AppConfig.defaultNarratorTtsEngineId = null
            AppConfig.defaultNarratorTtsVoiceId = null
            ReadAloud.refreshReadAloudClass()
        }
    }

    @Test fun explicitVoiceRefreshKeepsTheOldCatalogOnFailureAndReplacesItOnSuccess() = runBlocking {
        val v1 = entry(1)
        publish(v1)
        assertEquals("已加入资源库", install(v1).message)
        TtsEngineStore.saveEngine(requireNotNull(TtsEngineStore.engine(engineId)).copy(enabled = true), false)
        TtsEngineStore.upsertVoiceList(engineId, listOf(TtsVoice("voice-a", "旧声音")), false)
        val v2 = entry(2)
        publish(v2)
        assertEquals("已加入资源库", install(v2).message)
        assertTrue(runCatching { TtsEngineStore.ensureVoiceCatalog(engineId, forceRefresh = true) }.isFailure)
        assertEquals("旧声音", requireNotNull(TtsEngineStore.engine(engineId)).effectiveVoices().single().name)

        val v3Script = script(3).replace("function voices(options, ctx) { return []; }",
            "function voices(options, ctx) { return [{id:'voice-a',name:'新声音'}, {id:'voice-c',name:'新增声音'}]; }")
        val v3 = entry(3, v3Script)
        publish(v3)
        assertEquals("已加入资源库", install(v3).message)
        val refreshed = TtsEngineStore.ensureVoiceCatalog(engineId, forceRefresh = true)
        assertEquals(listOf("voice-a", "voice-c"), refreshed.effectiveVoices().map { it.id })
        assertEquals("新声音", refreshed.effectiveVoices().first().name)
        assertEquals("voice-a", refreshed.activeVoiceId)
    }

    @Test fun editAfterPreparationCannotBeOverwrittenAtCommit() = runBlocking {
        val v1 = entry(1)
        publish(v1)
        assertEquals("已加入资源库", install(v1).message)
        val v2 = entry(2)
        val candidate = ResourceInstaller.prepare(v2,
            File(ResourceFiles.root, "payload-cache/${v2.item.payload.sha256}"), record(), ResourceInstallMode.NORMAL)
        val edited = script(1, "// edited during installation")
        TtsEngineStore.saveEngine(requireNotNull(TtsEngineStore.engine(engineId)).copy(script = edited), false)
        val failure = runCatching { candidate.commit() }.exceptionOrNull()
        assertTrue(failure is ResourceConflict)
        assertNotNull((failure as ResourceConflict).ttsConflict)
        assertEquals(edited, requireNotNull(TtsEngineStore.engine(engineId)).script)
        assertEquals(1L, record().entry.item.versionCode)
    }

    @Test fun cancelDuringDownloadRemovesPartialFileAndKeepsTheInstalledVersion() = runBlocking {
        val v1 = entry(1)
        publish(v1)
        assertEquals("已加入资源库", install(v1).message)
        val payload = (script(2) + "\n// " + "x".repeat(100_000)).toByteArray()
        val v2 = entry(2, payload.toString(Charsets.UTF_8))
        File(ResourceFiles.root, "payload-cache/${v2.item.payload.sha256}").delete()
        val secondRead = CountDownLatch(1)
        val releaseRead = CountDownLatch(1)
        val source = object : Source {
            var offset = 0
            override fun read(sink: Buffer, byteCount: Long): Long {
                if (offset > 0) {
                    secondRead.countDown()
                    check(releaseRead.await(10, TimeUnit.SECONDS))
                }
                if (offset == payload.size) return -1
                val count = minOf(byteCount.toInt(), payload.size - offset)
                sink.write(payload, offset, count)
                offset += count
                return count.toLong()
            }
            override fun timeout(): Timeout = Timeout.NONE
            override fun close() = releaseRead.countDown()
        }.buffer()
        val fakeClient = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(object : ResponseBody() {
                    override fun contentType(): MediaType? = null
                    override fun contentLength() = payload.size.toLong()
                    override fun source(): BufferedSource = source
                }).build()
        }.build()
        val previousClient = swapClientForTest(fakeClient)
        try {
            publish(v2)
            ResourceRepository.install(v2)
            assertTrue("download never reached its second read", secondRead.await(10, TimeUnit.SECONDS))
            assertTrue(File(ResourceFiles.root, "payload-cache").listFiles().orEmpty().any { it.name.endsWith(".part") })
            ResourceRepository.cancel(resourceId)
            releaseRead.countDown()
            withTimeout(10_000) {
                while (ResourceRepository.state.value.tasks[resourceId]?.running != false) delay(20)
            }
            assertEquals("已取消", ResourceRepository.state.value.tasks[resourceId]?.message)
            assertFalse(File(ResourceFiles.root, "payload-cache").listFiles().orEmpty().any { it.name.endsWith(".part") })
            assertFalse(File(ResourceFiles.root, "payload-cache/${v2.item.payload.sha256}").exists())
            assertEquals(1L, record().entry.item.versionCode)
            assertEquals(script(1), requireNotNull(TtsEngineStore.engine(engineId)).script)
        } finally {
            releaseRead.countDown()
            ResourceRepository.cancel(resourceId)
            swapClientForTest(previousClient)
        }
    }

    /** Replace the private final client only inside the JVM test process. */
    private fun swapClientForTest(replacement: OkHttpClient): OkHttpClient {
        val clientField = ResourceRepository::class.java.getDeclaredField("client").apply { isAccessible = true }
        val unsafeType = Class.forName("sun.misc.Unsafe")
        val unsafe = unsafeType.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        val base = unsafeType.getMethod("staticFieldBase", java.lang.reflect.Field::class.java).invoke(unsafe, clientField)
        val offset = unsafeType.getMethod("staticFieldOffset", java.lang.reflect.Field::class.java).invoke(unsafe, clientField) as Long
        val previous = unsafeType.getMethod("getObjectVolatile", Any::class.java, Long::class.javaPrimitiveType)
            .invoke(unsafe, base, offset) as OkHttpClient
        unsafeType.getMethod("putObjectVolatile", Any::class.java, Long::class.javaPrimitiveType, Any::class.java)
            .invoke(unsafe, base, offset, replacement)
        return previous
    }

    @Test fun interruptionBeforeAndAfterCommitRecoversTheMatchingReceipt() = runBlocking {
        val v1 = entry(1)
        publish(v1)
        assertEquals("已加入资源库", install(v1).message)
        val old = record()
        val v2 = entry(2)
        val payload = File(ResourceFiles.root, "payload-cache/${v2.item.payload.sha256}")
        val candidate = ResourceInstaller.prepare(v2, payload, old, ResourceInstallMode.NORMAL)
        val pending = ResourceInstallation(entry = v2, target = candidate.target, baseline = candidate.baseline)
        val recover = ResourceRepository::class.java.getDeclaredMethod("recoverInstallationLocked", List::class.java)
            .apply { isAccessible = true }
        ResourceInstallStore.prepare(pending)
        recover.invoke(ResourceRepository, ResourceInstallStore.load())
        assertNull(ResourceInstallStore.pending())
        assertEquals(1L, record().entry.item.versionCode)
        assertEquals(old.baseline, ResourceInstaller.fingerprint(old))

        ResourceInstallStore.prepare(pending)
        candidate.commit()
        assertEquals("candidate commit must update the engine before recovery", candidate.baseline,
            ResourceInstaller.fingerprint(pending))
        assertEquals("journal must contain the new version", 2L, ResourceInstallStore.pending()?.entry?.item?.versionCode)
        recover.invoke(ResourceRepository, ResourceInstallStore.load())
        assertNull(ResourceInstallStore.pending())
        assertEquals(2L, record().entry.item.versionCode)
        assertEquals(candidate.baseline, ResourceInstaller.fingerprint(record()))
        assertTrue(requireNotNull(TtsEngineStore.engine(engineId)).resourceManaged)
        recover.invoke(ResourceRepository, ResourceInstallStore.load())
        assertEquals(2L, record().entry.item.versionCode)
    }
}

/** Android's rename-over-existing semantics differ from Windows File.renameTo in Robolectric. */
@Implements(AtomicFile::class)
class TestAtomicFileShadow {
    @RealObject private lateinit var actual: AtomicFile

    @Implementation fun startWrite(): FileOutputStream = FileOutputStream(actual.baseFile)
    @Implementation fun finishWrite(stream: FileOutputStream) = stream.close()
    @Implementation fun failWrite(stream: FileOutputStream) = stream.close()
    @Implementation fun openRead(): FileInputStream = FileInputStream(actual.baseFile)
    @Implementation fun delete() { actual.baseFile.delete() }
}
