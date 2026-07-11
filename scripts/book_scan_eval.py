#!/usr/bin/env python3
"""Run a resumable AI book-scan evaluation against an OpenAI-compatible API.

The evaluator uses the same built-in ``book_scan`` Skill as the App. It parses
TXT novels into chapters, sends either an orientation set or contiguous full
scan batches, validates ``book_scan_delta`` JSON, and persists a reusable local
manifest. Later ``--query`` calls only read that manifest and never call the
model again.

Examples (PowerShell):

  $env:SILICONFLOW_API_KEY = "..."
  python scripts/book_scan_eval.py --book "C:\\novel.txt" --stage orientation
  python scripts/book_scan_eval.py --book "C:\\novel.txt" --stage full_scan
  python scripts/book_scan_eval.py --book "C:\\novel.txt" --query relationship

Use ``--dry-run`` to validate parsing and generated requests without a key.
"""

from __future__ import annotations

import argparse
import hashlib
import html
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable, Sequence


ROOT = Path(__file__).resolve().parents[1]
SKILL_FILE = ROOT / "app/src/main/assets/skills/book_scan.md"
DEFAULT_OUT_DIR = ROOT / "build/book_scan_eval"
DEFAULT_API_URL = "https://api.siliconflow.cn/v1/chat/completions"
DEFAULT_MODEL = "Qwen/Qwen3-8B"

DIMENSIONS = {
    "work_positioning",
    "protagonist_experience",
    "character_ecology",
    "relationship",
    "plot_structure",
    "plot_logic",
    "worldbuilding",
    "power_progression",
    "pacing",
    "writing_style",
    "tone_and_content",
    "ending_safety",
}
STAGES = {"orientation", "full_scan", "targeted_review"}
EVENT_STATUSES = {"suspected", "confirmed", "resolved", "reversed", "not_found"}
SEVERITIES = {"critical", "high", "medium", "low", "info"}
MAJOR_RISK_TERMS = {
    "relationship.green_hat",
    "relationship.sent_love_interest",
    "relationship.major_heroine_death",
    "relationship.betrayal",
    "protagonist.abuse",
    "protagonist.prolonged_frustration",
    "character.important_death",
    "ending.rushed",
    "ending.hiatus",
}

# Plain TXT headings need stricter handling than Markdown headings. ``回(?!合)``
# avoids treating prose such as "第二回合，开始" as a chapter heading.
CHAPTER_RE = re.compile(
    r"(?m)^[ \t]*(?:#{1,6}[ \t]*)?"
    r"(第[〇零一二三四五六七八九十百千万两0-9]+"
    r"(?:章|回(?!合)|卷|部|集)[^\n]{0,100})[ \t]*$"
)


@dataclass(frozen=True)
class Chapter:
    index: int
    source_number: str
    title: str
    content: str

    @property
    def chars(self) -> int:
        return len(self.title) + 1 + len(self.content)

    def to_payload(self) -> dict[str, Any]:
        return {
            "index": self.index,
            "source_number": self.source_number,
            "title": self.title,
            "content": self.content,
        }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--book", required=True, help="TXT novel path")
    parser.add_argument(
        "--stage",
        choices=("orientation", "full_scan"),
        default="orientation",
        help="Orientation reads first/last chapters; full_scan walks every missing chapter.",
    )
    parser.add_argument("--book-name", help="Override parsed book name")
    parser.add_argument("--author", help="Override parsed author")
    parser.add_argument(
        "--book-status",
        choices=("auto", "ongoing", "completed", "hiatus", "unknown"),
        default="auto",
    )
    parser.add_argument("--model", default=DEFAULT_MODEL)
    parser.add_argument("--api-url", default=DEFAULT_API_URL)
    parser.add_argument("--api-key-env", default="SILICONFLOW_API_KEY")
    parser.add_argument("--out", default=str(DEFAULT_OUT_DIR))
    parser.add_argument("--batch-chapters", type=int, default=8)
    parser.add_argument("--max-chars", type=int, default=60_000)
    parser.add_argument(
        "--max-batches",
        type=int,
        default=0,
        help="0 means no artificial batch limit.",
    )
    parser.add_argument("--orientation-count", type=int, default=3)
    parser.add_argument("--timeout", type=int, default=180)
    parser.add_argument("--retries", type=int, default=2)
    parser.add_argument("--retry-sleep", type=float, default=8.0)
    parser.add_argument("--sleep", type=float, default=0.3)
    parser.add_argument("--temperature", type=float, default=0.0)
    parser.add_argument("--max-tokens", type=int, default=5000)
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--reset", action="store_true")
    parser.add_argument(
        "--rebuild-report",
        action="store_true",
        help="Normalize an existing manifest with current rules and rewrite report without API calls.",
    )
    parser.add_argument(
        "--query",
        choices=sorted(DIMENSIONS) + ["critical", "coverage"],
        help="Read an existing local manifest without calling the API.",
    )
    return parser.parse_args()


