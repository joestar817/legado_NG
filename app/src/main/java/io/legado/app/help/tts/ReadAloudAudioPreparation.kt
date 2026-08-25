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

internal fun hasReadAloudPlayablePrefix(
    preparedItemCount: Int,
    totalItemCount: Int
): Boolean = totalItemCount > 0 && preparedItemCount > 0

internal fun hasReadAloudProductionGap(
    enqueuedItemCount: Int,
    totalItemCount: Int
): Boolean = enqueuedItemCount in 0 until totalItemCount

internal fun readAloudWholeChapterPageEndIndex(pageCount: Int): Int? =
    (pageCount - 1).takeIf { it >= 0 }

internal enum class ReadAloudPlaylistAppendAction {
    NONE,
    START,
    RESUME
}

internal fun readAloudPlaylistAppendAction(
    resumeProductionGap: Boolean,
    wasPlaylistEmpty: Boolean,
    playbackIdle: Boolean
): ReadAloudPlaylistAppendAction = when {
    resumeProductionGap -> ReadAloudPlaylistAppendAction.RESUME
    wasPlaylistEmpty && playbackIdle -> ReadAloudPlaylistAppendAction.START
    else -> ReadAloudPlaylistAppendAction.NONE
}

/**
 * Tracks whether an empty ExoPlayer queue means a real chapter end or only a
 * temporary gap while later TTS items are still being prepared.
 */
internal class ReadAloudPlaylistProductionState {
    private var generation = 0L
    private var producing = false
    private var endedWhileProducing = false

    @Synchronized
    fun begin(): Long {
        generation += 1L
        producing = true
        endedWhileProducing = false
        return generation
    }

    @Synchronized
    fun isCurrent(token: Long): Boolean = token == generation

    @Synchronized
    fun continueProduction(token: Long): Boolean {
        if (token != generation) return false
        producing = true
        return true
    }

    @Synchronized
    fun onItemAppended(token: Long): Boolean {
        if (token != generation) return false
        val shouldResume = producing && endedWhileProducing
        endedWhileProducing = false
        return shouldResume
    }

    @Synchronized
    fun onPlaybackEnded(): Boolean {
        if (!producing) return true
        endedWhileProducing = true
        return false
    }

    @Synchronized
    fun finish(token: Long): Boolean {
        if (token != generation) return false
        producing = false
        val shouldFinishChapter = endedWhileProducing
        endedWhileProducing = false
        return shouldFinishChapter
    }

    @Synchronized
    fun cancel(token: Long) {
        if (token != generation) return
        producing = false
        endedWhileProducing = false
    }
}

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
