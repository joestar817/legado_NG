package io.legado.app.web.mcp

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.legado.app.data.appDb
import io.legado.app.data.entities.DictRule
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.data.entities.TxtTocRule
import io.legado.app.utils.GSON

object SettingsMcpTools {

    private const val DEFAULT_LIST_LIMIT = 50
    private const val MAX_LIST_LIMIT = 200

    fun tools(): List<Map<String, Any>> {
        return listOf(
            tool(
                name = "settings_rule_stats_get",
                description = "Return lightweight counts for TXT TOC, replacement/purify, and dictionary rules.",
                properties = emptyMap()
            ),
            tool(
                name = "settings_txt_toc_rule_list",
                description = "List TXT local-book table-of-contents rules. Default returns compact summaries; pass include_detail=true for full rule fields on the returned page.",
                properties = listProperties(includeDetail = true)
            ),
            tool(
                name = "settings_txt_toc_rule_get",
                description = "Get one TXT table-of-contents rule by id.",
                properties = mapOf("id" to numberSchema("TxtTocRule.id")),
                required = listOf("id")
            ),
            tool(
                name = "settings_txt_toc_rule_upsert",
                description = "Create or update one TXT table-of-contents rule.",
                properties = mapOf("rule" to mapOf("type" to "object", "description" to "TxtTocRule fields")),
                required = listOf("rule")
            ),
            tool(
                name = "settings_txt_toc_rule_delete",
                description = "Delete TXT table-of-contents rules by id.",
                properties = mapOf("ids" to arraySchema("TxtTocRule.id list")),
                required = listOf("ids")
            ),
            tool(
                name = "settings_txt_toc_rule_set_enabled",
                description = "Enable or disable TXT table-of-contents rules by id.",
                properties = mapOf(
                    "ids" to arraySchema("TxtTocRule.id list"),
                    "enabled" to booleanSchema("Default true")
                ),
                required = listOf("ids")
            ),
            tool(
                name = "settings_replace_rule_list",
                description = "List global replacement/purify rules. Default returns compact summaries; pass include_detail=true for full pattern/replacement fields on the returned page.",
                properties = listProperties(includeDetail = true) + mapOf(
                    "group" to stringSchema("Optional group keyword"),
                    "scope" to stringSchema("Optional scope keyword")
                )
            ),
            tool(
                name = "settings_replace_rule_get",
                description = "Get one replacement/purify rule by id.",
                properties = mapOf("id" to numberSchema("ReplaceRule.id")),
                required = listOf("id")
            ),
            tool(
                name = "settings_replace_rule_upsert",
                description = "Create or update one replacement/purify rule.",
                properties = mapOf("rule" to mapOf("type" to "object", "description" to "ReplaceRule fields")),
                required = listOf("rule")
            ),
            tool(
                name = "settings_replace_rule_delete",
                description = "Delete replacement/purify rules by id.",
                properties = mapOf("ids" to arraySchema("ReplaceRule.id list")),
                required = listOf("ids")
            ),
            tool(
                name = "settings_replace_rule_set_enabled",
                description = "Enable or disable replacement/purify rules by id.",
                properties = mapOf(
                    "ids" to arraySchema("ReplaceRule.id list"),
                    "enabled" to booleanSchema("Default true")
                ),
                required = listOf("ids")
            ),
            tool(
                name = "settings_dict_rule_list",
                description = "List dictionary lookup rules. Default returns compact summaries; pass include_detail=true for full URL/show rules on the returned page.",
                properties = listProperties(includeDetail = true)
            ),
            tool(
                name = "settings_dict_rule_get",
                description = "Get one dictionary rule by name.",
                properties = mapOf("name" to stringSchema("DictRule.name")),
                required = listOf("name")
            ),
            tool(
                name = "settings_dict_rule_upsert",
                description = "Create or update one dictionary rule.",
                properties = mapOf("rule" to mapOf("type" to "object", "description" to "DictRule fields")),
                required = listOf("rule")
            ),
            tool(
                name = "settings_dict_rule_delete",
                description = "Delete dictionary rules by name.",
                properties = mapOf("names" to arraySchema("DictRule.name list")),
                required = listOf("names")
            ),
            tool(
                name = "settings_dict_rule_set_enabled",
                description = "Enable or disable dictionary rules by name.",
                properties = mapOf(
                    "names" to arraySchema("DictRule.name list"),
                    "enabled" to booleanSchema("Default true")
                ),
                required = listOf("names")
            )
        )
    }

