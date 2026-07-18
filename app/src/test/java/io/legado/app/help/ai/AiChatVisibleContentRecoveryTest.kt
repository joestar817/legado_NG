package io.legado.app.help.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.google.gson.JsonObject

class AiChatVisibleContentRecoveryTest {

    @Test
    fun turnContentKeepsLongReportWhenLaterStepsAreEmptyOrShort() {
        val report = "## 快速定位\n\n这是已经生成的完整报告。"
        val afterEmpty = AiChatTurnContent.append(report, "")
        val completed = AiChatTurnContent.append(afterEmpty, "已完成。")

        assertEquals("$report\n\n已完成。", completed)
    }

    @Test
    fun turnContentDoesNotDuplicateRepeatedOrExpandedParts() {
        val first = "正在整理结果。"
        val expanded = "正在整理结果。\n\n## 结论\n正文"

        assertEquals(expanded, AiChatTurnContent.append(first, expanded))
        assertEquals(expanded, AiChatTurnContent.append(expanded, "## 结论"))
    }

    @Test
    fun toolStepContentSeparatesProviderProtocolAndRejectsItAsRecoveryCandidate() {
        val content = "准备执行工具。<｜DSML｜tool_calls>" + "内部协议".repeat(100)

        assertEquals("准备执行工具。", AiChatToolStepContent.providerProjection(content))
        assertEquals(null, AiChatToolStepContent.recoveryCandidate(content))
        assertTrue(AiChatToolStepContent.containsProviderProtocol(content))
    }

    @Test
    fun toolStepContentKeepsOnlySubstantialSafeRecoveryCandidates() {
        val report = ("## 完整结果\n\n这是一份可恢复的通用 Agent 正文。\n").repeat(8)

        assertEquals(null, AiChatToolStepContent.recoveryCandidate("正在读取。"))
        assertEquals(report.trim(), AiChatToolStepContent.recoveryCandidate(report))
    }

    @Test
    fun restoresEarlierVisibleContentWhenFinalReplyOnlyContainsInteraction() {
        val earlier = """
            ## 快速定位

            这是已经生成的扫描报告正文，长度足以证明它不是空的工具过程内容。这里继续补充一段，以覆盖移动端用户真正需要看到的核心结论。
        """.trimIndent().repeat(3)
        val final = """
            ```legado-interaction
            {"id":"next","type":"actions","title":"下一步","options":[]}
            ```
        """.trimIndent()

        val recovered = AiChatVisibleContentRecovery.recover(final, earlier)

        assertTrue(recovered!!.contains("快速定位"))
        assertEquals(1, "legado-interaction".toRegex().findAll(recovered).count())
    }

    @Test
    fun restoresSafeEarlierContentWhenFinalStepIsEmpty() {
        val earlier = ("## 已生成结果\n\n工具步骤已经生成完整正文。\n").repeat(10)

        assertEquals(earlier.trim(), AiChatVisibleContentRecovery.recover("", earlier))
    }

    @Test
    fun restoresEarlierVisibleContentWhenInteractionHasShortLeadIn() {
        val earlier = ("## 快速定位\n\n这份报告已经完整生成，最后的操作卡片只能作为后续操作入口，不能替换正文。\n").repeat(20)
        val final = """
            已保存档案。请选择下一步：

            ```legado-interaction
            {"id":"next","type":"actions","title":"下一步","options":[]}
            ```
        """.trimIndent()

        val recovered = AiChatVisibleContentRecovery.recover(final, earlier)

        assertTrue(recovered!!.contains("这份报告已经完整生成"))
        assertTrue(recovered.contains("已保存档案。请选择下一步："))
        assertEquals(1, "legado-interaction".toRegex().findAll(recovered).count())
    }

    @Test
    fun restoresEarlierVisibleContentWhenFinalReplyOnlyContainsLegacyHiddenArtifact() {
        val earlier = ("## 已生成结果\n\n这段完整正文已经在工具循环中生成，最终隐藏载荷不能把它从界面中覆盖掉。\n").repeat(8)
        val final = """
            ```legado-book-scan
            {"schema_version":1,"payload_type":"legacy_transport"}
            ```
        """.trimIndent()

        val recovered = AiChatVisibleContentRecovery.recover(final, earlier)

        assertTrue(recovered!!.contains("这段完整正文已经在工具循环中生成"))
        assertTrue(AiChatInteractionParser.parse(recovered).hasHiddenArtifacts)
        assertEquals(earlier.trim(), AiChatInteractionParser.parse(recovered).content)
    }

