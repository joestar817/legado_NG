package io.legado.app.ui.config

import android.graphics.Typeface
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.gson.JsonObject
import io.legado.app.R
import io.legado.app.help.ai.AiPendingToolCall
import io.legado.app.utils.dpToPx

internal data class WriteOperationSummary(
    val title: String,
    val description: String
)

internal fun createWriteOperationSummaryView(
    activity: AiChatActivity,
    index: Int,
    summary: WriteOperationSummary
): LinearLayout {
    return LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        background = ContextCompat.getDrawable(activity, R.drawable.ng_bg_purify_panel)
        setPadding(10.dpToPx(), 8.dpToPx(), 10.dpToPx(), 8.dpToPx())
        addView(TextView(activity).apply {
            text = "$index. ${summary.title}"
            setTextColor(ContextCompat.getColor(activity, R.color.ng_on_surface))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        })
        addView(TextView(activity).apply {
            text = summary.description
            setTextColor(ContextCompat.getColor(activity, R.color.ng_on_surface_variant))
            textSize = 13f
            setLineSpacing(1f, 1.05f)
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 4.dpToPx()
        })
    }.also { view ->
        view.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 8.dpToPx()
        }
    }
}

internal fun AiPendingToolCall.toWriteOperationSummary(): WriteOperationSummary {
    val args = arguments
    val title: String
    val description: String
    when (toolName) {
        "bookshelf_character_upsert" -> {
            title = "写入角色卡"
            description = buildObjectDescription(
                action = "创建或更新角色卡",
                primary = args.characterLabel(),
                secondary = args.workLabel(),
                missingPrimaryLabel = "未识别角色"
            )
        }
        "bookshelf_character_delete" -> {
            title = "删除角色卡"
            description = buildObjectDescription(
                action = "删除角色卡记录",
                primary = args.idsLabel(),
                secondary = args.workLabel()
            )
        }
        "bookshelf_character_set_enabled" -> {
            title = if (args.booleanOrNull("enabled") == false) {
                "禁用角色卡"
            } else {
                "启用角色卡"
            }
            description = buildObjectDescription(
                action = "修改角色卡启用状态",
                primary = args.idsLabel(),
                secondary = args.workLabel()
            )
        }
        "bookshelf_book_upsert" -> {
            title = "写入书籍"
            val book = args.objectOrNull("book")
            description = buildObjectDescription(
                action = "新增或更新书架书籍",
                primary = book?.bookLabel(),
                secondary = book?.stringOrNull("origin") ?: book?.stringOrNull("book_url")
            )
        }
        "bookshelf_book_delete" -> {
            title = "删除书籍"
            description = buildObjectDescription(
                action = "从书架删除书籍",
                primary = args.stringListLabel("book_urls", "bookUrls"),
                secondary = null
            )
        }
        "bookshelf_book_group_update" -> {
            title = "更新书籍分组"
            description = buildObjectDescription(
                action = "修改书籍所属分组",
                primary = args.stringListLabel("book_urls", "bookUrls"),
                secondary = args.stringListLabel("group_names", "groupNames")
                    ?: args.numberListLabel("group_ids", "groupIds")
            )
        }
        "bookshelf_cache_download" -> {
            title = if (args.booleanOrNull("refresh_existing") == true) "刷新章节缓存" else "缓存章节"
            description = buildObjectDescription(
                action = if (args.booleanOrNull("refresh_existing") == true) {
                    "清理并重新缓存指定章节"
                } else {
                    "触发指定章节离线缓存"
                },
                primary = args.cacheChapterSelectionLabel(),
                secondary = args.workLabel()
            )
        }
        "bookshelf_cache_clear" -> {
            title = "清理章节缓存"
            description = buildObjectDescription(
                action = "删除本地章节正文缓存",
                primary = args.cacheChapterSelectionLabel(),
                secondary = args.workLabel()
            )
        }
        "bookshelf_group_upsert" -> {
            title = "写入书架分组"
            val group = args.objectOrNull("group")
            description = buildObjectDescription(
                action = "创建或更新书架分组",
                primary = group?.stringOrNull("groupName") ?: group?.stringOrNull("name")
                    ?: args.stringOrNull("group_name"),
                secondary = null
            )
        }
        "bookshelf_group_delete" -> {
            title = "删除书架分组"
            description = buildObjectDescription(
                action = "删除书架分组",
                primary = args.stringListLabel("group_names", "groupNames")
                    ?: args.numberListLabel("group_ids", "groupIds"),
                secondary = if (args.booleanOrNull("only_empty") == false) "会先移除图书分组归属" else null
            )
        }
        "bookshelf_bookmark_upsert" -> {
            title = "写入书签"
            val bookmark = args.objectOrNull("bookmark")
            description = buildObjectDescription(
                action = "新增或更新书签",
                primary = bookmark?.stringOrNull("chapter_name") ?: bookmark?.stringOrNull("chapterName"),
                secondary = args.workLabel() ?: bookmark?.bookLabel()
            )
        }
        "bookshelf_bookmark_delete" -> {
            title = "删除书签"
            description = buildObjectDescription(
                action = "删除书签记录",
                primary = args.numberListLabel("times") ?: args.idsLabel(),
                secondary = null
            )
        }
        "bookshelf_read_record_upsert" -> {
            title = "写入阅读记录"
            val record = args.objectOrNull("record")
            description = buildObjectDescription(
                action = "新增或更新阅读记录",
                primary = record?.bookLabel() ?: args.workLabel(),
                secondary = null
            )
        }
        "bookshelf_read_record_delete" -> {
            title = "删除阅读记录"
            description = buildObjectDescription(
                action = "删除阅读记录",
                primary = args.stringOrNull("book_name") ?: args.idsLabel() ?: args.workLabel(),
                secondary = null
            )
        }
        "book_source_save" -> {
            title = "保存书源"
            val source = args.objectOrNull("source")
            description = buildObjectDescription(
                action = "新增或更新书源配置",
                primary = source?.stringOrNull("bookSourceName") ?: source?.stringOrNull("name"),
                secondary = source?.stringOrNull("bookSourceUrl") ?: source?.stringOrNull("url")
            )
        }
        "book_source_delete" -> {
            title = "删除书源"
            description = buildObjectDescription(
                action = "删除书源配置",
                primary = args.stringListLabel("urls"),
                secondary = null
            )
        }
        "book_source_set_enabled" -> {
            title = if (args.booleanOrNull("enabled") == false) "禁用书源" else "启用书源"
            description = buildObjectDescription(
                action = "修改书源启用状态",
                primary = args.stringListLabel("urls"),
                secondary = null
            )
        }
        "replace_rule_upsert",
        "bookshelf_replace_rule_upsert",
        "bookshelf_replace_rule_draft_upsert",
        "settings_replace_rule_upsert" -> {
            title = "写入替换规则"
            description = buildObjectDescription(
                action = "新增或更新替换净化规则",
                primary = args.ruleLabel(),
                secondary = args.workLabel()
            )
        }
        "replace_rule_delete",
        "bookshelf_replace_rule_delete",
        "bookshelf_replace_rule_rollback",
        "settings_replace_rule_delete" -> {
            title = "删除替换规则"
            description = buildObjectDescription(
                action = "删除替换净化规则",
                primary = args.idsLabel(),
                secondary = null
            )
        }
        "replace_rule_set_enabled",
        "bookshelf_replace_rule_set_enabled",
        "bookshelf_replace_rule_draft_apply",
        "settings_replace_rule_set_enabled" -> {
            title = if (args.booleanOrNull("enabled") == false) "禁用替换规则" else "启用替换规则"
            description = buildObjectDescription(
                action = "修改替换净化规则启用状态",
                primary = args.idsLabel(),
                secondary = null
            )
        }
        "settings_txt_toc_rule_upsert" -> {
            title = "写入目录规则"
            description = buildObjectDescription(
                action = "新增或更新 TXT 目录规则",
                primary = args.objectOrNull("rule")?.stringOrNull("name") ?: args.stringOrNull("name"),
                secondary = null
            )
        }
        "settings_txt_toc_rule_delete" -> {
            title = "删除目录规则"
            description = buildObjectDescription(
                action = "删除 TXT 目录规则",
                primary = args.idsLabel(),
                secondary = null
            )
        }
        "settings_txt_toc_rule_set_enabled" -> {
            title = if (args.booleanOrNull("enabled") == false) "禁用目录规则" else "启用目录规则"
            description = buildObjectDescription(
                action = "修改 TXT 目录规则启用状态",
                primary = args.idsLabel(),
                secondary = null
            )
        }
        "settings_dict_rule_upsert" -> {
            title = "写入字典规则"
            description = buildObjectDescription(
                action = "新增或更新字典规则",
                primary = args.objectOrNull("rule")?.stringOrNull("name") ?: args.stringOrNull("name"),
                secondary = null
            )
        }
        "settings_dict_rule_delete" -> {
            title = "删除字典规则"
            description = buildObjectDescription(
                action = "删除字典规则",
                primary = args.stringListLabel("names") ?: args.idsLabel(),
                secondary = null
            )
        }
        "settings_dict_rule_set_enabled" -> {
            title = if (args.booleanOrNull("enabled") == false) "禁用字典规则" else "启用字典规则"
            description = buildObjectDescription(
                action = "修改字典规则启用状态",
                primary = args.stringListLabel("names") ?: args.idsLabel(),
                secondary = null
            )
        }
        "network_log_clear" -> {
            title = "清空网络日志"
            description = buildObjectDescription(
                action = "删除网络请求日志",
                primary = args.stringOrNull("keyword") ?: args.stringOrNull("type") ?: "当前筛选范围",
                secondary = null
            )
        }
        "debug_log_clear" -> {
            title = "清空调试日志"
            description = buildObjectDescription(
                action = "删除调试日志",
                primary = args.stringOrNull("keyword") ?: "当前筛选范围",
                secondary = null
            )
        }
        else -> {
            title = readableWriteToolTitle(toolName)
            description = buildObjectDescription(
                action = "执行写操作",
                primary = toolName,
                secondary = args.shortArgumentHint()
            )
        }
    }
    return WriteOperationSummary(title, description)
}

