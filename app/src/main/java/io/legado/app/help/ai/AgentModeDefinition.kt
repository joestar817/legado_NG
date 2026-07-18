package io.legado.app.help.ai

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import io.legado.app.help.ai.runtime.AgentHookBinding
import io.legado.app.help.ai.runtime.AgentHookRegistry
import io.legado.app.web.mcp.McpInternalToolCatalog
import io.legado.app.web.mcp.McpMemoryAccessRange
import io.legado.app.web.mcp.McpMemoryAccess

enum class AgentModeKind {
    GENERAL,
    SYSTEM
}

data class AgentModeDefinition(
    val id: String,
    val revision: String,
    val kind: AgentModeKind,
    val allowsUserSkills: Boolean,
    val allowsManualMcpCapabilities: Boolean,
    val systemWorkflowId: String? = null,
    val systemSkillIds: List<String> = emptyList(),
    val fixedMcpCapabilityIds: List<String> = emptyList(),
    val memoryPolicy: AgentModeMemoryPolicy? = null,
    val entryPolicy: AgentModeEntryPolicy = AgentModeEntryPolicy(),
    val runtimeHooks: List<AgentHookBinding> = emptyList()
) {
    init {
        require(id.matches(ID_REGEX)) { "Agent Mode id 不合法：$id" }
        require(revision.isNotBlank()) { "Agent Mode revision 不能为空" }
        require((kind == AgentModeKind.SYSTEM) == (systemWorkflowId != null)) {
            "System Mode 必须绑定只读 System Workflow，General Mode 不能绑定"
        }
        require(kind != AgentModeKind.SYSTEM || !allowsUserSkills) {
            "System Mode 不能加载用户 Skill"
        }
        require(systemSkillIds.distinct().size == systemSkillIds.size) {
            "Agent Mode 固定 System Skill 不能重复"
        }
        require(
            kind != AgentModeKind.SYSTEM ||
                systemWorkflowId in availableSystemSkillIds
        ) {
            "System Mode 的工作流入口必须包含在固定 System Skill 集合中"
        }
        require(fixedMcpCapabilityIds.distinct().size == fixedMcpCapabilityIds.size) {
            "Agent Mode 固定 MCP capability 不能重复"
        }
    }

    val identity: AgentModeIdentity
        get() = AgentModeIdentity(id = id, revision = revision)

    val availableSystemSkillIds: List<String>
        get() = systemSkillIds.ifEmpty { listOfNotNull(systemWorkflowId) }

    private companion object {
        val ID_REGEX = Regex("^[a-z][a-z0-9_]{1,63}$")
    }
}

data class AgentModeIdentity(
    val id: String,
    val revision: String
)

data class AgentModeMemoryPolicy(
    val allowedRanges: List<McpMemoryAccessRange>
) {
    init {
        require(allowedRanges.isNotEmpty()) { "Agent Mode memory policy 不能为空" }
        require(allowedRanges.distinct().size == allowedRanges.size) {
            "Agent Mode memory policy range 不能重复"
        }
    }
}

data class AgentModeEntryPolicy(
    @SerializedName("requires_context")
    val requiresContext: Boolean = false,
    @SerializedName("start_behavior")
    val startBehavior: AgentModeEntryStartBehavior = AgentModeEntryStartBehavior.NONE,
    @SerializedName("confirmation")
    val confirmation: AgentModeEntryConfirmation? = null,
    @SerializedName("memory_probe")
    val memoryProbe: AgentModeEntryMemoryProbe? = null
) {
    init {
        val hostPresentationCount = listOfNotNull(confirmation, memoryProbe).size
        require(
            (startBehavior == AgentModeEntryStartBehavior.HOST_CONFIRMATION) ==
                (hostPresentationCount == 1)
        ) {
            "HOST_CONFIRMATION 必须且只能配置一种宿主入口展示"
        }
    }
}

enum class AgentModeEntryStartBehavior {
    @SerializedName("none")
    NONE,

    @SerializedName("automatic")
    AUTOMATIC,

    @SerializedName("host_confirmation")
    HOST_CONFIRMATION
}

