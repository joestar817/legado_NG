package io.legado.app.ui.config

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.legado.app.R
import io.legado.app.help.ai.AiConfig
import io.legado.app.help.ai.AiModel
import io.legado.app.help.ai.AiModelAbility
import io.legado.app.help.ai.AiModelModality
import io.legado.app.help.ai.AiModelType
import io.legado.app.help.ai.AiProviderSetting
import io.legado.app.help.ai.AiProviderStore
import io.legado.app.help.ai.AiProviderType
import io.legado.app.help.ai.AiReasoningLevel
import io.legado.app.lib.theme.accentColor
import io.legado.app.ui.widget.dialog.NgLongListBottomSheet
import io.legado.app.utils.applyTint

object AiAssistantConfigUi {

    data class AssistantModelOption(
        val provider: AiProviderSetting,
        val model: AiModel
    )

    fun selectedModel(): AssistantModelOption? {
        val providerId = AiConfig.assistantProviderId
        val modelId = AiConfig.assistantModelId
        if (providerId.isBlank() || modelId.isBlank()) {
            return null
        }
        val provider = AiProviderStore.provider(providerId)?.takeIf { it.enabled } ?: return null
        val model = provider.assistantEligibleModels().firstOrNull { it.safeId() == modelId }
            ?: return null
        return AssistantModelOption(provider, model)
    }

    fun selectedModelLabel(): String {
        return selectedModel()?.model?.displayName()
            ?: AiConfig.assistantModelId.substringAfterLast('/').ifBlank { "未选择模型" }
    }

    fun selectedModelIconRes(): Int {
        return selectedModel()?.let { selected ->
            selected.model.iconRes(selected.provider.iconRes())
        } ?: R.drawable.ic_cfg_web
    }

    fun selectedReasoningEnabled(): Boolean {
        return AiConfig.assistantReasoningLevel != AiReasoningLevel.OFF
    }

    fun showModelSelectSheet(
        context: Context,
        onChanged: () -> Unit
    ) {
        val sheet = NgLongListBottomSheet(
            context = context,
            searchHint = context.getString(R.string.ai_assistant_model_search_hint)
        )
        sheet.setScrollableContent { container, query, dialog ->
            renderModelOptions(context, container, query, dialog, onChanged)
        }
        sheet.show()
    }

