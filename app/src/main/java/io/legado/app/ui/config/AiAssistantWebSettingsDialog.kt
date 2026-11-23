package io.legado.app.ui.config

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.legado.app.R
import io.legado.app.help.ai.AiExternalSearchProvider
import io.legado.app.help.ai.AiExternalSearchService
import io.legado.app.help.ai.AiExternalSearchSettings
import io.legado.app.help.ai.AiExternalSearchStore
import io.legado.app.help.ai.AiWebRoutingMode
import io.legado.app.ui.design.components.NgStatusTagSpec
import io.legado.app.ui.design.components.compose.NgBottomDrawerSurface
import io.legado.app.ui.design.components.compose.NgDrawerContentCardStyle
import io.legado.app.ui.design.components.compose.NgFloatingTabBar
import io.legado.app.ui.design.components.compose.NgFloatingTabBarVariant
import io.legado.app.ui.design.components.compose.NgFloatingTabSpec
import io.legado.app.ui.design.components.compose.NgFormActionButton
import io.legado.app.ui.design.components.compose.NgFormActionButtonAppearance
import io.legado.app.ui.design.components.compose.NgFormGroupDivider
import io.legado.app.ui.design.components.compose.NgFormPanel
import io.legado.app.ui.design.components.compose.NgLongDrawerHeader
import io.legado.app.ui.design.components.compose.NgStatusTag
import io.legado.app.ui.design.components.compose.NgSwitchControl
import io.legado.app.ui.design.components.compose.NgSwitchControlVariant
import io.legado.app.ui.design.components.compose.ngDrawerContentCardColor
import io.legado.app.ui.design.components.compose.ngDrawerPrimaryTextColor
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgTheme

internal class AiAssistantWebSettingsDialog : BottomSheetDialogFragment() {
    companion object {
        fun show(manager: FragmentManager) {
            val tag = AiAssistantWebSettingsDialog::class.java.name
            if (!manager.isStateSaved && manager.findFragmentByTag(tag) == null) {
                AiAssistantWebSettingsDialog().showNow(manager, tag)
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?) = BottomSheetDialog(requireContext(), theme)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            NgAppTheme(updateSystemBars = false) {
                AiAssistantWebSettingsSheet(onManageServices = ::openSearchServices)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawableResource(R.color.transparent)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.22f)
            decorView.setPadding(0, 0, 0, 0)
        }
        val sheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?: return
        sheet.setBackgroundColor(AndroidColor.TRANSPARENT)
        sheet.layoutParams = sheet.layoutParams.apply { height = ViewGroup.LayoutParams.WRAP_CONTENT }
        BottomSheetBehavior.from(sheet).apply {
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    private fun openSearchServices() {
        startActivity(Intent(requireContext(), ConfigActivity::class.java).apply {
            putExtra("configTag", ConfigTag.AI_CONFIG)
            putExtra(AiConfigFragment.EXTRA_INITIAL_PAGE, AiConfigFragment.PAGE_SEARCH_SERVICES)
        })
    }
}

@Composable
internal fun assistantWebSettingsSummary(settings: AiExternalSearchSettings): String {
    if (!settings.webEnabled) return stringResource(R.string.ai_assistant_web_off)
    val serviceName = settings.activeService()?.displayName
    return when {
        settings.routing == AiWebRoutingMode.EXTERNAL -> stringResource(
            R.string.ai_search_summary_external,
            serviceName ?: stringResource(
                if (settings.selectedService.isEmpty()) R.string.ai_search_no_service
                else R.string.ai_search_service_unavailable,
            ),
        )
        !settings.backupEnabled -> stringResource(R.string.ai_search_summary_auto)
        serviceName != null -> stringResource(R.string.ai_search_summary_backup, serviceName)
        else -> stringResource(R.string.ai_search_summary_backup_unavailable)
    }
}

@Composable
internal fun AiAssistantWebSettingsSheet(onManageServices: () -> Unit) {
    val settings by AiExternalSearchStore.changes.collectAsState()
    val automatic = settings.routing == AiWebRoutingMode.AUTO
    val availableServices = remember(settings) { settings.services.filter { it.available } }
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.85f).dp

    NgBottomDrawerSurface(
        modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight),
        contentCardStyle = NgDrawerContentCardStyle.ADAPTIVE,
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            NgLongDrawerHeader(title = stringResource(R.string.ai_assistant_web_settings), centerTitle = true)
            Column(
                Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()),
            ) {
                NgFormPanel {
                    AiAssistantSwitchRow(
                        title = stringResource(R.string.ai_assistant_web_enable),
                        summary = stringResource(R.string.ai_assistant_web_enable_hint),
                        checked = settings.webEnabled,
                        onCheckedChange = { enabled ->
                            AiExternalSearchStore.update { it.copy(webEnabled = enabled) }
                        },
                    )
                    NgFormGroupDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AssistantWebFormLabel(
                            title = stringResource(R.string.ai_search_route),
                            summary = stringResource(
                                if (automatic) R.string.ai_search_route_auto_hint
                                else R.string.ai_search_route_external_hint,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        NgFloatingTabBar(
                            items = listOf(
                                NgFloatingTabSpec(text = stringResource(R.string.ai_search_route_auto)),
                                NgFloatingTabSpec(text = stringResource(R.string.ai_search_route_external)),
                            ),
                            selectedIndex = if (automatic) 0 else 1,
                            onTabSelected = { index ->
                                val routing = if (index == 0) AiWebRoutingMode.AUTO else AiWebRoutingMode.EXTERNAL
                                AiExternalSearchStore.update { it.copy(routing = routing) }
                            },
                            modifier = Modifier.width(132.dp),
                            variant = NgFloatingTabBarVariant.FORM_SELECTION,
                        )
                    }
                    if (automatic) {
                        NgFormGroupDivider()
                        AiAssistantSwitchRow(
                            title = stringResource(R.string.ai_search_backup_service),
                            summary = stringResource(
                                if (settings.backupEnabled) R.string.ai_search_backup_on_hint
                                else R.string.ai_search_backup_off_hint,
                            ),
                            checked = settings.backupEnabled,
                            onCheckedChange = { enabled ->
                                AiExternalSearchStore.update { it.copy(backupEnabled = enabled) }
                            },
                        )
                    }
                }
                if (!automatic || settings.backupEnabled) {
                    AssistantWebServicePicker(
                        automatic = automatic,
                        services = availableServices,
                        selectedServiceId = settings.selectedService,
                        onSelect = { provider ->
                            AiExternalSearchStore.update { it.selectService(provider) }
                        },
                        onManageServices = onManageServices,
                    )
                }
            }
        }
    }
}