    fun resources(): List<Map<String, String>> {
        return listOf(
            mapOf(
                "uri" to "legado://schema/settings",
                "name" to "settings-mcp-schema",
                "description" to "Settings MCP module interface summary",
                "mimeType" to "application/json"
            )
        )
    }

    fun readResource(uri: String): String? {
        if (uri != "legado://schema/settings") return null
        return GSON.toJson(
            mapOf(
                "module" to "settings",
                "submodules" to listOf("txt_toc_rules", "replace_rules", "dict_rules"),
                "notes" to listOf(
                    "TXT TOC rules are used for local TXT chapter detection.",
                    "Replacement rules are global purify rules; book-scoped draft creation remains available under bookshelf tools.",
                    "Dictionary rules define lookup URLs and display extraction rules; this module manages rules but does not run dictionary lookup."
                ),
                "tools" to tools().mapNotNull { it["name"] }
            )
        )
    }

    fun call(name: String, arguments: JsonObject): Map<String, Any?>? {
        return when (name) {
            "settings_rule_stats_get" -> getRuleStats()
            "settings_txt_toc_rule_list" -> listTxtTocRules(arguments)
            "settings_txt_toc_rule_get" -> getTxtTocRule(arguments)
            "settings_txt_toc_rule_upsert" -> upsertTxtTocRule(arguments)
            "settings_txt_toc_rule_delete" -> deleteTxtTocRules(arguments)
            "settings_txt_toc_rule_set_enabled" -> setTxtTocRulesEnabled(arguments)
            "settings_replace_rule_list" -> listReplaceRules(arguments)
            "settings_replace_rule_get" -> getReplaceRule(arguments)
            "settings_replace_rule_upsert" -> upsertReplaceRule(arguments)
            "settings_replace_rule_delete" -> deleteReplaceRules(arguments)
            "settings_replace_rule_set_enabled" -> setReplaceRulesEnabled(arguments)
            "settings_dict_rule_list" -> listDictRules(arguments)
            "settings_dict_rule_get" -> getDictRule(arguments)
            "settings_dict_rule_upsert" -> upsertDictRule(arguments)
            "settings_dict_rule_delete" -> deleteDictRules(arguments)
            "settings_dict_rule_set_enabled" -> setDictRulesEnabled(arguments)
            else -> null
        }
    }

    private fun getRuleStats(): Map<String, Any?> {
        val tocRules = appDb.txtTocRuleDao.all
        val replaceRules = appDb.replaceRuleDao.all
        val dictRules = appDb.dictRuleDao.all
        val validReplaceCount = replaceRules.count { it.isValid() }
        return toolResult(
            true,
            "native://settings/ruleStats",
            mapOf(
                "txt_toc_rules" to mapOf(
                    "total" to tocRules.size,
                    "enabled" to tocRules.count { it.enable },
                    "disabled" to tocRules.count { !it.enable },
                    "default" to tocRules.count { it.id < 0 },
                    "custom" to tocRules.count { it.id >= 0 },
                    "with_replacement" to tocRules.count { it.replacement.isNotBlank() }
                ),
                "replace_rules" to mapOf(
                    "total" to replaceRules.size,
                    "enabled" to replaceRules.count { it.isEnabled },
                    "disabled" to replaceRules.count { !it.isEnabled },
                    "regex" to replaceRules.count { it.isRegex },
                    "plain_text" to replaceRules.count { !it.isRegex },
                    "valid" to validReplaceCount,
                    "invalid" to (replaceRules.size - validReplaceCount),
                    "scope_title" to replaceRules.count { it.scopeTitle },
                    "scope_content" to replaceRules.count { it.scopeContent },
                    "scoped" to replaceRules.count { !it.scope.isNullOrBlank() },
                    "exclude_scoped" to replaceRules.count { !it.excludeScope.isNullOrBlank() },
                    "group_counts" to ruleGroupCounts(replaceRules.map { it.group })
                ),
                "dict_rules" to mapOf(
                    "total" to dictRules.size,
                    "enabled" to dictRules.count { it.enabled },
                    "disabled" to dictRules.count { !it.enabled },
                    "with_show_rule" to dictRules.count { it.showRule.isNotBlank() }
                )
            )
        )
    }