    fun showReasoningSheet(
        context: Context,
        onChanged: () -> Unit
    ) {
        val selected = selectedModel()
        if (selected == null) {
            Toast.makeText(
                context,
                R.string.ai_assistant_reasoning_select_model_first,
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        if (!selected.model.supportsReasoning()) {
            Toast.makeText(
                context,
                R.string.ai_assistant_reasoning_unsupported,
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val levels = AiReasoningLevel.entries.toList()
        val accentColor = context.accentColor
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24.dpToPx(context), 10.dpToPx(context), 24.dpToPx(context), 28.dpToPx(context))
            background = GradientDrawable().apply {
                cornerRadii = floatArrayOf(
                    28.dpToPx(context).toFloat(), 28.dpToPx(context).toFloat(),
                    28.dpToPx(context).toFloat(), 28.dpToPx(context).toFloat(),
                    0f, 0f,
                    0f, 0f
                )
                setColor(ContextCompat.getColor(context, R.color.ng_surface_soft))
            }
        }
        root.addView(View(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = 2.dpToPx(context).toFloat()
                setColor(ContextCompat.getColor(context, R.color.ng_on_surface_variant))
            }
        }, LinearLayout.LayoutParams(40.dpToPx(context), 4.dpToPx(context)).apply {
            bottomMargin = 26.dpToPx(context)
        })
        root.addView(TextView(context).apply {
            text = context.getString(R.string.ai_assistant_reasoning_title)
            setTextColor(ContextCompat.getColor(context, R.color.ng_on_surface))
            typeface = Typeface.DEFAULT_BOLD
            textSize = 22f
            gravity = Gravity.CENTER
        })
        root.addView(TextView(context).apply {
            text = context.getString(R.string.ai_assistant_reasoning_desc)
            setTextColor(ContextCompat.getColor(context, R.color.ng_on_surface_variant))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 8.dpToPx(context), 0, 22.dpToPx(context))
        })
        val currentLabel = TextView(context).apply {
            text = AiConfig.assistantReasoningLevel.displayName(context)
            setTextColor(ContextCompat.getColor(context, R.color.ng_on_surface))
            textSize = 18f
            gravity = Gravity.CENTER
        }
        val currentIcon = ImageView(context).apply {
            setImageResource(R.drawable.ic_ai_capability_reasoning)
            imageTintList = if (selectedReasoningEnabled()) {
                ColorStateList.valueOf(accentColor)
            } else {
                null
            }
        }
        root.addView(currentIcon, LinearLayout.LayoutParams(42.dpToPx(context), 42.dpToPx(context)))
        root.addView(currentLabel, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 8.dpToPx(context)
            bottomMargin = 22.dpToPx(context)
        })
        val stepBar = ReasoningStepBar(context).apply {
            stepCount = levels.size
            selectedIndex = levels.indexOf(AiConfig.assistantReasoningLevel).coerceAtLeast(0)
            stepColor = accentColor
            onSelectedIndexChanged = { index ->
                val level = levels.getOrNull(index) ?: AiReasoningLevel.AUTO
                AiConfig.assistantReasoningLevel = level
                currentLabel.text = level.displayName(context)
                currentIcon.imageTintList = if (level != AiReasoningLevel.OFF) {
                    ColorStateList.valueOf(accentColor)
                } else {
                    null
                }
                onChanged()
            }
        }
        root.addView(stepBar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            42.dpToPx(context)
        ))
        root.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            levels.forEach { level ->
                addView(TextView(context).apply {
                    text = level.displayName(context)
                    gravity = Gravity.CENTER
                    setTextColor(ContextCompat.getColor(context, R.color.ng_on_surface_variant))
                    textSize = 13f
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    weight = 1f
                })
            }
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 12.dpToPx(context)
        })
        val dialog = BottomSheetDialog(context)
        dialog.setContentView(root)
        dialog.setOnShowListener {
            val sheet = dialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            ) ?: return@setOnShowListener
            sheet.setBackgroundColor(Color.TRANSPARENT)
            BottomSheetBehavior.from(sheet).state = BottomSheetBehavior.STATE_EXPANDED
        }
        dialog.show()
    }

    fun showInternalMcpSheet(
        context: Context,
        onChanged: () -> Unit
    ) {
        val accentColor = context.accentColor
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dpToPx(context), 10.dpToPx(context), 24.dpToPx(context), 26.dpToPx(context))
            background = GradientDrawable().apply {
                cornerRadii = floatArrayOf(
                    28.dpToPx(context).toFloat(), 28.dpToPx(context).toFloat(),
                    28.dpToPx(context).toFloat(), 28.dpToPx(context).toFloat(),
                    0f, 0f,
                    0f, 0f
                )
                setColor(ContextCompat.getColor(context, R.color.ng_surface_soft))
            }
        }
        root.addView(View(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = 2.dpToPx(context).toFloat()
                setColor(ContextCompat.getColor(context, R.color.ng_on_surface_variant))
            }
        }, LinearLayout.LayoutParams(40.dpToPx(context), 4.dpToPx(context)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = 26.dpToPx(context)
        })
        root.addView(TextView(context).apply {
            text = context.getString(R.string.ai_internal_mcp)
            setTextColor(ContextCompat.getColor(context, R.color.ng_on_surface))
            typeface = Typeface.DEFAULT_BOLD
            textSize = 22f
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        root.addView(TextView(context).apply {
            text = context.getString(
                if (AiConfig.internalMcpEnabled) {
                    R.string.ai_internal_mcp_summary_on
                } else {
                    R.string.ai_internal_mcp_summary_off
                }
            )
            tag = "summary"
            setTextColor(ContextCompat.getColor(context, R.color.ng_on_surface_variant))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 8.dpToPx(context), 0, 22.dpToPx(context))
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        val summaryView = root.findViewWithTag<TextView>("summary")
        root.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            background = GradientDrawable().apply {
                cornerRadius = 18.dpToPx(context).toFloat()
                setColor(ContextCompat.getColor(context, R.color.ng_surface_card))
            }
            setPadding(16.dpToPx(context), 14.dpToPx(context), 16.dpToPx(context), 14.dpToPx(context))
            addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_ai_capability_tool)
                imageTintList = ColorStateList.valueOf(accentColor)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(ColorUtils.setAlphaComponent(accentColor, 24))
                }
                setPadding(10.dpToPx(context), 10.dpToPx(context), 10.dpToPx(context), 10.dpToPx(context))
            }, LinearLayout.LayoutParams(48.dpToPx(context), 48.dpToPx(context)).apply {
                marginEnd = 14.dpToPx(context)
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = context.getString(R.string.ai_internal_mcp)
                    setTextColor(ContextCompat.getColor(context, R.color.ng_on_surface))
                    typeface = Typeface.DEFAULT_BOLD
                    textSize = 16f
                })
                addView(TextView(context).apply {
                    text = context.getString(
                        if (AiConfig.internalMcpEnabled) {
                            R.string.ai_internal_mcp_summary_on
                        } else {
                            R.string.ai_internal_mcp_summary_off
                        }
                    )
                    tag = "rowSummary"
                    setTextColor(ContextCompat.getColor(context, R.color.ng_on_surface_variant))
                    textSize = 13f
                    setPadding(0, 4.dpToPx(context), 0, 0)
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                weight = 1f
            })
            val rowSummaryView = findViewWithTag<TextView>("rowSummary")
            var ignoreSwitchChange = false
            val switchView = Switch(context).apply {
                isChecked = AiConfig.internalMcpEnabled
                applyTint(accentColor)
                setOnCheckedChangeListener { _, isChecked ->
                    if (ignoreSwitchChange) {
                        return@setOnCheckedChangeListener
                    }
                    AiConfig.internalMcpEnabled = isChecked
                    val summaryRes = if (isChecked) {
                        R.string.ai_internal_mcp_summary_on
                    } else {
                        R.string.ai_internal_mcp_summary_off
                    }
                    summaryView.text = context.getString(summaryRes)
                    rowSummaryView.text = context.getString(summaryRes)
                    onChanged()
                }
            }
            addView(switchView, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = 14.dpToPx(context)
            })
            setOnClickListener {
                ignoreSwitchChange = true
                switchView.isChecked = !switchView.isChecked
                ignoreSwitchChange = false
                AiConfig.internalMcpEnabled = switchView.isChecked
                val summaryRes = if (switchView.isChecked) {
                    R.string.ai_internal_mcp_summary_on
                } else {
                    R.string.ai_internal_mcp_summary_off
                }
                summaryView.text = context.getString(summaryRes)
                rowSummaryView.text = context.getString(summaryRes)
                onChanged()
            }
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        val dialog = BottomSheetDialog(context)
        dialog.setContentView(root)
        dialog.setOnShowListener {
            val sheet = dialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            ) ?: return@setOnShowListener
            sheet.setBackgroundColor(Color.TRANSPARENT)
            BottomSheetBehavior.from(sheet).state = BottomSheetBehavior.STATE_EXPANDED
        }
        dialog.show()
    }

    private fun renderModelOptions(
        context: Context,
        container: LinearLayout,
        query: String,
        dialog: BottomSheetDialog,
        onChanged: () -> Unit
    ) {
        container.removeAllViews()
        val groupedOptions = modelOptions(query.trim())
        if (groupedOptions.isEmpty()) {
            container.addView(TextView(context).apply {
                text = context.getString(R.string.ai_assistant_model_empty)
                setTextColor(ContextCompat.getColor(context, R.color.ng_on_surface_variant))
                textSize = 15f
                gravity = Gravity.CENTER
                setPadding(0, 44.dpToPx(context), 0, 44.dpToPx(context))
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            return
        }
        groupedOptions.forEach { (provider, models) ->
            container.addView(createProviderHeader(context, provider))
            models.forEach { model ->
                container.addView(createModelCard(context, provider, model, dialog, onChanged))
            }
        }
    }

    private fun modelOptions(query: String): List<Pair<AiProviderSetting, List<AiModel>>> {
        return AiProviderStore.providers()
            .filter { it.enabled }
            .mapNotNull { provider ->
                val models = provider.assistantEligibleModels()
                    .filter { model ->
                        query.isBlank()
                            || provider.name.contains(query, ignoreCase = true)
                            || model.safeId().contains(query, ignoreCase = true)
                            || model.safeName().contains(query, ignoreCase = true)
                            || model.displayName().contains(query, ignoreCase = true)
                    }
                models.takeIf { it.isNotEmpty() }?.let { provider to it }
            }
    }

    private fun createProviderHeader(
        context: Context,
        provider: AiProviderSetting
    ): TextView {
        return TextView(context).apply {
            text = provider.name
            setTextColor(context.accentColor)
            typeface = Typeface.DEFAULT_BOLD
            textSize = 15f
            setPadding(2.dpToPx(context), 12.dpToPx(context), 2.dpToPx(context), 8.dpToPx(context))
        }
    }

    private fun createModelCard(
        context: Context,
        provider: AiProviderSetting,
        model: AiModel,
        dialog: BottomSheetDialog,
        onChanged: () -> Unit
    ): LinearLayout {
        val selected = AiConfig.assistantProviderId == provider.id &&
            AiConfig.assistantModelId == model.safeId()
        val accentColor = context.accentColor
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            background = GradientDrawable().apply {
                cornerRadius = 18.dpToPx(context).toFloat()
                setColor(ContextCompat.getColor(context, R.color.ng_surface_card))
            }
            setPadding(14.dpToPx(context), 12.dpToPx(context), 14.dpToPx(context), 12.dpToPx(context))
            setOnClickListener {
                AiConfig.saveAssistantModel(provider.id, model.safeId())
                onChanged()
                dialog.dismiss()
            }
            addView(ImageView(context).apply {
                setImageResource(model.iconRes(provider.iconRes()))
                background = ContextCompat.getDrawable(context, R.drawable.ng_bg_icon_circle)
                setPadding(7.dpToPx(context), 7.dpToPx(context), 7.dpToPx(context), 7.dpToPx(context))
            }, LinearLayout.LayoutParams(46.dpToPx(context), 46.dpToPx(context)).apply {
                marginEnd = 12.dpToPx(context)
            })
            val info = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = model.displayName()
                    setTextColor(ContextCompat.getColor(context, R.color.ng_on_surface))
                    typeface = Typeface.DEFAULT_BOLD
                    textSize = 16f
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                })
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 6.dpToPx(context), 0, 0)
                    model.capabilityTags().forEach {
                        addView(createModelCapabilityTag(context, it))
                    }
                })
            }
            addView(info, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                weight = 1f
            })
            addView(ImageView(context).apply {
                if (selected) {
                    setImageResource(R.drawable.ic_check)
                    imageTintList = ColorStateList.valueOf(accentColor)
                } else {
                    imageTintList = null
                }
            }, LinearLayout.LayoutParams(32.dpToPx(context), 32.dpToPx(context)).apply {
                marginStart = 10.dpToPx(context)
            })
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 12.dpToPx(context)
            }
        }
    }
}

