"""检查单文件内置 Skill 的整数版本；不改变 App 的升级判定。"""

import argparse
from dataclasses import dataclass
from pathlib import Path, PurePosixPath
import re
import subprocess
import sys


SKILL_DIR = "app/src/main/assets/skills"
MAX_VERSION = 2**31 - 1


@dataclass(frozen=True)
class Skill:
    path: str
    skill_id: str
    version: int
    payload: str


def parse_skill(path: str, content: str) -> Skill:
    """按 App 的平面 frontmatter 格式读取 id/version，不解析正文中的伪字段。"""
    lines = content.replace("\r\n", "\n").strip().split("\n")
    if not lines or lines[0] != "---":
        raise ValueError(f"{path!r}: 缺少 frontmatter")
    try:
        end = lines.index("---", 1)
    except ValueError:
        raise ValueError(f"{path!r}: frontmatter 未闭合") from None
    metadata = {}
    version_line = None
    for index in range(1, end):
        key, separator, value = lines[index].partition(":")
        key = key.strip()
        if not separator or key not in ("id", "version"):
            continue
        if key in metadata:
            raise ValueError(f"{path!r}: 重复的 {key} 字段")
        value = value.strip()
        # AiSkillRegistry 仅去除双引号，不能按完整 YAML 语义放宽。
        if len(value) >= 2 and value.startswith('"') and value.endswith('"'):
            value = value[1:-1]
        metadata[key] = value
        if key == "version":
            version_line = index
    raw_id = metadata.get("id", "").strip()
    if not raw_id:
        raise ValueError(f"{path!r}: 缺少 id")
    skill_id = re.sub(r"[^a-z0-9_\-\u4e00-\u9fa5]+", "_", raw_id.lower())
    skill_id = skill_id.strip("_-") or "custom_skill"
    raw_version = metadata.get("version", "")
    if not re.fullmatch(r"[0-9]+", raw_version):
        raise ValueError(f"{path!r}: version 必须是 1..{MAX_VERSION} 的整数")
    try:
        version = int(raw_version)
    except ValueError:
        version = 0
    if not 1 <= version <= MAX_VERSION:
        raise ValueError(f"{path!r}: version 必须是 1..{MAX_VERSION} 的整数")
    payload = "\n".join(line for index, line in enumerate(lines) if index != version_line)
    return Skill(path, skill_id, version, payload)


def git(repo: Path, *args: str) -> bytes:
    result = subprocess.run(["git", *args], cwd=repo, capture_output=True)
    if result.returncode:
        error = result.stderr.decode("utf-8", errors="replace").strip()
        raise ValueError(f"git {args[0]} 失败: {error}")
    return result.stdout


def resolve_commit(repo: Path, ref: str) -> str:
    return git(repo, "rev-parse", "--verify", "--end-of-options", f"{ref}^{{commit}}").decode().strip()


def collect_skills(documents) -> dict[str, Skill]:
    skills = {}
    for path, content in documents:
        skill = parse_skill(path, content)
        if skill.skill_id in skills:
            raise ValueError(f"{path!r}: id 与 {skills[skill.skill_id].path!r} 重复")
        skills[skill.skill_id] = skill
    return skills


def read_snapshot(repo: Path, commit: str) -> dict[str, Skill]:
    paths = git(repo, "ls-tree", "-r", "-z", "--name-only", commit, "--", SKILL_DIR)
    # 只检查 assets/skills/*.md；目录包的 SKILL.md/资源由包哈希负责。
    paths = [path.decode("utf-8") for path in paths.split(b"\0") if path]
    paths = [path for path in paths if PurePosixPath(path).parent.as_posix() == SKILL_DIR
             and PurePosixPath(path).suffix.lower() == ".md"]
    return collect_skills(
        (path, git(repo, "show", f"{commit}:{path}").decode("utf-8"))
        for path in paths
    )


def read_worktree(repo: Path) -> dict[str, Skill]:
    directory = repo / SKILL_DIR
    if not directory.exists():
        return {}
    return collect_skills(
        (path.relative_to(repo).as_posix(), path.read_text(encoding="utf-8"))
        for path in sorted(directory.iterdir())
        if path.is_file() and path.suffix.lower() == ".md"
    )


def check_versions(previous: dict[str, Skill], current: dict[str, Skill]) -> list[str]:
    errors = []
    for skill_id, skill in current.items():
        old = previous.get(skill_id)
        if old is None:
            continue
        if skill.version < old.version:
            errors.append(f"{skill.path!r}: version 不得回退 ({old.version} -> {skill.version})")
        elif skill.payload != old.payload and skill.version <= old.version:
            errors.append(f"{skill.path!r}: 内容已变化，version 必须大于目标分支的 {old.version}")
    return errors


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--base-ref", required=True, help="目标分支的最新提交或 ref")
    parser.add_argument("--head-ref", help="候选提交；省略时检查工作区文件")
    parser.add_argument("--require-base-ancestor", action="store_true",
                        help="CI 使用：候选提交必须包含最新目标分支")
    args = parser.parse_args(argv)
    try:
        repo = args.repo.resolve()
        base = resolve_commit(repo, args.base_ref)
        head = resolve_commit(repo, args.head_ref or "HEAD")
        if args.require_base_ancestor:
            result = subprocess.run(["git", "merge-base", "--is-ancestor", base, head], cwd=repo)
            if result.returncode != 0:
                raise ValueError("候选提交未包含最新目标分支，请更新分支后重新运行检查")
        previous = read_snapshot(repo, base)
        current = read_snapshot(repo, head) if args.head_ref else read_worktree(repo)
        errors = check_versions(previous, current)
        if errors:
            raise ValueError("\n".join(errors))
    except (ValueError, OSError, UnicodeError) as error:
        print(f"Skill 版本校验失败: {error}", file=sys.stderr)
        return 1
    print(f"Skill 版本校验通过：{len(current)} 个单文件 Skill，基线 {base[:12]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
