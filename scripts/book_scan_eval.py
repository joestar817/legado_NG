#!/usr/bin/env python3
"""Run a resumable AI book-scan evaluation against an OpenAI-compatible API.

The evaluator deliberately separates three layers:

* ``plan`` validates TXT decoding, chapter parsing, metadata and the fixed
  physical first-10/last-10 quick-scan plan without calling a model.
* ``simulated-agent`` loads the real built-in ``book_scan`` Skill, runs an
  OpenAI-compatible tool loop, and provides a small evaluator-local MCP
  adapter. Tool calls, raw responses, receipts and generic Agent memories are
  archived exactly; this mode does not claim to exercise the App database,
  Android Hook runtime or UI renderer.
* Real App behavior must still be verified from an exported device trace. The
  report states this limit instead of fabricating an App success result.

The evaluator never repairs model output, remaps risk terms, shifts chapter
indexes or reconstructs a private ``book_scan_delta``. It preserves raw output
and records protocol/quality warnings for later comparison.

Examples (PowerShell):

  python scripts/book_scan_eval.py --book "C:\\novel.txt" --mode plan
  $env:SILICONFLOW_API_KEY = "..."
  python scripts/book_scan_eval.py --book "C:\\novel.txt" --mode simulated-agent
  python scripts/book_scan_eval.py --book "C:\\novel.txt" --query warnings
"""

from __future__ import annotations

import argparse
import hashlib
import html
import http.client
import json
import os
import re
import shutil
import sys
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable, Sequence


ROOT = Path(__file__).resolve().parents[1]
SKILL_FILE = ROOT / "app/src/main/assets/skills/book_scan/SKILL.md"
READER_TAG_DIR = ROOT / "app/src/main/assets/skills/book_scan/references/reader-tags"
DEFAULT_SKILL_PACKAGE = SKILL_FILE.parent
DEFAULT_OUT_DIR = ROOT / "build/book_scan_eval"
DEFAULT_API_URL = "https://api.siliconflow.cn/v1/chat/completions"
DEFAULT_MODEL = "Qwen/Qwen3-8B"
RUN_SCHEMA_VERSION = 2
EVALUATION_MODE = "simulated_mcp"

PERSONALIZATION_SCHEMA_VERSION = 2
PROFILE_ANSWER_TYPES = {"single", "multi", "unrestricted"}
PROFILE_STANCES = {"like", "neutral", "avoid"}
PREFERENCE_OPTION_LABELS = {
    "like": "更偏好这类作品",
    "neutral": "可以接受",
    "avoid": "会因此劝退",
}
PROFILE_SOURCE_KINDS = {"ui_choice", "explicit_text"}
PROFILE_DIMENSION_VALUES = {
    "relationship_routes": {
        "route.no_romance",
        "route.single_partner",
        "route.multi_partner",
    },
    "narrative_centers": {
        "center.single_male",
        "center.multi_male_ensemble",
    },
    "defense_level": {
        "defense.god",
        "defense.heavy",
        "defense.cloth",
        "defense.low",
        "defense.negative",
    },
}
STABLE_TAG_ID_RE = re.compile(r"^[a-z][a-z0-9_]*(?:\.[a-z0-9_]+)+$")
TAG_HIT_STATUSES = {"confirmed", "suspected"}
TAG_HIT_INTENSITIES = {"low", "medium", "high"}
CATEGORY_DIMENSIONS = {"attitude", "action", "relationship", "outcome"}
CATEGORY_STANCES = {"prefer", "avoid", "monitor"}
CATEGORY_PRIORITIES = {"high", "medium", "low"}
RISK_BASE_LEVELS = {"god_risk", "risk", "frustrating"}
RISK_EVIDENCE_STATUSES = {
    "confirmed",
    "ongoing",
    "threatened",
    "outcome_unknown",
    "reversed",
}
FIT_LEVELS = {"high_match", "match", "tradeoff", "mismatch", "avoid", "insufficient"}
FIT_CONFIDENCE_LEVELS = {"high", "medium", "low"}

WATCH_TOOLS = {"bookshelf_text_window_get", "bookshelf_chapter_content_get"}
NAVIGATION_TOOLS = {"bookshelf_chapter_snippets_get"}
MEMORY_WRITE_TOOLS = {"agent_memory_upsert", "agent_memory_batch_upsert"}
SUPPORTED_TOOLS = {
    "use_skill",
    "bookshelf_current_book_get",
    "bookshelf_book_get",
    "bookshelf_chapter_list",
    "bookshelf_cache_status_get",
    "bookshelf_text_window_get",
    "bookshelf_chapter_content_get",
    "bookshelf_chapter_snippets_get",
    "agent_memory_status_get",
    "agent_memory_search",
    "agent_memory_upsert",
    "agent_memory_batch_upsert",
}

MARKDOWN_LINK_RE = re.compile(r"\[[^\]]+\]\(([^)]+)\)")

