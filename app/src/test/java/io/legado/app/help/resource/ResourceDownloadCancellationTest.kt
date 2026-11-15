package io.legado.app.help.resource

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.help.tts.TtsEngineStore
import io.legado.app.ui.design.theme.InterfaceFontTestApplication
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Real HTTP body stall: cancellation must close the socket, not wait for the read timeout. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = InterfaceFontTestApplication::class)
class ResourceDownloadCancellationTest {
    @Test fun cancellingAfterHeadersInterruptsAStalledPayloadRead() = runBlocking {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val accepted = AtomicReference<Socket>()
        val bodyStarted = CountDownLatch(1)
        val peerClosed = CountDownLatch(1)
        val serverThread = Executors.newSingleThreadExecutor()
        serverThread.submit {
            server.accept().use { socket ->
                accepted.set(socket)
                val input = socket.getInputStream().bufferedReader(Charsets.US_ASCII)
                while (!input.readLine().isNullOrEmpty()) Unit
                socket.getOutputStream().apply {
                    write("HTTP/1.1 200 OK\r\nContent-Length: 131072\r\nContent-Type: application/zip\r\n\r\n".toByteArray())
                    write(ByteArray(32768))
                    flush()
                }
                bodyStarted.countDown()
                if (input.read() == -1) peerClosed.countDown()
            }
        }
        val resourceId = "tts.cancel-body-test"
        val entry = ResourceEntry(ResourceCategory("tts", "朗读引擎", "tts", "resources/tts/index.json", 1),
            "a".repeat(40), ResourceItem(id = resourceId, name = "取消测试", author = "测试", versionCode = 1,
                payload = ResourcePayload("resources/tts/test.zip", "zip", 131072, "f".repeat(64),
                    TtsEngineStore.MICROSOFT_OFFLINE_ID, release = ResourceReleaseAsset("test", "voices.zip")),
                requirements = ResourceRequirements(capabilities = listOf(MICROSOFT_OFFLINE_TTS_CAPABILITY))), "b".repeat(40))
        val client = OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().url("http://127.0.0.1:${server.localPort}/bundle.zip").build())
        }.build()
        ResourceRepository.state // Initialize static clients before the test-only replacement.
        val oldClient = swapClient(client)
        val arch = System.getProperty("os.arch")
        System.setProperty("os.arch", "aarch64")
        try {
            @Suppress("UNCHECKED_CAST")
            val state = ResourceRepository::class.java.getDeclaredField("mutableState").apply { isAccessible = true }
                .get(null) as MutableStateFlow<ResourceCenterState>
            state.value = ResourceCenterState(snapshot = ResourceSnapshot(catalogCommit = entry.catalogCommit,
                catalog = ResourceCatalog(1, entry.sourceCommit, listOf(entry.category)), entries = listOf(entry)),
                loadedCategories = setOf("tts"), localChecked = true)
            ResourceRepository.install(entry)
            assertTrue("Response headers/body were not received", bodyStarted.await(10, TimeUnit.SECONDS))
            assertTrue("Payload did not start reading", withTimeoutOrNull(5_000) {
                while (ResourceRepository.state.value.tasks[resourceId]?.progress?.let { it >= .25f } != true) delay(10)
                true
            } == true)
            ResourceRepository.cancel(resourceId)
            assertTrue("Cancellation waited for the socket read timeout", withTimeoutOrNull(2_000) {
                while (ResourceRepository.state.value.tasks[resourceId]?.running != false) delay(10)
                true
            } == true)
            assertEquals("已取消", ResourceRepository.state.value.tasks[resourceId]?.message)
            assertTrue("Cancellation left the HTTP connection open", peerClosed.await(1, TimeUnit.SECONDS))
            assertFalse(File(ResourceFiles.root, "payload-cache").listFiles().orEmpty().any { it.name.endsWith(".part") })
            assertTrue(ResourceInstallStore.load().isEmpty())
            assertNull(ResourceInstallStore.pending())
        } finally {
            ResourceRepository.cancel(resourceId)
            accepted.get()?.close()
            server.close()
            serverThread.shutdownNow()
            withTimeoutOrNull(5_000) {
                while (ResourceRepository.state.value.tasks[resourceId]?.running == true) delay(10)
            }
            swapClient(oldClient)
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
            if (arch == null) System.clearProperty("os.arch") else System.setProperty("os.arch", arch)
        }
    }

    private fun swapClient(replacement: OkHttpClient): OkHttpClient {
        val field = ResourceRepository::class.java.getDeclaredField("offlineTtsClient").apply { isAccessible = true }
        val type = Class.forName("sun.misc.Unsafe")
        val unsafe = type.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        val base = type.getMethod("staticFieldBase", java.lang.reflect.Field::class.java).invoke(unsafe, field)
        val offset = type.getMethod("staticFieldOffset", java.lang.reflect.Field::class.java).invoke(unsafe, field) as Long
        val previous = type.getMethod("getObjectVolatile", Any::class.java, Long::class.javaPrimitiveType)
            .invoke(unsafe, base, offset) as OkHttpClient
        type.getMethod("putObjectVolatile", Any::class.java, Long::class.javaPrimitiveType, Any::class.java)
            .invoke(unsafe, base, offset, replacement)
        return previous
    }
}