    private fun listTxtTocRules(arguments: JsonObject): Map<String, Any?> {
        val enabled = arguments.get("enabled").asBooleanOrNull()
        val keyword = arguments.get("keyword").asStringOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        val includeDetail = arguments.get("include_detail").asBooleanOrNull() ?: false
        val (offset, limit) = page(arguments)
        val filtered = appDb.txtTocRuleDao.all
            .asSequence()
            .filter { enabled == null || it.enable == enabled }
            .filter { keyword == null || it.matchesKeyword(keyword) }
            .toList()
        return pageResult(
            upstreamEndpoint = "native://settings/txtTocRules",
            itemsKey = "rules",
            items = filtered.drop(offset).take(limit).map { rule ->
                if (includeDetail) rule.toMcpMap() else rule.toMcpSummary()
            },
            total = filtered.size,
            offset = offset,
            limit = limit,
            compact = !includeDetail
        )
    }

    private fun getTxtTocRule(arguments: JsonObject): Map<String, Any?> {
        val id = arguments.get("id").asLongOrNull() ?: throw IllegalArgumentException("id is required")
        val rule = appDb.txtTocRuleDao.get(id)
            ?: return notFound("native://settings/txtTocRule", "未找到目录规则: $id")
        return toolResult(true, "native://settings/txtTocRule", rule.toMcpMap())
    }

    private fun upsertTxtTocRule(arguments: JsonObject): Map<String, Any?> {
        val input = arguments.get("rule")?.takeIf { it.isJsonObject }?.asJsonObject
            ?: throw IllegalArgumentException("rule is required")
        val existing = input.get("id").asLongOrNull()?.let { appDb.txtTocRuleDao.get(it) }
        val now = System.currentTimeMillis()
        val rule = TxtTocRule(
            id = input.get("id").asLongOrNull() ?: now,
            name = input.get("name").asStringOrNull()?.trim().orEmpty(),
            rule = input.get("rule").asStringOrNull().orEmpty(),
            replacement = input.get("replacement").asStringOrNull().orEmpty(),
            example = input.get("example").asStringOrNull(),
            serialNumber = input.get("serial_number").asIntOrNull()
                ?: input.get("serialNumber").asIntOrNull()
                ?: existing?.serialNumber
                ?: (appDb.txtTocRuleDao.maxOrder + 1),
            enable = input.get("enabled").asBooleanOrNull()
                ?: input.get("enable").asBooleanOrNull()
                ?: existing?.enable
                ?: true
        )
        if (rule.name.isBlank()) throw IllegalArgumentException("rule.name is required")
        if (rule.rule.isBlank()) throw IllegalArgumentException("rule.rule is required")
        validateRegex(rule.rule, "rule.rule")
        appDb.txtTocRuleDao.insert(rule)
        return toolResult(
            true,
            "native://settings/txtTocRuleUpsert",
            mapOf("rule" to appDb.txtTocRuleDao.get(rule.id)?.toMcpMap(), "created" to (existing == null))
        )
    }

