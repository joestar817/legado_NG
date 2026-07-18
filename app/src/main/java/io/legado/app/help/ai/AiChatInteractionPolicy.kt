package io.legado.app.help.ai

import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import io.legado.app.utils.GSON
import java.util.concurrent.ConcurrentHashMap

data class AiChatInteractionPolicyContract(
    @SerializedName("schema_version")
    val schemaVersion: Int,
    @SerializedName("required_policy_types")
    val requiredPolicyTypes: Set<String> = emptySet(),
    @SerializedName("interactions")
    val interactions: Map<String, AiChatInteractionPolicyRule> = emptyMap()
)

data class AiChatInteractionPolicyRule(
    @SerializedName("type")
    val type: String,
    @SerializedName("version")
    val version: Int,
    @SerializedName("title")
    val title: String,
    @SerializedName("description")
    val description: String,
    @SerializedName("options")
    val options: List<AiChatInteractionOption> = emptyList(),
    @SerializedName("allowed_items")
    val allowedItems: Map<String, AiChatInteractionPolicyItem> = emptyMap(),
    @SerializedName("submit")
    val submit: AiChatInteractionSubmit? = null,
    @SerializedName("cancel")
    val cancel: AiChatInteractionSubmit? = null,
    @SerializedName("skip")
    val skip: AiChatInteractionSubmit? = null,
    @SerializedName("memory_patch")
    val memoryPatch: AiChatInteractionMemoryPatch? = null
)

data class AiChatInteractionPolicyItem(
    @SerializedName("label")
    val label: String,
    @SerializedName("description")
    val description: String
)

data class AiChatInteractionPolicyResult(
    val accepted: List<AiChatInteraction>,
    val rejected: List<AiChatInteraction>
)

/**
 * Generic host enforcement for interaction contracts declared by a read-only System Skill.
 * Business ids live only in the package JSON; Kotlin only validates ids and display mappings.
 */
object AiChatInteractionPolicyRegistry {

    private const val POLICY_PATH = "interaction-policy.json"
    private val cache = ConcurrentHashMap<String, AiChatInteractionPolicyContract>()

    fun resolve(mode: AgentModeDefinition): AiChatInteractionPolicyContract? {
        val skillId = mode.systemWorkflowId ?: return null
        val skillPackage = AiSkillPackageRegistry.systemPackage(skillId) ?: return null
        val cacheKey = "$skillId@${skillPackage.contentHash}"
        cache[cacheKey]?.let { return it }
        val content = skillPackage.files[POLICY_PATH]?.toString(Charsets.UTF_8) ?: return null
        return parse(content).also { cache[cacheKey] = it }
    }

    fun filter(
        contract: AiChatInteractionPolicyContract?,
        interactions: List<AiChatInteraction>
    ): AiChatInteractionPolicyResult {
        if (contract == null) {
            val (rejected, accepted) = interactions.partition { interaction ->
                interaction.type == AiChatInteractionType.POLICY_REF
            }
            return AiChatInteractionPolicyResult(accepted, rejected)
        }
        val accepted = mutableListOf<AiChatInteraction>()
        val rejected = mutableListOf<AiChatInteraction>()
        interactions.forEach { interaction ->
            val resolved = resolveTrusted(contract, interaction)
            if (resolved == null) {
                rejected += interaction
            } else {
                accepted += resolved
            }
        }
        return AiChatInteractionPolicyResult(accepted, rejected)
    }

    fun requireAllowed(
        contract: AiChatInteractionPolicyContract?,
        interaction: AiChatInteraction
    ) {
        if (contract == null) return
        val violations = violations(contract, interaction)
        val trusted = resolveTrusted(contract, interaction)
        require(violations.isEmpty() && trusted == interaction) {
            val details = if (violations.isNotEmpty()) {
                violations.joinToString("；")
            } else {
                "交互尚未使用宿主可信模板归一化"
            }
            "交互不符合当前 System Workflow 策略：$details"
        }
    }

    /**
     * Treat the model payload as an intent: declared id/type/version and dynamic item ids.
     * All UI copy, controls, options and durable-write templates come from the trusted package.
     */
    internal fun resolveTrusted(
        contract: AiChatInteractionPolicyContract,
        interaction: AiChatInteraction
    ): AiChatInteraction? {
        if (violations(contract, interaction).isNotEmpty()) return null
        val rule = contract.interactions[interaction.id] ?: return interaction
        val trustedItems = interaction.items.map { item ->
            val trusted = rule.allowedItems[item.id] ?: return null
            AiChatInteractionItem(
                id = item.id,
                label = trusted.label,
                description = trusted.description
            )
        }
        val dynamicChoiceOptions = if (
            rule.type == "multi_choice" && rule.allowedItems.isNotEmpty()
        ) {
            trustedItems.map { item ->
                AiChatInteractionOption(
                    label = item.label,
                    value = item.id,
                    description = item.description
                )
            }
        } else {
            rule.options
        }
        return interaction.copy(
            version = rule.version,
            type = rule.type.toInteractionType(),
            title = rule.title,
            description = rule.description,
            options = dynamicChoiceOptions,
            items = if (rule.type == "multi_choice") emptyList() else trustedItems,
            submit = rule.submit,
            cancel = rule.cancel,
            skip = rule.skip,
            memoryPatch = rule.memoryPatch
        )
    }

