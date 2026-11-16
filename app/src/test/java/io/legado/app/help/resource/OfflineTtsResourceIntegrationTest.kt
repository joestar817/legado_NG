package io.legado.app.help.resource

import android.net.Uri
import android.os.Build
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.constant.PreferKey
import io.legado.app.data.AppDatabase
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.data.entities.BookCharacterTtsBinding
import io.legado.app.data.entities.TtsEngineRuntimeEntity
import io.legado.app.help.tts.OfflineUpdateOsShadow
import io.legado.app.help.tts.ReadAloudTtsRouter
import io.legado.app.help.tts.TtsEngineStore
import io.legado.app.help.tts.TtsVoicePlaybackParams
import io.legado.app.help.tts.packages.MicrosoftTtsBundleManager
import io.legado.app.model.ReadAloud
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.design.theme.InterfaceFontTestApplication
import io.legado.app.utils.putPrefString
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.*
import okio.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import splitties.init.appCtx
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Actual signed bundles + real repository journal/Room. No native synthesis or user's device data. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = InterfaceFontTestApplication::class,
    shadows = [OfflineUpdateOsShadow::class, TestAtomicFileShadow::class])
class OfflineTtsResourceIntegrationTest {
    private val engineId = TtsEngineStore.MICROSOFT_OFFLINE_ID
    private val resourceId = "tts.microsoft-offline"
    private val kept = "zh-CN-YunxiNeural"
    private val other = "zh-CN-XiaoxiaoNeural"
    private val removed = "zh-TW-YunJheNeural"
    private lateinit var oldZip: File
    private lateinit var newZip: File
    private var arch: String? = null

    @Before fun isolatedState() {
        val old = System.getProperty("offline.resource.oldBundle")
        val new = System.getProperty("offline.resource.newBundle")
        assumeTrue("Real bundle fixtures are optional", old != null && new != null)
        oldZip = File(old!!); newZip = File(new!!)
        arch = System.getProperty("os.arch")
        System.setProperty("os.arch", "aarch64")
        ReflectionHelpers.setStaticField(Build::class.java, "SUPPORTED_ABIS", arrayOf("arm64-v8a"))
        val delegate = Class.forName("io.legado.app.data.AppDatabaseKt")
            .getDeclaredField("appDb\$delegate").apply { isAccessible = true }.get(null)
        delegate.javaClass.getDeclaredField("_value").apply { isAccessible = true }.set(delegate,
            Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
                .allowMainThreadQueries().build())
        appDb.openHelper.writableDatabase
        appCtx.putPrefString(PreferKey.ttsEngineV2SettingsJson, "[]")
        TtsEngineStore::class.java.getDeclaredField("engineSnapshotCache").apply { isAccessible = true }.set(null, null)
        TtsEngineStore::class.java.getDeclaredField("offlineCatalogRecovered").apply { isAccessible = true }.setBoolean(null, false)
        ReadAloud.httpTtsEngineV2 = null
        ReflectionHelpers.setStaticField(BaseReadAloudService::class.java, "isRun", false)
        ReflectionHelpers.setStaticField(BaseReadAloudService::class.java, "pause", true)
    }

    @After fun releaseState() {
        if (arch == null) return
        System.setProperty("os.arch", arch)
        ResourceRepository.cancel(resourceId)
        appDb.close()
        ReadAloud.httpTtsEngineV2 = null
        ReflectionHelpers.setStaticField(BaseReadAloudService::class.java, "isRun", false)
        ReflectionHelpers.setStaticField(BaseReadAloudService::class.java, "pause", true)
        TtsEngineStore::class.java.getDeclaredField("engineSnapshotCache").apply { isAccessible = true }.set(null, null)
        TtsEngineStore::class.java.getDeclaredField("offlineCatalogRecovered").apply { isAccessible = true }.setBoolean(null, false)
    }

    private fun entry(file: File, version: Long) = ResourceEntry(
        ResourceCategory("tts", "朗读引擎", "tts", "resources/tts/index.json", 1), "a".repeat(40),
        ResourceItem(id = resourceId, name = "微软中文离线语音", author = "测试", versionCode = version,
            payload = ResourcePayload("resources/tts/offline/voices.zip", "zip", file.length(), ResourceFiles.hash(file), engineId,
                release = ResourceReleaseAsset("test-$version", "voices.zip")),
            requirements = ResourceRequirements(capabilities = listOf(MICROSOFT_OFFLINE_TTS_CAPABILITY))), "b".repeat(40))