def read_text(path: Path) -> str:
    data = path.read_bytes()
    for encoding in ("utf-8-sig", "utf-8", "gb18030"):
        try:
            return data.decode(encoding)
        except UnicodeDecodeError:
            continue
    raise ValueError(f"Unsupported text encoding: {path}")


def clean_source_text(text: str) -> str:
    return html.unescape(text).replace("\r\n", "\n").replace("\r", "\n")


def split_chapters(text: str) -> tuple[str, list[Chapter]]:
    text = clean_source_text(text)
    matches = list(CHAPTER_RE.finditer(text))
    if not matches:
        return "", [Chapter(0, "", "全文", text.strip())]
    preamble = text[: matches[0].start()].strip()
    chapters: list[Chapter] = []
    for pos, match in enumerate(matches):
        body_start = match.end()
        body_end = matches[pos + 1].start() if pos + 1 < len(matches) else len(text)
        title = match.group(1).strip().lstrip("#").strip()
        number = parse_source_number(title)
        chapters.append(
            Chapter(
                index=pos,
                source_number=number,
                title=title,
                content=text[body_start:body_end].strip(),
            )
        )
    return preamble, chapters


def parse_source_number(title: str) -> str:
    match = re.match(r"第\s*([^章节回卷部集]+)\s*(?:章|回|卷|部|集)", title)
    return match.group(1).strip() if match else ""


def infer_book_metadata(path: Path, preamble: str) -> tuple[str, str, str]:
    stem = path.stem.strip()
    author = ""
    name = stem
    match = re.match(r"(.+?)\s+作者[：:]\s*(.+)$", stem)
    if match:
        name, author = match.group(1).strip(), match.group(2).strip()
    if preamble:
        lines = [line.strip(" \t#") for line in preamble.splitlines() if line.strip()]
        for line in lines[:12]:
            author_match = re.match(r"作者[：:]\s*(.+)", line)
            if author_match:
                author = author_match.group(1).strip()
            title_match = re.match(r"(?:书名|小说名)[：:]\s*(.+)", line)
            if title_match:
                name = title_match.group(1).strip()
    synopsis = preamble[:6000]
    return name, author, synopsis


def infer_book_status(requested: str, chapters: Sequence[Chapter], preamble: str) -> str:
    if requested != "auto":
        return requested
    ending_text = (chapters[-1].title if chapters else "") + "\n" + preamble[-500:]
    if any(marker in ending_text for marker in ("大结局", "完结感言", "全文完", "完本")):
        return "completed"
    return "ongoing"


def extract_skill_body(content: str) -> str:
    if not content.startswith("---"):
        return content.strip()
    end = re.search(r"\r?\n---(?:\r?\n|$)", content[3:])
    if not end:
        return content.strip()
    return content[3 + end.end() :].strip()


def stable_book_dir(base: Path, path: Path, name: str, author: str) -> Path:
    digest = hashlib.sha256(str(path.resolve()).encode("utf-8")).hexdigest()[:10]
    slug = re.sub(r"[^0-9A-Za-z\u4e00-\u9fff_-]+", "_", f"{name}_{author}").strip("_")
    return base / f"{slug[:60] or 'book'}_{digest}"


