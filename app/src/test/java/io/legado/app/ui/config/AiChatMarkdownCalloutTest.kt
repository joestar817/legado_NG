package io.legado.app.ui.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiChatMarkdownCalloutTest {

    @Test
    fun normalizesStrongDelimiterAfterChineseClosingPunctuation() {
        assertEquals(
            "读者主要跟随<strong>璇玑（白蛇仙转世）</strong>的视角走",
            normalizeAiChatMarkdown("读者主要跟随**璇玑（白蛇仙转世）**的视角走")
        )
        assertEquals(
            "终究是一个<strong>&quot;原谅了但没有完美结局&quot;</strong>的收束方式",
            normalizeAiChatMarkdown("终究是一个**\"原谅了但没有完美结局\"**的收束方式")
        )
        assertEquals(
            "**结尾状态**：保持原有合法 Markdown",
            normalizeAiChatMarkdown("**结尾状态**：保持原有合法 Markdown")
        )
    }

    @Test
    fun keepsOnlyHttpLinksClickable() {
        assertEquals(
            "读取快速定位后的交互定义",
            normalizeAiChatMarkdown("读取[快速定位后的交互定义](references/interactions.md)")
        )
        assertEquals(
            "查看[网页](https://example.com/path)",
            normalizeAiChatMarkdown("查看[网页](https://example.com/path)")
        )
        assertEquals(
            "`[内部定义](references/interactions.md)`",
            normalizeAiChatMarkdown("`[内部定义](references/interactions.md)`")
        )
    }

    @Test
    fun parsesWarningCalloutUntilQuoteEnds() {
        val lines = listOf(
            "> [!WARNING] 慎入警告",
            "> - 介意长期压抑的读者慎入",
            "> - 不接受主角受辱的读者慎入",
            "## 下一节"
        )

        val result = parseMarkdownCallout(lines, 0)

        assertEquals(MarkdownCalloutType.WARNING, result?.callout?.type)
        assertEquals("慎入警告", result?.callout?.title)
        assertEquals(true, result?.callout?.hasExplicitTitle)
        assertEquals(
            "- 介意长期压抑的读者慎入\n- 不接受主角受辱的读者慎入",
            result?.callout?.body
        )
        assertEquals(3, result?.nextIndex)
    }

    @Test
    fun keepsOrdinaryBlockquoteInNormalMarkdownPath() {
        assertNull(parseMarkdownCallout(listOf("> 普通引用"), 0))
    }

    @Test
    fun marksDefaultTitleSoRendererCanAddGenericIcon() {
        val result = parseMarkdownCallout(listOf("> [!NOTE]", "> 正文"), 0)

        assertEquals("提示", result?.callout?.title)
        assertEquals(false, result?.callout?.hasExplicitTitle)
    }

    @Test
    fun rendersLegadoBookReportPayloadToMarkdown() {
        val markdown = renderLegadoBookReportMarkdown(
            """
            {
              "type": "quick_scan_report",
              "verdict": "reject",
              "headline": "开头压力很重，不适合想轻松看书的读者",
              "basis": {
                "book": "白蛇",
                "author": "lingyungzs",
                "status": "已完结",
                "sampled": ["开头 1-5 章", "结尾 34-38 章"]
              },
              "overview": ["古风虐恋短篇", "开头直接进入高压情境"],
              "reader_feeling": "读起来压迫感很强，明显不是轻松甜文。",
              "confirmed_risks": [
                {"title": "极端虐待", "text": "开头样本已经出现会直接劝退的高压内容。", "level": "high"}
              ],
              "deterrent_points": [],
              "appeal_points": [],
              "main_problems": [],
              "unknowns": ["中段未扫"]
            }
            """.trimIndent()
        )

        assertEquals(true, markdown.contains("## 适读结论"))
        assertEquals(true, markdown.contains("> [!CAUTION] 🎯 当前判断"))
        assertEquals(true, markdown.contains("**目前不推荐**"))
        assertEquals(true, markdown.contains("- **书籍**：白蛇｜lingyungzs"))
        assertEquals(true, markdown.contains("> [!CAUTION] 极端虐待"))
        assertEquals(true, markdown.contains("> [!WARNING] 🔎 扫描边界：还有没扫完的地方"))
        assertEquals(true, markdown.contains("没扫到不等于没有隐藏雷点"))
    }

    @Test
    fun rendersLegadoBookReportXmlPayloadToMarkdown() {
        val markdown = renderLegadoBookReportMarkdown(
            """
            <book-report type="quick_scan_report">
              <verdict>cautious</verdict>
              <headline>能吃高压虐恋再继续</headline>
              <basic>
                <book>白蛇</book>
                <author>lingyungzs</author>
                <status>已完结</status>
                <word-count>4 万字</word-count>
                <category>玄幻言情</category>
                <sampled>开头 1-5 章 + 结尾 34-38 章</sampled>
                <positioning>古风高压虐恋短篇</positioning>
              </basic>
              <subjective-review>这不是轻松消遣，开头就把压迫感摆在脸上。</subjective-review>
              <overview>
                <item>开局直接进入高压虐恋，不靠慢热铺垫。</item>
              </overview>
              <audience>
                <item>适合能接受强压抑和后期清算的读者。</item>
              </audience>
              <reading-feeling>
                <pressure index="4" label="高压">压力主要来自开头连续伤害和结尾分离感。</pressure>
                <item>读起来憋屈感明显。</item>
              </reading-feeling>
              <risk level="high">
                <title>极端虐待</title>
                <text>开头样本已经出现会直接劝退的高压内容。</text>
              </risk>
              <appeal>
                <title>情绪推进够快</title>
                <text>短篇内冲突密度很高。</text>
              </appeal>
              <problem>
                <title>开头门槛过高</title>
                <text>不能接受虐恋压迫感的读者很难进。</text>
              </problem>
              <unknown>中段未扫。</unknown>
            </book-report>
            """.trimIndent()
        )

        assertEquals(true, markdown.contains("**谨慎试读**：能吃高压虐恋再继续"))
        assertEquals(true, markdown.contains("> [!WARNING] 🎯 当前判断"))
        assertEquals(true, markdown.contains("- **篇幅/分类**：4 万字｜玄幻言情"))
        assertEquals(true, markdown.contains("## 主观锐评"))
        assertEquals(true, markdown.contains("> [!WARNING] 老书虫吐槽"))
        assertEquals(true, markdown.contains("这不是轻松消遣，开头就把压迫感摆在脸上。"))
        assertEquals(true, markdown.contains("## 作品受众"))
        assertEquals(true, markdown.contains("## 阅读感受"))
        assertEquals(true, markdown.contains("**压抑指数**：★★★★☆｜4/5｜高度压抑"))
        assertEquals(false, markdown.contains("## 真正值得看的地方"))
        assertEquals(false, markdown.contains("## 明显的问题"))
        assertEquals(true, markdown.contains("> [!CAUTION] 极端虐待"))
    }

    @Test
    fun rendersContinueScanBookReportXmlPayloadToMarkdown() {
        val markdown = renderLegadoBookReportMarkdown(
            """
            <book-report type="continue_scan_report">
              <verdict>cautious</verdict>
              <headline>中段有关系雷，别当普通后宫日常冲</headline>
              <basic>
                <book>李庄生同学不想重生</book>
                <author>李白不太白</author>
                <sampled>继续排雷，补读中段关键章节</sampled>
              </basic>
              <risk level="high">
                <title>核心关系里有接盘感</title>
                <text>女主过去的关系持续影响男主婚姻，后面还把男主推回这段关系。</text>
              </risk>
              <unknown>还有部分中段没完整读完。</unknown>
            </book-report>
            """.trimIndent()
        )

        assertEquals(false, markdown.contains("报告解析失败"))
        assertEquals(true, markdown.contains("## 适读结论"))
        assertEquals(true, markdown.contains("**谨慎试读**：中段有关系雷，别当普通后宫日常冲"))
        assertEquals(true, markdown.contains("- **书籍**：李庄生同学不想重生｜李白不太白"))
        assertEquals(true, markdown.contains("## 重点避坑"))
        assertEquals(true, markdown.contains("核心关系里有接盘感"))
    }

    @Test
    fun removesDuplicatedVerdictPrefixFromBookReportHeadline() {
        val markdown = renderLegadoBookReportMarkdown(
            """
            <book-report type="quick_scan_report">
              <verdict>reject</verdict>
              <headline>目前不推荐：亲密关系施害 + 母子惨剧 + 洗白原谅</headline>
            </book-report>
            """.trimIndent()
        )

        assertEquals(
            true,
            markdown.contains("**目前不推荐**：亲密关系施害 + 母子惨剧 + 洗白原谅")
        )
        assertEquals(false, markdown.contains("目前不推荐**：目前不推荐："))
    }

    @Test
    fun doesNotStripReaderToneFromBookReportHeadline() {
        val markdown = renderLegadoBookReportMarkdown(
            """
            <book-report type="quick_scan_report">
              <verdict>reject</verdict>
              <headline>亲密关系施害，直接跳过</headline>
            </book-report>
            """.trimIndent()
        )

        assertEquals(true, markdown.contains("**目前不推荐**：亲密关系施害，直接跳过"))
        assertEquals(false, markdown.contains("目前不推荐**：目前不推荐"))
    }

    @Test
    fun toleratesLegacyXmlRiskAttributesWithoutLimitingRiskCount() {
        val markdown = renderLegadoBookReportMarkdown(
            """
            <book-report type="quick_scan_report">
              <verdict>cautious</verdict>
              <headline>硬雷明确，慎入</headline>
              <reading-feeling>
                <pressure index="5" label="极度压抑">样本已确认高压内容。</pressure>
              </reading-feeling>
              <risk severity="hard" label="极端虐待">低细节说明一。</risk>
              <risk title="核心亲属伤害" severity="high">低细节说明二。</risk>
              <risk title="亲密关系施害" severity="medium">低细节说明三。</risk>
              <risk title="整体苦大于甜" severity="medium">第四条已确认风险也应显示。</risk>
              <unknown>中段未扫。</unknown>
            </book-report>
            """.trimIndent()
        )

        assertEquals(true, markdown.contains("> [!CAUTION] 极端虐待"))
        assertEquals(true, markdown.contains("> 低细节说明一。"))
        assertEquals(true, markdown.contains("> [!CAUTION] 核心亲属伤害"))
        assertEquals(true, markdown.contains("> [!WARNING] 亲密关系施害"))
        assertEquals(true, markdown.contains("> [!WARNING] 整体苦大于甜"))
        assertEquals(true, markdown.contains("> 第四条已确认风险也应显示。"))
    }

    @Test
    fun toleratesStringArraysInLegacyJsonBookReport() {
        val markdown = renderLegadoBookReportMarkdown(
            """
            {
              "type": "quick_scan_report",
              "verdict": "cautious",
              "headline": "谨慎试读",
              "basis": {"book": "测试书"},
              "appeal_points": ["节奏很快"],
              "main_problems": ["重复解释偏多"],
              "confirmed_risks": ["开头压力过高"],
              "unknowns": ["中段未扫"]
            }
            """.trimIndent()
        )

        assertEquals(false, markdown.contains("## 真正值得看的地方"))
        assertEquals(false, markdown.contains("节奏很快"))
        assertEquals(false, markdown.contains("## 明显的问题"))
        assertEquals(false, markdown.contains("重复解释偏多"))
        assertEquals(true, markdown.contains("## 重点避坑"))
        assertEquals(true, markdown.contains("开头压力过高"))
    }

    @Test
    fun usesGreenVerdictOnlyForSuitableBooksAndHidesInternalScanWords() {
        val markdown = renderLegadoBookReportMarkdown(
            """
            <book-report type="quick_scan_report">
              <verdict>try</verdict>
              <headline>对目标读者来说可以直接试读</headline>
              <unknown>第二部后期至第三部初期（ch257-405）大量内容仅snippet覆盖，需继续确认；scan_100 与 manifest 不对读者展示。</unknown>
            </book-report>
            """.trimIndent()
        )

        assertEquals(true, markdown.contains("> [!TIP] 🎯 当前判断"))
        assertEquals(true, markdown.contains("第257—405章"))
        assertEquals(true, markdown.contains("许多章节只快速浏览了首尾"))
        assertEquals(true, markdown.contains("> [!WARNING] 🔎 扫描边界：还有没扫完的地方"))
        assertEquals(true, markdown.contains("没扫到不等于没有隐藏雷点"))
        assertEquals(true, markdown.contains("本轮检查"))
        assertEquals(true, markdown.contains("扫描记录"))
        assertEquals(false, markdown.contains("ch257-405"))
        assertEquals(false, markdown.contains("snippet"))
        assertEquals(false, markdown.contains("scan_100"))
        assertEquals(false, markdown.contains("manifest"))
    }
}
