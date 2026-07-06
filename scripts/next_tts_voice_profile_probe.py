#!/usr/bin/env python3
"""
Generate sample audio for the built-in Next Edge TTS template and create a
draft voice-profile table for later role binding.
"""

from __future__ import annotations

import argparse
import csv
import datetime as dt
import json
import math
import re
import shutil
import subprocess
import sys
import time
import urllib.parse
import urllib.request
from dataclasses import dataclass
from pathlib import Path

import numpy as np


REPO_ROOT = Path(__file__).resolve().parents[1]
NEXT_TEMPLATE = REPO_ROOT / "app/src/main/assets/defaultData/tts/next_edge_proxy.js"
DEFAULT_TEXT = "前不见古人，后不见来者。念天地之悠悠，独怆然而涕下。"
EDGE_VOICE_LIST_URL = (
    "https://speech.platform.bing.com/consumer/speech/synthesize/readaloud/voices/list"
    "?trustedclienttoken=6A5AA1D4EAFF4E9FB37E23D68491D6F4"
)

AZURE_VOICE_METADATA: dict[str, dict[str, list[str]]] = {
    "zh-CN-XiaoxiaoNeural": {
        "styles": ["affectionate", "angry", "assistant", "calm", "chat", "chat-casual", "cheerful", "customerservice", "disgruntled", "excited", "fearful", "friendly", "gentle", "lyrical", "newscast", "poetry-reading", "sad", "serious", "sorry", "whispering"],
        "roles": [],
    },
    "zh-CN-YunxiNeural": {
        "styles": ["angry", "assistant", "chat", "cheerful", "depressed", "disgruntled", "embarrassed", "fearful", "narration-relaxed", "newscast", "sad", "serious"],
        "roles": ["Boy", "Narrator", "YoungAdultMale"],
    },
    "zh-CN-YunjianNeural": {
        "styles": ["angry", "cheerful", "depressed", "disgruntled", "documentary-narration", "narration-relaxed", "sad", "serious", "sports-commentary", "sports-commentary-excited"],
        "roles": [],
    },
    "zh-CN-XiaoyiNeural": {
        "styles": ["affectionate", "angry", "cheerful", "disgruntled", "embarrassed", "fearful", "gentle", "sad", "serious"],
        "roles": [],
    },
    "zh-CN-YunyangNeural": {
        "styles": ["customerservice", "narration-professional", "newscast-casual"],
        "roles": [],
    },
    "zh-CN-XiaochenNeural": {"styles": ["livecommercial"], "roles": []},
    "zh-CN-XiaohanNeural": {
        "styles": ["affectionate", "angry", "calm", "cheerful", "disgruntled", "embarrassed", "fearful", "gentle", "sad", "serious"],
        "roles": [],
    },
    "zh-CN-XiaomengNeural": {"styles": ["chat"], "roles": []},
    "zh-CN-XiaomoNeural": {
        "styles": ["affectionate", "angry", "calm", "cheerful", "depressed", "disgruntled", "embarrassed", "envious", "fearful", "gentle", "sad", "serious"],
        "roles": ["Boy", "Girl", "OlderAdultFemale", "OlderAdultMale", "SeniorFemale", "SeniorMale", "YoungAdultFemale", "YoungAdultMale"],
    },
    "zh-CN-XiaoruiNeural": {"styles": ["angry", "calm", "fearful", "sad"], "roles": []},
    "zh-CN-XiaoshuangNeural": {"styles": ["chat"], "roles": []},
    "zh-CN-XiaoxiaoMultilingualNeural": {
        "styles": ["affectionate", "cheerful", "empathetic", "excited", "poetry-reading", "sorry", "story"],
        "roles": [],
    },
    "zh-CN-XiaozhenNeural": {
        "styles": ["angry", "cheerful", "disgruntled", "fearful", "sad", "serious"],
        "roles": [],
    },
    "zh-CN-YunfengNeural": {
        "styles": ["angry", "cheerful", "depressed", "disgruntled", "fearful", "sad", "serious"],
        "roles": [],
    },
    "zh-CN-YunhaoNeural": {"styles": ["advertisement-upbeat"], "roles": []},
    "zh-CN-YunxiaNeural": {"styles": ["angry", "calm", "cheerful", "fearful", "sad"], "roles": []},
    "zh-CN-YunyeNeural": {
        "styles": ["angry", "calm", "cheerful", "disgruntled", "embarrassed", "fearful", "sad", "serious"],
        "roles": ["Boy", "Girl", "OlderAdultFemale", "OlderAdultMale", "SeniorFemale", "SeniorMale", "YoungAdultFemale", "YoungAdultMale"],
    },
    "zh-CN-YunzeNeural": {
        "styles": ["angry", "calm", "cheerful", "depressed", "disgruntled", "documentary-narration", "fearful", "sad", "serious"],
        "roles": ["OlderAdultMale", "SeniorMale"],
    },
    "zh-CN-Xiaochen:DragonHDFlashLatestNeural": {
        "styles": ["cheerful", "debating", "empathetic", "live-commercial", "poetry-reading", "sad", "sorry"],
        "roles": [],
    },
    "zh-CN-Xiaoxiao:DragonHDFlashLatestNeural": {
        "styles": ["angry", "chat", "cheerful", "customer-service", "excited", "fearful", "sad", "voice-assistant"],
        "roles": [],
    },
    "zh-CN-Xiaoxiao2:DragonHDFlashLatestNeural": {
        "styles": ["affectionate", "angry", "anxious", "cheerful", "curious", "disappointed", "empathetic", "encouraging", "excited", "fearful", "guilty", "lonely", "poetry-reading", "sad", "sentimental", "sorry", "story", "surprised", "tired", "whispering"],
        "roles": [],
    },
    "zh-CN-Yunxiao:DragonHDFlashLatestNeural": {"styles": [], "roles": []},
    "zh-CN-Yunye:DragonHDFlashLatestNeural": {"styles": [], "roles": []},
    "zh-CN-Yunyi:DragonHDFlashLatestNeural": {
        "styles": ["assassin", "captain", "cavalier", "game-narrator", "geomancer", "poet", "prince"],
        "roles": [],
    },
}


