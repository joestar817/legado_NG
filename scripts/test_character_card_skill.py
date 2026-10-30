import unittest
from pathlib import Path


SKILL_PATH = (
    Path(__file__).resolve().parents[1]
    / "app"
    / "src"
    / "main"
    / "assets"
    / "skills"
    / "character_card_generate.md"
)


class CharacterCardSkillPolicyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.skill = SKILL_PATH.read_text(encoding="utf-8")

    def test_apply_batches_all_character_writes_before_results(self):
        self.assertIn("version: 27", self.skill)
        self.assertIn("同一次助手工具调用批次中一次性发出", self.skill)
        self.assertIn("只进行一次写操作确认", self.skill)

    def test_apply_state_is_persisted_after_the_business_batch(self):
        self.assertIn("agent_memory_batch_upsert", self.skill)
        self.assertIn("source_receipt_ids", self.skill)
        self.assertIn("batch 顶层", self.skill)
        self.assertNotIn("记忆更新成功后才继续下一个角色", self.skill)
        self.assertNotIn("每个结果后立即更新预览记忆", self.skill)

    def test_targeted_mode_is_explicit(self):
        self.assertIn("指定角色模式（targeted）", self.skill)
        self.assertIn("目标角色固定为该角色", self.skill)
        self.assertIn("不再询问", self.skill)
        self.assertIn("默认使用当前书", self.skill)
        self.assertIn("更新已有", self.skill)

    def test_discovery_mode_is_explicit(self):
        self.assertIn("发现模式（discovery）", self.skill)
        self.assertIn("先让用户选择扫描范围", self.skill)
        self.assertIn("再选择强度", self.skill)
        self.assertIn("探索窗口", self.skill)

    def test_preview_uses_text_search_to_locate_character_chapters(self):
        self.assertIn("bookshelf.search_content", self.skill)
        self.assertIn("bookshelf_text_search", self.skill)
        self.assertIn("命中章节前后各补 1 章", self.skill)
        self.assertIn("不读未命中章节", self.skill)

    def test_high_frequency_characters_use_deterministic_sparse_sampling(self):
        self.assertIn("高频主角的稀疏采样", self.skill)
        self.assertIn("matched_chapters / max(1, scanned_chapters) >= 0.4", self.skill)
        self.assertIn("每 50 章取该区间内第一个命中章节", self.skill)
        self.assertIn("每角色最多 6 章", self.skill)
        self.assertIn("最多 12 章", self.skill)
        self.assertIn("最多 20 章", self.skill)
        self.assertIn("高频角色第一页命中已足够抽样", self.skill)

    def test_preview_and_apply_are_hard_gated(self):
        self.assertIn("预览与应用之间的硬门禁", self.skill)
        self.assertIn("扫描强度（快速/平衡/深入）只授权采样，不授权写入", self.skill)
        self.assertIn("不得在采样完成的同一轮、同一批工具调用中调用 `bookshelf_character_upsert`", self.skill)
        self.assertIn("没有展示过预览表格时，不得声称", self.skill)
        self.assertIn("用户只选择扫描范围或扫描强度，不构成应用确认", self.skill)

    def test_memory_disabled_falls_back_to_chat_only(self):
        self.assertIn("### 4.1 开关与降级", self.skill)
        self.assertIn("agent_memory_status_get", self.skill)
        self.assertIn("不要调用任何 `agent_memory_*` 工具", self.skill)
        self.assertIn("不支持中断续扫或重启恢复", self.skill)

    def test_apply_confirm_block_is_concrete(self):
        self.assertIn("character_card_apply_confirm", self.skill)
        self.assertIn('"type": "confirm"', self.skill)
        self.assertIn("确认应用上面这些角色卡", self.skill)
        self.assertIn("prompt_template` 固定为", self.skill)

    def test_discovery_and_follow_up_budgets_do_not_overlap(self):
        self.assertIn("### 3.1 采样优先级与预算", self.skill)
        self.assertIn("探索总预算", self.skill)
        self.assertIn("追读预算替换探索预算，不叠加", self.skill)
        self.assertIn("`fast`：最多 12 章", self.skill)
        self.assertIn("`balanced`：最多 30 章", self.skill)
        self.assertIn("`deep`：最多 60 章", self.skill)

    def test_partial_failure_is_not_marked_confirmed(self):
        self.assertIn("preview 状态设为 `partial`", self.skill)
        self.assertIn("不要把部分失败写成 `confirmed`", self.skill)
        self.assertIn("只重试 `failed` 的角色", self.skill)

    def test_chapter_number_conversion_is_explicit(self):
        self.assertIn("### 2.2 章节号换算", self.skill)
        self.assertIn("`start_chapter_index=A-1`", self.skill)
        self.assertIn("`end_chapter_index=B-1`", self.skill)
        self.assertIn("`end=B`（不含）", self.skill)

    def test_gender_and_role_mappings_are_separate(self):
        self.assertIn("表格性别", self.skill)
        self.assertIn("表格角色定位", self.skill)
        self.assertIn("不要用 `other` 代替性别", self.skill)
        self.assertIn("`passerby` 保留在数据模型中", self.skill)

    def test_alias_and_character_count_limits(self):
        self.assertIn("一次性的吐槽、形容词、身份描述", self.skill)
        self.assertIn("`fast` 3-5 张", self.skill)
        self.assertIn("`balanced` 5-10 张", self.skill)
        self.assertIn("`deep` 最多 15 张", self.skill)
        self.assertIn("指定角色模式只生成 1 张", self.skill)

    def test_analysis_intent_has_no_apply_button(self):
        self.assertIn("### 8.3 分析主要角色", self.skill)
        self.assertIn("不输出应用按钮", self.skill)
        self.assertIn("不写库", self.skill)

    def test_receipts_and_partial_windows_are_traceable(self):
        self.assertIn("_agent_result.receipt_id", self.skill)
        self.assertIn("partial_windows", self.skill)
        self.assertIn("sampled_chapters", self.skill)
        self.assertIn("覆盖缺口", self.skill)

    def test_rollback_and_output_contract(self):
        self.assertIn("### 5.4 误写回滚", self.skill)
        self.assertIn("bookshelf_character_delete(ids=[...])", self.skill)
        self.assertIn("除 `legado-interaction` 交互块外", self.skill)
        self.assertIn("| 角色名称 | 性别 | 角色定位 | 别称 | 人物介绍 | 生图提示词 |", self.skill)

    def test_intro_length_is_not_restricted(self):
        self.assertNotIn("1-3 句话", self.skill)
        self.assertNotIn("不超过 200 字", self.skill)
        self.assertIn("`intro`：人物介绍，长度不限", self.skill)

    def test_cache_paging_and_chapter_numbers_are_explicit(self):
        self.assertIn("truncated_by_mcp=true", self.skill)
        self.assertIn("next_start", self.skill)
        self.assertIn("没有查完不得说“全书已缓存”", self.skill)
        self.assertIn("chapter_number", self.skill)
        self.assertIn("不要用 `index` 心算", self.skill)

    def test_discovery_candidates_need_real_reading(self):
        self.assertIn("每个候选角色至少要读 1 个命中窗口", self.skill)
        self.assertIn("只有搜索计数、没有读过正文的候选不能进入表格", self.skill)
        self.assertIn("旧别名也要按当前规则重新校验", self.skill)

    def test_search_and_snippet_token_controls(self):
        self.assertIn("不要重新发起第二个 `bookshelf_text_search`", self.skill)
        self.assertIn("每 4 个片段折算 1 章追读预算", self.skill)
        self.assertIn("单轮最多 5 个片段", self.skill)
        self.assertIn("同一章节不要同时用 `bookshelf_text_window_get` 和 `bookshelf_chapter_snippets_get`", self.skill)


if __name__ == "__main__":
    unittest.main()