    internal fun parse(content: String): AiChatInteractionPolicyContract {
        val root = JsonParser.parseString(content).takeIf { it.isJsonObject }
            ?: throw IllegalArgumentException("interaction policy 必须是 JSON object")
        val contract = GSON.fromJson(root, AiChatInteractionPolicyContract::class.java)
        require(contract.schemaVersion == 2) { "不支持的 interaction policy schema" }
        require(contract.requiredPolicyTypes.all(ALLOWED_TYPE_NAMES::contains)) {
            "interaction policy 包含未知 required type"
        }
        contract.interactions.forEach { (id, rule) ->
            require(id.matches(ID_REGEX)) { "interaction policy id 不合法：$id" }
            require(rule.type in ALLOWED_TYPE_NAMES) { "interaction policy type 不合法：${rule.type}" }
            require(rule.version > 0) { "interaction policy version 不合法：$id" }
            require(rule.title.isNotBlank() && rule.description.isNotBlank()) {
                "interaction policy 标题或说明为空：$id"
            }
            require(rule.allowedItems.keys.all { it.matches(ID_REGEX) }) {
                "interaction policy item id 不合法：$id"
            }
            require(rule.allowedItems.values.all { it.label.isNotBlank() }) {
                "interaction policy item 文案不完整：$id"
            }
            require(
                rule.options.all { it.value.isNotBlank() && it.label.isNotBlank() } &&
                    rule.options.map { it.value }.distinct().size == rule.options.size
            ) {
                "interaction policy option 不合法：$id"
            }
            validateControl(id, "submit", rule.submit)
            validateControl(id, "cancel", rule.cancel)
            validateControl(id, "skip", rule.skip)
        }
        return contract
    }

    internal fun violations(
        contract: AiChatInteractionPolicyContract,
        interaction: AiChatInteraction
    ): List<String> {
        val typeName = interaction.type.policyName()
        val rule = contract.interactions[interaction.id]
        if (rule == null) {
            return if (
                interaction.type == AiChatInteractionType.POLICY_REF ||
                typeName in contract.requiredPolicyTypes
            ) {
                listOf("$typeName 必须使用已声明的 interaction id")
            } else {
                emptyList()
            }
        }
        val violations = mutableListOf<String>()
        if (interaction.type != AiChatInteractionType.POLICY_REF) {
            if (rule.type != typeName) {
                violations += "type 应为 ${rule.type}"
            }
            if (rule.version != interaction.version) {
                violations += "version 应为 ${rule.version}"
            }
        } else if (interaction.version != 1) {
            violations += "policy_ref version 应为 1"
        }
        val invalidItems = interaction.items.filter { item ->
            item.id !in rule.allowedItems
        }
        if (invalidItems.isNotEmpty()) {
            violations += "items 未获策略允许：${invalidItems.joinToString { it.id }}"
        }
        if (interaction.items.map { it.id }.distinct().size != interaction.items.size) {
            violations += "items 包含重复 id"
        }
        if (rule.allowedItems.isNotEmpty() && interaction.items.isEmpty()) {
            violations += "当前交互至少需要一个 item id"
        }
        if (rule.allowedItems.isEmpty() && interaction.items.isNotEmpty()) {
            violations += "当前交互不接受动态 items"
        }
        return violations
    }

    private fun validateControl(
        interactionId: String,
        name: String,
        control: AiChatInteractionSubmit?
    ) {
        if (control == null) return
        require(control.label.isNotBlank()) {
            "interaction policy $name label 为空：$interactionId"
        }
    }

    private fun AiChatInteractionType.policyName(): String = when (this) {
        AiChatInteractionType.POLICY_REF -> "policy_ref"
        AiChatInteractionType.ACTIONS -> "actions"
        AiChatInteractionType.SINGLE_CHOICE -> "single_choice"
        AiChatInteractionType.MULTI_CHOICE -> "multi_choice"
        AiChatInteractionType.MULTI_TAG_STANCE -> "multi_tag_stance"
        AiChatInteractionType.CONFIRM -> "confirm"
    }

    private fun String.toInteractionType(): AiChatInteractionType = when (this) {
        "actions" -> AiChatInteractionType.ACTIONS
        "single_choice" -> AiChatInteractionType.SINGLE_CHOICE
        "multi_choice" -> AiChatInteractionType.MULTI_CHOICE
        "multi_tag_stance" -> AiChatInteractionType.MULTI_TAG_STANCE
        "confirm" -> AiChatInteractionType.CONFIRM
        else -> error("unsupported trusted interaction type: $this")
    }

    private val ALLOWED_TYPE_NAMES = setOf(
        "actions",
        "single_choice",
        "multi_choice",
        "multi_tag_stance",
        "confirm"
    )
    private val ID_REGEX = Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$")
}
