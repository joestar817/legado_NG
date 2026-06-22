#!/usr/bin/env python3
"""Mine reusable purify rules from sampled AI outputs and evaluate them.

The script uses an existing live-eval raw file. It takes the first N chapters as
sample evidence, mines reusable literal/delete rules from model input/output
diffs, then applies those rules to the full fixture and scores against the
expected draft.
"""

from __future__ import annotations

import argparse
import difflib
import json
import re
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
VARIANT_CHARS = set("幺麽擡")
NOISE_PATTERN = re.compile(r"[^\u4e00-\u9fffA-Za-z0-9，。！？：；、“”‘’「」『』（）《》…—,.!?;:'\"()\[\]{}<>~·\s]")
AD_PATTERN = re.compile(r"灵梦|中转群|QQ群|微信群|免费外群|本群免费提取|已购vip|私聊群主", re.I)


@dataclass
class RuleCandidate:
    kind: str
    pattern: str
    replacement: str
    evidence: set[str] = field(default_factory=set)
    examples: list[dict[str, Any]] = field(default_factory=list)
    sample_hits: int = 0
    total_hits: int = 0

    @property
    def key(self) -> tuple[str, str, str]:
        return self.kind, self.pattern, self.replacement


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--raw", required=True, help="Path to live eval raw JSON.")
    parser.add_argument("--sample-chapters", type=int, default=10)
    parser.add_argument("--min-evidence", type=int, default=2)
    parser.add_argument("--max-rules", type=int, default=200)
    parser.add_argument("--save-rules", default=None)
    args = parser.parse_args()

    raw_path = (ROOT / args.raw).resolve()
    raw = json.loads(raw_path.read_text(encoding="utf-8"))
    fixture = load_fixture(Path(raw["fixture"]))
    case_sources, expected_by_case = load_fixture_cases(fixture)
    mined_rules = mine_rules(raw, args.sample_chapters, args.min_evidence)
    ranked_rules = rank_rules(mined_rules, case_sources, args.max_rules)
    report = evaluate_rules(ranked_rules, case_sources, expected_by_case)

    print(f"raw={raw_path.relative_to(ROOT)}")
    print(f"sample_chapters={args.sample_chapters}")
    print(f"min_evidence={args.min_evidence}")
    print(f"rules={len(ranked_rules)}")
    print_top_rules(ranked_rules)
    print_report(report)

    if args.save_rules:
        save_path = (ROOT / args.save_rules).resolve()
        save_path.parent.mkdir(parents=True, exist_ok=True)
        save_path.write_text(
            json.dumps(
                {
                    "raw": str(raw_path.relative_to(ROOT)),
                    "sampleChapters": args.sample_chapters,
                    "minEvidence": args.min_evidence,
                    "rules": [serialize_rule(rule) for rule in ranked_rules],
                    "report": report,
                },
                ensure_ascii=False,
                indent=2,
            )
            + "\n",
            encoding="utf-8",
        )
        print(f"saved={save_path.relative_to(ROOT)}")
    return 0