# Plain TXT headings need stricter handling than Markdown headings. ``回(?!合)``
# avoids treating prose such as "第二回合，开始" as a chapter heading. Some
# exported novels append alternate-timeline chapters as standalone headings such
# as ``IF·0·1``; keep that deliberately narrow so ordinary English prose is not
# mistaken for a chapter boundary.
CHAPTER_RE = re.compile(
    r"(?m)^[ \t]*(?:#{1,6}[ \t]*)?"
    r"((?:第[〇零一二三四五六七八九十百千万两0-9]+"
    r"(?:章|回(?!合)|卷|部|集)[^\n]{0,100})|"
    r"(?:IF[·.．][0-9]+[·.．][0-9]+))[ \t]*$"
)
INTERACTION_BLOCK_RE = re.compile(
    r"(?is)```[ \t]*legado-interaction[^\r\n]*\r?\n.*?\r?\n```"
)
LEGACY_PRIVATE_BLOCK_RE = re.compile(
    r"(?is)```[ \t]*(?:book_scan_delta|legado-book-scan)[^\r\n]*\r?\n.*?\r?\n```"
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

    def to_payload(self, *, include_content: bool = True) -> dict[str, Any]:
        value: dict[str, Any] = {
            "index": self.index,
            "source_number": self.source_number,
            "title": self.title,
        }
        if include_content:
            value["content"] = self.content
        return value


@dataclass(frozen=True)
class SkillPackage:
    root: Path
    metadata: dict[str, str]
    entry_body: str
    package_hash: str
    files: tuple[str, ...]

    @property
    def name(self) -> str:
        return self.metadata.get("name") or self.metadata.get("id") or self.root.name

    def read(self, relative_path: str | None = None) -> str:
        normalized = "SKILL.md" if not relative_path else normalize_skill_path(relative_path)
        target = (self.root / normalized).resolve()
        root = self.root.resolve()
        if target != root and root not in target.parents:
            raise ValueError(f"Skill resource escapes package: {relative_path}")
        if not target.is_file():
            raise ValueError(f"Skill resource not found: {normalized}")
        content = target.read_text(encoding="utf-8")
        if normalized == "SKILL.md":
            return parse_frontmatter(content)[1]
        return content

    def read_declaration(self, metadata_key: str) -> dict[str, Any] | None:
        relative_path = self.metadata.get(metadata_key, "").strip()
        if not relative_path:
            return None
        normalized = normalize_skill_path(relative_path)
        if normalized not in self.files:
            raise ValueError(f"Skill declaration not found: {metadata_key}={normalized}")
        value = json.loads((self.root / normalized).read_text(encoding="utf-8"))
        if not isinstance(value, dict):
            raise ValueError(f"Skill declaration must be a JSON object: {normalized}")
        return value


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--book", required=True, help="TXT novel path")
    parser.add_argument(
        "--mode",
        choices=("plan", "simulated-agent"),
        default="simulated-agent",
        help="plan is deterministic; simulated-agent runs a model with local MCP adapters.",
    )
    parser.add_argument(
        "--stage",
        choices=("orientation", "full_scan"),
        default="orientation",
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
    parser.add_argument("--target-chapters", type=int, default=100)
    parser.add_argument("--max-steps", type=int, default=24)
    parser.add_argument("--timeout", type=int, default=180)
    parser.add_argument("--retries", type=int, default=2)
    parser.add_argument("--retry-sleep", type=float, default=8.0)
    parser.add_argument("--sleep", type=float, default=0.2)
    parser.add_argument("--temperature", type=float, default=0.0)
    parser.add_argument("--max-tokens", type=int, default=6000)
    parser.add_argument(
        "--skill-mode",
        choices=("single", "multifile"),
        default="multifile",
        help=(
            "single injects the coordinator body but still exposes its companion facts/report Skills; "
            "multifile loads the coordinator and companions progressively through evaluator-local use_skill."
        ),
    )
    parser.add_argument(
        "--skill-package",
        default=str(DEFAULT_SKILL_PACKAGE),
        help="Directory containing SKILL.md and referenced resources for multifile mode.",
    )
    parser.add_argument(
        "--reader-profile",
        default="",
        help="Optional confirmed global_profile JSON seeded into evaluator-local AgentMemory.",
    )
    parser.add_argument(
        "--reuse-book-memories",
        default="",
        help=(
            "Optional prior evaluator directory or memories.json. Copies only matching "
            "book-scoped book_scan facts before the run so a different profile can rerender without rereading text."
        ),
    )
    parser.add_argument(
        "--quick-scan-chapters",
        type=int,
        default=10,
        help="Expected physical chapter count at each end; the loaded Skill must request the same value.",
    )
    parser.add_argument(
        "--experiment-label",
        default="",
        help="Optional output subdirectory label, e.g. A_single_v31 or B_multifile_same.",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Alias for plan mode; writes plans/requests but never calls a model.",
    )
    parser.add_argument("--reset", action="store_true")
    parser.add_argument(
        "--rebuild-report",
        action="store_true",
        help="Rewrite report.md from archived evaluation.json without API calls.",
    )
    parser.add_argument(
        "--query",
        choices=("report", "warnings", "tool_calls", "memories", "coverage", "usage", "run", "raw"),
        help="Read archived evaluator artifacts without calling the API.",
    )
    parser.add_argument("--json", action="store_true", help="Print plan output as JSON")
    return parser.parse_args()


def read_text(path: Path) -> str:
    data = path.read_bytes()
    for encoding in ("utf-8-sig", "utf-8", "gb18030"):
        try:
            return data.decode(encoding)
        except UnicodeDecodeError:
            continue
    raise ValueError(f"Unsupported text encoding: {path}")


def load_dotenv(path: Path) -> None:
    if not path.is_file():
        return
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        if key and key not in os.environ:
            os.environ[key] = value.strip().strip('"').strip("'")


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
        chapters.append(
            Chapter(
                index=pos,
                source_number=parse_source_number(title),
                title=title,
                content=text[body_start:body_end].strip(),
            )
        )
    return preamble, chapters


def parse_source_number(title: str) -> str:
    match = re.match(r"第\s*([^章节回卷部集]+)\s*(?:章|回|卷|部|集)", title)
    if match:
        return match.group(1).strip()
    if re.fullmatch(r"IF[·.．][0-9]+[·.．][0-9]+", title, flags=re.IGNORECASE):
        return title
    return ""


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
    return name, author, preamble[:6000]


def infer_book_status(requested: str, chapters: Sequence[Chapter], preamble: str) -> str:
    if requested != "auto":
        return requested
    # Completed works may append IF/extra chapters after the canonical ending,
    # so the physical last title alone is not a reliable completion marker.
    ending_text = "\n".join(chapter.title for chapter in chapters) + "\n" + preamble[-500:]
    if any(
        marker in ending_text
        for marker in ("大结局", "完结感言", "全文完", "完本", "状态：已完结", "状态: 已完结")
    ):
        return "completed"
    return "ongoing"


def parse_frontmatter(content: str) -> tuple[dict[str, str], str]:
    if not content.startswith("---"):
        return {}, content.strip()
    end = re.search(r"\r?\n---(?:\r?\n|$)", content[3:])
    if not end:
        return {}, content.strip()
    metadata: dict[str, str] = {}
    for line in content[3 : 3 + end.start()].strip().splitlines():
        key, separator, value = line.partition(":")
        if separator and key.strip():
            metadata[key.strip()] = value.strip().strip('"')
    return metadata, content[3 + end.end() :].strip()


def normalize_skill_path(path: str) -> str:
    value = path.strip().replace("\\", "/")
    if not value or value.startswith("/"):
        raise ValueError("Skill resource path must be relative")
    parts = [part for part in value.split("/") if part and part != "."]
    if not parts or any(part == ".." for part in parts):
        raise ValueError("Skill resource path contains traversal")
    return "/".join(parts)


def load_skill_package(root: Path) -> SkillPackage:
    package_root = root.expanduser().resolve()
    entry = package_root / "SKILL.md"
    if not entry.is_file():
        raise ValueError(f"Skill package is missing SKILL.md: {package_root}")
    files = tuple(
        sorted(
            path.relative_to(package_root).as_posix()
            for path in package_root.rglob("*")
            if path.is_file()
        )
    )
    digest = hashlib.sha256()
    for relative_path in files:
        digest.update(relative_path.encode("utf-8"))
        digest.update(b"\0")
        digest.update((package_root / relative_path).read_bytes())
        digest.update(b"\0")
    content = entry.read_text(encoding="utf-8")
    metadata, body = parse_frontmatter(content)
    if not (metadata.get("name") or metadata.get("id")):
        raise ValueError("Skill package SKILL.md requires name or id")
    if not metadata.get("description"):
        raise ValueError("Skill package SKILL.md requires description")
    return SkillPackage(package_root, metadata, body, digest.hexdigest(), files)


def markdown_resource_links(content: str) -> set[str]:
    paths: set[str] = set()
    for raw in MARKDOWN_LINK_RE.findall(content):
        candidate = raw.strip().split("#", 1)[0].strip()
        if not candidate or "://" in candidate or candidate.startswith("#"):
            continue
        paths.add(normalize_skill_path(candidate))
    return paths


def stable_book_dir(base: Path, path: Path, name: str, author: str) -> Path:
    digest = hashlib.sha256(str(path.resolve()).encode("utf-8")).hexdigest()[:10]
    slug = re.sub(r"[^0-9A-Za-z\u4e00-\u9fff_-]+", "_", f"{name}_{author}").strip("_")
    return base / f"{slug[:60] or 'book'}_{digest}"


def fast_scan_ranges(total_count: int, edge_count: int = 10) -> list[tuple[int, int]]:
    """Return end-exclusive physical head/tail ranges required by the Skill."""
    if total_count <= 0 or edge_count <= 0:
        return []
    if total_count < edge_count * 2:
        return [(0, total_count)]
    return [(0, edge_count), (total_count - edge_count, total_count)]


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


def stable_json_hash(value: Any) -> str:
    encoded = json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(encoded.encode("utf-8")).hexdigest()


def personalization_contract_violations(
    kind: str,
    value: dict[str, Any],
) -> list[str]:
    """Validate BookScan personalization semantics outside the App kernel."""
    violations: list[str] = []
    if kind == "global_profile":
        if value.get("schema_version") != PERSONALIZATION_SCHEMA_VERSION:
            violations.append("global_profile schema_version must be 2")
        if value.get("profile_id") != "default":
            violations.append("global_profile profile_id must be default")
        dimensions = value.get("dimension_answers")
        if not isinstance(dimensions, dict):
            violations.append("dimension_answers must be an object")
        else:
            for dimension_id, answer in dimensions.items():
                allowed_values = PROFILE_DIMENSION_VALUES.get(str(dimension_id))
                if allowed_values is None:
                    violations.append(f"dimension_answers.{dimension_id} is unknown")
                    continue
                if not isinstance(answer, dict):
                    violations.append(f"dimension_answers.{dimension_id} must be an object")
                    continue
                answer_type = answer.get("answer_type")
                values = answer.get("values")
                if answer_type not in PROFILE_ANSWER_TYPES:
                    violations.append(f"dimension_answers.{dimension_id}.answer_type is invalid")
                if not isinstance(values, list):
                    violations.append(f"dimension_answers.{dimension_id}.values must be a list")
                else:
                    if answer_type == "unrestricted" and values:
                        violations.append(
                            f"dimension_answers.{dimension_id}.values must be empty when unrestricted"
                        )
                    if answer_type in {"single", "multi"} and not values:
                        violations.append(
                            f"dimension_answers.{dimension_id}.values must be non-empty"
                        )
                    if answer_type == "single" and len(values) != 1:
                        violations.append(
                            f"dimension_answers.{dimension_id}.values must contain exactly one value"
                        )
                    invalid_values = [item for item in values if item not in allowed_values]
                    if invalid_values:
                        violations.append(
                            f"dimension_answers.{dimension_id}.values contains invalid ids: {invalid_values}"
                        )
                if dimension_id == "defense_level" and answer_type not in {"single", "unrestricted"}:
                    violations.append("defense_level must be single or unrestricted")
                if dimension_id != "defense_level" and answer_type not in {"multi", "unrestricted"}:
                    violations.append(f"{dimension_id} must be multi or unrestricted")
                _append_profile_provenance_violations(
                    violations,
                    answer,
                    f"dimension_answers.{dimension_id}",
                )
        tag_stances = value.get("tag_stances")
        if not isinstance(tag_stances, dict):
            violations.append("tag_stances must be an object")
        else:
            for tag_id, stance in tag_stances.items():
                if not STABLE_TAG_ID_RE.fullmatch(str(tag_id)):
                    violations.append(f"tag_stances contains invalid stable id: {tag_id}")
                if not isinstance(stance, dict):
                    violations.append(f"tag_stances.{tag_id} must be an object")
                    continue
                if stance.get("stance") not in PROFILE_STANCES:
                    violations.append(f"tag_stances.{tag_id}.stance is invalid")
                _append_profile_provenance_violations(
                    violations,
                    stance,
                    f"tag_stances.{tag_id}",
                )
        custom_rules = value.get("custom_rules")
        if not isinstance(custom_rules, list):
            violations.append("custom_rules must be a list")
        else:
            for index, rule in enumerate(custom_rules):
                if not isinstance(rule, dict):
                    violations.append(f"custom_rules[{index}] must be an object")
                    continue
                if not isinstance(rule.get("text"), str) or not rule.get("text", "").strip():
                    violations.append(f"custom_rules[{index}].text must be non-empty")
                if not isinstance(rule.get("rule_id"), str) or not rule.get("rule_id", "").strip():
                    violations.append(f"custom_rules[{index}].rule_id must be non-empty")
                scope = str(rule.get("scope") or "")
                if scope != "global" and not scope.startswith("category:"):
                    violations.append(f"custom_rules[{index}].scope is invalid")
                if rule.get("stance") not in CATEGORY_STANCES:
                    violations.append(f"custom_rules[{index}].stance is invalid")
                if not isinstance(rule.get("source_text"), str) or not rule.get("source_text", "").strip():
                    violations.append(f"custom_rules[{index}].source_text must be non-empty")
                if rule.get("confirmed_by_user") is not True:
                    violations.append(f"custom_rules[{index}].confirmed_by_user must be true")
        return violations

    if kind == "category_profile":
        if value.get("schema_version") != 1:
            violations.append("category_profile schema_version must be 1")
        category_id = value.get("category_id")
        if not isinstance(category_id, str) or not category_id.strip():
            violations.append("category_id must be a non-empty string")
        if value.get("profile_id") != f"category:{category_id}":
            violations.append("category profile_id must be category:<category_id>")
        rules = value.get("rules")
        if not isinstance(rules, list) or not rules:
            violations.append("category_profile rules must be a non-empty list")
        else:
            for index, rule in enumerate(rules):
                if not isinstance(rule, dict):
                    violations.append(f"rules[{index}] must be an object")
                    continue
                if not isinstance(rule.get("target"), str) or not rule.get("target", "").strip():
                    violations.append(f"rules[{index}].target must be non-empty")
                if rule.get("dimension") not in CATEGORY_DIMENSIONS:
                    violations.append(f"rules[{index}].dimension is invalid")
                if rule.get("stance") not in CATEGORY_STANCES:
                    violations.append(f"rules[{index}].stance is invalid")
                if not isinstance(rule.get("expected_outcome"), str):
                    violations.append(f"rules[{index}].expected_outcome must be a string")
                if rule.get("priority") not in CATEGORY_PRIORITIES:
                    violations.append(f"rules[{index}].priority is invalid")
        if not isinstance(value.get("source_text"), str) or not value.get("source_text", "").strip():
            violations.append("source_text must preserve confirmed user wording")
        if value.get("confirmed_by_user") is not True:
            violations.append("confirmed_by_user must be true")
        return violations

    if kind == "risk":
        if value.get("schema_version") != 1:
            violations.append("risk schema_version must be 1")
        if not isinstance(value.get("risk_kind"), str) or not value.get("risk_kind", "").strip():
            violations.append("risk_kind must be a non-empty stable id")
        if value.get("base_level") not in RISK_BASE_LEVELS:
            violations.append("base_level is invalid")
        if value.get("evidence_status") not in RISK_EVIDENCE_STATUSES:
            violations.append("evidence_status is invalid")
        if not isinstance(value.get("relationship_context"), dict):
            violations.append("relationship_context must be an object")
        if not isinstance(value.get("reader_consequence"), dict):
            violations.append("reader_consequence must be an object")
        if not isinstance(value.get("evidence_refs"), list) or not value.get("evidence_refs"):
            violations.append("evidence_refs must be a non-empty list")
        return violations

    if kind == "fit_assessment":
        if value.get("schema_version") != 1:
            violations.append("fit_assessment schema_version must be 1")
        if value.get("fit_level") not in FIT_LEVELS:
            violations.append("fit_level is invalid")
        for field in ("match_evidence", "conflict_evidence", "decisive_unknowns"):
            if not isinstance(value.get(field), list):
                violations.append(f"{field} must be a list")
        if value.get("confidence") not in FIT_CONFIDENCE_LEVELS:
            violations.append("confidence is invalid")
        if not isinstance(value.get("coverage_boundary"), str) or not value.get(
            "coverage_boundary", ""
        ).strip():
            violations.append("coverage_boundary must be non-empty")
        return violations

    if kind == "tag_hit":
        if not STABLE_TAG_ID_RE.fullmatch(str(value.get("tag_id") or "")):
            violations.append("tag_hit tag_id must be a stable id")
        if value.get("detection_status") not in TAG_HIT_STATUSES:
            violations.append("tag_hit detection_status is invalid")
        if value.get("intensity") not in TAG_HIT_INTENSITIES:
            violations.append("tag_hit intensity is invalid")
        if not isinstance(value.get("evidence_refs"), list) or not value.get("evidence_refs"):
            violations.append("tag_hit evidence_refs must be a non-empty list")
        return violations

    return [f"unknown personalization contract kind: {kind}"]


def _append_profile_provenance_violations(
    violations: list[str],
    value: dict[str, Any],
    field: str,
) -> None:
    if value.get("source_kind") not in PROFILE_SOURCE_KINDS:
        violations.append(f"{field}.source_kind is invalid")
    if not isinstance(value.get("source_text"), str) or not value.get("source_text", "").strip():
        violations.append(f"{field}.source_text must preserve explicit user wording")
    if value.get("confirmed_by_user") is not True:
        violations.append(f"{field}.confirmed_by_user must be true")


def load_reader_profile(path: Path) -> dict[str, Any]:
    value = read_json(path)
    if not isinstance(value, dict):
        raise ValueError(f"Reader profile must be a JSON object: {path}")
    violations = personalization_contract_violations("global_profile", value)
    if violations:
        raise ValueError("Invalid reader profile: " + "; ".join(violations))
    return value


def load_reusable_book_memories(path: Path, work_key: str) -> list[dict[str, Any]]:
    source = path.expanduser().resolve()
    if source.is_dir():
        source = source / "memories.json"
    value = read_json(source)
    if not isinstance(value, list):
        raise ValueError(f"Reusable memories must be a JSON array: {source}")
    memories = [
        dict(item)
        for item in value
        if isinstance(item, dict)
        and str(item.get("scope_type") or "") == "book"
        and str(item.get("scope_key") or "") == work_key
        and str(item.get("domain") or "") == "book_scan"
    ]
    types = {str(item.get("memory_type") or "") for item in memories}
    if "manifest" not in types or "window_bundle" not in types:
        raise ValueError(
            f"Reusable memories must contain a manifest and window_bundle for work_key={work_key!r}"
        )
    return memories


def discover_companion_skill_packages(primary: SkillPackage | None) -> list[SkillPackage]:
    if primary is None or str(primary.metadata.get("id") or "") != "book_scan":
        return []
    packages: list[SkillPackage] = []
    for skill_id in ("book_scan_facts", "book_scan_report"):
        root = primary.root.parent / skill_id
        if (root / "SKILL.md").is_file():
            packages.append(load_skill_package(root))
    return packages


def read_json(path: Path, fallback: Any = None) -> Any:
    if not path.exists():
        return fallback
    return json.loads(path.read_text(encoding="utf-8"))


def write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding="utf-8")
    temporary.replace(path)


def plan_document(
    *,
    book_path: Path,
    name: str,
    author: str,
    status: str,
    chapters: Sequence[Chapter],
    skill_metadata: dict[str, str],
    skill_hash: str,
    quick_scan_chapters: int = 10,
    skill_mode: str = "single",
) -> dict[str, Any]:
    ranges = fast_scan_ranges(len(chapters), quick_scan_chapters)
    return {
        "schema_version": RUN_SCHEMA_VERSION,
        "mode": "plan",
        "skill_mode": skill_mode,
        "source_path": str(book_path),
        "book_name": name,
        "author": author,
        "book_status": status,
        "total_count": len(chapters),
        "skill": {
            "id": skill_metadata.get("id"),
            "version": skill_metadata.get("version"),
            "sha256": skill_hash,
        },
        "fast_scan_ranges": [
            {
                "start": start,
                "end": end,
                "first_title": chapters[start].title,
                "last_title": chapters[end - 1].title,
            }
            for start, end in ranges
        ],
        "rule": (
            f"首次快速定位固定读取物理开头{quick_scan_chapters}章与物理末尾"
            f"{quick_scan_chapters}章；不足{quick_scan_chapters * 2}章时合并重叠范围。"
        ),
        "limitations": [
            "plan 模式不调用模型、不执行 MCP、不验证 AgentMemory 或 App 渲染。",
        ],
    }


def openai_tools(
    *,
    include_use_skill: bool = False,
) -> list[dict[str, Any]]:
    def tool(name: str, description: str, properties: dict[str, Any], required: Sequence[str] = ()) -> dict[str, Any]:
        schema: dict[str, Any] = {
            "type": "object",
            "properties": properties,
            "additionalProperties": True,
        }
        if required:
            schema["required"] = list(required)
        return {
            "type": "function",
            "function": {"name": name, "description": description, "parameters": schema},
        }

    string = lambda description: {"type": "string", "description": description}
    integer = lambda description: {"type": "integer", "description": description}
    boolean = lambda description: {"type": "boolean", "description": description}
    memory_properties = {
        "id": string("Existing memory id; omit only when creating"),
        "scope_type": string("Concrete object type, for example book"),
        "scope_key": string("Stable object key"),
        "subject": string("Human-readable subject"),
        "domain": string("Business domain"),
        "memory_type": string("Memory type"),
        "title": string("Short title"),
        "content": string("Concise semantic content"),
        "data": {"type": "object", "additionalProperties": True},
        "tags": {"type": "array", "items": {"type": "string"}},
        "confidence": {"type": "number"},
        "source": string("Memory source"),
        "status": string("Memory status"),
        "source_receipt_ids": {"type": "array", "items": {"type": "string"}},
    }
    tools = [
        tool("bookshelf_current_book_get", "Get the evaluator's current local book.", {}),
        tool(
            "bookshelf_book_get",
            "Get one local book by stable work key.",
            {"work_key": string("Stable name + author work key")},
        ),
        tool(
            "bookshelf_chapter_list",
            "List a compact page of physical chapters.",
            {
                "start": integer("Zero-based start index"),
                "limit": integer("Maximum rows"),
                "include_detail": boolean("Include compact metadata"),
            },
        ),
        tool("bookshelf_cache_status_get", "Get local text availability.", {}),
        tool(
            "bookshelf_text_window_get",
            "Read a complete contiguous physical chapter window.",
            {
                "start_chapter_index": integer("Zero-based physical chapter index"),
                "chapter_count": integer("Number of chapters"),
                "char_limit": integer("Use 0 for complete text"),
            },
            ("start_chapter_index", "chapter_count"),
        ),
        tool(
            "bookshelf_chapter_content_get",
            "Read one physical chapter for targeted retry.",
            {
                "chapter_index": integer("Zero-based physical chapter index"),
                "char_limit": integer("Use 0 for complete text"),
            },
            ("chapter_index",),
        ),
        tool(
            "bookshelf_chapter_snippets_get",
            "Read head/tail snippets for selected local chapters without treating them as complete text coverage.",
            {
                "chapter_indexes": {
                    "type": "array",
                    "items": {"type": "integer"},
                    "description": "Zero-based physical chapter indexes",
                },
                "ranges": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {
                            "start": integer("Inclusive zero-based chapter index"),
                            "end": integer("Exclusive zero-based chapter index"),
                        },
                    },
                    "description": "Zero-based half-open chapter ranges",
                },
                "start": integer("Optional inclusive zero-based chapter index"),
                "end": integer("Optional exclusive zero-based chapter index"),
                "head_chars": integer("Default 200, max 2000"),
                "tail_chars": integer("Default 200, max 2000"),
                "include_title": boolean("Include chapter title in aggregate text"),
                "max_total_chars": integer("Default 20000, max 120000"),
            },
        ),
        tool("agent_memory_status_get", "Check whether evaluator-local Agent memory is enabled.", {}),
        tool(
            "agent_memory_search",
            "Search evaluator-local Agent memories by scope, domain and type.",
            {
                "scope_type": string("Optional scope type"),
                "scope_key": string("Optional scope key"),
                "subject": string("Optional subject filter"),
                "domain": string("Optional business domain"),
                "memory_type": string("Optional memory type"),
                "keyword": string("Optional keyword"),
                "status": string("Default active; empty includes all"),
                "offset": integer("Default 0"),
                "limit": integer("Default 10, max 50"),
            },
        ),
        tool(
            "agent_memory_upsert",
            "Create or update one evaluator-local generic Agent memory.",
            memory_properties,
            ("scope_type", "scope_key", "domain", "title", "content"),
        ),
        tool(
            "agent_memory_batch_upsert",
            "Atomically create or update up to 25 evaluator-local generic Agent memories.",
            {
                "items": {
                    "type": "array",
                    "minItems": 1,
                    "maxItems": 25,
                    "items": {
                        "type": "object",
                        "properties": memory_properties,
                        "required": ["scope_type", "scope_key", "domain", "title", "content"],
                        "additionalProperties": True,
                    },
                },
                "source_receipt_ids": {"type": "array", "items": {"type": "string"}},
            },
            ("items",),
        ),
    ]
    if include_use_skill:
        tools.insert(
            0,
            tool(
                "use_skill",
                "Load an enabled Skill entry or one resource linked from previously loaded Skill instructions.",
                {
                    "name": string("Enabled Skill name"),
                    "path": string(
                        "Optional relative resource path copied from a Markdown link in loaded Skill content; omit for SKILL.md"
                    ),
                },
                ("name",),
            ),
        )
    return tools