    private fun deleteTxtTocRules(arguments: JsonObject): Map<String, Any?> {
        val ids = arguments.get("ids").asLongList()
        val rules = ids.mapNotNull { appDb.txtTocRuleDao.get(it) }
        if (rules.isNotEmpty()) {
            appDb.txtTocRuleDao.delete(*rules.toTypedArray())
        }
        return toolResult(
            true,
            "native://settings/txtTocRuleDelete",
            mapOf("requested" to ids.size, "deleted" to rules.size, "ids" to rules.map { it.id })
        )
    }

    private fun setTxtTocRulesEnabled(arguments: JsonObject): Map<String, Any?> {
        val ids = arguments.get("ids").asLongList()
        val enabled = arguments.get("enabled").asBooleanOrNull() ?: true
        val rules = ids.mapNotNull { appDb.txtTocRuleDao.get(it) }
        if (rules.isNotEmpty()) {
            appDb.txtTocRuleDao.update(*rules.map { it.copy(enable = enabled) }.toTypedArray())
        }
        return toolResult(
            true,
            "native://settings/txtTocRuleSetEnabled",
            mapOf("requested" to ids.size, "updated" to rules.size, "enabled" to enabled, "ids" to rules.map { it.id })
        )
    }

    private fun listReplaceRules(arguments: JsonObject): Map<String, Any?> {
        val enabled = arguments.get("enabled").asBooleanOrNull()
        val keyword = arguments.get("keyword").asStringOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        val group = arguments.get("group").asStringOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        val scope = arguments.get("scope").asStringOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        val includeDetail = arguments.get("include_detail").asBooleanOrNull() ?: false
        val (offset, limit) = page(arguments)
        val filtered = appDb.replaceRuleDao.all
            .asSequence()
            .filter { enabled == null || it.isEnabled == enabled }
            .filter { keyword == null || it.matchesKeyword(keyword) }
            .filter { group == null || it.group.orEmpty().contains(group, ignoreCase = true) }
            .filter {
                scope == null ||
                        it.scope.orEmpty().contains(scope, ignoreCase = true) ||
                        it.excludeScope.orEmpty().contains(scope, ignoreCase = true)
            }
            .toList()
        return pageResult(
            upstreamEndpoint = "native://settings/replaceRules",
            itemsKey = "rules",
            items = filtered.drop(offset).take(limit).map { rule ->
                if (includeDetail) rule.toMcpMap() else rule.toMcpSummary()
            },
            total = filtered.size,
            offset = offset,
            limit = limit,
            compact = !includeDetail
        )
    }

    private fun getReplaceRule(arguments: JsonObject): Map<String, Any?> {
        val id = arguments.get("id").asLongOrNull() ?: throw IllegalArgumentException("id is required")
        val rule = appDb.replaceRuleDao.findById(id)
            ?: return notFound("native://settings/replaceRule", "未找到净化规则: $id")
        return toolResult(true, "native://settings/replaceRule", rule.toMcpMap())
    }