@dataclass
class Voice:
    name: str
    voice_id: str
    gender: str
    hd: bool = False


@dataclass
class Analysis:
    duration_s: float | None = None
    f0_median_hz: float | None = None
    f0_mean_hz: float | None = None
    f0_coverage: float | None = None
    rms_db: float | None = None
    centroid_hz: float | None = None
    error: str | None = None


def parse_template(path: Path) -> tuple[list[str], list[Voice], str]:
    script = path.read_text(encoding="utf-8")
    sample = re.search(r"^\s*//\s*@sampleText\s+(.+)$", script, re.M)
    sample_text = sample.group(1).strip() if sample else DEFAULT_TEXT
    apis = re.findall(r'value:\s*"([^"]+/tts)"', script)
    voices = parse_voice_objects(script)
    if not apis:
        raise SystemExit(f"No API options found in {path}")
    if not voices:
        raise SystemExit(f"No voices found in {path}")
    return apis, voices, sample_text


def parse_voice_objects(script: str) -> list[Voice]:
    array_start = script.find("var VOICES")
    if array_start < 0:
        return []
    bracket_start = script.find("[", array_start)
    if bracket_start < 0:
        return []
    blocks: list[str] = []
    in_string = False
    escaped = False
    array_depth = 0
    brace_depth = 0
    object_start: int | None = None
    for index in range(bracket_start, len(script)):
        char = script[index]
        if in_string:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                in_string = False
            continue
        if char == '"':
            in_string = True
            continue
        if char == "[":
            array_depth += 1
            continue
        if char == "]":
            array_depth -= 1
            if array_depth == 0:
                break
            continue
        if char == "{" and array_depth == 1:
            if brace_depth == 0:
                object_start = index
            brace_depth += 1
            continue
        if char == "}" and array_depth == 1 and brace_depth:
            brace_depth -= 1
            if brace_depth == 0 and object_start is not None:
                blocks.append(script[object_start : index + 1])
                object_start = None
    voices: list[Voice] = []
    for block in blocks:
        name = extract_js_string(block, "name")
        voice_id = extract_js_string(block, "id")
        gender = extract_js_string(block, "gender")
        if name and voice_id and gender:
            voices.append(
                Voice(
                    name=name,
                    voice_id=voice_id,
                    gender=gender,
                    hd=bool(re.search(r"\bhd\s*:\s*true\b", block)),
                )
            )
    return voices


