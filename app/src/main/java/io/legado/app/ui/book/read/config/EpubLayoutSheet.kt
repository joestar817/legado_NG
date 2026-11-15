package io.legado.app.ui.book.read.config

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.data.entities.Book
import io.legado.app.help.config.EpubLayoutPreferences
import io.legado.app.ui.design.components.compose.NgFormGroup
import io.legado.app.ui.design.components.compose.NgFormGroupDivider
import io.legado.app.ui.design.components.compose.NgFormPanel
import io.legado.app.ui.design.components.compose.NgFormSwitchSettingRow
import io.legado.app.ui.design.theme.NgTheme

@Composable
internal fun EpubLayoutSettings(
    book: Book,
    onStyleChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var choices by remember(book.bookUrl) { mutableStateOf(EpubLayoutPreferences.read(book.bookUrl)) }

    fun change(key: String, value: Boolean) {
        EpubLayoutPreferences.set(book.bookUrl, key, value)
        choices = EpubLayoutPreferences.read(book.bookUrl)
        onStyleChanged()
    }

    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 10.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "EPUB 排版", modifier = Modifier.weight(1f),
                color = Color(NgTheme.colors.primary), fontSize = 14.sp,
                lineHeight = 18.sp, fontWeight = FontWeight.Medium,
            )
            Text(
                text = "恢复默认",
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button) {
                        EpubLayoutPreferences.reset(book.bookUrl)
                        choices = EpubLayoutPreferences.read(book.bookUrl)
                        onStyleChanged()
                    }
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                color = Color(NgTheme.colors.primary), fontSize = 14.sp, lineHeight = 18.sp,
            )
        }
        NgFormPanel {
            NgFormSwitchSettingRow(
                title = "原书排版优先",
                checked = choices.getValue(EpubLayoutPreferences.PUBLISHER),
                onCheckedChange = { change(EpubLayoutPreferences.PUBLISHER, it) },
            )
        }
        Spacer(Modifier.height(12.dp))
        NgFormGroup("原书样式") {
            EpubLayoutPreferences.features.entries.forEachIndexed { index, (key, title) ->
                if (index > 0) NgFormGroupDivider()
                NgFormSwitchSettingRow(
                    title = title,
                    checked = choices.getValue(key),
                    onCheckedChange = { change(key, it) },
                )
            }
        }
    }
}
