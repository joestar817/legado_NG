#!/usr/bin/env python3
"""Experimental host-driven two-stage BookScan pipeline.

This deliberately does not execute the current workflow Skill.  It is a lab
prototype for comparing a small host-owned sampling state machine against the
full Agent workflow without changing Android production code.
"""

from __future__ import annotations

import argparse
import hashlib
import http.client
import json
import os
import re
import sys
import threading
import time
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
from typing import Any, Sequence

SCRIPT_ROOT = Path(__file__).resolve().parents[1]
if str(SCRIPT_ROOT) not in sys.path:
    sys.path.insert(0, str(SCRIPT_ROOT))

from scripts import book_scan_eval as evaluator
from scripts import book_scan_lab as lab


DEFAULT_OUT = evaluator.ROOT / "build/book_scan_two_stage_lab"
JSON_FENCE_RE = re.compile(r"(?is)```(?:json)?\s*(.*?)\s*```")


def chapter_snippet(chapter: evaluator.Chapter, head: int, tail: int) -> dict[str, Any]:
    content = chapter.content or ""
    head_text = content[:head]
    tail_text = content[-tail:] if tail and len(content) > len(head_text) else ""
    return {
        "index": chapter.index,
        "chapter": chapter.index + 1,
        "title": chapter.title,
        "head": head_text,
        "tail": tail_text,
        "content_chars": len(content),
        "middle_omitted": len(content) > len(head_text) + len(tail_text),
    }


def navigation_pack(
    chapters: Sequence[evaluator.Chapter], head: int = 200, tail: int = 200
) -> list[dict[str, Any]]:
    return [chapter_snippet(chapter, head, tail) for chapter in chapters]


def parse_json_object(content: str) -> dict[str, Any]:
    candidate = content.strip()
    match = JSON_FENCE_RE.fullmatch(candidate)
    if match:
        candidate = match.group(1).strip()
    value = json.loads(candidate)
    if not isinstance(value, dict):
        raise ValueError("model response must be a JSON object")
    return value


def normalize_plan(
    raw: dict[str, Any], total: int, max_suspects: int
) -> dict[str, Any]:
    suspects: list[dict[str, Any]] = []
    seen: set[int] = set()
    for item in raw.get("suspect_chapters") or []:
        if not isinstance(item, dict):
            continue
        try:
            index = int(item.get("index"))
        except (TypeError, ValueError):
            continue
        if index < 0 or index >= total or index in seen:
            continue
        seen.add(index)
        suspects.append(
            {
                "index": index,
                "chapter": index + 1,
                "reason": str(item.get("reason") or "需要核对上下文").strip(),
                "priority": str(item.get("priority") or "medium").strip(),
            }
        )
        if len(suspects) >= max_suspects:
            break
    trace_entities: list[dict[str, Any]] = []
    entity_seen: set[str] = set()
    for item in raw.get("trace_entities") or []:
        if isinstance(item, str):
            item = {"name": item}
        if not isinstance(item, dict):
            continue
        name = str(item.get("name") or "").strip()
        if len(name) < 2 or name in entity_seen:
            continue
        entity_seen.add(name)
        trace_entities.append(
            {
                "name": name,
                "reason": str(item.get("reason") or "人物或关系线需要持续追踪").strip(),
                "priority": str(item.get("priority") or "medium").strip(),
            }
        )
        if len(trace_entities) >= 8:
            break
    return {
        "profile": raw.get("profile") if isinstance(raw.get("profile"), dict) else {},
        "suspect_chapters": suspects,
        "trace_entities": trace_entities,
        "open_threads": [str(item) for item in raw.get("open_threads") or []],
        "unknowns": [str(item) for item in raw.get("unknowns") or []],
    }


def full_text_indexes(total: int, plan: dict[str, Any], baseline: int = 3) -> list[int]:
    indexes = list(range(min(baseline, total)))
    indexes.extend(range(max(0, total - baseline), total))
    indexes.extend(int(item["index"]) for item in plan.get("suspect_chapters") or [])
    return list(dict.fromkeys(indexes))


def stratified_indexes(total: int, count: int) -> list[int]:
    """Return deterministic whole-book navigation points, including both ends."""
    if total <= 0 or count <= 0:
        return []
    if count >= total:
        return list(range(total))
    if count == 1:
        return [0]
    return list(dict.fromkeys(round(i * (total - 1) / (count - 1)) for i in range(count)))


def quick_navigation_indexes(total: int, edge: int = 5, middle: int = 12) -> list[int]:
    indexes = list(range(min(edge, total)))
    indexes.extend(stratified_indexes(total, middle))
    indexes.extend(range(max(0, total - edge), total))
    return sorted(set(indexes))


def chunked_indexes(total: int, batch_size: int) -> list[list[int]]:
    if batch_size <= 0:
        raise ValueError("batch_size must be positive")
    return [list(range(start, min(total, start + batch_size))) for start in range(0, total, batch_size)]


def compact_ranges(indexes: Sequence[int]) -> str:
    values = sorted(set(int(index) + 1 for index in indexes))
    if not values:
        return "尚未核对完整章节"
    ranges: list[str] = []
    start = previous = values[0]
    for value in values[1:]:
        if value == previous + 1:
            previous = value
            continue
        ranges.append(str(start) if start == previous else f"{start}—{previous}")
        start = previous = value
    ranges.append(str(start) if start == previous else f"{start}—{previous}")
    return "第" + "、".join(ranges) + "章"


def merge_batch_plans(plans: Sequence[dict[str, Any]]) -> dict[str, Any]:
    suspects: list[dict[str, Any]] = []
    suspect_seen: set[int] = set()
    entities: dict[str, dict[str, Any]] = {}
    open_threads: list[str] = []
    unknowns: list[str] = []
    for batch_number, plan in enumerate(plans, start=1):
        for item in plan.get("suspect_chapters") or []:
            index = int(item["index"])
            if index in suspect_seen:
                continue
            suspect_seen.add(index)
            suspects.append({**item, "batch": batch_number})
        for item in plan.get("trace_entities") or []:
            name = str(item["name"])
            existing = entities.setdefault(
                name,
                {**item, "batches": [], "mentions": 0, "reasons": []},
            )
            existing["batches"].append(batch_number)
            existing["mentions"] += 1
            reason = str(item.get("reason") or "").strip()
            if reason and reason not in existing["reasons"]:
                existing["reasons"].append(reason)
            if item.get("priority") == "high":
                existing["priority"] = "high"
        open_threads.extend(str(item) for item in plan.get("open_threads") or [])
        unknowns.extend(str(item) for item in plan.get("unknowns") or [])
    return {
        "suspect_chapters": suspects,
        "trace_entities": list(entities.values()),
        "open_threads": list(dict.fromkeys(open_threads)),
        "unknowns": list(dict.fromkeys(unknowns)),
    }


