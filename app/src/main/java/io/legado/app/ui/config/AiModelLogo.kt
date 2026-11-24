package io.legado.app.ui.config

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import io.legado.app.R
import io.legado.app.ui.design.theme.NgTheme

@Composable
internal fun AiModelLogo(
    @DrawableRes iconRes: Int,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    onDark: Color = MaterialTheme.colorScheme.onSurface,
) {
    // These logos use dark artwork on a transparent background.
    val darkArtwork = when (iconRes) {
        R.drawable.ic_provider_openai,
        R.drawable.ic_provider_openrouter,
        R.drawable.ic_provider_moonshot,
        R.drawable.ic_model_anthropic,
        R.drawable.ic_model_github,
        R.drawable.ic_model_grok,
        R.drawable.ic_model_groq,
        R.drawable.ic_model_kimi,
        R.drawable.ic_model_ollama,
        R.drawable.ic_model_vercel,
        R.drawable.ic_model_xai -> true
        else -> false
    }
    Image(
        painter = painterResource(iconRes),
        contentDescription = contentDescription,
        modifier = modifier,
        colorFilter = if (NgTheme.snapshot.isDark && darkArtwork) ColorFilter.tint(onDark) else null,
    )
}
