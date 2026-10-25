package io.legado.app.ui.book.bgm

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.help.bgm.MusicPlatform
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.NgDialogTextActionButton
import io.legado.app.ui.design.components.compose.NgPaneDialog
import io.legado.app.ui.design.components.compose.NgFlatActionRail
import io.legado.app.ui.design.components.compose.NgFlatActionRailItem
import io.legado.app.ui.design.components.compose.NgFlatActionRailVariant
import io.legado.app.ui.design.components.compose.NgFormField
import io.legado.app.ui.design.components.compose.NgFormFieldVariant
import io.legado.app.ui.design.components.compose.NgFormActionButton
import io.legado.app.ui.design.components.compose.NgFormActionButtonAppearance
import io.legado.app.ui.design.components.compose.NgIconButton
import io.legado.app.ui.design.theme.NgTheme

/** Credentials are owned by the transient ViewModel state, never saved by the dialog. */
@Composable
internal fun BgmAccountDialog(
    platform: MusicPlatform,
    loggedIn: Boolean,
    accountBusy: Boolean,
    profileState: BgmAccountProfileUiState?,
    method: BgmLoginMethod,
    phoneState: BgmPhoneLoginUiState,
    bitmap: Bitmap?,
    status: String,
    onDismiss: () -> Unit,
    onLogout: () -> Unit,
    onRetryProfile: () -> Unit,
    onMethodChanged: (BgmLoginMethod) -> Unit,
    onPhoneChanged: (String) -> Unit,
    onCountryCodeChanged: (String) -> Unit,
    onCodeChanged: (String) -> Unit,
    onSendCode: () -> Unit,
    onSubmitCode: () -> Unit,
    onRefreshQr: () -> Unit,
) {
    val busy = accountBusy || phoneState.busy
    val showProfile = loggedIn && profileState != null
    val canSubmit = !busy && phoneState.codeSent && phoneState.phone.isNotBlank() &&
        phoneState.countryCode.isNotBlank() && phoneState.code.isNotBlank()
    NgPaneDialog(onDismissRequest = onDismiss) {
        BoxWithConstraints(Modifier.fillMaxWidth().imePadding()) {
            // Keep the primary action reachable when the keyboard reduces the dialog height.
            val bodyHeight = if (showProfile) (maxHeight - 128.dp).coerceIn(0.dp, 440.dp)
                else if (loggedIn) (maxHeight - 160.dp).coerceIn(100.dp, 420.dp)
                else (maxHeight - 92.dp).coerceIn(0.dp, 520.dp)
            NgDialog(
                title = if (loggedIn && !showProfile) platform.bgmTitle() else null,
                variant = NgDialogVariant.STANDARD,
                actions = {
                    if (showProfile) {
                        NgFormActionButton(
                            text = if (accountBusy) "正在退出…" else "退出登录",
                            onClick = onLogout,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !accountBusy,
                            appearance = NgFormActionButtonAppearance.DIALOG,
                            buttonHeight = 44.dp,
                            textSize = 15.sp,
                            textLineHeight = 20.sp,
                        )
                    } else if (loggedIn) {
                        NgDialogTextActionButton("关闭", onDismiss, secondary = true)
                        NgDialogTextActionButton(if (accountBusy) "处理中…" else "退出登录", onLogout,
                            enabled = !accountBusy)
                    } else if (method == BgmLoginMethod.PHONE) {
                        NgFormActionButton(
                            text = if (busy) "处理中…" else "登录",
                            onClick = onSubmitCode,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = canSubmit,
                            variant = NgButtonVariant.PRIMARY,
                            appearance = NgFormActionButtonAppearance.DIALOG,
                            buttonHeight = 48.dp,
                            textSize = 17.sp,
                            textLineHeight = 22.sp,
                        )
                    } else {
                        NgFormActionButton(
                            text = "刷新二维码",
                            onClick = onRefreshQr,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !accountBusy && status != "正在获取二维码…",
                            appearance = NgFormActionButtonAppearance.SURFACE_CARD_BORDERLESS,
                            buttonHeight = 48.dp,
                            textSize = 15.sp,
                            textLineHeight = 20.sp,
                            leadingIcon = {
                                Icon(painterResource(R.drawable.ic_refresh_black_24dp), null,
                                    Modifier.size(20.dp))
                            },
                        )
                    }
                },
            ) {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = bodyHeight).verticalScroll(rememberScrollState()),
                ) {
                    if (loggedIn && profileState != null) {
                        BgmLoginHeader(platform, onDismiss)
                        Spacer(Modifier.height(16.dp))
                        BgmAccountProfileContent(platform, profileState, !accountBusy, onRetryProfile)
                    } else if (loggedIn) {
                        BgmAccountText("已登录")
                    } else {
                        BgmLoginHeader(platform, onDismiss)
                        Spacer(Modifier.height(12.dp))
                        val methods = if (platform == MusicPlatform.QQ) listOf(
                            BgmLoginMethod.PHONE to "手机登录",
                            BgmLoginMethod.WECHAT_QR to "微信扫码",
                            BgmLoginMethod.QR to "QQ 扫码",
                        ) else listOf(BgmLoginMethod.PHONE to "手机登录", BgmLoginMethod.QR to "扫码登录")
                        NgFlatActionRail(
                            items = methods.map { (value, label) ->
                                NgFlatActionRailItem(label = label, emphasized = method == value, enabled = !busy)
                            },
                            onItemClick = { onMethodChanged(methods[it].first) },
                            variant = NgFlatActionRailVariant.FORM_UNDERLINE_TABS,
                        )
                        Spacer(Modifier.height(22.dp))
                        if (method == BgmLoginMethod.PHONE) {
                            BgmLoginFieldLabel("手机号")
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                NgFormField(
                                    label = "", value = phoneState.countryCode, onValueChange = onCountryCodeChanged,
                                    modifier = Modifier.width(56.dp).semantics { contentDescription = "国家码" },
                                    placeholder = "国家码", enabled = !busy,
                                    visualTransformation = BgmCountryCodeTransformation,
                                    variant = NgFormFieldVariant.DIALOG_UNDERLINE,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                                )
                                NgFormField(
                                    label = "", value = phoneState.phone, onValueChange = onPhoneChanged,
                                    modifier = Modifier.weight(1f), placeholder = "输入手机号", enabled = !busy,
                                    variant = NgFormFieldVariant.DIALOG_UNDERLINE,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                                )
                            }
                            Spacer(Modifier.height(18.dp))
                            BgmLoginFieldLabel("验证码")
                            NgFormField(
                                label = "", value = phoneState.code, onValueChange = onCodeChanged,
                                placeholder = "输入验证码", enabled = !busy,
                                variant = NgFormFieldVariant.DIALOG_UNDERLINE,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { if (canSubmit) onSubmitCode() }),
                                trailingContent = {
                                    NgDialogTextActionButton(
                                        text = if (phoneState.cooldownSeconds > 0) "${phoneState.cooldownSeconds} 秒"
                                            else if (phoneState.codeSent) "重新获取" else "获取验证码",
                                        onClick = onSendCode,
                                        enabled = !busy && phoneState.cooldownSeconds <= 0 &&
                                            phoneState.phone.isNotBlank() && phoneState.countryCode.isNotBlank(),
                                    )
                                },
                            )
                            if (phoneState.message.isNotBlank()) {
                                Spacer(Modifier.height(12.dp))
                                BgmAccountText(phoneState.message, secondary = true)
                            }
                        } else {
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(128.dp).background(Color.White), Alignment.Center) {
                                    if (bitmap != null) {
                                        Image(bitmap.asImageBitmap(), "登录二维码", Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Fit)
                                    } else if (status == "正在获取二维码…") {
                                        CircularProgressIndicator(Modifier.size(28.dp),
                                            color = Color(NgTheme.colors.primary), strokeWidth = 2.dp)
                                    }
                                }
                                if (status.isNotBlank()) {
                                    Spacer(Modifier.height(16.dp))
                                    BgmAccountText(status, centered = true)
                                }
                                Spacer(Modifier.height(6.dp))
                                val scanner = if (method == BgmLoginMethod.WECHAT_QR) "微信"
                                    else if (platform == MusicPlatform.QQ) "手机 QQ" else "网易云音乐"
                                BgmAccountText("使用另一台设备的${scanner}扫码",
                                    secondary = true, centered = true)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BgmLoginHeader(platform: MusicPlatform, onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BgmPlatformIcon(platform, Modifier.size(32.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text = platform.bgmTitle(),
            modifier = Modifier.weight(1f),
            color = Color(NgTheme.colors.onSurface),
            fontSize = 20.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        NgIconButton(onDismiss, Modifier.size(48.dp)) {
            Icon(painterResource(R.drawable.ic_baseline_close), "关闭",
                Modifier.size(22.dp), tint = Color(NgTheme.colors.onSurfaceVariant))
        }
    }
}

@Composable
private fun BgmLoginFieldLabel(text: String) {
    Text(text, color = Color(NgTheme.colors.onSurfaceVariant), fontSize = 12.sp, lineHeight = 16.sp)
}

/** Only formats the existing editable country code; the ViewModel still receives digits. */
private object BgmCountryCodeTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (text.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        return TransformedText(AnnotatedString("+") + text, object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = offset + 1
            override fun transformedToOriginal(offset: Int) = (offset - 1).coerceAtLeast(0)
        })
    }
}

@Composable
private fun BgmAccountText(text: String, secondary: Boolean = false, centered: Boolean = false) {
    Text(text, color = Color(if (secondary) NgTheme.colors.onSurfaceVariant else NgTheme.colors.onSurface),
        fontSize = if (secondary) 13.sp else 16.sp, lineHeight = if (secondary) 19.sp else 22.sp,
        textAlign = if (centered) TextAlign.Center else TextAlign.Start)
}
