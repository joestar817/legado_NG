#!/usr/bin/env python3
"""Run gold-set accuracy evaluation for TTS storyboard attribution."""

from __future__ import annotations

import argparse
import importlib.util
import json
import os
from pathlib import Path
import sys
import time
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
STORYBOARD_SCRIPT = ROOT / "scripts/tts_storyboard_eval.py"
DEFAULT_FIXTURE = ROOT / "scripts/fixtures/tts_storyboard_primary_role_gold.json"
DEFAULT_OUT_DIR = ROOT / "build/tts_storyboard_gold_eval"

SPEC = importlib.util.spec_from_file_location("tts_storyboard_eval", STORYBOARD_SCRIPT)
assert SPEC and SPEC.loader
storyboard = importlib.util.module_from_spec(SPEC)
sys.modules["tts_storyboard_eval"] = storyboard
SPEC.loader.exec_module(storyboard)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--fixture", default=str(DEFAULT_FIXTURE))
    parser.add_argument("--out", default=str(DEFAULT_OUT_DIR))
    parser.add_argument("--model", default=storyboard.DEFAULT_MODEL)
    parser.add_argument("--api-url", default=storyboard.API_URL)
    parser.add_argument("--api-key-env", default="SILICONFLOW_API_KEY")
    parser.add_argument("--max-tokens", type=int, default=4096)
    parser.add_argument("--timeout", type=int, default=180)
    parser.add_argument("--retries", type=int, default=1)
    parser.add_argument("--retry-sleep", type=float, default=8.0)
    parser.add_argument("--sleep", type=float, default=0.5)
    parser.add_argument("--case", action="append", default=[], help="Only run selected case id.")
    parser.add_argument("--offline", action="store_true", help="Only validate fixture boundaries; do not call model.")
    parser.add_argument(
        "--json-mode",
        action=argparse.BooleanOptionalAction,
        default=True,
        help="Send OpenAI-compatible response_format json_object.",
    )
    parser.add_argument(
        "--enable-thinking",
        choices=("true", "false", "omit"),
        default="omit",
    )
    return parser.parse_args()


