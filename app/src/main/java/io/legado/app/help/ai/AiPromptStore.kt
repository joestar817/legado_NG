package io.legado.app.help.ai

import io.legado.app.constant.PreferKey
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString
import splitties.init.appCtx

object AiPromptStore {

    enum class Prompt(
        val id: String,
        val key: String,
        val defaultPrompt: String
    ) {
        PARAGRAPH_PURIFY(
            id = "paragraph_purify",
            key = PreferKey.aiPromptParagraphPurify,
            defaultPrompt = """
            你是小说正文净化器。你的任务是删除混入小说正文的污染字符或污染片段。
            严格限制：
            1. 只能删除破坏词语或句子连贯性的污染内容。
            2. 不得新增文字，不得改写句子，不得替换同义词。
            3. 不得把全角数字改成半角数字，不得改动正常数量、年份、章节编号和人物称呼。
            4. 正文句中突兀夹杂、破坏语义连贯性的异常编号、符号或乱码片段通常是污染。
            5. 正常英文、拼音、专有名词、设定术语、章节编号、列表编号必须保留。
            6. 不确定时保留原文。
            """.trimIndent()
        ),
        CHAPTER_OPTIMIZE(
            id = "chapter_optimize",
            key = PreferKey.aiPromptChapterOptimize,
            defaultPrompt = """
            你是小说章节优化器。你的任务是逐段检查章节内容，识别并净化影响阅读体验的污染内容。
            重点关注：
            1. 正文句中混入的异常编号、符号、乱码片段。
            2. 章节段落中明显的站点提示、广告提示、分页提示。
            3. 标题或正文中的重复、残缺、站点残留。
            严格限制：
            1. 只能删除污染内容，不得新增文字，不得改写句子，不得替换同义词。
            2. 不得合并、拆分或重排段落。
            3. 不得把全角数字改成半角数字，不得改动正常数量、年份、章节编号和人物称呼。
            4. 正常英文、拼音、专有名词、设定术语、章节编号、列表编号必须保留。
            5. 必须逐段检查，发现污染就返回该段，不要因为其它段落更明显而漏掉轻微污染。
            6. 不确定时保留原文。
            """.trimIndent()
        )
    }

    fun prompt(type: Prompt): String {
        return appCtx.getPrefString(type.key, null)
            ?.takeIf { it.isNotBlank() }
            ?: type.defaultPrompt
    }

    fun isCustom(type: Prompt): Boolean {
        return appCtx.getPrefString(type.key, null)?.isNotBlank() == true
    }

    fun save(type: Prompt, prompt: String) {
        appCtx.putPrefString(type.key, prompt.trim())
    }

    fun reset(type: Prompt) {
        appCtx.putPrefString(type.key, "")
    }
}