private fun buildObjectDescription(
    action: String,
    primary: String?,
    secondary: String?,
    missingPrimaryLabel: String? = null
): String {
    return buildString {
        append(action)
        val primaryText = primary?.takeIf { it.isNotBlank() } ?: missingPrimaryLabel
        primaryText?.let {
            append("：")
            append(it.limitForDialog(80))
        }
        secondary?.takeIf { it.isNotBlank() && it != primary }?.let {
            append("\n对象：")
            append(it.limitForDialog(100))
        }
    }
}

private fun readableWriteToolTitle(toolName: String): String {
    return when {
        toolName.endsWith("_delete") -> "删除数据"
        toolName.endsWith("_upsert") || toolName.endsWith("_save") -> "写入数据"
        toolName.endsWith("_set_enabled") -> "修改启用状态"
        toolName.endsWith("_apply") -> "应用变更"
        toolName.endsWith("_rollback") -> "回滚变更"
        toolName.endsWith("_archive") -> "归档数据"
        toolName.endsWith("_clear") -> "清空数据"
        else -> "执行写操作"
    }
}

private fun JsonObject.objectOrNull(name: String): JsonObject? {
    return get(name)?.takeIf { it.isJsonObject }?.asJsonObject
}

private fun JsonObject.stringOrNull(name: String): String? {
    return runCatching {
        get(name)?.takeIf { it.isJsonPrimitive }?.asString
    }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
}