def extract_js_string(block: str, key: str) -> str | None:
    match = re.search(rf'\b{re.escape(key)}\s*:\s*"([^"]*)"', block)
    return match.group(1) if match else None


def synthesize(api: str, voice: Voice, text: str, timeout: float) -> bytes:
    query = urllib.parse.urlencode(
        {
            "t": text,
            "v": voice.voice_id,
            "r": "0",
            "p": "0",
            "s": "",
            "api_key": "",
        }
    )
    request = urllib.request.Request(f"{api.rstrip('/')}?{query}", method="GET")
    with urllib.request.urlopen(request, timeout=timeout) as response:
        content_type = response.headers.get("Content-Type", "")
        data = response.read()
    if not content_type.split(";")[0].lower().startswith("audio/"):
        raise RuntimeError(f"not audio response: {content_type}, {data[:120]!r}")
    if not data:
        raise RuntimeError("empty audio response")
    return data


def fetch_edge_voice_tags(timeout: float) -> dict[str, dict[str, list[str]]]:
    try:
        with urllib.request.urlopen(EDGE_VOICE_LIST_URL, timeout=timeout) as response:
            data = json.loads(response.read().decode("utf-8"))
    except Exception as exc:  # noqa: BLE001 - diagnostic data should not block synthesis
        print(f"warning: failed to fetch Edge voice list: {exc}", file=sys.stderr)
        return {}
    result: dict[str, dict[str, list[str]]] = {}
    for item in data:
        short_name = item.get("ShortName")
        if not short_name:
            continue
        tag = item.get("VoiceTag") or {}
        result[short_name] = {
            "categories": [str(value).strip() for value in tag.get("ContentCategories") or [] if str(value).strip()],
            "personalities": [str(value).strip() for value in tag.get("VoicePersonalities") or [] if str(value).strip()],
            "locale": [str(item.get("Locale") or "")],
            "gender": [str(item.get("Gender") or "")],
        }
    return result


def decode_audio(audio_path: Path, ffmpeg: str | None) -> tuple[np.ndarray, int]:
    if ffmpeg:
        cmd = [
            ffmpeg,
            "-v",
            "error",
            "-i",
            str(audio_path),
            "-f",
            "s16le",
            "-acodec",
            "pcm_s16le",
            "-ac",
            "1",
            "-ar",
            "16000",
            "-",
        ]
        raw = subprocess.check_output(cmd)
        return np.frombuffer(raw, dtype=np.int16).astype(np.float32) / 32768.0, 16000
    try:
        import miniaudio
    except ImportError as exc:
        raise RuntimeError("ffmpeg not found and miniaudio is not installed") from exc
    decoded = miniaudio.decode_file(str(audio_path), nchannels=1, sample_rate=16000)
    samples = np.frombuffer(decoded.samples, dtype=np.int16).astype(np.float32) / 32768.0
    return samples, decoded.sample_rate


