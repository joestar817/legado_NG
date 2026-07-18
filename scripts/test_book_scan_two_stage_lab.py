from __future__ import annotations

import unittest
from io import BytesIO
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest.mock import patch

from scripts import book_scan_eval as evaluator
from scripts import book_scan_two_stage_lab as two_stage


class BookScanTwoStageLabTest(unittest.TestCase):

    def chapters(self, total: int) -> list[evaluator.Chapter]:
        return [
            evaluator.Chapter(index=i, source_number=str(i + 1), title=f"第{i + 1}章", content="甲" * 1000)
            for i in range(total)
        ]

    def test_navigation_pack_omits_middle(self) -> None:
        pack = two_stage.navigation_pack(self.chapters(2), 100, 100)
        self.assertEqual(2, len(pack))
        self.assertEqual(100, len(pack[0]["head"]))
        self.assertEqual(100, len(pack[0]["tail"]))
        self.assertTrue(pack[0]["middle_omitted"])

    @patch("scripts.book_scan_two_stage_lab.evaluator.call_api_step")
    def test_auto_reasoning_matches_app_by_omitting_reasoning_parameters(self, call_api) -> None:
        call_api.return_value = {
            "choices": [{"message": {"content": "{}"}}],
            "usage": {"prompt_tokens": 1, "completion_tokens": 1, "total_tokens": 2},
        }
        call, _ = two_stage.call_model(
            api_url="https://example.invalid",
            api_key="key",
            model="model",
            messages=[],
            max_tokens=100,
            thinking=True,
            effort="auto",
            response_json=True,
            timeout=1,
        )
        self.assertNotIn("thinking", call["request"])
        self.assertNotIn("reasoning_effort", call["request"])

    def test_real_runs_default_to_streaming(self) -> None:
        args = two_stage.parse_args(["--book", "book.txt"])
        self.assertTrue(args.stream)

    @patch("scripts.book_scan_two_stage_lab.urllib.request.urlopen")
    def test_streaming_response_is_collected_as_normal_chat_response(self, urlopen) -> None:
        class FakeResponse:
            status = 200

            def __enter__(self):
                return self

            def __exit__(self, *_args):
                return False

            def __iter__(self):
                payload = (
                    'data: {"model":"grok","choices":[{"delta":{"content":"你"},"finish_reason":null}]}\n\n'
                    'data: {"model":"grok","choices":[{"delta":{"content":"好"},"finish_reason":"stop"}],'
                    '"usage":{"prompt_tokens":1,"completion_tokens":2,"total_tokens":3}}\n\n'
                    "data: [DONE]\n\n"
                )
                return iter(BytesIO(payload.encode("utf-8")))

        urlopen.return_value = FakeResponse()
        response = two_stage.call_streaming_api_step(
            api_url="https://example.invalid",
            api_key="key",
            body={"model": "grok", "messages": [], "stream": True},
            timeout=1,
            retries=0,
            retry_sleep=0,
        )
        self.assertEqual("你好", response["choices"][0]["message"]["content"])
        self.assertEqual("stop", response["choices"][0]["finish_reason"])
        self.assertEqual(3, response["usage"]["total_tokens"])

    def test_normalize_plan_rejects_invalid_and_duplicate_indexes(self) -> None:
        plan = two_stage.normalize_plan(
            {"suspect_chapters": [
                {"index": 3, "reason": "a"},
                {"index": 3, "reason": "duplicate"},
                {"index": 99, "reason": "outside"},
            ]},
            total=10,
            max_suspects=12,
        )
        self.assertEqual([3], [item["index"] for item in plan["suspect_chapters"]])

    def test_consolidation_protocol_only_returns_selected_chapter_ids(self) -> None:
        plan = two_stage.normalize_consolidation(
            {
                "selected": [
                    {"index": 8, "priority": "high", "reason": "关系转折"},
                    {"index": 8, "reason": "重复"},
                    {"index": 99, "reason": "越界"},
                ]
            },
            total=20,
            max_suspects=10,
        )
        self.assertEqual([8], [item["index"] for item in plan["suspect_chapters"]])

    def test_full_text_indexes_include_baseline_and_suspects(self) -> None:
        indexes = two_stage.full_text_indexes(
            20,
            {"suspect_chapters": [{"index": 8}, {"index": 18}]},
        )
        self.assertEqual([0, 1, 2, 17, 18, 19, 8], indexes)

    def test_quick_navigation_is_stratified_and_bounded(self) -> None:
        indexes = two_stage.quick_navigation_indexes(440, edge=5, middle=12)
        self.assertEqual(0, indexes[0])
        self.assertEqual(439, indexes[-1])
        self.assertLessEqual(len(indexes), 22)
        self.assertTrue(any(180 <= index <= 260 for index in indexes))

    def test_chunked_indexes_covers_every_chapter_once(self) -> None:
        batches = two_stage.chunked_indexes(125, 60)
        self.assertEqual([60, 60, 5], [len(batch) for batch in batches])
        self.assertEqual(list(range(125)), [index for batch in batches for index in batch])

    def test_compact_ranges_is_user_facing(self) -> None:
        self.assertEqual("第1—3、8、10—11章", two_stage.compact_ranges([0, 1, 2, 7, 9, 10]))

    def test_merge_and_entity_occurrence_tracking(self) -> None:
        chapters = [
            evaluator.Chapter(i, str(i + 1), f"第{i + 1}章", "沈未辰" if i in {2, 5, 8, 11, 14} else "")
            for i in range(15)
        ]
        merged = two_stage.merge_batch_plans([
            {
                "suspect_chapters": [{"index": 5, "priority": "high", "reason": "关系转折"}],
                "trace_entities": [{"name": "沈未辰", "priority": "high", "reason": "归宿未明"}],
                "open_threads": ["关系后果"],
                "unknowns": [],
            },
            {
                "suspect_chapters": [{"index": 11, "priority": "medium", "reason": "再次出现"}],
                "trace_entities": [{"name": "沈未辰", "priority": "medium", "reason": "结局待查"}],
                "open_threads": [],
                "unknowns": [],
            },
        ])
        self.assertEqual(2, merged["trace_entities"][0]["mentions"])
        occurrences = two_stage.entity_occurrence_indexes(chapters, merged["trace_entities"], per_entity=3)
        self.assertEqual(3, len(occurrences["沈未辰"]))
        self.assertEqual(2, occurrences["沈未辰"][0])
        self.assertEqual(14, occurrences["沈未辰"][-1])

    def test_host_finalize_owns_coverage_and_interaction(self) -> None:
        content = "```legado-book-report\n<book-report><verdict>cautious</verdict>"
        content += "<basic><book>测试</book></basic><unknown>中段未知</unknown></book-report>\n```"
        final = two_stage.host_finalize_report(content, [0, 1, 9], 10)
        self.assertIn("全书10章均已快速浏览首尾；完整核对第1—2、10章", final)
        self.assertIn('<interaction id="book_scan_reaction" />', final)

    def test_host_finalize_does_not_overstate_quick_navigation(self) -> None:
        content = "```legado-book-report\n<book-report><verdict>uncertain</verdict>"
        content += "<basic><book>测试</book></basic></book-report>\n```"
        final = two_stage.host_finalize_report(
            content,
            [0, 9],
            100,
            navigation_indexes=[0, 9, 50, 99],
        )
        self.assertIn("已快速浏览4/100章的首尾片段", final)
        self.assertNotIn("全书100章均已", final)

    def test_host_finalize_overwrites_objective_book_metadata(self) -> None:
        content = "```legado-book-report\n<book-report><verdict>try</verdict>"
        content += "<basic><book>猜错</book><word-count>15万字</word-count></basic>"
        content += "</book-report>\n```"
        final = two_stage.host_finalize_report(
            content,
            [0],
            1,
            metadata={"book": "实际书名", "word-count": "约4.2万字"},
        )
        self.assertIn("<book>实际书名</book>", final)
        self.assertIn("<word-count>约4.2万字</word-count>", final)
        self.assertNotIn("15万字", final)

    def test_report_checks_reject_whole_book_claim_and_empty_pressure(self) -> None:
        content = "```legado-book-report\n<book-report><verdict>cautious</verdict>"
        content += "<reading-feeling><pressure index=\"5\" /></reading-feeling>"
        content += "<risk><text>全文确认</text></risk></book-report>\n```"
        checks = two_stage.report_checks(content, [0, 1], 10)
        self.assertFalse(checks["passed"])
        self.assertEqual(2, len(checks["failures"]))

    def test_saved_state_can_be_reopened_without_model_call(self) -> None:
        report = "```legado-book-report\n<book-report><verdict>try</verdict></book-report>\n```"
        state = {
            "book": {"name": "测试"},
            "coverage": {"navigation_indexes": [0], "complete_indexes": [0]},
            "quick_report": report,
            "final_report": report,
            "interactions": [],
        }
        with TemporaryDirectory() as temporary:
            path = Path(temporary) / "state.json"
            path.write_text(__import__("json").dumps(state, ensure_ascii=False), encoding="utf-8")
            reopened = two_stage.verify_reopen_state(path)
        self.assertEqual("测试", reopened["book"]["name"])

    def test_attempt_log_keeps_retry_costs(self) -> None:
        call = {"response": {"choices": [{"finish_reason": "length"}]}}
        with TemporaryDirectory() as temporary:
            out = Path(temporary)
            two_stage.append_attempt(
                out,
                "batch_01_navigation",
                call,
                {"prompt_tokens": 10, "completion_tokens": 5, "total_tokens": 15},
            )
            attempts = two_stage.read_attempts(out / "attempts.jsonl")
        self.assertEqual("length", attempts[0]["finish_reason"])
        self.assertEqual(15, attempts[0]["usage"]["total_tokens"])

    def test_deep_report_prompt_uses_audience_baseline_and_ongoing_boundary(self) -> None:
        messages = two_stage.report_messages(
            name="测试",
            author="作者",
            status="ongoing",
            total=10,
            synopsis="简介",
            plan={},
            full_chapters=[],
            report_type="deep_scan_report",
        )
        system = messages[0]["content"]
        self.assertIn('type="deep_scan_report"', system)
        self.assertIn("明牌黑暗作品有伤亡也不能直接判劝退", system)
        self.assertIn("作品仍在连载时", system)
        payload = __import__("json").loads(messages[1]["content"])
        self.assertFalse(payload["evidence_scope"]["whole_book_complete"])
        self.assertTrue(payload["evidence_scope"]["navigation_is_snippet_only"])

    def test_bold_report_prompt_uses_reader_voice(self) -> None:
        messages = two_stage.report_messages(
            name="测试",
            author="作者",
            status="completed",
            total=10,
            synopsis="简介",
            plan={},
            full_chapters=[],
            report_style="bold",
        )
        system = messages[0]["content"]
        self.assertIn("你的判断可以有态度", system)
        self.assertIn("目标读者视角", system)
        self.assertIn("核心承诺", system)
        self.assertIn("像人话", system)
        self.assertNotIn("不是写论文", system)

    def test_parallel_workers_argument_is_opt_in(self) -> None:
        default_args = two_stage.parse_args(["--book", "book.txt"])
        self.assertEqual(1, default_args.parallel_workers)
        self.assertEqual("navigation", default_args.parallel_mode)
        parallel_args = two_stage.parse_args(["--book", "book.txt", "--parallel-workers", "3"])
        self.assertEqual(3, parallel_args.parallel_workers)
        raw_args = two_stage.parse_args([
            "--book", "book.txt",
            "--parallel-workers", "2",
            "--parallel-mode", "raw-chunks",
        ])
        self.assertEqual("raw-chunks", raw_args.parallel_mode)

    def test_raw_chunk_report_payload_marks_worker_findings_as_complete_text(self) -> None:
        messages = two_stage.report_messages(
            name="测试书",
            author="作者",
            status="completed",
            total=10,
            synopsis="简介",
            plan={
                "raw_chunk_findings": {
                    "covered_ranges": "第1—10章",
                    "risk_findings": [
                        {
                            "title": "关系雷",
                            "level": "high",
                            "chapters": [3],
                            "evidence": "发生了关系背刺",
                        }
                    ],
                }
            },
            full_chapters=[{"index": 2, "chapter": 3, "title": "三", "content": "正文"}],
        )
        payload = __import__("json").loads(messages[1]["content"])
        self.assertFalse(payload["evidence_scope"]["navigation_is_snippet_only"])
        self.assertTrue(payload["evidence_scope"]["raw_worker_findings_are_complete_text"])

    def test_host_finalize_raw_chunk_wording_does_not_say_snippet_navigation(self) -> None:
        content = "```legado-book-report\n<book-report><verdict>cautious</verdict>"
        content += "<basic><book>测试</book></basic></book-report>\n```"
        final = two_stage.host_finalize_report(
            content,
            [0, 1],
            4,
            raw_full_indexes=[0, 1, 2, 3],
        )
        self.assertIn("并行原文核对全书4章", final)
        self.assertNotIn("首尾片段", final)

    @patch("scripts.book_scan_two_stage_lab.call_model")
    def test_full_flow_parallel_batches_preserve_state_and_order(self, call_model) -> None:
        def fake_call_model(**kwargs):
            messages = kwargs["messages"]
            system = messages[0]["content"]
            response_json = kwargs["response_json"]
            if response_json:
                if "总排疑员" in system:
                    content = '{"selected":[{"index":2,"priority":"high","reason":"补关系因果"}]}'
                else:
                    payload = __import__("json").loads(messages[1]["content"])
                    index = payload["chapters"][0]["index"]
                    content = __import__("json").dumps(
                        {
                            "profile": {"genre": "测试"},
                            "suspect_chapters": [
                                {"index": index, "priority": "medium", "reason": "批次疑点"}
                            ],
                            "trace_entities": [
                                {"name": "沈未辰", "priority": "high", "reason": "关系线"}
                            ],
                            "open_threads": ["关系线待补"],
                            "unknowns": [],
                        },
                        ensure_ascii=False,
                    )
            else:
                content = (
                    "```legado-book-report\n"
                    "<book-report><verdict>try</verdict><headline>可以看</headline>"
                    "<basic><book>测试</book></basic>"
                    "<reading-feeling><pressure index=\"1\">低压</pressure></reading-feeling>"
                    "<unknown>中段仍有缺口</unknown></book-report>\n```"
                )
            response = {
                "choices": [{"finish_reason": "stop", "message": {"content": content}}],
                "usage": {"prompt_tokens": 1, "completion_tokens": 1, "total_tokens": 2},
            }
            return {"request": {"messages": messages}, "response": response}, response["usage"]

        call_model.side_effect = fake_call_model
        chapters = [
            evaluator.Chapter(index=i, source_number=str(i + 1), title=f"第{i + 1}章", content="沈未辰" + "甲" * 200)
            for i in range(6)
        ]
        with TemporaryDirectory() as temporary:
            out = Path(temporary)
            args = two_stage.parse_args([
                "--book", "book.txt",
                "--full-flow",
                "--batch-size", "2",
                "--parallel-workers", "2",
                "--out", str(out),
                "--name", "parallel",
            ])
            code = two_stage.run_full_flow(
                args=args,
                api_key="key",
                book_path=Path("book.txt"),
                text="测试正文",
                name="测试",
                author="作者",
                synopsis="简介",
                status="completed",
                chapters=chapters,
                out=out / "parallel",
                started=two_stage.time.time(),
            )
            summary = evaluator.read_json(out / "parallel" / "summary.json")
            batch_ranges = [
                evaluator.read_json(out / "parallel" / f"batch_{i:02d}_plan.json")["range"]
                for i in range(1, 4)
            ]
        self.assertEqual(0, code)
        self.assertEqual(2, summary["parallel_workers"])
        self.assertEqual(6, summary["navigation_count"])
        self.assertEqual(7, summary["calls"])
        self.assertTrue(summary["reopen_verified"])
        self.assertEqual([[0, 1], [2, 3], [4, 5]], batch_ranges)


if __name__ == "__main__":
    unittest.main()
