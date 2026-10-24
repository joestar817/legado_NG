package io.legado.app.help.bgm

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/** Foreground, explicit audition. It never changes the saved queue or speech playback. */
@SuppressLint("UnsafeOptInUsageError")
class BgmPreviewController(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(BgmPlaybackState())
    val state: StateFlow<BgmPlaybackState> = mutableState.asStateFlow()
    private val mutableProgress = MutableStateFlow(BgmPreviewProgress())
    val progress: StateFlow<BgmPreviewProgress> = mutableProgress.asStateFlow()
    private val mutableVolume = MutableStateFlow(previewGain(BgmRepository.settings.value.volume))
    val volume: StateFlow<Float> = mutableVolume.asStateFlow()
    private var lastAudibleVolume = mutableVolume.value.takeIf { it > 0f } ?: 0.15f
    private val request = BgmPreviewRequestGate()
    private var player: ExoPlayer? = null
    private var loadJob: Job? = null
    private var progressJob: Job? = null
    private var sourceDurationMs = 0L
    private var sourceIsPreview = false
    private var client: OkHttpClient? = null
    private var ownsFocus = false
    private var receiverRegistered = false
    private var released = false
    private var focusRequest: AudioFocusRequestCompat? = null
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) stop()
        }
    }

    fun play(track: MusicTrack) = onMain {
        if (released) return@onMain
        // Keep this controller's BGM suspension while replacing an existing preview.
        if (current !== this) current?.stop()
        closeAudio()
        current = this
        val token = request.begin()
        mutableState.value = BgmPlaybackState(BgmPlaybackStatus.LOADING, track.key)
        mutableProgress.value = BgmPreviewProgress()
        val hosted = BgmPlaybackController.beginPreview(this)
        if (!hosted) {
            val focus = AudioFocusRequestCompat.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(AudioAttributesCompat.Builder()
                    .setUsage(AudioAttributesCompat.USAGE_MEDIA)
                    .setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC)
                    .build())
                .setOnAudioFocusChangeListener { change ->
                    if (isCurrent(token) && change != AudioManager.AUDIOFOCUS_GAIN) stop()
                }
                .build()
            focusRequest = focus
            ownsFocus = runCatching {
                AudioManagerCompat.requestAudioFocus(audioManager, focus) ==
                    AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }.getOrDefault(false)
            if (!ownsFocus) {
                finish(BgmPlaybackState(BgmPlaybackStatus.ERROR, track.key, "无法开始试听"))
                return@onMain
            }
        }
        try {
            ContextCompat.registerReceiver(appContext, noisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                ContextCompat.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
        } catch (_: Exception) {
            finish(BgmPlaybackState(BgmPlaybackStatus.ERROR, track.key, "无法开始试听"))
            return@onMain
        }
        loadJob = scope.launch {
            try {
                var source = withContext(Dispatchers.IO) { BgmRepository.resolvePreview(track) }
                if (source.expiresAt > 0 && source.expiresAt <= System.currentTimeMillis()) {
                    source = withContext(Dispatchers.IO) { BgmRepository.resolvePreview(track) }
                }
                if (!isCurrent(token)) return@launch
                sourceDurationMs = source.fullDurationMs.takeIf { it > 0 } ?: track.durationMs
                sourceIsPreview = source.preview == true
                val mediaClient = newBgmMediaClient(track.platform)
                client = mediaClient
                val factory = DefaultDataSource.Factory(appContext,
                    OkHttpDataSource.Factory(mediaClient).setDefaultRequestProperties(source.headers))
                val mediaSource = DefaultMediaSourceFactory(factory)
                    .createMediaSource(MediaItem.fromUri(source.url))
                val active = ExoPlayer.Builder(appContext)
                    .setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(), false)
                    .build()
                player = active
                active.volume = mutableVolume.value
                active.addListener(object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) {
                        if (!isCurrent(token) || this@BgmPreviewController.player !== active) return
                        publishProgress(active)
                        if (active.isPlaying && progressJob?.isActive != true) {
                            progressJob = scope.launch {
                                while (isCurrent(token) && this@BgmPreviewController.player === active && active.isPlaying) {
                                    publishProgress(active)
                                    delay(250)
                                }
                            }
                        } else if (!active.isPlaying) {
                            progressJob?.cancel()
                            progressJob = null
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (!isCurrent(token) || player !== active) return
                        mutableState.value = BgmPlaybackState(
                            if (isPlaying) BgmPlaybackStatus.PLAYING else if (!active.playWhenReady)
                                BgmPlaybackStatus.PAUSED else BgmPlaybackStatus.LOADING,
                            track.key)
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (isCurrent(token) && playbackState == Player.STATE_ENDED) stop()
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        if (isCurrent(token)) finish(BgmPlaybackState(
                            BgmPlaybackStatus.ERROR, track.key, "${track.platform.label}暂不可试听"))
                    }
                })
                active.setMediaSource(mediaSource)
                active.prepare()
                active.play()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isCurrent(token)) {
                    val code = (error as? MusicProviderException)?.platformCode
                    finish(BgmPlaybackState(BgmPlaybackStatus.ERROR, track.key,
                        if (track.platform == MusicPlatform.LOCAL && error is MusicProviderException)
                            error.message.orEmpty()
                        else "${track.platform.label}暂不可试听${code?.let { " ($it)" }.orEmpty()}"))
                }
            }
        }
    }

    /** Call from the screen's onStop, on navigation away, or its explicit stop action. */
    fun stop() = onMain {
        finish(BgmPlaybackState())
    }

    fun togglePause() = onMain {
        val active = player ?: return@onMain
        if (released || current !== this) return@onMain
        if (active.playWhenReady) {
            active.pause()
            mutableState.value = mutableState.value.copy(status = BgmPlaybackStatus.PAUSED)
        } else {
            mutableState.value = mutableState.value.copy(status = BgmPlaybackStatus.LOADING)
            active.play()
        }
    }

    fun seekTo(positionMs: Long, expectedTrackKey: String? = null) = onMain {
        val active = player ?: return@onMain
        if (released || current !== this || active.playbackState != Player.STATE_READY ||
            expectedTrackKey != null && mutableState.value.trackKey != expectedTrackKey) return@onMain
        val position = mutableProgress.value.seekPosition(positionMs) ?: return@onMain
        active.seekTo(position)
        publishProgress(active)
    }

    /** Audition-only gain: never changes the saved BGM volume or the system volume. */
    fun setVolume(value: Float) = onMain {
        if (released) return@onMain
        val gain = previewGain(value)
        if (gain > 0f) lastAudibleVolume = gain
        mutableVolume.value = gain
        player?.volume = gain
    }

    fun toggleMute() = onMain {
        setVolume(if (mutableVolume.value > 0f) 0f else lastAudibleVolume)
    }

    private fun publishProgress(active: ExoPlayer) {
        mutableProgress.value = auditionProgress(
            active.currentPosition,
            active.duration,
            sourceDurationMs,
            sourceIsPreview,
            active.playbackState == Player.STATE_READY && active.isCurrentMediaItemSeekable,
        )
    }

    /** Primary resume takes over before its focus request; hosted auditions keep playing. */
    internal fun onPrimaryStarting() = onMain {
        if (ownsFocus) finish(BgmPlaybackState())
    }

    /** Call from the screen's onDestroy. Repeated release is safe. */
    fun release() = onMain {
        if (released) return@onMain
        finish(BgmPlaybackState())
        released = true
        scope.cancel()
    }

    private fun isCurrent(token: Long): Boolean =
        !released && current === this && request.isCurrent(token)

    private fun finish(state: BgmPlaybackState) {
        request.stop()
        closeAudio()
        mutableState.value = state
        mutableProgress.value = BgmPreviewProgress()
        if (current === this) current = null
        BgmPlaybackController.endPreview(this)
    }

    private fun closeAudio() {
        request.stop()
        loadJob?.cancel()
        loadJob = null
        progressJob?.cancel()
        progressJob = null
        player?.release()
        player = null
        sourceDurationMs = 0
        sourceIsPreview = false
        client?.let {
            // TLS shutdown may write to the socket, including when closing an audition from UI.
            Dispatchers.IO.asExecutor().execute {
                runCatching { it.dispatcher.cancelAll() }
                runCatching { it.connectionPool.evictAll() }
                runCatching { it.dispatcher.executorService.shutdown() }
            }
        }
        client = null
        if (receiverRegistered) {
            appContext.unregisterReceiver(noisyReceiver)
            receiverRegistered = false
        }
        if (ownsFocus) {
            ownsFocus = false
            focusRequest?.let { AudioManagerCompat.abandonAudioFocusRequest(audioManager, it) }
        }
        focusRequest = null
    }

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else scope.launch { action() }
    }

    internal companion object {
        private var current: BgmPreviewController? = null

        fun stopCurrentForHostChange() {
            current?.stop()
        }
    }
}

/** A late URL or player event from an old audition cannot revive it. */
internal class BgmPreviewRequestGate {
    private var generation = 0L
    private var active = false

    fun begin(): Long {
        active = true
        return ++generation
    }

    fun stop() {
        active = false
        generation++
    }

    fun isCurrent(token: Long): Boolean = active && generation == token
}

internal fun previewGain(volume: Float): Float =
    volume.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0.15f

/** Focus belongs to the active listening session, including preparation and buffering gaps. */
internal class BgmPreviewHostGate {
    var hostActive = false
        private set

    fun started() {
        hostActive = true
    }

    fun paused() {
        hostActive = false
    }
}