def entity_occurrence_indexes(
    chapters: Sequence[evaluator.Chapter],
    entities: Sequence[dict[str, Any]],
    *,
    per_entity: int = 5,
    max_entities: int = 12,
) -> dict[str, list[int]]:
    """Locate representative chapters for model-selected entities without judging them."""
    prioritized = sorted(
        entities,
        key=lambda item: (
            item.get("priority") != "high",
            -int(item.get("mentions") or 0),
            str(item.get("name") or ""),
        ),
    )[:max_entities]
    result: dict[str, list[int]] = {}
    for item in prioritized:
        name = str(item.get("name") or "").strip()
        if len(name) < 2:
            continue
        hits = [chapter.index for chapter in chapters if name in chapter.content or name in chapter.title]
        if not hits:
            continue
        positions = stratified_indexes(len(hits), min(per_entity, len(hits)))
        result[name] = [hits[position] for position in positions]
    return result


def call_model(
    *,
    api_url: str,
    api_key: str,
    model: str,
    messages: Sequence[dict[str, Any]],
    max_tokens: int,
    thinking: bool,
    effort: str,
    response_json: bool,
    timeout: int,
    stream: bool = False,
) -> tuple[dict[str, Any], dict[str, int]]:
    body: dict[str, Any] = {
        "model": model,
        "messages": list(messages),
        "temperature": 0.2,
        "max_tokens": max_tokens,
    }
    if thinking and effort != "auto":
        body["thinking"] = {"type": "enabled"}
        body["reasoning_effort"] = effort
    if response_json:
        body["response_format"] = {"type": "json_object"}
    if stream:
        body["stream"] = True
        body["stream_options"] = {"include_usage": True}
        response = call_streaming_api_step(
            api_url=api_url,
            api_key=api_key,
            body=body,
            timeout=timeout,
            retries=2,
            retry_sleep=2.0,
        )
    else:
        response = evaluator.call_api_step(
            api_url=api_url,
            api_key=api_key,
            body=body,
            timeout=timeout,
            retries=2,
            retry_sleep=2.0,
        )
    return {"request": body, "response": response}, evaluator.usage_from_response(response)