data class AgentModeEntryConfirmation(
    @SerializedName("title")
    val title: String,
    @SerializedName("description")
    val description: String,
    @SerializedName("confirm_label")
    val confirmLabel: String,
    @SerializedName("actions")
    val actions: List<AgentModeEntryAction> = emptyList()
) {
    init {
        require(title.isNotBlank()) { "Agent Mode 入口确认标题不能为空" }
        require(description.isNotBlank()) { "Agent Mode 入口确认说明不能为空" }
        require(confirmLabel.isNotBlank()) { "Agent Mode 入口确认按钮不能为空" }
        require(actions.map { it.value }.distinct().size == actions.size) {
            "Agent Mode 入口确认动作不能重复"
        }
    }
}

data class AgentModeEntryAction(
    @SerializedName("label")
    val label: String,
    @SerializedName("value")
    val value: String,
    @SerializedName("description")
    val description: String = ""
) {
    init {
        require(label.isNotBlank()) { "Agent Mode 入口确认动作标题不能为空" }
        require(value.matches(ENTRY_ACTION_VALUE_REGEX)) {
            "Agent Mode 入口确认动作 value 不合法：$value"
        }
    }
}

private val ENTRY_ACTION_VALUE_REGEX = Regex("^[a-z][a-z0-9_]{1,63}$")

data class AgentModeEntryNotice(
    @SerializedName("title")
    val title: String,
    @SerializedName("description")
    val description: String
) {
    init {
        require(title.isNotBlank()) { "Agent Mode 入口提示标题不能为空" }
        require(description.isNotBlank()) { "Agent Mode 入口提示说明不能为空" }
    }
}

/**
 * A declarative, read-only local-memory probe used before a System Mode starts.
 *
 * The probe only answers whether one matching memory exists. It never restores a chat,
 * interprets payload JSON, or decides whether the workflow-specific memory is current.
 */
data class AgentModeEntryMemoryProbe(
    @SerializedName("scope_type")
    val scopeType: String,
    @SerializedName("scope_key_context_path")
    val scopeKeyContextPath: String,
    @SerializedName("domain")
    val domain: String,
    @SerializedName("memory_type")
    val memoryType: String,
    @SerializedName("status")
    val status: String = "active",
    @SerializedName("loading")
    val loading: AgentModeEntryNotice,
    @SerializedName("absent")
    val absent: AgentModeEntryConfirmation,
    @SerializedName("present")
    val present: AgentModeEntryConfirmation,
    @SerializedName("disabled")
    val disabled: AgentModeEntryNotice,
    @SerializedName("error")
    val error: AgentModeEntryNotice
) {
    init {
        require(scopeType.isNotBlank() && scopeType == scopeType.trim()) {
            "Agent Mode 入口 Memory probe scopeType 不合法"
        }
        require(scopeKeyContextPath.matches(CONTEXT_PATH_REGEX)) {
            "Agent Mode 入口 Memory probe context path 不合法"
        }
        require(domain.isNotBlank() && domain == domain.trim()) {
            "Agent Mode 入口 Memory probe domain 不合法"
        }
        require(memoryType == memoryType.trim()) {
            "Agent Mode 入口 Memory probe memoryType 不合法"
        }
        require(status.isNotBlank() && status == status.trim()) {
            "Agent Mode 入口 Memory probe status 不合法"
        }
    }

    private companion object {
        val CONTEXT_PATH_REGEX = Regex("^payload\\.[A-Za-z0-9_]{1,128}$")
    }
}

enum class AgentModeEntryMemoryState {
    NOT_REQUIRED,
    LOADING,
    ABSENT,
    PRESENT,
    DISABLED,
    ERROR
}

data class AgentModeEntryMemoryProbeTarget(
    val scopeType: String,
    val scopeKey: String,
    val domain: String,
    val memoryType: String,
    val status: String
)