private fun JsonObject.booleanOrNull(name: String): Boolean? {
    return runCatching {
        get(name)?.takeIf { it.isJsonPrimitive }?.asBoolean
    }.getOrNull()
}

private fun JsonObject.workLabel(): String? {
    return stringOrNull("work_key") ?: stringOrNull("workKey")
        ?: bookLabel()
        ?: stringOrNull("book_url")
        ?: stringOrNull("bookUrl")
}

private fun JsonObject.bookLabel(): String? {
    val name = stringOrNull("name") ?: stringOrNull("book_name") ?: stringOrNull("bookName")
    val author = stringOrNull("author") ?: stringOrNull("book_author") ?: stringOrNull("bookAuthor")
    return when {
        !name.isNullOrBlank() && !author.isNullOrBlank() -> "$name / $author"
        !name.isNullOrBlank() -> name
        else -> null
    }
}

private fun JsonObject.ruleLabel(): String? {
    val rule = objectOrNull("rule")
    val ruleName = rule?.stringOrNull("name")
    val rulesCount = get("rules")?.takeIf { it.isJsonArray }?.asJsonArray?.size()
    return ruleName ?: rulesCount?.let { "${it} 条规则" }
}

private fun JsonObject.characterLabel(): String? {
    val character = objectOrNull("character")
    val singleName = character?.stringOrNull("name")
        ?: character?.stringOrNull("character_name")
        ?: character?.stringOrNull("characterName")
        ?: character?.stringOrNull("role_name")
        ?: character?.stringOrNull("roleName")
        ?: stringOrNull("character_name")
        ?: stringOrNull("characterName")
        ?: stringOrNull("role_name")
        ?: stringOrNull("roleName")
        ?: stringOrNull("name")
    if (!singleName.isNullOrBlank()) return singleName
    val characters = get("characters")?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
    return characters.mapNotNull { element ->
        element.takeIf { it.isJsonObject }?.asJsonObject?.let { item ->
            item.stringOrNull("name")
                ?: item.stringOrNull("character_name")
                ?: item.stringOrNull("characterName")
                ?: item.stringOrNull("role_name")
                ?: item.stringOrNull("roleName")
        }
    }.takeIf { it.isNotEmpty() }?.joinToPreview()
}