def empty_manifest(
    path: Path,
    name: str,
    author: str,
    total: int,
    book_status: str,
) -> dict[str, Any]:
    return {
        "schema_version": 1,
        "source_path": str(path.resolve()),
        "book_name": name,
        "author": author,
        "work_key": f"{name}\n{author}",
        "total_chapters": total,
        "book_status": book_status,
        "coverage": {
            "observed_chapters": [],
            "fully_read_ranges": [],
            "missing_ranges": [[0, total]],
            "coverage_rate": 0.0,
            "completed": False,
        },
        "dimension_signals": {dimension: [] for dimension in sorted(DIMENSIONS)},
        "events": [],
        "unresolved": [],
        "batches": [],
        "usage": {"prompt_tokens": 0, "completion_tokens": 0, "total_tokens": 0},
    }


def load_manifest(path: Path, fallback: dict[str, Any], reset: bool) -> dict[str, Any]:
    if reset or not path.exists():
        return fallback
    existing = json.loads(path.read_text(encoding="utf-8"))
    if existing.get("source_path") != fallback["source_path"]:
        raise ValueError("Manifest source_path does not match current book")
    if existing.get("total_chapters") != fallback["total_chapters"]:
        raise ValueError("Chapter count changed; rerun with --reset for this v0 evaluator")
    existing.setdefault("book_status", fallback["book_status"])
    return normalize_existing_manifest(existing)


def normalize_existing_manifest(manifest: dict[str, Any]) -> dict[str, Any]:
    book_status = str(manifest.get("book_status") or "unknown")
    manifest["events"] = [
        normalize_event_terms(event, book_status)
        for event in manifest.get("events") or []
        if isinstance(event, dict)
    ]
    signals = manifest.setdefault("dimension_signals", {})
    for dimension in list(signals):
        signals[dimension] = [
            normalize_dimension_signal(signal, book_status)
            for signal in signals.get(dimension) or []
            if isinstance(signal, dict)
        ]
    return manifest


def ranges_from_indexes(indexes: Iterable[int]) -> list[list[int]]:
    values = sorted(set(indexes))
    if not values:
        return []
    ranges: list[list[int]] = []
    start = previous = values[0]
    for value in values[1:]:
        if value == previous + 1:
            previous = value
            continue
        ranges.append([start, previous + 1])
        start = previous = value
    ranges.append([start, previous + 1])
    return ranges


def missing_ranges(total: int, observed: Iterable[int]) -> list[list[int]]:
    present = set(observed)
    return ranges_from_indexes(index for index in range(total) if index not in present)


def orientation_batches(chapters: Sequence[Chapter], count: int) -> list[list[Chapter]]:
    count = max(1, count)
    indexes = list(range(min(count, len(chapters))))
    indexes += list(range(max(0, len(chapters) - count), len(chapters)))
    selected = [chapters[index] for index in sorted(set(indexes))]
    # Opening and ending are separate batches so very long books do not imply a
    # false continuous range between them.
    groups: list[list[Chapter]] = []
    current: list[Chapter] = []
    for chapter in selected:
        if current and chapter.index != current[-1].index + 1:
            groups.append(current)
            current = []
        current.append(chapter)
    if current:
        groups.append(current)
    return groups


def next_full_scan_batches(
    chapters: Sequence[Chapter],
    observed: set[int],
    max_chapters: int,
    max_chars: int,
) -> Iterable[list[Chapter]]:
    batch: list[Chapter] = []
    chars = 0
    for chapter in chapters:
        if chapter.index in observed:
            if batch:
                yield batch
                batch, chars = [], 0
            continue
        if batch and (len(batch) >= max_chapters or chars + chapter.chars > max_chars):
            yield batch
            batch, chars = [], 0
        batch.append(chapter)
        chars += chapter.chars
        if chapter.chars >= max_chars:
            yield batch
            batch, chars = [], 0
    if batch:
        yield batch


