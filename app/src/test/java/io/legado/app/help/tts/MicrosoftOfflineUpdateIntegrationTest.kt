package io.legado.app.help.tts

import android.system.Os
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.constant.PreferKey
import io.legado.app.data.AppDatabase
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.data.entities.BookCharacterTtsBinding
import io.legado.app.data.entities.TtsEngineRuntimeEntity
import io.legado.app.help.config.AppConfig
import io.legado.app.help.tts.packages.MicrosoftTtsBundleManager
import io.legado.app.help.tts.packages.MicrosoftTtsCatalog
import io.legado.app.help.tts.packages.MicrosoftTtsPackageStore
import io.legado.app.help.tts.packages.encodeMicrosoftOfflineGraph
import io.legado.app.model.ReadAloud
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.design.theme.InterfaceFontTestApplication
import io.legado.app.utils.putPrefString
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.util.ReflectionHelpers
import splitties.init.appCtx
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = InterfaceFontTestApplication::class, shadows = [OfflineUpdateOsShadow::class])
class MicrosoftOfflineUpdateIntegrationTest {
    private val id = TtsEngineStore.MICROSOFT_OFFLINE_ID
    private val kept = TtsVoice("zh-CN-YunxiNeural", "云希")
    private val other = TtsVoice("zh-CN-XiaoxiaoNeural", "晓晓")
    private val removed = TtsVoice("zh-CN-Huihui-Apollo", "慧慧")
    private val personal = TtsVoicePlaybackParams(speedRatio = 1.2f, volumeGain = 1.1f, pitchRatio = 0.9f)

    @Before fun isolatedDatabaseAndState() {
        val delegate = Class.forName("io.legado.app.data.AppDatabaseKt")
            .getDeclaredField("appDb\$delegate").apply { isAccessible = true }.get(null)
        delegate.javaClass.getDeclaredField("_value").apply { isAccessible = true }.set(delegate,
            Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
                .allowMainThreadQueries().build())
        appDb.openHelper.writableDatabase
        appCtx.putPrefString(PreferKey.ttsEngineV2SettingsJson, "[]")
        resetSnapshots()
        ReadAloud.httpTtsEngineV2 = null
        ReflectionHelpers.setStaticField(BaseReadAloudService::class.java, "isRun", false)
        ReflectionHelpers.setStaticField(BaseReadAloudService::class.java, "pause", true)
    }

    private fun resetSnapshots() {
        TtsEngineStore::class.java.getDeclaredField("engineSnapshotCache").apply { isAccessible = true }.set(null, null)
        TtsEngineStore::class.java.getDeclaredField("offlineCatalogRecovered").apply { isAccessible = true }.setBoolean(null, false)
    }

    @After fun releaseState() {
        appDb.close()
        ReadAloud.httpTtsEngineV2 = null
        ReflectionHelpers.setStaticField(BaseReadAloudService::class.java, "isRun", false)
        ReflectionHelpers.setStaticField(BaseReadAloudService::class.java, "pause", true)
        resetSnapshots()
    }

    private fun catalog(voices: List<TtsVoice>, revision: Char = '1') = MicrosoftTtsCatalog(
        encodeMicrosoftOfflineGraph(mapOf(MicrosoftTtsPackageStore.ENGINE to revision.toString().repeat(64))), voices, true,
    )

    private fun personalize(): BookCharacterTtsBinding {
        val engine = requireNotNull(TtsEngineStore.engine(id))
        TtsEngineStore.saveEngine(engine.copy(activeVoiceId = kept.id, disabledVoiceIds = listOf(other.id),
            name = "个人离线语音", defaultSpeed = 39, defaultVolume = 68, defaultPitch = 42), false)
        appDb.ttsEngineRuntimeDao.upsert(TtsEngineRuntimeEntity(engineId = id, speed = 63, volume = 72, pitch = 41))
        TtsEngineStore.saveVoiceParams(id, kept.id, personal)
        appCtx.putPrefString(PreferKey.ttsEngineV2ActiveId, id)
        AppConfig.defaultNarratorTtsEngineId = id
        AppConfig.defaultNarratorTtsVoiceId = kept.id
        val binding = BookCharacterTtsBinding(workKey = "offline_update", targetId = 1, engineId = id, voiceId = kept.id)
        appDb.bookCharacterDao.insertProfile(BookCharacterProfile(binding.workKey))
        appDb.bookCharacterDao.upsertTtsBinding(binding)
        return binding
    }

    private fun assertPersonalState(binding: BookCharacterTtsBinding) {
        val updated = requireNotNull(TtsEngineStore.engine(id))
        assertEquals(kept.id, updated.activeVoiceId)
        assertEquals("个人离线语音", updated.name)
        assertEquals(listOf(other.id), updated.disabledVoiceIds)
        assertEquals(39, updated.defaultSpeed)
        assertEquals(68, updated.defaultVolume)
        assertEquals(42, updated.defaultPitch)
        assertEquals(63, updated.effectiveSpeed())
        assertEquals(72, updated.effectiveVolume())
        assertEquals(41, updated.effectivePitch())
        assertEquals(personal, updated.voiceParams[kept.id])
        assertEquals(id, TtsEngineStore.activeEngineId())
        assertEquals(id, AppConfig.defaultNarratorTtsEngineId)
        assertEquals(kept.id, AppConfig.defaultNarratorTtsVoiceId)
        assertEquals(binding, appDb.bookCharacterDao.getTtsBindings(binding.workKey).single())
        assertFalse(ReadAloudTtsRouter.isBindingUnavailable(binding))
    }

