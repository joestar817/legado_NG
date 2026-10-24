package io.legado.app.help.bgm

import android.annotation.SuppressLint
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Looper
import android.os.SystemClock
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
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Music follows the listening service. An explicit foreground play can start it before speech;
 * the service takes over that same selection and position when listening starts.
 */
@SuppressLint("UnsafeOptInUsageError")
class BgmPlaybackController(context: Context, private val foregroundOnly: Boolean = false) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var owner = BgmRepository.claimPlaybackOwner()
    private val speech = BgmSpeechGate()
    private val previewFocus = BgmPreviewHostGate()
    private val failures = BgmFailureBudget()
    private var settings = BgmRepository.settings.value
    private var player: ExoPlayer? = null
    private var loadJob: Job? = null
    private var waitJob: Job? = null
    private var fadeJob: Job? = null
    private var progressJob: Job? = null
    private var generation = 0L
    private var loadedRevision = -1L
    private var loadingRevision = -1L
    private var sourceExpiresAt = 0L
    private var refreshedSource = false
    private var failedSession = false
    private var waitingForAdvance = false
    private var pausedByUser = false
    private var released = false
    private var previewOwner: BgmPreviewController? = null
    private var resumePositionMs = 0L
    private var resumeDurationMs = 0L
    private var targetGain = 0f
    private val mediaClients = mutableMapOf<MusicPlatform, OkHttpClient>()
    private var manualFocus: AudioFocusRequestCompat? = null
    private var noisyRegistered = false
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) onPaused()
        }
    }

    init {
        // The old Android service may be destroyed after its replacement is already created.
        val previous = current
        if (!foregroundOnly && previous?.foregroundOnly == true &&
            previous.settings.selectionRevision == settings.selectionRevision &&
            previous.settings.currentTrack?.key == settings.currentTrack?.key) {
            resumePositionMs = previous.player?.currentPosition?.coerceAtLeast(0L) ?: previous.resumePositionMs
            resumeDurationMs = previous.player?.duration?.takeIf { it > 0L } ?: previous.resumeDurationMs
            pausedByUser = previous.pausedByUser
        }
        previous?.release()
        BgmPreviewController.stopCurrentForHostChange()
        current = this
        scope.launch {
            BgmRepository.settings.collect { next ->
                if (!isOwner()) return@collect
                val selectionChanged = next.selectionRevision != settings.selectionRevision ||
                    next.currentTrack?.key != settings.currentTrack?.key
                val enabling = next.enabled && !settings.enabled
                settings = next
                if (selectionChanged) {
                    resumePositionMs = 0L
                    resumeDurationMs = 0L
                    waitingForAdvance = false
                    if (failedSession) failures.reset()
                    failedSession = false
                    refreshedSource = false
                    clearMedia()
                }
                if (enabling) {
                    pausedByUser = false
                    waitingForAdvance = false
                    failedSession = false
                    failures.reset()
                }
                reconcile()
            }
        }
    }

    /** Call before the primary service requests focus, including resume after a user pause. */
    fun onPrimaryStarting() = onMain {
        if (!isOwner()) return@onMain
        val wasManual = speech.manualPlayback
        speech.primaryStarting()
        releaseManualFocus()
        previewFocus.started()
        // Only a preview started without an active host owns focus. Ordinary speech segments
        // must leave a preview sharing the already-playing host's focus uninterrupted.
        previewOwner?.onPrimaryStarting()
        if (wasManual) reconcile()
    }

    /** Call only for real primary audio, never for a play request or a prepared media item. */
    fun onSpeechStarted() = onMain {
        if (!isOwner()) return@onMain
        releaseManualFocus()
        previewFocus.started()
        previewOwner?.onPrimaryStarting()
        speech.started()
        waitJob?.cancel()
        waitJob = null
        reconcile()
    }

    /** Repeated waiting events do not extend the grace period. */
    fun onSpeechWaiting() = onMain {
        if (!isOwner()) return@onMain
        speech.waiting(SystemClock.elapsedRealtime())
        if (waitJob == null) {
            val remaining = speech.remainingWait(SystemClock.elapsedRealtime()) ?: return@onMain
            waitJob = scope.launch {
                delay(remaining)
                waitJob = null
                reconcile()
            }
        }
    }

    /** Explicit pause, calls, unplugging and focus loss must stop both streams immediately. */
    fun onPaused() = onMain {
        if (!isOwner()) return@onMain
        speech.pause()
        releaseManualFocus()
        previewFocus.paused()
        previewOwner?.stop()
        waitJob?.cancel()
        waitJob = null
        fadeJob?.cancel()
        targetGain = 0f
        player?.let {
            it.volume = 0f
            it.pause()
        }
        publish(BgmPlaybackStatus.PAUSED)
    }

    fun release() = onMain {
        if (released) return@onMain
        released = true
        releaseManualFocus()
        previewFocus.paused()
        previewOwner?.stop()
        generation++
        scope.cancel()
        player?.release()
        player = null
        val clients = mediaClients.values.toList()
        mediaClients.clear()
        Dispatchers.IO.asExecutor().execute {
            clients.forEach { client ->
                runCatching { client.dispatcher.cancelAll() }
                runCatching { client.connectionPool.evictAll() }
                runCatching { client.dispatcher.executorService.shutdown() }
            }
        }
        if (current === this) current = null
        BgmRepository.releasePlaybackOwner(owner)
    }

    private fun isOwner(): Boolean = !released && BgmRepository.isPlaybackOwner(owner)

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else scope.launch { action() }
    }

    private fun shouldPlay(): Boolean = previewOwner == null && settings.enabled && !failedSession && !pausedByUser &&
        !waitingForAdvance && settings.currentTrack != null &&
        BgmRepository.settings.value.enabled &&
        BgmRepository.settings.value.selectionRevision == settings.selectionRevision &&
        speech.canPlay(SystemClock.elapsedRealtime())

    private fun reconcile() {
        if (!isOwner()) return
        if (previewOwner != null) {
            publish(BgmPlaybackStatus.PAUSED, "正在试听")
            return
        }
        if (!shouldPlay()) {
            releaseManualFocus()
            fadeTo(0f, pauseAfter = true)
            if (!failedSession) {
                publish(when {
                    !settings.enabled || settings.currentTrack == null -> BgmPlaybackStatus.IDLE
                    pausedByUser -> BgmPlaybackStatus.PAUSED
                    speech.paused && speech.hasSpeech -> BgmPlaybackStatus.PAUSED
                    else -> BgmPlaybackStatus.WAITING
                })
            }
            return
        }
        if (speech.manualPlayback && !previewFocus.hostActive && !requestManualFocus()) {
            speech.pause()
            publish(BgmPlaybackStatus.ERROR, "未获取到音频焦点")
            return
        }
        if (loadedRevision != settings.selectionRevision) {
            if (loadingRevision != settings.selectionRevision) loadCurrent()
            return
        }
        player?.let {
            it.repeatMode = if (settings.repeatMode == BgmRepeatMode.ONE) {
                Player.REPEAT_MODE_ONE
            } else Player.REPEAT_MODE_OFF
            // Seeking to the end while paused must not advance the queue until playback resumes.
            if (it.playbackState == Player.STATE_ENDED) {
                if (settings.repeatMode == BgmRepeatMode.ONE) it.seekTo(0L) else {
                    waitingForAdvance = true
                    advanceQueue(generation, settings.selectionRevision)
                    publish(BgmPlaybackStatus.LOADING)
                    return
                }
            }
            if (!it.playWhenReady) {
                it.volume = 0f
                it.play()
            }
            fadeTo(settings.volume.takeIf { value -> value.isFinite() }?.coerceIn(0f, 1f) ?: 0.15f)
            publish(if (it.isPlaying) BgmPlaybackStatus.PLAYING else BgmPlaybackStatus.LOADING)
        }
    }

    private fun createPlayer(): ExoPlayer = ExoPlayer.Builder(appContext)
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
            false,
        )
        .build()

    /** Same foreground focus/noisy handling as audition; normal listening keeps its own focus. */
    private fun requestManualFocus(): Boolean {
        if (manualFocus != null) return true
        val manager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        lateinit var request: AudioFocusRequestCompat
        request = AudioFocusRequestCompat.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributesCompat.Builder()
                .setUsage(AudioAttributesCompat.USAGE_MEDIA)
                .setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener { change ->
                if (isOwner() && manualFocus === request && change != AudioManager.AUDIOFOCUS_GAIN &&
                    change != AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) onPaused()
            }.build()
        manualFocus = request
        val granted = runCatching {
            AudioManagerCompat.requestAudioFocus(manager, request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }.getOrDefault(false)
        if (!granted) { manualFocus = null; return false }
        try {
            ContextCompat.registerReceiver(appContext, noisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
            noisyRegistered = true
        } catch (_: Exception) {
            releaseManualFocus()
            return false
        }
        return true
    }

    private fun releaseManualFocus() {
        val request = manualFocus
        manualFocus = null
        if (noisyRegistered) { appContext.unregisterReceiver(noisyReceiver); noisyRegistered = false }
        if (request != null) {
            val manager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            AudioManagerCompat.abandonAudioFocusRequest(manager, request)
        }
    }

    private fun loadCurrent(positionMs: Long = resumePositionMs) {
        val track = settings.currentTrack ?: return
        val revision = settings.selectionRevision
        val requestGeneration = ++generation
        loadJob?.cancel()
        stopProgressUpdates()
        resumePositionMs = positionMs
        resumeDurationMs = player?.duration?.takeIf { it > 0L } ?: resumeDurationMs
        loadingRevision = revision
        loadedRevision = -1L
        player?.release()
        player = null
        fadeJob?.cancel()
        targetGain = 0f
        publish(BgmPlaybackStatus.LOADING)
        loadJob = scope.launch {
            try {
                var source = withContext(Dispatchers.IO) { BgmRepository.resolve(track) }
                if (source.expiresAt > 0 && source.expiresAt <= System.currentTimeMillis() && !refreshedSource) {
                    refreshedSource = true
                    source = withContext(Dispatchers.IO) { BgmRepository.resolve(track) }
                }
                if (!isCurrent(requestGeneration, revision)) return@launch
                sourceExpiresAt = source.expiresAt
                val dataSource = DefaultDataSource.Factory(
                    appContext,
                    OkHttpDataSource.Factory(mediaClient(track.platform)).setDefaultRequestProperties(source.headers),
                )
                val mediaSource = DefaultMediaSourceFactory(dataSource)
                    .createMediaSource(MediaItem.fromUri(source.url))
                val active = createPlayer()
                player = active
                active.volume = 0f
                active.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (!isCurrent(requestGeneration, revision) || player !== active) return
                        if (isPlaying) {
                            failures.reset()
                            publish(BgmPlaybackStatus.PLAYING)
                        }
                        syncProgressUpdates()
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (!isCurrent(requestGeneration, revision) || player !== active) return
                        if (playbackState == Player.STATE_ENDED &&
                            canAdvanceBgmAtEnd(active.playWhenReady, shouldPlay(), waitingForAdvance)
                        ) {
                            waitingForAdvance = true
                            advanceQueue(requestGeneration, revision)
                        }
                    }

                    override fun onEvents(player: Player, events: Player.Events) {
                        if (!isCurrent(requestGeneration, revision) || this@BgmPlaybackController.player !== active) return
                        syncProgressUpdates()
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        if (!isCurrent(requestGeneration, revision) || player !== active) return
                        val code = generateSequence<Throwable>(error) { it.cause }
                            .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
                            .firstOrNull()?.responseCode
                        val expired = sourceExpiresAt > 0 && sourceExpiresAt <= System.currentTimeMillis()
                        if (track.platform != MusicPlatform.LOCAL && !refreshedSource &&
                            (expired || code in setOf(401, 403, 410))) {
                            refreshedSource = true
                            loadCurrent(active.currentPosition.coerceAtLeast(0L))
                        } else failTrack(requestGeneration, revision, code)
                    }
                })
                active.setMediaSource(mediaSource)
                if (positionMs > 0) active.seekTo(positionMs)
                loadedRevision = revision
                loadingRevision = -1L
                active.prepare()
                reconcile()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                failTrack(requestGeneration, revision, (error as? MusicProviderException)?.platformCode,
                    if (track.platform == MusicPlatform.LOCAL && error is MusicProviderException) error.message else null)
            }
        }
    }

    private fun isCurrent(requestGeneration: Long, revision: Long): Boolean =
        isOwner() && previewOwner == null && generation == requestGeneration && settings.selectionRevision == revision &&
            BgmRepository.settings.value.selectionRevision == revision

    private fun failTrack(requestGeneration: Long, revision: Long, code: Int? = null, detail: String? = null) {
        if (!isCurrent(requestGeneration, revision)) return
        // A failed player cannot continue independently while the queue advances asynchronously.
        stopProgressUpdates()
        fadeJob?.cancel()
        targetGain = 0f
        val failedPlayer = player
        player = null
        failedPlayer?.release()
        loadingRevision = -1L
        loadedRevision = -1L
        if (failures.failed(settings.queue.size)) {
            failedSession = true
            releaseManualFocus()
            val platform = settings.currentTrack?.platform?.label ?: "背景音乐"
            publish(BgmPlaybackStatus.ERROR, detail?.takeIf(String::isNotBlank)
                ?: "${platform}暂不可播放${code?.let { " ($it)" }.orEmpty()}")
        } else {
            waitingForAdvance = true
            publish(BgmPlaybackStatus.LOADING, "正在切换下一首")
            advanceQueue(requestGeneration, revision)
        }
    }

    private fun advanceQueue(requestGeneration: Long, revision: Long) {
        val completion = BgmRepository.advanceIfCurrent(owner, revision)
        scope.launch {
            if (!completion.await() && isCurrent(requestGeneration, revision)) {
                waitingForAdvance = false
                failedSession = true
                publish(BgmPlaybackStatus.ERROR, "无法保存背景音乐设置")
            }
        }
    }

    private fun clearMedia() {
        generation++
        loadJob?.cancel()
        fadeJob?.cancel()
        stopProgressUpdates()
        player?.release()
        player = null
        loadingRevision = -1L
        loadedRevision = -1L
        sourceExpiresAt = 0L
        targetGain = 0f
        publishProgress()
    }

    private fun fadeTo(gain: Float, pauseAfter: Boolean = false) {
        val active = player ?: return
        if (targetGain == gain && fadeJob?.isActive == true) return
        if (targetGain == gain && active.volume == gain) {
            if (pauseAfter) active.pause()
            return
        }
        fadeJob?.cancel()
        targetGain = gain
        val initial = active.volume
        fadeJob = scope.launch {
            repeat(20) { step ->
                delay(20)
                if (!isOwner() || player !== active) return@launch
                active.volume = initial + (gain - initial) * ((step + 1) / 20f)
            }
            if (pauseAfter) active.pause()
        }
    }

    private fun publish(status: BgmPlaybackStatus, message: String = "") {
        BgmRepository.publishPlayback(owner, BgmPlaybackState(status, settings.currentTrack?.key, message))
        syncProgressUpdates()
    }

    private fun matchesSelection(expectedTrackKey: String, expectedRevision: Long): Boolean {
        val latest = BgmRepository.settings.value
        return isOwner() && previewOwner == null && settings.enabled && latest.enabled &&
            settings.currentTrack?.key == expectedTrackKey && latest.currentTrack?.key == expectedTrackKey &&
            settings.selectionRevision == expectedRevision && latest.selectionRevision == expectedRevision
    }

    private fun currentProgress(): BgmPlaybackProgress {
        val trackKey = settings.currentTrack?.key
        if (trackKey == null || !matchesSelection(trackKey, settings.selectionRevision)) {
            return BgmPlaybackProgress()
        }
        val active = player?.takeIf { loadedRevision == settings.selectionRevision }
        val actualDuration = active?.duration?.takeIf { it > 0L }
        if (actualDuration != null) resumeDurationMs = actualDuration
        return formalBgmProgress(
            trackKey = trackKey,
            selectionRevision = settings.selectionRevision,
            positionMs = active?.currentPosition ?: resumePositionMs,
            mediaDurationMs = actualDuration ?: resumeDurationMs,
            seekable = active != null && actualDuration != null && !failedSession && !waitingForAdvance &&
                (active.playbackState == Player.STATE_READY || active.playbackState == Player.STATE_ENDED) &&
                active.isCurrentMediaItemSeekable,
            canTogglePause = !failedSession && !waitingForAdvance,
            pausedByUser = pausedByUser,
        )
    }

    private fun publishProgress() {
        if (isOwner()) BgmRepository.publishProgress(owner, currentProgress())
    }

    private fun syncProgressUpdates() {
        publishProgress()
        val active = player
        if (active == null || !active.isPlaying || !isOwner() || previewOwner != null) {
            stopProgressUpdates()
            return
        }
        if (progressJob?.isActive == true) return
        val requestGeneration = generation
        val revision = loadedRevision
        progressJob = scope.launch {
            while (isCurrent(requestGeneration, revision) && player === active && active.isPlaying) {
                delay(250L)
                if (isCurrent(requestGeneration, revision) && player === active && active.isPlaying) {
                    publishProgress()
                }
            }
        }
    }

    private fun stopProgressUpdates() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun togglePause(expectedTrackKey: String, expectedRevision: Long) = onMain {
        if (!matchesSelection(expectedTrackKey, expectedRevision) || !currentProgress().canTogglePause) return@onMain
        pausedByUser = !pausedByUser && speech.canPlay(SystemClock.elapsedRealtime())
        if (pausedByUser) {
            releaseManualFocus()
            fadeJob?.cancel()
            targetGain = 0f
            player?.let {
                it.volume = 0f
                it.pause()
            }
            publish(BgmPlaybackStatus.PAUSED)
        } else {
            if (!speech.canPlay(SystemClock.elapsedRealtime())) speech.playManually()
            reconcile()
        }
    }

    private fun seekTo(positionMs: Long, expectedTrackKey: String, expectedRevision: Long) = onMain {
        if (!matchesSelection(expectedTrackKey, expectedRevision)) return@onMain
        val active = player ?: return@onMain
        val position = currentProgress().seekPosition(positionMs, expectedTrackKey, expectedRevision) ?: return@onMain
        active.seekTo(position)
        // seekTo does not change playWhenReady or the primary speech gate.
        syncProgressUpdates()
    }

    /** Cancels old callbacks and queued auto-advance before the preview can start. */
    private fun suspendForPreview(preview: BgmPreviewController) {
        if (previewOwner === preview) return
        previewOwner = preview
        releaseManualFocus()
        owner = BgmRepository.claimPlaybackOwner()
        val latest = BgmRepository.settings.value
        val sameSelection = settings.selectionRevision == latest.selectionRevision &&
            settings.currentTrack?.key == latest.currentTrack?.key
        val position = if (sameSelection) {
            player?.currentPosition?.coerceAtLeast(0L) ?: resumePositionMs
        } else 0L
        val duration = if (sameSelection) {
            player?.duration?.takeIf { it > 0L } ?: resumeDurationMs
        } else 0L
        clearMedia()
        resumePositionMs = position
        resumeDurationMs = duration
        waitingForAdvance = false
        publish(BgmPlaybackStatus.PAUSED, "正在试听")
    }

    private fun resumeFromPreview(preview: BgmPreviewController) {
        if (previewOwner !== preview) return
        previewOwner = null
        reconcile()
    }

    private fun mediaClient(platform: MusicPlatform): OkHttpClient =
        mediaClients.getOrPut(platform) { newBgmMediaClient(platform) }

    internal companion object {
        private var current: BgmPlaybackController? = null

        fun toggleCurrentPause(context: Context, expectedTrackKey: String, expectedRevision: Long) {
            val settings = BgmRepository.settings.value
            if (!settings.enabled || settings.currentTrack?.key != expectedTrackKey ||
                settings.selectionRevision != expectedRevision) return
            val host = current?.takeIf { it.isOwner() } ?: BgmPlaybackController(context, foregroundOnly = true)
            host.togglePause(expectedTrackKey, expectedRevision)
        }

        /** A pre-listening player belongs to the visible listening screen, not a new service. */
        fun releaseForegroundPlayback() {
            current?.takeIf { it.foregroundOnly }?.release()
        }

        fun seekCurrentTo(positionMs: Long, expectedTrackKey: String, expectedRevision: Long) {
            current?.seekTo(positionMs, expectedTrackKey, expectedRevision)
        }

        /** Main-thread only. A paused service exists but no longer supplies audio focus. */
        fun beginPreview(preview: BgmPreviewController): Boolean {
            val host = current?.takeIf { it.isOwner() } ?: return false
            host.suspendForPreview(preview)
            return host.previewFocus.hostActive
        }

        fun endPreview(preview: BgmPreviewController) {
            current?.resumeFromPreview(preview)
        }
    }

}

internal fun isAllowedBgmMediaAddress(platform: MusicPlatform, address: String): Boolean {
    val uri = runCatching { URI(address) }.getOrNull() ?: return false
    if (uri.scheme !in listOf("http", "https") || uri.userInfo != null ||
        uri.port !in listOf(-1, 80, 443)
    ) return false
    val host = uri.host.orEmpty().lowercase()
    return when (platform) {
        MusicPlatform.NETEASE -> host == "music.126.net" || host.endsWith(".music.126.net") ||
            host == "music.163.com" || host.endsWith(".music.163.com")
        MusicPlatform.QQ -> host.endsWith(".qq.com") || host.endsWith(".qqmusic.com")
        MusicPlatform.LOCAL -> false
    }
}

/** Account cookies, permissive TLS and general request logging never enter media requests. */
internal fun newBgmMediaClient(platform: MusicPlatform): OkHttpClient = OkHttpClient.Builder()
    .cookieJar(CookieJar.NO_COOKIES)
    .addNetworkInterceptor { chain ->
        val request = chain.request()
        if (!isAllowedBgmMediaAddress(platform, request.url.toString())) {
            throw IOException("音乐地址不可用")
        }
        chain.proceed(request)
    }
    .build()
