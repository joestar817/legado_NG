#!/usr/bin/env python3

from __future__ import annotations

import importlib.util
from pathlib import Path
import sys
import unittest


SCRIPT = Path(__file__).with_name("tts_storyboard_eval.py")
SPEC = importlib.util.spec_from_file_location("tts_storyboard_eval", SCRIPT)
assert SPEC and SPEC.loader
storyboard = importlib.util.module_from_spec(SPEC)
sys.modules["tts_storyboard_eval"] = storyboard
SPEC.loader.exec_module(storyboard)


def unit_text(payload: dict, unit_id: str) -> str:
    return storyboard.reconstruct_unit_text(payload, unit_id)


class TtsStoryboardEvalTest(unittest.TestCase):

    def test_quote_units_do_not_include_surrounding_narration(self) -> None:
        chapter = storyboard.Chapter(
            1,
            "网吧",
            "\n".join(
                [
                    "一小时后，网吧里。",
                    "“升子，你怎么变这么菜？”",
                    "在陈升被爆头很多次后，赵文博终于吐槽。",
                    "“脑子里全是分数。”陈升耸耸肩，装作一脸无奈。",
                    "绝不可能承认自己32岁的灵魂已经是个手残。",
                    "提到分数，赵文博也放下手中的鼠标，“升子，你给自己估分多少？”",
                    "“卧槽！升子，你是吃了仙丹还是怎么了？”赵文博目瞪口呆，“你学神附体了？”",
                ]
            ),
        )

        payload = storyboard.build_storyboard_payload(chapter, max_chars=2000)
        texts = [unit_text(payload, unit_id) for unit_id in payload["targetUnitIds"]]

        self.assertIn("“升子，你怎么变这么菜？”", texts)
        self.assertIn("“脑子里全是分数。”", texts)
        self.assertIn("“升子，你给自己估分多少？”", texts)
        self.assertIn("“卧槽！升子，你是吃了仙丹还是怎么了？”", texts)
        self.assertIn("“你学神附体了？”", texts)
        self.assertNotIn("在陈升被爆头很多次后，赵文博终于吐槽。", texts)
        self.assertFalse(any("赵文博目瞪口呆" in text for text in texts))
        self.assertFalse(any("绝不可能承认" in text for text in texts))

    def test_colon_units_are_conservative(self) -> None:
        chapter = storyboard.Chapter(
            1,
            "冒号",
            "\n".join(
                [
                    "前面黑板上写着大大的一行字：",
                    "“2010年5月27日，离高考还有10天。”",
                    "她脸上微红，矢口否认道：",
                    "“哪有，我是想考试的事走神了。”",
                    "他在女孩微信中的备注是：优质舔狗三型-升。",
                    "她心里慌乱地想：肯定是他！怎么办？",
                    "有些同学那渴望的眼神，就差喊上一句：快读啊，我们等着呢！",
                    "德国4:1英格兰！",
                ]
            ),
        )

        payload = storyboard.build_storyboard_payload(chapter, max_chars=2000)
        texts = [unit_text(payload, unit_id) for unit_id in payload["targetUnitIds"]]

        self.assertIn("“2010年5月27日，离高考还有10天。”", texts)
        self.assertIn("“哪有，我是想考试的事走神了。”", texts)
        self.assertIn("肯定是他！怎么办？", texts)
        self.assertIn("快读啊，我们等着呢！", texts)
        self.assertFalse(any("前面黑板" in text for text in texts))
        self.assertFalse(any("备注是" in text or "优质舔狗" in text for text in texts))
        self.assertFalse(any("4:1" in text for text in texts))

    def test_validation_rejects_missing_unknown_extra_and_text_leak(self) -> None:
        chapter = storyboard.Chapter(1, "校验", "“谁啊？”里面传出妈妈陈小杏的声音。")
        payload = storyboard.build_storyboard_payload(chapter, max_chars=1000)
        unit_id = payload["targetUnitIds"][0]
        result = {
            "units": [
                {
                    "unitId": unit_id,
                    "roleType": "character",
                    "characterName": "陈小杏",
                    "characterId": 1,
                    "speakerGender": "female",
                    "status": "assigned",
                    "confidence": 0.9,
                    "evidence": "后文声音: 陈小杏",
                    "text": "“谁啊？”",
                },
                {
                    "unitId": "unknown",
                    "roleType": "character",
                    "characterName": "陈升",
                    "characterId": 2,
                    "speakerGender": "male",
                    "status": "assigned",
                    "confidence": 0.8,
                    "evidence": "",
                },
            ],
            "newCharacters": [],
        }

        audit = storyboard.validate_storyboard_result(payload, result)

        self.assertFalse(audit["cacheable"])
        self.assertEqual(audit["text_leak_count"], 1)
        self.assertEqual(audit["unknown_unit_count"], 1)
        self.assertGreater(audit["invalid_schema_count"], 0)

    def test_validation_accepts_complete_compact_result(self) -> None:
        chapter = storyboard.Chapter(1, "校验", "“谁啊？”里面传出妈妈陈小杏的声音。")
        payload = storyboard.build_storyboard_payload(chapter, max_chars=1000)
        unit_id = payload["targetUnitIds"][0]
        result = {
            "units": [
                {
                    "unitId": unit_id,
                    "roleType": "character",
                    "characterName": "陈小杏",
                    "characterId": 1,
                    "speakerGender": "female",
                    "status": "assigned",
                    "confidence": 0.9,
                    "evidence": "后文声音: 陈小杏",
                }
            ],
            "newCharacters": [],
        }

        audit = storyboard.validate_storyboard_result(payload, result)

        self.assertTrue(audit["cacheable"])
        self.assertEqual(audit["missing_target_count"], 0)
        self.assertEqual(audit["text_leak_count"], 0)
        self.assertEqual(audit["invalid_schema_count"], 0)

    def test_validation_accepts_unknown_dialogue_with_gender(self) -> None:
        chapter = storyboard.Chapter(1, "校验", "“你是谁？”屋里传出一道年轻男声。")
        payload = storyboard.build_storyboard_payload(chapter, max_chars=1000)
        unit_id = payload["targetUnitIds"][0]
        result = {
            "units": [
                {
                    "unitId": unit_id,
                    "roleType": "character",
                    "characterName": "",
                    "characterId": 0,
                    "speakerGender": "male",
                    "status": "unknown",
                    "confidence": 0.72,
                    "evidence": "后文声音: 年轻男声",
                }
            ],
            "newCharacters": [],
        }

        audit = storyboard.validate_storyboard_result(payload, result)

        self.assertTrue(audit["cacheable"])
        self.assertEqual(audit["invalid_schema_count"], 0)

    def test_validation_accepts_unknown_dialogue_with_display_name(self) -> None:
        chapter = storyboard.Chapter(1, "校验", "柳烟儿冷哼一声：“这是我们请来的丹师。”")
        payload = storyboard.build_storyboard_payload(chapter, max_chars=1000)
        unit_id = payload["targetUnitIds"][0]
        result = {
            "units": [
                {
                    "unitId": unit_id,
                    "roleType": "character",
                    "characterName": "柳烟儿",
                    "characterId": 0,
                    "speakerGender": "female",
                    "status": "unknown",
                    "confidence": 0.95,
                    "evidence": "前文动作: 柳烟儿",
                }
            ],
            "newCharacters": [],
        }

        audit = storyboard.validate_storyboard_result(payload, result)

        self.assertTrue(audit["cacheable"])
        self.assertEqual(audit["invalid_schema_count"], 0)

    def test_validation_accepts_assigned_missing_character_with_gender_fallback(self) -> None:
        chapter = storyboard.Chapter(1, "校验", "身后下属厉声道：“你敢！”")
        payload = storyboard.build_storyboard_payload(chapter, max_chars=1000)
        unit_id = payload["targetUnitIds"][0]
        result = {
            "units": [
                {
                    "unitId": unit_id,
                    "roleType": "character",
                    "characterName": "",
                    "characterId": 0,
                    "speakerGender": "male",
                    "status": "assigned",
                    "confidence": 0.9,
                    "evidence": "前文声音: 身后下属",
                }
            ],
            "newCharacters": [],
        }

        audit = storyboard.validate_storyboard_result(payload, result)

        self.assertTrue(audit["cacheable"])
        self.assertEqual(audit["invalid_schema_count"], 0)


if __name__ == "__main__":
    unittest.main()