    @Test fun replacementKeepsBindingsParametersAndPreparedPlaybackWhileRemovingOldVoices() {
        TtsEngineStore.registerMicrosoftOffline(catalog(listOf(kept, other, removed)))
        val binding = personalize()
        for (paused in listOf(false, true)) {
            ReadAloud.httpTtsEngineV2 = TtsEngineStore.engine(id)
            ReflectionHelpers.setStaticField(BaseReadAloudService::class.java, "isRun", true)
            ReflectionHelpers.setStaticField(BaseReadAloudService::class.java, "pause", paused)
            val updated = TtsEngineStore.registerMicrosoftOffline(catalog(listOf(other, kept), '2'))
            assertEquals(setOf(kept.id, other.id), updated.effectiveVoices().map { it.id }.toSet())
            assertNull(TtsEngineStore.voice(id, removed.id))
            assertPersonalState(binding)
            assertTrue(BaseReadAloudService.isRun)
            assertEquals(paused, BaseReadAloudService.pause)
            assertEquals(updated.offlineRevision, ReadAloud.httpTtsEngineV2?.offlineRevision)
            assertEquals(setOf(kept.id, other.id), ReadAloud.httpTtsEngineV2?.effectiveVoices()?.map { it.id }?.toSet())
        }
    }

    @Test fun disabledEngineRemainsDisabledAndRemovedBindingBecomesUnavailable() {
        TtsEngineStore.registerMicrosoftOffline(catalog(listOf(kept, removed)))
        val engine = requireNotNull(TtsEngineStore.engine(id))
        TtsEngineStore.saveEngine(engine.copy(enabled = false, activeVoiceId = removed.id), false)
        val updated = TtsEngineStore.registerMicrosoftOffline(catalog(listOf(kept), '2'))
        assertFalse(updated.enabled)
        assertEquals(kept.id, updated.activeVoiceId)
        TtsEngineStore.saveEngine(updated.copy(enabled = true), false)
        assertTrue(ReadAloudTtsRouter.isBindingUnavailable(BookCharacterTtsBinding(engineId = id, voiceId = removed.id)))
        assertFalse(ReadAloudTtsRouter.isBindingUnavailable(BookCharacterTtsBinding(engineId = id, voiceId = kept.id)))
    }

    @Test fun committedThirteenToFourUpdateRecoversBeforeOrAfterCacheProjection() {
        val oldBundle = System.getProperty("offline.test.oldBundle")
        val newBundle = System.getProperty("offline.test.newBundle")
        assumeTrue("Local real-model regression fixtures are optional", oldBundle != null && newBundle != null)
        val root = File(appCtx.noBackupFilesDir, "tts-offline-microsoft")
        val device = MicrosoftTtsPackageStore.Device("arm64-v8a", 35, 4)
        val store = MicrosoftTtsPackageStore(root)
        store.inspect(File(oldBundle!!), device).use { store.install(it, it.trustRequired.values.toSet()) }
        fun current(): MicrosoftTtsCatalog {
            val graph = store.latestGraph(device)
            return MicrosoftTtsCatalog(encodeMicrosoftOfflineGraph(graph), store.catalogVoices(graph).map {
                TtsVoice(it.voiceId, it.name, it.locale, it.gender)
            }, true)
        }
        val oldCatalog = current()
        TtsEngineStore.registerMicrosoftOffline(oldCatalog)
        assertEquals(13, TtsEngineStore.voices(id).size)
        val binding = personalize()
        store.inspect(File(newBundle!!), device).use { store.install(it, it.trustRequired.values.toSet()) }
        val replacement = current()
        assertEquals(4, replacement.voices.size)
        val arch = System.getProperty("os.arch")
        try {
            for (cacheAlreadyUpdated in listOf(false, true)) {
                // The package commit succeeded, but its app callback never ran (or stopped after Room).
                System.setProperty("os.arch", arch)
                TtsEngineStore.registerMicrosoftOffline(oldCatalog)
                if (cacheAlreadyUpdated) TtsEngineStore.upsertVoiceList(id, replacement.voices, false)
                System.setProperty("os.arch", "aarch64")
                resetSnapshots()
                assertEquals(4, requireNotNull(TtsEngineStore.engine(id)).effectiveVoices().size)
                assertEquals(4, TtsEngineStore.voices(id).size)
                assertEquals(replacement.revision, TtsEngineStore.engine(id)?.offlineRevision)
                assertPersonalState(binding)
                assertEquals(4, MicrosoftTtsBundleManager.installed()?.voices?.size)
            }
        } finally {
            System.setProperty("os.arch", arch)
        }
    }
}

@Implements(Os::class)
class OfflineUpdateOsShadow {
    companion object {
        @JvmStatic @Implementation fun sysconf(name: Int): Long = 4096L
    }
}