def build_user_prompt(
    *,
    manifest: dict[str, Any],
    synopsis: str,
    stage: str,
    chapters: Sequence[Chapter],
) -> str:
    payload = {
        "task": "book_scan_delta",
        "protocol_version": 1,
        "book": {
            "name": manifest["book_name"],
            "author": manifest["author"],
            "work_key": manifest["work_key"],
            "total_chapters": manifest["total_chapters"],
            "book_status": manifest["book_status"],
            "synopsis": synopsis,
        },
        "scan_stage": stage,
        "already_observed_ranges": manifest["coverage"]["fully_read_ranges"],
        "chapters": [chapter.to_payload() for chapter in chapters],
    }
    return (
        "这是 AI 扫书离线端到端评测。只分析 payload 中给出的完整章节。\n"
        "严格按 Skill 的 book_scan_delta 协议返回一个 JSON 对象，不要 Markdown、不要解释。\n"
        "observed_chapters 必须完整列出 payload.chapters 的 index；即使没有风险也要返回覆盖增量。\n"
        "不要因为本批没有发现，就输出全书 not_found。\n\n"
        + json.dumps(payload, ensure_ascii=False, separators=(",", ":"))
    )


def call_api(
    *,
    api_url: str,
    api_key: str,
    model: str,
    system_prompt: str,
    user_prompt: str,
    temperature: float,
    max_tokens: int,
    timeout: int,
    retries: int,
    retry_sleep: float,
) -> tuple[str, dict[str, int]]:
    body = {
        "model": model,
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_prompt},
        ],
        "temperature": temperature,
        "max_tokens": max_tokens,
        "response_format": {"type": "json_object"},
    }
    request = urllib.request.Request(
        api_url,
        data=json.dumps(body, ensure_ascii=False).encode("utf-8"),
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
        },
        method="POST",
    )
    for attempt in range(retries + 1):
        try:
            with urllib.request.urlopen(request, timeout=timeout) as response:
                result = json.loads(response.read().decode("utf-8"))
            content = result["choices"][0]["message"].get("content") or ""
            usage = result.get("usage") or {}
            return content, {
                "prompt_tokens": int(usage.get("prompt_tokens") or 0),
                "completion_tokens": int(usage.get("completion_tokens") or 0),
                "total_tokens": int(usage.get("total_tokens") or 0),
            }
        except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError, KeyError, ValueError) as error:
            if attempt >= retries:
                if isinstance(error, urllib.error.HTTPError):
                    detail = error.read().decode("utf-8", errors="replace")[:1000]
                    raise RuntimeError(f"HTTP {error.code}: {detail}") from error
                raise RuntimeError(str(error)) from error
            time.sleep(retry_sleep * (attempt + 1))
    raise AssertionError("unreachable")


def extract_json_object(content: str) -> dict[str, Any]:
    text = content.strip()
    text = re.sub(r"^```(?:json|book_scan_delta)?\s*", "", text, flags=re.I)
    text = re.sub(r"\s*```$", "", text)
    try:
        value = json.loads(text)
    except json.JSONDecodeError:
        start, end = text.find("{"), text.rfind("}")
        if start < 0 or end <= start:
            raise
        value = json.loads(text[start : end + 1])
    if not isinstance(value, dict):
        raise ValueError("Model response must be one JSON object")
    wrapped = value.get("book_scan_delta")
    if isinstance(wrapped, dict):
        value = wrapped
    return value