    @Test
    fun restoresEarlierVisibleContentWhenLegacyHiddenArtifactHasShortLeadIn() {
        val earlier = ("## 已生成结果\n\n这是应当保留的长正文，短暂的收尾提示不构成一份新的独立回答。\n").repeat(12)
        val final = """
            处理完成。

            <book_scan_delta>{"schema_version":1}</book_scan_delta>
        """.trimIndent()

        val recovered = AiChatVisibleContentRecovery.recover(final, earlier)
        val visible = AiChatInteractionParser.parse(recovered!!).content

        assertTrue(visible.contains("这是应当保留的长正文"))
        assertTrue(visible.endsWith("处理完成。"))
    }

    @Test
    fun doesNotReplaceARealFinalAnswer() {
        val final = """
            ## 新结论

            这是一条真正的最终回答。它不仅是操作卡片的引导语，还包含了本轮分析得出的核心判断、依据和适用边界，因此不应被先前的内容替换。
            此外，它还补充说明了新的变化和接下来需要注意的限制，长度足以作为独立的最终结果。
            ```legado-interaction
            {"id":"next","type":"actions","title":"下一步","options":[]}
            ```
        """.trimIndent()

        assertEquals(null, AiChatVisibleContentRecovery.recover(final, "足够长的旧内容".repeat(20)))
    }

    @Test
    fun doesNotRestoreAnOldDraftForAnOrdinaryShortFinalAnswer() {
        val final = "处理完成，当前没有更多内容。"
        val oldDraft = ("这是一份很长但已经被后续回答取代的旧草稿。\n").repeat(20)
        val uploads = listOf(
            message("user", "执行任务"),
            message("assistant", oldDraft),
            message("assistant", final)
        )

        assertEquals(null, AiChatVisibleContentRecovery.recover(final, oldDraft))
        assertEquals(null, AiChatVisibleContentRecovery.recoverFromUploadHistory(final, uploads))
    }

    @Test
    fun finalizeHookRetryDoesNotReplaceACompleteAnswerWithACompletionClaim() {
        val report = ("## 完整报告\n\n这是已经生成并应当展示给用户的正文。\n").repeat(30)
        val retryClaim = "扫描完成。报告已输出，你可以继续操作。"

        assertEquals(
            report.trim(),
            AiChatVisibleContentRecovery.recoverAfterInternalRetry(retryClaim, report)
        )
        assertEquals(null, AiChatVisibleContentRecovery.recover(retryClaim, report))
    }

    @Test
    fun hidesLegacyBookScanMetadataWithoutHidingVisibleHistory() {
        val content = """
            ## 扫描边界

            已完整覆盖第 1～450 章。

            ```book_scan_delta
            {"schema_version":1,"work_key":"测试书","observed_chapters":[1,2,3]}
            ```
        """.trimIndent()

        assertEquals(
            "## 扫描边界\n\n已完整覆盖第 1～450 章。",
            AiChatInteractionParser.parse(content).content
        )
    }

    @Test
    fun restoresLastVisibleMessageFromPersistedUploadHistory() {
        val final = """
            ```legado-interaction
            {"id":"next","type":"actions","title":"下一步","options":[]}
            ```
        """.trimIndent()
        val report = ("## 快速定位\n\n这份报告已在工具循环中生成，但不能被最后的交互块覆盖。\n").repeat(4)
        val uploads = listOf(
            message("system", "系统提示"),
            message("user", "打开扫描"),
            message("assistant", report),
            message("assistant", final)
        )

        val recovered = AiChatVisibleContentRecovery.recoverFromUploadHistory(final, uploads)

        assertTrue(recovered!!.contains("这份报告已在工具循环中生成"))
        assertEquals(1, "legado-interaction".toRegex().findAll(recovered).count())
    }

    @Test
    fun restoresLastVisibleMessageWhenPersistedFinalOnlyContainsHiddenArtifact() {
        val final = """
            ```character_scan_meta
            {"schema_version":1,"payload_type":"legacy_transport"}
            ```
        """.trimIndent()
        val report = ("## 完整结果\n\n这份可见结果已生成并写入上传历史，隐藏载荷不能成为界面的唯一内容。\n").repeat(5)
        val uploads = listOf(
            message("user", "执行任务"),
            message("assistant", report),
            message("assistant", final)
        )

        val recovered = AiChatVisibleContentRecovery.recoverFromUploadHistory(final, uploads)

        assertEquals(report.trim(), AiChatInteractionParser.parse(recovered!!).content)
    }

    private fun message(role: String, content: String) = JsonObject().apply {
        addProperty("role", role)
        addProperty("content", content)
    }
}
