package io.legado.app.ui.config

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.help.config.NgCoverAlbum
import io.legado.app.ui.design.components.NgSettingsTrailing
import io.legado.app.ui.design.components.compose.NgBottomDrawerSurface
import io.legado.app.ui.design.components.compose.NgSettingsGroup
import io.legado.app.ui.design.components.compose.NgSettingsItem
import io.legado.app.ui.design.components.compose.NgSettingsSectionLabel
import io.legado.app.ui.design.theme.NgTheme

internal data class CoverConfigScreenState(
    val loadCoverOnlyWifi: Boolean = false,
    val useDefaultCover: Boolean = false,
    val coverAlbums: List<NgCoverAlbum> = emptyList(),
    val selectedCoverAlbumId: String? = null,
    val coverAlbumSummary: String = "",
    val dayCoverSummary: String = "",
    val dayShowName: Boolean = true,
    val dayShowAuthor: Boolean = true,
    val nightCoverSummary: String = "",
    val nightShowName: Boolean = true,
    val nightShowAuthor: Boolean = true
)

@Composable
internal fun CoverConfigScreen(
    state: CoverConfigScreenState,
    onLoadCoverOnlyWifiChanged: (Boolean) -> Unit,
    onOpenCoverRule: () -> Unit,
    onUseDefaultCoverChanged: (Boolean) -> Unit,
    onCoverAlbumSelected: (String?) -> Unit,
    onOpenDayCover: () -> Unit,
    onDayShowNameChanged: (Boolean) -> Unit,
    onDayShowAuthorChanged: (Boolean) -> Unit,
    onOpenNightCover: () -> Unit,
    onNightShowNameChanged: (Boolean) -> Unit,
    onNightShowAuthorChanged: (Boolean) -> Unit
) {
    var showCoverAlbumSelector by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 24.dp)
    ) {
        NgSettingsGroup {
            NgSettingsItem(
                title = stringResource(R.string.only_wifi),
                summary = stringResource(R.string.only_wifi_summary),
                trailing = NgSettingsTrailing.SWITCH,
                checked = state.loadCoverOnlyWifi,
                onCheckedChange = onLoadCoverOnlyWifiChanged,
                onClick = { onLoadCoverOnlyWifiChanged(!state.loadCoverOnlyWifi) }
            )
            NgSettingsItem(
                title = stringResource(R.string.cover_rule),
                summary = stringResource(R.string.cover_rule_summary),
                onClick = onOpenCoverRule
            )
            NgSettingsItem(
                title = stringResource(R.string.use_default_cover),
                summary = stringResource(R.string.use_default_cover_s),
                trailing = NgSettingsTrailing.SWITCH,
                checked = state.useDefaultCover,
                onCheckedChange = onUseDefaultCoverChanged,
                onClick = { onUseDefaultCoverChanged(!state.useDefaultCover) }
            )
            NgSettingsItem(
                title = stringResource(R.string.ng_cover_album),
                summary = state.coverAlbumSummary,
                enabled = state.coverAlbums.isNotEmpty(),
                onClick = { showCoverAlbumSelector = true }
            )
        }

        Spacer(Modifier.height(4.dp))
        NgSettingsSectionLabel(stringResource(R.string.day))
        NgSettingsGroup {
            NgSettingsItem(
                title = stringResource(R.string.default_cover),
                summary = state.dayCoverSummary,
                onClick = onOpenDayCover
            )
            NgSettingsItem(
                title = stringResource(R.string.cover_show_name),
                summary = stringResource(R.string.cover_show_name_summary),
                trailing = NgSettingsTrailing.SWITCH,
                checked = state.dayShowName,
                onCheckedChange = onDayShowNameChanged,
                onClick = { onDayShowNameChanged(!state.dayShowName) }
            )
            NgSettingsItem(
                title = stringResource(R.string.cover_show_author),
                summary = stringResource(R.string.cover_show_author_summary),
                enabled = state.dayShowName,
                trailing = NgSettingsTrailing.SWITCH,
                checked = state.dayShowAuthor,
                onCheckedChange = onDayShowAuthorChanged,
                onClick = { onDayShowAuthorChanged(!state.dayShowAuthor) }
            )
        }

        NgSettingsSectionLabel(stringResource(R.string.night))
        NgSettingsGroup {
            NgSettingsItem(
                title = stringResource(R.string.default_cover),
                summary = state.nightCoverSummary,
                onClick = onOpenNightCover
            )
            NgSettingsItem(
                title = stringResource(R.string.cover_show_name),
                summary = stringResource(R.string.cover_show_name_summary),
                trailing = NgSettingsTrailing.SWITCH,
                checked = state.nightShowName,
                onCheckedChange = onNightShowNameChanged,
                onClick = { onNightShowNameChanged(!state.nightShowName) }
            )
            NgSettingsItem(
                title = stringResource(R.string.cover_show_author),
                summary = stringResource(R.string.cover_show_author_summary),
                enabled = state.nightShowName,
                trailing = NgSettingsTrailing.SWITCH,
                checked = state.nightShowAuthor,
                onCheckedChange = onNightShowAuthorChanged,
                onClick = { onNightShowAuthorChanged(!state.nightShowAuthor) }
            )
        }
    }

    if (showCoverAlbumSelector) {
        NgCoverAlbumSelectionSheet(
            albums = state.coverAlbums,
            selectedAlbumId = state.selectedCoverAlbumId,
            onSelect = { albumId ->
                onCoverAlbumSelected(albumId)
                showCoverAlbumSelector = false
            },
            onDismissRequest = { showCoverAlbumSelector = false },
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun NgCoverAlbumSelectionSheet(
    albums: List<NgCoverAlbum>,
    selectedAlbumId: String?,
    onSelect: (String?) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val baseSnapshot = NgTheme.snapshot
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = Color.Transparent,
        contentColor = Color(baseSnapshot.colors.onSurface),
        shape = RectangleShape,
    ) {
        NgBottomDrawerSurface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.52f),
        ) {
            val snapshot = NgTheme.snapshot
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            ) {
                Text(
                    text = stringResource(R.string.ng_cover_album),
                    color = Color(snapshot.colors.onSurface),
                    fontSize = 21.sp,
                    lineHeight = 25.sp,
                    fontWeight = FontWeight.Bold,
                )
                NgSettingsGroup(modifier = Modifier.padding(top = 8.dp)) {
                    NgCoverAlbumSelectionItem(
                        title = stringResource(R.string.ng_cover_album_none),
                        summary = stringResource(R.string.ng_cover_album_none_summary),
                        selected = selectedAlbumId == null,
                        onClick = { onSelect(null) },
                    )
                    albums.forEach { album ->
                        NgCoverAlbumSelectionItem(
                            title = album.name,
                            summary = stringResource(
                                R.string.ng_cover_album_count,
                                album.lightImages.size,
                                album.darkImages.size,
                            ),
                            selected = album.id == selectedAlbumId,
                            onClick = { onSelect(album.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NgCoverAlbumSelectionItem(
    title: String,
    summary: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val snapshot = NgTheme.snapshot
    NgSettingsItem(
        title = title,
        summary = summary,
        trailing = NgSettingsTrailing.CUSTOM,
        customTrailing = {
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Color(snapshot.colors.primary),
                )
            }
        },
        onClick = onClick,
    )
}
