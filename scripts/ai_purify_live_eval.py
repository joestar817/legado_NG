#!/usr/bin/env python3
"""Run live AI purify evals with the app's chapter prompt format.

This calls a real OpenAI-compatible chat-completions endpoint. It does not start
the Android app, but it mirrors the app's chapter batch request shape:

- system: AiPurifyHelper chapter fixed protocol + AiPromptStore default prompt
- user: JSON array of {"id": Int, "input": String}
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
AI_HELPER = ROOT / "app/src/main/java/io/legado/app/help/ai/AiPurifyHelper.kt"
AI_PROMPT_STORE = ROOT / "app/src/main/java/io/legado/app/help/ai/AiPromptStore.kt"


@dataclass
class CaseMetrics:
    name: str
    total: int
    correct: int
    expected: int
    exact: int
    unchanged_correct: int
    false_negative: list[int]
    false_positive: list[int]
    exact_mismatch: list[dict[str, str]]
    protocol_errors: list[str]
    latency_ms: int
    model: str
    raw_content: str


def main() -> int:
    load_dotenv(ROOT / ".env")

    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--fixture",
        default="app/src/test/resources/ai-purify-fixtures/shanheji_live_eval.json",
        help="Fixture JSON with inputs and expectedChanges.",
    )
    parser.add_argument("--base-url", default=os.getenv("AI_BASE_URL", "https://api.deepseek.com"))
    parser.add_argument("--path", default=os.getenv("AI_CHAT_COMPLETIONS_PATH", "/chat/completions"))
    parser.add_argument("--model", default=os.getenv("AI_MODEL", "deepseek-v4-flash"))
    parser.add_argument(
        "--thinking",
        choices=("disabled", "enabled", "omit"),
        default=os.getenv("AI_THINKING", "disabled"),
        help="OpenAI-compatible thinking parameter. App-compatible default is disabled.",
    )
    parser.add_argument("--api-key", default=os.getenv("AI_API_KEY") or os.getenv("DEEPSEEK_API_KEY"))
    parser.add_argument("--api-key-env", default=None, help="Read API key from this env var.")
    parser.add_argument("--timeout", type=int, default=120)
    parser.add_argument("--strict-schema", action="store_true", help="Reject responses without output.")
    parser.add_argument(
        "--ignore-protocol-errors",
        action="store_true",
        help="Do not fail the run when the model used legacy cleaned/text/content fields.",
    )
    parser.add_argument("--start-case", type=int, default=1, help="1-based case index to start from.")
    parser.add_argument("--limit-cases", type=int, default=None, help="Maximum number of cases to run.")
    parser.add_argument("--save-raw", default=None, help="Optional path to save raw run details.")
    args = parser.parse_args()

    if args.api_key_env:
        args.api_key = os.getenv(args.api_key_env)
    if not args.api_key:
        print("Missing API key. Set AI_API_KEY or DEEPSEEK_API_KEY, or pass --api-key-env.", file=sys.stderr)
        return 2

    fixture_path = (ROOT / args.fixture).resolve()
    fixture = json.loads(fixture_path.read_text(encoding="utf-8"))
    system_prompt = build_system_prompt()
    reports: list[CaseMetrics] = []
    raw_runs: list[dict[str, Any]] = []
    selected_cases = fixture["cases"][args.start_case - 1 :]
    if args.limit_cases is not None:
        selected_cases = selected_cases[: args.limit_cases]

    for case in selected_cases:
        case = load_case_file_refs(fixture_path.parent, case)
        report, raw = run_case(args, system_prompt, case)
        reports.append(report)
        raw_runs.append(raw)
        print_case_report(report)
        if args.save_raw:
            save_raw(args, fixture_path, raw_runs)

    print_summary(reports)
    if args.save_raw:
        save_raw(args, fixture_path, raw_runs)
    passed = all(
        not r.false_negative
        and not r.false_positive
        and not r.exact_mismatch
        and (args.ignore_protocol_errors or not r.protocol_errors)
        for r in reports
    )
    return 0 if passed else 1


def run_case(args: argparse.Namespace, system_prompt: str, case: dict[str, Any]) -> tuple[CaseMetrics, dict[str, Any]]:
    inputs = case["inputs"]
    expected_by_id = {item["id"]: get_expected_output(item) for item in case.get("expectedChanges", [])}
    source_by_id = {item["id"]: get_input_text(item) for item in inputs}
    expected_ids = set(expected_by_id)
    source_length = sum(len(get_input_text(item)) for item in inputs)
    max_tokens = min(max(source_length * 2 + len(inputs) * 48 + 128, 512), 8192)
    user_content = json.dumps(inputs, ensure_ascii=False, indent=2)
    request_body = {
        "model": args.model,
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_content},
        ],
        "temperature": 0.0,
        "max_tokens": max_tokens,
        "stream": False,
    }
    if args.thinking != "omit":
        request_body["thinking"] = {"type": args.thinking}
    started = time.monotonic()
    response = post_json(
        url=args.base_url.rstrip("/") + ensure_start_slash(args.path),
        api_key=args.api_key,
        body=request_body,
        timeout=args.timeout,
    )
    latency_ms = int((time.monotonic() - started) * 1000)
    choice = response["choices"][0]
    content = choice["message"].get("content") or ""
    model = response.get("model") or args.model
    parsed, parse_error = parse_model_content(content)
    protocol_errors: list[str] = []
    outputs_by_id: dict[int, str] = {}

    if parse_error:
        protocol_errors.append(parse_error)
    else:
        for index, item in enumerate(parsed):
            if not isinstance(item, dict) or "id" not in item:
                protocol_errors.append(f"item[{index}]:schema_error")
                continue
            item_id = item["id"]
            if not isinstance(item_id, int):
                protocol_errors.append(f"item[{index}]:id_not_int")
                continue
            if "output" in item:
                cleaned = item["output"]
            elif not args.strict_schema and "cleaned" in item:
                protocol_errors.append(f"id={item_id}:legacy_cleaned_field")
                cleaned = item["cleaned"]
            elif not args.strict_schema and "text" in item:
                protocol_errors.append(f"id={item_id}:legacy_text_field")
                cleaned = item["text"]
            elif not args.strict_schema and "content" in item:
                protocol_errors.append(f"id={item_id}:legacy_content_field")
                cleaned = item["content"]
            else:
                protocol_errors.append(f"id={item_id}:missing_output")
                continue
            if not isinstance(cleaned, str):
                protocol_errors.append(f"id={item_id}:output_not_string")
                continue
            outputs_by_id[item_id] = cleaned

    exact = 0
    false_negative: list[int] = []
    exact_mismatch: list[dict[str, str]] = []
    for item_id, expected in expected_by_id.items():
        actual = outputs_by_id.get(item_id)
        if actual is None:
            false_negative.append(item_id)
        elif actual == expected:
            exact += 1
        else:
            exact_mismatch.append({"id": str(item_id), "expected": expected, "actual": actual})

    false_positive = [
        item_id
        for item_id, cleaned in outputs_by_id.items()
        if item_id not in expected_by_id and cleaned != source_by_id.get(item_id)
    ]
    unchanged_correct = sum(
        1
        for item_id, source in source_by_id.items()
        if item_id not in expected_ids and outputs_by_id.get(item_id, source) == source
    )
    total = len(inputs)
    report = CaseMetrics(
        name=case["name"],
        total=total,
        correct=exact + unchanged_correct,
        expected=len(expected_by_id),
        exact=exact,
        unchanged_correct=unchanged_correct,
        false_negative=false_negative,
        false_positive=false_positive,
        exact_mismatch=exact_mismatch,
        protocol_errors=protocol_errors,
        latency_ms=latency_ms,
        model=model,
        raw_content=content,
    )
    raw = {
        "name": case["name"],
        "request": request_body,
        "response": response,
        "parsedOutputs": outputs_by_id,
        "metrics": report.__dict__,
    }
    return report, raw


def save_raw(args: argparse.Namespace, fixture_path: Path, raw_runs: list[dict[str, Any]]) -> None:
    save_path = Path(args.save_raw)
    save_path.parent.mkdir(parents=True, exist_ok=True)
    output = {
        "fixture": str(fixture_path),
        "model": args.model,
        "baseUrl": args.base_url,
        "thinking": args.thinking,
        "startCase": args.start_case,
        "limitCases": args.limit_cases,
        "cases": raw_runs,
    }
    save_path.write_text(
        json.dumps(output, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )


def load_case_file_refs(base_dir: Path, case: dict[str, Any]) -> dict[str, Any]:
    loaded = dict(case)
    if "payloadFile" in loaded:
        loaded["inputs"] = read_json_ref(base_dir, str(loaded["payloadFile"]))
    if "expectedDraftFile" in loaded:
        loaded["expectedChanges"] = read_json_ref(base_dir, str(loaded["expectedDraftFile"]))
    return loaded


def read_json_ref(base_dir: Path, value: str) -> Any:
    path = Path(value)
    if not path.is_absolute():
        root_relative = ROOT / path
        path = root_relative if root_relative.exists() else base_dir / path
    return json.loads(path.read_text(encoding="utf-8"))


def get_input_text(item: dict[str, Any]) -> str:
    value = item.get("input", item.get("text"))
    if not isinstance(value, str):
        raise ValueError(f"Input item id={item.get('id')} missing input/text string")
    return value


def get_expected_output(item: dict[str, Any]) -> str:
    value = item.get("output", item.get("cleaned"))
    if not isinstance(value, str):
        raise ValueError(f"Expected item id={item.get('id')} missing output/cleaned string")
    return value


def build_system_prompt() -> str:
    helper_text = AI_HELPER.read_text(encoding="utf-8")
    prompt_store_text = AI_PROMPT_STORE.read_text(encoding="utf-8")
    protocol = extract_triple_block(
        helper_text,
        marker="用户会给你一个 JSON 数组",
    )
    task_prompt = extract_chapter_default_prompt(prompt_store_text)
    return protocol.replace(
        "${AiPromptStore.prompt(AiPromptStore.Prompt.CHAPTER_OPTIMIZE)}",
        task_prompt,
    )


def extract_triple_block(text: str, marker: str) -> str:
    marker_index = text.index(marker)
    start = text.rfind('"""', 0, marker_index)
    end = text.index('""".trimIndent()', marker_index)
    block = text[start + 3:end]
    return trim_kotlin_indent(block)