def validate_delta(
    raw: dict[str, Any],
    expected: Sequence[Chapter],
    manifest: dict[str, Any],
    stage: str,
) -> tuple[dict[str, Any], list[str]]:
    raw = repair_uniform_index_shift(raw, expected)
    errors: list[str] = []
    expected_indexes = {chapter.index for chapter in expected}
    if raw.get("schema_version") != 1:
        errors.append("schema_version must be 1")
    if raw.get("work_key") != manifest["work_key"]:
        errors.append("work_key mismatch")
    if raw.get("scan_stage") != stage:
        errors.append("scan_stage mismatch")
    if raw.get("book_status") != manifest.get("book_status"):
        errors.append("book_status mismatch")
    observed = raw.get("observed_chapters")
    if not isinstance(observed, list) or any(not isinstance(item, int) for item in observed):
        errors.append("observed_chapters must be an integer array")
        observed_set: set[int] = set()
    else:
        observed_set = set(observed)
        if observed_set != expected_indexes:
            errors.append(
                f"observed_chapters mismatch: expected={sorted(expected_indexes)} actual={sorted(observed_set)}"
            )

    signals = raw.get("dimension_signals")
    if not isinstance(signals, list):
        errors.append("dimension_signals must be an array")
        signals = []
    normalized_signals: list[dict[str, Any]] = []
    for pos, signal in enumerate(signals):
        if not isinstance(signal, dict):
            errors.append(f"dimension_signals[{pos}] must be an object")
            continue
        dimension = signal.get("dimension")
        if dimension not in DIMENSIONS:
            errors.append(f"dimension_signals[{pos}].dimension invalid: {dimension}")
            continue
        normalized_signals.append(
            normalize_dimension_signal(signal, str(manifest.get("book_status") or "unknown"))
        )

    events = raw.get("events")
    if not isinstance(events, list):
        errors.append("events must be an array")
        events = []
    normalized_events: list[dict[str, Any]] = []
    for pos, event in enumerate(events):
        if not isinstance(event, dict):
            errors.append(f"events[{pos}] must be an object")
            continue
        if not str(event.get("event_key") or "").strip():
            errors.append(f"events[{pos}].event_key is required")
        if event.get("status") not in EVENT_STATUSES:
            errors.append(f"events[{pos}].status invalid")
        if event.get("severity") not in SEVERITIES:
            errors.append(f"events[{pos}].severity invalid")
        indexes = event.get("chapter_indexes")
        if not isinstance(indexes, list) or any(item not in expected_indexes for item in indexes):
            errors.append(f"events[{pos}].chapter_indexes must reference this payload")
        normalized_events.append(
            normalize_event_terms(event, str(manifest.get("book_status") or "unknown"))
        )

    normalized = dict(raw)
    normalized["observed_chapters"] = sorted(observed_set & expected_indexes)
    normalized["dimension_signals"] = normalized_signals
    normalized["events"] = normalized_events
    unresolved = raw.get("unresolved")
    normalized["unresolved"] = [str(item).strip() for item in unresolved or [] if str(item).strip()]
    return normalized, errors


def repair_uniform_index_shift(
    raw: dict[str, Any],
    expected: Sequence[Chapter],
) -> dict[str, Any]:
    """Repair a whole-batch zero/one-based mix-up, never arbitrary indexes."""
    observed = raw.get("observed_chapters")
    expected_indexes = [chapter.index for chapter in expected]
    if not isinstance(observed, list) or any(not isinstance(item, int) for item in observed):
        return raw
    actual_indexes = sorted(set(observed))
    if len(actual_indexes) != len(expected_indexes) or actual_indexes == expected_indexes:
        return raw
    offsets = {expected_index - actual_index for actual_index, expected_index in zip(actual_indexes, expected_indexes)}
    if len(offsets) != 1:
        return raw
    offset = offsets.pop()
    if abs(offset) != 1:
        return raw
    mapping = dict(zip(actual_indexes, expected_indexes))
    repaired = dict(raw)
    repaired["observed_chapters"] = expected_indexes
    repaired_events: list[Any] = []
    for event in repaired.get("events") or []:
        if not isinstance(event, dict):
            repaired_events.append(event)
            continue
        item = dict(event)
        indexes = item.get("chapter_indexes")
        if isinstance(indexes, list) and all(isinstance(index, int) for index in indexes):
            item["chapter_indexes"] = [mapping.get(index, index + offset) for index in indexes]
        repaired_events.append(item)
    repaired["events"] = repaired_events
    return repaired


def normalize_dimension_signal(signal: dict[str, Any], book_status: str) -> dict[str, Any]:
    normalized = dict(signal)
    tags = [str(tag).strip() for tag in normalized.get("tags") or [] if str(tag).strip()]
    finding = str(normalized.get("finding") or "")
    if normalized.get("dimension") == "ending_safety" and book_status != "completed":
        if any("烂尾" in tag for tag in tags) or "烂尾" in finding:
            tags = [tag for tag in tags if "烂尾" not in tag]
            tags.append("当前未收束")
            finding = "当前文本末尾仍有未收束情节；作品未完结，不能据此判断烂尾。"
    normalized["tags"] = list(dict.fromkeys(tags))
    normalized["finding"] = finding
    return normalized