def analyze_audio(audio_path: Path, ffmpeg: str | None) -> Analysis:
    try:
        samples, sr = decode_audio(audio_path, ffmpeg)
        if samples.size == 0:
            return Analysis(error="decoded audio is empty")
        duration = samples.size / sr
        rms = math.sqrt(float(np.mean(samples * samples)) + 1e-12)
        rms_db = 20 * math.log10(max(rms, 1e-12))
        f0_values = estimate_f0(samples, sr)
        centroid = spectral_centroid(samples, sr)
        return Analysis(
            duration_s=duration,
            f0_median_hz=float(np.median(f0_values)) if f0_values else None,
            f0_mean_hz=float(np.mean(f0_values)) if f0_values else None,
            f0_coverage=len(f0_values) / max(1, int(duration / 0.02)),
            rms_db=rms_db,
            centroid_hz=centroid,
        )
    except Exception as exc:  # noqa: BLE001 - this is a diagnostic script
        return Analysis(error=str(exc))


def estimate_f0(samples: np.ndarray, sr: int) -> list[float]:
    frame_size = int(sr * 0.04)
    hop = int(sr * 0.02)
    min_lag = int(sr / 350)
    max_lag = int(sr / 70)
    values: list[float] = []
    if samples.size < frame_size:
        return values
    window = np.hanning(frame_size).astype(np.float32)
    for start in range(0, samples.size - frame_size, hop):
        frame = samples[start : start + frame_size]
        energy = float(np.mean(frame * frame))
        if energy < 1e-5:
            continue
        frame = (frame - np.mean(frame)) * window
        corr = np.correlate(frame, frame, mode="full")[frame_size - 1 :]
        if corr[0] <= 0:
            continue
        segment = corr[min_lag:max_lag]
        if segment.size == 0:
            continue
        lag = int(np.argmax(segment) + min_lag)
        confidence = float(corr[lag] / corr[0])
        if confidence < 0.28:
            continue
        values.append(sr / lag)
    return values


