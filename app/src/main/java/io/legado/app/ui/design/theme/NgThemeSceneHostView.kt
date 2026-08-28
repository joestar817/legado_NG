package io.legado.app.ui.design.theme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.AttributeSet
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner
import io.legado.app.R
import io.legado.app.help.config.ListeningCartoonType
import io.legado.app.help.config.NgThemeLibraryStore
import io.legado.app.help.config.NgThemeSceneProfile
import io.legado.app.ui.book.read.aloud.ListeningCartoonTextureHost
import io.legado.app.ui.book.read.aloud.availableCartoonTypes
import io.legado.app.ui.book.read.aloud.createCartoonMotionTextureView
import io.legado.app.ui.book.read.aloud.motionEnvironmentAllowed

/** Page-level host for the animated scene owned by the active NG theme. */
internal class NgThemeSceneHostView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private var profile: NgThemeSceneProfile? = null
    private var sceneType: ListeningCartoonType? = null
    private var textureView: TextureView? = null
    private var textureHost: ListeningCartoonTextureHost? = null
    private var hostActive = false
    private var sessionGranted = false
    private var sceneRevision = 0L
    private var receiverRegistered = false
    private var powerSave = powerManager?.isPowerSaveMode == true

    private val powerSaveReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            powerSave = powerManager?.isPowerSaveMode == true
            updateRenderer()
        }
    }

    init {
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = false
        isFocusable = false
        visibility = View.GONE
    }

    fun bind(value: NgThemeSceneProfile?) {
        profile = value?.normalized()?.takeIf { it.sceneType() != null }
        if (sessionGranted) refreshScene()
    }

    fun setHostActive(active: Boolean) {
        if (hostActive == active) return
        hostActive = active
        if (active && isAttachedToWindow) {
            NgThemeSceneCoordinator.activate(this)
        } else {
            NgThemeSceneCoordinator.deactivate(this)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        registerPowerSaveReceiver()
        if (hostActive) NgThemeSceneCoordinator.activate(this)
    }

    override fun onDetachedFromWindow() {
        hostActive = false
        NgThemeSceneCoordinator.deactivate(this)
        unregisterPowerSaveReceiver()
        super.onDetachedFromWindow()
    }

    internal fun isSessionEligible(): Boolean = hostActive && isAttachedToWindow

    internal fun grantSceneSession() {
        if (!isSessionEligible()) {
            NgThemeSceneCoordinator.deactivate(this)
            return
        }
        sessionGranted = true
        refreshScene()
    }

    internal fun releaseSceneSession(onReleased: () -> Unit) {
        sessionGranted = false
        sceneRevision++
        releaseScene(onReleased)
    }

    private fun refreshScene() {
        if (!sessionGranted || !isSessionEligible()) return
        val requestedType = profile?.sceneType()?.takeIf {
            it in context.availableCartoonTypes()
        }
        if (requestedType == sceneType) {
            updateRenderer()
            return
        }
        val revision = ++sceneRevision
        releaseScene {
            if (
                revision == sceneRevision &&
                sessionGranted &&
                isSessionEligible() &&
                requestedType != null
            ) {
                createScene(requestedType)
                updateRenderer()
            }
        }
    }

    private fun createScene(type: ListeningCartoonType) {
        val view = context.createCartoonMotionTextureView(type)
        view.layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT,
        )
        addView(view)
        sceneType = type
        textureView = view
        textureHost = view as ListeningCartoonTextureHost
        visibility = View.VISIBLE
    }

    private fun updateRenderer() {
        val currentProfile = profile ?: return
        textureHost?.update(
            intensity = currentProfile.intensity,
            animationAllowed = motionEnvironmentAllowed(
                resumed = sessionGranted && isSessionEligible(),
                powerSave = powerSave,
            ),
            timelineOriginNanos = NgThemeSceneCoordinator.timelineOriginNanos,
        )
    }

    private fun releaseScene(onReleased: () -> Unit = {}) {
        val oldHost = textureHost
        val oldView = textureView
        textureHost = null
        textureView = null
        sceneType = null
        if (oldHost == null) {
            visibility = View.GONE
            onReleased()
            return
        }
        oldHost.release {
            mainHandler.post(onReleased)
        }
        visibility = View.GONE
        oldView?.let(::removeView)
    }

    private fun registerPowerSaveReceiver() {
        if (receiverRegistered) return
        ContextCompat.registerReceiver(
            context,
            powerSaveReceiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        receiverRegistered = true
    }

    private fun unregisterPowerSaveReceiver() {
        if (!receiverRegistered) return
        runCatching { context.unregisterReceiver(powerSaveReceiver) }
        receiverRegistered = false
    }

    private companion object {
        val mainHandler = Handler(Looper.getMainLooper())
    }
}

/** Compose entrypoint for the one full-screen page that does not inherit BaseActivity. */
@Composable
internal fun NgThemeSceneBackground(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = remember(view) { view.findViewTreeLifecycleOwner() }
    val libraryState by remember(context) {
        NgThemeLibraryStore.observe(context)
    }.collectAsState()
    val profile = remember(context, libraryState) {
        NgThemeLibraryStore.activeTheme(context)?.sceneProfile
            ?.normalized()
            ?.takeIf { it.sceneType() in context.availableCartoonTypes() }
    } ?: return
    val sceneHost = remember(context) {
        NgThemeSceneHostView(context).apply {
            id = R.id.ng_liquid_glass_backdrop_source
            bind(profile)
        }
    }

    AndroidView(
        factory = { sceneHost },
        modifier = modifier,
        update = { it.bind(profile) },
    )
    DisposableEffect(sceneHost, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> sceneHost.setHostActive(true)
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP ->
                    sceneHost.setHostActive(false)
                else -> Unit
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        sceneHost.setHostActive(
            lifecycleOwner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true,
        )
        onDispose {
            lifecycleOwner?.lifecycle?.removeObserver(observer)
            sceneHost.setHostActive(false)
        }
    }
}

/**
 * Keeps at most one page-level theme scene alive while Activities hand the foreground over.
 * Renderer teardown is asynchronous, so the next host is granted only after the previous GL
 * session has released its resources.
 */
private object NgThemeSceneCoordinator {

    val timelineOriginNanos: Long = System.nanoTime()

    private var activeHost: NgThemeSceneHostView? = null
    private var pendingHost: NgThemeSceneHostView? = null
    private var releaseInProgress = false

    fun activate(host: NgThemeSceneHostView) {
        pendingHost = host
        if (releaseInProgress) return
        if (activeHost === host) {
            pendingHost = null
            host.grantSceneSession()
            return
        }
        val previous = activeHost
        if (previous == null) {
            grantPendingHost()
            return
        }
        activeHost = null
        releaseInProgress = true
        previous.releaseSceneSession(::onReleaseCompleted)
    }

    fun deactivate(host: NgThemeSceneHostView) {
        if (pendingHost === host) pendingHost = null
        if (activeHost !== host) return
        activeHost = null
        if (releaseInProgress) return
        releaseInProgress = true
        host.releaseSceneSession(::onReleaseCompleted)
    }

    private fun onReleaseCompleted() {
        releaseInProgress = false
        grantPendingHost()
    }

    private fun grantPendingHost() {
        val next = pendingHost?.takeIf(NgThemeSceneHostView::isSessionEligible)
        pendingHost = null
        if (next == null) return
        activeHost = next
        next.grantSceneSession()
    }
}