def extract_chapter_default_prompt(text: str) -> str:
    match = re.search(
        r"CHAPTER_OPTIMIZE\(\s*id\s*=.*?defaultPrompt\s*=\s*\"\"\"(.*?)\"\"\"\.trimIndent\(\)",
        text,
        re.S,
    )
    if not match:
        raise RuntimeError("Cannot extract CHAPTER_OPTIMIZE defaultPrompt")
    return trim_kotlin_indent(match.group(1))


def trim_kotlin_indent(block: str) -> str:
    lines = block.strip("\n").splitlines()
    non_empty = [line for line in lines if line.strip()]
    indent = min((len(line) - len(line.lstrip())) for line in non_empty) if non_empty else 0
    return "\n".join(line[indent:] for line in lines).strip()


def load_dotenv(path: Path) -> None:
    if not path.exists():
        return
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        if not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", key):
            continue
        if key in os.environ:
            continue
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in ("'", '"'):
            value = value[1:-1]
        os.environ[key] = value


def post_json(url: str, api_key: str, body: dict[str, Any], timeout: int) -> dict[str, Any]:
    data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(
        url=url,
        data=data,
        method="POST",
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
            "User-Agent": "ReadingNG-AiPurifyEval/1.0",
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"HTTP {error.code}: {detail[:1000]}") from error


