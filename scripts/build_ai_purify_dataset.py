#!/usr/bin/env python3
"""Build AI purify payloads and draft expected responses from a novel text file.

The generated payload files mirror the app's chapter batch user payload:

[
  {"id": 1, "input": "..."},
  {"id": 2, "input": "..."}
]

The draft expected response files intentionally use the model contract only:

[
  {"id": 2, "output": "..."}
]
"""

from __future__ import annotations

import argparse
import json
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Callable


ROOT = Path(__file__).resolve().parents[1]
CHAPTER_RE = re.compile(r"^(第[0-9一二三四五六七八九十百千万]+章(?:\s+.*|[（(][^）)\r\n]+[）)]|[^\r\n]*)?)$", re.M)


@dataclass
class Chapter:
    number: int
    title: str
    paragraphs: list[str]


@dataclass
class DraftChange:
    id: int
    input: str
    output: str
    rules: list[str]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--book", default="data/山河稷 作者：姬叉.txt")
    parser.add_argument("--out", default="data/ai-purify-dataset/shanheji")
    parser.add_argument("--max-chapters", type=int, default=50)
    parser.add_argument("--max-batch-input-length", type=int, default=10000)
    args = parser.parse_args()

    book_path = (ROOT / args.book).resolve()
    out_dir = (ROOT / args.out).resolve()
    chapters = parse_chapters(book_path)
    if args.max_chapters is not None:
        chapters = chapters[: args.max_chapters]

    payload_dir = out_dir / "payloads"
    expected_dir = out_dir / "expected_draft"
    review_dir = out_dir / "review"
    fixture_path = out_dir / f"{out_dir.name}_live_eval_draft.json"
    for directory in (payload_dir, expected_dir, review_dir):
        directory.mkdir(parents=True, exist_ok=True)

    index: dict[str, object] = {
        "sourceBook": str(book_path.relative_to(ROOT)),
        "output": str(out_dir.relative_to(ROOT)),
        "maxBatchInputLength": args.max_batch_input_length,
        "chapters": [],
    }
    fixture_cases: list[dict[str, object]] = []
    total_payloads = 0
    total_changes = 0
    total_paragraphs = 0

    for chapter in chapters:
        body_paragraphs = chapter.paragraphs[1:] if chapter.paragraphs and chapter.paragraphs[0] == chapter.title else chapter.paragraphs
        payload = [
            {"id": index + 1, "input": normalize_selected_text(text)}
            for index, text in enumerate(body_paragraphs)
            if normalize_selected_text(text)
        ]
        chunks = split_payload(payload, args.max_batch_input_length)
        chapter_changes = build_draft_changes(payload)
        change_by_id = {change.id: change for change in chapter_changes}
        chapter_entry = {
            "chapterNumber": chapter.number,
            "title": chapter.title,
            "paragraphs": len(payload),
            "payloadChunks": len(chunks),
            "draftChanges": len(chapter_changes),
            "chunks": [],
        }

        for chunk_index, chunk in enumerate(chunks, start=1):
            stem = f"{chapter.number:03d}_{chunk_index:02d}"
            payload_file = payload_dir / f"{stem}.json"
            expected_file = expected_dir / f"{stem}.json"
            review_file = review_dir / f"{stem}.json"
            chunk_ids = {item["id"] for item in chunk}
            expected = [
                {"id": change.id, "output": change.output}
                for change in chapter_changes
                if change.id in chunk_ids
            ]
            review = [
                {
                    "id": change.id,
                    "input": change.input,
                    "output": change.output,
                    "rules": change.rules,
                    "status": "draft",
                }
                for change in chapter_changes
                if change.id in chunk_ids
            ]
            write_json(payload_file, chunk)
            write_json(expected_file, expected)
            write_json(review_file, review)
            fixture_cases.append(
                {
                    "name": f"chapter {chapter.number:03d} chunk {chunk_index:02d}",
                    "chapterNumber": chapter.number,
                    "chapterTitle": chapter.title,
                    "payloadFile": str(payload_file.relative_to(ROOT)),
                    "expectedDraftFile": str(expected_file.relative_to(ROOT)),
                    "inputs": chunk,
                    "expectedChanges": expected,
                }
            )
            chapter_entry["chunks"].append(
                {
                    "chunk": chunk_index,
                    "payloadFile": str(payload_file.relative_to(ROOT)),
                    "expectedDraftFile": str(expected_file.relative_to(ROOT)),
                    "reviewFile": str(review_file.relative_to(ROOT)),
                    "paragraphs": len(chunk),
                    "draftChanges": len(expected),
                }
            )
            total_payloads += 1

        index["chapters"].append(chapter_entry)
        total_changes += len(chapter_changes)
        total_paragraphs += len(payload)

    index["summary"] = {
        "chapters": len(chapters),
        "payloadChunks": total_payloads,
        "paragraphs": total_paragraphs,
        "draftChanges": total_changes,
    }
    write_json(out_dir / "index.json", index)
    write_json(
        fixture_path,
        {
            "name": f"{out_dir.name}_live_eval_draft",
            "sourceBook": str(book_path.relative_to(ROOT)),
            "note": "Draft expectedChanges are generated by local rules and must be manually reviewed before using as gold data.",
            "cases": fixture_cases,
        },
    )

    print(f"chapters={len(chapters)}")
    print(f"payload_chunks={total_payloads}")
    print(f"paragraphs={total_paragraphs}")
    print(f"draft_changes={total_changes}")
    print(f"index={out_dir / 'index.json'}")
    print(f"fixture={fixture_path}")
    return 0


