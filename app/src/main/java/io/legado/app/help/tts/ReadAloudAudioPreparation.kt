package io.legado.app.help.tts

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream

internal data class ReadAloudAudioTask<T>(
    val cacheKey: String,
    val engineKey: String,
    val maxConcurrency: Int,
    val prepare: suspend () -> T
)

/**
 * 按播放顺序交付结果，同时让不同引擎在各自配额内并行准备音频。
 */
internal suspend fun <T> prepareReadAloudAudioTasks(
    tasks: List<ReadAloudAudioTask<T>>,
    globalConcurrency: Int,
    onPreparedInOrder: suspend (T) -> Unit = {}
) = coroutineScope {
    if (tasks.isEmpty()) return@coroutineScope
    val globalLimit = globalConcurrency.coerceAtLeast(1)
    val uniqueTasks = linkedMapOf<String, ReadAloudAudioTask<T>>()
    tasks.forEach { task -> uniqueTasks.putIfAbsent(task.cacheKey, task) }
    val results = uniqueTasks.keys.associateWith { CompletableDeferred<T>() }
    val pending = uniqueTasks.values.toMutableList()
    val completions = Channel<String>(Channel.UNLIMITED)

    val scheduler = launch {
        val activeByEngine = hashMapOf<String, Int>()
        var activeCount = 0
        try {
            while (pending.isNotEmpty() || activeCount > 0) {
                while (activeCount < globalLimit) {
                    val taskIndex = pending.indexOfFirst { task ->
                        activeByEngine.getOrDefault(task.engineKey, 0) <
                                task.maxConcurrency.coerceIn(1, globalLimit)
                    }
                    if (taskIndex < 0) break
                    val task = pending.removeAt(taskIndex)
                    activeCount++
                    activeByEngine[task.engineKey] =
                        activeByEngine.getOrDefault(task.engineKey, 0) + 1
                    launch {
                        try {
                            results.getValue(task.cacheKey).complete(task.prepare())
                        } catch (e: Throwable) {
                            results.getValue(task.cacheKey).completeExceptionally(e)
                        } finally {
                            completions.send(task.engineKey)
                        }
                    }
                }
                if (activeCount > 0) {
                    val engineKey = completions.receive()
                    activeCount--
                    val remaining = activeByEngine.getOrDefault(engineKey, 1) - 1
                    if (remaining == 0) activeByEngine.remove(engineKey)
                    else activeByEngine[engineKey] = remaining
                }
            }
        } finally {
            completions.close()
        }
    }

    try {
        tasks.forEach { task ->
            onPreparedInOrder(results.getValue(task.cacheKey).await())
        }
        scheduler.join()
    } finally {
        scheduler.cancel()
    }
}

internal suspend fun writeReadAloudAudioAtomically(
    target: File,
    input: InputStream
): File {
    target.parentFile?.mkdirs()
    if (target.isFile) {
        input.close()
        return target
    }
    val part = File.createTempFile("${target.name}.", ".part", target.parentFile)
    try {
        input.use { source ->
            part.outputStream().buffered().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = source.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
            }
        }
        currentCoroutineContext().ensureActive()
        if (target.isFile) {
            part.delete()
            return target
        }
        check(part.renameTo(target)) { "朗读音频缓存发布失败：${target.name}" }
        return target
    } catch (e: Throwable) {
        part.delete()
        throw e
    }
}
