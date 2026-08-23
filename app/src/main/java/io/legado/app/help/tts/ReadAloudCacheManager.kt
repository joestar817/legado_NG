package io.legado.app.help.tts

import android.content.Context
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.help.ai.AiTtsStoryboardHelper
import io.legado.app.service.BaseReadAloudService
import io.legado.app.utils.MD5Utils
import splitties.init.appCtx
import java.io.File

object ReadAloudCacheManager {

    private const val TTS_CACHE_DIRECTORY = "httpTTS"

    data class ClearResult(
        val storyboardChapterCount: Int,
        val ttsFileCount: Int,
    )

    suspend fun clearCurrentBook(book: Book): ClearResult {
        val chapters = appDb.bookChapterDao.getChapterList(book.bookUrl)
        val workKey = BookCharacterProfile.workKey(book.name, book.author)
        val characters = appDb.bookCharacterDao.getCharacters(workKey)
        val storyboards = AiTtsStoryboardHelper.listCachedStoryboards(
            book = book,
            chapters = chapters,
            characters = characters,
        )
        var storyboardChapterCount = 0
        storyboards.forEach { entry ->
            if (AiTtsStoryboardHelper.deleteCachedStoryboard(entry)) {
                storyboardChapterCount += 1
            }
        }
        val ttsFileCount = clearTtsAudioCache(
            directory = ttsCacheDirectory(appCtx, book),
            preserveInProgress = BaseReadAloudService.isRun,
        )
        return ClearResult(
            storyboardChapterCount = storyboardChapterCount,
            ttsFileCount = ttsFileCount,
        )
    }

    fun ttsCacheDirectory(context: Context, book: Book): File =
        File(
            ttsCacheRootDirectory(context),
            MD5Utils.md5Encode(BookCharacterProfile.workKey(book.name, book.author)),
        )

    fun ttsCacheRootDirectory(context: Context): File =
        File(context.cacheDir, TTS_CACHE_DIRECTORY)

    internal fun clearTtsAudioCache(
        directory: File,
        preserveInProgress: Boolean,
    ): Int {
        if (!directory.exists()) return 0
        val cacheFiles = directory.walkTopDown()
            .filter { file ->
                file.isFile && (!preserveInProgress || !file.name.endsWith(".part"))
            }
            .toList()
        val failed = cacheFiles.filterNot { file ->
            !file.exists() || file.delete()
        }
        check(failed.isEmpty()) {
            "无法删除 ${failed.size} 个 TTS 缓存文件"
        }
        directory.walkBottomUp()
            .filter { file ->
                file != directory && file.isDirectory && file.listFiles().isNullOrEmpty()
            }
            .forEach { it.delete() }
        return cacheFiles.size
    }
}