def load_fixture(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def unit_text(payload: dict[str, Any], unit_id: str) -> str:
    return storyboard.reconstruct_unit_text(payload, unit_id)


def build_payload(case: dict[str, Any], default_characters: list[dict[str, Any]]) -> dict[str, Any]:
    chapter = storyboard.Chapter(
        1,
        str(case.get("title") or case.get("id") or "gold"),
        "\n".join(str(item) for item in case.get("paragraphs") or []),
    )
    characters = case.get("knownCharacters") or default_characters
    return storyboard.build_storyboard_payload(chapter, max_chars=100000, known_characters=characters)


def expected_by_text(case: dict[str, Any]) -> dict[str, dict[str, Any]]:
    result: dict[str, dict[str, Any]] = {}
    for item in case.get("expected") or []:
        text = item.get("text")
        if not isinstance(text, str) or not text:
            raise ValueError(f"{case.get('id')}: expected item missing text")
        if text in result:
            raise ValueError(f"{case.get('id')}: duplicate expected text: {text}")
        result[text] = item
    return result


def validate_fixture_case(case: dict[str, Any], payload: dict[str, Any]) -> dict[str, Any]:
    expected = expected_by_text(case)
    units_by_text: dict[str, str] = {}
    duplicate_texts: list[str] = []
    for unit_id in payload.get("targetUnitIds") or []:
        text = unit_text(payload, unit_id)
        if text in units_by_text:
            duplicate_texts.append(text)
        units_by_text[text] = unit_id
    missing_expected = [text for text in expected if text not in units_by_text]
    extra_units = [text for text in units_by_text if text not in expected]
    return {
        "missingExpected": missing_expected,
        "extraUnits": extra_units,
        "duplicateTexts": duplicate_texts,
        "ok": not missing_expected and not extra_units and not duplicate_texts,
        "unitsByText": units_by_text,
    }


def compare_case(
    case: dict[str, Any],
    payload: dict[str, Any],
    result: dict[str, Any],
    audit: dict[str, Any],
) -> dict[str, Any]:
    expected = expected_by_text(case)
    fixture_validation = validate_fixture_case(case, payload)
    result_by_id = {
        item.get("unitId"): item
        for item in result.get("units") or []
        if isinstance(item, dict)
    }
    rows: list[dict[str, Any]] = []
    correct = 0
    total = 0
    if fixture_validation["ok"]:
        for text, unit_id in fixture_validation["unitsByText"].items():
            exp = expected[text]
            actual = result_by_id.get(unit_id)
            total += 1
            matched = actual_matches_expected(actual, exp)
            if matched:
                correct += 1
            rows.append(
                {
                    "text": text,
                    "unitId": unit_id,
                    "expected": comparable_expected(exp),
                    "actual": comparable_actual(actual),
                    "matched": matched,
                }
            )
    accuracy = correct / total if total else 0.0
    return {
        "caseId": case.get("id"),
        "title": case.get("title"),
        "fixtureOk": fixture_validation["ok"],
        "fixtureErrors": {
            "missingExpected": fixture_validation["missingExpected"],
            "extraUnits": fixture_validation["extraUnits"],
            "duplicateTexts": fixture_validation["duplicateTexts"],
        },
        "cacheable": audit.get("cacheable", False),
        "protocol": {
            "invalidSchemaCount": audit.get("invalid_schema_count", 0),
            "textLeakCount": audit.get("text_leak_count", 0),
            "missingTargetCount": audit.get("missing_target_count", 0),
            "unknownUnitCount": audit.get("unknown_unit_count", 0),
            "duplicateUnitCount": audit.get("duplicate_unit_count", 0),
        },
        "correct": correct,
        "total": total,
        "accuracy": accuracy,
        "rows": rows,
    }


def actual_matches_expected(actual: Any, expected: dict[str, Any]) -> bool:
    if not isinstance(actual, dict):
        return False
    expected_role = effective_role_type(expected.get("roleType"))
    expected_status = expected.get("status", "assigned")
    expected_name = expected.get("characterName", "")
    return (
        effective_role_type(actual.get("roleType")) == expected_role
        and actual.get("status") == expected_status
        and str(actual.get("characterName") or "") == expected_name
    )


def effective_role_type(role_type: Any) -> Any:
    if role_type in ("narrator", "other"):
        return "narrator"
    return role_type


def comparable_expected(item: dict[str, Any]) -> dict[str, Any]:
    return {
        "roleType": item.get("roleType"),
        "characterName": item.get("characterName", ""),
        "status": item.get("status", "assigned"),
    }


def comparable_actual(item: Any) -> dict[str, Any] | None:
    if not isinstance(item, dict):
        return None
    return {
        "roleType": item.get("roleType"),
        "characterName": item.get("characterName", ""),
        "status": item.get("status"),
        "confidence": item.get("confidence"),
        "evidence": item.get("evidence", ""),
    }


def write_markdown(path: Path, summary: dict[str, Any], case_results: list[dict[str, Any]]) -> None:
    lines = [
        "# TTS Storyboard Gold Eval",
        "",
        f"accuracy: {summary['accuracy']:.4f}",
        f"correct: {summary['correct']}",
        f"total: {summary['total']}",
        f"cacheable_cases: {summary['cacheableCases']}/{summary['caseCount']}",
        f"fixture_ok_cases: {summary['fixtureOkCases']}/{summary['caseCount']}",
        "",
    ]
    for case in case_results:
        lines.extend(
            [
                f"## {case['caseId']} - {case.get('title') or ''}",
                "",
                f"fixtureOk: {case['fixtureOk']}",
                f"cacheable: {case['cacheable']}",
                f"accuracy: {case['accuracy']:.4f} ({case['correct']}/{case['total']})",
                "",
            ]
        )
        if not case["fixtureOk"]:
            lines.append("### Fixture Errors")
            lines.append("")
            lines.append(json.dumps(case["fixtureErrors"], ensure_ascii=False, indent=2))
            lines.append("")
        mismatches = [row for row in case["rows"] if not row["matched"]]
        if mismatches:
            lines.append("### Mismatches")
            lines.append("")
            for row in mismatches:
                lines.append(f"- {row['text']}")
                lines.append(f"  - expected: `{row['expected']}`")
                lines.append(f"  - actual: `{row['actual']}`")
            lines.append("")
    path.write_text("\n".join(lines), encoding="utf-8")


def main() -> int:
    args = parse_args()
    fixture_path = Path(args.fixture)
    out_dir = Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)
    fixture = load_fixture(fixture_path)
    selected = set(args.case)
    cases = [
        case
        for case in fixture.get("cases") or []
        if not selected or case.get("id") in selected
    ]
    default_characters = fixture.get("knownCharacters") or []
    api_key = os.getenv(args.api_key_env)
    if not args.offline and not api_key:
        print(f"missing env: {args.api_key_env}", file=sys.stderr)
        return 2
    case_results: list[dict[str, Any]] = []
    for case in cases:
        case_id = str(case.get("id") or len(case_results) + 1)
        print(f"case {case_id}", flush=True)
        payload = build_payload(case, default_characters)
        fixture_validation = validate_fixture_case(case, payload)
        (out_dir / f"{case_id}.payload.json").write_text(
            json.dumps(payload, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        if args.offline or not fixture_validation["ok"]:
            result = {"units": [], "newCharacters": []}
            audit = storyboard.validate_storyboard_result(payload, result)
            case_result = compare_case(case, payload, result, audit)
            case_results.append(case_result)
            continue
        try:
            raw, content = storyboard.request_storyboard(
                api_url=args.api_url,
                api_key=api_key,
                model=args.model,
                payload=payload,
                max_tokens=args.max_tokens,
                timeout=args.timeout,
                json_mode=args.json_mode,
                enable_thinking=args.enable_thinking,
                retries=args.retries,
                retry_sleep=args.retry_sleep,
            )
            (out_dir / f"{case_id}.raw.json").write_text(
                json.dumps(raw, ensure_ascii=False, indent=2),
                encoding="utf-8",
            )
            (out_dir / f"{case_id}.content.txt").write_text(content, encoding="utf-8")
            result = json.loads(storyboard.extract_json(content))
        except Exception as error:
            result = {"units": [], "newCharacters": []}
            (out_dir / f"{case_id}.error.json").write_text(
                json.dumps({"caseId": case_id, "error": str(error)}, ensure_ascii=False, indent=2),
                encoding="utf-8",
            )
        audit = storyboard.validate_storyboard_result(payload, result)
        (out_dir / f"{case_id}.json").write_text(
            json.dumps(result, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        (out_dir / f"{case_id}.audit.json").write_text(
            json.dumps(audit, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        case_results.append(compare_case(case, payload, result, audit))
        time.sleep(args.sleep)
    total = sum(item["total"] for item in case_results)
    correct = sum(item["correct"] for item in case_results)
    summary = {
        "fixture": str(fixture_path),
        "model": args.model if not args.offline else "offline",
        "caseCount": len(case_results),
        "fixtureOkCases": sum(1 for item in case_results if item["fixtureOk"]),
        "cacheableCases": sum(1 for item in case_results if item["cacheable"]),
        "correct": correct,
        "total": total,
        "accuracy": correct / total if total else 0.0,
        "targetAccuracy": 0.98,
        "passed98": bool(total and correct / total >= 0.98),
    }
    output = {"summary": summary, "cases": case_results}
    (out_dir / "summary.json").write_text(
        json.dumps(output, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    write_markdown(out_dir / "summary.md", summary, case_results)
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    if args.offline:
        return 0 if summary["fixtureOkCases"] == summary["caseCount"] else 1
    return 0 if summary["passed98"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