def normalize_event_terms(event: dict[str, Any], book_status: str = "unknown") -> dict[str, Any]:
    """Map extracted atomic facts to stable project terms.

    This is deliberately downstream of semantic extraction. It does not scan
    raw novel text for keywords; it only normalizes a model-created event and
    its structured attributes.
    """
    normalized = dict(event)
    event_type = str(normalized.get("event_type") or "")
    attributes = normalized.get("attributes") if isinstance(normalized.get("attributes"), dict) else {}
    fact = str(normalized.get("fact") or "")
    forced_marriage = event_type == "relationship_forced_marriage" or (
        attributes.get("forced") is True
        and any(marker in fact for marker in ("成婚", "拜堂", "嫁给", "婚姻"))
    )
    terms = [str(term).strip() for term in normalized.get("term_ids") or [] if str(term).strip()]
    if forced_marriage:
        event_type = "relationship_forced_marriage"
        terms = [term for term in terms if term != "relationship.missed_love_interest"]
        terms += ["relationship.green_hat", "relationship.sent_love_interest"]
        attributes = dict(attributes)
        attributes.setdefault("forced", True)
        attributes.setdefault("marriage", True)
    if event_type != "character_death":
        terms = [term for term in terms if term != "character.important_death"]
    if book_status != "completed":
        terms = [term for term in terms if term != "ending.rushed"]
    normalized["event_type"] = event_type
    normalized["term_ids"] = list(dict.fromkeys(terms))
    normalized["attributes"] = attributes
    if normalized.get("severity") in {"critical", "high"} and not (set(terms) & MAJOR_RISK_TERMS):
        normalized["severity"] = "medium"
    return normalized


def event_identity(event: dict[str, Any]) -> str:
    key = str(event.get("event_key") or "").strip()
    if key:
        return key
    canonical = json.dumps(
        {
            "type": event.get("event_type"),
            "terms": event.get("term_ids"),
            "chapters": event.get("chapter_indexes"),
            "fact": event.get("fact"),
        },
        ensure_ascii=False,
        sort_keys=True,
    )
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()[:16]


def merge_delta(
    manifest: dict[str, Any],
    delta: dict[str, Any],
    *,
    batch_file: str,
    usage: dict[str, int],
) -> None:
    observed = set(manifest["coverage"].get("observed_chapters") or [])
    observed.update(delta["observed_chapters"])
    total = int(manifest["total_chapters"])
    manifest["coverage"] = {
        "observed_chapters": sorted(observed),
        "fully_read_ranges": ranges_from_indexes(observed),
        "missing_ranges": missing_ranges(total, observed),
        "coverage_rate": round(len(observed) / total, 6) if total else 1.0,
        "completed": len(observed) == total,
    }
    for signal in delta.get("dimension_signals") or []:
        bucket = manifest["dimension_signals"].setdefault(signal["dimension"], [])
        identity = json.dumps(signal, ensure_ascii=False, sort_keys=True)
        if all(json.dumps(item, ensure_ascii=False, sort_keys=True) != identity for item in bucket):
            bucket.append(signal)

    event_map = {event_identity(event): event for event in manifest.get("events") or []}
    for event in delta.get("events") or []:
        identity = event_identity(event)
        current = event_map.get(identity)
        if current is None or float(event.get("confidence") or 0) >= float(current.get("confidence") or 0):
            event_map[identity] = event
    manifest["events"] = list(event_map.values())
    manifest["unresolved"] = list(
        dict.fromkeys((manifest.get("unresolved") or []) + (delta.get("unresolved") or []))
    )[-200:]
    manifest["batches"].append(
        {
            "stage": delta["scan_stage"],
            "chapters": delta["observed_chapters"],
            "summary": str(delta.get("batch_summary") or ""),
            "file": batch_file,
            "usage": usage,
            "created_at": int(time.time()),
        }
    )
    for key in ("prompt_tokens", "completion_tokens", "total_tokens"):
        manifest["usage"][key] = int(manifest["usage"].get(key) or 0) + int(usage.get(key) or 0)


