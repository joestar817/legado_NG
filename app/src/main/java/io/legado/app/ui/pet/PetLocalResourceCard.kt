package io.legado.app.ui.pet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.help.pet.PetLocalRecord
import io.legado.app.help.pet.PetLocalStatus
import io.legado.app.help.resource.PetManagementActions
import io.legado.app.ui.design.components.compose.NgSettingsCardSurface
import io.legado.app.ui.design.components.compose.NgSettingsItemAppearance
import io.legado.app.ui.design.theme.NgTheme

/** A local package is not a remote catalog entry and never needs fabricated catalog metadata. */
@Composable
internal fun PetLocalResourceCard(pet: PetLocalRecord, grid: Boolean, onOpen: () -> Unit) {
    val status = when (pet.status) {
        PetLocalStatus.READY -> pet.originLabel()
        PetLocalStatus.MISSING -> "文件缺失"
        PetLocalStatus.INCOMPATIBLE -> "接口不兼容"
        PetLocalStatus.CORRUPT -> "文件损坏"
        PetLocalStatus.LOAD_ERROR -> "运行失败"
    }
    NgSettingsCardSurface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        appearance = NgSettingsItemAppearance.SURFACE_CARD, cornerRadius = 16.dp,
    ) {
        if (grid) {
            Column(Modifier.padding(10.dp)) {
                PetPreview(pet.id to pet.sha256, 0, Modifier.fillMaxWidth().height(88.dp)) { PetManagementActions.readPreview(pet.id) }
                Text(pet.displayName(), Modifier.padding(top = 8.dp), fontSize = 15.sp, lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium, color = Color(NgTheme.colors.onSurface), maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${pet.versionLabel()} · $status", Modifier.padding(top = 4.dp), fontSize = 12.sp,
                    color = Color(NgTheme.colors.onSurfaceVariant), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onOpen, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("去管理", color = Color(NgTheme.colors.primary))
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().heightIn(min = 84.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                PetPreview(pet.id to pet.sha256, 0, Modifier.size(56.dp)) { PetManagementActions.readPreview(pet.id) }
                Column(Modifier.weight(1f).padding(start = 10.dp, end = 8.dp)) {
                    Text(pet.displayName(), fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium,
                        color = Color(NgTheme.colors.onSurface), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${pet.versionLabel()} · $status", Modifier.padding(top = 4.dp), fontSize = 12.sp,
                        color = Color(NgTheme.colors.onSurfaceVariant), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                TextButton(onClick = onOpen, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("去管理", color = Color(NgTheme.colors.primary))
                }
            }
        }
    }
}
