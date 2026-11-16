package io.legado.app.help.tts.packages

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Serializes SDK requests and releases the dedicated worker after a short idle period. */
@RequiresApi(26)
internal object MicrosoftOfflineClient {
    private val mutex = Mutex()
    private val lock = Any()
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newCachedThreadPool()
    private var binding: Binding? = null
    private var idleClose: Runnable? = null

    suspend fun synthesize(context: Context, revision: String, voiceId: String, text: String): InputStream = mutex.withLock {
        MicrosoftOfflineSupport.currentUnavailableReason()?.let { throw IOException(it) }
        require(revision.isNotBlank()) { "离线引擎尚未准备完成" }
        require(voiceId.isNotBlank()) { "请选择离线发音人" }
        val app = context.applicationContext
        var created: File? = null
        var opened: InputStream? = null
        var handedOff = false
        var current: Binding? = null
        try {
            withContext(Dispatchers.IO) { created = File.createTempFile("offline-tts-", ".wav", app.cacheDir) }
            val file = requireNotNull(created)
            repeat(2) { attempt ->
                current = acquire(app)
                val selected = requireNotNull(current)
                val (service, result) = try {
                    val service = runInterruptible(Dispatchers.IO) { selected.awaitService() }
                    service to (withTimeoutOrNull(75_000L) { invoke(selected, service, revision, voiceId, text, file) }
                        ?: run {
                            MicrosoftOfflineTrace.client(MicrosoftOfflineTrace.ClientEvent.TIMEOUT)
                            throw IOException("离线语音合成超时")
                        })
                } catch (error: WorkerUnavailable) {
                    MicrosoftOfflineTrace.client(MicrosoftOfflineTrace.ClientEvent.WORKER_DIED)
                    invalidate(selected)
                    current = null
                    if (attempt == 0) return@repeat
                    throw error
                }
                if (result == MicrosoftOfflineService.RESTART_REQUIRED && attempt == 0) {
                    stopWorker(selected, service)
                    current = null
                    return@repeat
                }
                if (result.isNotEmpty()) throw IOException(result)
                withContext(Dispatchers.IO) {
                    val input = FileInputStream(file)
                    if (file.length() <= 44L || input.read() != 'R'.code || input.read() != 'I'.code ||
                        input.read() != 'F'.code || input.read() != 'F'.code) {
                        input.close()
                        throw IOException("离线语音未生成有效音频")
                    }
                    input.channel.position(0)
                    opened = object : FilterInputStream(input) {
                        override fun close() {
                            try { super.close() } finally { file.delete() }
                        }
                    }
                }
                val stream = requireNotNull(opened)
                handedOff = true
                return@withLock stream
            }
            throw IOException("离线引擎重启失败")
        } finally {
            current?.let(::scheduleIdleClose)
            if (!handedOff) {
                runCatching { opened?.close() }
                created?.delete()
            }
        }
    }

    private suspend fun acquire(context: Context): Binding {
        while (true) {
            val existing = synchronized(lock) {
                idleClose?.let(handler::removeCallbacks)
                idleClose = null
                binding
            }
            if (existing?.poisoned == true) {
                check(runInterruptible(Dispatchers.IO) { existing.cleaned.await(10, TimeUnit.SECONDS) }) {
                    "上一次离线朗读仍在停止，请稍后重试"
                }
                synchronized(lock) { if (binding === existing) binding = null }
                existing.close()
                continue
            }
            if (existing != null && !existing.dead) return existing
            synchronized(lock) {
                if (binding === existing) {
                    existing?.close()
                    val next = Binding(context)
                    binding = next
                    try { next.start() } catch (error: Throwable) {
                        binding = null
                        next.close()
                        throw error
                    }
                    return next
                }
            }
        }
    }

    private suspend fun invoke(
        selected: Binding,
        service: IMicrosoftOfflineService,
        revision: String,
        voiceId: String,
        text: String,
        file: File,
    ): String = suspendCancellableCoroutine { continuation ->
        val descriptor = try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_TRUNCATE)
        } catch (error: Throwable) {
            continuation.resumeWithException(error)
            return@suspendCancellableCoroutine
        }
        continuation.invokeOnCancellation {
            MicrosoftOfflineTrace.client(MicrosoftOfflineTrace.ClientEvent.CANCELLED)
            selected.poisoned = true
            executor.execute {
                try { runCatching { service.cancel() } } finally {
                    invalidate(selected)
                    selected.cleaned.countDown()
                }
            }
        }
        executor.execute {
            try {
                val result = service.synthesize(revision, voiceId, text, descriptor)
                if (continuation.isActive) runCatching { continuation.resume(result) }
            } catch (_: Throwable) {
                if (continuation.isActive) runCatching {
                    continuation.resumeWithException(WorkerUnavailable())
                }
            } finally {
                runCatching { descriptor.close() }
            }
        }
    }

    private suspend fun stopWorker(selected: Binding, service: IMicrosoftOfflineService) {
        selected.poisoned = true
        try { withContext(Dispatchers.IO) { runCatching { service.cancel() } } }
        finally {
            invalidate(selected)
            selected.cleaned.countDown()
        }
    }

    private fun invalidate(selected: Binding) = synchronized(lock) {
        if (binding === selected) binding = null
        selected.close()
    }

    private fun scheduleIdleClose(selected: Binding) = synchronized(lock) {
        if (binding !== selected || selected.poisoned) return@synchronized
        idleClose?.let(handler::removeCallbacks)
        val task = Runnable { invalidate(selected) }
        idleClose = task
        handler.postDelayed(task, 30_000L)
    }

    private class Binding(private val context: Context) : ServiceConnection {
        private val ready = CountDownLatch(1)
        val cleaned = CountDownLatch(1)
        @Volatile var service: IMicrosoftOfflineService? = null
        @Volatile var poisoned = false
        @Volatile var dead = false
        private var bound = false

        fun start() {
            bound = context.bindService(Intent(context, MicrosoftOfflineService::class.java), this, Context.BIND_AUTO_CREATE)
            check(bound) { "无法启动离线语音服务" }
        }

        fun awaitService(): IMicrosoftOfflineService {
            check(ready.await(20, TimeUnit.SECONDS)) { "启动离线语音服务超时" }
            return service?.takeIf { it.asBinder().isBinderAlive }
                ?: throw WorkerUnavailable()
        }

        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = IMicrosoftOfflineService.Stub.asInterface(binder)
            dead = binder == null
            ready.countDown()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            dead = true
            ready.countDown()
        }

        override fun onBindingDied(name: ComponentName?) = onServiceDisconnected(name)
        override fun onNullBinding(name: ComponentName?) = onServiceDisconnected(name)

        @Synchronized
        fun close() {
            if (bound) {
                bound = false
                runCatching { context.unbindService(this) }
            }
            service = null
            dead = true
            ready.countDown()
        }
    }

    private class WorkerUnavailable : IOException("离线语音服务已中断，请重试")
}