def load_fixture(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def load_fixture_cases(fixture: dict[str, Any]) -> tuple[list[dict[int, str]], list[dict[int, str]]]:
    case_sources: list[dict[int, str]] = []
    expected_by_case: list[dict[int, str]] = []
    base_dir = ROOT
    for case in fixture["cases"]:
        inputs = read_json_ref(base_dir, case["payloadFile"]) if "payloadFile" in case else case["inputs"]
        expected = (
            read_json_ref(base_dir, case["expectedDraftFile"])
            if "expectedDraftFile" in case
            else case.get("expectedChanges", [])
        )
        case_sources.append({int(item["id"]): str(item.get("input", item.get("text", ""))) for item in inputs})
        expected_by_case.append({int(item["id"]): str(item.get("output", item.get("cleaned", ""))) for item in expected})
    return case_sources, expected_by_case


def read_json_ref(base_dir: Path, value: str) -> Any:
    path = Path(value)
    if not path.is_absolute():
        root_relative = ROOT / path
        path = root_relative if root_relative.exists() else base_dir / path
    return json.loads(path.read_text(encoding="utf-8"))


def mine_rules(raw: dict[str, Any], sample_chapters: int, min_evidence: int) -> list[RuleCandidate]:
    candidates: dict[tuple[str, str, str], RuleCandidate] = {}
    for case_index, case in enumerate(raw["cases"], start=1):
        chapter_number = int(case.get("name", "").split()[1]) if case.get("name", "").startswith("chapter ") else case_index
        if chapter_number > sample_chapters:
            continue
        sources = parse_case_sources(case)
        outputs = {int(item_id): str(output) for item_id, output in case.get("parsedOutputs", {}).items()}
        for item_id, output in outputs.items():
            source = sources.get(item_id)
            if not source or output == source:
                continue
            evidence_id = f"{chapter_number:03d}:{item_id}"
            for kind, pattern, replacement in diff_to_rule_parts(source, output):
                if not is_rule_like(kind, pattern, replacement):
                    continue
                key = (kind, pattern, replacement)
                candidate = candidates.setdefault(key, RuleCandidate(kind, pattern, replacement))
                candidate.evidence.add(evidence_id)
                if len(candidate.examples) < 5:
                    candidate.examples.append(
                        {
                            "chapter": chapter_number,
                            "id": item_id,
                            "input": compact(source),
                            "output": compact(output),
                        }
                    )
    return [candidate for candidate in candidates.values() if len(candidate.evidence) >= min_evidence]


def parse_case_sources(case: dict[str, Any]) -> dict[int, str]:
    request = case.get("request", {})
    messages = request.get("messages", [])
    if not messages:
        return {}
    user_content = messages[-1].get("content", "[]")
    inputs = json.loads(user_content)
    return {int(item["id"]): str(item["input"]) for item in inputs}


def diff_to_rule_parts(source: str, output: str) -> list[tuple[str, str, str]]:
    parts: list[tuple[str, str, str]] = []
    matcher = difflib.SequenceMatcher(a=source, b=output, autojunk=False)
    for tag, i1, i2, j1, j2 in matcher.get_opcodes():
        if tag == "equal":
            continue
        before = source[i1:i2]
        after = output[j1:j2]
        if tag == "delete":
            parts.append(("delete", before, ""))
            continue
        if tag == "replace":
            parts.append(("literal", before, after))
            if before and after:
                for left in range(1, 4):
                    for right in range(0, 3):
                        expanded = expand_context(source, output, i1, i2, j1, j2, left, right)
                        if expanded:
                            parts.append(("literal", expanded[0], expanded[1]))
    return dedupe_parts(parts)


def expand_context(
    source: str,
    output: str,
    i1: int,
    i2: int,
    j1: int,
    j2: int,
    left: int,
    right: int,
) -> tuple[str, str] | None:
    source_start = max(0, i1 - left)
    output_start = max(0, j1 - left)
    source_end = min(len(source), i2 + right)
    output_end = min(len(output), j2 + right)
    before = source[source_start:source_end]
    after = output[output_start:output_end]
    if before == after:
        return None
    return before, after


def dedupe_parts(parts: list[tuple[str, str, str]]) -> list[tuple[str, str, str]]:
    seen: set[tuple[str, str, str]] = set()
    deduped: list[tuple[str, str, str]] = []
    for part in parts:
        if part in seen:
            continue
        seen.add(part)
        deduped.append(part)
    return deduped


def is_rule_like(kind: str, pattern: str, replacement: str) -> bool:
    if not pattern or pattern == replacement:
        return False
    if kind == "literal":
        if len(pattern) > 8 or len(replacement) > 8:
            return False
        if len(pattern) == 1 and pattern not in {"麽", "擡"}:
            return False
        return has_suspicious_signal(pattern)
    if kind == "delete":
        if len(pattern) > 32:
            return False
        return bool(NOISE_PATTERN.search(pattern) or AD_PATTERN.search(pattern))
    return False


def has_suspicious_signal(value: str) -> bool:
    return any(ch in VARIANT_CHARS for ch in value) or bool(NOISE_PATTERN.search(value) or AD_PATTERN.search(value))


def rank_rules(
    rules: list[RuleCandidate],
    case_sources: list[dict[int, str]],
    max_rules: int,
) -> list[RuleCandidate]:
    all_sources = [source for case in case_sources for source in case.values()]
    sample_sources = [source for case in case_sources[:10] for source in case.values()]
    for rule in rules:
        rule.sample_hits = count_hits(rule, sample_sources)
        rule.total_hits = count_hits(rule, all_sources)
    filtered = [rule for rule in rules if rule.total_hits > 0]
    filtered.sort(key=lambda rule: (-len(rule.evidence), -rule.total_hits, -len(rule.pattern), rule.pattern))
    selected: list[RuleCandidate] = []
    covered_patterns: set[str] = set()
    for rule in filtered:
        if len(selected) >= max_rules:
            break
        if is_redundant(rule, selected, covered_patterns):
            continue
        selected.append(rule)
        covered_patterns.add(rule.pattern)
    return selected


def is_redundant(rule: RuleCandidate, selected: list[RuleCandidate], covered_patterns: set[str]) -> bool:
    if rule.pattern in covered_patterns:
        return True
    for existing in selected:
        if (
            rule.kind == existing.kind == "literal"
            and rule.pattern in existing.pattern
            and rule.replacement in existing.replacement
        ):
            return True
    return False


def count_hits(rule: RuleCandidate, sources: list[str]) -> int:
    return sum(source.count(rule.pattern) for source in sources)


def evaluate_rules(
    rules: list[RuleCandidate],
    case_sources: list[dict[int, str]],
    expected_by_case: list[dict[int, str]],
) -> dict[str, Any]:
    total = 0
    correct = 0
    expected = 0
    exact = 0
    unchanged_correct = 0
    false_negative = 0
    false_positive = 0
    exact_mismatch = 0
    changed = 0
    for sources, expected_by_id in zip(case_sources, expected_by_case):
        expected += len(expected_by_id)
        for item_id, source in sources.items():
            total += 1
            output = apply_rules(source, rules)
            if output != source:
                changed += 1
            expected_output = expected_by_id.get(item_id)
            if expected_output is None:
                if output == source:
                    correct += 1
                    unchanged_correct += 1
                else:
                    false_positive += 1
                continue
            if output == expected_output:
                correct += 1
                exact += 1
            elif output == source:
                false_negative += 1
            else:
                exact_mismatch += 1
    return {
        "total": total,
        "correct": correct,
        "accuracy": correct / total if total else 1.0,
        "expected": expected,
        "exact": exact,
        "expectedChangeExact": exact / expected if expected else 1.0,
        "unchangedCorrect": unchanged_correct,
        "falseNegative": false_negative,
        "falsePositive": false_positive,
        "exactMismatch": exact_mismatch,
        "changed": changed,
    }


def apply_rules(text: str, rules: list[RuleCandidate]) -> str:
    current = text
    for rule in rules:
        if rule.kind == "literal":
            current = current.replace(rule.pattern, rule.replacement)
        elif rule.kind == "delete":
            current = current.replace(rule.pattern, "")
    return current


def print_top_rules(rules: list[RuleCandidate]) -> None:
    print("top_rules:")
    for rule in rules[:20]:
        print(
            f"  - {rule.kind}: {rule.pattern!r} -> {rule.replacement!r} "
            f"evidence={len(rule.evidence)} sample_hits={rule.sample_hits} total_hits={rule.total_hits}"
        )


def print_report(report: dict[str, Any]) -> None:
    print("Report")
    print(f"  accuracy={report['correct']}/{report['total']} ({report['accuracy']:.1%})")
    print(f"  expected_change_exact={report['exact']}/{report['expected']} ({report['expectedChangeExact']:.1%})")
    print(f"  unchanged_correct={report['unchangedCorrect']}")
    print(f"  false_negative={report['falseNegative']}")
    print(f"  false_positive={report['falsePositive']}")
    print(f"  exact_mismatch={report['exactMismatch']}")
    print(f"  changed={report['changed']}")


def serialize_rule(rule: RuleCandidate) -> dict[str, Any]:
    return {
        "kind": rule.kind,
        "pattern": rule.pattern,
        "replacement": rule.replacement,
        "evidenceCount": len(rule.evidence),
        "sampleHits": rule.sample_hits,
        "totalHits": rule.total_hits,
        "examples": rule.examples,
    }


def compact(value: str, limit: int = 120) -> str:
    value = value.replace("\n", "\\n")
    return value if len(value) <= limit else value[: limit - 1] + "…"


if __name__ == "__main__":
    raise SystemExit(main())