class LocalMcpSimulator:
    """Small deterministic adapter; it is not the Android MCP implementation."""

    def __init__(
        self,
        *,
        book_path: Path,
        name: str,
        author: str,
        synopsis: str,
        book_status: str,
        chapters: Sequence[Chapter],
        skill_revision: str,
        state_dir: Path,
        conversation_id: str,
        skill_package: SkillPackage | None = None,
        additional_skill_packages: Sequence[SkillPackage] = (),
        reader_profile: dict[str, Any] | None = None,
    ) -> None:
        self.book_path = book_path
        self.name = name
        self.author = author
        self.synopsis = synopsis
        self.book_status = book_status
        self.chapters = list(chapters)
        self.work_key = f"{name}\n{author}" if author else name
        self.skill_revision = skill_revision
        self.state_dir = state_dir
        self.conversation_id = conversation_id
        self.skill_package = skill_package
        packages = [package for package in [skill_package, *additional_skill_packages] if package]
        self.skill_packages: dict[str, SkillPackage] = {}
        for package in packages:
            for alias in {
                package.name,
                str(package.metadata.get("id") or "").strip(),
            }:
                if alias:
                    self.skill_packages[alias] = package
        self.loaded_skill_resources: list[str] = []
        self.allowed_skill_resources: dict[str, set[str]] = {
            str(package.metadata.get("id") or package.name): set()
            for package in packages
        }
        self.receipts_path = state_dir / "tool_receipts.json"
        self.memories_path = state_dir / "memories.json"
        self.receipts: dict[str, dict[str, Any]] = read_json(self.receipts_path, {}) or {}
        loaded_memories = read_json(self.memories_path, []) or []
        self.memories: list[dict[str, Any]] = (
            [dict(item) for item in loaded_memories if isinstance(item, dict)]
            if isinstance(loaded_memories, list)
            else []
        )
        if reader_profile is not None:
            self._seed_reader_profile(reader_profile)
        self.tool_dir = state_dir / "tool_results"
        self.tool_dir.mkdir(parents=True, exist_ok=True)
        self.sequence = len(list(self.tool_dir.glob("*.json")))

    def execute(self, *, tool_name: str, arguments: dict[str, Any], tool_call_id: str) -> dict[str, Any]:
        self.sequence += 1
        receipt_id = hashlib.sha256(
            f"{self.conversation_id}\n{tool_call_id}\n{tool_name}".encode("utf-8")
        ).hexdigest()[:32]
        try:
            if tool_name not in SUPPORTED_TOOLS:
                result = self._failure(f"Unsupported simulated MCP tool: {tool_name}")
            else:
                result = getattr(self, f"_tool_{tool_name}")(arguments, receipt_id)
        except Exception as error:  # noqa: BLE001 - a tool error is returned to the model
            result = self._failure(str(error))

        result.setdefault("receipt_id", receipt_id)
        normalized = result.setdefault("structuredContent", {}).setdefault("normalized_data", {})
        normalized.setdefault("receipt_id", receipt_id)
        normalized.setdefault("evaluation_mode", EVALUATION_MODE)
        success = bool(result.get("ok"))
        complete = success and bool(result.get("complete", True))
        evidence = self._receipt_evidence(tool_name, normalized)
        receipt = {
            "receipt_id": receipt_id,
            "conversation_id": self.conversation_id,
            "tool_call_id": tool_call_id,
            "tool_name": tool_name,
            "arguments_hash": stable_json_hash(arguments),
            "result_hash": stable_json_hash(result),
            "success": success,
            "complete": complete,
            "acknowledged_by": self.receipts.get(receipt_id, {}).get("acknowledged_by"),
            "evidence": evidence,
            "created_at": int(time.time()),
        }
        self.receipts[receipt_id] = receipt
        write_json(self.receipts_path, self.receipts)

        record = {
            "sequence": self.sequence,
            "tool_call_id": tool_call_id,
            "tool_name": tool_name,
            "arguments": arguments,
            "receipt": receipt,
            "result": result,
        }
        record_path = self.tool_dir / f"{self.sequence:04d}_{tool_name}.json"
        write_json(record_path, record)
        record["artifact_file"] = str(record_path.relative_to(self.state_dir))
        return record

    def _book_data(self) -> dict[str, Any]:
        return {
            "name": self.name,
            "author": self.author,
            "work_key": self.work_key,
            "book_status": self.book_status,
            "intro": self.synopsis,
            "total_count": len(self.chapters),
            "latest_chapter_title": self.chapters[-1].title,
            "source_path": str(self.book_path),
        }

    def _tool_use_skill(self, arguments: dict[str, Any], _: str) -> dict[str, Any]:
        requested_name = str(arguments.get("name") or "").strip()
        package = self.skill_packages.get(requested_name)
        if package is None:
            return self._failure(
                f"Skill '{requested_name}' is not enabled; available={sorted(self.skill_packages)}",
                code="SKILL_NOT_AVAILABLE",
            )
        package_id = str(package.metadata.get("id") or package.name)
        raw_path = str(arguments.get("path") or "").strip()
        relative_path = "SKILL.md" if not raw_path else normalize_skill_path(raw_path)
        allowed_resources = self.allowed_skill_resources.setdefault(package_id, set())
        if relative_path != "SKILL.md" and relative_path not in allowed_resources:
            return self._failure(
                f"Skill resource '{relative_path}' was not linked from previously loaded instructions",
                code="SKILL_PATH_NOT_DISCOVERED",
            )
        content = package.read(None if relative_path == "SKILL.md" else relative_path)
        if relative_path not in self.loaded_skill_resources:
            self.loaded_skill_resources.append(relative_path)
        allowed_resources.update(markdown_resource_links(content))
        return self._success(
            {
                "skill": package.name,
                "path": relative_path,
                "package_hash": package.package_hash,
                "content": content,
                "linked_resources": sorted(markdown_resource_links(content)),
            }
        )

    def _seed_reader_profile(self, profile: dict[str, Any]) -> None:
        now = int(time.time())
        self.memories = [
            memory
            for memory in self.memories
            if not (
                str(memory.get("scope_type") or "") == "global"
                and str(memory.get("scope_key") or "") == "default"
                and str(memory.get("domain") or "") == "reader_preference"
                and str(memory.get("memory_type") or "") == "global_profile"
            )
        ]
        self.memories.append(
            {
                "id": "evaluator-reader-profile",
                "scope_type": "global",
                "scope_key": "default",
                "subject": "默认读者",
                "domain": "reader_preference",
                "memory_type": "global_profile",
                "title": "默认阅读偏好",
                "content": "评测器预置的稀疏阅读偏好 v2。",
                "data": dict(profile),
                "tags": ["reader_preference", "global_profile"],
                "confidence": 1.0,
                "source": "user_confirmed_fixture",
                "status": "active",
                "created_at": now,
                "updated_at": now,
            }
        )
        write_json(self.memories_path, self.memories)

    def active_reader_profile(self) -> dict[str, Any] | None:
        for memory in reversed(self.memories):
            if (
                str(memory.get("scope_type") or "") == "global"
                and str(memory.get("scope_key") or "") == "default"
                and str(memory.get("domain") or "") == "reader_preference"
                and str(memory.get("memory_type") or "") == "global_profile"
                and isinstance(memory.get("data"), dict)
            ):
                return dict(memory["data"])
        return None

    def _success(self, data: dict[str, Any], *, complete: bool = True) -> dict[str, Any]:
        return {
            "ok": True,
            "complete": complete,
            "evaluation_mode": EVALUATION_MODE,
            "simulation_warning": "Evaluator-local adapter; not an Android/App MCP result.",
            "structuredContent": {"ok": True, "normalized_data": data},
        }

    def _failure(self, error: str, *, code: str = "SIMULATED_TOOL_ERROR") -> dict[str, Any]:
        return {
            "ok": False,
            "complete": False,
            "evaluation_mode": EVALUATION_MODE,
            "error_code": code,
            "error": error,
            "structuredContent": {"ok": False, "normalized_data": {}},
        }

    def _tool_bookshelf_current_book_get(self, _: dict[str, Any], __: str) -> dict[str, Any]:
        return self._success(self._book_data())

    def _tool_bookshelf_book_get(self, arguments: dict[str, Any], _: str) -> dict[str, Any]:
        requested = str(arguments.get("work_key") or self.work_key)
        if requested != self.work_key:
            return self._failure("work_key not found", code="BOOK_NOT_FOUND")
        return self._success(self._book_data())

    def _tool_bookshelf_chapter_list(self, arguments: dict[str, Any], _: str) -> dict[str, Any]:
        start = max(0, int(arguments.get("start") or 0))
        limit = max(1, min(200, int(arguments.get("limit") or 50)))
        page = self.chapters[start : start + limit]
        return self._success(
            {
                "total_count": len(self.chapters),
                "start": start,
                "limit": limit,
                "has_more": start + len(page) < len(self.chapters),
                "chapters": [chapter.to_payload(include_content=False) for chapter in page],
            }
        )

    def _tool_bookshelf_cache_status_get(self, _: dict[str, Any], __: str) -> dict[str, Any]:
        missing = [chapter.index for chapter in self.chapters if not chapter.content]
        return self._success(
            {
                "total_count": len(self.chapters),
                "cached_count": len(self.chapters) - len(missing),
                "missing_indexes": missing,
                "all_available": not missing,
            },
            complete=not missing,
        )

    def _tool_bookshelf_text_window_get(self, arguments: dict[str, Any], receipt_id: str) -> dict[str, Any]:
        start = int(arguments.get("start_chapter_index") or 0)
        count = int(arguments.get("chapter_count") or 0)
        char_limit = int(arguments.get("char_limit") or 0)
        if start < 0 or count < 1 or start >= len(self.chapters):
            return self._failure("Invalid chapter window", code="INVALID_RANGE")
        selected = self.chapters[start : min(len(self.chapters), start + count)]
        rows = []
        for chapter in selected:
            included = len(chapter.content) if char_limit <= 0 else min(len(chapter.content), char_limit)
            rows.append(
                {
                    **chapter.to_payload(include_content=False),
                    "content": chapter.content[:included],
                    "has_content": bool(chapter.content),
                    "content_chars": len(chapter.content),
                    "included_chars": included,
                    "truncated_by_mcp": included < len(chapter.content),
                }
            )
        complete = len(selected) == count and all(
            row["has_content"] and not row["truncated_by_mcp"] for row in rows
        )
        return self._success(
            {
                "receipt_id": receipt_id,
                "start_chapter_index": start,
                "requested_chapter_count": count,
                "returned_chapter_count": len(rows),
                "total_count": len(self.chapters),
                "chapters": rows,
            },
            complete=complete,
        )

    def _tool_bookshelf_chapter_content_get(self, arguments: dict[str, Any], receipt_id: str) -> dict[str, Any]:
        index = int(arguments.get("chapter_index") or 0)
        char_limit = int(arguments.get("char_limit") or 0)
        if index < 0 or index >= len(self.chapters):
            return self._failure("Invalid chapter index", code="INVALID_RANGE")
        chapter = self.chapters[index]
        included = len(chapter.content) if char_limit <= 0 else min(len(chapter.content), char_limit)
        truncated = included < len(chapter.content)
        return self._success(
            {
                "receipt_id": receipt_id,
                "chapter": {
                    **chapter.to_payload(include_content=False),
                    "content": chapter.content[:included],
                    "has_content": bool(chapter.content),
                    "content_chars": len(chapter.content),
                    "included_chars": included,
                    "truncated_by_mcp": truncated,
                },
            },
            complete=bool(chapter.content) and not truncated,
        )

    def _tool_bookshelf_chapter_snippets_get(
        self,
        arguments: dict[str, Any],
        receipt_id: str,
    ) -> dict[str, Any]:
        indexes: list[int] = []
        for value in arguments.get("chapter_indexes") or []:
            if isinstance(value, int):
                indexes.append(value)
        for value in arguments.get("ranges") or []:
            if not isinstance(value, dict):
                continue
            start = int(value.get("start") or 0)
            end = int(value.get("end") or start)
            indexes.extend(range(start, end))
        if not indexes and ("start" in arguments or "end" in arguments):
            start = max(0, int(arguments.get("start") or 0))
            end = min(len(self.chapters), int(arguments.get("end") or start))
            indexes.extend(range(start, end))
        indexes = list(dict.fromkeys(indexes))
        if not indexes:
            return self._failure(
                "chapter_indexes, ranges, or start/end is required",
                code="INVALID_RANGE",
            )
        requested_count = len(indexes)
        indexes = indexes[:100]
        if any(index < 0 or index >= len(self.chapters) for index in indexes):
            return self._failure("Invalid chapter index", code="INVALID_RANGE")

        head_chars = max(0, min(2000, int(arguments.get("head_chars") or 200)))
        tail_chars = max(0, min(2000, int(arguments.get("tail_chars") or 200)))
        include_title = bool(arguments.get("include_title", True))
        max_total_chars = max(0, min(120000, int(arguments.get("max_total_chars") or 20000)))
        remaining = max_total_chars
        items: list[dict[str, Any]] = []
        text_parts: list[str] = []
        for index in indexes:
            chapter = self.chapters[index]
            content = chapter.content
            aggregate_parts: list[str] = []
            if include_title and remaining > 0:
                title = chapter.title[:remaining]
                aggregate_parts.append(title)
                remaining -= len(title)
            head = ""
            tail = ""
            if content and remaining > 0:
                head = content[: min(head_chars, remaining)]
                remaining -= len(head)
                if tail_chars > 0 and len(content) > len(head) and remaining > 0:
                    tail = content[-min(tail_chars, remaining) :]
                    remaining -= len(tail)
            aggregate_parts.extend(part for part in (head, tail) if part)
            if aggregate_parts:
                text_parts.append("\n".join(aggregate_parts))
            included_chars = len(head) + len(tail)
            items.append(
                {
                    **chapter.to_payload(include_content=False),
                    "has_content": bool(content),
                    "head": head or None,
                    "tail": tail or None,
                    "content_chars": len(content),
                    "included_chars": included_chars,
                    "middle_omitted": len(content) > included_chars,
                    "omitted_by_total_limit": bool(content) and included_chars == 0 and remaining <= 0,
                    "sanitized_for_ai": True,
                }
            )
        truncated = requested_count > len(indexes) or remaining <= 0
        aggregate_text = "\n\n".join(text_parts)
        return self._success(
            {
                "receipt_id": receipt_id,
                "requested_chapter_count": requested_count,
                "returned_chapter_count": len(items),
                "head_chars": head_chars,
                "tail_chars": tail_chars,
                "max_total_chars": max_total_chars,
                "chapters": items,
                "missing_chapter_indexes": [
                    item["index"] for item in items if not item["has_content"]
                ],
                "text": aggregate_text,
                "total_included_chars": len(aggregate_text),
                "sanitized_for_ai": True,
                "truncated_by_mcp": truncated,
            },
            complete=not truncated,
        )

    def _tool_agent_memory_status_get(self, _: dict[str, Any], __: str) -> dict[str, Any]:
        return self._success({"enabled": True})

    def _tool_agent_memory_search(self, arguments: dict[str, Any], _: str) -> dict[str, Any]:
        self._validate_memory_access(arguments)
        offset = max(0, int(arguments.get("offset") or 0))
        limit = max(1, min(50, int(arguments.get("limit") or 10)))
        status = "active" if arguments.get("status") is None else str(arguments.get("status") or "")
        exact_filters = {
            "scope_type": str(arguments.get("scope_type") or "").strip(),
            "scope_key": str(arguments.get("scope_key") or "").strip(),
            "domain": str(arguments.get("domain") or "").strip(),
            "memory_type": str(arguments.get("memory_type") or "").strip(),
        }
        subject = str(arguments.get("subject") or "").strip().casefold()
        keyword = str(arguments.get("keyword") or "").strip().casefold()

        def matches(memory: dict[str, Any]) -> bool:
            if status and str(memory.get("status") or "active") != status:
                return False
            for key, expected in exact_filters.items():
                if expected and str(memory.get(key) or "").strip() != expected:
                    return False
            if subject and subject not in str(memory.get("subject") or "").casefold():
                return False
            if keyword:
                haystack = "\n".join(
                    str(memory.get(key) or "")
                    for key in ("title", "content", "subject", "tags")
                ).casefold()
                if keyword not in haystack:
                    return False
            return True

        matched = sorted(
            (dict(memory) for memory in self.memories if matches(memory)),
            key=lambda item: int(item.get("updated_at") or 0),
            reverse=True,
        )
        page = matched[offset : offset + limit]
        return self._success(
            {
                "enabled": True,
                "memories": page,
                "offset": offset,
                "limit": limit,
                "total": len(matched),
                "has_more": offset + len(page) < len(matched),
            }
        )

    def _tool_agent_memory_upsert(self, arguments: dict[str, Any], _: str) -> dict[str, Any]:
        source_receipt_ids = self._validated_source_receipt_ids(arguments)
        memory, created = self._upsert_memory(arguments)
        acknowledged = self._acknowledge_source_receipts(source_receipt_ids, [memory["id"]])
        write_json(self.memories_path, self.memories)
        write_json(self.receipts_path, self.receipts)
        return self._success(
            {
                "created": created,
                "memory": memory,
                "acknowledged_receipt_ids": acknowledged,
            }
        )

    def _tool_agent_memory_batch_upsert(self, arguments: dict[str, Any], _: str) -> dict[str, Any]:
        items = arguments.get("items")
        if not isinstance(items, list) or not items or len(items) > 25:
            return self._failure("items must contain 1 to 25 memory objects", code="INVALID_ARGUMENT")
        if any(not isinstance(item, dict) for item in items):
            return self._failure("all memory items must be objects", code="INVALID_ARGUMENT")
        source_receipt_ids = self._validated_source_receipt_ids(arguments)
        snapshot = [dict(memory) for memory in self.memories]
        results: list[dict[str, Any]] = []
        try:
            for item in items:
                memory, created = self._upsert_memory(item)
                results.append(
                    {
                        "id": memory["id"],
                        "created": created,
                        "memory_type": memory["memory_type"],
                        "title": memory["title"],
                    }
                )
        except Exception:
            self.memories = snapshot
            raise
        acknowledged = self._acknowledge_source_receipts(
            source_receipt_ids,
            [str(item["id"]) for item in results],
        )
        write_json(self.memories_path, self.memories)
        write_json(self.receipts_path, self.receipts)
        return self._success(
            {
                "requested": len(items),
                "created": sum(1 for item in results if item["created"]),
                "updated": sum(1 for item in results if not item["created"]),
                "memories": results,
                "acknowledged_receipt_ids": acknowledged,
            }
        )

    def _validated_source_receipt_ids(self, arguments: dict[str, Any]) -> list[str]:
        value = arguments.get("source_receipt_ids")
        if value is None:
            return []
        if not isinstance(value, list) or any(not isinstance(item, str) or not item.strip() for item in value):
            raise ValueError("source_receipt_ids must be a string array")
        receipt_ids = [item.strip() for item in value]
        if len(receipt_ids) > 25:
            raise ValueError("source_receipt_ids must contain at most 25 entries")
        if len(set(receipt_ids)) != len(receipt_ids):
            raise ValueError("source_receipt_ids must not contain duplicates")
        invalid = [
            receipt_id
            for receipt_id in receipt_ids
            if receipt_id not in self.receipts
            or not self.receipts[receipt_id].get("success")
            or not self.receipts[receipt_id].get("complete")
        ]
        if invalid:
            raise ValueError("source receipts are missing, failed or incomplete: " + ", ".join(invalid))
        return receipt_ids

    def _acknowledge_source_receipts(
        self,
        receipt_ids: Sequence[str],
        memory_ids: Sequence[str],
    ) -> list[str]:
        if not receipt_ids:
            return []
        consumer = "agent_memory:" + ",".join(memory_ids)
        for receipt_id in receipt_ids:
            self.receipts[receipt_id]["acknowledged_by"] = consumer
        return list(receipt_ids)

    def _upsert_memory(self, arguments: dict[str, Any]) -> tuple[dict[str, Any], bool]:
        required = ("scope_type", "scope_key", "domain", "title", "content")
        missing = [key for key in required if not str(arguments.get(key) or "").strip()]
        if missing:
            raise ValueError("memory is missing required fields: " + ", ".join(missing))
        self._validate_memory_access(arguments)
        if arguments.get("data") is not None and not isinstance(arguments.get("data"), dict):
            raise ValueError("memory data must be a JSON object")
        memory_id = str(arguments.get("id") or "").strip()
        existing_index = next(
            (index for index, item in enumerate(self.memories) if str(item.get("id") or "") == memory_id),
            None,
        ) if memory_id else None
        if not memory_id:
            seed = stable_json_hash(
                {
                    "sequence": self.sequence,
                    "count": len(self.memories),
                    "scope_type": arguments.get("scope_type"),
                    "scope_key": arguments.get("scope_key"),
                    "domain": arguments.get("domain"),
                    "memory_type": arguments.get("memory_type"),
                    "title": arguments.get("title"),
                }
            )
            memory_id = f"memory-{seed[:24]}"
        now = int(time.time())
        existing = self.memories[existing_index] if existing_index is not None else None
        memory = {
            "id": memory_id,
            "scope_type": str(arguments.get("scope_type") or "").strip(),
            "scope_key": str(arguments.get("scope_key") or "").strip(),
            "subject": str(arguments.get("subject") or "").strip(),
            "domain": str(arguments.get("domain") or "").strip(),
            "memory_type": str(arguments.get("memory_type") or (existing or {}).get("memory_type") or "note").strip(),
            "title": str(arguments.get("title") or "").strip(),
            "content": str(arguments.get("content") or "").strip(),
            "data": arguments.get("data") if isinstance(arguments.get("data"), dict) else dict((existing or {}).get("data") or {}),
            "tags": [str(item) for item in arguments.get("tags") or []],
            "confidence": float(arguments.get("confidence") if arguments.get("confidence") is not None else (existing or {}).get("confidence") or 1),
            "source": str(arguments.get("source") or (existing or {}).get("source") or "ai"),
            "status": str(arguments.get("status") or (existing or {}).get("status") or "active"),
            "created_at": int((existing or {}).get("created_at") or now),
            "updated_at": now,
        }
        if existing_index is None:
            self.memories.append(memory)
            return dict(memory), True
        self.memories[existing_index] = memory
        return dict(memory), False

    def _validate_memory_access(self, arguments: dict[str, Any]) -> None:
        scope_type = str(arguments.get("scope_type") or "").strip()
        scope_key = str(arguments.get("scope_key") or "").strip()
        domain = str(arguments.get("domain") or "").strip()
        if not scope_type or not scope_key or not domain:
            raise ValueError(
                "active Agent Mode memory policy requires concrete scope_type, scope_key, and domain"
            )
        allowed = (
            scope_type == "book" and domain == "book_scan"
            or scope_type == "global"
            and scope_key == "default"
            and domain == "reader_preference"
        )
        if not allowed:
            raise ValueError(
                "active Agent Mode memory policy does not allow "
                f"scope_type={scope_type}, scope_key={scope_key}, domain={domain}"
            )

    def _receipt_evidence(self, tool_name: str, normalized: dict[str, Any]) -> dict[str, Any]:
        indexes: list[int] = []
        if tool_name == "bookshelf_text_window_get":
            indexes = [
                int(row["index"])
                for row in normalized.get("chapters") or []
                if isinstance(row, dict) and isinstance(row.get("index"), int)
            ]
        elif tool_name == "bookshelf_chapter_content_get":
            chapter = normalized.get("chapter")
            if isinstance(chapter, dict) and isinstance(chapter.get("index"), int):
                indexes = [int(chapter["index"])]
        elif tool_name == "bookshelf_chapter_snippets_get":
            indexes = [
                int(row["index"])
                for row in normalized.get("chapters") or []
                if isinstance(row, dict) and isinstance(row.get("index"), int)
            ]
        return {
            "chapter_indexes": indexes,
            "coverage_kind": "navigation" if tool_name in NAVIGATION_TOOLS else "full_text",
        }


