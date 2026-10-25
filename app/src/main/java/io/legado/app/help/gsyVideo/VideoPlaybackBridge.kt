package io.legado.app.help.gsyVideo

import android.content.Context
import android.media.AudioManager
import android.view.Surface
import com.shuyu.gsyvideoplayer.listener.GSYMediaPlayerListener
import com.shuyu.gsyvideoplayer.utils.NetInfoModule
import io.legado.app.model.VideoPlay
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
object VideoPlaybackBridge : VideoPlaybackTarget, GSYMediaPlayerListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(VideoPlaybackState())
    val state = mutableState.asStateFlow()
    private val mutableOwner = MutableStateFlow<Any?>(null)
    val owner = mutableOwner.asStateFlow()
    private val completionEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val completions = completionEvents.asSharedFlow()
    private val manager get() = VideoPlay.videoManager
    private val audio by lazy { appCtx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private val focus = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            scope.launch {
                onVideoPause()
                if (change == AudioManager.AUDIOFOCUS_LOSS) {
                    seekOnStart = state.value.position
                    ticker?.cancel()
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
    private var ticker: Job? = null
    private var hasPlaybackPosition = false
    private var playWhenReady = true
    private var selectedSpeed = 1f
    private var network: NetInfoModule? = null
    private var networkType = ""
    private var networkChanged = false
    private var networkRetry: Job? = null

    fun attach(owner: Any, surface: Surface) {
        lease.attach(owner, surface)
        manager.setDisplay(surface)
        // A paused decoder needs to render its current frame into the new texture.
        if (state.value.prepared && !state.value.playing) {
            manager.seekTo(state.value.position.coerceAtMost((state.value.duration - 1).coerceAtLeast(0)))
        }
        mutableOwner.value = owner
    }

    fun detach(owner: Any, surface: Surface) {
        if (lease.detach(owner, surface)) {
            manager.setDisplay(null)
        }
    }

    override fun loading() {
        onVideoPause()
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
        mutableState.update { it.copy(preparing = false, error = message) }
    }

    override fun setUp(url: String?, cache: Boolean, cachePath: File?, title: String?): Boolean {
        networkRetry?.cancel()
        ticker?.cancel()
        hasPlaybackPosition = false
        manager.releaseMediaPlayer()
        this.url = url
        this.cache = cache
        this.cachePath = cachePath
        mutableState.value = VideoPlaybackState(title = title.orEmpty(), speed = selectedSpeed,
            position = seekOnStart, mediaRevision = state.value.mediaRevision + 1)
        return !url.isNullOrBlank()
    }

    @Suppress("DEPRECATION")
    override fun startPlayLogic() {
        val url = url ?: return
        if (state.value.preparing) return
        playWhenReady = true
        manager.initContext(appCtx)
        manager.setListener(this)
        audio.requestAudioFocus(focus, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        mutableState.update { it.copy(preparing = true, error = null) }
        manager.prepare(url, mapHeadData, false, selectedSpeed, cache, cachePath)
    }

    override fun onPrepared() {
        manager.setDisplay(lease.output)
        manager.start()
        if (seekOnStart > 0) manager.seekTo(seekOnStart)
        if (!playWhenReady) manager.pause()
        hasPlaybackPosition = true
        onVideoSizeChanged()
        mutableState.update { it.copy(preparing = false, prepared = true, playing = playWhenReady, ended = false,
            duration = manager.duration.coerceAtLeast(0), error = null) }
        if (network == null) {
            network = NetInfoModule(appCtx) { type ->
                if (networkType != type) networkChanged = true
                networkType = type
            }.also { networkType = it.currentConnectionType; it.onHostResume() }
        }
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                // Reattaching a completed video seeks just before its end to paint the last frame.
                // That decoder position must not turn a completed session into a paused one.
                mutableState.update { if (it.ended) it else it.copy(position = manager.currentPosition.coerceAtLeast(0),
                    duration = manager.duration.coerceAtLeast(0)) }
                delay(500)
            }
        }
    }

    fun toggle() {
        if (state.value.playing) onVideoPause()
        else if (!state.value.prepared && url?.startsWith("http", true) == true &&
            !com.shuyu.gsyvideoplayer.utils.CommonUtil.isWifiConnected(appCtx) &&
            !manager.cachePreview(appCtx, cachePath, url)) {
            mutableState.update { it.copy(networkConfirmation = true) }
        } else onVideoResume()
    }
    fun confirmNetwork(play: Boolean) {
        mutableState.update { it.copy(networkConfirmation = false) }
        if (play) onVideoResume()
    }
    override fun onVideoPause() {
        playWhenReady = false
        if (state.value.prepared) manager.pause()
        mutableState.update { it.copy(playing = false) }
    }
    override fun onVideoResume() = onVideoResume(false)
    override fun onVideoResume(seek: Boolean) {
        playWhenReady = true
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
        val target = position.coerceIn(0, state.value.duration.coerceAtLeast(0))
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
        ticker?.cancel()
        manager.setListener(null)
    }
    override fun onBufferingUpdate(percent: Int) { mutableState.update { it.copy(buffered = percent) } }
    override fun onSeekComplete() { mutableState.update { it.copy(seekRevision = it.seekRevision + 1) } }
    override fun onError(what: Int, extra: Int) {
        ticker?.cancel()
        seekOnStart = state.value.position
        VideoPlay.saveRead(seekOnStart.toInt())
        mutableState.update { it.copy(preparing = false, prepared = false, playing = false, buffering = false,
            error = "播放失败 ($what / $extra)") }
        if (networkChanged) {
            networkChanged = false
            networkRetry?.cancel()
            networkRetry = scope.launch {
                manager.releaseMediaPlayer()
                delay(500)
                startPlayLogic()
            }
        }
    }
    override fun onInfo(what: Int, extra: Int) {
        when (what) {
            701 -> mutableState.update { it.copy(buffering = true) }
            702 -> mutableState.update { it.copy(buffering = false) }
            10001 -> mutableState.update { it.copy(rotation = extra) }
        }
    }
    override fun onVideoSizeChanged() {
        val sarNum = manager.videoSarNum.coerceAtLeast(1)
        val sarDen = manager.videoSarDen.coerceAtLeast(1)
        val width = (manager.currentVideoWidth.toLong() * sarNum / sarDen).toInt()
        val height = manager.currentVideoHeight
        mutableState.update { it.copy(width = width, height = height) }
        VideoPlay.isPortraitVideo = state.value.aspect < 1f / 1.2f
    }
    override fun onBackFullscreen() = Unit

    @Suppress("DEPRECATION")
    fun release(owner: Any) {
        if (!lease.release(owner)) return
        // Until the decoder is prepared, UI position can still be a loading placeholder.
        // Keep the book/RSS/cache bookmark, including when source resolution failed.
        if (hasPlaybackPosition) VideoPlay.saveRead(state.value.position.toInt())
        VideoPlay.stopLoading()
        ticker?.cancel()
        networkRetry?.cancel()
        network?.onHostPause()
        network = null
        networkChanged = false
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