internal class ReasoningStepBar(context: Context) : View(context) {

    var stepCount: Int = 0
        set(value) {
            field = value.coerceAtLeast(0)
            selectedIndex = selectedIndex.coerceIn(0, (field - 1).coerceAtLeast(0))
            invalidate()
        }

    var selectedIndex: Int = 0
        set(value) {
            field = value.coerceIn(0, (stepCount - 1).coerceAtLeast(0))
            invalidate()
        }

    var stepColor: Int = Color.TRANSPARENT
        set(value) {
            field = value
            activePaint.color = value
            selectedPaint.color = value
            tickPaint.color = ColorUtils.setAlphaComponent(value, 190)
            inactivePaint.color = ColorUtils.setAlphaComponent(value, 38)
            invalidate()
        }

    var onSelectedIndexChanged: ((Int) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val radius = 4.5f * density
    private val selectedRadius = 6f * density
    private val tickStroke = 1.5f * density
    private val trackStroke = 2f * density
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = tickStroke
    }
    private val inactivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = trackStroke
    }
    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = trackStroke
    }
    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    init {
        isClickable = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (stepCount <= 1 || width <= 0 || height <= 0) {
            return
        }
        val y = height / 2f
        repeat(stepCount - 1) { index ->
            val leftX = stepCenterX(index) + stepRadius(index)
            val rightX = stepCenterX(index + 1) - stepRadius(index + 1)
            canvas.drawLine(
                leftX,
                y,
                rightX,
                y,
                if (index < selectedIndex) activePaint else inactivePaint
            )
        }
        repeat(stepCount) { index ->
            val x = stepCenterX(index)
            if (index == selectedIndex) {
                canvas.drawCircle(x, y, selectedRadius, selectedPaint)
            } else {
                canvas.drawCircle(x, y, radius, tickPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || stepCount <= 1) {
            return super.onTouchEvent(event)
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                updateSelection(event.x)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                updateSelection(event.x)
                return true
            }
            MotionEvent.ACTION_UP -> {
                updateSelection(event.x)
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun updateSelection(x: Float) {
        val index = nearestStepIndex(x)
        if (index != selectedIndex) {
            selectedIndex = index
            onSelectedIndexChanged?.invoke(index)
        }
    }

    private fun nearestStepIndex(x: Float): Int {
        val startX = stepCenterX(0)
        val endX = stepCenterX(stepCount - 1)
        val progress = ((x.coerceIn(startX, endX) - startX) / (endX - startX))
            .coerceIn(0f, 1f)
        return (progress * (stepCount - 1) + 0.5f).toInt()
            .coerceIn(0, stepCount - 1)
    }

    private fun stepCenterX(index: Int): Float {
        val segmentWidth = width.toFloat() / stepCount
        return segmentWidth * index + segmentWidth / 2f
    }

    private fun stepRadius(index: Int): Float {
        return if (index == selectedIndex) selectedRadius else radius
    }
}

internal enum class ModelCapabilityTag(
    val iconRes: Int,
    val labelRes: Int,
    val color: Int
) {
    TEXT(R.drawable.ic_ai_capability_text, R.string.ai_model_modality_text, 0xFF7C3AED.toInt()),
    VISION(R.drawable.ic_ai_capability_vision, R.string.ai_model_modality_image, 0xFFF59E0B.toInt()),
    VIDEO(R.drawable.ic_ai_capability_video, R.string.ai_model_modality_video, 0xFFEA580C.toInt()),
    ASR(R.drawable.ic_ai_capability_asr, R.string.ai_model_ability_asr, 0xFFDB2777.toInt()),
    TTS(R.drawable.ic_ai_capability_tts, R.string.ai_model_ability_tts, 0xFF0891B2.toInt()),
    TOOL(R.drawable.ic_ai_capability_tool, R.string.ai_model_ability_tool, 0xFF2563EB.toInt()),
    REASONING(
        R.drawable.ic_ai_capability_reasoning,
        R.string.ai_model_ability_reasoning,
        0xFF16A34A.toInt()
    )
}

internal fun createModelCapabilityTag(
    context: Context,
    capability: ModelCapabilityTag
): ImageView {
    val view = ImageView(context)
    val tintColor = capability.color
    val backgroundColor = ColorUtils.setAlphaComponent(tintColor, 24)
    view.setImageResource(capability.iconRes)
    view.imageTintList = ColorStateList.valueOf(tintColor)
    view.background = GradientDrawable().apply {
        cornerRadius = 5.dpToPx(context).toFloat()
        setColor(backgroundColor)
        setStroke(1.dpToPx(context), tintColor)
    }
    view.contentDescription = context.getString(capability.labelRes)
    view.scaleType = ImageView.ScaleType.CENTER_INSIDE
    view.setPadding(3.dpToPx(context), 3.dpToPx(context), 3.dpToPx(context), 3.dpToPx(context))
    view.layoutParams = LinearLayout.LayoutParams(
        20.dpToPx(context),
        20.dpToPx(context)
    ).apply {
        marginEnd = 4.dpToPx(context)
    }
    return view
}

internal fun AiProviderSetting.assistantEligibleModels(): List<AiModel> {
    val availableIds = effectiveAvailableModelIds().toSet()
    if (availableIds.isEmpty()) {
        return emptyList()
    }
    return displayModels()
        .filter { it.safeId() in availableIds }
        .filter { it.supportsChatText() }
}

internal fun AiProviderSetting.displayModels(): List<AiModel> {
    if (apiKey.isBlank()) {
        return emptyList()
    }
    return models
        .filter { it.safeId().isNotBlank() }
        .distinctBy { it.safeId() }
}

internal fun AiProviderSetting.effectiveAvailableModelIds(): List<String> {
    val cachedIds = models.map { it.safeId() }.filter { it.isNotBlank() }
    return if (availableModelSelectionInitialized) {
        availableModelIds.filter { it in cachedIds }
    } else {
        cachedIds
    }
}

internal fun AiModel.supportsChatText(): Boolean {
    return safeType() == AiModelType.CHAT &&
        AiModelModality.TEXT in safeInputModalities() &&
        AiModelModality.TEXT in safeOutputModalities()
}

internal fun AiModel.supportsReasoning(): Boolean {
    return AiModelAbility.REASONING in safeAbilities()
}

internal fun AiModel.displayName(): String {
    return safeDisplayName().ifBlank {
        val rawName = safeName().ifBlank { safeId() }.trim()
        rawName.substringAfterLast('/').ifBlank {
            safeId().substringAfterLast('/').ifBlank { safeId() }
        }
    }
}

internal fun AiModel.capabilityTags(): List<ModelCapabilityTag> {
    val abilities = safeAbilities()
    if (AiModelAbility.ASR in abilities) {
        return listOf(ModelCapabilityTag.ASR)
    }
    if (AiModelAbility.TTS in abilities) {
        return listOf(ModelCapabilityTag.TTS)
    }
    return buildList {
        if (
            AiModelModality.TEXT in safeInputModalities()
            || AiModelModality.TEXT in safeOutputModalities()
        ) {
            add(ModelCapabilityTag.TEXT)
        }
        if (
            AiModelModality.IMAGE in safeInputModalities()
            || AiModelModality.IMAGE in safeOutputModalities()
        ) {
            add(ModelCapabilityTag.VISION)
        }
        if (
            AiModelModality.VIDEO in safeInputModalities()
            || AiModelModality.VIDEO in safeOutputModalities()
        ) {
            add(ModelCapabilityTag.VIDEO)
        }
        if (AiModelAbility.TOOL in safeAbilities()) {
            add(ModelCapabilityTag.TOOL)
        }
        if (AiModelAbility.REASONING in abilities) {
            add(ModelCapabilityTag.REASONING)
        }
    }
}

internal fun AiProviderSetting.iconRes(): Int {
    return when (id) {
        "openai" -> R.drawable.ic_provider_openai
        "claude" -> R.drawable.ic_provider_claude
        "gemini" -> R.drawable.ic_provider_gemini
        "deepseek" -> R.drawable.ic_provider_deepseek
        "xiaomi_mimo" -> R.drawable.ic_provider_mimo
        "siliconflow" -> R.drawable.ic_provider_siliconflow
        "openrouter" -> R.drawable.ic_provider_openrouter
        "aliyun_bailian" -> R.drawable.ic_provider_aliyun
        "volcengine" -> R.drawable.ic_provider_volcengine
        "moonshot" -> R.drawable.ic_provider_moonshot
        "zhipu" -> R.drawable.ic_provider_zhipu
        else -> when (type) {
            AiProviderType.OPENAI -> R.drawable.ic_provider_openai
            AiProviderType.CLAUDE -> R.drawable.ic_provider_claude
            AiProviderType.GOOGLE -> R.drawable.ic_provider_gemini
        }
    }
}

internal fun AiModel.iconRes(defaultIconRes: Int): Int {
    val modelName = listOf(safeId(), safeName(), safeOwnedBy())
        .joinToString(" ")
        .lowercase()
    return when {
        Regex("(gpt|openai|o\\d)").containsMatchIn(modelName) -> R.drawable.ic_provider_openai
        Regex("(gemini|nano-banana)").containsMatchIn(modelName) -> R.drawable.ic_provider_gemini
        Regex("google").containsMatchIn(modelName) -> R.drawable.ic_model_google
        Regex("claude").containsMatchIn(modelName) -> R.drawable.ic_provider_claude
        Regex("anthropic").containsMatchIn(modelName) -> R.drawable.ic_model_anthropic
        Regex("deepseek").containsMatchIn(modelName) -> R.drawable.ic_provider_deepseek
        Regex("grok").containsMatchIn(modelName) -> R.drawable.ic_model_grok
        Regex("qwen|qwq|qvq").containsMatchIn(modelName) -> R.drawable.ic_model_qwen
        Regex("doubao").containsMatchIn(modelName) -> R.drawable.ic_model_doubao
        Regex("openrouter").containsMatchIn(modelName) -> R.drawable.ic_provider_openrouter
        Regex("zhipu|智谱|glm").containsMatchIn(modelName) -> R.drawable.ic_provider_zhipu
        Regex("mistral").containsMatchIn(modelName) -> R.drawable.ic_model_mistral
        Regex("meta\\b|(?<!o)llama").containsMatchIn(modelName) -> R.drawable.ic_model_meta
        Regex("hunyuan|tencent").containsMatchIn(modelName) -> R.drawable.ic_model_hunyuan
        Regex("gemma").containsMatchIn(modelName) -> R.drawable.ic_model_gemma
        Regex("perplexity").containsMatchIn(modelName) -> R.drawable.ic_model_perplexity
        Regex("aliyun|阿里云|百炼").containsMatchIn(modelName) -> R.drawable.ic_provider_aliyun
        Regex("bytedance|火山|seed").containsMatchIn(modelName) -> R.drawable.ic_provider_volcengine
        Regex("silicon|硅基").containsMatchIn(modelName) -> R.drawable.ic_provider_siliconflow
        Regex("aihubmix").containsMatchIn(modelName) -> R.drawable.ic_model_aihubmix
        Regex("ollama").containsMatchIn(modelName) -> R.drawable.ic_model_ollama
        Regex("github").containsMatchIn(modelName) -> R.drawable.ic_model_github
        Regex("cloudflare").containsMatchIn(modelName) -> R.drawable.ic_model_cloudflare
        Regex("minimax").containsMatchIn(modelName) -> R.drawable.ic_model_minimax
        Regex("xai").containsMatchIn(modelName) -> R.drawable.ic_model_xai
        Regex("juhenext").containsMatchIn(modelName) -> R.drawable.ic_model_juhenext
        Regex("kimi").containsMatchIn(modelName) -> R.drawable.ic_model_kimi
        Regex("moonshot|月之暗面").containsMatchIn(modelName) -> R.drawable.ic_provider_moonshot
        Regex("302").containsMatchIn(modelName) -> R.drawable.ic_model_302ai
        Regex("step|阶跃").containsMatchIn(modelName) -> R.drawable.ic_model_stepfun
        Regex("intern|书生").containsMatchIn(modelName) -> R.drawable.ic_model_internlm
        Regex("cohere|command-.+").containsMatchIn(modelName) -> R.drawable.ic_model_cohere
        Regex("tavern").containsMatchIn(modelName) -> R.drawable.ic_model_tavern
        Regex("cerebras").containsMatchIn(modelName) -> R.drawable.ic_model_cerebras
        Regex("nvidia").containsMatchIn(modelName) -> R.drawable.ic_model_nvidia
        Regex("ppio|派欧").containsMatchIn(modelName) -> R.drawable.ic_model_ppio
        Regex("vercel").containsMatchIn(modelName) -> R.drawable.ic_model_vercel
        Regex("groq").containsMatchIn(modelName) -> R.drawable.ic_model_groq
        Regex("tokenpony|小马算力").containsMatchIn(modelName) -> R.drawable.ic_model_tokenpony
        Regex("ling|ring|百灵").containsMatchIn(modelName) -> R.drawable.ic_model_ling
        Regex("mimo|xiaomi|小米").containsMatchIn(modelName) -> R.drawable.ic_provider_mimo
        Regex("longcat").containsMatchIn(modelName) -> R.drawable.ic_model_longcat
        else -> defaultIconRes
    }
}

private fun AiReasoningLevel.displayName(context: Context): String {
    return when (this) {
        AiReasoningLevel.OFF -> context.getString(R.string.ai_reasoning_level_off)
        AiReasoningLevel.AUTO -> context.getString(R.string.ai_reasoning_level_auto)
        AiReasoningLevel.LOW -> context.getString(R.string.ai_reasoning_level_low)
        AiReasoningLevel.MEDIUM -> context.getString(R.string.ai_reasoning_level_medium)
        AiReasoningLevel.HIGH -> context.getString(R.string.ai_reasoning_level_high)
        AiReasoningLevel.ULTRA -> context.getString(R.string.ai_reasoning_level_ultra)
    }
}

internal fun AiModel.safeId(): String {
    return runCatching { id }.getOrNull().orEmpty()
}

internal fun AiModel.safeName(): String {
    return runCatching { name }.getOrNull().orEmpty()
}

internal fun AiModel.safeDisplayName(): String {
    return runCatching { displayName }.getOrNull().orEmpty()
}

internal fun AiModel.safeOwnedBy(): String {
    return runCatching { ownedBy }.getOrNull().orEmpty()
}

internal fun AiModel.safeType(): AiModelType {
    return runCatching { type }.getOrNull() ?: AiModelType.CHAT
}

internal fun AiModel.safeInputModalities(): List<AiModelModality> {
    return runCatching { inputModalities }.getOrNull().orEmpty()
}

internal fun AiModel.safeOutputModalities(): List<AiModelModality> {
    return runCatching { outputModalities }.getOrNull().orEmpty()
}

internal fun AiModel.safeAbilities(): List<AiModelAbility> {
    return runCatching { abilities }.getOrNull().orEmpty()
}

private fun Int.dpToPx(context: Context): Int {
    return (this * context.resources.displayMetrics.density + 0.5f).toInt()
}