def spectral_centroid(samples: np.ndarray, sr: int) -> float | None:
    frame_size = min(4096, samples.size)
    if frame_size < 512:
        return None
    starts = np.linspace(0, samples.size - frame_size, num=min(16, max(1, samples.size // frame_size))).astype(int)
    centroids = []
    freqs = np.fft.rfftfreq(frame_size, 1 / sr)
    window = np.hanning(frame_size)
    for start in starts:
        frame = samples[start : start + frame_size] * window
        mag = np.abs(np.fft.rfft(frame))
        total = float(np.sum(mag))
        if total > 0:
            centroids.append(float(np.sum(freqs * mag) / total))
    return float(np.median(centroids)) if centroids else None


def classify_voice(
    voice: Voice,
    analysis: Analysis,
    edge_tag: dict[str, list[str]],
    azure_meta: dict[str, list[str]],
) -> tuple[str, str, str, str]:
    gender_label = "女声" if voice.gender == "female" else "男声" if voice.gender == "male" else "未知"
    f0 = analysis.f0_median_hz
    centroid = analysis.centroid_hz
    categories = set(edge_tag.get("categories") or [])
    personalities = set(edge_tag.get("personalities") or [])
    styles = set(azure_meta.get("styles") or [])
    roles = set(azure_meta.get("roles") or [])

    if voice.gender == "female":
        if "Child" in voice.voice_id or "Cartoon" in categories:
            age = "少女"
        elif f0 and f0 >= 245:
            age = "少女"
        elif f0 and f0 < 205:
            age = "女中年"
        else:
            age = "女青年"
        if "Warm" in personalities or "gentle" in styles or "affectionate" in styles:
            tone = "温柔"
        elif "Lively" in personalities:
            tone = "活泼清亮"
        elif centroid and centroid >= 1750:
            tone = "清亮"
        elif f0 and f0 < 210:
            tone = "稳重"
        else:
            tone = "温柔自然"
    elif voice.gender == "male":
        if {"OlderAdultMale", "SeniorMale"} & roles:
            age = "男中老年"
        elif "Cartoon" in categories or f0 and f0 >= 165:
            age = "少年/男青年"
        elif f0 and f0 < 120:
            age = "男中年"
        else:
            age = "男青年"
        if "Professional" in personalities or "Reliable" in personalities:
            tone = "专业稳重"
        elif "Sunshine" in personalities or "Lively" in personalities:
            tone = "阳光清朗"
        elif "Passion" in personalities or "Sports" in categories:
            tone = "热血有力"
        elif "Cute" in personalities:
            tone = "少年感"
        elif f0 and f0 < 125:
            tone = "沉稳低磁"
        elif centroid and centroid >= 1500:
            tone = "清朗"
        else:
            tone = "自然温和"
    else:
        age = "未知"
        tone = "自然"

    profile = f"{age}-{tone}"
    role_hint = role_hint_for(voice.gender, age, tone, categories, roles)
    notes = "HD/FHD" if voice.hd else ""
    if analysis.error:
        notes = f"{notes}; analysis_error={analysis.error}".strip("; ")
    return gender_label, profile, role_hint, notes


def role_hint_for(gender: str, age: str, tone: str, categories: set[str], roles: set[str]) -> str:
    if "News" in categories:
        return "新闻播报, 正文旁白, 官方说明"
    if "Sports" in categories:
        return "赛事解说, 热血男配, 战斗旁白"
    if "Narrator" in roles or "Novel" in categories and "沉稳" in tone:
        return "旁白, 男主, 成熟角色"
    if gender == "female":
        if "少女" in age:
            return "少女, 校园女主, 活泼配角"
        if "女中年" in age:
            return "母亲, 老师, 成熟女性, 旁白"
        return "女主, 女青年, 温柔配角"
    if gender == "male":
        if "男中年" in age:
            return "大叔, 父亲, 反派, 厚重旁白"
        if "少年" in age:
            return "少年, 年轻男主, 同学"
        return "男主, 男青年, 书生, 旁白"
    return "待人工标注"


def write_reports(out_dir: Path, rows: list[dict[str, str]]) -> None:
    csv_path = out_dir / "voice_profiles.csv"
    md_path = out_dir / "voice_profiles.md"
    with csv_path.open("w", encoding="utf-8-sig", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)
    with md_path.open("w", encoding="utf-8") as f:
        f.write("# Next Edge TTS 发音人画像初稿\n\n")
        f.write("| 名称 | 声线画像 | Edge 标签 | Azure styles/roles | 角色建议 | F0中位数 | 音频 |\n")
        f.write("| --- | --- | --- | --- | --- | ---: | --- |\n")
        for row in rows:
            f.write(
                f"| {row['name']} | {row['profile']} | {row['edge_tags']} | "
                f"{row['azure_tags']} | {row['role_hint']} | {row['f0_median_hz']} | {row['audio']} |\n"
            )
        f.write("\n说明：画像优先参考 Edge VoiceTag 和 Azure styles/roles，再用音频特征兜底；仍需要人工听感复核后再作为角色绑定标准。\n")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--api", help="TTS API endpoint. Defaults to the first API in next_edge_proxy.js.")
    parser.add_argument("--text", help="Sample text. Defaults to @sampleText or built-in fallback.")
    parser.add_argument("--limit", type=int, help="Only process the first N voices.")
    parser.add_argument("--timeout", type=float, default=15.0)
    parser.add_argument("--metadata-timeout", type=float, default=15.0)
    parser.add_argument("--ffmpeg", default=shutil.which("ffmpeg"), help="Path to ffmpeg. Empty value disables ffmpeg.")
    parser.add_argument("--out", type=Path, default=None)
    args = parser.parse_args()

    apis, voices, sample_text = parse_template(NEXT_TEMPLATE)
    edge_tags = fetch_edge_voice_tags(args.metadata_timeout)
    api = args.api or apis[0]
    text = args.text or sample_text
    if args.limit:
        voices = voices[: args.limit]
    timestamp = dt.datetime.now().strftime("%Y%m%d-%H%M%S")
    out_dir = args.out or (REPO_ROOT / "build/tts_voice_profiles" / f"next_edge_{timestamp}")
    audio_dir = out_dir / "audio"
    audio_dir.mkdir(parents=True, exist_ok=True)

    rows: list[dict[str, str]] = []
    manifest = {
        "api": api,
        "sample_text": text,
        "ffmpeg": args.ffmpeg or "",
        "created_at": timestamp,
        "edge_voice_list_url": EDGE_VOICE_LIST_URL,
        "voices": [],
    }
    print(f"API: {api}")
    print(f"voices: {len(voices)}")
    print(f"output: {out_dir}")
    for index, voice in enumerate(voices, 1):
        safe_name = re.sub(r"[^\w.-]+", "_", f"{index:02d}_{voice.name}_{voice.voice_id}", flags=re.UNICODE)
        audio_path = audio_dir / f"{safe_name}.mp3"
        status = "ok"
        error = ""
        started = time.perf_counter()
        try:
            audio_path.write_bytes(synthesize(api, voice, text, args.timeout))
        except Exception as exc:  # noqa: BLE001 - diagnostic script
            status = "error"
            error = str(exc)
        elapsed_ms = int((time.perf_counter() - started) * 1000)
        analysis = analyze_audio(audio_path, args.ffmpeg) if status == "ok" else Analysis(error=error)
        edge_tag = edge_tags.get(voice.voice_id, {})
        azure_meta = AZURE_VOICE_METADATA.get(voice.voice_id, {})
        gender_label, profile, role_hint, notes = classify_voice(voice, analysis, edge_tag, azure_meta)
        edge_tag_text = format_tags((edge_tag.get("categories") or []) + (edge_tag.get("personalities") or []))
        azure_tag_text = format_tags((azure_meta.get("styles") or [])[:6] + (azure_meta.get("roles") or []))
        row = {
            "name": voice.name,
            "voice_id": voice.voice_id,
            "gender": gender_label,
            "profile": profile,
            "edge_categories": format_tags(edge_tag.get("categories") or []),
            "edge_personalities": format_tags(edge_tag.get("personalities") or []),
            "azure_styles": format_tags(azure_meta.get("styles") or []),
            "azure_roles": format_tags(azure_meta.get("roles") or []),
            "edge_tags": edge_tag_text,
            "azure_tags": azure_tag_text,
            "role_hint": role_hint,
            "f0_median_hz": format_float(analysis.f0_median_hz),
            "f0_mean_hz": format_float(analysis.f0_mean_hz),
            "f0_coverage": format_float(analysis.f0_coverage),
            "rms_db": format_float(analysis.rms_db),
            "centroid_hz": format_float(analysis.centroid_hz),
            "duration_s": format_float(analysis.duration_s),
            "elapsed_ms": str(elapsed_ms),
            "status": status,
            "notes": notes,
            "audio": str(audio_path.relative_to(out_dir)) if audio_path.exists() else "",
        }
        rows.append(row)
        manifest["voices"].append(row)
        print(f"[{index:02d}/{len(voices)}] {voice.name} {status} {profile} {elapsed_ms}ms")
    if not rows:
        raise SystemExit("No rows generated")
    write_reports(out_dir, rows)
    (out_dir / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"CSV: {out_dir / 'voice_profiles.csv'}")
    print(f"MD:  {out_dir / 'voice_profiles.md'}")
    return 0


def format_float(value: float | None) -> str:
    if value is None or math.isnan(value):
        return ""
    return f"{value:.1f}"


def format_tags(values: list[str]) -> str:
    return ", ".join(value.strip() for value in values if value.strip())


if __name__ == "__main__":
    raise SystemExit(main())
