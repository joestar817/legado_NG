package io.legado.app.ui.config

import android.content.Context
import android.content.res.ColorStateList
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import io.legado.app.R
import io.legado.app.databinding.ItemTtsVoiceBinding
import io.legado.app.help.tts.TtsEngineSetting
import io.legado.app.help.tts.TtsVoice
import io.legado.app.utils.dpToPx

object TtsVoiceCardBinder {

    fun bind(
        context: Context,
        binding: ItemTtsVoiceBinding,
        item: TtsVoice,
        engine: TtsEngineSetting?,
        isSystemEngine: Boolean,
        showControls: Boolean
    ) {
        binding.textName.text = item.name
        bindVoiceHeaderTags(context, binding, item, isSystemEngine)
        bindVoiceTags(context, binding, item, engine, isSystemEngine)
        binding.switchEnabled.isVisible = showControls
        binding.imagePreview.isVisible = showControls
    }

    private fun bindVoiceHeaderTags(
        context: Context,
        binding: ItemTtsVoiceBinding,
        item: TtsVoice,
        isSystemEngine: Boolean
    ) {
        bindGenderIcon(context, binding, item.takeUnless { isSystemEngine }?.gender)
        val languageLabels = item.takeUnless { isSystemEngine }
            ?.language
            ?.let { voiceLanguageLabels(it) }
            .orEmpty()
        binding.layoutLanguageTags.removeAllViews()
        binding.layoutLanguageTags.isVisible = languageLabels.isNotEmpty()
        languageLabels.forEach { label ->
            binding.layoutLanguageTags.addView(
                createLanguageTagView(binding.layoutLanguageTags, label)
            )
        }
        binding.layoutHeaderTags.removeAllViews()
        val style = item.takeUnless { isSystemEngine }?.style?.takeIf { it.isNotBlank() }
        binding.layoutHeaderTags.isVisible = style != null
        style?.let {
            binding.layoutHeaderTags.addView(
                createVoiceTagView(context, binding.layoutHeaderTags, coloredVoiceTag(it, 0))
            )
        }
    }

    private fun bindGenderIcon(context: Context, binding: ItemTtsVoiceBinding, gender: String?) {
        when (gender?.takeIf { it.isNotBlank() }?.lowercase()) {
            "male", "man" -> {
                binding.imageGender.isVisible = true
                binding.imageGender.setImageResource(R.drawable.ic_tts_gender_male)
                binding.imageGender.imageTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(context, R.color.ng_tts_gender_male)
                )
            }
            "female", "woman" -> {
                binding.imageGender.isVisible = true
                binding.imageGender.setImageResource(R.drawable.ic_tts_gender_female)
                binding.imageGender.imageTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(context, R.color.ng_tts_gender_female)
                )
            }
            else -> {
                binding.imageGender.isVisible = false
                binding.imageGender.setImageDrawable(null)
                binding.imageGender.imageTintList = null
            }
        }
    }

    private fun bindVoiceTags(
        context: Context,
        binding: ItemTtsVoiceBinding,
        item: TtsVoice,
        engine: TtsEngineSetting?,
        isSystemEngine: Boolean
    ) {
        val container = binding.layoutTags
        container.removeAllViews()
        val tags = if (isSystemEngine) {
            listOf(VoiceTag(engine?.name.orEmpty().ifBlank { item.id }))
        } else {
            buildVoiceTags(item)
        }
        binding.scrollTags.isVisible = tags.isNotEmpty()
        tags.forEach { tag ->
            container.addView(createVoiceTagView(context, container, tag))
        }
    }

    private fun buildVoiceTags(item: TtsVoice): List<VoiceTag> {
        val values = item.tags.filter { it.isNotBlank() }.distinct()
        if (values.isEmpty() && item.style.isNullOrBlank()) {
            return listOf(VoiceTag(item.id))
        }
        return values.mapIndexed { index, value -> coloredVoiceTag(value, index) }
    }

    private fun coloredVoiceTag(text: String, index: Int): VoiceTag {
        return when (index % 5) {
            0 -> VoiceTag(text, R.drawable.ng_bg_tts_voice_tag_blue, R.color.ng_tts_tag_blue)
            1 -> VoiceTag(text, R.drawable.ng_bg_tts_voice_tag_purple, R.color.ng_tts_tag_purple)
            2 -> VoiceTag(text, R.drawable.ng_bg_tts_voice_tag_orange, R.color.ng_tts_tag_orange)
            3 -> VoiceTag(text, R.drawable.ng_bg_tts_voice_tag_green, R.color.ng_tts_tag_green)
            else -> VoiceTag(text, R.drawable.ng_bg_tts_voice_tag_pink, R.color.ng_tts_tag_pink)
        }
    }

    private fun voiceLanguageLabels(language: String?): List<String> {
        val raw = language?.trim()?.takeIf { it.isNotEmpty() } ?: return emptyList()
        val values = raw
            .replace('，', ',')
            .replace('、', ',')
            .replace(';', ',')
            .replace('；', ',')
            .replace('/', ',')
            .replace('|', ',')
            .replace('+', ',')
            .split(',', ' ', '\n', '\t')
            .mapNotNull { voiceLanguageLabel(it) }
            .distinct()
        return values.ifEmpty {
            voiceLanguageLabel(raw)?.let { listOf(it) }.orEmpty()
        }
    }

    private fun voiceLanguageLabel(language: String?): String? {
        val code = language?.takeIf { it.isNotBlank() }
            ?.substringBefore("-")
            ?.substringBefore("_")
            ?.lowercase()
            ?: return null
        return when (code) {
            "zh", "cmn" -> "中"
            "yue" -> "粤"
            "wuu" -> "吴"
            "en" -> "英"
            "ja", "jp", "jpn" -> "日"
            "ko", "kr", "kor" -> "韩"
            "fr", "fra", "fre" -> "法"
            "de", "deu", "ger" -> "德"
            "es", "esp", "spa" -> "西"
            "pt", "por" -> "葡"
            else -> code.uppercase()
        }
    }

    private fun createLanguageTagView(container: LinearLayout, text: String): TextView {
        val context = container.context
        return TextView(context).apply {
            this.text = text
            gravity = Gravity.CENTER
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(ContextCompat.getColor(context, R.color.ng_tts_language))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setBackgroundResource(R.drawable.ng_bg_tts_language_tag)
            layoutParams = LinearLayout.LayoutParams(
                if (text.length <= 1) 24.dpToPx() else 34.dpToPx(),
                LinearLayout.LayoutParams.MATCH_PARENT
            ).apply {
                marginEnd = 4.dpToPx()
            }
        }
    }

    private fun createVoiceTagView(
        context: Context,
        container: LinearLayout,
        tag: VoiceTag
    ): TextView {
        val textColor = ContextCompat.getColor(context, tag.colorRes)
        return TextView(container.context).apply {
            text = tag.text
            gravity = Gravity.CENTER
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setBackgroundResource(tag.backgroundRes)
            setPadding(8.dpToPx(), 0, 8.dpToPx(), 0)
            minWidth = 0
            maxWidth = 116.dpToPx()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            ).apply {
                marginEnd = 6.dpToPx()
            }
        }
    }

    private data class VoiceTag(
        val text: String,
        val backgroundRes: Int = R.drawable.ng_bg_tts_voice_tag_blue,
        val colorRes: Int = R.color.ng_tts_tag_blue
    )
}