fun resolveAgentModeEntryMemoryProbeTarget(
    mode: AgentModeDefinition,
    context: AgentModeEntryContext?
): AgentModeEntryMemoryProbeTarget {
    val probe = requireNotNull(mode.entryPolicy.memoryProbe) {
        "Agent Mode 未声明入口 Memory probe"
    }
    val contextKey = probe.scopeKeyContextPath.removePrefix("payload.")
    val scopeKey = context?.payload
        ?.get(contextKey)
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
        ?.asString
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: throw IllegalArgumentException(
            "Agent Mode 入口上下文缺少 ${probe.scopeKeyContextPath}"
        )
    val target = AgentModeEntryMemoryProbeTarget(
        scopeType = probe.scopeType,
        scopeKey = scopeKey,
        domain = probe.domain,
        memoryType = probe.memoryType,
        status = probe.status
    )
    val memoryPolicy = requireNotNull(mode.memoryPolicy) {
        "Agent Mode 入口 Memory probe 缺少 memory policy"
    }
    require(memoryPolicy.allowedRanges.any { range ->
        range.matches(
            scopeType = target.scopeType,
            scopeKey = target.scopeKey,
            domain = target.domain
        )
    }) {
        "Agent Mode 入口 Memory probe 超出 memory policy"
    }
    return target
}

enum class AgentModeEntryLaunchDecision {
    NONE,
    SHOW_CONFIRMATION,
    START_AUTOMATICALLY,
    MISSING_REQUIRED_CONTEXT
}

fun resolveAgentModeEntryLaunch(
    policy: AgentModeEntryPolicy,
    hasRequiredContext: Boolean,
    alreadyStarted: Boolean,
    hasVisibleMessages: Boolean
): AgentModeEntryLaunchDecision {
    if (alreadyStarted || hasVisibleMessages) {
        return AgentModeEntryLaunchDecision.NONE
    }
    if (policy.startBehavior == AgentModeEntryStartBehavior.NONE) {
        return AgentModeEntryLaunchDecision.NONE
    }
    if (policy.requiresContext && !hasRequiredContext) {
        return AgentModeEntryLaunchDecision.MISSING_REQUIRED_CONTEXT
    }
    return when (policy.startBehavior) {
        AgentModeEntryStartBehavior.NONE -> AgentModeEntryLaunchDecision.NONE
        AgentModeEntryStartBehavior.AUTOMATIC ->
            AgentModeEntryLaunchDecision.START_AUTOMATICALLY
        AgentModeEntryStartBehavior.HOST_CONFIRMATION ->
            AgentModeEntryLaunchDecision.SHOW_CONFIRMATION
    }
}

/**
 * Agent Kernel 可用 Mode 的唯一注册表。
 *
 * P1 只注册 General Mode；后续 System Mode 必须以定义注入，不能在 Kernel
 * 或 AiChatClient 中增加 mode id 业务分支。
 */
object AgentModeRegistry {

    const val GENERAL_ID = "general"
    const val GENERAL_REVISION = "1"
    const val BOOK_SCAN_ID = "book_scan"
    const val BOOK_SCAN_REVISION = "1"
    private const val LEGACY_DEFAULT_ID = "default"
    private val BOOK_SCAN_MCP_CAPABILITIES = listOf(
        "bookshelf.query",
        "bookshelf.read_content",
        "bookshelf.cache_status",
        "ai.memory"
    )

    val general = AgentModeDefinition(
        id = GENERAL_ID,
        revision = GENERAL_REVISION,
        kind = AgentModeKind.GENERAL,
        allowsUserSkills = true,
        allowsManualMcpCapabilities = true
    )