    private fun upsertReplaceRule(arguments: JsonObject): Map<String, Any?> {
        val input = arguments.get("rule")?.takeIf { it.isJsonObject }?.asJsonObject
            ?: throw IllegalArgumentException("rule is required")
        val existing = input.get("id").asLongOrNull()?.let { appDb.replaceRuleDao.findById(it) }
        val rule = existing ?: ReplaceRule()
        input.get("id").asLongOrNull()?.let { rule.id = it }
        rule.name = input.get("name").asStringOrNull()?.trim() ?: rule.name
        rule.group = input.get("group").asStringOrNull() ?: rule.group
        rule.pattern = input.get("pattern").asStringOrNull() ?: rule.pattern
        rule.replacement = input.get("replacement").asStringOrNull() ?: rule.replacement
        rule.scope = input.get("scope").asStringOrNull() ?: rule.scope
        rule.scopeTitle = input.get("scope_title").asBooleanOrNull()
            ?: input.get("scopeTitle").asBooleanOrNull()
            ?: rule.scopeTitle
        rule.scopeContent = input.get("scope_content").asBooleanOrNull()
            ?: input.get("scopeContent").asBooleanOrNull()
            ?: rule.scopeContent
        rule.excludeScope = input.get("exclude_scope").asStringOrNull()
            ?: input.get("excludeScope").asStringOrNull()
            ?: rule.excludeScope
        rule.isEnabled = input.get("enabled").asBooleanOrNull()
            ?: input.get("isEnabled").asBooleanOrNull()
            ?: rule.isEnabled
        rule.isRegex = input.get("is_regex").asBooleanOrNull()
            ?: input.get("isRegex").asBooleanOrNull()
            ?: rule.isRegex
        rule.timeoutMillisecond = input.get("timeout_millisecond").asLongOrNull()
            ?: input.get("timeoutMillisecond").asLongOrNull()
            ?: rule.timeoutMillisecond
        rule.order = input.get("order").asIntOrNull()
            ?: input.get("sort_order").asIntOrNull()
            ?: rule.order.takeIf { it != Int.MIN_VALUE }
                    ?: (appDb.replaceRuleDao.maxOrder + 1)
        if (rule.name.isBlank()) throw IllegalArgumentException("rule.name is required")
        rule.checkValid()
        val ids = appDb.replaceRuleDao.insert(rule)
        val id = ids.firstOrNull()?.takeIf { it > 0 } ?: rule.id
        return toolResult(
            true,
            "native://settings/replaceRuleUpsert",
            mapOf("rule" to appDb.replaceRuleDao.findById(id)?.toMcpMap(), "created" to (existing == null))
        )
    }

    private fun deleteReplaceRules(arguments: JsonObject): Map<String, Any?> {
        val ids = arguments.get("ids").asLongList()
        val rules = appDb.replaceRuleDao.findByIds(*ids.toLongArray())
        if (rules.isNotEmpty()) {
            appDb.replaceRuleDao.delete(*rules.toTypedArray())
        }
        return toolResult(
            true,
            "native://settings/replaceRuleDelete",
            mapOf("requested" to ids.size, "deleted" to rules.size, "ids" to rules.map { it.id })
        )
    }

    private fun setReplaceRulesEnabled(arguments: JsonObject): Map<String, Any?> {
        val ids = arguments.get("ids").asLongList()
        val enabled = arguments.get("enabled").asBooleanOrNull() ?: true
        val rules = appDb.replaceRuleDao.findByIds(*ids.toLongArray())
        if (rules.isNotEmpty()) {
            rules.forEach { it.isEnabled = enabled }
            appDb.replaceRuleDao.update(*rules.toTypedArray())
        }
        return toolResult(
            true,
            "native://settings/replaceRuleSetEnabled",
            mapOf("requested" to ids.size, "updated" to rules.size, "enabled" to enabled, "ids" to rules.map { it.id })
        )
    }

    private fun listDictRules(arguments: JsonObject): Map<String, Any?> {
        val enabled = arguments.get("enabled").asBooleanOrNull()
        val keyword = arguments.get("keyword").asStringOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        val includeDetail = arguments.get("include_detail").asBooleanOrNull() ?: false
        val (offset, limit) = page(arguments)
        val filtered = appDb.dictRuleDao.all
            .asSequence()
            .filter { enabled == null || it.enabled == enabled }
            .filter { keyword == null || it.matchesKeyword(keyword) }
            .toList()
        return pageResult(
            upstreamEndpoint = "native://settings/dictRules",
            itemsKey = "rules",
            items = filtered.drop(offset).take(limit).map { rule ->
                if (includeDetail) rule.toMcpMap() else rule.toMcpSummary()
            },
            total = filtered.size,
            offset = offset,
            limit = limit,
            compact = !includeDetail
        )
    }