def build_initial_messages(
    *,
    skill_body: str,
    skill_mode: str = "single",
    skill_metadata: dict[str, str] | None = None,
    skill_hash: str = "",
    name: str,
    author: str,
    work_key: str,
    status: str,
    total: int,
    synopsis: str,
    stage: str,
    target_chapters: int,
    enabled_skill_metadata: Sequence[dict[str, str]] = (),
    user_request: str | None = None,
) -> list[dict[str, Any]]:
    metadata = skill_metadata or {}
    system = (
        "你是 Legado / 阅读NG 的通用 AI 助手。\n"
        "本评测中的工具由本地 deterministic adapter 提供，工具结果会明确标记 "
        "evaluation_mode=simulated_mcp；它不是 Android App 数据库或 UI 的成功证明。\n\n"
        "扫书评测输出纪律：带工具调用的 assistant 消息不得输出过程播报；"
        "加载 book_scan_report 后，最终无工具回复的第一个字符必须是 #，"
        "并从 `## 适读结论` 开始，不得公开 Skill 加载、输入门禁、自检或报告生成过程。\n\n"
    )
    if skill_mode == "multifile":
        skill_name = metadata.get("name") or metadata.get("id") or "book_scan"
        catalog = [metadata, *enabled_skill_metadata]
        available_skills = "\n".join(
            (
                "  <skill>"
                f"<name>{item.get('id') or item.get('name')}</name>"
                f"<description>{item.get('description', '')}</description>"
                "</skill>"
            )
            for item in catalog
        )
        system += (
            "**Skills**\n"
            "当前任务启用了以下 Skill。用户请求与其匹配时，必须先调用 `use_skill` 且不传 path，"
            "加载 SKILL.md；随后只能使用已加载说明中的 Markdown 链接按需读取资源，不得猜测路径。\n"
            "<available_skills>\n"
            f"{available_skills}\n"
            "</available_skills>\n\n"
            "[AI_ACTIVE_SKILL]\n"
            f"id: {metadata.get('id') or skill_name}\n"
            f"name: {skill_name}\n"
            f"version: {metadata.get('version', '')}\n"
            f"package_hash: {skill_hash}\n"
            "instructions: call use_skill before executing the task\n"
        )
    else:
        skill_id = metadata.get("id") or "book_scan"
        version = metadata.get("version") or "unversioned"
        runtime_revision = f"{skill_id}@{version}@{skill_hash}" if skill_hash else f"{skill_id}@{version}"
        active_metadata = {
            "skill_id": skill_id,
            "title": metadata.get("name") or skill_id,
            "revision": version,
            "content_hash": skill_hash,
            "runtime_revision": runtime_revision,
        }
        system += (
            "下面是当前活动 Skill。\n\n[AI_ACTIVE_SKILL]\n"
            + json.dumps(active_metadata, ensure_ascii=False, separators=(",", ":"))
            + "\n\n"
            + skill_body
        )
    request = user_request or (
        "进入 AI 扫书专用模式，按入口上下文开始当前书快速定位。"
        if stage == "orientation"
        else f"继续完整扫描，新增覆盖 {target_chapters} 章"
    )
    user = (
        "当前书籍上下文：\n"
        f"- 书名：{name}\n- 作者：{author or '未知'}\n"
        "- work_key 是下方 XML 标签内部的原始多行文本。调用工具时只传标签内文本，"
        "不传引号或标签，不得把换行写成两个普通字符 `\\n`，也不得只传第一行：\n"
        f"<work_key>{work_key}</work_key>\n"
        f"- 状态：{status}\n- 物理章节总数：{total}\n- 简介：{synopsis}\n\n"
        f"用户请求：{request}"
    )
    return [{"role": "system", "content": system}, {"role": "user", "content": user}]


def build_request_body(
    *,
    model: str,
    messages: Sequence[dict[str, Any]],
    tools: Sequence[dict[str, Any]],
    temperature: float,
    max_tokens: int,
) -> dict[str, Any]:
    body = {
        "model": model,
        "messages": [
            {key: value for key, value in message.items() if not key.startswith("_")}
            for message in messages
        ],
        "temperature": temperature,
        "max_tokens": max_tokens,
    }
    if tools:
        body["tools"] = list(tools)
        body["tool_choice"] = "auto"
    return body


def call_api_step(
    *,
    api_url: str,
    api_key: str,
    body: dict[str, Any],
    timeout: int,
    retries: int,
    retry_sleep: float,
) -> dict[str, Any]:
    request = urllib.request.Request(
        api_url,
        data=json.dumps(body, ensure_ascii=False).encode("utf-8"),
        headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
        method="POST",
    )
    for attempt in range(retries + 1):
        try:
            with urllib.request.urlopen(request, timeout=timeout) as response:
                if body.get("stream"):
                    result = collect_streaming_chat_response(response, str(body.get("model") or ""))
                else:
                    result = json.loads(response.read().decode("utf-8"))
            message = result["choices"][0]["message"]
            if not isinstance(message, dict):
                raise ValueError("choices[0].message must be an object")
            return result
        except (
            urllib.error.HTTPError,
            urllib.error.URLError,
            http.client.IncompleteRead,
            http.client.RemoteDisconnected,
            TimeoutError,
            KeyError,
            ValueError,
            json.JSONDecodeError,
        ) as error:
            if attempt >= retries:
                if isinstance(error, urllib.error.HTTPError):
                    detail = error.read().decode("utf-8", errors="replace")[:2000]
                    raise RuntimeError(f"HTTP {error.code}: {detail}") from error
                raise RuntimeError(str(error)) from error
            time.sleep(retry_sleep * (attempt + 1))
    raise AssertionError("unreachable")


def collect_streaming_chat_response(
    lines: Any,
    fallback_model: str = "",
) -> dict[str, Any]:
    """Collect OpenAI-compatible SSE deltas, including fragmented tool calls."""
    content: list[str] = []
    reasoning: list[str] = []
    tool_calls: dict[int, dict[str, Any]] = {}
    model = fallback_model
    finish_reason = None
    usage: dict[str, Any] = {}
    saw_done = False
    for raw_line in lines:
        line = raw_line.decode("utf-8", errors="replace").strip()
        if not line.startswith("data:"):
            continue
        data = line[5:].strip()
        if data == "[DONE]":
            saw_done = True
            break
        if not data:
            continue
        event = json.loads(data)
        model = str(event.get("model") or model)
        usage = event.get("usage") or usage
        choices = event.get("choices") or []
        if not choices:
            continue
        choice = choices[0]
        finish_reason = choice.get("finish_reason") or finish_reason
        delta = choice.get("delta") or {}
        content.append(str(delta.get("content") or ""))
        reasoning.append(str(delta.get("reasoning_content") or ""))
        for tool_delta in delta.get("tool_calls") or []:
            index = int(tool_delta.get("index") or 0)
            target = tool_calls.setdefault(
                index,
                {"id": "", "type": "function", "function": {"name": "", "arguments": ""}},
            )
            target["id"] += str(tool_delta.get("id") or "")
            target["type"] = str(tool_delta.get("type") or target["type"])
            function = tool_delta.get("function") or {}
            target["function"]["name"] += str(function.get("name") or "")
            target["function"]["arguments"] += str(function.get("arguments") or "")
    if not saw_done and not finish_reason:
        raise http.client.IncompleteRead("".join(content).encode("utf-8"))
    message: dict[str, Any] = {"role": "assistant", "content": "".join(content)}
    if any(reasoning):
        message["reasoning_content"] = "".join(reasoning)
    if tool_calls:
        message["tool_calls"] = [tool_calls[index] for index in sorted(tool_calls)]
    return {
        "model": model,
        "choices": [{"finish_reason": finish_reason, "message": message}],
        "usage": usage,
    }


def parse_tool_arguments(tool_call: dict[str, Any]) -> tuple[str, dict[str, Any], str]:
    call_id = str(tool_call.get("id") or "").strip()
    function = tool_call.get("function")
    if not call_id or not isinstance(function, dict):
        raise ValueError("Tool call must contain id and function")
    name = str(function.get("name") or "").strip()
    raw_arguments = function.get("arguments") or "{}"
    if isinstance(raw_arguments, str):
        arguments = json.loads(raw_arguments)
    elif isinstance(raw_arguments, dict):
        arguments = raw_arguments
    else:
        raise ValueError(f"Tool {name} arguments must be JSON object text")
    if not isinstance(arguments, dict):
        raise ValueError(f"Tool {name} arguments must decode to an object")
    return name, arguments, call_id


