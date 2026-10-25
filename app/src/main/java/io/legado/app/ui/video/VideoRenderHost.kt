package io.legado.app.ui.video

import android.content.Context
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.help.gsyVideo.VideoPlaybackBridge
import io.legado.app.help.gsyVideo.VideoPlaybackState
import io.legado.app.help.gsyVideo.BiliDanmukuParser
import io.legado.app.help.gsyVideo.DanmakuAdapter
import io.legado.app.model.VideoPlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import master.flame.danmaku.controller.DrawHandler
import master.flame.danmaku.danmaku.loader.android.DanmakuLoaderFactory
import master.flame.danmaku.danmaku.model.BaseDanmaku
import master.flame.danmaku.danmaku.model.DanmakuTimer
import master.flame.danmaku.danmaku.model.IDisplayer
import master.flame.danmaku.danmaku.model.android.DanmakuContext
import master.flame.danmaku.danmaku.model.android.SpannedCacheStuffer
import master.flame.danmaku.ui.widget.DanmakuView
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

/** Only the output texture and danmaku renderer are Views; all controls live in Compose. */
@Composable
fun VideoRenderHost(owner: Any, state: VideoPlaybackState, danmakuVisible: Boolean,
                    modifier: Modifier = Modifier, allowDanmaku: Boolean = true) {
    Box(modifier) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
            TextureView(context).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    private var output: Surface? = null
                    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                        output = Surface(texture).also { VideoPlaybackBridge.attach(owner, it) }
                    }
                    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit
                    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                        output?.let { VideoPlaybackBridge.detach(owner, it); it.release() }
                        output = null
                        return true
                    }
                }
            }
        }, update = { texture ->
            val width = texture.width.toFloat()
            val height = texture.height.toFloat()
            if (width > 0 && height > 0) {
                val matrix = android.graphics.Matrix()
                matrix.postRotate(state.rotation.toFloat(), width / 2, height / 2)
                if (state.rotation % 180 != 0) matrix.postScale(width / height, height / width, width / 2, height / 2)
                texture.setTransform(matrix)
            }
        })
        if (allowDanmaku && (VideoPlay.danmakuFile != null || !VideoPlay.danmakuStr.isNullOrBlank())) {
            key(state.mediaRevision) { VideoDanmaku(state, danmakuVisible) }
        }
    }
}

@Composable
private fun VideoDanmaku(state: VideoPlaybackState, visible: Boolean) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val view = remember { VideoDanmakuView(context) }
    val currentState by rememberUpdatedState(state)
    val currentVisible by rememberUpdatedState(visible)
    var prepared by remember { mutableStateOf(false) }
    var lastSeekRevision by remember { mutableStateOf<Int?>(null) }
    DisposableEffect(view) { onDispose { view.dispose() } }
    LaunchedEffect(view) {
        val file = VideoPlay.danmakuFile
        val text = VideoPlay.danmakuStr
        val parser = withContext(Dispatchers.IO) {
            val loader = DanmakuLoaderFactory.create(DanmakuLoaderFactory.TAG_BILI)
            runCatching {
                if (file != null) loader.load(file.inputStream())
                else if (text != null && text.startsWith("http", true)) loader.load(text)
                else loader.load(text.orEmpty().byteInputStream())
                BiliDanmukuParser().apply { load(loader.dataSource) }
            }.getOrNull()
        } ?: return@LaunchedEffect
        val config = DanmakuContext.create()
            .setDanmakuStyle(IDisplayer.DANMAKU_STYLE_STROKEN, 3f)
            .setDuplicateMergingEnabled(false)
            .setScrollSpeedFactor(VideoPlay.danmakuSpeed)
            .setScaleTextSize(1f)
            .setCacheStuffer(SpannedCacheStuffer(), DanmakuAdapter(view))
            .setMaximumLines(hashMapOf(BaseDanmaku.TYPE_SCROLL_RL to 5))
            .preventOverlapping(hashMapOf(BaseDanmaku.TYPE_SCROLL_RL to true, BaseDanmaku.TYPE_FIX_TOP to true))
        view.setCallback(object : DrawHandler.Callback {
            override fun updateTimer(timer: DanmakuTimer?) = Unit
            override fun drawingFinished() = Unit
            override fun danmakuShown(danmaku: BaseDanmaku?) = Unit
            override fun prepared() {
                view.post {
                    if (!view.disposed) {
                        view.start(currentState.position)
                        if (!currentVisible) view.hide()
                        prepared = true
                    }
                }
            }
        })
        view.enableDanmakuDrawingCache(true)
        view.prepare(parser, config)
    }
    LaunchedEffect(prepared, state.playing, state.buffering, state.seekRevision, visible, state.position, state.speed) {
        if (prepared) {
            if (visible) view.show() else view.hide()
            val seek = lastSeekRevision != state.seekRevision ||
                abs(view.currentTime - state.position) > 1000 || state.speed != 1f
            view.syncPlayback(if (seek) state.position else null, state.playing && !state.buffering)
            lastSeekRevision = state.seekRevision
        }
    }
    AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
}

/** DFM's START/SEEK handlers resume drawing and remove queued PAUSE messages. */
private class VideoDanmakuView(context: Context) : DanmakuView(context) {
    private val playbackRevision = AtomicLong()
    private val pendingSeek = AtomicReference<Long?>(null)
    @Volatile
    var disposed = false
        private set

    fun syncPlayback(position: Long?, running: Boolean) {
        val revision = playbackRevision.incrementAndGet()
        if (position != null) pendingSeek.set(position)
        val drawHandler = handler ?: return
        drawHandler.post {
            if (isCurrent(revision, drawHandler)) {
                // State-only updates retain an explicit seek until a current handler task consumes it.
                pendingSeek.getAndSet(null)?.let { seekTo(it) }
                // Queue the state update after SEEK, so its PAUSE cannot be removed by SEEK/START.
                drawHandler.post {
                    if (isCurrent(revision, drawHandler)) {
                        // RESUME resets DFM's clock; position polling must not repeatedly resume it.
                        if (running) {
                            if (drawHandler.isStop) drawHandler.resume()
                        } else if (!drawHandler.isStop) {
                            drawHandler.pause()
                        }
                    }
                }
            }
        }
    }

    private fun isCurrent(revision: Long, drawHandler: DrawHandler): Boolean =
        !disposed && playbackRevision.get() == revision && handler === drawHandler

    fun dispose() {
        disposed = true
        playbackRevision.incrementAndGet()
        pendingSeek.set(null)
        release()
    }
}