def call_streaming_api_step(
    *,
    api_url: str,
    api_key: str,
    body: dict[str, Any],
    timeout: int,
    retries: int,
    retry_sleep: float,
) -> dict[str, Any]:
    """Collect an OpenAI-compatible SSE response into a normal chat response."""
    for attempt in range(retries + 1):
        try:
            request = urllib.request.Request(
                api_url,
                data=json.dumps(body, ensure_ascii=False).encode("utf-8"),
                headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
                method="POST",
            )
            content: list[str] = []
            reasoning: list[str] = []
            model = body.get("model")
            finish_reason = None
            usage: dict[str, Any] = {}
            saw_done = False
            with urllib.request.urlopen(request, timeout=timeout) as response:
                for raw_line in response:
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
                    model = event.get("model") or model
                    usage = event.get("usage") or usage
                    choices = event.get("choices") or []
                    if not choices:
                        continue
                    choice = choices[0]
                    finish_reason = choice.get("finish_reason") or finish_reason
                    delta = choice.get("delta") or {}
                    content.append(str(delta.get("content") or ""))
                    reasoning.append(str(delta.get("reasoning_content") or ""))
            if not saw_done and not finish_reason:
                raise http.client.IncompleteRead(b"".join(part.encode("utf-8") for part in content))
            message: dict[str, Any] = {"role": "assistant", "content": "".join(content)}
            if any(reasoning):
                message["reasoning_content"] = "".join(reasoning)
            return {
                "model": model,
                "choices": [{"finish_reason": finish_reason, "message": message}],
                "usage": usage,
            }
        except (
            urllib.error.HTTPError,
            urllib.error.URLError,
            http.client.IncompleteRead,
            http.client.RemoteDisconnected,
            TimeoutError,
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


def planning_messages(
    *,
    name: str,
    author: str,
    synopsis: str,
    status: str,
    snippets: Sequence[dict[str, Any]],
    max_suspects: int,
    scope: str = "全书导航",
    carry_entities: Sequence[str] = (),
) -> list[dict[str, Any]]:
    system = f"""你是 Legado NG 扫书流程的排疑员。宿主已经提供{scope}的目录和每章首尾片段。
本轮只负责导航，不写书评、不判定适读结论。片段不是完整正文，不能据此确认或排除雷点。
找出最多 {max_suspects} 个必须补读完整正文的章节，优先关系背刺、强迫、亲密关系施害、极端虐待、主角长期失权、重大死亡、结局喂屎、身份反转，以及首尾信息之间缺失的关键因果。
同时列出最多 8 个需要跨章节持续追踪的人名或稳定称谓。只列可能牵涉读者风险、感情归宿或重大因果的对象，不要把所有角色都塞进来。carry_entities 是前批留下的追踪对象；本批出现新证据时继续保留，确实无关时不必机械重复。
不要因为题材词或普通口味找茬。返回 JSON 对象：
{{"profile":{{"genre":"","target_audience":"","tone":"","premise":"","ending_hint":""}},"suspect_chapters":[{{"index":0,"priority":"high|medium","reason":""}}],"trace_entities":[{{"name":"明确人名或稳定称谓","priority":"high|medium","reason":"为什么要跨章追踪"}}],"open_threads":["尚未闭合的因果链"],"unknowns":[""]}}
index 必须使用输入中的零基物理索引。"""
    user = json.dumps(
        {
            "book": {"name": name, "author": author, "status": status, "synopsis": synopsis},
            "navigation_only": True,
            "carry_entities": list(carry_entities),
            "chapters": list(snippets),
        },
        ensure_ascii=False,
        separators=(",", ":"),
    )
    return [{"role": "system", "content": system}, {"role": "user", "content": user}]


def raw_chunk_messages(
    *,
    name: str,
    author: str,
    synopsis: str,
    status: str,
    chapters: Sequence[dict[str, Any]],
    max_suspects: int,
    scope: str,
    quick_profile: dict[str, Any] | None = None,
) -> list[dict[str, Any]]:
    system = f"""你是 Legado NG 扫书流程的原文采集员。宿主给你的不是章节首尾片段，而是{scope}的完整章节正文。
本轮只负责从原文里提取事实和疑点，不写最终书评，不直接给适读结论。
先参考 quick_profile 理解作品画像，但不能被它带偏；如果本段原文推翻了快速画像，要直接指出。
重点找这些会影响读者选择的内容：关系背刺、送女、绿帽感/接盘感、强迫或无同意亲密行为、核心角色长期失权、酷刑/永久伤残、亲属/儿童/胎儿伤害、重大死亡、结局喂屎、主角承诺被背刺、长期水文或主线失焦。
后宫、多女主、黑暗、群像、慢热这些普通口味项不要直接算雷；只有它们破坏目标读者期待或触发上面风险时再标记。
每条发现都要写清“发生了什么”和“为什么读者会在意”，不要复述猎奇细节。
返回 JSON 对象，控制长度，禁止 Markdown：
{{"range_summary":"本段一句话概括","risk_findings":[{{"title":"短标题","level":"high|medium|low","type":"relationship|violence|plot|ending|pacing|other","chapters":[1],"evidence":"低细节事实","reader_impact":"读者为什么可能介意"}}],"suspect_chapters":[{{"index":0,"priority":"high|medium","reason":"需要主控重点复核的原因"}}],"trace_entities":[{{"name":"明确人名或稳定称谓","priority":"high|medium","reason":"为什么要跨段追踪"}}],"open_threads":["尚未闭合的因果链"],"unknowns":["本段无法确认的事"]}}
suspect_chapters.index 必须使用输入中的零基物理索引；risk_findings.chapters 使用用户可见的一基章号。最多输出 {max_suspects} 个 suspect_chapters。"""
    user = json.dumps(
        {
            "book": {"name": name, "author": author, "status": status, "synopsis": synopsis},
            "input_is_complete_raw_chapters": True,
            "quick_profile": quick_profile or {},
            "chapters": list(chapters),
        },
        ensure_ascii=False,
        separators=(",", ":"),
    )
    return [{"role": "system", "content": system}, {"role": "user", "content": user}]


def normalize_raw_chunk_plan(
    raw: dict[str, Any],
    *,
    allowed_indexes: set[int],
    total: int,
    max_suspects: int,
) -> dict[str, Any]:
    plan = normalize_plan(raw, total, max_suspects)
    plan["suspect_chapters"] = [
        item for item in plan["suspect_chapters"] if int(item["index"]) in allowed_indexes
    ]
    risk_findings: list[dict[str, Any]] = []
    for item in raw.get("risk_findings") or []:
        if not isinstance(item, dict):
            continue
        title = str(item.get("title") or "").strip()
        evidence = str(item.get("evidence") or "").strip()
        if not title or not evidence:
            continue
        chapters: list[int] = []
        for value in item.get("chapters") or []:
            try:
                chapter_number = int(value)
            except (TypeError, ValueError):
                continue
            index = chapter_number - 1
            if index in allowed_indexes:
                chapters.append(chapter_number)
        risk_findings.append(
            {
                "title": title,
                "level": str(item.get("level") or "medium").strip(),
                "type": str(item.get("type") or "other").strip(),
                "chapters": sorted(set(chapters)),
                "evidence": evidence,
                "reader_impact": str(item.get("reader_impact") or "").strip(),
            }
        )
    plan["range_summary"] = str(raw.get("range_summary") or "").strip()
    plan["risk_findings"] = risk_findings
    return plan


def merge_raw_chunk_plans(plans: Sequence[dict[str, Any]]) -> dict[str, Any]:
    merged = merge_batch_plans(plans)
    risk_findings: list[dict[str, Any]] = []
    seen: set[tuple[str, tuple[int, ...]]] = set()
    range_summaries: list[str] = []
    for batch_number, plan in enumerate(plans, start=1):
        summary = str(plan.get("range_summary") or "").strip()
        if summary:
            range_summaries.append(f"批次{batch_number}: {summary}")
        for item in plan.get("risk_findings") or []:
            key = (
                str(item.get("title") or ""),
                tuple(int(value) for value in item.get("chapters") or []),
            )
            if key in seen:
                continue
            seen.add(key)
            risk_findings.append({**item, "batch": batch_number})
    merged["risk_findings"] = risk_findings
    merged["range_summaries"] = range_summaries
    return merged


def consolidation_messages(
    *,
    name: str,
    total: int,
    merged_plan: dict[str, Any],
    candidates: Sequence[dict[str, Any]],
    entity_occurrences: dict[str, list[int]],
    max_suspects: int,
) -> list[dict[str, Any]]:
    system = f"""你是 Legado NG 长篇扫书的总排疑员。前面的分批导航只看了章节标题和首尾片段，宿主现在汇总了可疑章节、跨章人物线和候选片段。
你的任务是选择最多 {max_suspects} 个最值得补读完整正文的章节，用来还原“前因—行为—影响—归宿”，尤其避免只读事件爆发章却漏掉关系建立或最终后果。
不要写报告，不要把片段当成已确认事实，不要因为普通题材口味找茬。只返回一个尽量短的 JSON：
{{"selected":[{{"index":0,"priority":"high|medium","reason":"不超过30字"}}]}}
index 必须来自 candidate_chapters。不要重复人物表、作品简介、开放问题或分析过程，整个 JSON 控制在 3000 字符以内。"""
    compact_findings = {
        "suspects": [
            {key: item.get(key) for key in ("index", "priority", "reason", "batch")}
            for item in merged_plan.get("suspect_chapters") or []
        ],
        "traced_entities": [
            {
                "name": item.get("name"),
                "priority": item.get("priority"),
                "reasons": list(item.get("reasons") or [])[:2],
            }
            for item in merged_plan.get("trace_entities") or []
        ],
        "open_threads": list(merged_plan.get("open_threads") or [])[:24],
    }
    user = json.dumps(
        {
            "book": {"name": name, "total_chapters": total},
            "batch_findings": compact_findings,
            "entity_occurrences": entity_occurrences,
            "candidate_chapters": list(candidates),
        },
        ensure_ascii=False,
        separators=(",", ":"),
    )
    return [{"role": "system", "content": system}, {"role": "user", "content": user}]


def normalize_consolidation(
    raw: dict[str, Any], total: int, max_suspects: int
) -> dict[str, Any]:
    selected: list[dict[str, Any]] = []
    seen: set[int] = set()
    for item in raw.get("selected") or raw.get("suspect_chapters") or []:
        if not isinstance(item, dict):
            continue
        try:
            index = int(item.get("index"))
        except (TypeError, ValueError):
            continue
        if index < 0 or index >= total or index in seen:
            continue
        seen.add(index)
        selected.append(
            {
                "index": index,
                "chapter": index + 1,
                "priority": str(item.get("priority") or "medium"),
                "reason": str(item.get("reason") or "补齐关键因果").strip(),
            }
        )
        if len(selected) >= max_suspects:
            break
    return {"suspect_chapters": selected}


def report_messages(
    *,
    name: str,
    author: str,
    status: str,
    total: int,
    synopsis: str,
    plan: dict[str, Any],
    full_chapters: Sequence[dict[str, Any]],
    report_type: str = "quick_scan_report",
    report_style: str = "structured",
) -> list[dict[str, Any]]:
    if report_style == "bold":
        style_rules = """你是 Legado NG 的 AI 扫书助手。你站在读者这边，帮用户快速判断这本书值不值得继续看。
你的判断可以有态度。好看就说好看，踩雷就说踩雷，适合小众读者就直接说适合谁。用户要的是能省时间的选书判断，不是端水式总结。
分析时先看这本书承诺给谁看：后宫文按后宫读者的标准扫，武侠群像按武侠群像读者的标准扫，虐文也要分清是情绪拉扯还是突破底线的施害。
先用目标读者视角判断，不替本来不会点进来的读者挑毛病。后宫文的家庭压力、商战黑手、阶段性受挫、擦边密度，只要没破坏后院安稳、主角爽感、关系归宿这些核心承诺，就写成压力来源或受众提醒，不要直接把 try 拉成 cautious。
判断适读结论时，也要代入普通读者读到这些情节时的即时反应：是觉得虐得有劲、后面复仇或情绪释放撑得住，还是会觉得作者在拿猎奇伤害恶心人。如果后者明显更强，就直接不推荐，不要用“有特定受众”把它磨成谨慎试读。
作品一边写极端伤害，一边又替施害者开脱、洗白或让受害者原谅时，要按读者的憋屈感和反感来判断，不要只看剧情设定是否自洽。
输出尽量短、准、像人话。少剧透，不复述猎奇过程，不替角色或作者开脱。"""
    else:
        style_rules = """你是 Legado NG 内置的 AI 扫书助理，站在读者选书的角度判断作品是否值得继续看。
报告要短、自然、低剧透，不写论文，不揣摩作者意图，不替角色开脱，也不为了完整硬找毛病。作品定位和作品速览只说题材前提、阅读气质和结构，不泄露身份反转、具体死法、复仇步骤或最终去向。
"""
    raw_worker_mode = bool((plan or {}).get("raw_chunk_findings"))
    evidence_rule = (
        "raw_chunk_findings 来自并行 worker 直接阅读完整原文后的事实包，可以作为已核对样本使用；full_chapters 是主控抽查/补充阅读的原文。"
        if raw_worker_mode
        else "只有 full_chapters 中的完整正文可以确认风险。navigation_plan 只能帮助理解题材和指出未知，不能用于声称风险已排除。"
    )
    system = f"""{style_rules}
先识别作品明牌受众，再判断目标受众内部是否踩雷。后宫、多女主、黑暗、慢热、悲剧、群像等普通口味只用于说明受众，不自动算雷；明牌黑暗作品有伤亡也不能直接判劝退，要看它是否越过该类读者通常预期，或是否背刺作品自己的核心承诺。
极端酷刑、胎儿/儿童/核心亲属严重伤害、亲密关系施害、强迫、背叛、送女、绿帽、长期失权仍要如实列出。但 verdict 不能按标签数量机械决定：目标受众大概率仍能接受时用 try；存在显著代价或关键未知时用 cautious；只有已确认内容连目标受众通常也难以接受，或作品明确背叛自身卖点时才用 reject。持续、猎奇、以羞辱和去人格化为主要体验的极端施害，即使作品明牌为虐文，也可以判 reject。
{evidence_rule}
风险说明只概括伤害类型和读者影响，不复述猎奇过程。没有完整核对全部章节时，风险正文禁止出现“全文确认”“全书确认”或“未见其他风险”，应写“已核对样本确认”。作品仍在连载时，只能写“当前进展/最新样本”，不得称为结局、收尾、最终命运或已确认烂尾。
verdict 只能是 try、cautious、reject、uncertain。输出只能包含一个 legado-book-report 代码块，不要输出交互或普通正文：
```legado-book-report
<book-report type="{report_type}">
  <verdict>cautious</verdict><headline>一句读者判断</headline>
  <basic><book>书名</book><author>作者</author><status>状态</status><word-count>篇幅</word-count><category>分类</category><positioning>一句话定位</positioning></basic>
  <overview><item title="分类流派">...</item><item title="核心元素">...</item><item title="整体风格">...</item><item title="世界设定">...</item><item title="叙事结构">...</item></overview>
  <audience><item>...</item></audience>
  <reading-feeling><pressure index="1-5" label="等级">必须写一句压力来源和释放情况</pressure><item title="整体感受">...</item></reading-feeling>
  <risk level="high|medium"><title>...</title><text>低细节依据</text></risk>
  <unknown>仍未确认的内容</unknown>
</book-report>
```"""
    user = json.dumps(
        {
            "book": {
                "name": name,
                "author": author,
                "status": status,
                "total_chapters": total,
                "synopsis": synopsis,
            },
            "evidence_scope": {
                "navigation_is_snippet_only": not raw_worker_mode,
                "raw_worker_findings_are_complete_text": raw_worker_mode,
                "complete_chapter_indexes": [int(item["index"]) for item in full_chapters],
                "complete_chapter_count": len(full_chapters),
                "whole_book_complete": len(full_chapters) >= total,
                "confirmed_fact_wording": (
                    "并行 worker 原文事实包可写已核对样本确认；只有 raw/full 覆盖全书时才可写全文确认"
                    if raw_worker_mode
                    else "只有完整章节可写已核对样本确认，禁止写全文确认"
                ),
            },
            "navigation_plan": plan,
            "full_chapters": list(full_chapters),
        },
        ensure_ascii=False,
        separators=(",", ":"),
    )
    return [{"role": "system", "content": system}, {"role": "user", "content": user}]


def host_finalize_report(
    content: str,
    full_indexes: Sequence[int],
    total: int,
    metadata: dict[str, str] | None = None,
    navigation_indexes: Sequence[int] | None = None,
    raw_full_indexes: Sequence[int] | None = None,
    interaction_id: str = "book_scan_reaction",
) -> str:
    report = lab.parse_report(content)
    if not report or not report.get("valid_xml"):
        raise ValueError(f"invalid report: {(report or {}).get('error', 'missing block')}")
    root = ET.fromstring(str(report["raw"]))
    basic = root.find("basic")
    if basic is None:
        basic = ET.SubElement(root, "basic")
    for tag, value in (metadata or {}).items():
        node = basic.find(tag)
        if node is None:
            node = ET.SubElement(basic, tag)
        node.text = value
    sampled = basic.find("sampled")
    if sampled is None:
        sampled = ET.SubElement(basic, "sampled")
    if raw_full_indexes is not None:
        raw_values = sorted(set(int(index) for index in raw_full_indexes))
        if len(raw_values) >= total:
            coverage_text = f"并行原文核对全书{total}章"
        else:
            coverage_text = f"并行原文核对{compact_ranges(raw_values)}"
        sampled.text = f"{coverage_text}；主控补读{compact_ranges(full_indexes)}"
    else:
        navigated = sorted(set(navigation_indexes if navigation_indexes is not None else range(total)))
        if len(navigated) >= total:
            coverage_text = f"全书{total}章均已快速浏览首尾"
        else:
            coverage_text = f"已快速浏览{len(navigated)}/{total}章的首尾片段"
        sampled.text = f"{coverage_text}；完整核对{compact_ranges(full_indexes)}"
    xml = ET.tostring(root, encoding="unicode")
    return (
        f"```legado-book-report\n{xml}\n```\n\n"
        f"```legado-interaction\n<interaction id=\"{interaction_id}\" />\n```"
    )


def report_checks(content: str, full_indexes: Sequence[int], total: int) -> dict[str, Any]:
    failures: list[str] = []
    warnings: list[str] = []
    report = lab.parse_report(content)
    if not report or not report.get("valid_xml"):
        failures.append(f"invalid report XML: {(report or {}).get('error', 'missing report')}")
        return {"passed": False, "failures": failures, "warnings": warnings}
    if len(full_indexes) < total and ("全文确认" in content or "全书确认" in content):
        failures.append("report claims whole-book confirmation without complete text coverage")
    root = ET.fromstring(str(report["raw"]))
    pressure = root.find(".//pressure")
    if pressure is None or not "".join(pressure.itertext()).strip():
        failures.append("pressure node has no explanatory text")
    return {"passed": not failures, "failures": failures, "warnings": warnings}


def usage_total(*items: dict[str, int]) -> dict[str, int]:
    keys = {key for item in items for key in item}
    return {key: sum(int(item.get(key) or 0) for item in items) for key in keys}


def model_content(call: dict[str, Any]) -> str:
    return str(call["response"]["choices"][0]["message"].get("content") or "")


def write_call(out: Path, label: str, call: dict[str, Any]) -> None:
    evaluator.write_json(out / f"{label}_request.json", call["request"])
    evaluator.write_json(out / f"{label}_response.json", call["response"])


def append_attempt(out: Path, label: str, call: dict[str, Any], usage: dict[str, int]) -> None:
    choice = (call.get("response", {}).get("choices") or [{}])[0]
    record = {
        "label": label,
        "timestamp": int(time.time()),
        "finish_reason": choice.get("finish_reason"),
        "usage": usage,
    }
    with (out / "attempts.jsonl").open("a", encoding="utf-8") as handle:
        handle.write(json.dumps(record, ensure_ascii=False, separators=(",", ":")) + "\n")


def read_attempts(path: Path) -> list[dict[str, Any]]:
    if not path.exists():
        return []
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def candidate_indexes(
    total: int,
    merged_plan: dict[str, Any],
    occurrences: dict[str, list[int]],
    baseline: int = 5,
) -> list[int]:
    indexes = list(range(min(baseline, total)))
    indexes.extend(range(max(0, total - baseline), total))
    indexes.extend(int(item["index"]) for item in merged_plan.get("suspect_chapters") or [])
    for values in occurrences.values():
        indexes.extend(values)
    return sorted(set(index for index in indexes if 0 <= index < total))


def final_full_text_indexes(
    total: int,
    consolidated: dict[str, Any],
    merged_plan: dict[str, Any],
    baseline: int = 5,
) -> list[int]:
    indexes = list(range(min(baseline, total)))
    indexes.extend(range(max(0, total - baseline), total))
    indexes.extend(int(item["index"]) for item in consolidated.get("suspect_chapters") or [])
    indexes.extend(
        int(item["index"])
        for item in merged_plan.get("suspect_chapters") or []
        if item.get("priority") == "high"
    )
    return sorted(set(index for index in indexes if 0 <= index < total))


def verify_reopen_state(path: Path) -> dict[str, Any]:
    state = json.loads(path.read_text(encoding="utf-8"))
    required = {"book", "coverage", "quick_report", "final_report", "interactions"}
    missing = sorted(required - set(state))
    if missing:
        raise ValueError(f"saved state missing fields: {', '.join(missing)}")
    if not lab.parse_report(str(state["final_report"])):
        raise ValueError("saved final report cannot be reopened")
    return state


def sha256_text(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def run_full_flow(
    *,
    args: argparse.Namespace,
    api_key: str,
    book_path: Path,
    text: str,
    name: str,
    author: str,
    synopsis: str,
    status: str,
    chapters: Sequence[evaluator.Chapter],
    out: Path,
    started: float,
) -> int:
    total = len(chapters)
    all_snippets = navigation_pack(chapters, args.head_chars, args.tail_chars)
    usages: list[dict[str, int]] = []
    calls = 0
    invoke_lock = threading.Lock()

    def invoke(
        label: str,
        messages: Sequence[dict[str, Any]],
        *,
        response_json: bool,
        use_cache: bool = True,
    ) -> dict[str, Any]:
        nonlocal calls
        cached_request = out / f"{label}_request.json"
        cached_response = out / f"{label}_response.json"
        if use_cache and cached_request.exists() and cached_response.exists():
            call = {
                "request": evaluator.read_json(cached_request),
                "response": evaluator.read_json(cached_response),
            }
            with invoke_lock:
                usages.append(evaluator.usage_from_response(call["response"]))
                calls += 1
            print(f"[resume] {label}", flush=True)
            return call
        call, usage = call_model(
            api_url=args.api_url,
            api_key=api_key,
            model=args.model,
            messages=messages,
            max_tokens=args.planning_max_tokens if response_json else args.report_max_tokens,
            thinking=args.thinking,
            effort=args.planning_reasoning_effort if response_json else args.reasoning_effort,
            response_json=response_json,
            timeout=args.timeout,
            stream=args.stream,
        )
        write_call(out, label, call)
        with invoke_lock:
            append_attempt(out, label, call, usage)
            usages.append(usage)
            calls += 1
        return call

    def decode_plan(
        label: str,
        messages: Sequence[dict[str, Any]],
        call: dict[str, Any],
        max_suspects: int,
    ) -> dict[str, Any]:
        try:
            raw = parse_json_object(model_content(call))
        except (json.JSONDecodeError, ValueError):
            print(f"[retry] {label} returned incomplete JSON", flush=True)
            (out / f"{label}_request.json").unlink(missing_ok=True)
            (out / f"{label}_response.json").unlink(missing_ok=True)
            call = invoke(label, messages, response_json=True)
            raw = parse_json_object(model_content(call))
        return normalize_plan(raw, total, max_suspects)

    def decode_consolidation(
        label: str,
        messages: Sequence[dict[str, Any]],
        call: dict[str, Any],
    ) -> dict[str, Any]:
        try:
            raw = parse_json_object(model_content(call))
        except (json.JSONDecodeError, ValueError):
            print(f"[retry] {label} returned incomplete JSON", flush=True)
            (out / f"{label}_request.json").unlink(missing_ok=True)
            (out / f"{label}_response.json").unlink(missing_ok=True)
            call = invoke(label, messages, response_json=True)
            raw = parse_json_object(model_content(call))
        return normalize_consolidation(raw, total, args.consolidation_max_suspects)

    print(f"[quick] navigation for {total} chapters", flush=True)
    quick_nav = quick_navigation_indexes(total, args.quick_edge, args.quick_middle)
    quick_messages = planning_messages(
        name=name,
        author=author,
        synopsis=synopsis,
        status=status,
        snippets=[all_snippets[index] for index in quick_nav],
        max_suspects=args.max_suspects,
        scope=f"快速定位抽取的 {len(quick_nav)}/{total} 章",
    )
    quick_call = invoke(
        "quick_navigation",
        quick_messages,
        response_json=True,
    )
    quick_plan = decode_plan("quick_navigation", quick_messages, quick_call, args.max_suspects)
    evaluator.write_json(out / "quick_navigation_plan.json", quick_plan)
    quick_full = full_text_indexes(total, quick_plan, baseline=3)
    quick_report_call = invoke(
        "quick_report",
        report_messages(
            name=name,
            author=author,
            status=status,
            total=total,
            synopsis=synopsis,
            plan=quick_plan,
            full_chapters=[chapters[index].to_payload(include_content=True) for index in quick_full],
            report_style=args.report_style,
        ),
        response_json=False,
    )
    content_chars = sum(chapter.chars for chapter in chapters)
    word_count = f"约{content_chars / 10000:.1f}万字" if content_chars >= 10000 else f"约{content_chars}字"
    metadata = {"book": name, "author": author or "未知", "status": status, "word-count": word_count}
    quick_report = host_finalize_report(
        model_content(quick_report_call),
        quick_full,
        total,
        metadata=metadata,
        navigation_indexes=quick_nav,
    )
    (out / "quick_output.md").write_text(quick_report, encoding="utf-8")

    interactions = [
        {"id": "book_scan_reaction", "selection": "continue_scan"},
        {"id": "book_scan_like_reasons", "selection": "skip"},
        {"id": "book_scan_target", "selection": "scan_all_remaining"},
    ]
    print("[continue] user selected continue_scan -> scan_all_remaining", flush=True)

    batch_plans: list[dict[str, Any]] = []
    carry_entities = [str(item["name"]) for item in quick_plan.get("trace_entities") or []]
    batches = chunked_indexes(total, args.batch_size)
    raw_chunk_indexes: list[int] = []

    def run_navigation_batch(number: int, indexes: list[int], seed_entities: Sequence[str]) -> dict[str, Any]:
        print(f"[continue] batch {number}/{len(batches)} chapters {indexes[0] + 1}-{indexes[-1] + 1}", flush=True)
        batch_messages = planning_messages(
            name=name,
            author=author,
            synopsis=synopsis,
            status=status,
            snippets=[all_snippets[index] for index in indexes],
            max_suspects=args.batch_max_suspects,
            scope=f"第 {indexes[0] + 1}—{indexes[-1] + 1} 章",
            carry_entities=seed_entities,
        )
        batch_call = invoke(
            f"batch_{number:02d}_navigation",
            batch_messages,
            response_json=True,
        )
        plan = decode_plan(
            f"batch_{number:02d}_navigation",
            batch_messages,
            batch_call,
            args.batch_max_suspects,
        )
        allowed = set(indexes)
        plan["suspect_chapters"] = [
            item for item in plan["suspect_chapters"] if int(item["index"]) in allowed
        ]
        plan["range"] = [indexes[0], indexes[-1]]
        evaluator.write_json(out / f"batch_{number:02d}_plan.json", plan)
        return plan

    def run_raw_chunk_batch(number: int, indexes: list[int]) -> dict[str, Any]:
        print(
            f"[continue] raw batch {number}/{len(batches)} chapters "
            f"{indexes[0] + 1}-{indexes[-1] + 1}",
            flush=True,
        )
        batch_messages = raw_chunk_messages(
            name=name,
            author=author,
            synopsis=synopsis,
            status=status,
            chapters=[chapters[index].to_payload(include_content=True) for index in indexes],
            max_suspects=args.batch_max_suspects,
            scope=f"第 {indexes[0] + 1}—{indexes[-1] + 1} 章",
            quick_profile=quick_plan.get("profile") or {},
        )
        label = f"batch_{number:02d}_raw_chunk"
        batch_call = invoke(label, batch_messages, response_json=True)
        try:
            raw = parse_json_object(model_content(batch_call))
        except (json.JSONDecodeError, ValueError):
            print(f"[retry] {label} returned incomplete JSON", flush=True)
            (out / f"{label}_request.json").unlink(missing_ok=True)
            (out / f"{label}_response.json").unlink(missing_ok=True)
            batch_call = invoke(label, batch_messages, response_json=True)
            raw = parse_json_object(model_content(batch_call))
        plan = normalize_raw_chunk_plan(
            raw,
            allowed_indexes=set(indexes),
            total=total,
            max_suspects=args.batch_max_suspects,
        )
        plan["range"] = [indexes[0], indexes[-1]]
        evaluator.write_json(out / f"batch_{number:02d}_raw_plan.json", plan)
        return plan

    if args.parallel_mode == "raw-chunks":
        print(
            f"[continue] running {len(batches)} raw chapter chunks with "
            f"{args.parallel_workers} parallel workers",
            flush=True,
        )
        if args.parallel_workers > 1:
            results: dict[int, dict[str, Any]] = {}
            with ThreadPoolExecutor(max_workers=args.parallel_workers) as executor:
                futures = {
                    executor.submit(run_raw_chunk_batch, number, indexes): number
                    for number, indexes in enumerate(batches, start=1)
                }
                for future in as_completed(futures):
                    number = futures[future]
                    results[number] = future.result()
                    print(f"[continue] raw batch {number}/{len(batches)} done", flush=True)
            batch_plans = [results[number] for number in sorted(results)]
        else:
            for number, indexes in enumerate(batches, start=1):
                batch_plans.append(run_raw_chunk_batch(number, indexes))

        merged = merge_raw_chunk_plans([quick_plan, *batch_plans])
        raw_chunk_indexes = sorted({index for batch in batches for index in batch})
        occurrences = entity_occurrence_indexes(
            chapters,
            merged["trace_entities"],
            per_entity=args.entity_samples,
            max_entities=args.max_trace_entities,
        )
        consolidated = {
            "suspect_chapters": [
                {key: item.get(key) for key in ("index", "chapter", "priority", "reason", "batch")}
                for item in merged.get("suspect_chapters", [])
            ][: args.consolidation_max_suspects]
        }
        evaluator.write_json(out / "merged_raw_chunks.json", merged)
        evaluator.write_json(out / "entity_occurrences.json", occurrences)
        evaluator.write_json(out / "deep_consolidation_plan.json", consolidated)
        print(
            f"[continue] merged {len(merged.get('risk_findings') or [])} raw findings "
            f"from {len(batch_plans)} chunks",
            flush=True,
        )
    elif args.parallel_workers > 1:
        print(
            f"[continue] running {len(batches)} navigation batches with "
            f"{args.parallel_workers} parallel workers",
            flush=True,
        )
        seed_entities = carry_entities[-args.max_trace_entities :]
        results: dict[int, dict[str, Any]] = {}
        with ThreadPoolExecutor(max_workers=args.parallel_workers) as executor:
            futures = {
                executor.submit(run_navigation_batch, number, indexes, seed_entities): number
                for number, indexes in enumerate(batches, start=1)
            }
            for future in as_completed(futures):
                number = futures[future]
                results[number] = future.result()
                print(f"[continue] batch {number}/{len(batches)} done", flush=True)
        batch_plans = [results[number] for number in sorted(results)]
    else:
        for number, indexes in enumerate(batches, start=1):
            plan = run_navigation_batch(number, indexes, carry_entities)
            batch_plans.append(plan)
            carry_entities = list(
                dict.fromkeys(
                    [*carry_entities, *(str(item["name"]) for item in plan.get("trace_entities") or [])]
                )
            )[-args.max_trace_entities :]

    if args.parallel_mode == "navigation":
        merged = merge_batch_plans([quick_plan, *batch_plans])
        occurrences = entity_occurrence_indexes(
            chapters,
            merged["trace_entities"],
            per_entity=args.entity_samples,
            max_entities=args.max_trace_entities,
        )
        candidates = candidate_indexes(total, merged, occurrences)
        candidate_snippets = [all_snippets[index] for index in candidates]
        evaluator.write_json(out / "merged_navigation.json", merged)
        evaluator.write_json(out / "entity_occurrences.json", occurrences)
        print(
            f"[continue] consolidating {len(candidates)} candidates from "
            f"{len(merged['trace_entities'])} traced entities",
            flush=True,
        )
        consolidate_messages = consolidation_messages(
            name=name,
            total=total,
            merged_plan=merged,
            candidates=candidate_snippets,
            entity_occurrences=occurrences,
            max_suspects=args.consolidation_max_suspects,
        )
        consolidate_call = invoke(
            "deep_consolidation",
            consolidate_messages,
            response_json=True,
        )
        consolidated = decode_consolidation(
            "deep_consolidation",
            consolidate_messages,
            consolidate_call,
        )
        allowed_candidates = set(candidates)
        consolidated["suspect_chapters"] = [
            item for item in consolidated["suspect_chapters"] if int(item["index"]) in allowed_candidates
        ]
        evaluator.write_json(out / "deep_consolidation_plan.json", consolidated)

    deep_full = final_full_text_indexes(total, consolidated, merged, baseline=5)
    complete_indexes = sorted(set([*quick_full, *deep_full]))
    report_plan = {
        "quick_profile": quick_plan.get("profile") or {},
        "batch_findings": merged,
        "consolidated": consolidated,
        "entity_occurrences": occurrences,
    }
    if args.parallel_mode == "raw-chunks":
        report_plan["raw_chunk_findings"] = {
            "covered_ranges": compact_ranges(raw_chunk_indexes),
            "range_summaries": merged.get("range_summaries") or [],
            "risk_findings": merged.get("risk_findings") or [],
            "open_threads": merged.get("open_threads") or [],
            "unknowns": merged.get("unknowns") or [],
        }
    print(f"[report] reading {len(complete_indexes)} complete chapters", flush=True)
    final_report_call = invoke(
        "final_report",
        report_messages(
            name=name,
            author=author,
            status=status,
            total=total,
            synopsis=synopsis,
            plan=report_plan,
            full_chapters=[chapters[index].to_payload(include_content=True) for index in complete_indexes],
            report_type="deep_scan_report",
            report_style=args.report_style,
        ),
        response_json=False,
        use_cache=not args.refresh_final_report,
    )
    final_report = host_finalize_report(
        model_content(final_report_call),
        complete_indexes,
        total,
        metadata=metadata,
        navigation_indexes=range(total) if args.parallel_mode == "navigation" else quick_nav,
        raw_full_indexes=raw_chunk_indexes if args.parallel_mode == "raw-chunks" else None,
    )
    (out / "final_output.md").write_text(final_report, encoding="utf-8")

    state = {
        "version": 1,
        "book": {
            "path": str(book_path),
            "name": name,
            "author": author,
            "chapters": total,
            "content_sha256": sha256_text(text),
        },
        "model": args.model,
        "interactions": interactions,
        "coverage": {
            "navigation_indexes": list(range(total)) if args.parallel_mode == "navigation" else quick_nav,
            "raw_chunk_indexes": raw_chunk_indexes,
            "raw_chunk_ranges": compact_ranges(raw_chunk_indexes) if raw_chunk_indexes else "",
            "complete_indexes": complete_indexes,
            "complete_ranges": compact_ranges(complete_indexes),
        },
        "quick_plan": quick_plan,
        "deep_plan": consolidated,
        "trace_entities": merged["trace_entities"],
        "entity_occurrences": occurrences,
        "quick_report": quick_report,
        "final_report": final_report,
    }
    state_path = out / "state.json"
    evaluator.write_json(state_path, state)
    reopened = verify_reopen_state(state_path)

    confirmed_indexes = raw_chunk_indexes if args.parallel_mode == "raw-chunks" else complete_indexes
    checks = report_checks(final_report, confirmed_indexes, total)
    quick_checks = report_checks(quick_report, quick_full, total)
    checks["failures"] = [*quick_checks["failures"], *checks["failures"]]
    checks["warnings"] = [*quick_checks["warnings"], *checks["warnings"]]
    checks["passed"] = not checks["failures"]
    usage = usage_total(*usages)
    attempts = read_attempts(out / "attempts.jsonl")
    actual_usage = usage_total(*(item.get("usage") or {} for item in attempts)) if attempts else usage
    run = {
        "scenario_name": args.name,
        "book": state["book"],
        "model": args.model,
        "transport": "stream" if args.stream else "non_stream",
        "reasoning": {"enabled": args.thinking, "effort": args.reasoning_effort},
        "parallel_workers": args.parallel_workers,
        "parallel_mode": args.parallel_mode,
        "calls": calls,
        "usage": usage,
        "actual_attempt_calls": len(attempts) if attempts else calls,
        "actual_attempt_usage": actual_usage,
        "elapsed_seconds": round(time.time() - started, 1),
        "navigation_count": total if args.parallel_mode == "navigation" else len(quick_nav),
        "quick_navigation_count": len(quick_nav),
        "raw_chunk_chapter_count": len(raw_chunk_indexes),
        "raw_chunk_ranges": compact_ranges(raw_chunk_indexes) if raw_chunk_indexes else "",
        "complete_read_indexes": complete_indexes,
        "complete_read_ranges": compact_ranges(complete_indexes),
        "trace_entities": [item["name"] for item in merged["trace_entities"]],
        "suspect_chapters": consolidated["suspect_chapters"],
        "quick_report": lab.parse_report(quick_report),
        "final_report": lab.parse_report(final_report),
        "reopen_verified": reopened["final_report"] == final_report,
        "checks": checks,
    }
    evaluator.write_json(out / "summary.json", run)
    html_run = {
        "scenario_name": args.name,
        "model": args.model,
        "usage": usage,
        "turns": [
            {"turn": 1, "content": quick_report, "report": run["quick_report"]},
            {"turn": 2, "content": final_report, "report": run["final_report"]},
        ],
        "tool_trace": [],
        "checks": {
            "passed": checks["passed"],
            "failures": checks["failures"],
            "warnings": [*checks["warnings"], "实验性宿主编排流程，不是正式 Skill"],
        },
    }
    lab.write_html(html_run, out / "report.html")
    print(json.dumps(run, ensure_ascii=False, indent=2), flush=True)
    print(f"report: {out / 'report.html'}", flush=True)
    return 0 if checks["passed"] and run["reopen_verified"] else 1


def parse_args(argv: Sequence[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--book", type=Path, required=True)
    parser.add_argument("--model", default="deepseek-v4-pro")
    parser.add_argument("--api-url", default="https://api.deepseek.com/chat/completions")
    parser.add_argument("--api-key-env", default="DEEPSEEK_API_KEY")
    parser.add_argument("--name", default="two-stage")
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    parser.add_argument("--max-suspects", type=int, default=12)
    parser.add_argument("--planning-max-tokens", type=int, default=8192)
    parser.add_argument("--report-max-tokens", type=int, default=8192)
    parser.add_argument("--report-style", choices=["structured", "bold"], default="structured")
    parser.add_argument("--head-chars", type=int, default=200)
    parser.add_argument("--tail-chars", type=int, default=200)
    parser.add_argument("--full-flow", action="store_true")
    parser.add_argument(
        "--quick-profile-only",
        action="store_true",
        help="非 full-flow 时只使用快速画像范围，不把全书 snippet 交给导航阶段",
    )
    parser.add_argument("--quick-edge", type=int, default=5)
    parser.add_argument("--quick-middle", type=int, default=12)
    parser.add_argument("--batch-size", type=int, default=60)
    parser.add_argument(
        "--parallel-workers",
        type=int,
        default=1,
        help="Full-flow batch concurrency. Keep 1 for serial runs; use 2-3 for subagent-style local experiments.",
    )
    parser.add_argument(
        "--parallel-mode",
        choices=["navigation", "raw-chunks"],
        default="navigation",
        help=(
            "Full-flow parallel strategy. navigation keeps the old chapter head/tail batch mode; "
            "raw-chunks sends complete raw chapter ranges to workers."
        ),
    )
    parser.add_argument("--batch-max-suspects", type=int, default=5)
    parser.add_argument("--consolidation-max-suspects", type=int, default=24)
    parser.add_argument("--entity-samples", type=int, default=5)
    parser.add_argument("--max-trace-entities", type=int, default=12)
    parser.add_argument("--refresh-final-report", action="store_true")
    parser.add_argument("--thinking", action=argparse.BooleanOptionalAction, default=True)
    parser.add_argument("--reasoning-effort", default="high")
    parser.add_argument("--planning-reasoning-effort", default="medium")
    parser.add_argument(
        "--stream",
        action=argparse.BooleanOptionalAction,
        default=True,
        help="Use streaming responses for real model calls (default: enabled)",
    )
    parser.add_argument("--timeout", type=int, default=600)
    parser.add_argument("--reset", action="store_true")
    return parser.parse_args(argv)


def main(argv: Sequence[str] | None = None) -> int:
    args = parse_args(argv)
    if args.parallel_workers < 1:
        raise ValueError("--parallel-workers must be >= 1")
    evaluator.load_dotenv(evaluator.ROOT / ".env")
    api_key = os.environ.get(args.api_key_env, "")
    if not api_key:
        raise ValueError(f"environment variable {args.api_key_env} is empty")
    book_path = args.book.resolve()
    text = evaluator.read_text(book_path)
    preamble, chapters = evaluator.split_chapters(text)
    name, author, synopsis = evaluator.infer_book_metadata(book_path, preamble)
    status = evaluator.infer_book_status("auto", chapters, preamble)
    out = args.out.resolve() / args.name
    if args.reset and out.exists():
        import shutil
        shutil.rmtree(out)
    out.mkdir(parents=True, exist_ok=True)

    started = time.time()
    if args.full_flow:
        return run_full_flow(
            args=args,
            api_key=api_key,
            book_path=book_path,
            text=text,
            name=name,
            author=author,
            synopsis=synopsis,
            status=status,
            chapters=chapters,
            out=out,
            started=started,
        )

    if args.quick_profile_only:
        navigation_indexes = quick_navigation_indexes(len(chapters), args.quick_edge, args.quick_middle)
        navigation_chapters = [chapters[index] for index in navigation_indexes]
    else:
        navigation_indexes = list(range(len(chapters)))
        navigation_chapters = chapters
    snippets = navigation_pack(navigation_chapters, args.head_chars, args.tail_chars)
    first, usage1 = call_model(
        api_url=args.api_url,
        api_key=api_key,
        model=args.model,
        messages=planning_messages(
            name=name,
            author=author,
            synopsis=synopsis,
            status=status,
            snippets=snippets,
            max_suspects=args.max_suspects,
            scope=(
                f"快速画像抽取的 {len(snippets)}/{len(chapters)} 章"
                if args.quick_profile_only
                else f"全书 {len(chapters)} 章首尾片段导航"
            ),
        ),
        max_tokens=args.planning_max_tokens,
        thinking=args.thinking,
        effort=args.reasoning_effort,
        response_json=True,
        timeout=args.timeout,
        stream=args.stream,
    )
    evaluator.write_json(out / "stage1_request.json", first["request"])
    evaluator.write_json(out / "stage1_response.json", first["response"])
    stage1_content = str(first["response"]["choices"][0]["message"].get("content") or "")
    plan = normalize_plan(parse_json_object(stage1_content), len(chapters), args.max_suspects)
    evaluator.write_json(out / "navigation_plan.json", plan)

    indexes = full_text_indexes(len(chapters), plan)
    full_chapters = [chapters[index].to_payload(include_content=True) for index in indexes]
    second, usage2 = call_model(
        api_url=args.api_url,
        api_key=api_key,
        model=args.model,
        messages=report_messages(
            name=name,
            author=author,
            status=status,
            total=len(chapters),
            synopsis=synopsis,
            plan=plan,
            full_chapters=full_chapters,
            report_style=args.report_style,
        ),
        max_tokens=args.report_max_tokens,
        thinking=args.thinking,
        effort=args.reasoning_effort,
        response_json=False,
        timeout=args.timeout,
        stream=args.stream,
    )
    evaluator.write_json(out / "stage2_request.json", second["request"])
    evaluator.write_json(out / "stage2_response.json", second["response"])
    stage2_content = str(second["response"]["choices"][0]["message"].get("content") or "")
    content_chars = sum(chapter.chars for chapter in chapters)
    word_count = (
        f"约{content_chars / 10000:.1f}万字" if content_chars >= 10000 else f"约{content_chars}字"
    )
    final_content = host_finalize_report(
        stage2_content,
        indexes,
        len(chapters),
        metadata={
            "book": name,
            "author": author or "未知",
            "status": status,
            "word-count": word_count,
        },
        navigation_indexes=navigation_indexes,
    )
    (out / "final_output.md").write_text(final_content, encoding="utf-8")

    usage = {key: usage1[key] + usage2[key] for key in usage1}
    checks = report_checks(final_content, indexes, len(chapters))
    run = {
        "scenario_name": args.name,
        "book": {"path": str(book_path), "name": name, "author": author, "chapters": len(chapters)},
        "model": args.model,
        "transport": "stream" if args.stream else "non_stream",
        "reasoning": {"enabled": args.thinking, "effort": args.reasoning_effort},
        "calls": 2,
        "usage": usage,
        "elapsed_seconds": round(time.time() - started, 1),
        "navigation_count": len(snippets),
        "quick_profile_only": bool(args.quick_profile_only),
        "complete_read_indexes": indexes,
        "suspect_chapters": plan["suspect_chapters"],
        "final_report": lab.parse_report(final_content),
        "checks": checks,
    }
    evaluator.write_json(out / "summary.json", run)
    html_run = {
        "scenario_name": args.name,
        "model": args.model,
        "usage": usage,
        "turns": [{"turn": 1, "content": final_content, "report": run["final_report"]}],
        "tool_trace": [],
        "checks": {
            "passed": checks["passed"],
            "failures": checks["failures"],
            "warnings": [*checks["warnings"], "实验性两阶段流程，不是正式 Skill"],
        },
    }
    lab.write_html(html_run, out / "report.html")
    print(json.dumps(run, ensure_ascii=False, indent=2))
    print(f"report: {out / 'report.html'}")
    return 0 if checks["passed"] else 1


if __name__ == "__main__":
    sys.exit(main())