def write_report(manifest: dict[str, Any], path: Path) -> None:
    coverage = manifest["coverage"]
    critical = [
        event
        for event in manifest.get("events") or []
        if event.get("severity") in {"critical", "high"}
        and event.get("status") in {"suspected", "confirmed"}
    ]
    lines = [
        f"# {manifest['book_name']} AI 扫书评测",
        "",
        f"- 作者：{manifest['author'] or '未知'}",
        f"- 覆盖：{len(coverage['observed_chapters'])}/{manifest['total_chapters']} "
        f"({coverage['coverage_rate']:.1%})",
        f"- 完整扫描：{'是' if coverage['completed'] else '否'}",
        f"- API 累计 Token：{manifest['usage']['total_tokens']}",
        "",
        "## 重大风险候选",
        "",
    ]
    if critical:
        for event in sorted(critical, key=lambda item: (item.get("severity") != "critical", -(item.get("confidence") or 0))):
            terms = "、".join(event.get("term_ids") or []) or event.get("event_type") or "未分类"
            lines.append(
                f"- **{terms}** [{event.get('status')}/{event.get('severity')}/"
                f"{float(event.get('confidence') or 0):.2f}]："
                f"{event.get('spoiler_safe_summary') or event.get('fact') or '未提供摘要'}"
            )
    else:
        lines.append("- 当前已扫描范围内没有记录 critical/high 候选；这不等于全书确定不存在。")
    lines += ["", "## 多维信号", ""]
    for dimension in sorted(DIMENSIONS):
        signals = manifest["dimension_signals"].get(dimension) or []
        if not signals:
            continue
        lines.append(f"### {dimension}")
        lines.append("")
        for signal in signals[-12:]:
            tags = "、".join(signal.get("tags") or [])
            finding = signal.get("finding") or ""
            lines.append(f"- {tags}：{finding}" if tags else f"- {finding}")
        lines.append("")
    lines += ["## 覆盖缺口", ""]
    if coverage["missing_ranges"]:
        lines.append(
            "- " + "、".join(f"{start}-{end - 1}" for start, end in coverage["missing_ranges"][:30])
        )
    else:
        lines.append("- 无")
    path.write_text("\n".join(lines).rstrip() + "\n", encoding="utf-8")


def query_manifest(manifest: dict[str, Any], query: str) -> None:
    if query == "coverage":
        print(json.dumps(manifest["coverage"], ensure_ascii=False, indent=2))
        return
    if query == "critical":
        value = [
            event
            for event in manifest.get("events") or []
            if event.get("severity") in {"critical", "high"}
        ]
    else:
        value = {
            "dimension": query,
            "signals": manifest["dimension_signals"].get(query) or [],
            "events": [
                event
                for event in manifest.get("events") or []
                if query == "relationship"
                and any(str(term).startswith("relationship.") for term in event.get("term_ids") or [])
                or query == "protagonist_experience"
                and any(str(term).startswith("protagonist.") for term in event.get("term_ids") or [])
                or query == "ending_safety"
                and any(str(term).startswith("ending.") for term in event.get("term_ids") or [])
            ],
        }
    print(json.dumps(value, ensure_ascii=False, indent=2))