    private fun getDictRule(arguments: JsonObject): Map<String, Any?> {
        val name = arguments.get("name").asStringOrNull()?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("name is required")
        val rule = appDb.dictRuleDao.getByName(name)
            ?: return notFound("native://settings/dictRule", "未找到字典规则: $name")
        return toolResult(true, "native://settings/dictRule", rule.toMcpMap())
    }

    private fun upsertDictRule(arguments: JsonObject): Map<String, Any?> {
        val input = arguments.get("rule")?.takeIf { it.isJsonObject }?.asJsonObject
            ?: throw IllegalArgumentException("rule is required")
        val name = input.get("name").asStringOrNull()?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("rule.name is required")
        val existing = appDb.dictRuleDao.getByName(name)
        val rule = DictRule(
            name = name,
            urlRule = input.get("url_rule").asStringOrNull()
                ?: input.get("urlRule").asStringOrNull()
                ?: existing?.urlRule
                ?: "",
            showRule = input.get("show_rule").asStringOrNull()
                ?: input.get("showRule").asStringOrNull()
                ?: existing?.showRule
                ?: "",
            enabled = input.get("enabled").asBooleanOrNull() ?: existing?.enabled ?: true,
            sortNumber = input.get("sort_number").asIntOrNull()
                ?: input.get("sortNumber").asIntOrNull()
                ?: existing?.sortNumber
                ?: nextDictSortNumber()
        )
        if (rule.urlRule.isBlank()) throw IllegalArgumentException("rule.url_rule is required")
        appDb.dictRuleDao.insert(rule)
        return toolResult(
            true,
            "native://settings/dictRuleUpsert",
            mapOf("rule" to appDb.dictRuleDao.getByName(rule.name)?.toMcpMap(), "created" to (existing == null))
        )
    }

    private fun deleteDictRules(arguments: JsonObject): Map<String, Any?> {
        val names = arguments.get("names").asStringList()
        val rules = names.mapNotNull { appDb.dictRuleDao.getByName(it) }
        if (rules.isNotEmpty()) {
            appDb.dictRuleDao.delete(*rules.toTypedArray())
        }
        return toolResult(
            true,
            "native://settings/dictRuleDelete",
            mapOf("requested" to names.size, "deleted" to rules.size, "names" to rules.map { it.name })
        )
    }

    private fun setDictRulesEnabled(arguments: JsonObject): Map<String, Any?> {
        val names = arguments.get("names").asStringList()
        val enabled = arguments.get("enabled").asBooleanOrNull() ?: true
        val rules = names.mapNotNull { appDb.dictRuleDao.getByName(it) }
        if (rules.isNotEmpty()) {
            appDb.dictRuleDao.update(*rules.map { it.copy(enabled = enabled) }.toTypedArray())
        }
        return toolResult(
            true,
            "native://settings/dictRuleSetEnabled",
            mapOf("requested" to names.size, "updated" to rules.size, "enabled" to enabled, "names" to rules.map { it.name })
        )
    }

    private fun page(arguments: JsonObject): Pair<Int, Int> {
        val offset = (arguments.get("offset").asIntOrNull() ?: 0).coerceAtLeast(0)
        val limit = (arguments.get("limit").asIntOrNull() ?: DEFAULT_LIST_LIMIT)
            .coerceIn(1, MAX_LIST_LIMIT)
        return offset to limit
    }

    private fun ruleGroupCounts(groups: List<String?>): List<Map<String, Any>> {
        val counts = linkedMapOf<String, Int>()
        groups.forEach { value ->
            val names = splitRuleGroupNames(value).ifEmpty { listOf("未分组") }
            names.forEach { name ->
                counts[name] = (counts[name] ?: 0) + 1
            }
        }
        return counts
            .map { (group, count) -> mapOf("group" to group, "count" to count) }
            .sortedWith(compareByDescending<Map<String, Any>> { it["count"] as Int }.thenBy { it["group"] as String })
    }