def execute_pending_tool_calls(
    *,
    run: dict[str, Any],
    simulator: LocalMcpSimulator,
    run_path: Path,
) -> int:
    messages = run.get("messages") or []
    assistant_index = next(
        (
            index
            for index in range(len(messages) - 1, -1, -1)
            if messages[index].get("role") == "assistant" and messages[index].get("tool_calls")
        ),
        -1,
    )
    if assistant_index < 0:
        return 0
    tool_calls = messages[assistant_index].get("tool_calls") or []
    completed_ids = {
        str(message.get("tool_call_id") or "")
        for message in messages[assistant_index + 1 :]
        if message.get("role") == "tool"
    }
    executed = 0
    for tool_call in tool_calls:
        raw_call_id = str(tool_call.get("id") or "")
        if raw_call_id and raw_call_id in completed_ids:
            continue
        try:
            tool_name, arguments, call_id = parse_tool_arguments(tool_call)
            record = simulator.execute(
                tool_name=tool_name,
                arguments=arguments,
                tool_call_id=call_id,
            )
            run.setdefault("tool_trace", []).append(record)
            tool_result = record["result"]
        except Exception as error:  # noqa: BLE001 - malformed call is returned to model
            call_id = raw_call_id or f"invalid-{run.get('step', 0)}-{executed}"
            tool_name = str((tool_call.get("function") or {}).get("name") or "unknown")
            tool_result = {
                "ok": False,
                "complete": False,
                "evaluation_mode": EVALUATION_MODE,
                "error": f"Malformed tool call: {error}",
            }
        run["messages"].append(
            {
                "role": "tool",
                "tool_call_id": call_id,
                "name": tool_name,
                "content": json.dumps(tool_result, ensure_ascii=False, separators=(",", ":")),
            }
        )
        run["updated_at"] = int(time.time())
        write_json(run_path, run)
        executed += 1
    return executed


def usage_from_response(response: dict[str, Any]) -> dict[str, int]:
    usage = response.get("usage") or {}
    return {
        "prompt_tokens": int(usage.get("prompt_tokens") or 0),
        "completion_tokens": int(usage.get("completion_tokens") or 0),
        "total_tokens": int(usage.get("total_tokens") or 0),
    }


def add_usage(total: dict[str, int], current: dict[str, int]) -> None:
    for key in ("prompt_tokens", "completion_tokens", "total_tokens"):
        total[key] = int(total.get(key) or 0) + int(current.get(key) or 0)


def provider_protocol_prefix(content: str) -> str:
    indexes = [
        content.find(marker)
        for marker in ("<｜DSML｜", "<|DSML|")
        if content.find(marker) >= 0
    ]
    return content[: min(indexes)].rstrip() if indexes else content


def visible_text(content: str) -> str:
    content = provider_protocol_prefix(content)
    value = INTERACTION_BLOCK_RE.sub("", content)
    value = LEGACY_PRIVATE_BLOCK_RE.sub("", value)
    return value.strip()


def interaction_blocks(content: str) -> list[dict[str, Any]]:
    interactions: list[dict[str, Any]] = []
    for match in INTERACTION_BLOCK_RE.finditer(content):
        block = match.group(0)
        payload = block.split("\n", 1)[1].rsplit("\n```", 1)[0]
        value = json.loads(payload)
        if not isinstance(value, dict):
            raise ValueError("legado-interaction payload must be a JSON object")
        interactions.append(value)
    return interactions


def latest_interaction_result(messages: Sequence[dict[str, Any]]) -> dict[str, Any] | None:
    for message in reversed(messages):
        if message.get("role") != "user":
            continue
        content = message.get("content")
        if not isinstance(content, str):
            continue
        value = run_json_object(content)
        if value and value.get("type") == "interaction_result":
            return value
    return None


def interaction_result_display_lines(
    messages: Sequence[dict[str, Any]],
    result: dict[str, Any],
) -> list[str]:
    interaction_id = str(result.get("interaction_id") or "")
    source_interaction: dict[str, Any] | None = None
    for message in messages:
        if message.get("role") != "assistant":
            continue
        content = message.get("content")
        if not isinstance(content, str):
            continue
        for interaction in interaction_blocks(content):
            if str(interaction.get("id") or "") == interaction_id:
                source_interaction = interaction
    if source_interaction is None:
        return []
    item_labels = {
        str(item.get("id") or ""): str(item.get("label") or "")
        for item in source_interaction.get("items") or []
        if isinstance(item, dict)
    }
    option_labels = {
        str(option.get("value") or ""): str(option.get("label") or "")
        for option in source_interaction.get("options") or []
        if isinstance(option, dict)
    }
    lines: list[str] = []
    for selected in result.get("selected") or []:
        if not isinstance(selected, dict):
            continue
        item_id = str(selected.get("id") or "")
        value = str(selected.get("value") or "")
        item_label = item_labels.get(item_id, "")
        option_label = option_labels.get(value, "")
        if item_label and option_label:
            lines.append(f"{item_label}：{option_label}")
    return lines


def run_json_object(content: str) -> dict[str, Any] | None:
    value = run_catching_json(content)
    return value if isinstance(value, dict) else None


def run_catching_json(content: str) -> Any | None:
    try:
        return json.loads(content)
    except (TypeError, json.JSONDecodeError):
        return None


def reader_tag_ids(filename: str) -> set[str]:
    content = (READER_TAG_DIR / filename).read_text(encoding="utf-8")
    return set(re.findall(r"(?m)^- `([a-z][a-z0-9_]*(?:\.[a-z0-9_]+)+)`", content))


def reader_tag_detectability(filename: str) -> dict[str, str]:
    content = (READER_TAG_DIR / filename).read_text(encoding="utf-8")
    return {
        tag_id: detectability
        for tag_id, detectability in re.findall(
            r"(?m)^- `([a-z][a-z0-9_]*(?:\.[a-z0-9_]+)+)`｜[^｜]+｜(high|medium|low)｜",
            content,
        )
    }


def reader_tag_question_policies(filename: str) -> dict[str, str]:
    content = (READER_TAG_DIR / filename).read_text(encoding="utf-8")
    return {
        tag_id: policy
        for tag_id, policy in re.findall(
            r"(?m)^- `([a-z][a-z0-9_]*(?:\.[a-z0-9_]+)+)`｜[^｜]+｜"
            r"(?:high|medium|low)｜(selection_impact|never_ask)｜",
            content,
        )
    }


def reader_tag_display_copy(filename: str) -> dict[str, dict[str, str]]:
    content = (READER_TAG_DIR / filename).read_text(encoding="utf-8")
    return {
        tag_id: {
            "label": label.strip(),
            "description": definition.strip(),
        }
        for tag_id, label, definition in re.findall(
            r"(?m)^- `([a-z][a-z0-9_]*(?:\.[a-z0-9_]+)+)`｜([^｜]+)｜"
            r"(?:high|medium|low)｜(?:selection_impact|never_ask)｜([^｜]+)｜",
            content,
        )
    }


def profile_known_tag_ids(reader_profile: dict[str, Any] | None) -> set[str]:
    if not isinstance(reader_profile, dict):
        return set()
    known: set[str] = set()
    tag_stances = reader_profile.get("tag_stances")
    if isinstance(tag_stances, dict):
        known.update(str(tag_id) for tag_id in tag_stances)
    dimensions = reader_profile.get("dimension_answers")
    if isinstance(dimensions, dict):
        for answer in dimensions.values():
            if not isinstance(answer, dict) or not answer.get("confirmed_by_user"):
                continue
            values = answer.get("values")
            if isinstance(values, list):
                known.update(str(value) for value in values)
    return known


def confirmed_fact_tag_ids(memories: Sequence[dict[str, Any]]) -> set[str]:
    result: set[str] = set()
    for memory in memories:
        if str(memory.get("domain") or "") != "book_scan":
            continue
        if str(memory.get("memory_type") or "") != "window_bundle":
            continue
        data = memory.get("data")
        tag_hits = data.get("tag_hits") if isinstance(data, dict) else None
        if not isinstance(tag_hits, list):
            continue
        for tag_hit in tag_hits:
            if not isinstance(tag_hit, dict):
                continue
            if tag_hit.get("detection_status") == "confirmed":
                tag_id = str(tag_hit.get("tag_id") or "")
                if tag_id:
                    result.add(tag_id)
        relationship_profile = data.get("relationship_profile")
        if isinstance(relationship_profile, dict):
            route_tag_id = {
                "none": "route.no_romance",
                "single": "route.single_partner",
                "multi": "route.multi_partner",
            }.get(str(relationship_profile.get("structure") or ""))
            if route_tag_id:
                result.add(route_tag_id)
    return result


def confirmed_warning_risk_ids(memories: Sequence[dict[str, Any]]) -> set[str]:
    result: set[str] = set()
    for memory in memories:
        if str(memory.get("domain") or "") != "book_scan":
            continue
        if str(memory.get("memory_type") or "") != "window_bundle":
            continue
        data = memory.get("data")
        risks = data.get("risks") if isinstance(data, dict) else None
        if not isinstance(risks, list):
            continue
        for risk in risks:
            if not isinstance(risk, dict):
                continue
            if risk.get("evidence_status") not in {"confirmed", "ongoing"}:
                continue
            risk_kind = str(risk.get("risk_kind") or "")
            if risk_kind:
                result.add(risk_kind)
    return result


def window_bundle_evidence_range_violations(data: dict[str, Any]) -> list[str]:
    chapter_range = data.get("chapter_range")
    if (
        not isinstance(chapter_range, list)
        or len(chapter_range) != 2
        or not all(isinstance(value, int) for value in chapter_range)
        or chapter_range[0] < 0
        or chapter_range[1] < chapter_range[0]
    ):
        return ["chapter_range must be a valid [start,end) integer range"]
    start, end = chapter_range
    violations: list[str] = []

    def walk(value: Any, path: str) -> None:
        if isinstance(value, dict):
            for key, child in value.items():
                child_path = f"{path}.{key}" if path else key
                if key == "evidence_refs":
                    if not isinstance(child, list):
                        violations.append(f"{child_path} must be a list")
                        continue
                    for index, evidence_ref in enumerate(child):
                        evidence_path = f"{child_path}[{index}]"
                        if not isinstance(evidence_ref, dict):
                            violations.append(f"{evidence_path} must be an object")
                            continue
                        chapter_index = evidence_ref.get("chapter_index")
                        if not isinstance(chapter_index, int):
                            violations.append(f"{evidence_path}.chapter_index must be an integer")
                        elif chapter_index < start or chapter_index >= end:
                            violations.append(
                                f"{evidence_path}.chapter_index={chapter_index} is outside "
                                f"chapter_range=[{start},{end})"
                            )
                    continue
                walk(child, child_path)
        elif isinstance(value, list):
            for index, child in enumerate(value):
                walk(child, f"{path}[{index}]")

    walk(data, "")
    return violations


def eligible_unknown_confirmed_tag_ids(
    confirmed_tag_ids: Iterable[str],
    reader_profile: dict[str, Any] | None,
) -> set[str]:
    detectability = reader_tag_detectability("selectable-tags.md")
    question_policies = reader_tag_question_policies("selectable-tags.md")
    selectable = {
        tag_id
        for tag_id, policy in question_policies.items()
        if policy == "selection_impact" and detectability.get(tag_id) in {"high", "medium"}
    }
    return set(confirmed_tag_ids) & selectable - profile_known_tag_ids(reader_profile)


def collect_visible_segments(messages: Sequence[dict[str, Any]]) -> list[str]:
    segments: list[str] = []
    for message in messages:
        if message.get("role") != "assistant" or message.get("_visible") is False:
            continue
        if message.get("tool_calls"):
            continue
        content = message.get("content")
        if not isinstance(content, str):
            continue
        value = visible_text(content)
        if value:
            segments.append(value)
    return segments


def output_contract_violations(
    content: str,
    skill_package: SkillPackage | None,
) -> list[str]:
    contract = skill_package.read_declaration("output_contract") if skill_package else None
    if not contract:
        return []
    visible = visible_text(content)
    violations: list[str] = []
    minimum = int(contract.get("min_visible_chars") or 0)
    if len(visible) < minimum:
        violations.append(f"visible content has {len(visible)} chars; minimum is {minimum}")
    forbidden = contract.get("forbidden_regex") or []
    for item in forbidden:
        if not isinstance(item, dict) or not item.get("pattern"):
            continue
        if re.search(str(item["pattern"]), visible):
            violations.append(str(item.get("message") or f"forbidden pattern: {item['pattern']}"))
    return violations


def personalized_report_contract_violations(
    content: str,
    reader_profile: dict[str, Any] | None = None,
    *,
    confirmed_tag_ids: Iterable[str] | None = None,
    confirmed_risk_ids: Iterable[str] | None = None,
) -> list[str]:
    visible = visible_text(content)
    violations: list[str] = []
    required_headings = (
        "## 适读结论",
        "## 基础信息",
        "## 作品速览",
        "## 代入与关系",
        "## 阅读感受",
    )
    positions = [visible.find(heading) for heading in required_headings]
    if not visible.startswith("## 适读结论"):
        violations.append("quick report must start with ## 适读结论")
    if any(position < 0 for position in positions):
        missing = [heading for heading, position in zip(required_headings, positions) if position < 0]
        violations.append("quick report is missing core headings: " + ", ".join(missing))
    elif positions != sorted(positions):
        violations.append("quick report core headings are out of order")
    boundary = "以上只依据开头与结尾样本；中段人物成长、剧情质量及隐藏风险仍需继续扫描确认。"
    if boundary not in visible:
        violations.append("quick report is missing the concise evidence boundary")
    for forbidden_heading in (
        "## 个性化可读性分析",
        "## 扫描边界",
        "## 主观锐评",
        "## 关键人物",
        "## 核心关系",
        "## 避坑警告",
        "## 最大败笔",
    ):
        if forbidden_heading in visible:
            violations.append(f"quick report contains obsolete or forbidden heading: {forbidden_heading}")
    for marker in ("匹配证据", "冲突证据", "决定性未知", "置信度", "适合的阅读期待"):
        if marker in visible:
            violations.append(f"quick report exposes obsolete audit field: {marker}")
    if re.search(r"(?m)^\s*\|.+\|\s*$", visible):
        violations.append("quick report must not contain a Markdown table")
    if not re.search(r"(?m)^> \[!IMPORTANT\] 🎯 当前判断\s*$", visible):
        violations.append("quick report must render the fit conclusion as an IMPORTANT callout")
    if not re.search(r"(?m)^> \[!WARNING\] 🔎 扫描边界.*$", visible):
        violations.append("quick report must render the evidence boundary as a WARNING callout")
    if "## 已确认雷点" in visible and not re.search(
        r"(?m)^> \[!(?:WARNING|CAUTION)\] .+$", visible
    ):
        violations.append("quick report risks must use WARNING or CAUTION callouts")

    for marker in ("中段推测", "推测中段", "大概率中段", "中后段", "全程"):
        if marker in visible:
            violations.append(f"quick report must keep unread middle chapters unknown: {marker}")
    for process_marker in (
        "事实包已确认",
        "事实包已更新",
        "档案已更新至最新版本",
        "agent_memory_",
        "schema_version",
        "reader_tag_catalog_version",
    ):
        if process_marker in visible:
            violations.append(
                f"quick report leaks internal workflow or protocol text: {process_marker}"
            )
    risk_section_match = re.search(
        r"(?ms)^## (?:已确认雷点|避坑警告)\s*$\n(.*?)(?=^## |\Z)",
        visible,
    )
    if risk_section_match and re.search(
        r"已逆转|结果未知|已出现威胁|威胁未落地|evidence_status\s*=\s*(?:reversed|threatened|outcome_unknown)",
        risk_section_match.group(1),
    ):
        violations.append(
            "quick report risk warnings must contain only confirmed or ongoing risks"
        )
    if risk_section_match and confirmed_risk_ids is not None and not set(confirmed_risk_ids):
        violations.append(
            "quick report must not create a warning section when no confirmed or ongoing risk exists"
        )
    for forbidden_quality_judgment in (
        "人设崩塌",
        "人设崩坏",
        "剧情崩坏",
        "角色降智",
        "主角降智",
        "长期窝囊受气",
        "最大败笔",
    ):
        if forbidden_quality_judgment in visible:
            violations.append(
                "quick report must not assert uncovered negative quality judgment: "
                + forbidden_quality_judgment
            )

    try:
        interactions = interaction_blocks(content)
    except (ValueError, json.JSONDecodeError) as error:
        violations.append(f"quick report has invalid interaction JSON: {error}")
        return violations
    if len(interactions) != 1:
        violations.append(f"quick report must contain exactly one interaction: actual={len(interactions)}")
        return violations
    interaction = interactions[0]
    interaction_id = str(interaction.get("id") or "")
    if interaction_id != "book_scan_reaction":
        violations.append(
            "quick report must end with the book_scan_reaction policy reference: "
            f"actual={interaction_id}"
        )
    if set(interaction) != {"id"}:
        violations.append(
            "book_scan_reaction must be an id-only policy reference; host owns all UI copy"
        )
    return violations


