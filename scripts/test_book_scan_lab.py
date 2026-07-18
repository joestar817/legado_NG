from __future__ import annotations

import unittest

from scripts import book_scan_lab as lab


class BookScanLabTest(unittest.TestCase):

    def setUp(self) -> None:
        self.policy = lab.load_policy(
            lab.evaluator.DEFAULT_SKILL_PACKAGE / "interaction-policy.json"
        )

    def test_xml_interaction_is_hydrated_by_host_policy(self) -> None:
        reference = lab.parse_interaction(
            "```legado-interaction\n"
            "<legado-interaction><interaction id=\"book_scan_reaction\" /></legado-interaction>\n"
            "```"
        )
        self.assertIsNotNone(reference)
        hydrated = lab.hydrate_interaction(reference or {}, self.policy)
        self.assertEqual("接下来怎么处理这本书？", hydrated["title"])
        self.assertEqual("continue_scan", hydrated["options"][0]["value"])

    def test_xml_hyphenated_item_ids_are_not_silently_ignored(self) -> None:
        reference = lab.parse_interaction(
            "```legado-interaction\n"
            "<interaction id=\"book_scan_like_reasons\" "
            "item-ids=\"feedback.genre_setting,feedback.unknown\" />\n```"
        )
        self.assertEqual(
            ["feedback.genre_setting", "feedback.unknown"],
            (reference or {})["item_ids"],
        )
        with self.assertRaisesRegex(ValueError, "unknown item ids"):
            lab.hydrate_interaction(reference or {}, self.policy)

    def test_policy_selection_produces_internal_prompt_only_after_click(self) -> None:
        interaction = lab.hydrate_interaction(
            {"id": "book_scan_reaction", "item_ids": []}, self.policy
        )
        prompt, selection = lab.interaction_prompt(
            interaction,
            {"interaction": "book_scan_reaction", "select": "continue_scan"},
        )
        self.assertIn("reaction=continue_scan", prompt)
        self.assertEqual(["continue_scan"], selection["values"])

    def test_skip_and_scan_target_flow(self) -> None:
        reasons = lab.hydrate_interaction(
            {"id": "book_scan_like_reasons", "item_ids": []}, self.policy
        )
        prompt, selection = lab.interaction_prompt(
            reasons,
            {"interaction": "book_scan_like_reasons", "action": "skip"},
        )
        self.assertIn("SKIPPED", prompt)
        self.assertEqual("skip", selection["action"])

        target = lab.hydrate_interaction(
            {"id": "book_scan_target", "item_ids": []}, self.policy
        )
        prompt, _ = lab.interaction_prompt(
            target,
            {"interaction": "book_scan_target", "select": "scan_100"},
        )
        self.assertIn("继续补约 100 章", prompt)
        self.assertIn("scan_100", prompt)

    def test_report_parser_extracts_verdict_and_risks(self) -> None:
        report = lab.parse_report(
            "```legado-book-report\n"
            "<book-report><verdict>reject</verdict><headline>不建议看</headline>"
            "<risks><risk level=\"high\"><title>关系背刺</title><text>确认</text>"
            "</risk></risks></book-report>\n```"
        )
        self.assertIsNotNone(report)
        self.assertTrue((report or {})["valid_xml"])
        self.assertEqual("reject", (report or {})["verdict"])
        self.assertEqual(["关系背刺"], (report or {})["risk_titles"])

    def test_expectations_flag_visible_internal_protocol(self) -> None:
        run = {
            "turns": [{"content": "snippet覆盖", "report": None}],
            "tool_trace": [],
            "usage": {"total_tokens": 1},
            "book_memory_count": 1,
        }
        result = lab.evaluate_expectations(run, {"forbidden_terms": ["snippet覆盖"]})
        self.assertFalse(result["passed"])
        self.assertIn("forbidden visible term", result["failures"][0])

    def test_expectations_reject_full_book_claim_from_snippets_only(self) -> None:
        run = {
            "book": {"chapters": 38},
            "turns": [{
                "turn": 1,
                "content": "```legado-book-report\n<book-report><verdict>cautious</verdict>"
                "<unknown>全书主线已全部确认，未见额外隐藏雷点</unknown></book-report>\n```",
                "report": {"valid_xml": True, "verdict": "cautious"},
            }],
            "tool_trace": [],
            "usage": {"total_tokens": 1},
            "coverage": {"complete_read_indexes": []},
            "book_memory_count": 1,
        }
        result = lab.evaluate_expectations(run, {})
        self.assertFalse(result["passed"])
        self.assertTrue(any("complete text evidence" in item for item in result["failures"]))

    def test_cli_accepts_deep_thinking_options(self) -> None:
        args = lab.parse_args([
            "--scenario",
            "scenario.json",
            "--thinking",
            "--reasoning-effort",
            "high",
        ])
        self.assertTrue(args.thinking)
        self.assertEqual("high", args.reasoning_effort)

    def test_real_runs_default_to_streaming(self) -> None:
        args = lab.parse_args(["--scenario", "scenario.json"])
        self.assertTrue(args.stream)

    def test_expectations_reject_complete_text_label_when_only_snippets_were_read(self) -> None:
        run = {
            "book": {"chapters": 38},
            "turns": [{
                "turn": 1,
                "content": "```legado-book-report\n<book-report><verdict>cautious</verdict>"
                "<basic><sampled>完整正文第1—5章</sampled></basic>"
                "<unknown>其余章节只看首尾</unknown></book-report>\n```",
                "report": {"valid_xml": True, "verdict": "cautious"},
            }],
            "tool_trace": [],
            "usage": {"total_tokens": 1},
            "coverage": {"complete_read_indexes": [], "navigation_indexes": list(range(38))},
            "book_memory_count": 1,
        }
        result = lab.evaluate_expectations(run, {})
        self.assertFalse(result["passed"])
        self.assertTrue(any("no complete chapter" in item for item in result["failures"]))


if __name__ == "__main__":
    unittest.main()