@Composable
internal fun AiAssistantSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AssistantWebFormLabel(title, summary, Modifier.weight(1f))
        NgSwitchControl(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.clearAndSetSemantics { },
            variant = NgSwitchControlVariant.COMPACT,
        )
    }
}

@Composable
private fun AssistantWebFormLabel(title: String, summary: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, color = Color(NgTheme.colors.onSurface), fontSize = 15.sp, lineHeight = 20.sp)
        Text(summary, color = Color(NgTheme.colors.onSurfaceVariant), fontSize = 12.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun AssistantWebServicePicker(
    automatic: Boolean,
    services: List<AiExternalSearchService>,
    selectedServiceId: String,
    onSelect: (AiExternalSearchProvider) -> Unit,
    onManageServices: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 2.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(if (automatic) R.string.ai_search_choose_backup else R.string.ai_search_choose_external),
            modifier = Modifier.weight(1f),
            color = Color(NgTheme.colors.onSurface),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        NgFormActionButton(
            text = stringResource(R.string.ai_search_manage_services),
            onClick = onManageServices,
            appearance = NgFormActionButtonAppearance.TEXT,
            textSize = 13.sp,
        )
    }
    if (services.isEmpty()) {
        AssistantWebPickerHint(stringResource(R.string.ai_search_no_available_services))
        return
    }

    val tags = searchServiceCapabilityTags()
    Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        services.forEach { service ->
            key(service.provider.id) {
                AssistantWebServiceCard(
                    service = service,
                    tags = tags,
                    selected = service.provider.id == selectedServiceId,
                    onClick = { onSelect(service.provider) },
                )
            }
        }
    }
    if (services.none { it.provider.id == selectedServiceId }) {
        AssistantWebPickerHint(stringResource(R.string.ai_search_choose_service_hint))
    }
}

@Composable
private fun AssistantWebPickerHint(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 6.dp),
        color = Color(NgTheme.colors.onSurfaceVariant),
        fontSize = 12.sp,
        lineHeight = 17.sp,
    )
}

@Composable
private fun AssistantWebServiceCard(
    service: AiExternalSearchService,
    tags: List<NgStatusTagSpec>,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(ngDrawerContentCardColor()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchServiceLogo(service.provider, size = 42.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = service.displayName,
                    color = ngDrawerPrimaryTextColor(colorResource(R.color.ng_on_surface)),
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    tags.forEach { NgStatusTag(it) }
                }
            }
        }
        AiSelectionCardIndicator(selected)
    }
}