def preference_rerender_contract_violations(
    content: str,
    reader_profile: dict[str, Any] | None = None,
    *,
    confirmed_tag_ids: Iterable[str] | None = None,
    interaction_status: str = "saved",
    expected_saved_lines: Sequence[str] | None = None,
) -> list[str]:
    visible = visible_text(content)
    violations: list[str] = []
    if interaction_status == "skipped":
        if "已保存偏好" in visible:
            violations.append("skipped preference result must not claim that preferences were saved")
        if "本次未保存阅读偏好" not in visible:
            violations.append("skipped preference result must state that no preference was saved")
        if "以上只依据开头与结尾样本；中段人物成长、剧情质量及隐藏风险仍需继续扫描确认。" not in visible:
            violations.append("skipped preference result is missing the evidence boundary")
        try:
            interactions = interaction_blocks(content)
        except (ValueError, json.JSONDecodeError) as error:
            return [f"skipped preference result has invalid interaction JSON: {error}"]
        if len(interactions) != 1 or interactions[0].get("id") != "book_scan_next":
            violations.append("skipped preference result must end with exactly one book_scan_next interaction")
        return violations
    if not visible.startswith("## 推荐已更新"):
        violations.append("preference rerender must start with ## 推荐已更新")
    for marker in (
        "> [!NOTE] ✅ 已保存偏好",
        "## 新结论",
        "> [!IMPORTANT] 🎯 更新后的判断",
        "## 变化原因",
        "> [!WARNING] 🔎 扫描边界",
        "以上只依据开头与结尾样本；中段人物成长、剧情质量及隐藏风险仍需继续扫描确认。",
    ):
        if marker not in visible:
            violations.append(f"preference rerender is missing: {marker}")
    if "## 适读结论" in visible or "## 基础信息" in visible:
        violations.append("preference rerender must be a compact delta, not a duplicate full report")
    for expected_line in expected_saved_lines or []:
        if visible.count(expected_line) != 1:
            violations.append(
                f"preference rerender must echo saved selection exactly once: {expected_line}"
            )
    try:
        interactions = interaction_blocks(content)
    except (ValueError, json.JSONDecodeError) as error:
        violations.append(f"preference rerender has invalid interaction JSON: {error}")
        return violations
    if len(interactions) != 1:
        violations.append(
            f"preference rerender must contain exactly one interaction: actual={len(interactions)}"
        )
        return violations
    interaction = interactions[0]
    interaction_id = str(interaction.get("id") or "")
    if interaction_id == "reader_preference_adaptive_tags":
        violations.extend(
            multi_tag_stance_contract_violations(
                interaction,
                reader_profile=reader_profile,
                confirmed_tag_ids=(
                    None if confirmed_tag_ids is None else set(confirmed_tag_ids)
                ),
            )
        )
    elif interaction_id != "book_scan_next":
        violations.append(
            f"preference rerender contains unknown interaction id: {interaction_id}"
        )
    return violations


def multi_tag_stance_contract_violations(
    interaction: dict[str, Any],
    *,
    reader_profile: dict[str, Any] | None,
    confirmed_tag_ids: set[str] | None,
) -> list[str]:
    violations: list[str] = []
    if interaction.get("version") != 2:
        violations.append("multi_tag_stance version must be 2")
    if interaction.get("type") != "multi_tag_stance":
        violations.append("adaptive preference interaction type must be multi_tag_stance")
    option_values = [
        str(option.get("value") or "")
        for option in interaction.get("options") or []
        if isinstance(option, dict)
    ]
    if len(option_values) != 3 or set(option_values) != PROFILE_STANCES:
        violations.append("multi_tag_stance top-level options must contain like, neutral and avoid")
    option_labels = {
        str(option.get("value") or ""): str(option.get("label") or "")
        for option in interaction.get("options") or []
        if isinstance(option, dict)
    }
    if option_labels != PREFERENCE_OPTION_LABELS:
        violations.append(
            "multi_tag_stance option labels must map to 更偏好这类作品, 可以接受, 会因此劝退"
        )
    items = interaction.get("items")
    if not isinstance(items, list) or not 1 <= len(items) <= 5:
        violations.append("multi_tag_stance items must contain 1..5 entries")
        items = []
    selectable_detectability = reader_tag_detectability("selectable-tags.md")
    question_policies = reader_tag_question_policies("selectable-tags.md")
    selectable_copy = reader_tag_display_copy("selectable-tags.md")
    selectable_ids = {
        tag_id
        for tag_id, policy in question_policies.items()
        if policy == "selection_impact"
        and selectable_detectability.get(tag_id) in {"high", "medium"}
    }
    known_ids = profile_known_tag_ids(reader_profile)
    item_ids: list[str] = []
    for index, item in enumerate(items):
        if not isinstance(item, dict):
            violations.append(f"multi_tag_stance items[{index}] must be an object")
            continue
        item_id = str(item.get("id") or "")
        item_ids.append(item_id)
        if not STABLE_TAG_ID_RE.fullmatch(item_id):
            violations.append(f"multi_tag_stance items[{index}].id must be a stable tag id")
        if item_id not in selectable_ids:
            violations.append(f"multi_tag_stance candidate is not selectable: {item_id}")
        if confirmed_tag_ids is not None and item_id not in confirmed_tag_ids:
            violations.append(f"multi_tag_stance candidate lacks a confirmed tag_hit: {item_id}")
        if item_id in known_ids:
            violations.append(f"multi_tag_stance repeats a known reader stance: {item_id}")
        expected_copy = selectable_copy.get(item_id)
        if expected_copy is not None and (
            item.get("label") != expected_copy["label"]
            or item.get("description") != expected_copy["description"]
        ):
            violations.append(
                f"multi_tag_stance candidate copy must match the trusted catalog: {item_id}"
            )
        if "options" in item:
            violations.append(f"multi_tag_stance items[{index}] must not repeat options")
        if not isinstance(item.get("description"), str) or not item.get("description", "").strip():
            violations.append(f"multi_tag_stance items[{index}].description must be non-empty")
    if len(item_ids) != len(set(item_ids)):
        violations.append("multi_tag_stance item ids must be unique")

    patch = interaction.get("memory_patch")
    if not isinstance(patch, dict):
        violations.append("multi_tag_stance memory_patch must be an object")
    else:
        expected = {
            "operation": "json_map_merge",
            "scope_type": "global",
            "scope_key": "default",
            "domain": "reader_preference",
            "memory_type": "global_profile",
            "map_field": "tag_stances",
            "value_field": "stance",
            "on_base_mismatch": "replace",
        }
        for field, value in expected.items():
            if patch.get(field) != value:
                violations.append(f"multi_tag_stance memory_patch.{field} must be {value}")
        if not isinstance(patch.get("title"), str) or not patch.get("title", "").strip():
            violations.append("multi_tag_stance memory_patch.title must be non-empty")
        if not isinstance(patch.get("content"), str) or not patch.get("content", "").strip():
            violations.append("multi_tag_stance memory_patch.content must be non-empty")
        base_data = patch.get("base_data")
        expected_base = {
            "schema_version": 2,
            "profile_id": "default",
            "dimension_answers": {},
            "tag_stances": {},
            "custom_rules": [],
        }
        if base_data != expected_base:
            violations.append("multi_tag_stance memory_patch.base_data must be sparse global_profile v2")
        if patch.get("identity_fields") != ["schema_version", "profile_id"]:
            violations.append(
                "multi_tag_stance memory_patch.identity_fields must be schema_version, profile_id"
            )
    if not isinstance(interaction.get("submit"), dict):
        violations.append("multi_tag_stance submit must be an object")
    if not isinstance(interaction.get("skip"), dict):
        violations.append("multi_tag_stance skip must be an object")
    return violations


def book_fact_fingerprint(memories: Sequence[dict[str, Any]]) -> str:
    windows = [
        {
            "scope_type": memory.get("scope_type"),
            "scope_key": memory.get("scope_key"),
            "domain": memory.get("domain"),
            "memory_type": memory.get("memory_type"),
            "data": memory.get("data"),
        }
        for memory in memories
        if str(memory.get("scope_type") or "") == "book"
        and str(memory.get("domain") or "") == "book_scan"
        and str(memory.get("memory_type") or "") == "window_bundle"
    ]
    windows.sort(key=lambda item: stable_json_hash(item))
    return stable_json_hash(windows) if windows else ""


def evidence_coverage(receipts: dict[str, dict[str, Any]]) -> dict[str, Any]:
    all_indexes: set[int] = set()
    acknowledged_indexes: set[int] = set()
    navigation_indexes: set[int] = set()
    acknowledged_navigation_indexes: set[int] = set()
    for receipt in receipts.values():
        tool_name = receipt.get("tool_name")
        if tool_name not in WATCH_TOOLS | NAVIGATION_TOOLS:
            continue
        if not receipt.get("success") or not receipt.get("complete"):
            continue
        indexes = {
            int(index)
            for index in (receipt.get("evidence") or {}).get("chapter_indexes") or []
            if isinstance(index, int)
        }
        if tool_name in NAVIGATION_TOOLS:
            navigation_indexes.update(indexes)
            if receipt.get("acknowledged_by"):
                acknowledged_navigation_indexes.update(indexes)
        else:
            all_indexes.update(indexes)
            if receipt.get("acknowledged_by"):
                acknowledged_indexes.update(indexes)
    return {
        "complete_read_indexes": sorted(all_indexes),
        "complete_read_ranges": ranges_from_indexes(all_indexes),
        "memory_acknowledged_indexes": sorted(acknowledged_indexes),
        "memory_acknowledged_ranges": ranges_from_indexes(acknowledged_indexes),
        "navigation_indexes": sorted(navigation_indexes),
        "navigation_ranges": ranges_from_indexes(navigation_indexes),
        "memory_acknowledged_navigation_indexes": sorted(acknowledged_navigation_indexes),
        "memory_acknowledged_navigation_ranges": ranges_from_indexes(
            acknowledged_navigation_indexes
        ),
    }


def memory_structure_metrics(memories: Sequence[dict[str, Any]]) -> dict[str, int]:
    counts: dict[str, int] = {
        "items": len(memories),
        "manifest": 0,
        "window": 0,
        "events": 0,
        "critical_high_events": 0,
        "characters": 0,
        "relationships": 0,
        "emotional_turns": 0,
        "decisions": 0,
        "reader_experience": 0,
        "analysis": 0,
    }
    metric_by_type = {
        "manifest": "manifest",
        "window": "window",
        "window_bundle": "window",
        "event": "events",
        "character": "characters",
        "relationship": "relationships",
        "emotional_turn": "emotional_turns",
        "decision": "decisions",
        "reader_experience": "reader_experience",
        "analysis": "analysis",
    }
    for memory in memories:
        memory_type = str(memory.get("memory_type") or "")
        metric = metric_by_type.get(memory_type)
        if metric:
            counts[metric] += 1
        data = memory.get("data")
        if memory_type == "event" and isinstance(data, dict):
            if str(data.get("severity") or "") in {"critical", "high"}:
                counts["critical_high_events"] += 1
    return counts


def loaded_skill_resources_from_trace(trace: Sequence[dict[str, Any]]) -> list[str]:
    resources: list[str] = []
    for record in trace:
        if record.get("tool_name") != "use_skill":
            continue
        receipt = record.get("receipt") or {}
        if not receipt.get("success"):
            continue
        arguments = record.get("arguments") or {}
        path = normalize_skill_path(str(arguments.get("path") or "SKILL.md"))
        if path not in resources:
            resources.append(path)
    return resources


def skill_context_metrics(
    *,
    skill_body: str,
    skill_package: SkillPackage | None,
    loaded_resources: Sequence[str],
) -> dict[str, Any]:
    if skill_package is None:
        injected_chars = len(skill_body)
        return {
            "initial_injected_content_chars": injected_chars,
            "progressively_loaded_content_chars": 0,
            "effective_skill_content_chars": injected_chars,
            "loaded_resources": [],
        }

    loaded_chars = 0
    for path in loaded_resources:
        loaded_chars += len(skill_package.read(None if path == "SKILL.md" else path))
    return {
        "initial_injected_content_chars": 0,
        "progressively_loaded_content_chars": loaded_chars,
        "effective_skill_content_chars": loaded_chars,
        "package_file_count": len(skill_package.files),
        "loaded_resources": list(loaded_resources),
    }