    private fun splitRuleGroupNames(value: String?): List<String> {
        return value.orEmpty()
            .split(',', '，')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun pageResult(
        upstreamEndpoint: String,
        itemsKey: String,
        items: List<Map<String, Any?>>,
        total: Int,
        offset: Int,
        limit: Int,
        compact: Boolean? = null
    ): Map<String, Any?> {
        val data = linkedMapOf<String, Any?>(
            itemsKey to items,
            "offset" to offset,
            "limit" to limit,
            "total" to total,
            "has_more" to (offset + items.size < total)
        )
        compact?.let { data["compact"] = it }
        return toolResult(
            true,
            upstreamEndpoint,
            data
        )
    }

    private fun TxtTocRule.matchesKeyword(keyword: String): Boolean {
        return sequenceOf(name, rule, replacement, example).filterNotNull()
            .any { it.contains(keyword, ignoreCase = true) }
    }

    private fun ReplaceRule.matchesKeyword(keyword: String): Boolean {
        return sequenceOf(name, group, pattern, replacement, scope, excludeScope).filterNotNull()
            .any { it.contains(keyword, ignoreCase = true) }
    }

    private fun DictRule.matchesKeyword(keyword: String): Boolean {
        return sequenceOf(name, urlRule, showRule).any { it.contains(keyword, ignoreCase = true) }
    }

    private fun TxtTocRule.toMcpMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "name" to name,
            "rule" to rule,
            "replacement" to replacement,
            "example" to example,
            "serial_number" to serialNumber,
            "enabled" to enable,
            "is_default" to (id < 0)
        )
    }

    private fun TxtTocRule.toMcpSummary(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "name" to name,
            "rule_preview" to rule.takePreview(),
            "replacement_preview" to replacement.takePreview(),
            "example_preview" to example.takePreview(),
            "serial_number" to serialNumber,
            "enabled" to enable,
            "is_default" to (id < 0)
        )
    }

    private fun ReplaceRule.toMcpMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "name" to name,
            "group" to group,
            "pattern" to pattern,
            "replacement" to replacement,
            "scope" to scope,
            "scope_title" to scopeTitle,
            "scope_content" to scopeContent,
            "exclude_scope" to excludeScope,
            "enabled" to isEnabled,
            "is_regex" to isRegex,
            "timeout_millisecond" to timeoutMillisecond,
            "order" to order,
            "is_valid" to isValid()
        )
    }

    private fun ReplaceRule.toMcpSummary(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "name" to name,
            "group" to group,
            "pattern_preview" to pattern.takePreview(),
            "replacement_preview" to replacement.takePreview(),
            "scope" to scope,
            "scope_title" to scopeTitle,
            "scope_content" to scopeContent,
            "exclude_scope" to excludeScope,
            "enabled" to isEnabled,
            "is_regex" to isRegex,
            "timeout_millisecond" to timeoutMillisecond,
            "order" to order,
            "is_valid" to isValid()
        )
    }

    private fun DictRule.toMcpMap(): Map<String, Any?> {
        return mapOf(
            "name" to name,
            "url_rule" to urlRule,
            "show_rule" to showRule,
            "enabled" to enabled,
            "sort_number" to sortNumber
        )
    }

    private fun DictRule.toMcpSummary(): Map<String, Any?> {
        return mapOf(
            "name" to name,
            "url_rule_preview" to urlRule.takePreview(),
            "show_rule_preview" to showRule.takePreview(),
            "enabled" to enabled,
            "sort_number" to sortNumber
        )
    }

    private fun nextDictSortNumber(): Int {
        return (appDb.dictRuleDao.all.maxOfOrNull { it.sortNumber } ?: 0) + 1
    }

    private fun validateRegex(pattern: String, fieldName: String) {
        runCatching { Regex(pattern) }.getOrElse {
            throw IllegalArgumentException("$fieldName 正则语法错误: ${it.localizedMessage}")
        }
    }

    private fun notFound(upstreamEndpoint: String, warning: String): Map<String, Any?> {
        return toolResult(false, upstreamEndpoint, null, warnings = listOf(warning))
    }

    private fun toolResult(
        ok: Boolean,
        upstreamEndpoint: String,
        normalizedData: Any?,
        rawUpstream: Any? = null,
        warnings: List<String> = emptyList(),
        sessionId: String? = null
    ): Map<String, Any?> {
        return mapOf(
            "ok" to ok,
            "upstream_endpoint" to upstreamEndpoint,
            "normalized_data" to normalizedData,
            "raw_upstream" to rawUpstream,
            "warnings" to warnings,
            "session_id" to sessionId
        )
    }

    private fun tool(
        name: String,
        description: String,
        properties: Map<String, Any>,
        required: List<String> = emptyList()
    ): Map<String, Any> {
        return mapOf(
            "name" to name,
            "description" to description,
            "inputSchema" to mapOf(
                "type" to "object",
                "properties" to properties,
                "required" to required
            )
        )
    }

    private fun listProperties(includeDetail: Boolean = false): Map<String, Any> {
        val properties = linkedMapOf<String, Any>(
            "keyword" to stringSchema("Optional keyword filter"),
            "enabled" to booleanSchema("Optional enabled filter"),
            "offset" to numberSchema("Default 0"),
            "limit" to numberSchema("Default 50, max 200")
        )
        if (includeDetail) {
            properties["include_detail"] = booleanSchema("Default false. Include full rule fields for the returned page")
        }
        return properties
    }

    private fun stringSchema(description: String): Map<String, String> {
        return mapOf("type" to "string", "description" to description)
    }

    private fun numberSchema(description: String): Map<String, String> {
        return mapOf("type" to "number", "description" to description)
    }

    private fun booleanSchema(description: String): Map<String, Any> {
        return mapOf("type" to "boolean", "description" to description)
    }

    private fun arraySchema(description: String): Map<String, Any> {
        return mapOf("type" to "array", "description" to description)
    }

    private fun String?.takePreview(maxChars: Int = 120): String? {
        val value = this ?: return null
        return if (value.length <= maxChars) value else value.take(maxChars) + "\n[truncated by MCP at $maxChars chars]"
    }

    private fun JsonElement?.asStringOrNull(): String? {
        return this?.takeIf { it.isJsonPrimitive }?.asString
    }

    private fun JsonElement?.asIntOrNull(): Int? {
        return runCatching { this?.takeIf { it.isJsonPrimitive }?.asInt }.getOrNull()
    }

    private fun JsonElement?.asLongOrNull(): Long? {
        return runCatching { this?.takeIf { it.isJsonPrimitive }?.asLong }.getOrNull()
    }

    private fun JsonElement?.asBooleanOrNull(): Boolean? {
        return runCatching { this?.takeIf { it.isJsonPrimitive }?.asBoolean }.getOrNull()
    }

    private fun JsonElement?.asLongList(): List<Long> {
        val element = this ?: throw IllegalArgumentException("ids is required")
        return when {
            element.isJsonArray -> element.asJsonArray.mapNotNull { it.asLongOrNull() }
            element.isJsonPrimitive -> listOfNotNull(element.asLongOrNull())
            else -> emptyList()
        }.also {
            if (it.isEmpty()) throw IllegalArgumentException("ids is required")
        }
    }

    private fun JsonElement?.asStringList(): List<String> {
        val element = this ?: throw IllegalArgumentException("names is required")
        return when {
            element.isJsonArray -> element.asJsonArray.mapNotNull { it.asStringOrNull()?.trim() }
            element.isJsonPrimitive -> element.asString.split(',', '，').map { it.trim() }
            else -> emptyList()
        }.filter { it.isNotEmpty() }.also {
            if (it.isEmpty()) throw IllegalArgumentException("names is required")
        }
    }
}