private fun JsonObject.cacheChapterSelectionLabel(): String? {
    if (booleanOrNull("clear_book") == true) {
        return "整本书缓存"
    }
    numberListLabel("chapter_indexes", "chapterIndexes")?.let { return "章节 $it" }
    get("ranges")?.takeIf { it.isJsonArray }?.asJsonArray?.let { ranges ->
        if (ranges.size() > 0) {
            val labels = ranges.mapNotNull { item ->
                item.takeIf { it.isJsonObject }?.asJsonObject?.let { range ->
                    val start = range.stringOrNull("start")
                    val end = range.stringOrNull("end")
                    if (start != null && end != null) "$start-$end" else start
                }
            }
            if (labels.isNotEmpty()) return labels.joinToPreview()
        }
    }
    val start = stringOrNull("start")
    val end = stringOrNull("end")
    return when {
        start != null && end != null -> "章节 $start-$end"
        start != null -> "章节 $start"
        else -> null
    }
}

private fun JsonObject.idsLabel(): String? {
    return numberListLabel("ids")
        ?: stringListLabel("ids", "names")
        ?: stringOrNull("id")
}

private fun JsonObject.stringListLabel(vararg names: String): String? {
    names.forEach { name ->
        val element = get(name) ?: return@forEach
        when {
            element.isJsonArray -> {
                val values = element.asJsonArray.mapNotNull {
                    runCatching { it.asString }.getOrNull()?.takeIf { value -> value.isNotBlank() }
                }
                if (values.isNotEmpty()) return values.joinToPreview()
            }
            element.isJsonPrimitive -> {
                return runCatching { element.asString }.getOrNull()
                    ?.split(',', '，')
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    ?.takeIf { it.isNotEmpty() }
                    ?.joinToPreview()
            }
        }
    }
    return null
}

private fun JsonObject.numberListLabel(vararg names: String): String? {
    names.forEach { name ->
        val element = get(name) ?: return@forEach
        when {
            element.isJsonArray -> {
                val values = element.asJsonArray.mapNotNull {
                    runCatching { it.asLong }.getOrNull()?.toString()
                }
                if (values.isNotEmpty()) return values.joinToPreview(prefix = "#")
            }
            element.isJsonPrimitive -> {
                return runCatching { element.asLong }.getOrNull()?.let { "#$it" }
            }
        }
    }
    return null
}

private fun JsonObject.shortArgumentHint(): String? {
    return sequenceOf("name", "keyword", "scope", "group", "id")
        .mapNotNull { stringOrNull(it) }
        .firstOrNull()
}

private fun List<String>.joinToPreview(prefix: String = ""): String {
    val visible = take(3).joinToString("、") { "$prefix$it" }
    return if (size > 3) "$visible 等 ${size} 项" else visible
}

private fun String.limitForDialog(max: Int): String {
    return if (length <= max) this else take(max) + "..."
}