def evaluate_run(
    *,
    run: dict[str, Any],
    simulator: LocalMcpSimulator,
    expected_fast_ranges: Sequence[tuple[int, int]],
    skill_body: str = "",
    skill_package: SkillPackage | None = None,
) -> dict[str, Any]:
    protocol_warnings: list[str] = []
    quality_warnings: list[str] = []
    transport_diagnostics: list[str] = []
    trace = run.get("tool_trace") or []
    actual_indexes: set[int] = set()
    for record in trace:
        if record.get("tool_name") != "bookshelf_text_window_get":
            continue
        receipt = record.get("receipt") or {}
        if not receipt.get("success"):
            continue
        indexes = (receipt.get("evidence") or {}).get("chapter_indexes") or []
        actual_indexes.update(index for index in indexes if isinstance(index, int))
    actual_ranges = ranges_from_indexes(actual_indexes)
    reused_book_memories = bool(run.get("reused_book_memories"))
    if run.get("stage") == "orientation" and not reused_book_memories:
        expected = [[start, end] for start, end in expected_fast_ranges]
        if actual_ranges != expected:
            protocol_warnings.append(
                f"fast_scan_ranges mismatch: expected={expected} actual={actual_ranges}"
            )
    elif run.get("stage") == "orientation" and reused_book_memories and actual_ranges:
        protocol_warnings.append(
            f"rerender from reusable book memories unexpectedly reread text: actual={actual_ranges}"
        )

    memory_writes = [record for record in trace if record.get("tool_name") in MEMORY_WRITE_TOOLS]
    if reused_book_memories:
        reused_book_write_count = 0
        for record in memory_writes:
            arguments = record.get("arguments") or {}
            items = arguments.get("items") if record.get("tool_name") == "agent_memory_batch_upsert" else [arguments]
            if not isinstance(items, list):
                continue
            reused_book_write_count += sum(
                1
                for item in items
                if isinstance(item, dict)
                and str(item.get("scope_type") or "") == "book"
                and str(item.get("domain") or "") == "book_scan"
            )
        if reused_book_write_count:
            protocol_warnings.append(
                "rerender from reusable book memories must not rewrite book-scoped facts: "
                f"actual={reused_book_write_count}"
            )
    failed_memory_writes = [
        record for record in memory_writes if not (record.get("receipt") or {}).get("success")
    ]
    failed_skill_loads = [
        record
        for record in trace
        if record.get("tool_name") == "use_skill"
        and not (record.get("receipt") or {}).get("success")
    ]
    failed_tool_calls = [
        record for record in trace if not (record.get("receipt") or {}).get("success")
    ]
    if actual_ranges and not memory_writes:
        protocol_warnings.append("text was read but no AgentMemory write tool call was observed")
    if failed_memory_writes:
        protocol_warnings.append(f"AgentMemory write failures: {len(failed_memory_writes)}")
    if failed_skill_loads:
        protocol_warnings.append(f"use_skill failures: {len(failed_skill_loads)}")
    if failed_tool_calls:
        failed_names = [str(record.get("tool_name") or "unknown") for record in failed_tool_calls]
        protocol_warnings.append(f"failed tool calls: {failed_names}")

    profile_flow = str(run.get("profile_flow") or "none")
    profile_write_items: list[tuple[dict[str, Any], dict[str, Any]]] = []
    for record in memory_writes:
        if not (record.get("receipt") or {}).get("success"):
            continue
        arguments = record.get("arguments") or {}
        candidates = (
            arguments.get("items") or []
            if record.get("tool_name") == "agent_memory_batch_upsert"
            else [arguments]
        )
        for item in candidates:
            if not isinstance(item, dict):
                continue
            if (
                str(item.get("scope_type") or "") == "global"
                and str(item.get("scope_key") or "") == "default"
                and str(item.get("domain") or "") == "reader_preference"
                and str(item.get("memory_type") or "") == "global_profile"
            ):
                profile_write_items.append((record, item))

    active_profile = simulator.active_reader_profile()
    global_profile_memories = [
        memory
        for memory in simulator.memories
        if str(memory.get("scope_type") or "") == "global"
        and str(memory.get("scope_key") or "") == "default"
        and str(memory.get("domain") or "") == "reader_preference"
        and str(memory.get("memory_type") or "") == "global_profile"
    ]
    if profile_flow not in {"none", "seeded"}:
        protocol_warnings.append(f"obsolete reader profile flow was used: {profile_flow}")
    if profile_flow == "none" and profile_write_items:
        protocol_warnings.append(
            "model must not create or update global_profile before explicit user selection"
        )
    if active_profile is not None:
        protocol_warnings.extend(
            "reader_profile: " + violation
            for violation in personalization_contract_violations("global_profile", active_profile)
        )
    book_memories = [
        memory
        for memory in simulator.memories
        if str(memory.get("scope_type") or "") == "book"
        and str(memory.get("scope_key") or "") == simulator.work_key
        and str(memory.get("domain") or "") == "book_scan"
    ]
    foreign_book_scan_memories = [
        memory
        for memory in simulator.memories
        if str(memory.get("scope_type") or "") == "book"
        and str(memory.get("domain") or "") == "book_scan"
        and str(memory.get("scope_key") or "") != simulator.work_key
    ]
    manifests = [memory for memory in book_memories if memory.get("memory_type") == "manifest"]
    bundles = [memory for memory in book_memories if memory.get("memory_type") == "window_bundle"]
    if foreign_book_scan_memories:
        protocol_warnings.append(
            "book_scan memories were persisted under a scope_key different from the exact work_key"
        )
    if (actual_ranges or reused_book_memories) and len(manifests) != 1:
        protocol_warnings.append(
            f"expected exactly one book_scan manifest for the exact work_key; actual={len(manifests)}"
        )
    expected_bundle_count = len(expected_fast_ranges) if actual_ranges and not reused_book_memories else 1
    if (actual_ranges or reused_book_memories) and len(bundles) < expected_bundle_count:
        protocol_warnings.append(
            f"book_scan window_bundle count is incomplete: expected_at_least={expected_bundle_count} actual={len(bundles)}"
        )
    all_catalog_ids = (
        reader_tag_ids("selectable-tags.md")
        | reader_tag_ids("risk-tags.md")
        | reader_tag_ids("analysis-tags.md")
        | reader_tag_ids("profile-axes.md")
    )
    risk_tag_ids = reader_tag_ids("risk-tags.md")
    analysis_tag_ids = reader_tag_ids("analysis-tags.md")
    selectable_detectability = reader_tag_detectability("selectable-tags.md")
    for bundle_index, bundle in enumerate(bundles):
        data = bundle.get("data")
        if not isinstance(data, dict):
            protocol_warnings.append(f"window_bundle[{bundle_index}].data must be an object")
            continue
        if data.get("fact_schema_version") != 8:
            protocol_warnings.append(
                f"window_bundle[{bundle_index}].fact_schema_version must be 8"
            )
        if data.get("reader_tag_catalog_version") != 2:
            protocol_warnings.append(
                f"window_bundle[{bundle_index}].reader_tag_catalog_version must be 2"
            )
        protocol_warnings.extend(
            f"window_bundle[{bundle_index}]: {violation}"
            for violation in window_bundle_evidence_range_violations(data)
        )
        tag_hits = data.get("tag_hits")
        if not isinstance(tag_hits, list):
            protocol_warnings.append(f"window_bundle[{bundle_index}].tag_hits must be a list")
        else:
            for hit_index, tag_hit in enumerate(tag_hits):
                if not isinstance(tag_hit, dict):
                    protocol_warnings.append(
                        f"window_bundle[{bundle_index}].tag_hits[{hit_index}] must be an object"
                    )
                    continue
                protocol_warnings.extend(
                    f"window_bundle[{bundle_index}].tag_hits[{hit_index}]: {violation}"
                    for violation in personalization_contract_violations("tag_hit", tag_hit)
                )
                if str(tag_hit.get("tag_id") or "") not in all_catalog_ids:
                    protocol_warnings.append(
                        f"window_bundle[{bundle_index}].tag_hits[{hit_index}] uses an unknown tag id"
                    )
                tag_id = str(tag_hit.get("tag_id") or "")
                if tag_id in risk_tag_ids:
                    protocol_warnings.append(
                        f"window_bundle[{bundle_index}].tag_hits[{hit_index}] must store always_warn facts in risks"
                    )
                if tag_hit.get("detection_status") == "confirmed" and (
                    tag_id in analysis_tag_ids
                    or selectable_detectability.get(tag_id) == "low"
                ):
                    protocol_warnings.append(
                        f"window_bundle[{bundle_index}].tag_hits[{hit_index}] confirms a low-detectability tag during quick scan"
                    )
        risks = data.get("risks")
        if not isinstance(risks, list):
            protocol_warnings.append(f"window_bundle[{bundle_index}].risks must be a list")
        else:
            for risk_index, risk in enumerate(risks):
                if not isinstance(risk, dict):
                    protocol_warnings.append(
                        f"window_bundle[{bundle_index}].risks[{risk_index}] must be an object"
                    )
                    continue
                protocol_warnings.extend(
                    f"window_bundle[{bundle_index}].risks[{risk_index}]: {violation}"
                    for violation in personalization_contract_violations("risk", risk)
                )
                if str(risk.get("risk_kind") or "") not in risk_tag_ids:
                    protocol_warnings.append(
                        f"window_bundle[{bundle_index}].risks[{risk_index}] uses an unknown risk tag id"
                    )
        if not isinstance(data.get("relationship_profile"), dict):
            protocol_warnings.append(
                f"window_bundle[{bundle_index}].relationship_profile must be an object"
            )
        if not isinstance(data.get("content_intensity"), dict):
            protocol_warnings.append(
                f"window_bundle[{bundle_index}].content_intensity must be an object"
            )
    messages = run.get("messages") or []
    segments = collect_visible_segments(messages)
    aggregate = "\n\n".join(segments).strip()
    raw_assistant = "\n\n".join(
        str(message.get("_raw_content", message.get("content")) or "")
        for message in messages
        if message.get("role") == "assistant" and message.get("content") is not None
    )
    hidden_tool_step_count = sum(
        1
        for message in messages
        if message.get("role") == "assistant"
        and message.get("tool_calls")
        and visible_text(str(message.get("content") or ""))
    )
    if not aggregate:
        protocol_warnings.append("no visible assistant report was produced")
    elif len(aggregate) < 200:
        quality_warnings.append(f"visible report is unusually short: {len(aggregate)} chars")
    if "book_scan_delta" in raw_assistant or "legado-book-scan" in raw_assistant:
        protocol_warnings.append("legacy private book-scan protocol appeared in assistant output")
    if "<｜DSML｜" in raw_assistant or "<|DSML|" in raw_assistant:
        transport_diagnostics.append(
            "provider tool-protocol markers appeared in raw transport and were isolated from visible/provider projection"
        )
    if aggregate.startswith("{") and aggregate.endswith("}"):
        protocol_warnings.append("visible assistant output looks like raw JSON instead of a reader report")
    preference_delta = bool(segments and segments[-1].startswith("## 推荐已更新"))
    if len(segments) > 1 and not preference_delta:
        quality_warnings.append(
            f"assistant produced {len(segments)} visible text segments; inspect whether progress text leaked before the report"
        )
    if re.search(r"(?m)^\s*\|.+\|\s*$", aggregate):
        quality_warnings.append("visible report contains a Markdown table; mobile quick-scan reports should use compact sections")
    for label, markers in {
        "适读结论": ("适读结论",),
        "作品成分": ("作品速览", "分类流派", "核心元素", "主要驱动力"),
        "阅读感受": ("阅读感受", "样本氛围", "主要压力"),
        "证据边界": ("开头与结尾样本", "中段人物成长", "隐藏风险"),
    }.items():
        if aggregate and not any(marker in aggregate for marker in markers):
            quality_warnings.append(f"visible report may be missing {label} information")

    reader_profile = active_profile
    report_skill_loaded = any(
        record.get("tool_name") == "use_skill"
        and (record.get("receipt") or {}).get("success")
        and str((record.get("arguments") or {}).get("name") or "")
        in {"book_scan_report", "个性化扫书报告"}
        for record in trace
    )
    skill_version = str((run.get("skill") or {}).get("version") or "")
    p3_or_newer = skill_version.isdigit() and int(skill_version) >= 52
    final_assistant_content = next(
        (
            str(message.get("content") or "")
            for message in reversed(messages)
            if message.get("role") == "assistant"
            and message.get("_visible") is not False
            and not message.get("tool_calls")
        ),
        "",
    )
    if (
        run.get("stage") == "orientation"
        and aggregate
        and final_assistant_content
        and (report_skill_loaded or p3_or_newer)
    ):
        confirmed_tags = confirmed_fact_tag_ids(book_memories)
        confirmed_risks = confirmed_warning_risk_ids(book_memories)
        if visible_text(final_assistant_content).startswith("## 推荐已更新"):
            interaction_result = latest_interaction_result(messages)
            interaction_status = str((interaction_result or {}).get("status") or "saved")
            expected_saved_lines = (
                interaction_result_display_lines(messages, interaction_result)
                if interaction_result is not None
                else []
            )
            prior_full_report_exists = any(
                visible_text(str(message.get("content") or "")).startswith("## 适读结论")
                for message in messages[:-1]
                if message.get("role") == "assistant" and not message.get("tool_calls")
            )
            if not prior_full_report_exists:
                protocol_warnings.append(
                    "preference_rerender: original full report is missing from message history"
                )
            protocol_warnings.extend(
                "preference_rerender: " + violation
                for violation in preference_rerender_contract_violations(
                    final_assistant_content,
                    reader_profile,
                    confirmed_tag_ids=confirmed_tags,
                    interaction_status=interaction_status,
                    expected_saved_lines=expected_saved_lines,
                )
            )
        else:
            protocol_warnings.extend(
                "personalized_report: " + violation
                for violation in personalized_report_contract_violations(
                    final_assistant_content,
                    reader_profile,
                    confirmed_tag_ids=confirmed_tags,
                    confirmed_risk_ids=confirmed_risks,
                )
            )
    coverage = evidence_coverage(simulator.receipts)
    loaded_resources = loaded_skill_resources_from_trace(trace)
    return {
        "schema_version": RUN_SCHEMA_VERSION,
        "mode": "simulated-agent",
        "status": run.get("status"),
        "stage": run.get("stage"),
        "skill": run.get("skill"),
        "book": run.get("book"),
        "expected_fast_scan_ranges": [[start, end] for start, end in expected_fast_ranges],
        "actual_text_window_ranges": actual_ranges,
        "protocol_warnings": protocol_warnings,
        "quality_warnings": quality_warnings,
        "transport_diagnostics": transport_diagnostics,
        "visible_segments": segments,
        "visible_report": aggregate,
        "reader_profile": reader_profile,
        "profile_flow": profile_flow,
        "reader_preference_memory": {
            "profile_write_count": len(profile_write_items),
            "global_profile_memory_count": len(global_profile_memories),
        },
        "book_fact_fingerprint": book_fact_fingerprint(simulator.memories),
        "output_contract_violations": list(run.get("output_contract_violations") or []),
        "hidden_tool_step_content_count": hidden_tool_step_count,
        "tool_call_count": len(trace),
        "tool_calls": [
            {
                "sequence": record.get("sequence"),
                "tool_name": record.get("tool_name"),
                "arguments": record.get("arguments"),
                "receipt_id": (record.get("receipt") or {}).get("receipt_id"),
                "success": (record.get("receipt") or {}).get("success"),
                "complete": (record.get("receipt") or {}).get("complete"),
                "artifact_file": record.get("artifact_file"),
            }
            for record in trace
        ],
        "memory_artifact": {
            "item_count": len(simulator.memories),
            "write_count": len(memory_writes),
            "file": str(simulator.memories_path),
            "structure_metrics": memory_structure_metrics(simulator.memories),
        },
        "loaded_skill_resources": loaded_resources,
        "skill_context": skill_context_metrics(
            skill_body=skill_body,
            skill_package=skill_package,
            loaded_resources=loaded_resources,
        ),
        "evidence_coverage": coverage,
        "usage": run.get("usage") or {},
        "elapsed_seconds": max(0, int(run.get("updated_at") or 0) - int(run.get("created_at") or 0)),
        "simulation_limits": [
            "工具适配器只模拟声明的返回形状、回执和通用 AgentMemory 工件，不是 Android MCP/Room 实现。",
            "未执行 App Hook、权限弹窗、自动压缩、进程恢复或 UI Markdown/interaction 渲染。",
            "业务记忆保持模型原样；评测器只统计类型，不补写、合并或修正人物/关系/雷点字段。",
            "真实 App 协议稳定性仍需设备导出 trace 单独验收。",
        ],
        "updated_at": int(time.time()),
    }


def write_report(evaluation: dict[str, Any], path: Path) -> None:
    warnings = (evaluation.get("protocol_warnings") or []) + (evaluation.get("quality_warnings") or [])
    memory = evaluation.get("memory_artifact") or {}
    lines = [
        f"# {((evaluation.get('book') or {}).get('name') or '未知作品')} AI 扫书评测",
        "",
        f"- 模式：{evaluation.get('mode')}",
        f"- 状态：{evaluation.get('status')}",
        f"- 阶段：{evaluation.get('stage')}",
        f"- Skill：{((evaluation.get('skill') or {}).get('id'))}@{((evaluation.get('skill') or {}).get('version'))}",
        f"- 工具调用：{evaluation.get('tool_call_count', 0)}",
        f"- AgentMemory 条目：{memory.get('item_count', 0)}",
        f"- AgentMemory 写入：{memory.get('write_count', 0)}",
        f"- API Token：{((evaluation.get('usage') or {}).get('total_tokens') or 0)}",
        "",
        "## 评测层级与限制",
        "",
    ]
    lines.extend(f"- {item}" for item in evaluation.get("simulation_limits") or [])
    lines += [
        "",
        "## 快速定位工具轨迹",
        "",
        f"- 预期：`{evaluation.get('expected_fast_scan_ranges')}`",
        f"- 实际：`{evaluation.get('actual_text_window_ranges')}`",
        "",
        "## 协议与质量警告",
        "",
    ]
    if warnings:
        lines.extend(f"- {warning}" for warning in warnings)
    else:
        lines.append("- 当前评测层未发现警告。")
    diagnostics = evaluation.get("transport_diagnostics") or []
    if diagnostics:
        lines += ["", "## Provider 传输诊断", ""]
        lines.extend(f"- {item}" for item in diagnostics)
    lines += ["", "## 模型可见报告（原顺序聚合）", ""]
    lines.append(evaluation.get("visible_report") or "_没有可见报告。_")
    path.write_text("\n".join(lines).rstrip() + "\n", encoding="utf-8")


