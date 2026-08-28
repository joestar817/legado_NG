package io.legado.app.ui.book.info.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.data.entities.Book
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.compose.NgActionBarButton
import io.legado.app.ui.design.components.compose.NgActionBarButtonSizeVariant
import io.legado.app.ui.design.components.compose.NgActionBarButtonSurfaceVariant
import io.legado.app.ui.design.components.compose.NgBookCover
import io.legado.app.ui.design.components.compose.NgFloatingTabBar
import io.legado.app.ui.design.components.compose.NgFloatingTabBarVariant
import io.legado.app.ui.design.components.compose.NgFloatingTabSpec
import io.legado.app.ui.design.components.compose.NgFormField
import io.legado.app.ui.design.components.compose.NgFormFieldVariant
import io.legado.app.ui.design.components.compose.NgFormMultilineField
import io.legado.app.ui.design.components.compose.NgGlassDefaults
import io.legado.app.ui.design.components.compose.NgGlassSurface
import io.legado.app.ui.design.components.compose.NgMaterialRole
import io.legado.app.ui.design.theme.NgTheme

internal const val BOOK_TYPE_TEXT = 0
internal const val BOOK_TYPE_AUDIO = 1
internal const val BOOK_TYPE_IMAGE = 2
internal const val BOOK_TYPE_VIDEO = 3

internal data class BookInfoEditUiState(
    val book: Book? = null,
    val name: String = "",
    val author: String = "",
    val typeIndex: Int = BOOK_TYPE_TEXT,
    val coverUrl: String = "",
    val intro: String = "",
    val coverRevision: Int = 0,
)

internal sealed interface BookInfoEditUiEvent {
    data object Back : BookInfoEditUiEvent
    data object Save : BookInfoEditUiEvent
    data class NameChange(val value: String) : BookInfoEditUiEvent
    data class AuthorChange(val value: String) : BookInfoEditUiEvent
    data class TypeChange(val index: Int) : BookInfoEditUiEvent
    data class CoverUrlChange(val value: String) : BookInfoEditUiEvent
    data class IntroChange(val value: String) : BookInfoEditUiEvent
    data object SelectLocalCover : BookInfoEditUiEvent
    data object ChangeCover : BookInfoEditUiEvent
    data object RefreshCover : BookInfoEditUiEvent
}

@Composable
internal fun BookInfoEditScreen(
    state: BookInfoEditUiState,
    onEvent: (BookInfoEditUiEvent) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        BookInfoEditTopBar(
            saveEnabled = state.book != null,
            onBack = { onEvent(BookInfoEditUiEvent.Back) },
            onSave = { onEvent(BookInfoEditUiEvent.Save) },
        )
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .navigationBarsPadding()
                .imePadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 10.dp,
                top = 4.dp,
                end = 10.dp,
                bottom = 10.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "basic") {
                BookInfoEditBasicCard(state = state, onEvent = onEvent)
            }
            item(key = "cover") {
                BookInfoEditCoverCard(state = state, onEvent = onEvent)
            }
            item(key = "intro") {
                BookInfoEditIntroCard(state = state, onEvent = onEvent)
            }
        }
    }
}

@Composable
private fun BookInfoEditTopBar(
    saveEnabled: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .statusBarsPadding()
            .padding(start = 10.dp, top = 8.dp, end = 10.dp, bottom = 4.dp)
            .fillMaxWidth()
            .height(56.dp),
        color = colorResource(R.color.ng_surface_card),
        shape = RoundedCornerShape(NgTheme.shapes.mediumDp.dp),
        shadowElevation = NgTheme.effects.cardElevationDp.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BookInfoEditToolbarIcon(
                iconRes = R.drawable.ic_arrow_back,
                contentDescription = stringResource(R.string.back),
                onClick = onBack,
            )
            Text(
                text = stringResource(R.string.book_edit_title),
                modifier = Modifier.weight(1f).padding(start = 4.dp),
                color = Color(NgTheme.colors.onSurface),
                fontSize = 17.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            BookInfoEditToolbarIcon(
                iconRes = R.drawable.ic_save,
                contentDescription = stringResource(R.string.action_save),
                enabled = saveEnabled,
                onClick = onSave,
            )
        }
    }
}

@Composable
private fun BookInfoEditToolbarIcon(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(48.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp),
            tint = Color(NgTheme.colors.onSurface).copy(alpha = if (enabled) 1f else 0.38f),
        )
    }
}

