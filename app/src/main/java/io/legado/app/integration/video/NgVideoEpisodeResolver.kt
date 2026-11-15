package io.legado.app.integration.video

import android.net.Uri
import io.legado.app.constant.AppLog
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.exception.ContentEmptyException
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.getDanmaku
import io.legado.app.integration.source.NgExtensionSourceBridge
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.model.webBook.WebBook
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.MD5Utils
import io.legado.features.adapter.VideoFeatures
import io.legado.features.api.source.VideoResource
import io.legado.features.api.video.PlaybackRequest
import io.legado.features.api.video.PlaybackSelection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import java.io.File

internal data class NgResolvedEpisode(
    val request: PlaybackRequest,
    val resources: List<VideoResource>,
    val selection: PlaybackSelection,
    val expiresAt: Long?,
    val title: String,
    val input: String,
    val danmaku: Any?,
)

internal object NgVideoEpisodeResolver {
    suspend fun resolve(source: BookSource, book: Book, chapter: BookChapter,
                        selection: PlaybackSelection, directory: File, save: Boolean): NgResolvedEpisode =
        NgVideoBridge.analyzingRules {
            val content = NgVideoBridge.ruleContent {
                val raw = BookHelp.getContent(book, chapter)?.takeIf { it.isNotBlank() } ?: try {
                    WebBook.getContentAwait(source, book, chapter, needSave = save)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    if (NgExtensionSourceBridge.handles(source)) throw error
                    AppLog.put("视频播放：正文解析失败，回退章节地址\n${error.localizedMessage}", error)
                    null
                }
                raw?.takeIf { it.isNotBlank() } ?: chapter.getAbsoluteURL().takeIf { !chapter.isVolume }.orEmpty()
            }.trim()
            val session = VideoFeatures()
            val identity = listOf(source.getKey(), book.bookUrl, chapter.index, chapter.url).joinToString("\u0000")
            val revision = session.begin(identity)
            session.select(selection.lineId, selection.qualityTitle, 0)
            session.tracks(selection.subtitle, selection.audio, 0)
            fun resources(): List<VideoResource> = chapter.variableMap["legadoFeatures.videos"]
                ?.let { GSON.fromJson(it, Array<VideoResource>::class.java).toList() }.orEmpty()
            var variants = resources()
            val request = if (variants.isEmpty()) null else session.resolveRefreshing(revision, identity, variants) {
                NgExtensionSourceBridge.content(source, book, chapter, save = false)
                resources().also { variants = it }
            }
            val input = request?.url ?: when {
                content.isEmpty() -> throw ContentEmptyException("正文为空")
                NgVideoBridge.isDash(content) -> {
                    val file = FileUtils.createFileIfNotExist(directory, MD5Utils.md5Encode(content) + ".mpd")
                    file.writeText(content)
                    Uri.fromFile(file).toString()
                }
                else -> content
            }
            val analyze = AnalyzeUrl(
                if (input.startsWith("<")) chapter.url else NgVideoBridge.initialInput(input),
                source = source, ruleData = book, chapter = chapter,
                coroutineContext = currentCoroutineContext(),
            )
            val resolved = NgVideoBridge.resolveMedia(analyze, chapter.url,
                input.takeIf { it.startsWith("<") }, input.takeUnless { it.startsWith("<") } ?: analyze.url, request)
            val selectedResource = variants.firstOrNull { it.url == request?.url && it.lineId == request?.lineId }
            NgResolvedEpisode(
                (request ?: PlaybackRequest(identity, "default", 0, resolved.url, emptyMap(), emptyList(), emptyList(), 0))
                    .copy(url = resolved.url, headers = resolved.headers),
                variants, session.selected(), listOfNotNull(selectedResource?.expiresAt, resolved.expiresAt).minOrNull(),
                chapter.title, input, chapter.getDanmaku(),
            )
        }
}
