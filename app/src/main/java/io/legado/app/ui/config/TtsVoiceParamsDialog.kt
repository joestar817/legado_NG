package io.legado.app.ui.config

import android.content.Context
import androidx.activity.ComponentDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntRect
import io.legado.app.R
import io.legado.app.help.tts.TtsEngineSetting
import io.legado.app.help.tts.TtsEngineStore
import io.legado.app.help.tts.TtsVoice
import io.legado.app.help.tts.TtsVoicePlaybackParams
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.NgDialogTextActionButton
import io.legado.app.ui.design.components.compose.NgPaneDialogContent
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.widget.dialog.applyNgWindow

internal fun showTtsVoiceParamsDialog(
    context: Context,
    engine: TtsEngineSetting,
    voice: TtsVoice,
    onEngineUpdated: (TtsEngineSetting) -> Unit,
    onDismissed: () -> Unit = {},
    paneBounds: State<IntRect?>? = null,
): ComponentDialog {
    val dialog = ComponentDialog(context)
    val composeView = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent {
            NgAppTheme(updateSystemBars = false) {
                val content: @Composable () -> Unit = {
                    TtsVoiceParamsDialogContent(
                        voiceName = voice.name,
                        initialParams = engine.voicePlaybackParams(voice.id),
                        initiallyCustomized = engine.hasVoiceParams(voice.id),
                        onParamsChanged = { params ->
                            TtsEngineStore.saveVoiceParams(
                                engineId = engine.id,
                                voiceId = voice.id,
                                params = params,
                            )?.let(onEngineUpdated)
                        },
                        onFollowEngine = {
                            TtsEngineStore.removeVoiceParams(engine.id, voice.id)
                                ?.let(onEngineUpdated)
                            dialog.dismiss()
                        },
                        onDismiss = dialog::dismiss,
                    )
                }
                if (paneBounds == null) {
                    content()
                } else {
                    NgPaneDialogContent(
                        bounds = paneBounds.value,
                        onDismissRequest = dialog::dismiss,
                        window = dialog.window,
                        content = content,
                    )
                    SideEffect {
                        if (paneBounds.value == null) dialog.applyNgWindow()
                    }
                }
            }
        }
    }
    dialog.setContentView(composeView)
    dialog.setOnDismissListener { onDismissed() }
    if (paneBounds != null) dialog.applyNgWindow()
    dialog.show()
    if (paneBounds == null) dialog.applyNgWindow()
    return dialog
}

@Composable
private fun TtsVoiceParamsDialogContent(
    voiceName: String,
    initialParams: TtsVoicePlaybackParams,
    initiallyCustomized: Boolean,
    onParamsChanged: (TtsVoicePlaybackParams) -> Unit,
    onFollowEngine: () -> Unit,
    onDismiss: () -> Unit,
) {
    var params by remember { mutableStateOf(initialParams) }
    var customized by remember { mutableStateOf(initiallyCustomized) }
    val saveParams = {
        customized = !params.isNeutral()
        onParamsChanged(params)
    }
    NgDialog(
        title = stringResource(R.string.tts_voice_params_title, voiceName),
        variant = NgDialogVariant.STANDARD,
        actions = {
            if (customized) {
                NgDialogTextActionButton(
                    text = stringResource(R.string.tts_voice_follow_engine),
                    onClick = onFollowEngine,
                )
            }
            NgDialogTextActionButton(
                text = stringResource(R.string.complete),
                onClick = onDismiss,
            )
        },
    ) {
        TtsVoicePlaybackParamsSliderPanel(
            speedRatio = params.speedRatio,
            volumeGain = params.volumeGain,
            pitchRatio = params.pitchRatio,
            onSpeedChange = { params = params.copy(speedRatio = it) },
            onVolumeChange = { params = params.copy(volumeGain = it) },
            onPitchChange = { params = params.copy(pitchRatio = it) },
            onValueChangeFinished = saveParams,
        )
    }
}
