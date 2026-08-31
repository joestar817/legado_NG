package io.legado.app.ui.about

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.design.components.compose.NgGlassDefaults
import io.legado.app.ui.design.components.compose.NgGlassSurface
import io.legado.app.ui.design.components.compose.NgLauncherIcon
import io.legado.app.ui.design.components.compose.NgMaterialRole
import io.legado.app.ui.design.theme.NgTheme

@Composable
internal fun AboutScreen(
    versionName: String,
    onContributorsClick: () -> Unit,
    onCheckUpdateClick: () -> Unit,
    onTelegramClick: () -> Unit,
    onPrivacyPolicyClick: () -> Unit,
    onLicenseClick: () -> Unit,
    onDisclaimerClick: () -> Unit,
    onCrashLogClick: () -> Unit,
    onSaveLogClick: () -> Unit,
    onCreateHeapDumpClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 20.dp),
    ) {
        AboutAppCard(
            versionName = versionName,
            actions = listOf(
                AboutLinkAction(
                    labelRes = R.string.about_action_github,
                    iconRes = R.drawable.ic_model_github,
                    onClick = onContributorsClick,
                ),
                AboutLinkAction(
                    labelRes = R.string.about_action_update,
                    iconRes = R.drawable.ic_about_cloud_download,
                    iconSize = 28.dp,
                    onClick = onCheckUpdateClick,
                ),
                AboutLinkAction(
                    labelRes = R.string.telegram,
                    iconRes = R.drawable.ic_about_telegram,
                    iconSize = 23.dp,
                    onClick = onTelegramClick,
                ),
            ),
        )
        Spacer(modifier = Modifier.height(16.dp))
        AboutToolList(
            tools = listOf(
                AboutToolAction(
                    titleRes = R.string.crash_log,
                    iconRes = R.drawable.ic_bug_report,
                    onClick = onCrashLogClick,
                ),
                AboutToolAction(
                    titleRes = R.string.save_log,
                    iconRes = R.drawable.ic_save,
                    onClick = onSaveLogClick,
                ),
                AboutToolAction(
                    titleRes = R.string.create_heap_dump,
                    iconRes = R.drawable.ic_storage_black_24dp,
                    onClick = onCreateHeapDumpClick,
                ),
                AboutToolAction(
                    titleRes = R.string.privacy_policy,
                    iconRes = R.drawable.ic_about_lock,
                    onClick = onPrivacyPolicyClick,
                ),
                AboutToolAction(
                    titleRes = R.string.license,
                    iconRes = R.drawable.ic_about_license,
                    onClick = onLicenseClick,
                ),
                AboutToolAction(
                    titleRes = R.string.disclaimer,
                    iconRes = R.drawable.ic_about_info,
                    onClick = onDisclaimerClick,
                ),
            ),
        )
    }
}

@Composable
private fun AboutAppCard(
    versionName: String,
    actions: List<AboutLinkAction>,
) {
    NgGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        role = NgMaterialRole.OVERLAY,
        shape = RoundedCornerShape(18.dp),
        style = NgGlassDefaults.style().copy(shadowElevation = 0.dp),
        liquidCornerRadius = 18.dp,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 20.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            NgLauncherIcon(
                iconRes = R.mipmap.ic_launcher,
                contentDescription = stringResource(R.string.app_name_ng),
                modifier = Modifier
                    .size(78.dp)
                    .clip(RoundedCornerShape(18.dp)),
            )
            Text(
                text = stringResource(R.string.app_name_ng),
                modifier = Modifier.padding(top = 10.dp),
                color = Color(NgTheme.colors.onSurface),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = versionName,
                modifier = Modifier.padding(top = 7.dp),
                color = Color(NgTheme.colors.primary),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                text = stringResource(R.string.about_description_ng),
                modifier = Modifier.padding(top = 9.dp),
                color = Color(NgTheme.colors.onSurfaceVariant),
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            HorizontalDivider(
                modifier = Modifier.padding(top = 18.dp, bottom = 14.dp),
                color = Color(NgTheme.colors.outlineVariant).copy(alpha = 0.26f),
            )
            Row(
                modifier = Modifier.fillMaxWidth(0.80f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                actions.forEach { action ->
                    AboutLinkButton(
                        action = action,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutLinkButton(
    action: AboutLinkAction,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(action.labelRes)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(13.dp))
                .clickable(onClick = action.onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(action.iconRes),
                contentDescription = label,
                modifier = Modifier.size(action.iconSize),
                tint = Color(NgTheme.colors.onSurfaceVariant),
            )
        }
        Text(
            text = label,
            modifier = Modifier.padding(top = 6.dp),
            color = Color(NgTheme.colors.onSurfaceVariant),
            fontSize = 11.sp,
            lineHeight = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AboutToolList(tools: List<AboutToolAction>) {
    NgGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        role = NgMaterialRole.CONTENT,
        shape = RoundedCornerShape(18.dp),
        style = NgGlassDefaults.style().copy(shadowElevation = 0.dp),
        liquidCornerRadius = 18.dp,
    ) {
        tools.forEachIndexed { index, tool ->
            AboutToolRow(tool)
            if (index != tools.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 52.dp, end = 14.dp),
                    color = Color(NgTheme.colors.outlineVariant).copy(alpha = 0.24f),
                )
            }
        }
    }
}

@Composable
private fun AboutToolRow(tool: AboutToolAction) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = tool.onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(tool.iconRes),
            contentDescription = null,
            modifier = Modifier.size(21.dp),
            tint = Color(NgTheme.colors.onSurfaceVariant),
        )
        Text(
            text = stringResource(tool.titleRes),
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
            color = Color(NgTheme.colors.onSurface),
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right_20),
            contentDescription = null,
            modifier = Modifier.size(19.dp),
            tint = Color(NgTheme.colors.onSurfaceVariant),
        )
    }
}

private data class AboutLinkAction(
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
    val iconSize: Dp = 23.dp,
    val onClick: () -> Unit,
)

private data class AboutToolAction(
    @param:StringRes val titleRes: Int,
    @param:DrawableRes val iconRes: Int,
    val onClick: () -> Unit,
)
