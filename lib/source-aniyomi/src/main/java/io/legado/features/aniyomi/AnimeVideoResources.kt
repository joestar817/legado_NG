package io.legado.features.aniyomi

import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import io.legado.features.api.source.MediaTrack
import io.legado.features.api.source.VideoResource
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal object AnimeVideoResources {
    suspend fun resolve(source: AnimeSource, input: Video, lineId: String, lineTitle: String): VideoResource {
        var video = input
        if (source is AnimeHttpSource) {
            if (video.videoUrl == "null") {
                // The legacy constructor keeps a separate page URL for getVideoUrl.
                video.videoUrl = source.getVideoUrl(video)
            }
            if (!video.initialized) video = source.resolveVideo(video) ?: error("扩展未能解析播放资源：${video.videoTitle}")
        }
        check(!video.usesHttpServer()) { "该线路需要尚未接入的扩展本地代理：$lineTitle" }
        // Anikoto adds these demuxer hints to HLS streams. Media3 handles MPEG-TS HLS
        // natively; no MPV/FFmpeg operation is needed for these exact hints.
        check(video.mpvArgs.all { it == "demuxer-lavf-o" to "force_mpegts=1" } &&
            video.ffmpegStreamArgs.all { it == "force_mpegts" to "1" } && video.ffmpegVideoArgs.isEmpty()) {
            "该线路需要尚未支持的 MPV/FFmpeg 参数：$lineTitle"
        }
        check(video.videoUrl.toHttpUrlOrNull() != null) { "扩展未解析出有效媒体地址：${video.videoTitle}" }
        return VideoResource(video.videoUrl, video.videoTitle, video.headers?.toMap().orEmpty(),
            video.subtitleTracks.map { MediaTrack(it.url, it.lang) },
            video.audioTracks.map { MediaTrack(it.url, it.lang) }, video.preferred, lineId, lineTitle)
    }
}
