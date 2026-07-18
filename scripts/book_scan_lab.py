#!/usr/bin/env python3
"""Headless end-to-end lab for the built-in BookScan Agent flow.

The lab executes the real Skill package with a real OpenAI-compatible model,
but replaces Android MCP, AgentMemory storage and host interaction rendering
with deterministic local adapters.  It is intended for fast exploratory
testing; it does not claim that the Android renderer or lifecycle works.
"""

from __future__ import annotations

import argparse
import html
import json
import os
import re
import shutil
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any, Sequence

SCRIPT_ROOT = Path(__file__).resolve().parents[1]
if str(SCRIPT_ROOT) not in sys.path:
    sys.path.insert(0, str(SCRIPT_ROOT))

from scripts import book_scan_eval as evaluator


DEFAULT_API_URL = "https://api.deepseek.com/chat/completions"
DEFAULT_MODEL = "deepseek-v4-flash"
DEFAULT_KEY_ENV = "DEEPSEEK_API_KEY"
DEFAULT_OUT = evaluator.ROOT / "build/book_scan_lab"

REPORT_BLOCK_RE = re.compile(
    r"(?is)```\s*legado-book-report[^\r\n]*\r?\n(.*?)\r?\n```"
)
INTERACTION_BLOCK_RE = re.compile(
    r"(?is)```\s*legado-interaction[^\r\n]*\r?\n(.*?)\r?\n```"
)
XML_INTERACTION_RE = re.compile(
    r"(?is)<interaction\b([^>]*)/?>"
)
XML_ATTR_RE = re.compile(r"([A-Za-z_][\w.-]*)\s*=\s*(['\"])(.*?)\2", re.S)