def parse_model_content(content: str) -> tuple[Any, str | None]:
    text = content.strip()
    if text.startswith("```"):
        lines = text.splitlines()
        if lines and lines[0].startswith("```"):
            lines = lines[1:]
        if lines and lines[-1].strip() == "```":
            lines = lines[:-1]
        text = "\n".join(lines).strip()
    start = text.find("[")
    end = text.rfind("]")
    if start < 0 or end <= start:
        return None, "missing_json_array"
    try:
        return json.loads(text[start:end + 1]), None
    except json.JSONDecodeError as error:
        return None, f"json_parse_error:{error}"


def print_case_report(report: CaseMetrics) -> None:
    accuracy = report.correct / report.total if report.total else 1.0
    change_accuracy = report.exact / report.expected if report.expected else 1.0
    print(f"\n[{report.name}] model={report.model} latency={report.latency_ms}ms")
    print(f"  accuracy={report.correct}/{report.total} ({accuracy:.1%})")
    print(f"  expected_change_exact={report.exact}/{report.expected} ({change_accuracy:.1%})")
    print(f"  unchanged_correct={report.unchanged_correct}")
    print(f"  false_negative={report.false_negative}")
    print(f"  false_positive={report.false_positive}")
    print(f"  exact_mismatch={len(report.exact_mismatch)}")
    for mismatch in report.exact_mismatch:
        print(f"    id={mismatch['id']}")
        print(f"      expected: {mismatch['expected']}")
        print(f"      actual:   {mismatch['actual']}")
    print(f"  protocol_errors={report.protocol_errors}")


def print_summary(reports: list[CaseMetrics]) -> None:
    total = sum(report.total for report in reports)
    correct = sum(report.correct for report in reports)
    expected = sum(report.expected for report in reports)
    exact = sum(report.exact for report in reports)
    unchanged_correct = sum(report.unchanged_correct for report in reports)
    false_negative = sum(len(report.false_negative) for report in reports)
    false_positive = sum(len(report.false_positive) for report in reports)
    exact_mismatch = sum(len(report.exact_mismatch) for report in reports)
    protocol_errors = sum(len(report.protocol_errors) for report in reports)
    accuracy = correct / total if total else 1.0
    change_accuracy = exact / expected if expected else 1.0
    print("\nSummary")
    print(f"  accuracy={correct}/{total} ({accuracy:.1%})")
    print(f"  expected_change_exact={exact}/{expected} ({change_accuracy:.1%})")
    print(f"  unchanged_correct={unchanged_correct}")
    print(f"  false_negative={false_negative}")
    print(f"  false_positive={false_positive}")
    print(f"  exact_mismatch={exact_mismatch}")
    print(f"  protocol_errors={protocol_errors}")


def ensure_start_slash(value: str) -> str:
    return value if value.startswith("/") else "/" + value


if __name__ == "__main__":
    raise SystemExit(main())