    private fun cache(entry: ResourceEntry, zip: File) {
        File(ResourceFiles.root, "payload-cache/${entry.item.payload.sha256}").also { it.parentFile!!.mkdirs(); zip.copyTo(it, true) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun publish(entry: ResourceEntry) {
        val flow = ResourceRepository::class.java.getDeclaredField("mutableState").apply { isAccessible = true }
            .get(null) as MutableStateFlow<ResourceCenterState>
        flow.value = ResourceCenterState(snapshot = ResourceSnapshot(catalogCommit = entry.catalogCommit,
            catalog = ResourceCatalog(1, entry.sourceCommit, listOf(entry.category)), entries = listOf(entry)),
            installations = ResourceInstallStore.load(), loadedCategories = setOf("tts"), localChecked = true)
    }

    private suspend fun waitTask(): ResourceTask = withTimeout(90_000) {
        while (true) {
            val task = ResourceRepository.state.value.tasks[resourceId]
            if (task != null && !task.running) { delay(100); return@withTimeout task }
            delay(20)
        }
        @Suppress("UNREACHABLE_CODE") error("unreachable")
    }

    private suspend fun install(entry: ResourceEntry, confirmed: TtsResourceConflict? = null): ResourceTask {
        publish(entry)
        ResourceRepository.install(entry, if (confirmed == null) ResourceInstallMode.NORMAL else ResourceInstallMode.REPLACE,
            confirmedTtsConflict = confirmed)
        return waitTask()
    }

    private fun record() = ResourceInstallStore.load().single { it.entry.item.id == resourceId }
    private suspend fun localImport(file: File) {
        MicrosoftTtsBundleManager.inspect(Uri.fromFile(file)).use { it.commit() }
        OfflineTtsResourceInstaller.refreshEngine()
    }

    private fun personalized(): BookCharacterTtsBinding {
        TtsEngineStore.saveEngine(requireNotNull(TtsEngineStore.engine(engineId)).copy(enabled = true,
            name = "个人名称", activeVoiceId = kept, disabledVoiceIds = listOf(other), defaultSpeed = 37), false)
        appDb.ttsEngineRuntimeDao.upsert(TtsEngineRuntimeEntity(engineId = engineId, speed = 63, volume = 72, pitch = 41))
        TtsEngineStore.saveVoiceParams(engineId, kept, TtsVoicePlaybackParams(1.2f, 0.8f, 1.1f))
        val binding = BookCharacterTtsBinding(workKey = "resource_update", targetId = 1, engineId = engineId, voiceId = kept)
        appDb.bookCharacterDao.insertProfile(BookCharacterProfile(binding.workKey))
        appDb.bookCharacterDao.upsertTtsBinding(binding)
        return binding
    }

    private fun assertPersonal(binding: BookCharacterTtsBinding) {
        val engine = requireNotNull(TtsEngineStore.engine(engineId))
        assertEquals(setOf(kept, other), engine.effectiveVoices().map { it.id }.toSet())
        assertEquals(kept, engine.activeVoiceId); assertEquals(listOf(other), engine.disabledVoiceIds)
        assertEquals("个人名称", engine.name); assertEquals(37, engine.defaultSpeed)
        assertEquals(63, engine.effectiveSpeed()); assertEquals(72, engine.effectiveVolume()); assertEquals(41, engine.effectivePitch())
        assertEquals(TtsVoicePlaybackParams(1.2f, 0.8f, 1.1f), engine.voiceParams[kept])
        assertEquals(binding, appDb.bookCharacterDao.getTtsBindings(binding.workKey).single())
        assertFalse(ReadAloudTtsRouter.isBindingUnavailable(binding))
        assertTrue(ReadAloudTtsRouter.isBindingUnavailable(binding.copy(voiceId = removed)))
    }

    private fun swapClient(replacement: OkHttpClient): OkHttpClient {
        ResourceRepository.state // Initialize the singleton before Unsafe reads its static fields.
        val field = ResourceRepository::class.java.getDeclaredField("offlineTtsClient").apply { isAccessible = true }
        val type = Class.forName("sun.misc.Unsafe")
        val unsafe = type.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        val base = type.getMethod("staticFieldBase", java.lang.reflect.Field::class.java).invoke(unsafe, field)
        val offset = type.getMethod("staticFieldOffset", java.lang.reflect.Field::class.java).invoke(unsafe, field) as Long
        val old = type.getMethod("getObjectVolatile", Any::class.java, Long::class.javaPrimitiveType).invoke(unsafe, base, offset) as OkHttpClient
        type.getMethod("putObjectVolatile", Any::class.java, Long::class.javaPrimitiveType, Any::class.java).invoke(unsafe, base, offset, replacement)
        return old
    }

    private fun client(source: () -> BufferedSource, length: Long, observed: (String) -> Unit = {}) = OkHttpClient.Builder().addInterceptor { chain ->
        observed(chain.request().url.toString())
        val buffered = source()
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(object : ResponseBody() {
                override fun contentType(): MediaType? = null
                override fun contentLength() = length
                override fun source() = buffered
            }).build()
    }.build()

    @Test fun downloadInstallAndFourToTwoUpdatePreservePersonalStateAndPlaybackFlags() = runBlocking {
        val before = entry(oldZip, 1)
        var url: String? = null
        val previous = swapClient(client({ oldZip.source().buffer() }, oldZip.length()) { url = it })
        try { assertEquals("离线语音已安装", install(before).message) } finally { swapClient(previous) }
        assertTrue(requireNotNull(url).contains("/releases/download/test-1/voices.zip"))
        assertEquals(4, TtsEngineStore.voices(engineId).size)
        val binding = personalized()
        ReadAloud.httpTtsEngineV2 = TtsEngineStore.engine(engineId)
        ReflectionHelpers.setStaticField(BaseReadAloudService::class.java, "isRun", true)
        val newer = entry(newZip, 2); cache(newer, newZip)
        assertEquals("离线语音已安装", install(newer).message)
        assertPersonal(binding); assertTrue(BaseReadAloudService.isRun); assertTrue(BaseReadAloudService.pause)
        assertEquals(2L, record().entry.item.versionCode)
        assertEquals(ResourceLocalState.PRESENT, ResourceInstaller.localState(record()))
        assertEquals("离线语音已安装", install(newer).message)
        assertEquals(1, ResourceInstallStore.load().size)
        assertPersonal(binding)
    }

    @Test fun localConflictCancelThenOverwriteAndIdenticalAssociation() = runBlocking {
        localImport(oldZip)
        val baseline = MicrosoftTtsBundleManager.installedFingerprint()
        val binding = personalized()
        val incoming = entry(newZip, 1); cache(incoming, newZip)
        val conflict = install(incoming)
        assertTrue(conflict.conflict); assertNotNull(conflict.ttsConflict)
        ResourceRepository.dismissTask(resourceId)
        assertEquals(baseline, MicrosoftTtsBundleManager.installedFingerprint())
        assertTrue(ResourceInstallStore.load().isEmpty()); assertEquals(4, TtsEngineStore.voices(engineId).size)
        assertEquals("离线语音已安装", install(incoming, conflict.ttsConflict).message)
        assertPersonal(binding)
        ResourceInstallStore.save(emptyList())
        assertEquals("离线语音已安装", install(incoming).message)
        assertEquals(1, ResourceInstallStore.load().size)
    }

    @Test fun downloadCancellationAndCorruptionLeaveOldBundleAndReceiptUntouched() = runBlocking {
        val before = entry(oldZip, 1); cache(before, oldZip)
        assertEquals("离线语音已安装", install(before).message)
        val old = record(); val next = entry(newZip, 2)
        val started = CountDownLatch(1); val unblock = CountDownLatch(1)
        val stream = object : Source {
            var first = true
            override fun read(sink: Buffer, byteCount: Long): Long {
                if (first) { first = false; sink.write(ByteArray(4096)); return 4096 }
                started.countDown(); check(unblock.await(15, TimeUnit.SECONDS)); return -1
            }
            override fun timeout() = Timeout.NONE
            override fun close() { unblock.countDown() }
        }.buffer()
        val previous = swapClient(client({ stream }, newZip.length()))
        try {
            publish(next); ResourceRepository.install(next)
            assertTrue(started.await(15, TimeUnit.SECONDS)); ResourceRepository.cancel(resourceId); unblock.countDown()
            assertEquals("已取消", waitTask().message)
            assertEquals(old, record()); assertEquals(old.baseline, MicrosoftTtsBundleManager.installedFingerprint())
            assertFalse(File(ResourceFiles.root, "payload-cache").listFiles().orEmpty().any { it.name.endsWith(".part") })
            swapClient(client({ Buffer().writeUtf8("broken") }, 6))
            assertFalse(install(next).conflict)
            assertEquals(old, record()); assertEquals(old.baseline, MicrosoftTtsBundleManager.installedFingerprint())
            assertNull(ResourceInstallStore.pending())
        } finally { unblock.countDown(); swapClient(previous) }
    }

    @Test fun interruptedReceiptRecoversOnlyAfterPackageCommit() = runBlocking {
        val before = entry(oldZip, 1); cache(before, oldZip)
        assertEquals("离线语音已安装", install(before).message)
        val old = record(); val binding = personalized(); val next = entry(newZip, 2)
        val candidate = ResourceInstaller.prepare(next, newZip, old, ResourceInstallMode.NORMAL)
        val pending = ResourceInstallation(entry = next, target = candidate.target, baseline = candidate.baseline)
        val recover = ResourceRepository::class.java.getDeclaredMethod("recoverInstallationLocked", List::class.java).apply { isAccessible = true }
        try {
            ResourceInstallStore.prepare(pending)
            recover.invoke(ResourceRepository, ResourceInstallStore.load())
            assertEquals(old, record()); assertNull(ResourceInstallStore.pending())
            ResourceInstallStore.prepare(pending); candidate.commit()
            assertEquals(old, record())
            recover.invoke(ResourceRepository, ResourceInstallStore.load())
            assertEquals(2L, record().entry.item.versionCode); assertNull(ResourceInstallStore.pending())
            assertPersonal(binding)
            recover.invoke(ResourceRepository, ResourceInstallStore.load())
            assertEquals(2L, record().entry.item.versionCode)
        } finally { candidate.discard() }
    }
}