@Composable
private fun BookInfoEditBasicCard(
    state: BookInfoEditUiState,
    onEvent: (BookInfoEditUiEvent) -> Unit,
) {
    BookInfoEditCard {
        BookInfoEditSectionTitle(stringResource(R.string.book_info_basic))
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            state.book?.let { book ->
                NgBookCover(
                    book = book,
                    modifier = Modifier.width(112.dp).aspectRatio(0.80f),
                    coverRadius = 8,
                    coverAspectRatio = 0.80f,
                    contentDescription = stringResource(R.string.img_cover),
                    revision = state.coverRevision,
                )
            } ?: Box(
                modifier = Modifier
                    .width(112.dp)
                    .aspectRatio(0.75f)
                    .background(colorResource(R.color.ng_settings_item)),
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                NgFormField(
                    label = stringResource(R.string.book_name),
                    value = state.name,
                    onValueChange = { onEvent(BookInfoEditUiEvent.NameChange(it)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    variant = NgFormFieldVariant.LABELED_UNDERLINE,
                )
                Spacer(Modifier.height(18.dp))
                NgFormField(
                    label = stringResource(R.string.author),
                    value = state.author,
                    onValueChange = { onEvent(BookInfoEditUiEvent.AuthorChange(it)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    variant = NgFormFieldVariant.LABELED_UNDERLINE,
                )
            }
        }
        Text(
            text = stringResource(R.string.book_info_type),
            modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
            color = Color(NgTheme.colors.onSurfaceVariant),
            fontSize = 13.sp,
            lineHeight = 16.sp,
        )
        NgFloatingTabBar(
            items = listOf(
                NgFloatingTabSpec(text = stringResource(R.string.book_type_text)),
                NgFloatingTabSpec(text = stringResource(R.string.book_type_audio)),
                NgFloatingTabSpec(text = stringResource(R.string.book_type_image)),
                NgFloatingTabSpec(text = stringResource(R.string.book_type_video)),
            ),
            selectedIndex = state.typeIndex,
            onTabSelected = { onEvent(BookInfoEditUiEvent.TypeChange(it)) },
            variant = NgFloatingTabBarVariant.SOLID_LIGHT_CONTENT,
        )
    }
}

@Composable
private fun BookInfoEditCoverCard(
    state: BookInfoEditUiState,
    onEvent: (BookInfoEditUiEvent) -> Unit,
) {
    BookInfoEditCard {
        BookInfoEditSectionTitle(stringResource(R.string.book_info_cover_section))
        Spacer(Modifier.height(16.dp))
        NgFormField(
            label = stringResource(R.string.cover_path),
            value = state.coverUrl,
            onValueChange = { onEvent(BookInfoEditUiEvent.CoverUrlChange(it)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            variant = NgFormFieldVariant.LABELED_UNDERLINE,
            trailingContent = {
                IconButton(
                    onClick = { onEvent(BookInfoEditUiEvent.RefreshCover) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_refresh_black_24dp),
                        contentDescription = stringResource(R.string.refresh_cover),
                        modifier = Modifier.size(20.dp),
                        tint = Color(NgTheme.colors.onSurface),
                    )
                }
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NgActionBarButton(
                text = stringResource(R.string.book_cover_local_image),
                icon = ImageVector.vectorResource(R.drawable.ic_image),
                onClick = { onEvent(BookInfoEditUiEvent.SelectLocalCover) },
                modifier = Modifier.weight(1f),
                variant = NgButtonVariant.OUTLINE,
                surfaceVariant = NgActionBarButtonSurfaceVariant.NEUTRAL,
                sizeVariant = NgActionBarButtonSizeVariant.COMPACT,
            )
            NgActionBarButton(
                text = stringResource(R.string.change_cover_source),
                icon = ImageVector.vectorResource(R.drawable.ic_web_outline),
                onClick = { onEvent(BookInfoEditUiEvent.ChangeCover) },
                modifier = Modifier.weight(1f),
                variant = NgButtonVariant.OUTLINE,
                surfaceVariant = NgActionBarButtonSurfaceVariant.NEUTRAL,
                sizeVariant = NgActionBarButtonSizeVariant.COMPACT,
            )
        }
    }
}

@Composable
private fun BookInfoEditIntroCard(
    state: BookInfoEditUiState,
    onEvent: (BookInfoEditUiEvent) -> Unit,
) {
    BookInfoEditCard {
        BookInfoEditSectionTitle(stringResource(R.string.book_intro))
        Spacer(Modifier.height(16.dp))
        NgFormMultilineField(
            value = state.intro,
            onValueChange = { onEvent(BookInfoEditUiEvent.IntroChange(it)) },
            minHeight = 220.dp,
            maxHeight = 520.dp,
            minLines = 8,
            maxLines = 24,
            containerColor = colorResource(R.color.ng_surface_card),
        )
    }
}

@Composable
private fun BookInfoEditCard(
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    NgGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        role = NgMaterialRole.CONTENT,
        shape = RoundedCornerShape(NgTheme.shapes.mediumDp.dp),
        style = NgGlassDefaults.bookDetailStyle(
            containerColor = colorResource(R.color.ng_surface_card),
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        content = content,
    )
}

@Composable
private fun BookInfoEditSectionTitle(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(20.dp)
                .background(
                    color = Color(NgTheme.colors.primary),
                    shape = RoundedCornerShape(2.dp),
                ),
        )
        Text(
            text = text,
            modifier = Modifier.padding(start = 12.dp),
            color = Color(NgTheme.colors.onSurface),
            fontSize = 18.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
