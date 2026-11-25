package io.legado.app.ui.book.character

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import io.legado.app.R
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookCharacter.RoleTag
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

private const val EXTRA_AUTOMATIC_SILHOUETTE_STYLE = "automaticCharacterSilhouetteStyle"

internal fun Book.automaticCharacterSilhouetteStyle(): BookCharacterSilhouetteStyle =
    detectBookCharacterSilhouetteStyle(name, getDisplayIntro(), kind, customTag)

internal fun Intent.putAutomaticCharacterSilhouetteStyle(style: BookCharacterSilhouetteStyle) {
    putExtra(EXTRA_AUTOMATIC_SILHOUETTE_STYLE, style.key)
}

internal fun Intent.automaticCharacterSilhouetteStyle(): BookCharacterSilhouetteStyle =
    BookCharacterSilhouetteStyle.fromKey(getStringExtra(EXTRA_AUTOMATIC_SILHOUETTE_STYLE))
        .resolve(BookCharacterSilhouetteStyle.GENERIC)

internal object BookCharacterSilhouetteSettings {
    private const val KEY = "globalStyle"

    fun get(context: Context): BookCharacterSilhouetteStyle =
        BookCharacterSilhouetteStyle.fromKey(preferences(context).getString(KEY, null))

    fun set(context: Context, style: BookCharacterSilhouetteStyle) {
        preferences(context).edit {
            if (style == BookCharacterSilhouetteStyle.AUTO) remove(KEY) else putString(KEY, style.key)
        }
    }

    fun changes(context: Context) = callbackFlow {
        val prefs = preferences(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY || key == null) trySend(get(context))
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(get(context))
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences("book-character-silhouette-style", Context.MODE_PRIVATE)
}

@Composable
internal fun rememberBookCharacterSilhouetteStyle(): BookCharacterSilhouetteStyle {
    val context = LocalContext.current.applicationContext
    val changes = remember(context) { BookCharacterSilhouetteSettings.changes(context) }
    val initial = remember(context) { BookCharacterSilhouetteSettings.get(context) }
    return changes.collectAsStateWithLifecycle(initialValue = initial).value
}

internal data class BookCharacterSilhouette(
    @DrawableRes val drawable: Int,
    val background: Color,
)

private class RoleSilhouettes(
    @DrawableRes val maleLead: Int,
    @DrawableRes val femaleLead: Int,
    @DrawableRes val maleSupport: Int,
    @DrawableRes val femaleSupport: Int,
)

private val genericSilhouettes = RoleSilhouettes(
    R.drawable.book_character_silhouette_generic_male_lead,
    R.drawable.book_character_silhouette_generic_female_lead,
    R.drawable.book_character_silhouette_generic_male_support,
    R.drawable.book_character_silhouette_generic_female_support,
)
private val silhouettesByStyle = mapOf(
    BookCharacterSilhouetteStyle.ANCIENT to RoleSilhouettes(
        R.drawable.book_character_silhouette_ancient_male_lead,
        R.drawable.book_character_silhouette_ancient_female_lead,
        R.drawable.book_character_silhouette_ancient_male_support,
        R.drawable.book_character_silhouette_ancient_female_support,
    ),
    BookCharacterSilhouetteStyle.MODERN to RoleSilhouettes(
        R.drawable.book_character_silhouette_modern_male_lead,
        R.drawable.book_character_silhouette_modern_female_lead,
        R.drawable.book_character_silhouette_modern_male_support,
        R.drawable.book_character_silhouette_modern_female_support,
    ),
    BookCharacterSilhouetteStyle.FANTASY to RoleSilhouettes(
        R.drawable.book_character_silhouette_fantasy_male_lead,
        R.drawable.book_character_silhouette_fantasy_female_lead,
        R.drawable.book_character_silhouette_fantasy_male_support,
        R.drawable.book_character_silhouette_fantasy_female_support,
    ),
)
private val blueBackground = Color(0xFFD6E6FA)
private val redBackground = Color(0xFFF7DDE0)
private val neutralSilhouette = BookCharacterSilhouette(
    R.drawable.book_character_silhouette_neutral,
    Color(0xFFE3E8F1),
)

internal fun BookCharacterSilhouetteStyle.forRole(
    roleTag: String,
    hasPortrait: Boolean = false,
): BookCharacterSilhouette {
    val art = silhouettesByStyle[if (hasPortrait) BookCharacterSilhouetteStyle.GENERIC else this]
        ?: genericSilhouettes
    return when (roleTag) {
        RoleTag.MALE_LEAD -> BookCharacterSilhouette(art.maleLead, blueBackground)
        RoleTag.FEMALE_LEAD -> BookCharacterSilhouette(art.femaleLead, redBackground)
        RoleTag.MALE_SUPPORT -> BookCharacterSilhouette(art.maleSupport, blueBackground)
        RoleTag.FEMALE_SUPPORT -> BookCharacterSilhouette(art.femaleSupport, redBackground)
        else -> neutralSilhouette
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
internal fun BookCharacterPortraitImage(
    portrait: Uri?,
    silhouette: BookCharacterSilhouette,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    alignment: Alignment? = null,
) {
    if (portrait == null) {
        Image(
            painter = painterResource(silhouette.drawable),
            contentDescription = contentDescription,
            modifier = modifier.background(silhouette.background),
            alignment = alignment ?: Alignment.TopCenter,
            contentScale = ContentScale.Crop,
        )
    } else {
        GlideImage(
            model = portrait,
            contentDescription = contentDescription,
            modifier = modifier.background(silhouette.background),
            alignment = alignment ?: Alignment.Center,
            contentScale = ContentScale.Crop,
        ) { it.error(silhouette.drawable) }
    }
}
