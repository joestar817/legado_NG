package io.legado.app.help.video

import android.annotation.SuppressLint
import android.view.Surface
import androidx.media3.common.*
import androidx.media3.common.util.Size
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.*
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.exoplayer.ExoPlayerHelper
import io.legado.app.integration.video.NgVideoBridge
import io.legado.app.integration.video.NgVideoDataSource
import io.legado.app.model.VideoPlay
import splitties.init.appCtx
import java.io.File
import java.util.concurrent.TimeUnit

interface VideoPlaybackListener {
    fun onPrepared()
    fun onAutoCompletion()
    fun onCompletion()
    fun onBufferingUpdate(percent: Int)
    fun onSeekComplete()
    fun onError(what: Int, extra: Int)
    fun onInfo(what: Int, extra: Int)
    fun onVideoSizeChanged(rotationDegrees: Int = 0)
    fun onBackFullscreen()
    fun onVideoPause()
    fun onVideoResume()
    fun onVideoResume(seek: Boolean)
}

/** The application owns the player, request headers, decoder policy and output lifetime. */
@SuppressLint("UnsafeOptInUsageError")
internal class Media3VideoEngine {
    private var player: ExoPlayer? = null
    // A TextureView can become available before prepare creates the decoder.
    // Preserve its output across player recreation instead of waiting for STATE_READY.
    private var output: Surface? = null
    private var outputWidth = 0
    private var outputHeight = 0
    private var listener: VideoPlaybackListener? = null
    fun setListener(value: VideoPlaybackListener?) { listener = value }
    val currentPosition get() = player?.currentPosition ?: 0L
    val duration get() = player?.duration?.coerceAtLeast(0) ?: 0L
    val bufferedPercentage get() = player?.bufferedPercentage ?: 0
    val bufferedPosition get() = player?.bufferedPosition?.coerceAtLeast(0) ?: 0L
    val isPlaying get() = player?.isPlaying == true
    val currentVideoWidth get() = player?.videoSize?.let { (it.width * it.pixelWidthHeightRatio).toInt() } ?: 0
    val currentVideoHeight get() = player?.videoSize?.height ?: 0
    val videoSarNum get() = 1
    val videoSarDen get() = 1

    @Suppress("UNUSED_PARAMETER")
    fun prepare(url: String, headers: Map<String, String>?, loop: Boolean, speed: Float, cache: Boolean, cachePath: File?,
                startPositionMs: Long, playWhenReady: Boolean) {
        releaseMediaPlayer()
        val request = NgVideoBridge.currentRequest()
        val policy = VideoBufferPolicy.fromId(VideoPlay.bufferPolicy)
        val client = okHttpClient.newBuilder().connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS).callTimeout(0, TimeUnit.SECONDS).build()
        val http = OkHttpDataSource.Factory(client).setDefaultRequestProperties(headers.orEmpty())
        val cached = ExoPlayerHelper.createCacheDataSourceFactory(http)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        val upstream = if (cache) DataSource.Factory { NgVideoDataSource(cached.createDataSource(), http.createDataSource()) } else http
        val sources = DefaultMediaSourceFactory(appCtx)
            .setDataSourceFactory(DefaultDataSource.Factory(appCtx, upstream))
            .setLiveTargetOffsetMs(5000)
        val created = ExoPlayer.Builder(appCtx, DefaultRenderersFactory(appCtx).setEnableDecoderFallback(VideoPlay.decoderFallback))
            .setAudioAttributes(AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).setUsage(C.USAGE_MEDIA).build(), false)
            .setMediaSourceFactory(sources)
            .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(policy.minMs, policy.maxMs, policy.startMs, policy.resumeMs)
                .setPrioritizeTimeOverSizeThresholds(true).build())
            .setHandleAudioBecomingNoisy(true)
            .build()
        player = created
        var prepared = false
        var liveRecoveryUsed = false
        created.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (player !== created) return
                listener?.onInfo(if (playbackState == Player.STATE_BUFFERING) 701 else 702, 0)
                if (created.isCurrentMediaItemLive) created.setSeekParameters(SeekParameters.DEFAULT)
                if (playbackState == Player.STATE_READY && !prepared) {
                    prepared = true
                    listener?.onPrepared()
                } else if (playbackState == Player.STATE_ENDED) listener?.onAutoCompletion()
            }
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (player === created) listener?.onVideoSizeChanged(videoSize.unappliedRotationDegrees)
            }
            override fun onRenderedFirstFrame() {
                if (player === created) listener?.onVideoSizeChanged(created.videoSize.unappliedRotationDegrees)
            }
            override fun onPlayerError(error: PlaybackException) {
                if (player !== created) return
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW && !liveRecoveryUsed) {
                    liveRecoveryUsed = true
                    created.seekToDefaultPosition()
                    created.prepare()
                } else listener?.onError(error.errorCode, 0)
            }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (player === created && !playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY) {
                    listener?.onVideoPause()
                }
            }
        })
        created.setSeekParameters(if (policy == VideoBufferPolicy.LIVE) SeekParameters.DEFAULT else SeekParameters.CLOSEST_SYNC)
        created.setPlaybackSpeed(speed)
        created.repeatMode = if (loop) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
        NgVideoBridge.enhance(created)
        NgVideoBridge.selectAudio(created, request)
        setDisplay(output, outputWidth, outputHeight)
        created.setMediaSource(NgVideoBridge.source(appCtx, sources.createMediaSource(NgVideoBridge.mediaItem(url)), request), startPositionMs.coerceAtLeast(0))
        created.playWhenReady = playWhenReady
        created.prepare()
    }

    fun setDisplay(surface: Surface?, width: Int = 0, height: Int = 0) {
        output = surface
        outputWidth = width
        outputHeight = height
        val current = player ?: return
        current.setVideoSurface(surface)
        if (surface != null && width > 0 && height > 0) {
            for (index in 0 until current.rendererCount) if (current.getRendererType(index) == C.TRACK_TYPE_VIDEO) {
                current.createMessage(current.getRenderer(index)).setType(Renderer.MSG_SET_VIDEO_OUTPUT_RESOLUTION)
                    .setPayload(Size(width, height)).send()
            }
        }
    }
    fun start() { player?.play() }
    fun pause() { player?.pause() }
    fun seekTo(position: Long) { player?.seekTo(position) }
    @Suppress("UNUSED_PARAMETER")
    fun setSpeed(speed: Float, soundTouch: Boolean) { player?.setPlaybackSpeed(speed) }
    fun releaseMediaPlayer() {
        val previous = player
        player = null
        previous?.release()
    }
}
