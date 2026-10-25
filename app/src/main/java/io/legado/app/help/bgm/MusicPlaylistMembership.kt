package io.legado.app.help.bgm

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Read membership independently from search pagination; an incomplete read is not an empty set. */
internal suspend fun collectPlaylistTrackIds(loadPage: suspend (Int) -> MusicPage<MusicTrack>): Set<String> {
    val ids = linkedSetOf<String>()
    var offset = 0
    repeat(500) {
        currentCoroutineContext().ensureActive()
        val page = loadPage(offset)
        currentCoroutineContext().ensureActive()
        ids.addAll(page.items.map(MusicTrack::id))
        val next = page.nextOffset
        if (next == null) {
            if (page.total != null && offset + page.items.size < page.total) {
                throw MusicProviderException("歌单歌曲未完整返回")
            }
            return ids
        }
        if (next <= offset) throw MusicProviderException("歌单分页不可用")
        offset = next
    }
    throw MusicProviderException("歌单过大，暂时无法核对已有歌曲")
}
