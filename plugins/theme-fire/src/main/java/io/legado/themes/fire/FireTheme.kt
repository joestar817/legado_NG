package io.legado.themes.fire

import android.content.Context
import android.view.View
import io.legado.effect.api.*

class FireTheme : EffectThemePlugin {
    override fun createSession(context: Context, assets: EffectThemeAssets, callbacks: EffectThemeCallbacks): EffectThemeSession {
        val view = ListeningFireTextureView(context, assets, callbacks)
        return object : EffectThemeSession {
            private var released = false
            override fun getView(): View = view
            override fun update(parameters: Map<String, String>, animationAllowed: Boolean, timelineOriginNanos: Long?) {
                if (released) return
                val intensity = (parameters["intensity"]?.toDoubleOrNull()?.takeIf { it.isFinite() }
                    ?.let { kotlin.math.round(it).toInt() } ?: 40).coerceIn(0, 100)
                val mode = parameters["colorMode"] ?: "cover"
                val color = when (mode) {
                    "original" -> -0x0000FAE7
                    "custom" -> parseColor(parameters["color"], -3065305)
                    else -> {
                        val source = com.materialkolor.hct.Hct.fromInt(parseColor(parameters["host.coverColor"]
                            ?: parameters["host.primaryColor"], -3065305))
                        com.materialkolor.hct.Hct.from(source.hue, kotlin.math.max(source.chroma, 72.0), 43.0).toInt()
                    }
                }
                view.setAnimationAllowed(animationAllowed)
                view.update(ListeningFireStyle.fromStorage(parameters["style"]), intensity, color, mode != "original")
            }
            override fun release(onReleased: Runnable) {
                released = true
                view.dispose { onReleased.run() }
            }
        }
    }
}

private fun parseColor(value: String?, fallback: Int): Int = value?.toIntOrNull()
    ?: value?.let { runCatching { android.graphics.Color.parseColor(it) }.getOrNull() } ?: fallback