def parse_chapters(path: Path) -> list[Chapter]:
    text = path.read_text(encoding="utf-8")
    matches = list(CHAPTER_RE.finditer(text))
    if not matches:
        raise RuntimeError(f"No chapter title matched in {path}")
    chapters: list[Chapter] = []
    for index, match in enumerate(matches):
        start = match.start()
        end = matches[index + 1].start() if index + 1 < len(matches) else len(text)
        block = text[start:end]
        paragraphs = [normalize_selected_text(line) for line in block.splitlines() if normalize_selected_text(line)]
        chapters.append(
            Chapter(
                number=index + 1,
                title=match.group(1).strip(),
                paragraphs=paragraphs,
            )
        )
    return chapters


def normalize_selected_text(text: str) -> str:
    return "\n".join(line.strip() for line in text.splitlines()).strip()


def split_payload(payload: list[dict[str, object]], max_batch_input_length: int) -> list[list[dict[str, object]]]:
    chunks: list[list[dict[str, object]]] = []
    current: list[dict[str, object]] = []
    current_length = 0
    for item in payload:
        text = str(item["input"])
        if current and current_length + len(text) > max_batch_input_length:
            chunks.append(current)
            current = []
            current_length = 0
        current.append(item)
        current_length += len(text)
    if current:
        chunks.append(current)
    return chunks


def build_draft_changes(payload: list[dict[str, object]]) -> list[DraftChange]:
    changes: list[DraftChange] = []
    for item in payload:
        item_id = int(item["id"])
        source = str(item["input"])
        output, rules = apply_draft_rules(source)
        if output != source:
            changes.append(DraftChange(item_id, source, output, rules))
    return changes


def apply_draft_rules(text: str) -> tuple[str, list[str]]:
    rules: list[str] = []
    current = text

    ad_delete_patterns: list[tuple[str, str]] = [
        (r"灵梦", "ad:lingmeng"),
        (r"版权归原作者|下载后24小时内删除|支持订阅正版|拒绝盗版", "ad:copyright-notice"),
        (r"杜绝沉迷网络小说|更多全网小说", "ad:reading-notice"),
        (r"免费外群|中转群|QQ群|微信群", "ad:group-promo"),
        (r"本群免费提取|已购vip章节|已购VIP章节|私聊群主", "ad:vip-extract-promo"),
    ]
    for pattern, rule in ad_delete_patterns:
        if re.search(pattern, current, flags=re.I):
            return "", [rule]

    replacements: list[tuple[str, str, str]] = [
        ("为什幺", "为什么", "variant:为什幺->为什么"),
        ("为什么幺", "为什么", "variant:为什么幺->为什么"),
        ("什幺", "什么", "variant:什幺->什么"),
        ("甚幺", "什么", "variant:甚幺->什么"),
        ("怎幺", "怎么", "variant:怎幺->怎么"),
        ("这幺", "这么", "variant:这幺->这么"),
        ("那幺", "那么", "variant:那幺->那么"),
        ("多幺", "多么", "variant:多幺->多么"),
        ("麽", "么", "variant:麽->么"),
        ("擡", "抬", "variant:擡->抬"),
    ]
    for old, new, rule in replacements:
        if old in current:
            current = current.replace(old, new)
            rules.append(rule)

    current = regex_replace(current, r"幺(?=[？?!！。．，,、」”])", "么", "variant:sentence-final-幺->么", rules)
    current = regex_replace(
        current,
        r"[①②③④⑤⑥⑦⑧⑨⑩⑪⑫⑬⑭⑮⑯⑰⑱⑲⑳⑴⑵⑶⑷⑸⑹⑺⑻⑼⑽㈠㈡㈢㈣㈤㈥㈦㈧㈨㈩０１２３４５６７８９]",
        "",
        "noise:embedded-number-marker",
        rules,
    )
    return current, rules


def regex_replace(text: str, pattern: str, replacement: str, rule: str, rules: list[str]) -> str:
    changed = re.sub(pattern, replacement, text)
    if changed != text:
        rules.append(rule)
    return changed


def write_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    raise SystemExit(main())
