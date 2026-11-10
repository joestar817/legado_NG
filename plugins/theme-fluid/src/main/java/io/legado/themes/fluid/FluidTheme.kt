package io.legado.themes.fluid

import android.content.Context
import android.view.View
import io.legado.effect.api.*

class FluidTheme : EffectThemePlugin {
    override fun createSession(context: Context, assets: EffectThemeAssets, callbacks: EffectThemeCallbacks): EffectThemeSession {
        val view = ListeningFluidTextureView(context, assets, callbacks)
        return object : EffectThemeSession {
            private var released = false
            override fun getView(): View = view
            override fun update(parameters: Map<String, String>, animationAllowed: Boolean, timelineOriginNanos: Long?) {
                if (released) return
                val intensity = (parameters["intensity"]?.toDoubleOrNull()?.takeIf { it.isFinite() }
                    ?.let { kotlin.math.round(it).toInt() } ?: 100).coerceIn(0, 100)
                view.setAnimationAllowed(animationAllowed)
                view.update(ListeningFluidType.fromStorage(parameters["style"]), intensity)
            }
            override fun release(onReleased: Runnable) {
                released = true
                view.dispose { onReleased.run() }
            }
        }
    }
}