def load_scenario(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError("scenario root must be a JSON object")
    if int(value.get("schema_version") or 0) != 1:
        raise ValueError("scenario schema_version must be 1")
    flow = value.get("flow", [])
    if not isinstance(flow, list) or not all(isinstance(item, dict) for item in flow):
        raise ValueError("scenario flow must be an array of objects")
    return value


def resolve_book_path(raw: str, scenario_path: Path) -> Path:
    candidate = Path(raw).expanduser()
    if candidate.is_absolute():
        return candidate
    repo_candidate = evaluator.ROOT / candidate
    if repo_candidate.is_file():
        return repo_candidate
    return scenario_path.parent / candidate


def load_policy(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    interactions = value.get("interactions") if isinstance(value, dict) else None
    if not isinstance(interactions, dict):
        raise ValueError(f"invalid interaction policy: {path}")
    return value


def parse_interaction(content: str) -> dict[str, Any] | None:
    blocks = INTERACTION_BLOCK_RE.findall(content or "")
    candidates = blocks or [content or ""]
    for candidate in reversed(candidates):
        xml_match = XML_INTERACTION_RE.search(candidate)
        if xml_match:
            attributes = {
                key: value.strip()
                for key, _, value in XML_ATTR_RE.findall(xml_match.group(1))
            }
            interaction_id = attributes.get("id", "").strip()
            if interaction_id:
                item_ids = (
                    attributes.get("item-ids")
                    or attributes.get("item_ids")
                    or attributes.get("items")
                    or ""
                )
                return {
                    "id": interaction_id,
                    "item_ids": [item.strip() for item in item_ids.split(",") if item.strip()],
                    "source": "xml",
                }
        try:
            payload = json.loads(candidate.strip())
        except json.JSONDecodeError:
            continue
        if isinstance(payload, dict) and str(payload.get("id") or "").strip():
            raw_items = payload.get("item_ids") or payload.get("items") or []
            if isinstance(raw_items, str):
                raw_items = raw_items.split(",")
            return {
                "id": str(payload["id"]).strip(),
                "item_ids": [str(item).strip() for item in raw_items if str(item).strip()],
                "source": "json",
            }
    return None


def hydrate_interaction(reference: dict[str, Any], policy: dict[str, Any]) -> dict[str, Any]:
    interaction_id = str(reference.get("id") or "")
    definition = (policy.get("interactions") or {}).get(interaction_id)
    if not isinstance(definition, dict):
        raise ValueError(f"unknown interaction id: {interaction_id}")
    item_ids = reference.get("item_ids") or []
    allowed = definition.get("allowed_items") or {}
    unknown = [item_id for item_id in item_ids if item_id not in allowed]
    if unknown:
        raise ValueError(f"interaction {interaction_id} contains unknown item ids: {unknown}")
    result = dict(definition)
    result["id"] = interaction_id
    result["item_ids"] = list(item_ids)
    return result


def _fill_template(template: str, values: dict[str, str]) -> str:
    result = template
    for key, value in values.items():
        result = result.replace("{{" + key + "}}", value)
    return result


def interaction_prompt(
    interaction: dict[str, Any], action: dict[str, Any]
) -> tuple[str, dict[str, Any]]:
    interaction_id = interaction["id"]
    expected = str(action.get("interaction") or "")
    if expected and interaction_id != expected:
        raise ValueError(f"expected interaction {expected}, got {interaction_id}")
    mode = str(action.get("action") or "select")
    if mode == "skip":
        skip = interaction.get("skip")
        if not isinstance(skip, dict) or not str(skip.get("prompt_template") or ""):
            raise ValueError(f"interaction {interaction_id} does not support skip")
        return str(skip["prompt_template"]), {"action": "skip", "interaction": interaction_id}

    requested = action.get("select")
    requested_values = requested if isinstance(requested, list) else [requested]
    requested_values = [str(value) for value in requested_values if value is not None]
    allowed_items = interaction.get("allowed_items") or {}
    options = interaction.get("options") or []
    option_map = {
        str(option.get("value")): option
        for option in options
        if isinstance(option, dict) and option.get("value") is not None
    }
    labels: list[str] = []
    for value in requested_values:
        if value in option_map:
            labels.append(str(option_map[value].get("label") or value))
        elif value in allowed_items:
            labels.append(str((allowed_items[value] or {}).get("label") or value))
        else:
            raise ValueError(f"value {value} is not allowed by interaction {interaction_id}")
    submit = interaction.get("submit") or {}
    template = str(submit.get("prompt_template") or "")
    if not template:
        raise ValueError(f"interaction {interaction_id} has no submit prompt")
    rendered = _fill_template(
        template,
        {
            "value": requested_values[0] if requested_values else "",
            "values": ",".join(requested_values),
            "label": labels[0] if labels else "",
            "labels": "、".join(labels),
        },
    )
    return rendered, {
        "action": "select",
        "interaction": interaction_id,
        "values": requested_values,
        "labels": labels,
    }


def parse_report(content: str) -> dict[str, Any] | None:
    matches = REPORT_BLOCK_RE.findall(content or "")
    if not matches:
        return None
    raw = matches[-1].strip()
    result: dict[str, Any] = {"raw": raw, "valid_xml": False}
    try:
        root = ET.fromstring(raw)
    except ET.ParseError as error:
        result["error"] = str(error)
        return result
    result["valid_xml"] = True
    result["verdict"] = (root.findtext(".//verdict") or root.get("verdict") or "").strip()
    result["headline"] = (root.findtext(".//headline") or "").strip()
    result["risk_titles"] = [
        (node.findtext("title") or node.get("title") or node.get("label") or "").strip()
        for node in root.findall(".//risk")
    ]
    result["unknowns"] = [
        " ".join("".join(node.itertext()).split()) for node in root.findall(".//unknown")
    ]
    return result


def build_messages(
    package: evaluator.SkillPackage,
    companions: Sequence[evaluator.SkillPackage],
    *,
    name: str,
    author: str,
    work_key: str,
    status: str,
    total: int,
    synopsis: str,
    request: str,
) -> list[dict[str, Any]]:
    catalog = [package, *companions]
    skills = "\n".join(
        "  <skill>"
        f"<name>{item.metadata.get('id') or item.name}</name>"
        f"<description>{item.metadata.get('description', '')}</description>"
        "</skill>"
        for item in catalog
    )
    system = (
        "你是 Legado NG 的 AI 助理。当前是无界面的扫书实验室：Skill、模型和多轮交互是真实的，"
        "MCP、AgentMemory 和宿主交互由本地确定性适配器模拟；这不代表 Android UI 已通过。\n\n"
        "当前启用了以下 Skill。任务匹配时先调用 use_skill 加载入口，随后只按已加载内容中的链接读取资源。\n"
        f"<available_skills>\n{skills}\n</available_skills>\n\n"
        "[AI_ACTIVE_SKILL]\n"
        f"id: {package.metadata.get('id') or package.name}\n"
        f"name: {package.name}\n"
        f"version: {package.metadata.get('version', '')}\n"
        f"package_hash: {package.package_hash}\n"
        "instructions: call use_skill before executing the task\n"
    )
    user = (
        "当前书籍上下文：\n"
        f"- 书名：{name}\n- 作者：{author or '未知'}\n"
        "- work_key 是 XML 标签里的原始多行文本，调用工具时必须原样传递：\n"
        f"<work_key>{work_key}</work_key>\n"
        f"- 状态：{status}\n- 物理章节总数：{total}\n- 简介：{synopsis}\n\n"
        f"用户请求：{request}"
    )
    return [{"role": "system", "content": system}, {"role": "user", "content": user}]


def run_turn(
    *,
    run: dict[str, Any],
    simulator: evaluator.LocalMcpSimulator,
    run_path: Path,
    raw_dir: Path,
    api_url: str,
    api_key: str,
    model: str,
    temperature: float,
    max_tokens: int,
    thinking: bool,
    reasoning_effort: str,
    stream: bool,
    timeout: int,
    max_steps: int,
) -> dict[str, Any]:
    turn_number = int(run.get("turn") or 0) + 1
    run["turn"] = turn_number
    usage_before = dict(run.get("usage") or {})
    tools = evaluator.openai_tools(include_use_skill=True)
    for step in range(1, max_steps + 1):
        evaluator.execute_pending_tool_calls(run=run, simulator=simulator, run_path=run_path)
        body = evaluator.build_request_body(
            model=model,
            messages=run["messages"],
            tools=tools,
            temperature=temperature,
            max_tokens=max_tokens,
        )
        if thinking:
            body["thinking"] = {"type": "enabled"}
            body["reasoning_effort"] = reasoning_effort or "high"
        if stream:
            body["stream"] = True
            body["stream_options"] = {"include_usage": True}
        artifact = raw_dir / f"turn_{turn_number:02d}_step_{step:02d}"
        evaluator.write_json(artifact.with_suffix(".request.json"), body)
        response = evaluator.call_api_step(
            api_url=api_url,
            api_key=api_key,
            body=body,
            timeout=timeout,
            retries=2,
            retry_sleep=2.0,
        )
        evaluator.write_json(artifact.with_suffix(".response.json"), response)
        usage = evaluator.usage_from_response(response)
        evaluator.add_usage(run.setdefault("usage", {}), usage)
        message = dict(response["choices"][0]["message"])
        run["messages"].append(message)
        run["step"] = int(run.get("step") or 0) + 1
        run["updated_at"] = int(time.time())
        evaluator.write_json(run_path, run)
        if not message.get("tool_calls"):
            content = str(message.get("content") or "")
            usage_after = run.get("usage") or {}
            turn_usage = {
                key: int(usage_after.get(key) or 0) - int(usage_before.get(key) or 0)
                for key in ("prompt_tokens", "completion_tokens", "total_tokens")
            }
            turn_result = {
                "turn": turn_number,
                "content": content,
                "interaction": parse_interaction(content),
                "report": parse_report(content),
                "usage": turn_usage,
            }
            run.setdefault("turns", []).append(turn_result)
            evaluator.write_json(run_path, run)
            return turn_result
    raise RuntimeError(f"turn {turn_number} exceeded max_steps={max_steps}")


def current_book_memories(simulator: evaluator.LocalMcpSimulator) -> list[dict[str, Any]]:
    return [
        memory
        for memory in simulator.memories
        if memory.get("scope_type") == "book"
        and memory.get("scope_key") == simulator.work_key
        and memory.get("domain") == "book_scan"
        and memory.get("status", "active") == "active"
    ]


def evaluate_expectations(run: dict[str, Any], expectations: dict[str, Any]) -> dict[str, Any]:
    failures: list[str] = list(run.get("runtime_failures") or [])
    warnings: list[str] = []
    reports = [turn.get("report") for turn in run.get("turns", []) if turn.get("report")]
    last_report = reports[-1] if reports else None
    visible = "\n".join(str(turn.get("content") or "") for turn in run.get("turns", []))
    verdicts = set(str(value) for value in expectations.get("verdict_in", []))
    if verdicts and (not last_report or last_report.get("verdict") not in verdicts):
        failures.append(f"final verdict is not in {sorted(verdicts)}")
    for term in expectations.get("required_terms", []):
        if str(term) not in visible:
            failures.append(f"missing required term: {term}")
    for group in expectations.get("required_terms_any", []):
        if isinstance(group, list) and not any(str(term) in visible for term in group):
            failures.append(f"none of required terms found: {group}")
    for term in expectations.get("forbidden_terms", []):
        if str(term) in visible:
            failures.append(f"forbidden visible term: {term}")
    max_calls = expectations.get("max_tool_calls")
    if max_calls is not None and len(run.get("tool_trace", [])) > int(max_calls):
        failures.append(f"tool calls exceed {max_calls}")
    max_total = expectations.get("max_total_tokens")
    total_tokens = int((run.get("usage") or {}).get("total_tokens") or 0)
    if max_total is not None and total_tokens > int(max_total):
        failures.append(f"total tokens exceed {max_total}")
    if expectations.get("must_write_book_memory", True) and not run.get("book_memory_count"):
        failures.append("no active book_scan AgentMemory was written")
    for report in reports:
        if not report.get("valid_xml"):
            failures.append(f"invalid report XML: {report.get('error', 'unknown error')}")
    for turn in run.get("turns", []):
        if not turn.get("report"):
            continue
        remainder = REPORT_BLOCK_RE.sub("", str(turn.get("content") or ""))
        remainder = INTERACTION_BLOCK_RE.sub("", remainder).strip()
        if remainder:
            failures.append(f"turn {turn.get('turn')} contains prose outside protocol blocks")
    coverage = run.get("coverage") or {}
    full_count = len(coverage.get("complete_read_indexes") or [])
    total_chapters = int((run.get("book") or {}).get("chapters") or 0)
    final_content = str((run.get("turns") or [{}])[-1].get("content") or "")
    full_claim = re.search(
        r"(?:全书|全文).{0,18}(?:全部确认|完整确认|已经确认|已全部|未见额外隐藏雷点)",
        final_content,
        re.S,
    )
    if full_claim and full_count < total_chapters:
        failures.append(
            f"full-book claim exceeds complete text evidence: {full_count}/{total_chapters} chapters"
        )
    if "完整正文" in final_content and full_count == 0:
        failures.append("report claims complete text coverage but no complete chapter was read")
    if not reports:
        warnings.append("no legado-book-report block was produced")
    return {"passed": not failures, "failures": failures, "warnings": warnings}


def write_transcript(run: dict[str, Any], path: Path) -> None:
    chunks = [f"# {run['scenario_name']}", ""]
    for turn in run.get("turns", []):
        chunks.extend([f"## Turn {turn['turn']}", "", str(turn.get("content") or ""), ""])
    path.write_text("\n".join(chunks), encoding="utf-8")


def write_html(run: dict[str, Any], path: Path) -> None:
    checks = run.get("checks") or {}
    status = "通过" if checks.get("passed") else "未通过"
    cards = []
    for turn in run.get("turns", []):
        content = html.escape(str(turn.get("content") or ""))
        cards.append(f"<section><h2>Turn {turn['turn']}</h2><pre>{content}</pre></section>")
    tools = "".join(
        f"<li>{html.escape(str(item.get('tool_name')))}</li>" for item in run.get("tool_trace", [])
    )
    failures = "".join(f"<li>{html.escape(item)}</li>" for item in checks.get("failures", []))
    document = f"""<!doctype html><html lang=\"zh-CN\"><meta charset=\"utf-8\">
<title>{html.escape(run['scenario_name'])}</title>
<style>body{{font:16px/1.6 system-ui;max-width:1100px;margin:2rem auto;padding:0 1rem;background:#f7f5f2;color:#252321}}section{{background:white;border:1px solid #ddd4cb;border-radius:14px;padding:1rem 1.25rem;margin:1rem 0}}pre{{white-space:pre-wrap;word-break:break-word}}.ok{{color:#28743b}}.bad{{color:#ad2d22}}code{{background:#eee8e2;padding:.1rem .3rem}}</style>
<body><h1>{html.escape(run['scenario_name'])}</h1>
<p class=\"{'ok' if checks.get('passed') else 'bad'}\">检查结果：{status}</p>
<p>模型：<code>{html.escape(run['model'])}</code>；总 token：{int(run.get('usage', {}).get('total_tokens', 0))}；工具调用：{len(run.get('tool_trace', []))}</p>
<section><h2>失败项</h2><ul>{failures or '<li>无</li>'}</ul></section>
{''.join(cards)}<section><h2>工具序列</h2><ol>{tools}</ol></section>
<p>这是无界面模拟结果，不代表 Android UI、数据库 Hook 或生命周期已验收。</p></body></html>"""
    path.write_text(document, encoding="utf-8")


def run_once(args: argparse.Namespace, scenario: dict[str, Any], scenario_path: Path, index: int) -> dict[str, Any]:
    package = evaluator.load_skill_package(evaluator.DEFAULT_SKILL_PACKAGE)
    companions = evaluator.discover_companion_skill_packages(package)
    policy = load_policy(package.root / "interaction-policy.json")
    raw_book = args.book or scenario.get("book")
    if not raw_book:
        raise ValueError("book is required in scenario or --book")
    book_path = resolve_book_path(str(raw_book), scenario_path).resolve()
    text = evaluator.read_text(book_path)
    preamble, chapters = evaluator.split_chapters(text)
    if not chapters:
        raise ValueError(f"no chapters detected: {book_path}")
    name, author, synopsis = evaluator.infer_book_metadata(book_path, preamble)
    status = evaluator.infer_book_status(str(scenario.get("book_status") or "auto"), chapters, preamble)
    scenario_name = str(args.name or scenario.get("name") or scenario_path.stem)
    run_dir = Path(args.out).resolve() / scenario_name / f"run_{index:03d}"
    if args.reset and run_dir.exists():
        shutil.rmtree(run_dir)
    run_dir.mkdir(parents=True, exist_ok=True)
    raw_dir = run_dir / "raw"
    raw_dir.mkdir(exist_ok=True)
    run_path = run_dir / "run.json"
    model = args.model or scenario.get("model") or DEFAULT_MODEL
    api_url = args.api_url or scenario.get("api_url") or DEFAULT_API_URL
    thinking = bool(args.thinking or scenario.get("thinking"))
    reasoning_effort = str(
        args.reasoning_effort or scenario.get("reasoning_effort") or "high"
    )
    simulator = evaluator.LocalMcpSimulator(
        book_path=book_path,
        name=name,
        author=author,
        synopsis=synopsis,
        book_status=status,
        chapters=chapters,
        skill_revision=str(package.metadata.get("version") or ""),
        state_dir=run_dir / "state",
        conversation_id=f"lab-{scenario_name}-{index}",
        skill_package=package,
        additional_skill_packages=companions,
    )
    request = str(scenario.get("initial_request") or "进入 AI 扫书专用模式，按入口上下文开始当前书快速定位。")
    run: dict[str, Any] = {
        "schema_version": 1,
        "scenario_name": scenario_name,
        "book": {"path": str(book_path), "name": name, "author": author, "chapters": len(chapters)},
        "model": model,
        "reasoning": {
            "enabled": thinking,
            "effort": reasoning_effort if thinking else None,
        },
        "transport": "stream" if args.stream else "non_stream",
        "skill": {"version": package.metadata.get("version"), "hash": package.package_hash},
        "messages": build_messages(
            package,
            companions,
            name=name,
            author=author,
            work_key=simulator.work_key,
            status=status,
            total=len(chapters),
            synopsis=synopsis,
            request=request,
        ),
        "turns": [],
        "tool_trace": [],
        "interactions": [],
        "usage": {"prompt_tokens": 0, "completion_tokens": 0, "total_tokens": 0},
        "started_at": int(time.time()),
    }
    evaluator.write_json(run_path, run)
    if args.dry_run:
        run["dry_run"] = True
        run["book_memory_count"] = 0
        run["checks"] = {"passed": True, "failures": [], "warnings": ["dry-run: model was not called"]}
    else:
        key_env = args.api_key_env or scenario.get("api_key_env") or DEFAULT_KEY_ENV
        api_key = os.environ.get(str(key_env), "")
        if not api_key:
            raise ValueError(f"environment variable {key_env} is empty")
        turn = run_turn(
            run=run,
            simulator=simulator,
            run_path=run_path,
            raw_dir=raw_dir,
            api_url=str(api_url),
            api_key=api_key,
            model=str(model),
            temperature=float(args.temperature),
            max_tokens=int(args.max_tokens),
            thinking=thinking,
            reasoning_effort=reasoning_effort,
            stream=bool(args.stream),
            timeout=int(args.timeout),
            max_steps=int(args.max_steps_per_turn),
        )
        for action in scenario.get("flow", []):
            reference = turn.get("interaction")
            if not reference:
                raise RuntimeError(f"turn {turn['turn']} did not return an interaction")
            try:
                interaction = hydrate_interaction(reference, policy)
            except ValueError as error:
                run.setdefault("runtime_failures", []).append(str(error))
                if not scenario.get("recover_protocol_errors"):
                    raise
                expected_id = str(action.get("interaction") or "")
                interaction = hydrate_interaction(
                    {"id": expected_id, "item_ids": []}, policy
                )
                run.setdefault("recoveries", []).append(
                    {
                        "turn": turn.get("turn"),
                        "error": str(error),
                        "recovered_as": expected_id,
                        "note": "实验室为继续探索而恢复；真实 App 会拒绝该协议。",
                    }
                )
            prompt, selection = interaction_prompt(interaction, action)
            run["interactions"].append(selection)
            run["messages"].append({"role": "user", "content": prompt})
            turn = run_turn(
                run=run,
                simulator=simulator,
                run_path=run_path,
                raw_dir=raw_dir,
                api_url=str(api_url),
                api_key=api_key,
                model=str(model),
                temperature=float(args.temperature),
                max_tokens=int(args.max_tokens),
                thinking=thinking,
                reasoning_effort=reasoning_effort,
                stream=bool(args.stream),
                timeout=int(args.timeout),
                max_steps=int(args.max_steps_per_turn),
            )
        memories = current_book_memories(simulator)
        run["book_memory_count"] = len(memories)
        run["coverage"] = evaluator.evidence_coverage(simulator.receipts)
        run["reopen_memory_found"] = bool(memories)
        if scenario.get("reopen"):
            run["reopen"] = {
                "found": bool(memories),
                "message": "已找到本书扫书档案" if memories else "未找到本书扫书档案",
            }
        run["checks"] = evaluate_expectations(run, scenario.get("expectations") or {})
    run["finished_at"] = int(time.time())
    evaluator.write_json(run_path, run)
    evaluator.write_json(run_dir / "summary.json", {
        key: run[key]
        for key in ("scenario_name", "book", "model", "skill", "usage", "book_memory_count", "reopen_memory_found", "checks")
        if key in run
    })
    write_transcript(run, run_dir / "transcript.md")
    write_html(run, run_dir / "report.html")
    return run


def parse_args(argv: Sequence[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--scenario", type=Path, required=True)
    parser.add_argument("--name", help="Override the output run name")
    parser.add_argument("--book")
    parser.add_argument("--model")
    parser.add_argument("--api-url")
    parser.add_argument("--api-key-env")
    parser.add_argument("--repeat", type=int)
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    parser.add_argument("--temperature", type=float, default=0.2)
    parser.add_argument("--max-tokens", type=int, default=8192)
    parser.add_argument(
        "--stream",
        action=argparse.BooleanOptionalAction,
        default=True,
        help="Use streaming responses for real model calls (default: enabled)",
    )
    parser.add_argument("--thinking", action="store_true", help="Send thinking.type=enabled")
    parser.add_argument(
        "--reasoning-effort",
        choices=("low", "medium", "high", "max", "xhigh"),
        help="Reasoning effort sent when thinking is enabled (default: high)",
    )
    parser.add_argument("--timeout", type=int, default=600)
    parser.add_argument("--max-steps-per-turn", type=int, default=32)
    parser.add_argument("--reset", action="store_true")
    parser.add_argument("--dry-run", action="store_true")
    return parser.parse_args(argv)


def main(argv: Sequence[str] | None = None) -> int:
    args = parse_args(argv)
    evaluator.load_dotenv(evaluator.ROOT / ".env")
    scenario_path = args.scenario.resolve()
    scenario = load_scenario(scenario_path)
    repeat = args.repeat or int(scenario.get("repeat") or 1)
    results = []
    for index in range(1, repeat + 1):
        print(f"[{index}/{repeat}] {scenario.get('name') or scenario_path.stem}", flush=True)
        results.append(run_once(args, scenario, scenario_path, index))
    passed = all(bool((run.get("checks") or {}).get("passed")) for run in results)
    for index, run in enumerate(results, start=1):
        report_path = Path(args.out).resolve() / run["scenario_name"] / f"run_{index:03d}" / "report.html"
        print(f"report: {report_path}")
        print(json.dumps(run.get("checks"), ensure_ascii=False))
    return 0 if passed else 1


if __name__ == "__main__":
    sys.exit(main())