    val bookScan = AgentModeDefinition(
        id = BOOK_SCAN_ID,
        revision = BOOK_SCAN_REVISION,
        kind = AgentModeKind.SYSTEM,
        allowsUserSkills = false,
        allowsManualMcpCapabilities = false,
        systemWorkflowId = AiSkillRegistry.SKILL_BOOK_SCAN,
        systemSkillIds = listOf(
            AiSkillRegistry.SKILL_BOOK_SCAN,
            AiSkillRegistry.SKILL_BOOK_SCAN_FACTS,
            AiSkillRegistry.SKILL_BOOK_SCAN_REPORT
        ),
        fixedMcpCapabilityIds = BOOK_SCAN_MCP_CAPABILITIES,
        memoryPolicy = AgentModeMemoryPolicy(
            allowedRanges = listOf(
                McpMemoryAccessRange(scopeType = "book", domain = "book_scan"),
                McpMemoryAccessRange(
                    scopeType = "global",
                    scopeKey = "default",
                    domain = "reader_preference",
                    access = McpMemoryAccess.READ_ONLY
                )
            )
        ),
        entryPolicy = AgentModeEntryPolicy(
            requiresContext = true,
            startBehavior = AgentModeEntryStartBehavior.HOST_CONFIRMATION,
            memoryProbe = AgentModeEntryMemoryProbe(
                scopeType = "book",
                scopeKeyContextPath = "payload.work_key",
                domain = "book_scan",
                memoryType = "",
                loading = AgentModeEntryNotice(
                    title = "AI 扫书：{{context.payload.book_name}}",
                    description = "正在检查这本书已有的本地扫书档案……"
                ),
                absent = AgentModeEntryConfirmation(
                    title = "AI 扫书：{{context.payload.book_name}}",
                    description = "尚未找到这本书的扫书档案。点击后将读取开头与结尾样本进行快速定位。\n\n" +
                        "开始前可先在下方设置模型和思考深度。",
                    confirmLabel = "开始快速定位"
                ),
                present = AgentModeEntryConfirmation(
                    title = "AI 扫书：{{context.payload.book_name}}",
                    description = "已找到这本书的本地扫书档案。可以直接查看当前结论，也可以先看覆盖缺口再继续排雷；已有有效事实不会重复读取正文。\n\n" +
                        "开始前可先在下方设置模型和思考深度。",
                    confirmLabel = "选择操作",
                    actions = listOf(
                        AgentModeEntryAction(
                            label = "查看当前结论",
                            value = "view_current_report",
                            description = "核对已有档案并生成当前结论。"
                        ),
                        AgentModeEntryAction(
                            label = "继续排雷",
                            value = "continue_scan",
                            description = "先查看覆盖缺口，再选择扫描范围。"
                        )
                    )
                ),
                disabled = AgentModeEntryNotice(
                    title = "暂时无法检查扫书档案",
                    description = "AI 记忆系统未开启。请先在 AI 设置中开启记忆，再重新进入扫书模式。"
                ),
                error = AgentModeEntryNotice(
                    title = "扫书档案检查失败",
                    description = "读取本地扫书档案时发生错误；当前不会把它当成首次扫描。请重新进入后再试。"
                )
            )
        ),
        runtimeHooks = AgentHookRegistry.validateBindings(
            bindings = listOf(memoryFlushBinding()),
            allowedToolNames = McpInternalToolCatalog.resolveToolNames(BOOK_SCAN_MCP_CAPABILITIES)
        )
    )

    private val definitions = listOf(general, bookScan).associateBy(AgentModeDefinition::id)

    fun all(): List<AgentModeDefinition> = definitions.values.toList()

    fun resolve(identity: AgentModeIdentity): AgentModeDefinition? {
        return definitions[identity.id]?.takeIf { definition ->
            definition.revision == identity.revision
        }
    }

    fun normalizePersistedIdentity(
        modeId: String?,
        modeRevision: String?
    ): AgentModeIdentity {
        val normalizedId = modeId?.trim().orEmpty().let { id ->
            if (id.isBlank() || id == LEGACY_DEFAULT_ID) GENERAL_ID else id
        }
        val definition = definitions[normalizedId]
        val normalizedRevision = modeRevision?.trim().orEmpty().ifBlank {
            definition?.revision.orEmpty()
        }
        return AgentModeIdentity(
            id = normalizedId,
            revision = normalizedRevision
        )
    }

    private fun memoryFlushBinding(): AgentHookBinding {
        return AgentHookBinding(
            id = "core.memory_flush",
            version = 1,
            config = JsonObject().apply {
                add("watch_tools", JsonArray().apply {
                    add("bookshelf_chapter_content_get")
                    add("bookshelf_text_window_get")
                    add("bookshelf_chapter_snippets_get")
                })
                add("flush_tools", JsonArray().apply {
                    add("agent_memory_upsert")
                    add("agent_memory_batch_upsert")
                })
                addProperty("max_pending", 2)
                add("block_before", JsonArray().apply {
                    add("compaction")
                    add("turn_finalize")
                })
            }
        )
    }

}
