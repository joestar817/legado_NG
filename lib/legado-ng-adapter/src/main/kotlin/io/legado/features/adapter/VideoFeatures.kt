package io.legado.features.adapter

import io.legado.features.api.source.VideoResource
import io.legado.features.api.video.*
import io.legado.features.video.*

class VideoFeatures {
    class Recovery {
        private val delegate = PlaybackRecovery()
        fun reset() = delegate.reset()
        fun restarted() = delegate.restarted()
        fun nextAttempt() = delegate.nextAttempt()
        fun stalled(now: Long, position: Long, buffered: Long, waiting: Boolean,
                    playing: Boolean, playWhenReady: Boolean) =
            delegate.stalled(now, position, buffered, waiting, playing, playWhenReady)
    }

    private val session = VideoSession()
    fun begin(episode: String) = session.begin(episode)
    fun current(revision: Long) = session.current(revision)
    fun invalidate() = session.invalidate()
    fun select(lineId: String?, quality: String?, position: Long) = session.select(lineId, quality, position)
    fun tracks(subtitle: Int?, audio: Int?, position: Long) = session.tracks(subtitle, audio, position)
    fun resolve(revision: Long, episode: String, resources: List<VideoResource>) = session.resolve(revision, VideoCatalog.variants(episode, resources), System.currentTimeMillis())
    suspend fun resolveRefreshing(revision: Long, episode: String, resources: List<VideoResource>, refresh: suspend () -> List<VideoResource>) =
        session.resolveRefreshing(revision, VideoCatalog.variants(episode, resources)) { VideoCatalog.variants(episode, refresh()) }
    fun headers(base: Map<String, String>, media: Map<String, String>) = VideoCatalog.headers(base, media)
    fun selected() = session.selected()
}
