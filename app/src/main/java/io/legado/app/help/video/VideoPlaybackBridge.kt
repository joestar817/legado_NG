package io.legado.app.help.video

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.AudioManager
import android.view.Surface
import android.net.ConnectivityManager
import android.os.SystemClock
import androidx.media3.common.PlaybackException
import io.legado.app.constant.AppLog
import io.legado.app.model.VideoPlay
import io.legado.features.adapter.VideoFeatures
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import splitties.init.appCtx
import java.io.File

data class VideoPlaybackState(
    val title: String = "",
    val preparing: Boolean = false,
    val prepared: Boolean = false,
    val playing: Boolean = false,
    val ended: Boolean = false,
    val buffering: Boolean = false,
    val position: Long = 0,
    val duration: Long = 0,
    val buffered: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val rotation: Int = 0,
    val speed: Float = 1f,
    val error: String? = null,
    val mediaRevision: Int = 0,
    val seekRevision: Int = 0,
    val networkConfirmation: Boolean = false,
) {
    val aspect: Float get() = if (width > 0 && height > 0) {
        if (rotation % 180 == 0) width.toFloat() / height else height.toFloat() / width
    } else 16f / 9f
}

/** A single media session shared by the activity and the overlay, independent of composition. */
object VideoPlaybackBridge : VideoPlaybackTarget, VideoPlaybackListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(VideoPlaybackState())
    val state = mutableState.asStateFlow()
    private val mutableOwner = MutableStateFlow<Any?>(null)
    val owner = mutableOwner.asStateFlow()
    private val completionEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val completions = completionEvents.asSharedFlow()
    private val manager = Media3VideoEngine()
    private val audio by lazy { appCtx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private val focus = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            scope.launch {
                onVideoPause()
                if (change == AudioManager.AUDIOFOCUS_LOSS) {
                    seekOnStart = state.value.position
                    ticker?.cancel()
                    recovery.restarted()
                    manager.releaseMediaPlayer()
                    mutableState.update { it.copy(prepared = false, preparing = false, buffering = false) }
                }
            }
        }
    }
    override var seekOnStart = 0L
    override var mapHeadData: Map<String, String>? = null
    private var url: String? = null
    private var cachePath: File? = null
    private var cache = false
    private val lease = VideoOutputLease<Surface>()
    private var outputWidth = 0
    private var outputHeight = 0
    private var ticker: Job? = null
    private var dimensionFallback: Job? = null
    private var hasPlaybackPosition = false
    private var playWhenReady = true
    private var selectedSpeed = 1f
    private val connectivity get() = appCtx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var networkRetry: Job? = null
    private val recovery = VideoFeatures.Recovery()

    fun attach(owner: Any, surface: Surface, width: Int, height: Int) {
        lease.attach(owner, surface)
        outputWidth = width
        outputHeight = height
        manager.setDisplay(surface, width, height)
        // A paused decoder needs to render its current frame into the new texture.
        if (state.value.prepared && !state.value.playing) {
            manager.seekTo(state.value.position.coerceAtMost((state.value.duration - 1).coerceAtLeast(0)))
        }
        mutableOwner.value = owner
    }

    fun resize(owner: Any, surface: Surface, width: Int, height: Int) {
        if (lease.owner !== owner || lease.output !== surface) return
        outputWidth = width
        outputHeight = height
        manager.setDisplay(surface, width, height)
    }

    fun detach(owner: Any, surface: Surface) {
        if (lease.detach(owner, surface)) {
            manager.setDisplay(null)
        }
    }

    override fun loading() {
        onVideoPause()
        recovery.reset()
        ticker?.cancel()
        networkRetry?.cancel()
        // A failed new source must never resume the previous source's resolved URL.
        url = null
        hasPlaybackPosition = false
        manager.releaseMediaPlayer()
        mutableState.update { it.copy(preparing = true, prepared = false, ended = false, position = seekOnStart,
            duration = 0, buffering = false, error = null, networkConfirmation = false) }
    }

    override fun loadError(message: String) {
        ticker?.cancel()
        networkRetry?.cancel()
        mutableState.update { it.copy(preparing = false, error = message) }
    }

    override fun setUp(url: String?, cache: Boolean, cachePath: File?, title: String?): Boolean {
        recovery.reset()
        networkRetry?.cancel()
        ticker?.cancel()
        dimensionFallback?.cancel()
        hasPlaybackPosition = false
        manager.releaseMediaPlayer()
        this.url = url
        this.cache = cache
        this.cachePath = cachePath
        mutableState.value = VideoPlaybackState(title = title.orEmpty(), speed = selectedSpeed,
            position = seekOnStart, mediaRevision = state.value.mediaRevision + 1)
        if (!url.isNullOrBlank()) fetchVideoDimensions(url, mapHeadData.orEmpty(), state.value.mediaRevision)
        return !url.isNullOrBlank()
    }

    private fun fetchVideoDimensions(url: String, headers: Map<String, String>, revision: Int) {
        dimensionFallback = scope.launch(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(url, headers)
                val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toIntOrNull() ?: 0
                val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toIntOrNull() ?: 0
                val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                    ?.toIntOrNull()?.let { ((it % 360) + 360) % 360 } ?: 0
                if (width <= 0 || height <= 0) return@launch
                withContext(Dispatchers.Main.immediate) {
                    mutableState.update { current ->
                        if (current.mediaRevision != revision || current.width > 0 && current.height > 0) {
                            current
                        } else {
                            current.copy(width = width, height = height, rotation = rotation)
                        }
                    }
                    if (state.value.mediaRevision == revision && state.value.width > 0 && state.value.height > 0) {
                        VideoPlay.isPortraitVideo = state.value.aspect < 1f / 1.2f
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Decoder callbacks remain the primary source when metadata probing is unavailable.
            } finally {
                runCatching { retriever.release() }
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun startPlayLogic() {
        val url = url ?: return
        playWhenReady = true
        recovery.restarted()
        ticker?.cancel()
        networkRetry?.cancel()
        manager.setListener(this)
        audio.requestAudioFocus(focus, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        mutableState.update { it.copy(preparing = true, error = null) }
        try {
            manager.prepare(url, mapHeadData, false, selectedSpeed, cache, cachePath, seekOnStart, playWhenReady)
            startTicker()
        } catch (error: Exception) {
            manager.releaseMediaPlayer()
            loadError("播放器初始化失败：${error.localizedMessage.orEmpty()}")
        }
    }

    override fun onPrepared() {
        // Output, bookmark and playWhenReady are supplied before prepare. A READY
        // callback must not bind the output or trigger another startup seek.
        seekOnStart = 0
        hasPlaybackPosition = true
        onVideoSizeChanged(state.value.rotation)
        mutableState.update { it.copy(preparing = false, prepared = true, playing = playWhenReady, ended = false,
            duration = manager.duration.coerceAtLeast(0), error = null) }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                // Reattaching a completed video seeks just before its end to paint the last frame.
                // That decoder position must not turn a completed session into a paused one.
                mutableState.update { if (it.ended || !it.prepared) it else it.copy(position = manager.currentPosition.coerceAtLeast(0),
                    duration = manager.duration.coerceAtLeast(0), buffered = manager.bufferedPercentage) }
                val current = state.value
                if (!current.ended && recovery.stalled(SystemClock.elapsedRealtime(), current.position,
                        manager.bufferedPosition, current.preparing || current.buffering,
                        manager.isPlaying, playWhenReady)) {
                    if (!recoverPlayback("缓冲超时")) failPlayback("视频缓冲超时，请重试或切换线路")
                    return@launch
                }
                delay(500)
            }
        }
    }

    fun toggle() {
        if (state.value.playing) onVideoPause()
        else if (!state.value.prepared && url?.startsWith("http", true) == true &&
            !isWifiConnected()) {
            mutableState.update { it.copy(networkConfirmation = true) }
        } else onVideoResume()
    }
    fun confirmNetwork(play: Boolean) {
        mutableState.update { it.copy(networkConfirmation = false) }
        if (play) onVideoResume()
    }
    override fun onVideoPause() {
        playWhenReady = false
        val pendingRetry = networkRetry?.isActive == true
        networkRetry?.cancel()
        recovery.restarted()
        manager.pause()
        mutableState.update { it.copy(playing = false,
            preparing = if (pendingRetry) false else it.preparing,
            buffering = if (pendingRetry) false else it.buffering) }
    }
    override fun onVideoResume() = onVideoResume(false)
    override fun onVideoResume(seek: Boolean) {
        playWhenReady = true
        if (state.value.error != null) recovery.reset()
        recovery.restarted()
        if (state.value.ended || state.value.duration > 0 && state.value.position >= state.value.duration) seekTo(0)
        if (!state.value.prepared) {
            if (url == null && state.value.error != null) {
                retry()
                return
            }
            startPlayLogic()
            return
        }
        @Suppress("DEPRECATION")
        audio.requestAudioFocus(focus, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        manager.start()
        mutableState.update { it.copy(playing = true) }
    }
    fun seekTo(position: Long) {
        recovery.restarted()
        val target = if (state.value.duration > 0) position.coerceIn(0, state.value.duration) else position.coerceAtLeast(0)
        if (state.value.prepared) manager.seekTo(target) else seekOnStart = target
        mutableState.update { it.copy(position = target, ended = false, seekRevision = it.seekRevision + 1) }
    }
    fun speed(value: Float, temporary: Boolean = false) {
        if (!temporary) selectedSpeed = value
        manager.setSpeed(value, true)
        mutableState.update { it.copy(speed = value) }
    }
    fun restoreSpeed() = speed(selectedSpeed)
    fun next(offset: Int) = VideoPlay.upDurIndex(offset, this)
    fun retry() = VideoPlay.startPlay(this)

    override fun onAutoCompletion() {
        mutableState.update { it.copy(playing = false, buffering = false, ended = true, position = it.duration) }
        VideoPlay.saveRead()
        if (!next(1)) {
            onVideoPause()
            completionEvents.tryEmit(Unit)
        }
    }
    override fun onCompletion() {
        playWhenReady = false
        ticker?.cancel()
        networkRetry?.cancel()
        manager.setListener(null)
    }
    override fun onBufferingUpdate(percent: Int) { mutableState.update { it.copy(buffered = percent) } }
    override fun onSeekComplete() { mutableState.update { it.copy(seekRevision = it.seekRevision + 1) } }
    override fun onError(what: Int, extra: Int) {
        val recoverable = what == PlaybackException.ERROR_CODE_IO_UNSPECIFIED ||
            what == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
            what == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
            what == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
            what == PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE
        if (recoverable && recoverPlayback("网络错误 $what")) return
        failPlayback("播放失败 ($what / $extra)，请重试或切换线路")
    }

    private fun saveRecoveryPosition() {
        seekOnStart = if (state.value.prepared) manager.currentPosition.coerceAtLeast(0) else state.value.position
        if (hasPlaybackPosition) VideoPlay.saveRead(seekOnStart.coerceIn(0, Int.MAX_VALUE.toLong()).toInt())
    }

    private fun recoverPlayback(reason: String): Boolean {
        val currentUrl = url ?: return false
        if (!playWhenReady) return false
        val attempt = recovery.nextAttempt() ?: return false
        saveRecoveryPosition()
        ticker?.cancel()
        networkRetry?.cancel()
        manager.releaseMediaPlayer()
        val revision = state.value.mediaRevision
        mutableState.update { it.copy(preparing = true, prepared = false, playing = false, buffering = false,
            position = seekOnStart, buffered = 0, error = null) }
        AppLog.put("视频播放：$reason，保留进度自动重连 ($attempt/2)")
        networkRetry = scope.launch {
            delay(attempt * 750L)
            if (playWhenReady && url == currentUrl && state.value.mediaRevision == revision) {
                networkRetry = null
                startPlayLogic()
            }
        }
        return true
    }

    private fun failPlayback(message: String) {
        saveRecoveryPosition()
        ticker?.cancel()
        networkRetry?.cancel()
        manager.releaseMediaPlayer()
        playWhenReady = false
        mutableState.update { it.copy(preparing = false, prepared = false, playing = false, buffering = false,
            position = seekOnStart, error = message) }
        AppLog.put("视频播放：$message")
    }
    override fun onInfo(what: Int, extra: Int) {
        when (what) {
            701 -> mutableState.update { it.copy(buffering = true) }
            702 -> mutableState.update { it.copy(buffering = false) }
            10001 -> mutableState.update { it.copy(rotation = extra) }
        }
    }
    override fun onVideoSizeChanged(rotationDegrees: Int) {
        val sarNum = manager.videoSarNum.coerceAtLeast(1)
        val sarDen = manager.videoSarDen.coerceAtLeast(1)
        val width = (manager.currentVideoWidth.toLong() * sarNum / sarDen).toInt()
        val height = manager.currentVideoHeight
        if (width <= 0 || height <= 0) return
        mutableState.update { it.copy(width = width, height = height, rotation = rotationDegrees) }
        VideoPlay.isPortraitVideo = state.value.aspect < 1f / 1.2f
    }
    override fun onBackFullscreen() = Unit

    @Suppress("DEPRECATION")
    private fun isWifiConnected(): Boolean =
        connectivity.activeNetworkInfo?.type == ConnectivityManager.TYPE_WIFI

    fun releasePlayer() {
        playWhenReady = false
        recovery.reset()
        ticker?.cancel()
        networkRetry?.cancel()
        dimensionFallback?.cancel()
        manager.setListener(null)
        manager.setDisplay(null)
        manager.releaseMediaPlayer()
    }

    @Suppress("DEPRECATION")
    fun release(owner: Any) {
        if (!lease.release(owner)) return
        // Until the decoder is prepared, UI position can still be a loading placeholder.
        // Keep the book/RSS/cache bookmark, including when source resolution failed.
        if (hasPlaybackPosition) VideoPlay.saveRead(state.value.position.toInt())
        VideoPlay.stopLoading()
        ticker?.cancel()
        networkRetry?.cancel()
        audio.abandonAudioFocus(focus)
        manager.setDisplay(null)
        mutableOwner.value = null
        VideoPlay.releaseAllVideos()
        url = null
        selectedSpeed = 1f
        hasPlaybackPosition = false
        mutableState.value = VideoPlaybackState()
    }
}