def reset_eval_state(out_dir: Path) -> None:
    for name in ("runs", "requests", "responses", "tool_results", "shards"):
        target = out_dir / name
        if target.exists():
            shutil.rmtree(target)
    for name in (
        "memories.json",
        "tool_receipts.json",
        "evaluation.json",
        "report.md",
        "plan.json",
        "manifest.json",
    ):
        target = out_dir / name
        if target.exists():
            target.unlink()


def query_artifacts(out_dir: Path, query: str) -> int:
    evaluation = read_json(out_dir / "evaluation.json")
    if not isinstance(evaluation, dict):
        print("No evaluation.json. Run plan or simulated-agent first.", file=sys.stderr)
        return 2
    if query == "report":
        print(evaluation.get("visible_report") or "")
    elif query == "warnings":
        print(
            json.dumps(
                {
                    "protocol_warnings": evaluation.get("protocol_warnings") or [],
                    "quality_warnings": evaluation.get("quality_warnings") or [],
                },
                ensure_ascii=False,
                indent=2,
            )
        )
    elif query == "tool_calls":
        print(json.dumps(evaluation.get("tool_calls") or [], ensure_ascii=False, indent=2))
    elif query == "memories":
        print(json.dumps(read_json(out_dir / "memories.json", []), ensure_ascii=False, indent=2))
    elif query == "coverage":
        print(json.dumps(evaluation.get("evidence_coverage") or {}, ensure_ascii=False, indent=2))
    elif query == "usage":
        print(json.dumps(evaluation.get("usage") or {}, ensure_ascii=False, indent=2))
    elif query == "raw":
        run_file = Path(str(evaluation.get("run_file") or ""))
        run = read_json(run_file, {}) if run_file.is_file() else {}
        print(json.dumps(run.get("messages") or [], ensure_ascii=False, indent=2))
    else:
        print(json.dumps(evaluation, ensure_ascii=False, indent=2))
    return 0


def run_simulated_agent(
    *,
    args: argparse.Namespace,
    out_dir: Path,
    book_path: Path,
    name: str,
    author: str,
    synopsis: str,
    book_status: str,
    chapters: Sequence[Chapter],
    skill_metadata: dict[str, str],
    skill_body: str,
    skill_hash: str,
    source_hash: str,
    skill_mode: str = "single",
    skill_package: SkillPackage | None = None,
    additional_skill_packages: Sequence[SkillPackage] = (),
    reader_profile: dict[str, Any] | None = None,
) -> int:
    run_dir = out_dir / "runs" / args.stage
    request_dir = out_dir / "requests" / args.stage
    response_dir = out_dir / "responses" / args.stage
    for directory in (run_dir, request_dir, response_dir):
        directory.mkdir(parents=True, exist_ok=True)
    run_path = run_dir / "run.json"
    reader_profile_hash = stable_json_hash(reader_profile) if reader_profile else ""
    conversation_id = hashlib.sha256(
        (
            f"{book_path}\n{args.stage}\n{skill_mode}\n{args.quick_scan_chapters}\n"
            f"{skill_hash}\n{reader_profile_hash}"
        ).encode("utf-8")
    ).hexdigest()[:32]
    simulator = LocalMcpSimulator(
        book_path=book_path,
        name=name,
        author=author,
        synopsis=synopsis,
        book_status=book_status,
        chapters=chapters,
        skill_revision=str(skill_metadata.get("version") or "unknown"),
        state_dir=out_dir,
        conversation_id=conversation_id,
        skill_package=skill_package,
        additional_skill_packages=additional_skill_packages,
        reader_profile=reader_profile,
    )
    run = read_json(run_path)
    if isinstance(run, dict):
        for key, expected in {
            "source_hash": source_hash,
            "model": args.model,
            "skill_hash": skill_hash,
            "stage": args.stage,
            "skill_mode": skill_mode,
            "quick_scan_chapters": args.quick_scan_chapters,
            "reader_profile_hash": reader_profile_hash,
            "profile_flow": "seeded" if reader_profile else "none",
            "reused_book_memories": bool(args.reuse_book_memories.strip()),
        }.items():
            if run.get(key) != expected:
                raise ValueError(f"Archived run {key} changed; use --reset")
        if run.get("status") == "completed":
            evaluation = evaluate_run(
                run=run,
                simulator=simulator,
                expected_fast_ranges=fast_scan_ranges(len(chapters), args.quick_scan_chapters),
                skill_body=skill_body,
                skill_package=skill_package,
            )
            evaluation["run_file"] = str(run_path)
            write_json(out_dir / "evaluation.json", evaluation)
            write_report(evaluation, out_dir / "report.md")
            print(f"Run already completed; rebuilt evaluation from {run_path}")
            return 1 if evaluation["protocol_warnings"] else 0
        print(f"Resuming interrupted run from step {run.get('step', 0)}")
    else:
        messages = build_initial_messages(
            skill_body=skill_body,
            skill_mode=skill_mode,
            skill_metadata=skill_metadata,
            skill_hash=skill_hash,
            name=name,
            author=author,
            work_key=simulator.work_key,
            status=book_status,
            total=len(chapters),
            synopsis=synopsis,
            stage=args.stage,
            target_chapters=args.target_chapters,
            enabled_skill_metadata=[
                package.metadata for package in additional_skill_packages
            ],
        )
        run = {
            "schema_version": RUN_SCHEMA_VERSION,
            "mode": "simulated-agent",
            "status": "running",
            "stage": args.stage,
            "source_hash": source_hash,
            "model": args.model,
            "skill_hash": skill_hash,
            "skill_mode": skill_mode,
            "quick_scan_chapters": args.quick_scan_chapters,
            "reader_profile_hash": reader_profile_hash,
            "profile_flow": "seeded" if reader_profile else "none",
            "reused_book_memories": bool(args.reuse_book_memories.strip()),
            "skill": {
                "id": skill_metadata.get("id"),
                "version": skill_metadata.get("version"),
                "sha256": skill_hash,
            },
            "book": {
                "name": name,
                "author": author,
                "work_key": simulator.work_key,
                "total_count": len(chapters),
                "book_status": book_status,
            },
            "conversation_id": conversation_id,
            "reader_profile": reader_profile,
            "step": 0,
            "messages": messages,
            "tool_trace": [],
            "usage": {"prompt_tokens": 0, "completion_tokens": 0, "total_tokens": 0},
            "created_at": int(time.time()),
            "updated_at": int(time.time()),
        }
        write_json(run_path, run)

    api_key = os.environ.get(args.api_key_env, "").strip()
    if not api_key:
        print(
            f"Missing API key environment variable: {args.api_key_env}. Use --mode plan or --dry-run.",
            file=sys.stderr,
        )
        return 2
    tools = openai_tools(
        include_use_skill=(skill_mode == "multifile" or bool(additional_skill_packages)),
    )
    failure: str | None = None
    while int(run.get("step") or 0) < args.max_steps:
        execute_pending_tool_calls(run=run, simulator=simulator, run_path=run_path)
        step = int(run.get("step") or 0) + 1
        print(f"[step {step}/{args.max_steps}] messages={len(run['messages'])}")
        try:
            request_body = build_request_body(
                model=args.model,
                messages=run["messages"],
                tools=tools,
                temperature=args.temperature,
                max_tokens=args.max_tokens,
            )
            write_json(request_dir / f"step_{step:04d}.json", request_body)
            response = call_api_step(
                api_url=args.api_url,
                api_key=api_key,
                body=request_body,
                timeout=args.timeout,
                retries=args.retries,
                retry_sleep=args.retry_sleep,
            )
            write_json(response_dir / f"step_{step:04d}.json", response)
            message = dict(response["choices"][0]["message"])
            message["role"] = "assistant"
            if message.get("content") is None:
                message["content"] = ""
            raw_content = str(message.get("content") or "")
            provider_content = provider_protocol_prefix(raw_content)
            if provider_content != raw_content:
                message["_raw_content"] = raw_content
                message["content"] = provider_content
            run["messages"].append(message)
            add_usage(run["usage"], usage_from_response(response))
            tool_calls = message.get("tool_calls") or []
            run["step"] = step
            run["updated_at"] = int(time.time())
            write_json(run_path, run)
            if not tool_calls:
                violations = output_contract_violations(str(message.get("content") or ""), skill_package)
                report_skill_loaded = any(
                    record.get("tool_name") == "use_skill"
                    and (record.get("receipt") or {}).get("success")
                    and str((record.get("arguments") or {}).get("name") or "")
                    in {"book_scan_report", "个性化扫书报告"}
                    for record in run.get("tool_trace") or []
                )
                active_profile = simulator.active_reader_profile()
                if args.stage == "orientation" and report_skill_loaded:
                    violations.extend(
                        "personalized_report: " + violation
                        for violation in personalized_report_contract_violations(
                            str(message.get("content") or ""),
                            active_profile,
                            confirmed_tag_ids=confirmed_fact_tag_ids(simulator.memories),
                            confirmed_risk_ids=confirmed_warning_risk_ids(simulator.memories),
                        )
                    )
                run["output_contract_violations"] = violations
                run["status"] = "completed"
                run["updated_at"] = int(time.time())
                write_json(run_path, run)
                break
            execute_pending_tool_calls(run=run, simulator=simulator, run_path=run_path)
            if args.sleep > 0:
                time.sleep(args.sleep)
        except Exception as error:  # noqa: BLE001 - run state is persisted before returning
            failure = str(error)
            run["status"] = "interrupted"
            run["last_error"] = failure
            run["updated_at"] = int(time.time())
            write_json(run_path, run)
            (run_dir / "last_error.txt").write_text(failure, encoding="utf-8")
            print(f"Interrupted: {failure}", file=sys.stderr)
            break
    else:
        run["status"] = "incomplete"
        run["last_error"] = f"max_steps reached: {args.max_steps}"
        run["updated_at"] = int(time.time())
        write_json(run_path, run)

    evaluation = evaluate_run(
        run=run,
        simulator=simulator,
        expected_fast_ranges=fast_scan_ranges(len(chapters), args.quick_scan_chapters),
        skill_body=skill_body,
        skill_package=skill_package,
    )
    if run.get("status") != "completed":
        evaluation["protocol_warnings"].append(
            f"run did not complete: status={run.get('status')} error={run.get('last_error') or failure or 'unknown'}"
        )
    evaluation["run_file"] = str(run_path)
    write_json(out_dir / "evaluation.json", evaluation)
    write_report(evaluation, out_dir / "report.md")
    print(
        f"status={run.get('status')} tools={len(run.get('tool_trace') or [])} "
        f"warnings={len(evaluation['protocol_warnings']) + len(evaluation['quality_warnings'])} "
        f"report={out_dir / 'report.md'}"
    )
    return 1 if run.get("status") != "completed" or evaluation["protocol_warnings"] else 0


def main() -> int:
    load_dotenv(ROOT / ".env")
    args = parse_args()
    book_path = Path(args.book).expanduser().resolve()
    if not book_path.is_file():
        print(f"Book not found: {book_path}", file=sys.stderr)
        return 2
    if args.target_chapters < 1 or args.max_steps < 1 or args.quick_scan_chapters < 1:
        raise ValueError("--target-chapters, --max-steps and --quick-scan-chapters must be >= 1")
    preamble, chapters = split_chapters(read_text(book_path))
    inferred_name, inferred_author, synopsis = infer_book_metadata(book_path, preamble)
    name = args.book_name or inferred_name
    author = args.author if args.author is not None else inferred_author
    status = infer_book_status(args.book_status, chapters, preamble)
    source_hash = hashlib.sha256(book_path.read_bytes()).hexdigest()
    skill_package: SkillPackage | None = None
    if args.skill_mode == "multifile":
        skill_package = load_skill_package(Path(args.skill_package))
        additional_skill_packages = discover_companion_skill_packages(skill_package)
        skill_metadata = skill_package.metadata
        skill_body = ""
        skill_hash = skill_package.package_hash
    else:
        additional_skill_packages = discover_companion_skill_packages(
            load_skill_package(SKILL_FILE.parent)
        )
        skill_content = SKILL_FILE.read_text(encoding="utf-8")
        skill_metadata, skill_body = parse_frontmatter(skill_content)
        skill_hash = hashlib.sha256(skill_content.encode("utf-8")).hexdigest()
    reader_profile = (
        load_reader_profile(Path(args.reader_profile).expanduser().resolve())
        if args.reader_profile.strip()
        else None
    )
    work_key = f"{name}\n{author}" if author else name
    reusable_book_memories = (
        load_reusable_book_memories(
            Path(args.reuse_book_memories),
            work_key,
        )
        if args.reuse_book_memories.strip()
        else []
    )
    out_dir = stable_book_dir(Path(args.out).resolve(), book_path, name, author)
    if args.experiment_label.strip():
        label = re.sub(r"[^0-9A-Za-z_-]+", "_", args.experiment_label.strip()).strip("_")
        if not label:
            raise ValueError("--experiment-label must contain a letter, number, underscore or dash")
        out_dir = out_dir / label
    out_dir.mkdir(parents=True, exist_ok=True)
    if args.reset:
        reset_eval_state(out_dir)
    if reusable_book_memories:
        write_json(out_dir / "memories.json", reusable_book_memories)
    plan = plan_document(
        book_path=book_path,
        name=name,
        author=author,
        status=status,
        chapters=chapters,
        skill_metadata=skill_metadata,
        skill_hash=skill_hash,
        quick_scan_chapters=args.quick_scan_chapters,
        skill_mode=args.skill_mode,
    )
    write_json(out_dir / "plan.json", plan)

    print(
        f"book={name} author={author or '未知'} chapters={len(chapters)} "
        f"status={status} skill={skill_metadata.get('id')}@{skill_metadata.get('version')} out={out_dir}"
    )
    if args.query:
        return query_artifacts(out_dir, args.query)
    if args.rebuild_report:
        evaluation = read_json(out_dir / "evaluation.json")
        if not isinstance(evaluation, dict):
            print("No evaluation.json to rebuild.", file=sys.stderr)
            return 2
        write_report(evaluation, out_dir / "report.md")
        print(f"Rebuilt {out_dir / 'report.md'} without modifying model output")
        return 0
    if args.mode == "plan" or args.dry_run:
        request_preview = {
            "model": args.model,
            "messages": build_initial_messages(
                skill_body=skill_body,
                skill_mode=args.skill_mode,
                skill_metadata=skill_metadata,
                skill_hash=skill_hash,
                name=name,
                author=author,
                work_key=f"{name}\n{author}" if author else name,
                status=status,
                total=len(chapters),
                synopsis=synopsis,
                stage=args.stage,
                target_chapters=args.target_chapters,
            ),
            "tools": openai_tools(
                include_use_skill=(
                    args.skill_mode == "multifile" or bool(additional_skill_packages)
                )
            ),
            "note": "Preview only; no tool result or AgentMemory state is fabricated.",
        }
        write_json(out_dir / "requests" / "dry_run_initial.json", request_preview)
        plan_evaluation = {
            **plan,
            "status": "plan_only",
            "stage": args.stage,
            "protocol_warnings": [],
            "quality_warnings": [],
            "visible_report": "",
            "tool_calls": [],
            "memory_artifact": {},
            "evidence_coverage": {},
            "usage": {},
            "simulation_limits": plan["limitations"],
        }
        write_json(out_dir / "evaluation.json", plan_evaluation)
        if args.json:
            print(json.dumps(plan, ensure_ascii=False, indent=2))
        else:
            for item in plan["fast_scan_ranges"]:
                print(
                    f"fast-scan {item['start']}-{item['end'] - 1}: "
                    f"{item['first_title']} -> {item['last_title']}"
                )
            print(plan["rule"])
        return 0
    return run_simulated_agent(
        args=args,
        out_dir=out_dir,
        book_path=book_path,
        name=name,
        author=author,
        synopsis=synopsis,
        book_status=status,
        chapters=chapters,
        skill_metadata=skill_metadata,
        skill_body=skill_body,
        skill_hash=skill_hash,
        source_hash=source_hash,
        skill_mode=args.skill_mode,
        skill_package=skill_package,
        additional_skill_packages=additional_skill_packages,
        reader_profile=reader_profile,
    )


if __name__ == "__main__":
    raise SystemExit(main())
