package io.legado.app.ui.config

import android.content.Context
import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import io.legado.app.R
import io.legado.app.databinding.ItemTtsEngineBinding
import io.legado.app.help.tts.TtsEngineSetting
import io.legado.app.help.tts.TtsEngineType
import io.legado.app.lib.theme.accentColor
import io.legado.app.utils.dpToPx

object TtsEngineCardBinder {

    fun bind(
        context: Context,
        binding: ItemTtsEngineBinding,
        engine: TtsEngineSetting,
        trailing: Trailing = Trailing.DRAG
    ) = binding.run {
        textName.text = engine.name
        imageIcon.imageTintList = ColorStateList.valueOf(context.accentColor)
        textEnabled.setBackgroundResource(
            if (engine.enabled) R.drawable.ng_bg_tag_success else R.drawable.ng_bg_tag_warning
        )
        textEnabled.setTextColor(
            ContextCompat.getColor(
                context,
                if (engine.enabled) R.color.ng_success else R.color.ng_warning
            )
        )
        textEnabled.text = context.getString(
            if (engine.enabled) R.string.enabled else R.string.disabled
        )
        textType.text = when (engine.type) {
            TtsEngineType.SYSTEM -> context.getString(R.string.tts_engine_type_system)
            TtsEngineType.SCRIPT -> context.getString(R.string.tts_engine_type_script)
        }
        textVoiceCount.text = when {
            engine.type == TtsEngineType.SYSTEM ->
                context.getString(R.string.character_tts_system_default_voice)
            engine.effectiveVoices().isEmpty() ->
                context.getString(R.string.tts_engine_voice_not_loaded)
            else -> context.getString(
                R.string.tts_engine_voice_count,
                engine.effectiveVoices().size
            )
        }
        viewSelectionIndicator.isVisible = trailing == Trailing.SELECTED
        viewSelectionIndicator.backgroundTintList = ColorStateList.valueOf(context.accentColor)
        when (trailing) {
            Trailing.DRAG -> {
                imageDragHandle.isVisible = true
                imageDragHandle.setImageResource(R.drawable.ic_drag_handle)
                imageDragHandle.imageTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(context, R.color.ng_on_surface)
                )
                imageDragHandle.setPadding(
                    8.dpToPx(), 20.dpToPx(), 4.dpToPx(), 20.dpToPx()
                )
            }

            Trailing.SELECTED -> {
                imageDragHandle.isVisible = false
            }

            Trailing.NONE -> imageDragHandle.isVisible = false
        }
    }

    enum class Trailing {
        DRAG,
        SELECTED,
        NONE
    }
}
