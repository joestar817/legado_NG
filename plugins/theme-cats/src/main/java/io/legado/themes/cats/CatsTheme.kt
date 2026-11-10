package io.legado.themes.cats

import android.content.Context
import android.view.View
import io.legado.effect.api.*

class CatsTheme : EffectThemePlugin {
    override fun createSession(context: Context, assets: EffectThemeAssets, callbacks: EffectThemeCallbacks): EffectThemeSession {
        val view = ListeningCatsTextureView(context, assets, callbacks)
        return object : EffectThemeSession {
            private var released = false
            override fun getView(): View = view
            override fun update(parameters: Map<String, String>, animationAllowed: Boolean, timelineOriginNanos: Long?) {
                if (released) return
                val intensity = (parameters["intensity"]?.toDoubleOrNull()?.takeIf { it.isFinite() }
                    ?.let { kotlin.math.round(it).toInt() } ?: 100).coerceIn(0, 100)
                view.update(intensity, animationAllowed, timelineOriginNanos)
            }
            override fun release(onReleased: Runnable) {
                released = true
                view.dispose { onReleased.run() }
            }
        }
    }
}