def main() -> int:
    args = parse_args()
    book_path = Path(args.book).expanduser().resolve()
    if not book_path.is_file():
        print(f"Book not found: {book_path}", file=sys.stderr)
        return 2
    preamble, chapters = split_chapters(read_text(book_path))
    inferred_name, inferred_author, synopsis = infer_book_metadata(book_path, preamble)
    name = args.book_name or inferred_name
    author = args.author if args.author is not None else inferred_author
    book_status = infer_book_status(args.book_status, chapters, preamble)
    out_dir = stable_book_dir(Path(args.out).resolve(), book_path, name, author)
    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / "requests").mkdir(exist_ok=True)
    (out_dir / "shards").mkdir(exist_ok=True)
    manifest_path = out_dir / "manifest.json"
    manifest = load_manifest(
        manifest_path,
        empty_manifest(book_path, name, author, len(chapters), book_status),
        args.reset,
    )

    print(
        f"book={name} author={author or '未知'} chapters={len(chapters)} "
        f"status={book_status} preamble_chars={len(preamble)}"
    )
    print(f"first={chapters[0].title!r} last={chapters[-1].title!r} out={out_dir}")
    if args.rebuild_report:
        if not manifest_path.exists():
            print("No existing manifest to rebuild.", file=sys.stderr)
            return 2
        manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
        write_report(manifest, out_dir / "report.md")
        print("normalized existing manifest and rebuilt report without API calls")
        return 0
    if args.query:
        if not manifest_path.exists():
            print("No existing manifest. Run an orientation or full scan first.", file=sys.stderr)
            return 2
        query_manifest(manifest, args.query)
        return 0

    if args.batch_chapters < 1 or args.max_chars < 1000:
        raise ValueError("--batch-chapters must be >=1 and --max-chars must be >=1000")
    if args.stage == "orientation":
        already_observed = set(manifest["coverage"].get("observed_chapters") or [])
        batches = [
            missing
            for group in orientation_batches(chapters, args.orientation_count)
            if (missing := [chapter for chapter in group if chapter.index not in already_observed])
        ]
    else:
        batches = next_full_scan_batches(
            chapters,
            set(manifest["coverage"].get("observed_chapters") or []),
            args.batch_chapters,
            args.max_chars,
        )
    batches = list(batches)
    if args.max_batches > 0:
        batches = batches[: args.max_batches]
    if not batches:
        print("No missing chapters for this stage.")
        write_report(manifest, out_dir / "report.md")
        return 0

    system_prompt = extract_skill_body(SKILL_FILE.read_text(encoding="utf-8"))
    api_key = os.environ.get(args.api_key_env, "").strip()
    if not args.dry_run and not api_key:
        print(
            f"Missing API key environment variable: {args.api_key_env}. "
            "Use --dry-run to only validate parsing/request generation.",
            file=sys.stderr,
        )
        return 2

    failures = 0
    for batch_index, batch in enumerate(batches, start=1):
        indexes = [chapter.index for chapter in batch]
        label = f"{indexes[0]:06d}-{indexes[-1]:06d}"
        user_prompt = build_user_prompt(
            manifest=manifest,
            synopsis=synopsis,
            stage=args.stage,
            chapters=batch,
        )
        request_file = out_dir / "requests" / f"{args.stage}_{label}.json"
        request_file.write_text(
            json.dumps(
                {
                    "model": args.model,
                    "messages": [
                        {"role": "system", "content": system_prompt},
                        {"role": "user", "content": user_prompt},
                    ],
                },
                ensure_ascii=False,
                indent=2,
            ),
            encoding="utf-8",
        )
        print(
            f"[{batch_index}/{len(batches)}] stage={args.stage} chapters={indexes[0]}-{indexes[-1]} "
            f"chars={sum(chapter.chars for chapter in batch)}"
        )
        if args.dry_run:
            continue
        try:
            content, usage = call_api(
                api_url=args.api_url,
                api_key=api_key,
                model=args.model,
                system_prompt=system_prompt,
                user_prompt=user_prompt,
                temperature=args.temperature,
                max_tokens=args.max_tokens,
                timeout=args.timeout,
                retries=args.retries,
                retry_sleep=args.retry_sleep,
            )
            raw = extract_json_object(content)
            delta, errors = validate_delta(raw, batch, manifest, args.stage)
            shard_file = out_dir / "shards" / f"{args.stage}_{label}.json"
            shard_file.write_text(
                json.dumps(
                    {"delta": delta, "validation_errors": errors, "usage": usage, "raw": content},
                    ensure_ascii=False,
                    indent=2,
                ),
                encoding="utf-8",
            )
            if errors:
                failures += 1
                print("  validation failed: " + "; ".join(errors), file=sys.stderr)
                continue
            merge_delta(
                manifest,
                delta,
                batch_file=str(shard_file.relative_to(out_dir)),
                usage=usage,
            )
            manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
            write_report(manifest, out_dir / "report.md")
            print(
                f"  saved coverage={len(manifest['coverage']['observed_chapters'])}/"
                f"{manifest['total_chapters']} events={len(manifest['events'])}"
            )
        except Exception as error:  # noqa: BLE001 - CLI must checkpoint other batches
            failures += 1
            error_file = out_dir / "shards" / f"{args.stage}_{label}.error.txt"
            error_file.write_text(str(error), encoding="utf-8")
            print(f"  failed: {error}", file=sys.stderr)
        if args.sleep > 0:
            time.sleep(args.sleep)

    if args.dry_run:
        print(
            f"dry-run complete; generated {len(batches)} request file(s); "
            "manifest was not modified"
        )
    else:
        print(f"complete failures={failures} manifest={manifest_path}")
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
