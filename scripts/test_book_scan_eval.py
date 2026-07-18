from __future__ import annotations

import json
import re
import tempfile
import unittest
from io import BytesIO
from pathlib import Path

from scripts import book_scan_eval as evaluator


class BookScanEvalTest(unittest.TestCase):

    def test_streaming_collector_reassembles_fragmented_tool_calls(self) -> None:
        payload = (
            'data: {"model":"grok","choices":[{"delta":{"reasoning_content":"想",'
            '"tool_calls":[{"index":0,"id":"call_1","type":"function",'
            '"function":{"name":"bookshelf_","arguments":"{\\"a\\":"}}]},'
            '"finish_reason":null}]}\n\n'
            'data: {"choices":[{"delta":{"reasoning_content":"完",'
            '"tool_calls":[{"index":0,"function":{"name":"book_get",'
            '"arguments":"1}"}}]},"finish_reason":"tool_calls"}],'
            '"usage":{"prompt_tokens":2,"completion_tokens":3,"total_tokens":5}}\n\n'
            "data: [DONE]\n\n"
        )
        response = evaluator.collect_streaming_chat_response(BytesIO(payload.encode("utf-8")))
        message = response["choices"][0]["message"]
        self.assertEqual("想完", message["reasoning_content"])
        self.assertEqual("bookshelf_book_get", message["tool_calls"][0]["function"]["name"])
        self.assertEqual('{"a":1}', message["tool_calls"][0]["function"]["arguments"])
        self.assertEqual(5, response["usage"]["total_tokens"])

    def test_read_text_supports_utf8_bom_and_gb18030(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            utf8 = root / "utf8.txt"
            gb = root / "gb.txt"
            utf8.write_bytes("书名：测试".encode("utf-8-sig"))
            gb.write_bytes("作者：甲".encode("gb18030"))

            self.assertEqual("书名：测试", evaluator.read_text(utf8))
            self.assertEqual("作者：甲", evaluator.read_text(gb))

    def test_split_chapters_and_metadata_keep_physical_order(self) -> None:
        text = """书名：样书
作者：作者甲

第一章 开端
正文一
第二回 继续
正文二
第三回合，开始
仍属于第二回正文
"""
        preamble, chapters = evaluator.split_chapters(text)
        name, author, synopsis = evaluator.infer_book_metadata(Path("fallback.txt"), preamble)

        self.assertEqual("样书", name)
        self.assertEqual("作者甲", author)
        self.assertIn("书名：样书", synopsis)
        self.assertEqual([0, 1], [chapter.index for chapter in chapters])
        self.assertEqual(["一", "二"], [chapter.source_number for chapter in chapters])
        self.assertIn("第三回合，开始", chapters[1].content)

    def test_split_chapters_supports_narrow_if_timeline_headings(self) -> None:
        text = """第733章 大结局
正文结局
IF·0·1
支线一
IF.1.63
支线结局
ordinary IF statement
仍属于支线结局
"""

        _, chapters = evaluator.split_chapters(text)

        self.assertEqual(
            ["第733章 大结局", "IF·0·1", "IF.1.63"],
            [chapter.title for chapter in chapters],
        )
        self.assertEqual(
            ["733", "IF·0·1", "IF.1.63"],
            [chapter.source_number for chapter in chapters],
        )
        self.assertIn("ordinary IF statement", chapters[-1].content)

    def test_completed_status_survives_if_chapters_after_main_ending(self) -> None:
        _, chapters = evaluator.split_chapters(
            """第733章 大结局
正文结局
IF·0·1
支线正文
"""
        )

        self.assertEqual("completed", evaluator.infer_book_status("auto", chapters, ""))

    def test_fast_scan_ranges_are_fixed_physical_head_and_tail(self) -> None:
        self.assertEqual([], evaluator.fast_scan_ranges(0))
        self.assertEqual([(0, 1)], evaluator.fast_scan_ranges(1))
        self.assertEqual([(0, 19)], evaluator.fast_scan_ranges(19))
        self.assertEqual([(0, 10), (10, 20)], evaluator.fast_scan_ranges(20))
        self.assertEqual([(0, 10), (90, 100)], evaluator.fast_scan_ranges(100))
        self.assertEqual([(0, 3), (97, 100)], evaluator.fast_scan_ranges(100, 3))
        self.assertEqual([(0, 5)], evaluator.fast_scan_ranges(5, 3))

    def test_snippet_receipt_is_navigation_not_full_text_coverage(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            simulator = self.simulator(Path(directory), total=6)
            snippet = simulator.execute(
                tool_name="bookshelf_chapter_snippets_get",
                arguments={"ranges": [{"start": 1, "end": 4}]},
                tool_call_id="snippet-navigation",
            )
            full = simulator.execute(
                tool_name="bookshelf_chapter_content_get",
                arguments={"chapter_index": 2, "char_limit": 0},
                tool_call_id="full-content",
            )

            self.assertTrue(snippet["receipt"]["complete"])
            coverage = evaluator.evidence_coverage(simulator.receipts)
            self.assertEqual([1, 2, 3], coverage["navigation_indexes"])
            self.assertEqual([2], coverage["complete_read_indexes"])

    def test_multifile_skill_package_hash_and_path_safety(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "references").mkdir()
            (root / "SKILL.md").write_text(
                "---\nid: demo\nname: demo\ndescription: test\n---\n[Quick](references/quick.md)",
                encoding="utf-8",
            )
            (root / "references" / "quick.md").write_text("quick rules", encoding="utf-8")
            package = evaluator.load_skill_package(root)

            self.assertEqual(("SKILL.md", "references/quick.md"), package.files)
            self.assertEqual("[Quick](references/quick.md)", package.read())
            self.assertEqual("quick rules", package.read("references/quick.md"))
            self.assertEqual({"references/quick.md"}, evaluator.markdown_resource_links(package.read()))
            self.assertEqual(64, len(package.package_hash))
            with self.assertRaises(ValueError):
                package.read("../outside.md")

    def test_multifile_skill_loads_output_contract_and_uses_agent_memory(self) -> None:
        package = evaluator.load_skill_package(
            evaluator.ROOT / "scripts/fixtures/skills/book_scan_multifile_light"
        )

        output = package.read_declaration("output_contract")
        self.assertEqual(1, output["max_retries"])
        self.assertIn("agent_memory_batch_upsert", package.read("quick_scan.md"))
        self.assertIn("不得维护第二套扫书档案", package.read("quick_scan.md"))

    def test_use_skill_requires_entry_then_linked_resource(self) -> None:
        package = evaluator.load_skill_package(
            evaluator.ROOT / "scripts/fixtures/skills/book_scan_multifile_same"
        )
        with tempfile.TemporaryDirectory() as directory:
            simulator = self.simulator(Path(directory), total=25, skill_package=package)
            denied = simulator.execute(
                tool_name="use_skill",
                arguments={"name": package.name, "path": "quick_scan.md"},
                tool_call_id="resource-before-entry",
            )
            entry = simulator.execute(
                tool_name="use_skill",
                arguments={"name": package.name},
                tool_call_id="entry",
            )
            resource = simulator.execute(
                tool_name="use_skill",
                arguments={"name": package.name, "path": "quick_scan.md"},
                tool_call_id="resource-after-entry",
            )

            self.assertFalse(denied["receipt"]["success"])
            self.assertTrue(entry["receipt"]["success"])
            self.assertTrue(resource["receipt"]["success"])
            self.assertEqual(["SKILL.md", "quick_scan.md"], simulator.loaded_skill_resources)

    def test_loaded_skill_resources_are_rebuilt_from_successful_trace(self) -> None:
        trace = [
            {
                "tool_name": "use_skill",
                "arguments": {"name": "book_scan"},
                "receipt": {"success": True},
            },
            {
                "tool_name": "use_skill",
                "arguments": {"name": "book_scan", "path": "quick_scan.md"},
                "receipt": {"success": True},
            },
            {
                "tool_name": "use_skill",
                "arguments": {"name": "book_scan", "path": "full_scan.md"},
                "receipt": {"success": False},
            },
        ]

        self.assertEqual(
            ["SKILL.md", "quick_scan.md"],
            evaluator.loaded_skill_resources_from_trace(trace),
        )

    def test_skill_context_metrics_count_only_loaded_multifile_resources(self) -> None:
        package = evaluator.load_skill_package(
            evaluator.ROOT / "scripts/fixtures/skills/book_scan_multifile_same"
        )
        metrics = evaluator.skill_context_metrics(
            skill_body="",
            skill_package=package,
            loaded_resources=["SKILL.md", "quick_scan.md"],
        )

        self.assertEqual(0, metrics["initial_injected_content_chars"])
        self.assertEqual(
            len(package.entry_body) + len(package.read("quick_scan.md")),
            metrics["effective_skill_content_chars"],
        )
        self.assertNotIn("full_scan.md", metrics["loaded_resources"])

    def test_multifile_initial_context_contains_metadata_not_entry_body(self) -> None:
        package = evaluator.load_skill_package(
            evaluator.ROOT / "scripts/fixtures/skills/book_scan_multifile_same"
        )
        messages = evaluator.build_initial_messages(
            skill_body="",
            skill_mode="multifile",
            skill_metadata=package.metadata,
            skill_hash=package.package_hash,
            name="测试书",
            author="作者",
            work_key="测试书\n作者",
            status="ongoing",
            total=100,
            synopsis="简介",
            stage="orientation",
            target_chapters=100,
            enabled_skill_metadata=[
                {
                    "id": "book_scan_facts",
                    "name": "扫书事实取证",
                    "description": "中性事实",
                }
            ],
        )
        system = messages[0]["content"]

        self.assertIn("use_skill", system)
        self.assertIn(package.package_hash, system)
        self.assertIn("<name>book_scan_facts</name>", system)
        self.assertNotIn("当前请求命中某一路由后", system)
        tool_names = {
            item["function"]["name"]
            for item in evaluator.openai_tools(include_use_skill=True)
        }
        self.assertIn("use_skill", tool_names)

    def test_single_initial_context_contains_runtime_revision(self) -> None:
        messages = evaluator.build_initial_messages(
            skill_body="single body",
            skill_metadata={"id": "book_scan", "name": "AI 扫书", "version": "31"},
            skill_hash="abc123",
            name="测试书",
            author="",
            work_key="测试书",
            status="ongoing",
            total=100,
            synopsis="简介",
            stage="orientation",
            target_chapters=100,
        )

        self.assertIn('"runtime_revision":"book_scan@31@abc123"', messages[0]["content"])
        self.assertIn("single body", messages[0]["content"])

    def test_simulator_work_key_has_no_trailing_newline_without_author(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            simulator = evaluator.LocalMcpSimulator(
                book_path=Path(directory) / "book.txt",
                name="测试书",
                author="",
                synopsis="",
                book_status="ongoing",
                chapters=[evaluator.Chapter(0, "1", "第一章", "正文")],
                skill_revision="31",
                state_dir=Path(directory),
                conversation_id="test-no-author",
            )

            self.assertEqual("测试书", simulator.work_key)

    def test_real_skill_frontmatter_and_tool_catalog_are_loaded_without_private_delta(self) -> None:
        content = evaluator.SKILL_FILE.read_text(encoding="utf-8")
        metadata, body = evaluator.parse_frontmatter(content)
        tool_names = {item["function"]["name"] for item in evaluator.openai_tools()}

        self.assertEqual(evaluator.SKILL_FILE.parent, evaluator.DEFAULT_SKILL_PACKAGE)
        self.assertEqual("book_scan", metadata["id"])
        self.assertTrue(metadata["version"].isdigit())
        self.assertEqual("82", metadata["version"])
        self.assertIn("ai.memory", metadata.get("mcp_capabilities", ""))
        self.assertIn("bookshelf_text_window_get", tool_names)
        self.assertIn("agent_memory_batch_upsert", tool_names)
        self.assertNotIn("bookshelf_chapter_snippets_get", body)
        self.assertIn("只固定读取开头 10 章和结尾/最新 10 章", body)
        self.assertIn("快速定位正文读取最多两次", body)
        self.assertIn("禁止再调用任何正文读取工具", body)
        self.assertIn("保存记忆时复用本轮两次正文窗口的回执", body)
        self.assertIn("继续扫描只补连续正文范围", body)
        self.assertIn("固定文案、按钮、确认提示和后续交互由 App", body)
        self.assertIn("后宫文扫绿帽、送女、漏女、乱收、关系背刺", body)
        self.assertIn("支线压力、欲扬先抑、家庭矛盾、商战黑手", body)
        self.assertIn("破坏目标受众最在意的核心承诺", body)
        self.assertIn("真正让读者反感的机制", body)
        self.assertIn("严格 NTR、送女未命中，不等于没有关系雷", body)
        self.assertIn("男频关系洁癖风险", body)
        self.assertIn("破鞋感或被染指感", body)
        self.assertIn("绿帽感和普通虐主", body)
        self.assertIn("精神攻击", body)
        self.assertIn("没有确认回执前不要结束本轮", body)

        package = evaluator.load_skill_package(evaluator.SKILL_FILE.parent)
        package.read("interaction-policy.json")
        package.read("references/mcp_tools.md")
        package.read("references/sampling.md")
        package.read("references/reader_view.md")
        package.read("references/report_payload.md")
        package.read("references/risk_checklist.md")
        package.read("references/full-scan.md")
        package.read("references/interactions.md")
        personalization = package.read("references/personalization-contract.md")
        preferences = package.read("references/preferences.md")
        tag_index = package.read("references/reader-tags/index.md")
        selectable_tags = package.read("references/reader-tags/selectable-tags.md")
        risk_tags = package.read("references/reader-tags/risk-tags.md")
        analysis_tags = package.read("references/reader-tags/analysis-tags.md")
        facts = (evaluator.ROOT / "app/src/main/assets/skills/book_scan_facts/SKILL.md").read_text(
            encoding="utf-8"
        )
        report = (evaluator.ROOT / "app/src/main/assets/skills/book_scan_report/SKILL.md").read_text(
            encoding="utf-8"
        )
        facts_metadata, _ = evaluator.parse_frontmatter(facts)
        report_metadata, _ = evaluator.parse_frontmatter(report)
        self.assertEqual("8", facts_metadata["version"])
        self.assertEqual("39", report_metadata["version"])
        resource_links = evaluator.markdown_resource_links(body)
        self.assertIn("references/mcp_tools.md", resource_links)
        self.assertIn("references/sampling.md", resource_links)
        self.assertIn("references/reader_view.md", resource_links)
        self.assertIn("references/report_payload.md", resource_links)
        self.assertIn("references/risk_checklist.md", resource_links)
        self.assertIn("references/full-scan.md", resource_links)
        self.assertIn("references/interactions.md", resource_links)
        self.assertIn("66 个稳定作品标签", tag_index)
        self.assertIn("每次默认选择 3 个", tag_index)
        self.assertIn("不能生成长期偏好", tag_index)
        tag_line_re = re.compile(r"^- `([a-z][a-z0-9_.]+)`｜", re.MULTILINE)
        selectable_ids = tag_line_re.findall(selectable_tags)
        risk_ids = tag_line_re.findall(risk_tags)
        analysis_ids = tag_line_re.findall(analysis_tags)
        all_tag_ids = selectable_ids + risk_ids + analysis_ids
        self.assertEqual(43, len(selectable_ids))
        self.assertEqual(11, len(risk_ids))
        self.assertEqual(17, len(analysis_ids))
        self.assertEqual(71, len(all_tag_ids))
        self.assertEqual(len(all_tag_ids), len(set(all_tag_ids)))
        self.assertIn("setting.system_present", selectable_ids)
        self.assertIn("route.multi_partner", selectable_ids)
        self.assertIn("protagonist.system_driven", selectable_ids)
        self.assertIn("risk.forced_third_party_marriage", risk_ids)
        self.assertIn("quality.character_consistency_break", analysis_ids)
        self.assertIn("evidence_refs", facts)
        self.assertIn("window_bundle", facts)
        self.assertIn("每个正文回执只保存一条", facts)
        self.assertIn("首次快扫只调用一次", facts)
        self.assertIn("reader_experience", facts)
        self.assertIn("work_components", facts)
        self.assertIn("tag_hits", facts)
        self.assertIn("relationship_profile", facts)
        self.assertIn("content_intensity", facts)
        self.assertIn("risks", facts)
        self.assertIn("fact_schema_version=8", facts)
        self.assertIn("reader_tag_catalog_version=2", facts)
        self.assertIn("不得用空 `tag_hits` 绕过渐进偏好流程", facts)
        self.assertIn("同一本书面对不同读者时，本层事实必须完全一致", facts)
        self.assertIn('<book-report type="quick_scan_report">', report)
        self.assertIn('<interaction id="book_scan_reaction" />', report)
        self.assertIn("老读者拍板", report)
        self.assertIn("别看", report)
        self.assertIn("无同意亲密行为", report)
        self.assertIn("subjective-review", report)
        self.assertIn("男频关系洁癖风险", report)
        self.assertIn('<pressure index="2" label="略有压力">', report)
        self.assertIn('<risk level="high">', report)
        self.assertIn("后宫文扫绿帽、送女、漏女、乱收、关系背刺", report)
        self.assertIn("绿帽感和普通虐主", report)
        self.assertIn("精神攻击", report)
        self.assertIn("risk` 数量不硬限制", report)
        self.assertIn("开局就明确多女主后宫、结局多女共处、擦边荤梗密集，都不是 risk", report)
        self.assertIn("压抑指数沿用五级", report)
        self.assertIn("App 会渲染标题、卡片、固定提醒、按钮和后续交互", report)
        self.assertNotIn("## 主观锐评", report)
        self.assertNotIn("book_scan_review_work", body)
        self.assertIn("scope_type=global", personalization)
        self.assertIn("domain=reader_preference", personalization)
        self.assertIn('"dimension_answers"', personalization)
        self.assertIn('"route.no_romance|route.single_partner|route.multi_partner"', personalization)
        self.assertIn('"defense.god|defense.heavy|defense.cloth|defense.low|defense.negative"', personalization)
        self.assertIn('"tag_stances"', personalization)
        self.assertIn(
            '"fit_level": "high_match|match|tradeoff|mismatch|avoid|insufficient"',
            personalization,
        )
        self.assertIn("未读中段的人物成长", personalization)
        self.assertIn("reader_preference_adaptive_tags", preferences)
        self.assertIn("multi_tag_stance", preferences)
        self.assertIn('"map_field": "tag_stances"', preferences)
        self.assertIn('"profile_id": "default"', preferences)
        self.assertIn("明确选择", preferences)
        self.assertIn("先读取当前书首尾样本", preferences)
        interactions = [
            json.loads(match.group(0).split("\n", 1)[1].rsplit("\n```", 1)[0])
            for match in evaluator.INTERACTION_BLOCK_RE.finditer(preferences)
        ]
        self.assertEqual(1, len(interactions))
        self.assertEqual("reader_preference_adaptive_tags", interactions[0]["id"])
        self.assertEqual("multi_tag_stance", interactions[0]["type"])

        post_scan_interactions = package.read("references/interactions.md")
        self.assertIn("只有需要挑选继续了解点或劝退原因时才读取该 JSON", post_scan_interactions)
        self.assertIn("禁止根据中文含义拼接 `feedback.*`", post_scan_interactions)

    def test_personalization_contract_accepts_sparse_v2_and_stable_fact_shapes(self) -> None:
        global_profile = {
            "schema_version": 2,
            "profile_id": "default",
            "dimension_answers": {
                "relationship_routes": {
                    "answer_type": "multi",
                    "values": ["route.multi_partner"],
                    "source_kind": "ui_choice",
                    "source_text": "后宫可以",
                    "confirmed_by_user": True,
                },
                "defense_level": {
                    "answer_type": "single",
                    "values": ["defense.cloth"],
                    "source_kind": "ui_choice",
                    "source_text": "布甲",
                    "confirmed_by_user": True,
                },
            },
            "tag_stances": {
                "setting.system_present": {
                    "stance": "avoid",
                    "source_kind": "ui_choice",
                    "source_text": "不喜欢有系统",
                    "confirmed_by_user": True,
                }
            },
            "custom_rules": [],
        }
        category_profile = {
            "schema_version": 1,
            "profile_id": "category:douluo_fanfiction",
            "category_id": "douluo_fanfiction",
            "category_label": "斗罗大陆同人",
            "rules": [
                {
                    "target": "original_protagonist_faction",
                    "dimension": "action",
                    "stance": "avoid",
                    "expected_outcome": "不无底线追随",
                    "priority": "high",
                }
            ],
            "source_text": "不喜欢主角无底线追随原著主角",
            "confirmed_by_user": True,
        }
        risk = {
            "schema_version": 1,
            "risk_kind": "risk.third_party_sexual_violation",
            "base_level": "god_risk",
            "evidence_status": "confirmed",
            "relationship_context": {"before": "盟友", "commitments": [], "consent_context": "拒绝"},
            "reader_consequence": {
                "affected": ["角色甲"],
                "irreversible_outcome": "",
                "relationship_impact": "信任破裂",
            },
            "evidence_refs": [{"chapter_index": 9, "content_sha256": "abc"}],
        }
        fit = {
            "schema_version": 1,
            "fit_level": "tradeoff",
            "match_evidence": ["relationship_route=harem 与已确认多女主路线一致"],
            "conflict_evidence": ["已确认严重关系风险"],
            "decisive_unknowns": ["中段是否完成关系修复"],
            "confidence": "medium",
            "coverage_boundary": "开头 10 章与末尾 10 章",
        }

        self.assertEqual([], evaluator.personalization_contract_violations("global_profile", global_profile))
        self.assertEqual([], evaluator.personalization_contract_violations("category_profile", category_profile))
        self.assertEqual([], evaluator.personalization_contract_violations("risk", risk))
        self.assertEqual([], evaluator.personalization_contract_violations("fit_assessment", fit))
        self.assertEqual(
            [],
            evaluator.personalization_contract_violations(
                "tag_hit",
                {
                    "tag_id": "setting.system_present",
                    "detection_status": "confirmed",
                    "intensity": "high",
                    "evidence_refs": [{"chapter_index": 0}],
                },
            ),
        )

        empty_profile = {
            "schema_version": 2,
            "profile_id": "default",
            "dimension_answers": {},
            "tag_stances": {},
            "custom_rules": [],
        }
        self.assertEqual(
            [], evaluator.personalization_contract_violations("global_profile", empty_profile)
        )

    def test_personalization_contract_rejects_unconfirmed_or_overloaded_values(self) -> None:
        violations = evaluator.personalization_contract_violations(
            "global_profile",
            {
                "schema_version": 1,
                "profile_id": "serious_novel",
                "dimension_answers": [],
                "tag_stances": {"bad id": {"stance": "maybe"}},
                "custom_rules": "not-a-list",
            },
        )

        self.assertIn("global_profile profile_id must be default", violations)
        self.assertIn("global_profile schema_version must be 2", violations)
        self.assertIn("dimension_answers must be an object", violations)
        self.assertIn("tag_stances contains invalid stable id", "\n".join(violations))
        self.assertIn("custom_rules must be a list", violations)

    def test_reader_profile_fixtures_are_sparse_v2(self) -> None:
        fixture_dir = evaluator.ROOT / "scripts/fixtures/reader_profiles"
        for path in fixture_dir.glob("*.json"):
            with self.subTest(path=path.name):
                profile = evaluator.load_reader_profile(path)
                self.assertEqual(2, profile["schema_version"])
                self.assertIsInstance(profile["dimension_answers"], dict)
                self.assertIsInstance(profile["tag_stances"], dict)
                self.assertIsInstance(profile["custom_rules"], list)

    def test_adaptive_preference_interaction_uses_declarative_memory_patch(self) -> None:
        package = evaluator.load_skill_package(evaluator.SKILL_FILE.parent)
        preferences = package.read("references/preferences.md")
        interaction = evaluator.interaction_blocks(preferences)[0]

        self.assertEqual("multi_tag_stance", interaction["type"])
        self.assertEqual(
            {"like", "neutral", "avoid"},
            {option["value"] for option in interaction["options"]},
        )
        self.assertEqual(
            evaluator.PREFERENCE_OPTION_LABELS,
            {option["value"]: option["label"] for option in interaction["options"]},
        )
        self.assertIn("id", interaction["items"][0])
        self.assertNotIn("options", interaction["items"][0])
        self.assertEqual("json_map_merge", interaction["memory_patch"]["operation"])
        self.assertEqual("tag_stances", interaction["memory_patch"]["map_field"])
        self.assertEqual("stance", interaction["memory_patch"]["value_field"])
        self.assertEqual("replace", interaction["memory_patch"]["on_base_mismatch"])

    def test_adaptive_preference_interaction_rejects_low_detectability_candidate(self) -> None:
        package = evaluator.load_skill_package(evaluator.SKILL_FILE.parent)
        interaction = evaluator.interaction_blocks(package.read("references/preferences.md"))[0]
        interaction["items"] = [
            {
                "id": "protagonist.indecisive",
                "label": "优柔寡断",
                "description": "首尾样本里出现过一次犹豫。",
            }
        ]

        violations = evaluator.multi_tag_stance_contract_violations(
            interaction,
            reader_profile=None,
            confirmed_tag_ids={"protagonist.indecisive"},
        )

        self.assertTrue(any("not selectable" in item for item in violations))

    def test_adaptive_preference_interaction_rejects_negative_judgment_even_when_detectable(self) -> None:
        package = evaluator.load_skill_package(evaluator.SKILL_FILE.parent)
        interaction = evaluator.interaction_blocks(package.read("references/preferences.md"))[0]
        for tag_id in (
            "protagonist.passive_humiliation",
            "protagonist.romantic_doormat",
            "protagonist.moral_bottomless",
        ):
            with self.subTest(tag_id=tag_id):
                interaction["items"] = [
                    {
                        "id": tag_id,
                        "label": "负面判断",
                        "description": "不应进入偏好卡。",
                    }
                ]
                violations = evaluator.multi_tag_stance_contract_violations(
                    interaction,
                    reader_profile=None,
                    confirmed_tag_ids={tag_id},
                )
                self.assertTrue(any("not selectable" in item for item in violations))

    def test_adaptive_preference_interaction_rejects_every_always_warn_risk(self) -> None:
        package = evaluator.load_skill_package(evaluator.SKILL_FILE.parent)
        interaction = evaluator.interaction_blocks(package.read("references/preferences.md"))[0]
        for tag_id in evaluator.reader_tag_ids("risk-tags.md"):
            with self.subTest(tag_id=tag_id):
                interaction["items"] = [
                    {
                        "id": tag_id,
                        "label": "严重风险",
                        "description": "只能警告，不能询问喜好。",
                    }
                ]
                violations = evaluator.multi_tag_stance_contract_violations(
                    interaction,
                    reader_profile=None,
                    confirmed_tag_ids={tag_id},
                )
                self.assertTrue(any("not selectable" in item for item in violations))

    def test_confirmed_multi_partner_route_is_a_neutral_question_candidate(self) -> None:
        memories = [
            {
                "domain": "book_scan",
                "memory_type": "window_bundle",
                "data": {
                    "tag_hits": [],
                    "relationship_profile": {"structure": "multi"},
                },
            }
        ]

        confirmed = evaluator.confirmed_fact_tag_ids(memories)

        self.assertIn("route.multi_partner", confirmed)
        self.assertEqual(
            {"route.multi_partner"},
            evaluator.eligible_unknown_confirmed_tag_ids(confirmed, None),
        )

    def test_missing_question_policy_fails_closed(self) -> None:
        policies = evaluator.reader_tag_question_policies("selectable-tags.md")
        detectability = evaluator.reader_tag_detectability("selectable-tags.md")

        self.assertEqual(set(detectability), set(policies))

    def test_host_interaction_policy_matches_neutral_quick_question_catalog(self) -> None:
        policy = json.loads(
            (
                evaluator.ROOT
                / "app/src/main/assets/skills/book_scan/interaction-policy.json"
            ).read_text(encoding="utf-8")
        )
        policies = evaluator.reader_tag_question_policies("selectable-tags.md")
        detectability = evaluator.reader_tag_detectability("selectable-tags.md")
        catalog_text = (
            evaluator.ROOT
            / "app/src/main/assets/skills/book_scan/references/reader-tags/selectable-tags.md"
        ).read_text(encoding="utf-8")
        catalog_copy: dict[str, dict[str, str]] = {}
        for line in catalog_text.splitlines():
            match = re.match(
                r"^- `([^`]+)`｜([^｜]+)｜([^｜]+)｜([^｜]+)｜([^｜]+)｜",
                line,
            )
            if not match:
                continue
            tag_id, label, _detectability, _question_policy, definition = match.groups()
            catalog_copy[tag_id] = {
                "label": label.strip(),
                "description": definition.strip(),
            }
        expected_ids = {
            tag_id
            for tag_id, question_policy in policies.items()
            if question_policy == "selection_impact"
            and detectability.get(tag_id) in {"high", "medium"}
        }
        rule = policy["interactions"]["reader_preference_adaptive_tags"]

        self.assertEqual(2, policy["schema_version"])
        self.assertEqual(expected_ids, set(rule["allowed_items"]))
        self.assertEqual(
            {tag_id: catalog_copy[tag_id] for tag_id in expected_ids},
            rule["allowed_items"],
        )
        self.assertEqual(
            evaluator.PREFERENCE_OPTION_LABELS,
            {option["value"]: option["label"] for option in rule["options"]},
        )
        self.assertEqual("reader_preference", rule["memory_patch"]["domain"])
        self.assertEqual("global_profile", rule["memory_patch"]["memory_type"])

    def test_orientation_scans_without_reader_profile(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            simulator = self.simulator(Path(directory), total=25)
            trace = [
                simulator.execute(
                    tool_name="bookshelf_text_window_get",
                    arguments={"start_chapter_index": 0, "chapter_count": 10, "char_limit": 0},
                    tool_call_id="read-head",
                )
            ]
            trace.append(self.save_memories(simulator, suffix="head", covered_count=10))
            trace.append(
                simulator.execute(
                    tool_name="bookshelf_text_window_get",
                    arguments={"start_chapter_index": 15, "chapter_count": 10, "char_limit": 0},
                    tool_call_id="read-tail",
                )
            )
            trace.append(self.save_memories(simulator, suffix="tail", covered_count=20))
            report = """## 适读结论
> [!IMPORTANT] 🎯 当前判断
> 当前还没有你的阅读偏好；从首尾样本看，这是一部轻松的都市重生经营文，适合想看校园创业和明确多女主走向的读者。

## 基础信息
- **书籍**：测试书｜测试作者
- **状态**：已完结｜25 章
- **覆盖**：1～10、16～25；11～15 未读

## 作品速览
- **分类流派：** 都市、校园、重生
- **核心元素：** 校园创业、多女主

## 代入与关系
- **代入视角：** 男主创业成长
- **关系走向：** 多女主，结尾关系稳定

## 阅读感受
- **首尾样本氛围：** 轻松，主要压力可控

> [!WARNING] 🔎 扫描边界
> 以上只依据开头与结尾样本；中段人物成长、剧情质量及隐藏风险仍需继续扫描确认。

```legado-interaction
{"id":"book_scan_reaction"}
```
"""
            run = {
                "status": "completed",
                "stage": "orientation",
                "profile_flow": "none",
                "skill": {"id": "book_scan", "version": "55"},
                "book": {"name": "测试书"},
                "messages": [{"role": "assistant", "content": report}],
                "tool_trace": trace,
                "usage": {},
            }

            result = evaluator.evaluate_run(
                run=run,
                simulator=simulator,
                expected_fast_ranges=evaluator.fast_scan_ranges(25),
            )

            self.assertEqual([], result["protocol_warnings"])
            self.assertEqual("none", result["profile_flow"])
            self.assertEqual(0, result["reader_preference_memory"]["profile_write_count"])
            self.assertIsNone(result["reader_profile"])

    def test_orientation_rejects_model_created_empty_reader_profile(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            simulator = self.simulator(Path(directory), total=25)
            profile_write = simulator.execute(
                tool_name="agent_memory_batch_upsert",
                arguments={
                    "items": [
                        {
                            "scope_type": "global",
                            "scope_key": "default",
                            "domain": "reader_preference",
                            "memory_type": "global_profile",
                            "title": "默认阅读偏好",
                            "content": "空档案",
                            "data": {
                                "schema_version": 2,
                                "profile_id": "default",
                                "dimension_answers": {},
                                "tag_stances": {},
                                "custom_rules": [],
                            },
                            "tags": ["reader_preference"],
                            "confidence": 1,
                            "source": "ai",
                            "status": "active",
                        }
                    ]
                },
                tool_call_id="model-created-empty-profile",
            )
            self.assertTrue(profile_write["receipt"]["success"])
            run = {
                "status": "completed",
                "stage": "audit",
                "profile_flow": "none",
                "skill": {"id": "book_scan", "version": "55"},
                "book": {"name": "测试书"},
                "messages": [{"role": "assistant", "content": "检查" * 150}],
                "tool_trace": [profile_write],
                "usage": {},
            }

            result = evaluator.evaluate_run(
                run=run,
                simulator=simulator,
                expected_fast_ranges=[],
            )

            self.assertTrue(
                any(
                    "must not create or update global_profile" in warning
                    for warning in result["protocol_warnings"]
                )
            )

    def test_personalized_report_contract_accepts_dynamic_report_and_reaction_card(self) -> None:
        profile = {
            "schema_version": 2,
            "profile_id": "default",
            "dimension_answers": {},
            "tag_stances": {},
            "custom_rules": [],
        }
        report = """## 适读结论
> [!IMPORTANT] 🎯 当前判断
> 从首尾样本看，这是有系统的都市重生经营文；你还没表态是否喜欢系统，目前只能给出一般受众建议。

## 基础信息
- **书籍**：测试书｜测试作者
- **状态**：已完结｜24 章
- **覆盖**：1～10、15～24；11～14 未读

## 作品速览
- **分类流派：** 都市、校园、重生
- **核心元素：** 系统、经营

## 代入与关系
- **代入视角：** 男主成长
- **关系走向：** 首尾样本未见明显关系冲突

## 阅读感受
- **首尾样本氛围：** 轻松

> [!WARNING] 🔎 扫描边界
> 以上只依据开头与结尾样本；中段人物成长、剧情质量及隐藏风险仍需继续扫描确认。

```legado-interaction
{"id":"book_scan_reaction"}
```
"""

        self.assertEqual(
            [],
            evaluator.personalized_report_contract_violations(
                report,
                profile,
                confirmed_tag_ids={"setting.system_present"},
            ),
        )

    def test_personalized_report_contract_rejects_legacy_toxic_report(self) -> None:
        profile = None
        report = """## 主观锐评
| 项目 | 结论 |
|---|---|
| 关系 | 后宫多女主 |
"""

        violations = "\n".join(
            evaluator.personalized_report_contract_violations(report, profile)
        )

        self.assertIn("must start with ## 适读结论", violations)
        self.assertIn("forbidden heading", violations)
        self.assertIn("Markdown table", violations)

    def test_personalized_report_contract_rejects_direct_next_interaction(self) -> None:
        report = """## 适读结论
> [!IMPORTANT] 🎯 当前判断
> 首尾样本持续黑暗压迫，是否适合取决于读者对重口虐文的接受度。

## 基础信息
- **扫描：** 快速定位

## 作品速览
- **核心元素：** 黑暗虐恋

## 代入与关系
- **关系基调：** 高压虐恋

## 阅读感受
- **氛围：** 高压、低缓解

> [!WARNING] 🔎 扫描边界
> 以上只依据开头与结尾样本；中段人物成长、剧情质量及隐藏风险仍需继续扫描确认。

```legado-interaction
{"version":1,"id":"book_scan_next","type":"actions","title":"继续查看","description":"继续查看。","options":[{"label":"继续扫描","value":"show_scan_targets"},{"label":"分析当前","value":"show_analysis_targets"}],"submit":{"label":"继续","prompt_template":"{{value}}"}}
```
"""

        violations = evaluator.personalized_report_contract_violations(
            report,
            None,
            confirmed_tag_ids={"tone.dark_cruel"},
        )

        self.assertTrue(any("must end with the book_scan_reaction" in item for item in violations))

    def test_window_bundle_rejects_evidence_from_another_window(self) -> None:
        violations = evaluator.window_bundle_evidence_range_violations(
            {
                "chapter_range": [28, 38],
                "tag_hits": [
                    {
                        "tag_id": "tone.dark_cruel",
                        "evidence_refs": [{"chapter_index": 0}],
                    }
                ],
                "risks": [
                    {
                        "risk_kind": "risk.core_love_interest_death",
                        "evidence_refs": [{"chapter_index": 37}],
                    }
                ],
            }
        )

        self.assertEqual(1, len(violations))
        self.assertIn("chapter_index=0", violations[0])
        self.assertIn("chapter_range=[28,38)", violations[0])

    def test_personalized_report_rejects_middle_inference_and_reversed_warning(self) -> None:
        report = """## 适读结论
> [!IMPORTANT] 🎯 当前判断
> 当前有明显取舍。

## 基础信息
- **扫描：** 快速定位

## 作品速览
- **走向：** 中段推测持续受虐

## 代入与关系
- **关系：** 一对一

## 已确认雷点
> [!WARNING] 核心角色濒死（已逆转）
> 末尾已经复活。

## 阅读感受
- **氛围：** 高压

事实包已确认更新至最新版本。

> [!WARNING] 🔎 扫描边界
> 以上只依据开头与结尾样本；中段人物成长、剧情质量及隐藏风险仍需继续扫描确认。

```legado-interaction
{"version":1,"id":"book_scan_next","type":"actions","title":"继续查看","description":"继续查看。","options":[{"label":"继续扫描","value":"show_scan_targets"},{"label":"分析当前","value":"show_analysis_targets"}],"submit":{"label":"继续","prompt_template":"{{value}}"}}
```
"""

        violations = evaluator.personalized_report_contract_violations(report)

        self.assertTrue(any("middle chapters unknown" in item for item in violations))
        self.assertTrue(any("confirmed or ongoing" in item for item in violations))
        self.assertTrue(any("internal workflow" in item for item in violations))

    def test_preference_rerender_requires_saved_state_delta_and_next_interaction(self) -> None:
        good = """## 推荐已更新
> [!NOTE] ✅ 已保存偏好
> - 黑暗残酷：会因此劝退

## 新结论
> [!IMPORTANT] 🎯 更新后的判断
> 这本书与你刚保存的偏好明显冲突，当前不推荐。

## 变化原因
- 首尾样本的高压低缓解与“会因此劝退”直接冲突。

> [!WARNING] 🔎 扫描边界
> 以上只依据开头与结尾样本；中段人物成长、剧情质量及隐藏风险仍需继续扫描确认。

```legado-interaction
{"version":1,"id":"book_scan_next","type":"actions","title":"继续查看","description":"继续读取更多正文可以降低中段未知。","options":[{"label":"继续扫描","value":"show_scan_targets"},{"label":"分析当前","value":"show_analysis_targets"}],"submit":{"label":"继续","prompt_template":"{{value}}"}}
```
"""
        bad = """已记录你的偏好。

是否需要我进一步分析，或者继续查看其他书籍？
"""

        self.assertEqual([], evaluator.preference_rerender_contract_violations(good))
        self.assertEqual(
            [],
            evaluator.preference_rerender_contract_violations(
                good,
                expected_saved_lines=["黑暗残酷：会因此劝退"],
            ),
        )
        wrong_echo = evaluator.preference_rerender_contract_violations(
            good,
            expected_saved_lines=["黑暗残酷：可以接受"],
        )
        self.assertTrue(any("echo saved selection" in item for item in wrong_echo))
        bad_violations = evaluator.preference_rerender_contract_violations(bad)
        self.assertTrue(any("must start" in item for item in bad_violations))
        self.assertTrue(any("exactly one interaction" in item for item in bad_violations))

    def test_skipped_preference_result_never_claims_a_saved_choice(self) -> None:
        skipped = """本次未保存阅读偏好，当前结论保持不变。

> [!WARNING] 🔎 扫描边界
> 以上只依据开头与结尾样本；中段人物成长、剧情质量及隐藏风险仍需继续扫描确认。

```legado-interaction
{"version":1,"id":"book_scan_next","type":"actions","title":"继续查看","description":"继续查看。","options":[{"label":"继续扫描","value":"show_scan_targets"},{"label":"分析当前","value":"show_analysis_targets"}],"submit":{"label":"继续","prompt_template":"{{value}}"}}
```
"""

        self.assertEqual(
            [],
            evaluator.preference_rerender_contract_violations(
                skipped,
                interaction_status="skipped",
            ),
        )

    def test_real_20260716_bad_export_remains_a_fixed_offline_regression(self) -> None:
        fixture = (
            evaluator.ROOT
            / "scripts/fixtures/book_scan_exports/chat-export-2026-07-16_02-17-58.bad.md"
        ).read_text(encoding="utf-8")
        initial = fixture.split("<!-- initial-report:start -->", 1)[1].split(
            "<!-- initial-report:end -->", 1
        )[0].strip()
        rerender = fixture.split("<!-- preference-result:start -->", 1)[1].split(
            "<!-- preference-result:end -->", 1
        )[0].strip()

        initial_violations = evaluator.personalized_report_contract_violations(
            initial,
            None,
            confirmed_tag_ids={
                "tone.dark_cruel",
                "protagonist.passive_humiliation",
            },
            confirmed_risk_ids=set(),
        )
        rerender_violations = evaluator.preference_rerender_contract_violations(rerender)

        self.assertTrue(any("middle chapters unknown" in item for item in initial_violations))
        self.assertTrue(any("book_scan_reaction" in item for item in initial_violations))
        self.assertTrue(any("host owns all UI copy" in item for item in initial_violations))
        self.assertTrue(any("obsolete or forbidden heading" in item for item in initial_violations))
        self.assertTrue(any("no confirmed or ongoing risk" in item for item in initial_violations))
        self.assertTrue(any("must start with ## 推荐已更新" in item for item in rerender_violations))
        self.assertTrue(any("exactly one interaction" in item for item in rerender_violations))

    def test_book_scan_discovers_fact_and_report_companion_skills(self) -> None:
        primary = evaluator.load_skill_package(evaluator.SKILL_FILE.parent)
        companions = evaluator.discover_companion_skill_packages(primary)
        self.assertEqual(
            ["book_scan_facts", "book_scan_report"],
            [package.metadata["id"] for package in companions],
        )
        with tempfile.TemporaryDirectory() as directory:
            simulator = self.simulator(
                Path(directory),
                total=24,
                skill_package=primary,
                additional_skill_packages=companions,
            )
            for package in (primary, *companions):
                loaded = simulator.execute(
                    tool_name="use_skill",
                    arguments={"name": package.metadata["id"]},
                    tool_call_id=f"load-{package.metadata['id']}",
                )
                self.assertTrue(loaded["receipt"]["success"])
                self.assertEqual(
                    package.name,
                    loaded["result"]["structuredContent"]["normalized_data"]["skill"],
                )

    def test_seeded_reader_profile_is_active_but_not_part_of_book_fact_fingerprint(self) -> None:
        profile = self.reader_profile_v2()
        with tempfile.TemporaryDirectory() as directory:
            simulator = self.simulator(Path(directory), total=24, reader_profile=profile)
            before = evaluator.book_fact_fingerprint(simulator.memories)
            simulator.memories.append(
                self.memory_item(
                    simulator,
                    "window_bundle",
                    "开头窗口",
                    {"window_role": "opening", "chapter_range": [0, 10]},
                )
            )
            after = evaluator.book_fact_fingerprint(simulator.memories)

            self.assertEqual(profile, simulator.active_reader_profile())
            self.assertNotEqual(before, after)

    def test_reusable_book_memories_filter_out_reader_profile(self) -> None:
        work_key = "测试书\n测试作者"
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            evaluator.write_json(
                root / "memories.json",
                [
                    {
                        "scope_type": "global",
                        "scope_key": "default",
                        "domain": "reader_preference",
                        "memory_type": "global_profile",
                    },
                    {
                        "scope_type": "book",
                        "scope_key": work_key,
                        "domain": "book_scan",
                        "memory_type": "manifest",
                    },
                    {
                        "scope_type": "book",
                        "scope_key": work_key,
                        "domain": "book_scan",
                        "memory_type": "window_bundle",
                    },
                ],
            )

            memories = evaluator.load_reusable_book_memories(root, work_key)

            self.assertEqual(["manifest", "window_bundle"], [m["memory_type"] for m in memories])

    def test_simulator_batch_upserts_and_searches_generic_agent_memories(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            simulator = self.simulator(Path(directory), total=25)
            write = simulator.execute(
                tool_name="agent_memory_batch_upsert",
                arguments={
                    "items": [
                        self.memory_item(simulator, "manifest", "档案索引", {"covered_count": 20}),
                        self.memory_item(
                            simulator,
                            "relationship",
                            "A与B",
                            {"pair": ["A", "B"], "stage": "close"},
                        ),
                    ]
                },
                tool_call_id="memory-write-1",
            )
            search = simulator.execute(
                tool_name="agent_memory_search",
                arguments={
                    "scope_type": "book",
                    "scope_key": simulator.work_key,
                    "domain": "book_scan",
                    "memory_type": "relationship",
                },
                tool_call_id="memory-search-1",
            )

            self.assertTrue(write["receipt"]["success"])
            self.assertEqual(2, len(simulator.memories))
            normalized = search["result"]["structuredContent"]["normalized_data"]
            self.assertEqual(1, normalized["total"])
            self.assertEqual(["A", "B"], normalized["memories"][0]["data"]["pair"])

    def test_simulator_memory_policy_allows_profile_and_rejects_broad_or_foreign_access(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            simulator = self.simulator(Path(directory), total=6)
            profile = simulator.execute(
                tool_name="agent_memory_upsert",
                arguments={
                    "scope_type": "global",
                    "scope_key": "default",
                    "domain": "reader_preference",
                    "memory_type": "global_profile",
                    "title": "默认阅读偏好",
                    "content": "后宫可，布甲",
                    "data": self.reader_profile_v2(),
                },
                tool_call_id="preference-write",
            )
            broad_search = simulator.execute(
                tool_name="agent_memory_search",
                arguments={"scope_type": "book", "domain": "book_scan"},
                tool_call_id="broad-memory-search",
            )
            foreign_write = simulator.execute(
                tool_name="agent_memory_upsert",
                arguments={
                    "scope_type": "global",
                    "scope_key": "default",
                    "domain": "unrelated",
                    "title": "越权",
                    "content": "越权",
                },
                tool_call_id="foreign-memory-write",
            )

            self.assertTrue(profile["receipt"]["success"])
            self.assertFalse(broad_search["receipt"]["success"])
            self.assertIn("requires concrete", broad_search["result"]["error"])
            self.assertFalse(foreign_write["receipt"]["success"])
            self.assertIn("does not allow", foreign_write["result"]["error"])

    def test_memory_batch_rejects_structured_data_encoded_as_string_atomically(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            simulator = self.simulator(Path(directory), total=6)
            write = simulator.execute(
                tool_name="agent_memory_batch_upsert",
                arguments={
                    "items": [
                        self.memory_item(simulator, "manifest", "档案索引", {}),
                        self.memory_item(simulator, "event", "错误事件", "[]"),
                    ],
                },
                tool_call_id="memory-invalid-data",
            )

            self.assertFalse(write["receipt"]["success"])
            self.assertIn("JSON object", write["result"]["error"])
            self.assertEqual([], simulator.memories)

    def test_memory_write_acknowledges_complete_source_receipts(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            simulator = self.simulator(Path(directory), total=6)
            read = simulator.execute(
                tool_name="bookshelf_text_window_get",
                arguments={"start_chapter_index": 0, "chapter_count": 2, "char_limit": 0},
                tool_call_id="read-for-memory",
            )
            receipt_id = read["receipt"]["receipt_id"]
            write = simulator.execute(
                tool_name="agent_memory_batch_upsert",
                arguments={
                    "items": [self.memory_item(simulator, "manifest", "档案索引", {})],
                    "source_receipt_ids": [receipt_id],
                },
                tool_call_id="memory-with-source",
            )

            normalized = write["result"]["structuredContent"]["normalized_data"]
            self.assertEqual([receipt_id], normalized["acknowledged_receipt_ids"])
            self.assertTrue(simulator.receipts[receipt_id]["acknowledged_by"].startswith("agent_memory:"))
            self.assertEqual(
                [[0, 2]],
                evaluator.evidence_coverage(simulator.receipts)["memory_acknowledged_ranges"],
            )

    def test_memory_update_reuses_existing_id(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            simulator = self.simulator(Path(directory), total=25)
            created = simulator.execute(
                tool_name="agent_memory_upsert",
                arguments=self.memory_item(simulator, "manifest", "档案索引", {"covered_count": 10}),
                tool_call_id="memory-create",
            )
            memory = created["result"]["structuredContent"]["normalized_data"]["memory"]
            update = self.memory_item(simulator, "manifest", "档案索引", {"covered_count": 20})
            update["id"] = memory["id"]
            updated = simulator.execute(
                tool_name="agent_memory_upsert",
                arguments=update,
                tool_call_id="memory-update",
            )

            self.assertTrue(updated["receipt"]["success"])
            self.assertEqual(1, len(simulator.memories))
            self.assertEqual(20, simulator.memories[0]["data"]["covered_count"])

    def test_evaluation_checks_fast_scan_memory_and_visible_report(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            simulator = self.simulator(Path(directory), total=25)
            trace = []
            first = simulator.execute(
                tool_name="bookshelf_text_window_get",
                arguments={"start_chapter_index": 0, "chapter_count": 10, "char_limit": 0},
                tool_call_id="read-head",
            )
            trace.append(first)
            trace.append(self.save_memories(simulator, suffix="head", covered_count=10))
            tail = simulator.execute(
                tool_name="bookshelf_text_window_get",
                arguments={"start_chapter_index": 15, "chapter_count": 10, "char_limit": 0},
                tool_call_id="read-tail",
            )
            trace.append(tail)
            trace.append(self.save_memories(simulator, suffix="tail", covered_count=20))
            report = (
                "作品基本信息与整体风格已经建立。主角人物画像和核心关系已按双向状态整理。"
                "阅读体验与压抑程度目前属于中等，风险结论只限于已读范围。"
                "扫描覆盖为物理开头十章和末尾十章，未覆盖中段，因此这里只是快速定位边界。"
            ) * 3
            run = {
                "status": "completed",
                "stage": "orientation",
                "skill": {"id": "book_scan", "version": "30"},
                "book": {"name": "测试书"},
                "messages": [
                    {"role": "assistant", "content": report},
                    {
                        "role": "assistant",
                        "content": "```legado-interaction\n{\"id\":\"next\"}\n```",
                    },
                ],
                "tool_trace": trace,
                "usage": {"prompt_tokens": 10, "completion_tokens": 5, "total_tokens": 15},
            }

            result = evaluator.evaluate_run(
                run=run,
                simulator=simulator,
                expected_fast_ranges=evaluator.fast_scan_ranges(25),
            )

            self.assertEqual([], result["protocol_warnings"])
            self.assertEqual([[0, 10], [15, 25]], result["actual_text_window_ranges"])
            self.assertEqual(report, result["visible_report"])
            self.assertEqual(2, result["memory_artifact"]["write_count"])
            self.assertEqual(1, result["memory_artifact"]["structure_metrics"]["manifest"])
            self.assertEqual(2, result["memory_artifact"]["structure_metrics"]["window"])

    def test_evaluation_warns_without_repairing_wrong_range_or_private_output(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            simulator = self.simulator(Path(directory), total=25)
            read = simulator.execute(
                tool_name="bookshelf_text_window_get",
                arguments={"start_chapter_index": 1, "chapter_count": 5, "char_limit": 0},
                tool_call_id="wrong-read",
            )
            raw = "```book_scan_delta\n{\"observed_chapters\":[2,3,4,5,6]}\n```"
            run = {
                "status": "completed",
                "stage": "orientation",
                "skill": {"id": "book_scan", "version": "30"},
                "book": {"name": "测试书"},
                "messages": [{"role": "assistant", "content": raw}],
                "tool_trace": [read],
                "usage": {},
            }

            result = evaluator.evaluate_run(
                run=run,
                simulator=simulator,
                expected_fast_ranges=evaluator.fast_scan_ranges(25),
            )

            warnings = "\n".join(result["protocol_warnings"])
            self.assertIn("fast_scan_ranges mismatch", warnings)
            self.assertIn("no AgentMemory write", warnings)
            self.assertIn("legacy private", warnings)
            self.assertEqual("", result["visible_report"])
            self.assertEqual([1, 2, 3, 4, 5], read["receipt"]["evidence"]["chapter_indexes"])

    def test_evaluation_warns_when_provider_tool_markers_leak(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            simulator = self.simulator(Path(directory), total=1)
            run = {
                "status": "completed",
                "stage": "orientation",
                "skill": {"id": "book_scan", "version": "pilot"},
                "book": {"name": "测试书"},
                "messages": [
                    {
                        "role": "assistant",
                        "content": "<｜DSML｜tool_calls>内部工具协议不应对用户可见" * 10,
                        "tool_calls": [{"id": "protocol", "function": {"name": "x"}}],
                    }
                ],
                "tool_trace": [],
                "usage": {},
            }

            result = evaluator.evaluate_run(
                run=run,
                simulator=simulator,
                expected_fast_ranges=evaluator.fast_scan_ranges(1),
            )

            self.assertTrue(
                any("provider tool-protocol markers" in item for item in result["transport_diagnostics"])
            )
            self.assertFalse(
                any("provider tool-protocol markers" in item for item in result["protocol_warnings"])
            )
            self.assertEqual("", result["visible_report"])

    def test_visible_report_ignores_tool_step_narration_and_provider_transport(self) -> None:
        report = "## 最终报告\n\n" + "人物关系、阅读情绪和扫描边界均已说明。" * 20
        messages = [
            {
                "role": "assistant",
                "content": "开始读取正文。",
                "tool_calls": [{"id": "read", "function": {"name": "read"}}],
            },
            {
                "role": "assistant",
                "content": "准备提交。<｜DSML｜tool_calls>" + "x" * 1000,
                "tool_calls": [{"id": "commit", "function": {"name": "commit"}}],
            },
            {"role": "assistant", "content": report},
        ]

        self.assertEqual([report], evaluator.collect_visible_segments(messages))

    def test_output_contract_rejects_markdown_table(self) -> None:
        package = evaluator.load_skill_package(
            evaluator.ROOT / "scripts/fixtures/skills/book_scan_multifile_light"
        )
        content = ("正文结论。" * 50) + "\n| 项目 | 结论 |\n|---|---|\n| 风险 | 高 |"

        violations = evaluator.output_contract_violations(content, package)

        self.assertTrue(any("Markdown tables" in item for item in violations))

    def test_memory_metrics_count_types_and_high_risks(self) -> None:
        metrics = evaluator.memory_structure_metrics(
            [
                {"memory_type": "manifest", "data": {}},
                {"memory_type": "event", "data": {"severity": "critical"}},
                {"memory_type": "event", "data": {"severity": "low"}},
                {"memory_type": "relationship", "data": {}},
            ]
        )

        self.assertEqual(1, metrics["manifest"])
        self.assertEqual(2, metrics["events"])
        self.assertEqual(1, metrics["critical_high_events"])
        self.assertEqual(1, metrics["relationships"])

    def test_resume_executes_archived_pending_tool_call_once(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            simulator = self.simulator(root, total=25)
            run_path = root / "run.json"
            run = {
                "step": 1,
                "messages": [
                    {
                        "role": "assistant",
                        "content": "",
                        "tool_calls": [
                            {
                                "id": "pending-read",
                                "type": "function",
                                "function": {
                                    "name": "bookshelf_text_window_get",
                                    "arguments": "{\"start_chapter_index\":0,\"chapter_count\":10,\"char_limit\":0}",
                                },
                            }
                        ],
                    }
                ],
                "tool_trace": [],
            }

            first = evaluator.execute_pending_tool_calls(
                run=run,
                simulator=simulator,
                run_path=run_path,
            )
            second = evaluator.execute_pending_tool_calls(
                run=run,
                simulator=simulator,
                run_path=run_path,
            )

            self.assertEqual(1, first)
            self.assertEqual(0, second)
            self.assertEqual("tool", run["messages"][-1]["role"])
            self.assertEqual("pending-read", run["messages"][-1]["tool_call_id"])
            self.assertEqual(1, len(run["tool_trace"]))
            self.assertTrue(run_path.is_file())

    def simulator(
        self,
        state_dir: Path,
        *,
        total: int,
        skill_package: evaluator.SkillPackage | None = None,
        additional_skill_packages: tuple[evaluator.SkillPackage, ...] | list[evaluator.SkillPackage] = (),
        reader_profile: dict | None = None,
    ) -> evaluator.LocalMcpSimulator:
        chapters = [
            evaluator.Chapter(
                index=index,
                source_number=str(index + 1),
                title=f"第{index + 1}章",
                content=f"第{index + 1}章正文" * 10,
            )
            for index in range(total)
        ]
        book_path = state_dir / "book.txt"
        book_path.write_text("fixture", encoding="utf-8")
        return evaluator.LocalMcpSimulator(
            book_path=book_path,
            name="测试书",
            author="测试作者",
            synopsis="测试简介",
            book_status="completed",
            chapters=chapters,
            skill_revision="30",
            state_dir=state_dir,
            conversation_id="conversation-test",
            skill_package=skill_package,
            additional_skill_packages=additional_skill_packages,
            reader_profile=reader_profile,
        )

    def reader_profile_v2(self) -> dict:
        return {
            "schema_version": 2,
            "profile_id": "default",
            "dimension_answers": {
                "relationship_routes": {
                    "answer_type": "multi",
                    "values": ["route.multi_partner"],
                    "source_kind": "ui_choice",
                    "source_text": "后宫可以",
                    "confirmed_by_user": True,
                },
                "defense_level": {
                    "answer_type": "single",
                    "values": ["defense.cloth"],
                    "source_kind": "ui_choice",
                    "source_text": "布甲",
                    "confirmed_by_user": True,
                },
            },
            "tag_stances": {},
            "custom_rules": [],
        }

    def memory_item(
        self,
        simulator: evaluator.LocalMcpSimulator,
        memory_type: str,
        title: str,
        data: object,
    ) -> dict:
        return {
            "scope_type": "book",
            "scope_key": simulator.work_key,
            "subject": "测试书（测试作者）",
            "domain": "book_scan",
            "memory_type": memory_type,
            "title": title,
            "content": f"{title}摘要",
            "data": data,
            "tags": ["book_scan", memory_type],
            "confidence": 1,
            "source": "ai",
            "status": "active",
        }

    def save_memories(
        self,
        simulator: evaluator.LocalMcpSimulator,
        *,
        suffix: str,
        covered_count: int,
    ) -> dict:
        existing = next(
            (item for item in simulator.memories if item.get("memory_type") == "manifest"),
            None,
        )
        manifest = self.memory_item(
            simulator,
            "manifest",
            "档案索引",
            {"covered_count": covered_count},
        )
        if existing:
            manifest["id"] = existing["id"]
        return simulator.execute(
            tool_name="agent_memory_batch_upsert",
            arguments={
                "items": [
                    manifest,
                    self.memory_item(
                        simulator,
                        "window_bundle",
                        f"{suffix}窗口",
                        {
                            "fact_schema_version": 8,
                            "reader_tag_catalog_version": 2,
                            "chapter_range": (
                                [0, min(10, len(simulator.chapters))]
                                if suffix == "head"
                                else [
                                    max(0, len(simulator.chapters) - 10),
                                    len(simulator.chapters),
                                ]
                            ),
                            "window_role": suffix,
                            "covered_count": covered_count,
                            "tag_hits": [],
                            "relationship_profile": {
                                "structure": "unclear",
                                "tone": "unclear",
                                "outcome": "unknown",
                                "commitment_state": "unknown",
                            },
                            "content_intensity": {},
                            "risks": [],
                        },
                    ),
                ],
            },
            tool_call_id=f"memory-{suffix}",
        )


if __name__ == "__main__":
    unittest.main()
